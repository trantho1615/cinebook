package com.cinebook.catalog.web.dto;

import com.cinebook.catalog.domain.venue.Room;

import java.util.UUID;

public record RoomResponse(UUID id, UUID cinemaId, String name,
                           String roomType, String status, long seatCount) {

    public static RoomResponse from(Room room, long seatCount) {
        return new RoomResponse(
                room.getId(), room.getCinemaId(), room.getName(),
                room.getRoomType().name(), room.getStatus().name(), seatCount);
    }
}
