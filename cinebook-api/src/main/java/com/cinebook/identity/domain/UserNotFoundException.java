package com.cinebook.identity.domain;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("Khong tim thay nguoi dung: " + id);
    }
}
