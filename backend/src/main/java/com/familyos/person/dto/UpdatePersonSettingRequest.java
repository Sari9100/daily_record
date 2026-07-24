package com.familyos.person.dto;

import com.familyos.person.entity.DetailLevel;
import com.familyos.person.entity.ViewMode;
import jakarta.validation.constraints.NotNull;

/** PUT /api/v1/me/settings — 항상 설정 전체를 보낸다(부분 업데이트 아님). */
public record UpdatePersonSettingRequest(
        @NotNull DetailLevel sharedScheduleDetailLevel,
        @NotNull ViewMode ledgerDefaultView,
        @NotNull ViewMode scheduleDefaultView,
        @NotNull ViewMode diaryDefaultView
) {
}
