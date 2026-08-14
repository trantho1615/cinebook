package com.cinebook.booking.web;

import com.cinebook.booking.infra.HoldSeatsUseCase;
import com.cinebook.booking.infra.IdempotencyService;
import com.cinebook.booking.web.dto.BookingResponse;
import com.cinebook.booking.web.dto.HoldSeatsRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class BookingController {

    private final HoldSeatsUseCase holdSeats;
    private final IdempotencyService idempotency;

    public BookingController(HoldSeatsUseCase holdSeats, IdempotencyService idempotency) {
        this.holdSeats = holdSeats;
        this.idempotency = idempotency;
    }

    /**
     * Header Idempotency-Key la tuy chon: bat buoc no se lam hong moi client hien co
     * ma khong dem lai gi. Khong co header thi request chay binh thuong.
     */
    @PostMapping("/showtimes/{showtimeId}/holds")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse hold(@PathVariable UUID showtimeId,
                                @Valid @RequestBody HoldSeatsRequest request,
                                @AuthenticationPrincipal UUID userId,
                                @RequestHeader(value = "Idempotency-Key", required = false)
                                String idempotencyKey) {
        return idempotency.execute(
                idempotencyKey, userId, "POST /showtimes/{id}/holds",
                () -> BookingResponse.from(holdSeats.hold(userId, showtimeId, request.seatIds())),
                BookingResponse.class);
    }
}
