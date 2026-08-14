package com.cinebook.booking.web;

import com.cinebook.booking.domain.BookingNotFoundException;
import com.cinebook.booking.domain.DuplicateRequestException;
import com.cinebook.booking.domain.HoldExpiredException;
import com.cinebook.booking.domain.InvalidSeatSelectionException;
import com.cinebook.booking.domain.SeatsUnavailableException;
import com.cinebook.booking.domain.ShowtimeNotBookableException;
import com.cinebook.shared.web.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler(SeatsUnavailableException.class)
    public ResponseEntity<ApiError> handleSeatsUnavailable(SeatsUnavailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("SEATS_UNAVAILABLE", e.getMessage()));
    }

    @ExceptionHandler(InvalidSeatSelectionException.class)
    public ResponseEntity<ApiError> handleInvalidSelection(InvalidSeatSelectionException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("INVALID_SEAT_SELECTION", e.getMessage()));
    }

    @ExceptionHandler(ShowtimeNotBookableException.class)
    public ResponseEntity<ApiError> handleNotBookable(ShowtimeNotBookableException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("SHOWTIME_NOT_BOOKABLE", e.getMessage()));
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ApiError> handleDuplicateRequest(DuplicateRequestException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DUPLICATE_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ApiError> handleBookingNotFound(BookingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("BOOKING_NOT_FOUND", e.getMessage()));
    }

    /**
     * 410 GONE chu khong phai 404 hay 409: tai nguyen DA TUNG ton tai va gio khong con.
     * Client phan biet duoc voi "chua bao gio co".
     */
    @ExceptionHandler(HoldExpiredException.class)
    public ResponseEntity<ApiError> handleHoldExpired(HoldExpiredException e) {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(new ApiError("HOLD_EXPIRED", e.getMessage()));
    }
}
