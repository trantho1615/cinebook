package com.cinebook.booking.domain;

import java.util.UUID;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID id) {
        super("Khong tim thay don dat ve: " + id);
    }
}
