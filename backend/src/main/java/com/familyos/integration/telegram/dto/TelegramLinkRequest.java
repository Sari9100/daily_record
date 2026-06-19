package com.familyos.integration.telegram.dto;

import jakarta.validation.constraints.NotNull;

/** 로그인 사용자가 본인 텔레그램 계정을 연결. */
public record TelegramLinkRequest(
        @NotNull Long telegramUserId
) {
}
