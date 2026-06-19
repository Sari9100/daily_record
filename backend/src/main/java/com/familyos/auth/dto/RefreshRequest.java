package com.familyos.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/refresh */
public record RefreshRequest(
        @NotBlank String refreshToken
) {
}
