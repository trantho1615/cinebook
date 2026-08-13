package com.cinebook.catalog.domain.showtime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "showtimes")
public class Showtime {

    /**
     * Khoang nghi giua hai suat de don rap va cho khan gia ra vao.
     * Vi end_at duoc tinh kem khoang nay, rang buoc no_overlap o database tro thanh
     * thu BAO DAM khoang nghi — khong con trong cho admin nho.
     */
    public static final Duration CLEANING_BUFFER = Duration.ofMinutes(15);

    @Id
    private UUID id;

    @Column(name = "movie_id", nullable = false)
    private UUID movieId;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "base_price", nullable = false)
    private long basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShowtimeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Showtime() {
    }

    public static Showtime schedule(UUID movieId, UUID roomId, Instant startAt,
                                    int movieDurationMin, long basePrice) {
        Showtime showtime = new Showtime();
        showtime.id = UUID.randomUUID();
        showtime.movieId = movieId;
        showtime.roomId = roomId;
        showtime.startAt = startAt;
        showtime.endAt = startAt.plus(Duration.ofMinutes(movieDurationMin)).plus(CLEANING_BUFFER);
        showtime.basePrice = basePrice;
        showtime.status = ShowtimeStatus.SCHEDULED;
        showtime.createdAt = Instant.now();
        return showtime;
    }

    public void cancel() {
        this.status = ShowtimeStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMovieId() {
        return movieId;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public long getBasePrice() {
        return basePrice;
    }

    public ShowtimeStatus getStatus() {
        return status;
    }
}
