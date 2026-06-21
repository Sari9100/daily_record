package com.familyos.integration.telegram.dto;

import com.familyos.schedule.dto.ScheduleRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Hermes 일정 생성/수정 요청. 행동 주체는 telegramUserId 로 결정(→ Person),
 * 일정 본문은 기존 {@link ScheduleRequest} 를 그대로 재사용한다.
 */
public record TelegramScheduleRequest(
        @NotNull Long telegramUserId,
        @NotNull @Valid ScheduleRequest schedule
) {
}
