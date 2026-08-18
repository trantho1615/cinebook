package com.cinebook.booking.domain;

public class NoAdjacentSeatsException extends RuntimeException {

    public NoAdjacentSeatsException(int count) {
        super("Khong con " + count + " ghe trong lien nhau trong suat chieu nay");
    }
}
