package com.cinebook.catalog.web.dto;

import com.cinebook.catalog.domain.movie.Movie;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record MovieResponse(
        UUID id,
        String title,
        String originalTitle,
        String description,
        int durationMin,
        Set<String> genres,
        String ageRating,
        String posterUrl,
        String trailerUrl,
        LocalDate releaseDate,
        String status) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getOriginalTitle(),
                movie.getDescription(),
                movie.getDurationMin(),
                movie.getGenres(),
                movie.getAgeRating().name(),
                movie.getPosterUrl(),
                movie.getTrailerUrl(),
                movie.getReleaseDate(),
                movie.getStatus().name());
    }
}
