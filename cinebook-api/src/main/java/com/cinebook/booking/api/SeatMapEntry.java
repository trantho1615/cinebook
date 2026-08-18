package com.cinebook.booking.api;

import java.util.UUID;

public record SeatMapEntry(
        UUID seatId,
        String rowLabel,
        int seatNumber,
        String label,
        String seatType,
        long price,
        SeatStatus status) {
}
