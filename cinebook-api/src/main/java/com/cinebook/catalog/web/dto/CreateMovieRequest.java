package com.cinebook.catalog.web.dto;

import com.cinebook.catalog.domain.movie.AgeRating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

public record CreateMovieRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String originalTitle,
        String description,
        @Min(1) @Max(600) int durationMin,
        @NotEmpty Set<@NotBlank @Size(max = 50) String> genres,
        @NotNull AgeRating ageRating,
        @Size(max = 500) String posterUrl,
        @Size(max = 500) String trailerUrl,
        @NotNull LocalDate releaseDate) {
}
