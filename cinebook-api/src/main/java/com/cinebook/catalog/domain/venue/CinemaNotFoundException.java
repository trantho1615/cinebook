package com.cinebook.catalog.domain.venue;

import java.util.UUID;

public class CinemaNotFoundException extends RuntimeException {

    public CinemaNotFoundException(UUID id) {
        super("Khong tim thay rap: " + id);
    }
}
