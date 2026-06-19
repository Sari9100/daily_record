package com.familyos.photo.dto;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 사진 메타 + 서명 조회 URL. storageKey/절대경로는 노출하지 않는다(서명 URL 만).
 */
public record PhotoResponse(
        Long id,
        @Nullable Long diaryId,
        String url,
        @Nullable Integer width,
        @Nullable Integer height,
        @Nullable Instant takenAt,
        @Nullable String mimeType
) {
}
