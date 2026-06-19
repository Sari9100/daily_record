package com.familyos.ledger.dto;

import com.familyos.common.domain.Visibility;
import com.familyos.ledger.entity.SettlementStatus;
import com.familyos.ledger.entity.TransactionType;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 거래 응답.
 *
 * <p>결제 계좌(source/target)는 <b>소유자 본인에게만 노출</b> — 타인 개인계좌는 마스킹(null).
 * tags 는 tag 도메인 구현 전까지 빈 목록.
 */
public record TransactionResponse(
        Long id,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        @Nullable CategoryRef category,
        @Nullable AccountRef sourceAccount,
        @Nullable AccountRef targetAccount,
        @Nullable Long subjectPersonId,
        Visibility visibility,
        SettlementStatus settlementStatus,
        Instant occurredAt,
        @Nullable String memo,
        @Nullable Long collectionId,
        List<String> tags
) {

    public record AccountRef(Long id, String name) {
    }

    public record CategoryRef(Long id, String name) {
    }
}
