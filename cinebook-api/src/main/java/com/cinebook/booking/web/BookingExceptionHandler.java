package com.cinebook.booking.web;

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
}
