package com.cinebook.shared.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Chi xu ly cac exception cua framework, dung chung cho moi module.
 *
 * Exception nghiep vu cua tung module duoc anh xa boi @RestControllerAdvice rieng
 * cua module do (vi du IdentityExceptionHandler). Neu gom tat ca vao day thi lop nay
 * se phai import domain cua ca nam module — vua vi pham luat ranh gioi ma
 * ModuleBoundaryTest ep, vua bien no thanh mot god class.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", detail));
    }
}
