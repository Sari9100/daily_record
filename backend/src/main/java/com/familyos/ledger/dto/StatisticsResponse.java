package com.familyos.ledger.dto;

import java.math.BigDecimal;
import java.util.List;

/** GET /api/v1/transactions/statistics 응답. TRANSFER 는 모든 집계에서 제외. */
public record StatisticsResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        List<CategoryStat> byCategory,
        List<MonthStat> byMonth
) {
}
