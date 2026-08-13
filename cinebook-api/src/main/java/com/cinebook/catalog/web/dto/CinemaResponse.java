package com.cinebook.catalog.web.dto;

import com.cinebook.catalog.domain.venue.Cinema;

import java.util.UUID;

public record CinemaResponse(UUID id, String name, String address,
                             String district, String city, String status) {

    public static CinemaResponse from(Cinema cinema) {
        return new CinemaResponse(
                cinema.getId(), cinema.getName(), cinema.getAddress(),
                cinema.getDistrict(), cinema.getCity(), cinema.getStatus().name());
    }
}
