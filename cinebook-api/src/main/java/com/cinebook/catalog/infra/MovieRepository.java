package com.cinebook.catalog.infra;

import com.cinebook.catalog.domain.movie.Movie;
import com.cinebook.catalog.domain.movie.MovieStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MovieRepository extends JpaRepository<Movie, UUID> {

    List<Movie> findByStatusOrderByReleaseDateDesc(MovieStatus status);

    List<Movie> findAllByOrderByReleaseDateDesc();
}
