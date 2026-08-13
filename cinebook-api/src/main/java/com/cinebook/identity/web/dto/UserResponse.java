package com.cinebook.identity.web.dto;

import com.cinebook.identity.domain.User;

import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, String role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
