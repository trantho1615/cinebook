package com.cinebook.catalog.web.dto;

import com.cinebook.catalog.domain.venue.RoomType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull RoomType roomType,
        @Min(1) @Max(26) int rowCount,
        @Min(1) @Max(40) int seatsPerRow) {
}
