package com.familyos.diary.dto;

import com.familyos.common.domain.Visibility;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 일상기록 응답.
 *
 * <p>photos 는 photo 도메인 구현 시 채워진다(현재 빈 목록). 사진 URL 은 인증 후 서명 URL 로 서빙.
 */
public record DiaryResponse(
        Long id,
        @Nullable String title,
        String content,
        Visibility visibility,
        Instant recordedAt,
        LocalDate recordedOn,
        List<Long> subjects,
        @Nullable Long collectionId,
        List<String> tags,
        List<PhotoRef> photos
) {

    /** photo 도메인 구현 시 url(서명)·메타로 채움. */
    public record PhotoRef(Long id, @Nullable String url, @Nullable Integer width,
                           @Nullable Integer height, @Nullable Instant takenAt) {
    }
}
