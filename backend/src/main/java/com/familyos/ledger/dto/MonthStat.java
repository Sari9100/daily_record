package com.familyos.ledger.dto;

import java.math.BigDecimal;

/** 월별 집계 항목. month 는 뷰어 timezone 기준 로컬 월("YYYY-MM"). TRANSFER 제외. */
public record MonthStat(
        String month,
        BigDecimal income,
        BigDecimal expense
) {
}
