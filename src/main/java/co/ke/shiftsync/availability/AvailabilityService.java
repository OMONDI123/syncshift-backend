package co.ke.shiftsync.availability;

import co.ke.shiftsync.audit.AuditService;
import co.ke.shiftsync.common.AuditEntityType;
import co.ke.shiftsync.common.Role;
import co.ke.shiftsync.common.exceptions.NotFoundException;
import co.ke.shiftsync.common.exceptions.UnauthorizedActionException;
import co.ke.shiftsync.notification.NotificationService;
import co.ke.shiftsync.notification.NotificationType;
import co.ke.shiftsync.security.CurrentUser;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final AvailabilityRepository repository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public List<AvailabilityWindow> forUser(Long userId) {
        return repository.findByUserId(userId);
    }

    @Transactional
    public AvailabilityWindow setRecurring(Long userId, int dayOfWeek, int startMinutes, int endMinutes) {
        AppUser actor = requireCanEdit(userId);
        repository.findByUserId(userId).stream()
                .filter(w -> w.getType() == AvailabilityType.RECURRING && dayOfWeek == safe(w.getDayOfWeek()))
                .forEach(repository::delete);
        AppUser target = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));
        AvailabilityWindow saved = repository.save(AvailabilityWindow.builder()
                .user(target).type(AvailabilityType.RECURRING).dayOfWeek(dayOfWeek)
                .startMinutes(startMinutes).endMinutes(endMinutes).available(true).build());
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.AVAILABILITY, String.valueOf(userId), "recurring_set");
        notifyManagersOfChange(target, "updated their recurring weekly availability");
        return saved;
    }

    @Transactional
    public void clearRecurring(Long userId, int dayOfWeek) {
        AppUser actor = requireCanEdit(userId);
        repository.findByUserId(userId).stream()
                .filter(w -> w.getType() == AvailabilityType.RECURRING && dayOfWeek == safe(w.getDayOfWeek()))
                .forEach(repository::delete);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.AVAILABILITY, String.valueOf(userId), "recurring_cleared");
        AppUser target = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));
        notifyManagersOfChange(target, "cleared a recurring availability window");
    }

    @Transactional
    public AvailabilityWindow addException(Long userId, LocalDate date, boolean available) {
        AppUser actor = requireCanEdit(userId);
        repository.findByUserId(userId).stream()
                .filter(w -> w.getType() == AvailabilityType.EXCEPTION && date.equals(w.getDate()))
                .forEach(repository::delete);
        AppUser target = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));
        AvailabilityWindow saved = repository.save(AvailabilityWindow.builder()
                .user(target).type(AvailabilityType.EXCEPTION).date(date)
                .startMinutes(0).endMinutes(24 * 60 - 1).available(available).build());
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.AVAILABILITY, String.valueOf(userId), "exception_added");
        notifyManagersOfChange(target, "added a one-off availability exception for " + date);
        return saved;
    }

    @Transactional
    public void removeException(Long id) {
        AvailabilityWindow window = repository.findById(id).orElseThrow(() -> new NotFoundException("Availability window not found: " + id));
        AppUser actor = requireCanEdit(window.getUser().getId());
        repository.delete(window);
        auditService.log(actor.getId(), actor.getName(), AuditEntityType.AVAILABILITY, String.valueOf(id), "exception_removed");
        notifyManagersOfChange(window.getUser(), "removed an availability exception");
    }

    /** Requirement #7: "Managers receive notifications for ... staff
     * availability changes." Notifies every manager of every location the
     * target staff member is certified at (deduplicated, since one manager
     * can run more than one of them) — skipped for non-STAFF (admins/managers
     * don't have certified-location availability that a manager needs to
     * react to). */
    private void notifyManagersOfChange(AppUser target, String changeDescription) {
        if (target.getRole() != Role.STAFF) return;
        Set<AppUser> managersToNotify = new LinkedHashSet<>();
        for (var location : target.getCertifiedLocations()) {
            managersToNotify.addAll(userRepository.findManagersOfLocation(location.getId()));
        }
        for (AppUser manager : managersToNotify) {
            notificationService.send(manager, NotificationType.AVAILABILITY_CHANGED, "Staff availability changed",
                    "%s %s.".formatted(target.getName(), changeDescription), null, null);
        }
    }

    /** Staff edit their own availability. Admins can edit anyone's. Managers
     * can edit availability for staff certified at one of the locations they
     * run — reasonable in practice (a manager helping an employee set up
     * their profile) and not specified either way in the brief, so documented
     * here as an assumption rather than left as an open hole. */
    private AppUser requireCanEdit(Long targetUserId) {
        AppUser actor = currentUser.get();
        if (actor.getId().equals(targetUserId) || actor.getRole() == Role.ADMIN) return actor;
        if (actor.getRole() == Role.MANAGER) {
            AppUser target = userRepository.findById(targetUserId).orElseThrow(() -> new NotFoundException("User not found: " + targetUserId));
            boolean managesThem = target.getCertifiedLocations().stream()
                    .anyMatch(loc -> actor.getManagedLocations().stream().anyMatch(m -> m.getId().equals(loc.getId())));
            if (managesThem) return actor;
        }
        throw new UnauthorizedActionException("You don't have permission to edit this person's availability.");
    }

    private int safe(Integer i) {
        return i == null ? -1 : i;
    }
}
