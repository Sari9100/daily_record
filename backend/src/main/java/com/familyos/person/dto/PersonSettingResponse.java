package com.familyos.person.dto;

import com.familyos.person.entity.DetailLevel;
import com.familyos.person.entity.ViewMode;

/** GET/PUT /api/v1/me/settings 응답. */
public record PersonSettingResponse(
        DetailLevel sharedScheduleDetailLevel,
        ViewMode ledgerDefaultView,
        ViewMode scheduleDefaultView,
        ViewMode diaryDefaultView
) {
}
