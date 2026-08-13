package com.cinebook.catalog.domain.venue;

import java.util.UUID;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(UUID id) {
        super("Khong tim thay phong chieu: " + id);
    }
}
