package com.familyos.photo.dto;

import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

/** POST /photos/{id}/complete — 업로드 확정 + 메타. */
public record CompleteRequest(
        @Positive @Nullable Integer width,
        @Positive @Nullable Integer height
) {
}
