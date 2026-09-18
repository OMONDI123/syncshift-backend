package co.ke.shiftsync.notification;

import co.ke.shiftsync.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A persisted, per-user notification — requirement #7: "All notifications
 * are persisted and viewable in a notification center with read/unread
 * status." Real-time delivery is a side effect (pushed over WebSocket when
 * created); this row is the durable record a user sees on refresh/re-login
 * regardless of whether they were online when it fired. */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notif_user", columnList = "user_id,readAt"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String body;

    /** Optional deep-link target, e.g. a shift or swap request id. */
    private Long linkShiftId;
    private Long linkSwapId;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;
}
