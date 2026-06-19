package com.familyos.auth.dto;

import com.familyos.common.domain.FamilyRole;

/** 로그인 응답. accessToken/refreshToken + 본인 요약. */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        PersonSummary person
) {

    public record PersonSummary(
            Long id,
            String name,
            Long familyId,
            FamilyRole role
    ) {
    }
}
