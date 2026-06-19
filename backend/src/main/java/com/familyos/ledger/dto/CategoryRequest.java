package com.familyos.ledger.dto;

import com.familyos.ledger.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * 카테고리 생성 요청. isSystem 은 서버가 false 로 고정(클라가 시스템 카테고리 생성 불가).
 * parentId 는 선택(계층), 지정 시 alive 검증.
 */
public record CategoryRequest(
        @NotBlank String name,
        @NotNull CategoryType type,
        @Nullable Long parentId
) {
}
