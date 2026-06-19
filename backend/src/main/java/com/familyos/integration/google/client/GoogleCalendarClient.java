package com.familyos.integration.google.client;

import com.familyos.common.error.BusinessException;
import com.familyos.integration.google.dto.GoogleEvent;
import com.familyos.integration.google.dto.GoogleEventWrite;
import com.familyos.integration.google.dto.GoogleEventsResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 구글 캘린더 events.list 호출 (RestClient). Phase 1 읽기.
 *
 * <p>증분 동기화는 syncToken + showDeleted=true(취소 이벤트 수신). syncToken 만료(410)는
 * {@link SyncTokenExpiredException} 으로 변환 → 전체 재동기화 폴백.
 */
@Component
public class GoogleCalendarClient {

    private static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/{calendarId}/events";
    private static final String EVENT_URL = EVENTS_URL + "/{eventId}";
    private static final int MAX_RESULTS = 250;

    private final RestClient restClient = RestClient.create();

    /**
     * @param syncToken  null 이면 전체 동기화, 있으면 증분
     * @param pageToken  페이지네이션 토큰(null=첫 페이지)
     * @param showDeleted 증분 시 true(취소 이벤트 포함)
     */
    public GoogleEventsResponse listEvents(String accessToken, String calendarId,
                                           @Nullable String syncToken, @Nullable String pageToken,
                                           boolean showDeleted) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(EVENTS_URL)
                .queryParam("maxResults", MAX_RESULTS)
                .queryParam("showDeleted", showDeleted);
        if (syncToken != null) {
            uri.queryParam("syncToken", syncToken);
        }
        if (pageToken != null) {
            uri.queryParam("pageToken", pageToken);
        }
        String url = uri.buildAndExpand(calendarId).toUriString();

        try {
            GoogleEventsResponse res = restClient.get()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .body(GoogleEventsResponse.class);
            if (res == null) {
                throw new BusinessException("구글 캘린더 응답이 비었습니다.");
            }
            return res;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 410) {
                throw new SyncTokenExpiredException();
            }
            throw new BusinessException("구글 캘린더 조회 실패: " + e.getStatusCode().value());
        }
    }

    /** 우리→구글 생성. 응답의 id 를 google_event_id 로 저장. */
    public GoogleEvent insertEvent(String accessToken, String calendarId, GoogleEventWrite body) {
        String url = UriComponentsBuilder.fromUriString(EVENTS_URL).buildAndExpand(calendarId).toUriString();
        try {
            GoogleEvent res = restClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GoogleEvent.class);
            if (res == null || res.id() == null) {
                throw new BusinessException("구글 이벤트 생성 응답이 비었습니다.");
            }
            return res;
        } catch (RestClientResponseException e) {
            throw new BusinessException("구글 이벤트 생성 실패: " + e.getStatusCode().value());
        }
    }

    /** 우리→구글 수정(google_event_id 로). */
    public void updateEvent(String accessToken, String calendarId, String eventId, GoogleEventWrite body) {
        String url = UriComponentsBuilder.fromUriString(EVENT_URL).buildAndExpand(calendarId, eventId).toUriString();
        try {
            restClient.put()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new BusinessException("구글 이벤트 수정 실패: " + e.getStatusCode().value());
        }
    }
}
