package com.familyos.auth.dto;

/** 토큰 재발급 응답(회전된 access/refresh). */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
