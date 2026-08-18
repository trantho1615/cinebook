package com.cinebook.booking.domain;

import java.util.UUID;

/**
 * Suat chieu khong ton tai, nhin tu goc do booking.
 *
 * Khong tai su dung catalog.domain.showtime.ShowtimeNotFoundException: no nam trong
 * domain cua module khac, va ModuleBoundaryTest cam booking cham vao do.
 */
public class ShowtimeUnknownException extends RuntimeException {

    public ShowtimeUnknownException(UUID id) {
        super("Khong tim thay suat chieu: " + id);
    }
}
