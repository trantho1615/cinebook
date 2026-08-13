package com.cinebook.catalog.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read model hop thanh cua mot suat chieu: suat + tom tat phim + rap + phong + so do ghe.
 *
 * Ton tai de nguoi goi chi phai thuc hien MOT lan goi thay vi ghep tu bon nguon.
 * Day cung la endpoint se duoc load test va cache o milestone observability.
 */
public record ShowtimeDetail(
        UUID showtimeId,
        Instant startAt,
        Instant endAt,
        String status,
        UUID movieId,
        String movieTitle,
        int durationMin,
        String ageRating,
        UUID cinemaId,
        String cinemaName,
        UUID roomId,
        String roomName,
        long basePrice,
        List<SeatView> seats) {
}
