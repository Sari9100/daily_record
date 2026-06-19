package com.familyos.integration.google.service;

import com.familyos.integration.google.entity.GoogleSyncState;
import com.familyos.integration.google.repository.GoogleSyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 구글 동기화 정기 폴링 (docs §1-4 트리거②, §1-9). 푸시 webhook 의 신뢰성 폴백이자 기본 동기화 메커니즘.
 *
 * <p>연동된(refresh token 살아있는) 구성원마다 독립적으로 구글→우리 증분 동기화 + 우리→구글 PENDING 전송을
 * 수행한다. 한 사용자의 실패가 다른 가족으로 번지지 않도록 사용자별 try/catch + 독립 트랜잭션
 * ({@link GoogleSyncWorker} 가 보장).
 *
 * <p>주기는 {@code google.poll.interval-ms}(기본 15분). 가족 규모라 구글 rate limit 에 여유.
 */
@Component
public class GoogleSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoogleSyncScheduler.class);

    private final GoogleSyncStateRepository repository;
    private final GoogleSyncWorker worker;
    private final GooglePushChannelService channelService;

    @Value("${google.poll.enabled:true}")
    private boolean pollEnabled;

    public GoogleSyncScheduler(GoogleSyncStateRepository repository,
                               GoogleSyncWorker worker,
                               GooglePushChannelService channelService) {
        this.repository = repository;
        this.worker = worker;
        this.channelService = channelService;
    }

    @Scheduled(
            fixedDelayString = "${google.poll.interval-ms:900000}",
            initialDelayString = "${google.poll.initial-delay-ms:120000}")
    public void pollConnected() {
        if (!pollEnabled) {
            return;
        }
        List<GoogleSyncState> states = repository.findByRefreshTokenEncIsNotNull();
        if (states.isEmpty()) {
            return;
        }
        int ok = 0, failed = 0;
        for (GoogleSyncState state : states) {
            try {
                worker.syncAndPushAs(state);
                ok++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("구글 정기 동기화 실패: personId={}, {}", state.getPersonId(), e.getMessage());
            }
        }
        channelService.renewChannelsIfNeeded();
        log.info("구글 정기 동기화 완료: 대상 {}명(성공 {}, 실패 {})", states.size(), ok, failed);
    }
}
