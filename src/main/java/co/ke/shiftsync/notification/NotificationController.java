package co.ke.shiftsync.notification;

import co.ke.shiftsync.common.exceptions.UnauthorizedActionException;
import co.ke.shiftsync.notification.NotificationDtos.NotificationResponse;
import co.ke.shiftsync.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final NotificationRepository repository;
    private final CurrentUser currentUser;

    @GetMapping
    public List<NotificationResponse> list() {
        return service.forUser(currentUser.id()).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount(currentUser.id()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        Notification n = repository.findById(id).orElseThrow();
        if (!n.getUser().getId().equals(currentUser.id())) {
            throw new UnauthorizedActionException("That notification doesn't belong to you.");
        }
        service.markRead(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        service.markAllRead(currentUser.id());
        return ResponseEntity.noContent().build();
    }
}
