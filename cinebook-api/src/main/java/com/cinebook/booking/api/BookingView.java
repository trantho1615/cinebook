package com.cinebook.booking.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingView(
        UUID bookingId,
        String code,
        String status,
        long totalAmount,
        Instant holdExpiresAt,
        Instant createdAt,
        UUID showtimeId,
        String movieTitle,
        String cinemaName,
        Instant startAt,
        List<String> seats) {
}
