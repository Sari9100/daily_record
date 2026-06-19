package com.familyos.schedule.dto;

import com.familyos.common.domain.Visibility;
import com.familyos.schedule.entity.ScheduleType;
import com.familyos.schedule.entity.SyncStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 일정 응답.
 *
 * <p>타인의 SHARED_PERSONAL 일정은 뷰어 PersonSetting=SUMMARY 면 title="바쁨", description/location 마스킹.
 */
public record ScheduleResponse(
        Long id,
        String title,
        @Nullable String description,
        @Nullable String location,
        @Nullable Instant startedAt,
        @Nullable Instant endedAt,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        boolean allDay,
        Visibility visibility,
        ScheduleType scheduleType,
        boolean isDone,
        @Nullable String recurrenceRule,
        List<Long> subjects,
        List<Long> participants,
        @Nullable Long collectionId,
        @Nullable SyncStatus syncStatus
) {
}
