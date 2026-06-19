package com.familyos.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/logout — 제출된 리프레시 토큰의 jti 를 폐기. */
public record LogoutRequest(
        @NotBlank String refreshToken
) {
}
