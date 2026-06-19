package com.familyos.diary.dto;

import com.familyos.common.domain.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 일상기록 생성/수정 요청.
 *
 * <p>recordedOn(날짜) 필수 — 타임라인 날짜축. recordedAt(시각)은 선택(없으면 서버 작성시각).
 * collectionId·subjectPersonIds·tagIds 는 alive 검증 후 연결.
 */
public record DiaryRequest(
        @Nullable String title,
        @NotBlank String content,
        @NotNull Visibility visibility,
        @NotNull LocalDate recordedOn,
        @Nullable Instant recordedAt,
        @Nullable Long collectionId,
        @Nullable List<Long> subjectPersonIds,
        @Nullable List<Long> tagIds
) {

    public List<Long> subjectsOrEmpty() {
        return subjectPersonIds == null ? List.of() : subjectPersonIds;
    }

    public List<Long> tagIdsOrEmpty() {
        return tagIds == null ? List.of() : tagIds;
    }
}
