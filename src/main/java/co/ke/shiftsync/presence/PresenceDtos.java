package co.ke.shiftsync.presence;

import java.time.Instant;

public class PresenceDtos {
    public record ClockRecordResponse(Long id, Long userId, String userName, Long shiftId, Long locationId,
                                       Instant clockInUtc, Instant clockOutUtc) {
        public static ClockRecordResponse from(ClockRecord r) {
            return new ClockRecordResponse(r.getId(), r.getUser().getId(), r.getUser().getName(), r.getShift().getId(),
                    r.getLocation().getId(), r.getClockInUtc(), r.getClockOutUtc());
        }
    }
}
