package com.cinebook.booking.domain;

public class ShowtimeNotBookableException extends RuntimeException {

    public ShowtimeNotBookableException(String reason) {
        super(reason);
    }
}
