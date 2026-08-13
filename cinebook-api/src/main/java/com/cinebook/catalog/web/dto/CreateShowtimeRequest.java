package com.cinebook.catalog.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Khong co truong endAt: gio ket thuc la thu he thong tinh tu thoi luong phim
 * cong khoang nghi don dep, khong phai thu admin nhap.
 */
public record CreateShowtimeRequest(
        @NotNull UUID movieId,
        @NotNull UUID roomId,
        @NotNull Instant startAt,
        @Min(1) long basePrice) {
}
