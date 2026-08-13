package com.cinebook.catalog.web;

import com.cinebook.catalog.domain.movie.Movie;
import com.cinebook.catalog.domain.movie.MovieNotFoundException;
import com.cinebook.catalog.domain.movie.MovieStatus;
import com.cinebook.catalog.infra.MovieRepository;
import com.cinebook.catalog.web.dto.CreateMovieRequest;
import com.cinebook.catalog.web.dto.MovieResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class MovieController {

    private final MovieRepository movies;

    public MovieController(MovieRepository movies) {
        this.movies = movies;
    }

    @GetMapping("/movies")
    public List<MovieResponse> list(@RequestParam(required = false) MovieStatus status) {
        List<Movie> found = (status == null)
                ? movies.findAllByOrderByReleaseDateDesc()
                : movies.findByStatusOrderByReleaseDateDesc(status);
        return found.stream().map(MovieResponse::from).toList();
    }

    @GetMapping("/movies/{id}")
    public MovieResponse get(@PathVariable UUID id) {
        return movies.findById(id)
                .map(MovieResponse::from)
                .orElseThrow(() -> new MovieNotFoundException(id));
    }

    @PostMapping("/admin/movies")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse create(@Valid @RequestBody CreateMovieRequest request) {
        Movie movie = Movie.create(
                request.title(),
                request.originalTitle(),
                request.description(),
                request.durationMin(),
                request.genres(),
                request.ageRating(),
                request.posterUrl(),
                request.trailerUrl(),
                request.releaseDate());
        return MovieResponse.from(movies.save(movie));
    }

    @PatchMapping("/admin/movies/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public MovieResponse changeStatus(@PathVariable UUID id, @RequestParam MovieStatus status) {
        Movie movie = movies.findById(id).orElseThrow(() -> new MovieNotFoundException(id));
        movie.changeStatus(status);
        return MovieResponse.from(movies.save(movie));
    }
}
