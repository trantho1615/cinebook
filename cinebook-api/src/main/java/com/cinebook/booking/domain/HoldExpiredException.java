package com.cinebook.booking.domain;

public class HoldExpiredException extends RuntimeException {

    public HoldExpiredException() {
        super("Thoi gian giu ghe da het, vui long chon lai");
    }
}
