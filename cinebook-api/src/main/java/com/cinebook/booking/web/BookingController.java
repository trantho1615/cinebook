package com.cinebook.booking.web;

import com.cinebook.booking.api.BookingView;
import com.cinebook.booking.domain.BookingNotFoundException;
import com.cinebook.booking.infra.BookingQueryJdbc;
import com.cinebook.booking.infra.CancelBookingUseCase;
import com.cinebook.booking.infra.HoldSeatsUseCase;
import com.cinebook.booking.infra.IdempotencyService;
import com.cinebook.booking.web.dto.BookingResponse;
import com.cinebook.booking.web.dto.HoldSeatsRequest;
import com.cinebook.identity.api.AccessControl;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class BookingController {

    private final HoldSeatsUseCase holdSeats;
    private final IdempotencyService idempotency;
    private final CancelBookingUseCase cancelBookingUseCase;
    private final BookingQueryJdbc bookingQuery;
    private final AccessControl accessControl;

    public BookingController(HoldSeatsUseCase holdSeats,
                             IdempotencyService idempotency,
                             CancelBookingUseCase cancelBookingUseCase,
                             BookingQueryJdbc bookingQuery,
                             AccessControl accessControl) {
        this.holdSeats = holdSeats;
        this.idempotency = idempotency;
        this.cancelBookingUseCase = cancelBookingUseCase;
        this.bookingQuery = bookingQuery;
        this.accessControl = accessControl;
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

    @GetMapping("/bookings")
    public List<BookingView> myBookings(@AuthenticationPrincipal UUID userId) {
        return bookingQuery.findByUser(userId);
    }

    @GetMapping("/bookings/{id}")
    public BookingView getBooking(@PathVariable UUID id) {
        // Kiem tra quyen TRUOC khi nap du lieu: khong doc thu ma nguoi goi khong
        // duoc phep thay, du chi de roi nem loi.
        UUID owner = bookingQuery.findOwner(id).orElseThrow(() -> new BookingNotFoundException(id));
        accessControl.requireSelfOrAdmin(owner);

        return bookingQuery.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    @DeleteMapping("/bookings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelBooking(@PathVariable UUID id) {
        cancelBookingUseCase.cancel(id);
    }
}
