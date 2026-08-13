package com.cinebook.catalog.domain.showtime;

import java.util.UUID;

public class ShowtimeNotFoundException extends RuntimeException {

    public ShowtimeNotFoundException(UUID id) {
        super("Khong tim thay suat chieu: " + id);
    }
}
