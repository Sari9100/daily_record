package com.familyos.timeline.dto;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 통합 타임라인 응답 — 날짜별 그룹, 각 항목에 type 태그. */
public record TimelineResponse(
        List<TimelineDay> days
) {

    public record TimelineDay(
            LocalDate date,
            List<TimelineItem> items
    ) {
    }

    /**
     * @param time         로컬 "HH:mm" (시각 없는 항목은 null)
     * @param thumbnailUrl PHOTO 의 서명 썸네일 URL (그 외 null)
     */
    public record TimelineItem(
            String type,
            Long id,
            @Nullable String time,
            @Nullable String title,
            @Nullable BigDecimal amount,
            @Nullable String thumbnailUrl
    ) {
    }
}
