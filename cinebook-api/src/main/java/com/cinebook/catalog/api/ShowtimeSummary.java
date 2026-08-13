package com.cinebook.catalog.api;

import java.time.Instant;
import java.util.UUID;

public record ShowtimeSummary(
        UUID showtimeId,
        Instant startAt,
        UUID movieId,
        String movieTitle,
        UUID cinemaId,
        String cinemaName,
        String district,
        UUID roomId,
        String roomName,
        long basePrice) {
}
