package co.ke.shiftsync.notification;

import java.time.Instant;

public class NotificationDtos {
    public record NotificationResponse(Long id, String type, String title, String body,
                                        Long linkShiftId, Long linkSwapId, Instant createdAt, boolean read) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
                    n.getLinkShiftId(), n.getLinkSwapId(), n.getCreatedAt(), n.getReadAt() != null);
        }
    }
}
