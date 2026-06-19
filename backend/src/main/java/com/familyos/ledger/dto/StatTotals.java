package com.familyos.ledger.dto;

import java.math.BigDecimal;

/** 통계 합계(매퍼 결과). TRANSFER 제외. */
public record StatTotals(
        BigDecimal totalIncome,
        BigDecimal totalExpense
) {
}
