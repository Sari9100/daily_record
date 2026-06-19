package com.familyos.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/auth/login */
public record LoginRequest(
        @NotBlank String loginId,
        @NotBlank String password
) {
}
