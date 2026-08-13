package com.cinebook.identity.domain;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Email da duoc su dung: " + email);
    }
}
