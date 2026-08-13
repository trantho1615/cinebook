package com.cinebook.identity.domain;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Ban khong co quyen truy cap tai nguyen nay");
    }
}
