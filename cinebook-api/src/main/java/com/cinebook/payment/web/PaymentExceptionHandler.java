package com.cinebook.payment.web;

import com.cinebook.payment.domain.PaymentNotAllowedException;
import com.cinebook.shared.web.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentNotAllowedException.class)
    public ResponseEntity<ApiError> handleNotAllowed(PaymentNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("PAYMENT_NOT_ALLOWED", e.getMessage()));
    }
}
