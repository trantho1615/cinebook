package com.cinebook.booking.domain;

public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException() {
        super("Mot yeu cau voi cung Idempotency-Key dang duoc xu ly");
    }
}
