package com.example.messenger.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static TokenResponse of(String access, String refresh, long expiresInSec) {
        return new TokenResponse(access, refresh, "Bearer", expiresInSec);
    }
}
