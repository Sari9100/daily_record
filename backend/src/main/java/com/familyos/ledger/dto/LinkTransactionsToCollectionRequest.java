package com.familyos.ledger.dto;

import jakarta.validation.constraints.NotEmpty;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * POST /api/v1/transactions/link-collection — 가계부 다중선택 → 묶음 일괄연결.
 * collectionId 는 null 허용(연결 해제 용도로도 재사용 가능).
 */
public record LinkTransactionsToCollectionRequest(
        @NotEmpty List<Long> transactionIds,
        @Nullable Long collectionId
) {
}
