package com.cinebook.identity.web;

import com.cinebook.identity.domain.EmailAlreadyUsedException;
import com.cinebook.identity.domain.ForbiddenException;
import com.cinebook.identity.domain.InvalidCredentialsException;
import com.cinebook.identity.domain.InvalidRefreshTokenException;
import com.cinebook.identity.domain.TooManyLoginAttemptsException;
import com.cinebook.identity.domain.UserNotFoundException;
import com.cinebook.shared.web.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_REFRESH_TOKEN", e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("USER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ApiError> handleTooManyAttempts(TooManyLoginAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(e.getRetryAfter().toSeconds()))
                .body(new ApiError("TOO_MANY_LOGIN_ATTEMPTS", e.getMessage()));
    }

    /**
     * @PreAuthorize nem AccessDeniedException tu tang AOP, khong di qua
     * accessDeniedHandler cua filter chain — nen phai bat rieng o day.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "Ban khong co quyen truy cap tai nguyen nay"));
    }
}
