package com.cinebook.catalog.web;

import com.cinebook.catalog.domain.movie.MovieNotFoundException;
import com.cinebook.shared.web.ApiError;
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
}
