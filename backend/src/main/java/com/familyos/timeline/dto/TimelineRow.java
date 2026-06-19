package com.familyos.timeline.dto;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 타임라인 평면 행(매퍼 결과). 4개 도메인을 UNION 으로 정규화.
 *
 * @param type        SCHEDULE / DIARY / TRANSACTION / PHOTO
 * @param groupDate   날짜축 그룹 키(로컬 날짜)
 * @param sortInstant 정렬용 시각(UTC). 종일 일정/시각없는 사진은 null → 그룹 맨 위
 * @param title       일정/일상기록 제목(거래·사진은 null)
 * @param amount      거래 금액(그 외 null)
 */
public record TimelineRow(
        String type,
        Long id,
        LocalDate groupDate,
        @Nullable Instant sortInstant,
        @Nullable String title,
        @Nullable BigDecimal amount
) {
}
