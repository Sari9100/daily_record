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

import java.util.Map;

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
    private static final String WATCH_URL = EVENTS_URL + "/watch";
    private static final String CHANNELS_STOP_URL = "https://www.googleapis.com/calendar/v3/channels/stop";
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

    /** 우리→구글 삭제. 멱등 — 이미 삭제된 이벤트(404/410)는 성공으로 간주. */
    public void deleteEvent(String accessToken, String calendarId, String eventId) {
        String url = UriComponentsBuilder.fromUriString(EVENT_URL).buildAndExpand(calendarId, eventId).toUriString();
        try {
            restClient.delete()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 404 || code == 410) {
                return; // 이미 구글에서 사라짐 → 멱등 성공
            }
            throw new BusinessException("구글 이벤트 삭제 실패: " + code);
        }
    }

    /**
     * 푸시 채널 등록(events.watch, Phase 3). 변경 발생 시 구글이 {@code address} 로 POST.
     * 응답의 resourceId/expiration 을 저장해 두었다가 만료 전 재등록한다.
     *
     * @param channelId 우리가 생성한 채널 식별자(UUID)
     * @param address   공개 수신 URL(HTTPS, 도메인 인증 필요)
     * @param token     채널 토큰(수신 시 X-Goog-Channel-Token 으로 되돌아옴 → 위조 방지)
     */
    public GoogleWatchResponse watchEvents(String accessToken, String calendarId,
                                           String channelId, String address, String token) {
        String url = UriComponentsBuilder.fromUriString(WATCH_URL).buildAndExpand(calendarId).toUriString();
        Map<String, Object> body = Map.of(
                "id", channelId,
                "type", "web_hook",
                "address", address,
                "token", token);
        try {
            GoogleWatchResponse res = restClient.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GoogleWatchResponse.class);
            if (res == null) {
                throw new BusinessException("구글 watch 응답이 비었습니다.");
            }
            return res;
        } catch (RestClientResponseException e) {
            throw new BusinessException("구글 푸시 채널 등록 실패: " + e.getStatusCode().value());
        }
    }

    /** 푸시 채널 해제(channels.stop). 멱등 — 이미 없는 채널(404)은 성공으로 간주. */
    public void stopChannel(String accessToken, String channelId, String resourceId) {
        Map<String, Object> body = Map.of("id", channelId, "resourceId", resourceId);
        try {
            restClient.post()
                    .uri(CHANNELS_STOP_URL)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return;
            }
            throw new BusinessException("구글 푸시 채널 해제 실패: " + e.getStatusCode().value());
        }
    }

    /** events.watch 응답(필요 필드만). expiration 은 epoch millis 문자열. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record GoogleWatchResponse(
            @org.jspecify.annotations.Nullable String resourceId,
            @org.jspecify.annotations.Nullable String expiration
    ) {
    }
}
