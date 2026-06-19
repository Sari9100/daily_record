package com.familyos.person.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/v1/family/members/{personId}/account (PARENT) — 기존 Person 에 로그인 계정 부여. */
public record CreateAccountRequest(
        @NotBlank @Size(min = 3, max = 100) String loginId,
        @NotBlank @Size(min = 8, max = 100) String password
) {
}
