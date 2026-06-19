package com.familyos.integration.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** 구글 캘린더 이벤트(필요 필드만). status=cancelled → 삭제. start/end 는 dateTime(시점) 또는 date(종일). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleEvent(
        String id,
        @Nullable String status,
        @Nullable String summary,
        @Nullable String description,
        @Nullable String location,
        @Nullable GoogleEventDateTime start,
        @Nullable GoogleEventDateTime end,
        @Nullable List<String> recurrence
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoogleEventDateTime(
            @JsonProperty("dateTime") @Nullable String dateTime,  // RFC3339 (시점, offset 포함)
            @Nullable String date,                                 // YYYY-MM-DD (종일)
            @JsonProperty("timeZone") @Nullable String timeZone
    ) {
    }
}
