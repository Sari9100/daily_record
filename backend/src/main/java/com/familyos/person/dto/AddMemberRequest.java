package com.familyos.person.dto;

import com.familyos.common.domain.FamilyRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/** POST /api/v1/family/members (PARENT) — 계정 없는 구성원(주로 자녀) 추가. */
public record AddMemberRequest(
        @NotBlank String name,
        @NotNull FamilyRole role,
        @Nullable LocalDate birthDate,
        @Nullable String timezone
) {
}
