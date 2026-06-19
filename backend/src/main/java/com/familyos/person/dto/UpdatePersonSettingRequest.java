package com.familyos.person.dto;

import com.familyos.person.entity.DetailLevel;
import jakarta.validation.constraints.NotNull;

/** PUT /api/v1/me/settings. */
public record UpdatePersonSettingRequest(
        @NotNull DetailLevel sharedScheduleDetailLevel
) {
}
