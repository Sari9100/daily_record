package com.familyos.integration.google.dto;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 구글 이벤트를 우리 Schedule 필드로 정규화한 중간 결과 (시각/날짜 이원화 규칙 적용).
 * 종일이면 startDate/endDate, 시점이면 startedAt/endedAt 만 채워진다.
 */
public record MappedSchedule(
        String title,
        @Nullable String description,
        @Nullable String location,
        @Nullable Instant startedAt,
        @Nullable Instant endedAt,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        boolean allDay,
        @Nullable String recurrenceRule
) {
}
