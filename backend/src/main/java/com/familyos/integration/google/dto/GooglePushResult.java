package com.familyos.integration.google.dto;

/** 우리→구글 전송 결과. */
public record GooglePushResult(
        int inserted,
        int updated
) {
}
