package com.cinebook.identity.web.dto;

public record TokenResponse(String accessToken, long expiresInSeconds) {
}
