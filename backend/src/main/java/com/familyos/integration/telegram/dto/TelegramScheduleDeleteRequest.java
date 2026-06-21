package com.familyos.integration.telegram.dto;

import jakarta.validation.constraints.NotNull;

/** Hermes 일정 삭제 요청. 행동 주체는 telegramUserId 로 결정. */
public record TelegramScheduleDeleteRequest(
        @NotNull Long telegramUserId
) {
}
