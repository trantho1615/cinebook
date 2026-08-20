package com.cinebook.shared.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP tren WebSocket cho so do ghe.
 *
 * Broker don gian trong bo nho la du: moi instance chi day xuong nhung client dang noi
 * voi CHINH no, con viec lan toa giua cac instance da do Redis pub/sub lo. Dung mot
 * message broker ngoai (RabbitMQ relay) o day la them mot thanh phan phai van hanh ma
 * khong giai quyet them van de nao.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }
}
