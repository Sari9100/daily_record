package com.familyos.auth.service;

/** 발급된 토큰 한 쌍(내부 전달용). */
public record IssuedTokens(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
