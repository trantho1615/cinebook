package com.cinebook.identity.web;

import com.cinebook.identity.domain.EmailAlreadyUsedException;
import com.cinebook.identity.domain.InvalidCredentialsException;
import com.cinebook.shared.web.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Anh xa exception cua rieng module identity sang ma HTTP.
 *
 * Nam trong identity.web chu khong nam o shared: chi module so huu exception moi
 * biet no tuong ung voi ma nao, va de shared import domain cua tung module se
 * vi pham luat ranh gioi ma ModuleBoundaryTest ep.
 */
@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyUsed(EmailAlreadyUsedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("EMAIL_ALREADY_USED", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_CREDENTIALS", e.getMessage()));
    }
}
