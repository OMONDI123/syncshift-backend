package co.ke.shiftsync.audit;

import java.time.Instant;

public class AuditDtos {
    public record AuditEntryResponse(Long id, Instant atUtc, Long actorUserId, String actorName,
                                      String entityType, String entityId, String action,
                                      Long locationId, String before, String after) {
        public static AuditEntryResponse from(AuditLogEntry e) {
            return new AuditEntryResponse(e.getId(), e.getAtUtc(), e.getActorUserId(), e.getActorName(),
                    e.getEntityType().name(), e.getEntityId(), e.getAction(), e.getLocationId(),
                    e.getBeforeJson(), e.getAfterJson());
        }
    }
}
