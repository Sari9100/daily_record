package com.familyos.integration.google.dto;

/** 동기화 결과 요약. */
public record GoogleSyncResult(
        boolean fullSync,
        int created,
        int updated,
        int deleted
) {
}
