package com.cinebook.catalog.web;

import com.cinebook.catalog.api.ShowtimeDetail;
import com.cinebook.catalog.api.ShowtimeQuery;
import com.cinebook.catalog.domain.movie.Movie;
import com.cinebook.catalog.domain.movie.MovieNotFoundException;
import com.cinebook.catalog.domain.showtime.Showtime;
import com.cinebook.catalog.domain.showtime.ShowtimeNotFoundException;
import com.cinebook.catalog.domain.showtime.ShowtimeOverlapException;
import com.cinebook.catalog.domain.venue.RoomNotFoundException;
import com.cinebook.catalog.infra.MovieRepository;
import com.cinebook.catalog.infra.RoomRepository;
import com.cinebook.catalog.infra.ShowtimeRepository;
import com.cinebook.catalog.web.dto.CreateShowtimeRequest;
import com.cinebook.catalog.web.dto.ShowtimeResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ShowtimeController {

    private final ShowtimeRepository showtimes;
    private final MovieRepository movies;
    private final RoomRepository rooms;
    private final ShowtimeQuery showtimeQuery;

    public ShowtimeController(ShowtimeRepository showtimes, MovieRepository movies,
                              RoomRepository rooms, ShowtimeQuery showtimeQuery) {
        this.showtimes = showtimes;
        this.movies = movies;
        this.rooms = rooms;
        this.showtimeQuery = showtimeQuery;
    }

    @GetMapping("/showtimes/{id}")
    public ShowtimeDetail get(@PathVariable UUID id) {
        return showtimeQuery.findDetail(id)
                .orElseThrow(() -> new ShowtimeNotFoundException(id));
    }

    @PostMapping("/admin/showtimes")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ShowtimeResponse create(@Valid @RequestBody CreateShowtimeRequest request) {
        Movie movie = movies.findById(request.movieId())
                .orElseThrow(() -> new MovieNotFoundException(request.movieId()));
        if (!rooms.existsById(request.roomId())) {
            throw new RoomNotFoundException(request.roomId());
        }

        Showtime showtime = Showtime.schedule(
                movie.getId(), request.roomId(), request.startAt(),
                movie.getDurationMin(), request.basePrice());

        try {
            // saveAndFlush chu khong phai save: save chi dua entity vao persistence
            // context va cau INSERT bi hoan toi luc commit — tuc la SAU khi method da
            // return, nam ngoai khoi try/catch nay (spec muc 6.2.1).
            return ShowtimeResponse.from(showtimes.saveAndFlush(showtime));
        } catch (DataIntegrityViolationException e) {
            // Rang buoc no_overlap o database la nguon su that duy nhat ve viec trung lich.
            // Bat o day chi de doi thanh thong bao co nghia cho nguoi dung.
            if (String.valueOf(e.getMessage()).contains("no_overlap")) {
                throw new ShowtimeOverlapException();
            }
            throw e;
        }
    }
}
