package co.ke.shiftsync.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS at /ws. Topics used across the app (all under /topic,
 * i.e. server -> many-subscriber broadcast, matching requirement #6's "no
 * refresh needed" real-time features):
 *
 *   /topic/locations/{id}/schedule   shift created/updated/published
 *   /topic/locations/{id}/presence   on-duty-now dashboard
 *   /topic/users/{id}/notifications  per-user notification center
 *   /topic/shifts/{id}/conflict      assignment race lost by a manager's UI
 *
 * Authentication for the WebSocket handshake itself is handled by
 * WsAuthChannelInterceptor (reads the JWT from the STOMP CONNECT header,
 * same token the REST API uses) rather than cookies/session, consistent
 * with the stateless JWT design everywhere else in this API.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WsAuthChannelInterceptor wsAuthChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(wsAuthChannelInterceptor);
    }
}
