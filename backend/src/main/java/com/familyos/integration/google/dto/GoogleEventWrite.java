package com.familyos.integration.google.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** 우리→구글 전송용 이벤트 본문. null 필드는 직렬화 생략(구글이 무시/유지). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleEventWrite(
        String summary,
        @Nullable String description,
        @Nullable String location,
        EventDateWrite start,
        EventDateWrite end,
        @Nullable List<String> recurrence
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventDateWrite(
            @JsonProperty("dateTime") @Nullable String dateTime,  // 시점: RFC3339(UTC)
            @Nullable String date                                  // 종일: YYYY-MM-DD
    ) {
    }
}
