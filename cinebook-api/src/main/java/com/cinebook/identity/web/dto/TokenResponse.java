package com.cinebook.identity.web.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {
}
