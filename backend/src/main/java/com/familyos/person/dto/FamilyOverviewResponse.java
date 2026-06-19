package com.familyos.person.dto;

import com.familyos.common.domain.FamilyRole;

import java.util.List;

/** GET /api/v1/family — 현재 가족 정보 + 구성원 목록. */
public record FamilyOverviewResponse(
        FamilyDto family,
        List<MemberDto> members
) {

    public record FamilyDto(Long id, String name) {
    }

    public record MemberDto(
            Long personId,
            String name,
            FamilyRole role,
            boolean hasAccount
    ) {
    }
}
