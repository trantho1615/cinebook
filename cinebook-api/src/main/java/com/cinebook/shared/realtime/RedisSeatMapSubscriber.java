package com.cinebook.shared.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;

/**
 * Redis -> WebSocket.
 *
 * Moi instance cinebook-api deu nghe kenh nay, ke ca instance khong xu ly request giu
 * ghe — do chinh la cach client dang noi voi instance khac nhin thay thay doi.
 */
@Configuration
public class RedisSeatMapSubscriber {

    @Bean
    RedisMessageListenerContainer seatMapListenerContainer(RedisConnectionFactory connectionFactory,
                                                           SimpMessagingTemplate messagingTemplate) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            String showtimeId = channel.substring(SeatMapChannel.PREFIX.length());
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            messagingTemplate.convertAndSend("/topic/showtimes/" + showtimeId, payload);
        }, new PatternTopic(SeatMapChannel.PREFIX + "*"));
        return container;
    }
}
