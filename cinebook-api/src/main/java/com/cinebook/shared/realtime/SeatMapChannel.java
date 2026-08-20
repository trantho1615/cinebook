package com.cinebook.shared.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Bao cho moi instance cinebook-api rang so do ghe cua mot suat chieu vua doi.
 *
 * Redis pub/sub chu khong phai Kafka: viec nay nhe, can ngay, va mat cung khong sao —
 * client vao sau se load lai so do ghe qua REST. Kafka danh cho viec nang va cham ma
 * KHONG duoc mat (gui email, doi soat). Dung nham chieu nao cung sai: Kafka de doi mau
 * ghe la them do tre vo ich, Redis pub/sub de gui email la mat email khi khong co
 * subscriber nao dang song.
 */
@Component
public class SeatMapChannel {

    private static final Logger log = LoggerFactory.getLogger(SeatMapChannel.class);

    static final String PREFIX = "seatmap:";

    private final StringRedisTemplate redis;

    public SeatMapChannel(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Publish SAU KHI transaction commit.
     *
     * Publish ngay trong transaction roi transaction rollback thi moi UI dang mo vua nhan
     * mot su that khong ton tai — va khong co cach nao rut lai.
     */
    public void seatsChanged(UUID showtimeId, String status, List<String> seatLabels) {
        String payload = """
                {"showtimeId":"%s","status":"%s","seats":[%s]}"""
                .formatted(showtimeId, status,
                        seatLabels.stream().map(label -> "\"" + label + "\"")
                                .reduce((a, b) -> a + "," + b).orElse(""));

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doPublish(showtimeId, payload);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doPublish(showtimeId, payload);
            }
        });
    }

    private void doPublish(UUID showtimeId, String payload) {
        try {
            redis.convertAndSend(PREFIX + showtimeId, payload);
        } catch (RuntimeException e) {
            // Redis chet KHONG duoc lam hong giao dich giu ghe. Mat realtime thi UI cua
            // nguoi khac cham mot nhip; nem exception o day thi khong ai mua duoc ve.
            // Spec muc 7: "Redis chet -> seat map fallback doc thang Postgres".
            log.warn("Khong publish duoc thay doi seat map cho {}", showtimeId, e);
        }
    }
}
