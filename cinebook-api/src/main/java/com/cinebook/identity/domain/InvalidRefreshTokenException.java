package com.cinebook.identity.domain;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token khong hop le hoac da het hieu luc");
    }
}
