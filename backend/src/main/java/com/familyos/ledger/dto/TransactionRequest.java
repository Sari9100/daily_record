package com.familyos.ledger.dto;

import com.familyos.common.domain.Visibility;
import com.familyos.ledger.entity.TransactionSource;
import com.familyos.ledger.entity.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 거래 생성/수정 요청.
 *
 * <p>서버 결정 값(family_id, settlement_status)은 받지 않는다. visibility 는 PRIVATE/PARENTS/FAMILY 만(서비스 검증).
 * collectionId·tagIds 는 collection/tag 도메인 구현 시 추가(현재 미지원 — alive 검증 불가한 참조를 받지 않음).
 */
public record TransactionRequest(
        @NotNull TransactionType transactionType,
        @NotNull @Positive BigDecimal amount,
        @Nullable String currency,
        @Nullable Long sourceAccountId,
        @Nullable Long targetAccountId,
        @Nullable Long categoryId,
        @Nullable Long subjectPersonId,
        @NotNull Visibility visibility,
        @NotNull Instant occurredAt,
        @Nullable String memo,
        @Nullable TransactionSource source
) {
}
