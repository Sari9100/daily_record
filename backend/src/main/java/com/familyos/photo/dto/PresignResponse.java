package com.familyos.photo.dto;

import org.jspecify.annotations.Nullable;

/**
 * presign 응답.
 * @param uploadUrl 바이너리를 PUT 할 서버 경로. 중복(기존 사진 재사용) 시 null(업로드 불필요).
 * @param duplicated photoHash 중복으로 기존 사진을 반환했는지
 */
public record PresignResponse(
        Long photoId,
        @Nullable String uploadUrl,
        String storageKey,
        boolean duplicated
) {
}
