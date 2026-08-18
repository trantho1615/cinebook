package com.cinebook.booking.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record HoldSeatsRequest(@NotEmpty List<UUID> seatIds) {
}
