package com.familyos.collection.dto;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** 묶음 생성/수정. coverPhotoId 는 photo 도메인 구현 시 별도 설정. */
public record CollectionRequest(
        @NotBlank String name,
        @Nullable String description,
        @Nullable Instant startedAt,
        @Nullable Instant endedAt,
        @Nullable List<Long> tagIds
) {
    public List<Long> tagIdsOrEmpty() {
        return tagIds == null ? List.of() : tagIds;
    }
}
