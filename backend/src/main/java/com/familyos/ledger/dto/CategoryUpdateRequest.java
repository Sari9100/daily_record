package com.familyos.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

/** 카테고리 수정. type 은 변경하지 않는다(통계 일관성). */
public record CategoryUpdateRequest(
        @NotBlank String name,
        @Nullable Long parentId
) {
}
