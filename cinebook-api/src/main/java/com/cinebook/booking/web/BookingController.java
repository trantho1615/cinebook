package com.cinebook.booking.web;

import com.cinebook.booking.infra.HoldSeatsUseCase;
import com.cinebook.booking.web.dto.BookingResponse;
import com.cinebook.booking.web.dto.HoldSeatsRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class BookingController {

    private final HoldSeatsUseCase holdSeats;

    public BookingController(HoldSeatsUseCase holdSeats) {
        this.holdSeats = holdSeats;
    }

    @PostMapping("/showtimes/{showtimeId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse hold(@PathVariable UUID showtimeId,
                                @Valid @RequestBody HoldSeatsRequest request,
                                @AuthenticationPrincipal UUID userId) {
        return BookingResponse.from(holdSeats.hold(userId, showtimeId, request.seatIds()));
    }
}
