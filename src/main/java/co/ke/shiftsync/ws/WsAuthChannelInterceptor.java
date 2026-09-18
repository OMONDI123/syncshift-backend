package co.ke.shiftsync.ws;

import co.ke.shiftsync.security.AppUserPrincipal;
import co.ke.shiftsync.security.JwtService;
import co.ke.shiftsync.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/** Reads `Authorization: Bearer <jwt>` off the STOMP CONNECT frame (the same
 * token used for REST calls) and attaches the resolved user as the STOMP
 * session's Principal, so per-user topics can later be secured/addressed by
 * that identity rather than trusting whatever the client claims. */
@Component
@RequiredArgsConstructor
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                try {
                    String token = auth.substring(7);
                    String email = jwtService.extractEmail(token);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    if (jwtService.isTokenValid(token, userDetails)) {
                        accessor.setUser(new StompPrincipal(((AppUserPrincipal) userDetails).getId(), email));
                    }
                } catch (Exception ignored) {
                    // Leave unauthenticated; connection proceeds but user-specific
                    // topics simply won't resolve to a real identity for this session.
                }
            }
        }
        return message;
    }

    public record StompPrincipal(Long userId, String email) implements java.security.Principal {
        @Override
        public String getName() {
            return email;
        }
    }
}
