package com.cinebook.catalog.api;

import java.util.UUID;

public record SeatView(
        UUID seatId,
        String rowLabel,
        int seatNumber,
        String label,
        String seatType,
        long price) {
}
