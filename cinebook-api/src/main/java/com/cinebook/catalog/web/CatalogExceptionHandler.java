package com.cinebook.catalog.web;

import com.cinebook.catalog.domain.movie.MovieNotFoundException;
import com.cinebook.catalog.domain.venue.CinemaNotFoundException;
import com.cinebook.catalog.domain.venue.RoomNotFoundException;
import com.cinebook.shared.web.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Anh xa exception cua rieng module catalog sang ma HTTP.
 *
 * Khong gop vao shared/web/ApiExceptionHandler: lop o shared ma import
 * catalog.domain se bi ModuleBoundaryTest chan, va gom het vao mot cho se bien
 * no thanh god class import domain cua ca nam module.
 */
@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(MovieNotFoundException.class)
    public ResponseEntity<ApiError> handleMovieNotFound(MovieNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("MOVIE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(CinemaNotFoundException.class)
    public ResponseEntity<ApiError> handleCinemaNotFound(CinemaNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("CINEMA_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ApiError> handleRoomNotFound(RoomNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("ROOM_NOT_FOUND", e.getMessage()));
    }

    /**
     * Rang buoc UNIQUE va CHECK cua database la lop bao ve cuoi cung. Khi no ban loi,
     * doi thanh 409 thay vi de bung ra 500 — day la xung dot du lieu, khong phai loi he thong.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DATA_CONFLICT", "Du lieu vi pham rang buoc cua he thong"));
    }
}
