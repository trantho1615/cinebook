package com.cinebook.catalog.domain.showtime;

public class ShowtimeOverlapException extends RuntimeException {

    public ShowtimeOverlapException() {
        super("Phong nay da co suat chieu khac trong khung gio do");
    }
}
