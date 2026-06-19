package com.familyos.collection.dto;

import com.familyos.collection.entity.Collection;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record CollectionResponse(
        Long id,
        String name,
        @Nullable String description,
        @Nullable Long coverPhotoId,
        @Nullable Instant startedAt,
        @Nullable Instant endedAt
) {
    public static CollectionResponse from(Collection c) {
        return new CollectionResponse(
                c.getId(), c.getName(), c.getDescription(), c.getCoverPhotoId(),
                c.getStartedAt(), c.getEndedAt());
    }
}
