package com.familyos.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** POST /photos/presign — 업로드 의도 등록. photoHash 는 SHA-256 hex(중복 제거 키). */
public record PresignRequest(
        @NotBlank String originalFilename,
        @NotBlank String mimeType,
        @NotNull @Positive Long fileSize,
        @NotBlank String photoHash,
        @Nullable Long diaryId,
        @Nullable Instant takenAt
) {
}
