package co.ke.shiftsync.notification;

import co.ke.shiftsync.common.NotificationChannel;
import co.ke.shiftsync.notification.NotificationDtos.NotificationResponse;
import co.ke.shiftsync.user.AppUser;
import co.ke.shiftsync.ws.RealtimeGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Requirement #7: persisted, per-user notifications with read/unread state,
 * pushed live over WebSocket, with a per-user channel preference. Email is
 * SIMULATED — logged, never actually sent — since the brief only asks for
 * "email simulation".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final RealtimeGateway realtimeGateway;

    @Transactional
    public Notification send(AppUser recipient, NotificationType type, String title, String body,
                              Long linkShiftId, Long linkSwapId) {
        Notification n = repository.save(Notification.builder()
                .user(recipient).type(type).title(title).body(body)
                .linkShiftId(linkShiftId).linkSwapId(linkSwapId)
                .createdAt(Instant.now())
                .build());

        realtimeGateway.notifyUser(recipient.getId(), NotificationResponse.from(n));

        if (recipient.getNotificationChannel() == NotificationChannel.IN_APP_AND_EMAIL) {
            log.info("[SIMULATED EMAIL] To: {} <{}> | Subject: {} | Body: {}",
                    recipient.getName(), recipient.getEmail(), title, body);
        }
        return n;
    }

    public List<Notification> forUser(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markRead(Long notificationId) {
        repository.findById(notificationId).ifPresent(n -> {
            if (n.getReadAt() == null) {
                n.setReadAt(Instant.now());
                repository.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(Long userId) {
        List<Notification> unread = repository.findByUserIdAndReadAtIsNull(userId);
        Instant now = Instant.now();
        unread.forEach(n -> n.setReadAt(now));
        repository.saveAll(unread);
    }
}
