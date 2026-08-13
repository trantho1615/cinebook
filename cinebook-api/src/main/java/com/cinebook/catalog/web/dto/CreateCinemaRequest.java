package com.cinebook.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCinemaRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 100) String district,
        @NotBlank @Size(max = 100) String city) {
}
