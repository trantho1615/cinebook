package com.cinebook.booking.web.dto;

import com.cinebook.booking.infra.HoldSeatsUseCase;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID bookingId,
        String code,
        String status,
        long totalAmount,
        Instant holdExpiresAt,
        List<String> seats) {

    public static BookingResponse from(HoldSeatsUseCase.HoldResult result) {
        return new BookingResponse(
                result.bookingId(), result.code(), "PENDING",
                result.totalAmount(), result.holdExpiresAt(), result.seatLabels());
    }
}
