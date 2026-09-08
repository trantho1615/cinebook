package com.cinebook.shared.web;

import jakarta.validation.ConstraintViolationException;
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

    /**
     * Rang buoc tren THAM SO cua controller (@RequestParam kem @Min/@Max tren lop mang
     * @Validated) nem ConstraintViolationException, khong phai MethodArgumentNotValidException
     * o tren — cai do chi cho @Valid @RequestBody.
     *
     * Thieu handler nay thi hau qua khong phai 500 nhu ta tuong, ma la 401: exception khong
     * ai bat se roi vao dispatch toi /error, ma /error khong nam trong danh sach permitAll
     * cua SecurityConfig nen bi chan lai. Mot tham so sai kieu tra ve "can dang nhap" la
     * thong bao danh lac huong hoan toan.
     *
     * Phat hien o Milestone 10 khi them @Min/@Max cho /showtimes. Anh huong ca
     * /showtimes/{id}/seats/suggest?count=... vi endpoint do cung dung cung co che.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleParamValidation(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_FAILED", detail));
    }
}
