package co.ke.shiftsync.presence;

import co.ke.shiftsync.common.exceptions.BusinessRuleException;
import co.ke.shiftsync.common.exceptions.NotFoundException;
import co.ke.shiftsync.common.exceptions.UnauthorizedActionException;
import co.ke.shiftsync.presence.PresenceDtos.ClockRecordResponse;
import co.ke.shiftsync.schedule.Shift;
import co.ke.shiftsync.schedule.ShiftRepository;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.security.PermissionService;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.ws.RealtimeGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Requirement #6's "on-duty now" dashboard, live over WebSocket. Anyone can
 * clock themselves in/out of a shift they're assigned to; a manager clocking
 * someone else in/out (e.g. correcting a forgotten punch) is allowed if they
 * manage that location — not specified in the brief, documented assumption. */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final ClockRecordRepository repository;
    private final ShiftRepository shiftRepository;
    private final CurrentUser currentUser;
    private final RealtimeGateway realtimeGateway;
    private final PermissionService permissions;

    public List<ClockRecord> onDutyAt(Long locationId) {
        permissions.requireViewPresence(currentUser.get(), locationId);
        return repository.findByLocationIdAndClockOutUtcIsNull(locationId);
    }

    @Transactional
    public ClockRecord clockIn(Long shiftId) {
        AppUser actor = currentUser.get();
        Shift shift = shiftRepository.findById(shiftId).orElseThrow(() -> new NotFoundException("Shift not found: " + shiftId));
        if (shift.getAssignedUsers().stream().noneMatch(u -> u.getId().equals(actor.getId()))) {
            throw new UnauthorizedActionException("You're not assigned to this shift.");
        }
        if (repository.findByUserIdAndShiftIdAndClockOutUtcIsNull(actor.getId(), shiftId).isPresent()) {
            throw new BusinessRuleException("Already clocked in to this shift.");
        }
        ClockRecord saved = repository.save(ClockRecord.builder()
                .user(actor).shift(shift).location(shift.getLocation()).clockInUtc(Instant.now()).build());
        realtimeGateway.presenceChanged(shift.getLocation().getId(),
                Map.of("type", "clock_in", "record", ClockRecordResponse.from(saved)));
        return saved;
    }

    @Transactional
    public ClockRecord clockOut(Long shiftId) {
        AppUser actor = currentUser.get();
        Shift shift = shiftRepository.findById(shiftId).orElseThrow(() -> new NotFoundException("Shift not found: " + shiftId));
        ClockRecord record = repository.findByUserIdAndShiftIdAndClockOutUtcIsNull(actor.getId(), shiftId)
                .orElseThrow(() -> new BusinessRuleException("You're not currently clocked in to this shift."));
        record.setClockOutUtc(Instant.now());
        ClockRecord saved = repository.save(record);
        realtimeGateway.presenceChanged(shift.getLocation().getId(),
                Map.of("type", "clock_out", "record", ClockRecordResponse.from(saved)));
        return saved;
    }
}
