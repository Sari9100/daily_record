package com.familyos.schedule.dto;

import com.familyos.common.domain.Visibility;
import com.familyos.schedule.entity.ScheduleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 일정 생성/수정 요청.
 *
 * <p>이원화: allDay=true → startDate(필수)/endDate, started/ended 금지. allDay=false → startedAt(필수)/endedAt, date 금지.
 * google 동기화 필드(googleEventId/syncStatus)·collectionId 는 받지 않는다(서버·동기화 모듈 / collection 도메인 대기).
 */
public record ScheduleRequest(
        @NotBlank String title,
        @Nullable String description,
        @Nullable String location,
        @Nullable Instant startedAt,
        @Nullable Instant endedAt,
        @Nullable LocalDate startDate,
        @Nullable LocalDate endDate,
        boolean allDay,
        @NotNull Visibility visibility,
        @NotNull ScheduleType scheduleType,
        @Nullable String recurrenceRule,
        @Nullable Long collectionId,
        @Nullable List<Long> subjectPersonIds,
        @Nullable List<Long> participantPersonIds
) {

    public List<Long> subjectsOrEmpty() {
        return subjectPersonIds == null ? List.of() : subjectPersonIds;
    }

    public List<Long> participantsOrEmpty() {
        return participantPersonIds == null ? List.of() : participantPersonIds;
    }
}
