package com.familyos.integration.google.service;

import com.familyos.integration.google.GoogleProperties;
import com.familyos.integration.google.client.GoogleCalendarClient;
import com.familyos.integration.google.client.GoogleCalendarClient.GoogleWatchResponse;
import com.familyos.integration.google.crypto.TokenCipher;
import com.familyos.integration.google.entity.GoogleSyncState;
import com.familyos.integration.google.oauth.GoogleOAuthClient;
import com.familyos.integration.google.repository.GoogleSyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 구글 푸시 채널(events.watch) 관리 (Phase 3, docs §1-5).
 *
 * <p><b>활성 조건</b>: {@code google.push.enabled=true} + 공개 HTTPS 수신 URL + 채널 토큰.
 * 공개 URL/도메인 인증(Cloudflare)이 갖춰지기 전에는 비활성 — 그동안은 정기 폴링(스케줄러)이 동기화를 책임진다.
 * 푸시는 어차피 "변경 발생" 신호일 뿐이고 신뢰성 폴백이 폴링이라(docs §1-0), 비활성이어도 양방향 동기화는 동작한다.
 */
@Service
public class GooglePushChannelService {

    private static final Logger log = LoggerFactory.getLogger(GooglePushChannelService.class);
    /** 만료 임박 임계값 — 이 시간 안에 만료되면 재등록. */
    private static final Duration RENEW_BEFORE = Duration.ofHours(24);

    private final GoogleProperties props;
    private final GoogleOAuthClient oauthClient;
    private final TokenCipher tokenCipher;
    private final GoogleCalendarClient calendarClient;
    private final GoogleSyncStateRepository repository;
    private final GoogleSyncWorker worker;

    public GooglePushChannelService(GoogleProperties props,
                                    GoogleOAuthClient oauthClient,
                                    TokenCipher tokenCipher,
                                    GoogleCalendarClient calendarClient,
                                    GoogleSyncStateRepository repository,
                                    GoogleSyncWorker worker) {
        this.props = props;
        this.oauthClient = oauthClient;
        this.tokenCipher = tokenCipher;
        this.calendarClient = calendarClient;
        this.repository = repository;
        this.worker = worker;
    }

    public boolean isEnabled() {
        return props.isPushEnabled();
    }

    /**
     * 푸시 알림 수신 처리(docs §1-5). 알림 본문엔 데이터가 없으므로 "변경 발생" 신호로만 보고 증분 동기화를 당긴다.
     *
     * @param channelId     X-Goog-Channel-ID
     * @param channelToken  X-Goog-Channel-Token — 우리가 watch 시 등록한 토큰과 일치해야 함(위조 방지)
     * @param resourceState X-Goog-Resource-State — "sync"(채널 개통 핸드셰이크)는 무시, 그 외는 변경
     */
    public void handleNotification(@org.jspecify.annotations.Nullable String channelId,
                                   @org.jspecify.annotations.Nullable String channelToken,
                                   @org.jspecify.annotations.Nullable String resourceState) {
        if (!isEnabled()) {
            return;
        }
        if (channelToken == null || !channelToken.equals(props.push().token())) {
            log.warn("구글 푸시 알림 채널 토큰 불일치 — 무시: channelId={}", channelId);
            return; // 위조 가능성 → 조용히 무시(구글에는 200 응답)
        }
        if ("sync".equals(resourceState) || channelId == null) {
            return; // 채널 개통 직후 핸드셰이크 — 동기화할 변경 아님
        }
        GoogleSyncState state = repository.findByChannelId(channelId).orElse(null);
        if (state == null || state.getRefreshTokenEnc() == null) {
            return; // 해제됐거나 모르는 채널
        }
        worker.syncAs(state); // 구글→우리 증분만 (inbound 신호)
    }

    /**
     * 채널 등록(연동 직후). 호출자(handleCallback)의 트랜잭션 안에서 managed entity 를 변형하므로 별도 tx 불필요.
     * push 비활성/토큰 없음이면 조용히 no-op.
     */
    public void ensureChannel(GoogleSyncState state) {
        if (!isEnabled() || state.getRefreshTokenEnc() == null) {
            return;
        }
        String accessToken = freshAccessToken(state);
        String channelId = UUID.randomUUID().toString();
        GoogleWatchResponse w = calendarClient.watchEvents(
                accessToken, state.getGoogleCalendarId(), channelId,
                props.push().callbackUrl(), props.push().token());
        Instant expires = (w.expiration() == null) ? null : Instant.ofEpochMilli(Long.parseLong(w.expiration()));
        state.updateChannel(channelId, w.resourceId(), expires);
        log.info("구글 푸시 채널 등록: personId={}, channelId={}, expires={}", state.getPersonId(), channelId, expires);
    }

    /**
     * 채널 해제(연동 해제 직전). refresh token 이 아직 살아있을 때 호출해야 access token 을 얻어 stop 가능.
     * 호출자(disconnect) 트랜잭션 안에서 managed entity 변형.
     */
    public void stopChannel(GoogleSyncState state) {
        if (state.getChannelId() == null || state.getChannelResourceId() == null || state.getRefreshTokenEnc() == null) {
            state.clearChannel();
            return;
        }
        try {
            String accessToken = freshAccessToken(state);
            calendarClient.stopChannel(accessToken, state.getChannelId(), state.getChannelResourceId());
        } catch (RuntimeException e) {
            log.warn("구글 푸시 채널 해제 실패(무시하고 흔적만 제거): personId={}, {}", state.getPersonId(), e.getMessage());
        }
        state.clearChannel();
    }

    /** 만료 임박/미등록 채널 재등록 — 스케줄러가 주기 호출. push 비활성이면 no-op. */
    @Transactional
    public void renewChannelsIfNeeded() {
        if (!isEnabled()) {
            return;
        }
        Instant threshold = Instant.now().plus(RENEW_BEFORE);
        for (GoogleSyncState state : repository.findByRefreshTokenEncIsNotNull()) {
            try {
                Instant expires = state.getChannelExpiresAt();
                if (state.getChannelId() == null || expires == null || expires.isBefore(threshold)) {
                    ensureChannel(state);
                }
            } catch (RuntimeException e) {
                log.warn("구글 푸시 채널 갱신 실패: personId={}, {}", state.getPersonId(), e.getMessage());
            }
        }
    }

    private String freshAccessToken(GoogleSyncState state) {
        String refreshToken = tokenCipher.decrypt(state.getRefreshTokenEnc());
        return oauthClient.refreshAccessToken(refreshToken).accessToken();
    }
}
