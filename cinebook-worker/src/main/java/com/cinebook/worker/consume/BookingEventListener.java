package com.cinebook.worker.consume;

import com.cinebook.notification.infra.SendBookingConfirmedUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Tieu thu event cua booking va gui thong bao.
 *
 * Khong tu khu trung o day: viec do nam trong SendBookingConfirmedUseCase, duoi mot rang
 * buoc UNIQUE cua database. Khu trung bang bien nho trong tien trinh la vo dung ngay khi
 * co instance thu hai.
 */
@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final ObjectMapper objectMapper;
    private final SendBookingConfirmedUseCase sendBookingConfirmed;

    public BookingEventListener(ObjectMapper objectMapper,
                                SendBookingConfirmedUseCase sendBookingConfirmed) {
        this.objectMapper = objectMapper;
        this.sendBookingConfirmed = sendBookingConfirmed;
    }

    @KafkaListener(topics = "booking.events", groupId = "cinebook-notification")
    public void nhan(String message) {
        JsonNode json = objectMapper.readTree(message);
        String eventType = json.path("eventType").asString();
        if (!"BookingConfirmed".equals(eventType)) {
            return;
        }

        long eventId = json.path("eventId").asLong();
        UUID bookingId = UUID.fromString(json.path("aggregateId").asString());
        boolean daGui = sendBookingConfirmed.handle(eventId, bookingId);

        if (!daGui) {
            log.debug("Event {} khong sinh thong bao moi", eventId);
        }
    }
}
