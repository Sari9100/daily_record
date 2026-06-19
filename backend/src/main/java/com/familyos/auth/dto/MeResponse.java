package com.familyos.auth.dto;

import com.familyos.common.domain.FamilyRole;

/** GET /api/v1/auth/me — 현재 토큰 기준 본인 정보. */
public record MeResponse(
        PersonDto person,
        FamilyDto family,
        FamilyRole role
) {

    public record PersonDto(Long id, String name) {
    }

    public record FamilyDto(Long id, String name) {
    }
}
