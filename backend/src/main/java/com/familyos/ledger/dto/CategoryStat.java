package com.familyos.ledger.dto;

import com.familyos.ledger.entity.CategoryType;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * 카테고리별 집계 항목.
 * 삭제된 카테고리도 과거 거래 집계에 포함된다(LEFT JOIN, deleted_at 필터 안 함) — name 은 보존된 값.
 */
public record CategoryStat(
        Long categoryId,
        @Nullable String name,
        @Nullable CategoryType type,
        BigDecimal amount
) {
}
