package com.familyos.integration.google.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** events.list 응답. nextPageToken(페이지네이션) / nextSyncToken(증분 동기화 토큰, 마지막 페이지에만). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleEventsResponse(
        @Nullable List<GoogleEvent> items,
        @JsonProperty("nextPageToken") @Nullable String nextPageToken,
        @JsonProperty("nextSyncToken") @Nullable String nextSyncToken
) {
    public List<GoogleEvent> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }
}
