package com.cinebook.identity.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Gioi han tren cua mat khau la 72 vi BCrypt am tham cat bo moi ky tu sau byte thu 72.
 * Khong chan o day thi hai mat khau dai khac nhau co the cung dang nhap duoc.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 10, max = 72) String password,
        @NotBlank @Size(max = 150) String fullName,
        @Size(max = 20) String phone) {
}
