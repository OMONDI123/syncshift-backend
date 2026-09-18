package co.ke.shiftsync.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Thin, typed wrapper around SimpMessagingTemplate so services don't
 * hand-build topic path strings all over the codebase. */
@Service
@RequiredArgsConstructor
public class RealtimeGateway {

    private final SimpMessagingTemplate messagingTemplate;

    public void scheduleChanged(Long locationId, Object payload) {
        messagingTemplate.convertAndSend("/topic/locations/" + locationId + "/schedule", payload);
    }

    public void presenceChanged(Long locationId, Object payload) {
        messagingTemplate.convertAndSend("/topic/locations/" + locationId + "/presence", payload);
    }

    public void notifyUser(Long userId, Object payload) {
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/notifications", payload);
    }

    public void assignmentConflict(Long shiftId, Object payload) {
        messagingTemplate.convertAndSend("/topic/shifts/" + shiftId + "/conflict", payload);
    }

    public void swapChanged(Long locationId, Object payload) {
        messagingTemplate.convertAndSend("/topic/locations/" + locationId + "/swaps", payload);
    }
}
