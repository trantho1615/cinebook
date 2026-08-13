package com.cinebook.catalog.web.dto;

import com.cinebook.catalog.domain.showtime.Showtime;

import java.time.Instant;
import java.util.UUID;

public record ShowtimeResponse(UUID id, UUID movieId, UUID roomId,
                               Instant startAt, Instant endAt,
                               long basePrice, String status) {

    public static ShowtimeResponse from(Showtime showtime) {
        return new ShowtimeResponse(
                showtime.getId(), showtime.getMovieId(), showtime.getRoomId(),
                showtime.getStartAt(), showtime.getEndAt(),
                showtime.getBasePrice(), showtime.getStatus().name());
    }
}
