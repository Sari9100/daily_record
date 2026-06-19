package com.familyos.collection.dto;

import java.math.BigDecimal;

/**
 * 묶음 요약 (GET /collections/{id}/summary). 연결된 거래·일정·기록·사진 집계.
 * TRANSFER 는 건수/합계에서 제외(가계부 집계 일관성).
 */
public record CollectionSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        int transactionCount,
        int scheduleCount,
        int diaryCount,
        int photoCount
) {
}
