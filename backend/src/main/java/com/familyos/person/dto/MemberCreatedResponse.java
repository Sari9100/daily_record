package com.familyos.person.dto;

import com.familyos.common.domain.FamilyRole;

/** 구성원 추가 결과. */
public record MemberCreatedResponse(
        Long personId,
        String name,
        FamilyRole role,
        boolean hasAccount
) {
}
