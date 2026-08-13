package com.cinebook.catalog.domain.movie;

import java.util.UUID;

public class MovieNotFoundException extends RuntimeException {

    public MovieNotFoundException(UUID id) {
        super("Khong tim thay phim: " + id);
    }
}
