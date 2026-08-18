package com.cinebook.booking.domain;

public class InvalidSeatSelectionException extends RuntimeException {

    public InvalidSeatSelectionException(String reason) {
        super(reason);
    }
}
