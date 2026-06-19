package com.familyos.auth.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 회전 직후 grace window 캐시 (06-5-2).
 *
 * <p>정상적인 동시 요청이 같은 refresh 를 거의 동시에 보낼 수 있다. 막 회전(revoked)된 jti 가
 * 유예 시간 내 재제출되면 탈취가 아닌 레이스로 보고, <b>직전 회전으로 발급된 최신 토큰</b>을 그대로 돌려준다.
 *
 * <p>단일 서버(로컬 Mac Mini) 전제의 in-memory 구현. 다중 인스턴스 확장 시 Redis 등으로 대체.
 * 짧은 TTL(기본 10초)이며 만료 항목은 조회 시 지연 정리한다.
 */
@Component
public class RefreshGraceCache {

    private record Entry(IssuedTokens tokens, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    /** 회전된 oldJti → 새로 발급된 토큰 쌍을 유예시간 동안 보관. */
    public void put(String oldJti, IssuedTokens tokens, Duration grace) {
        cache.put(oldJti, new Entry(tokens, Instant.now().plus(grace)));
    }

    /** 유예 내면 직전 발급 토큰 반환, 아니면 empty. */
    public Optional<IssuedTokens> get(String oldJti) {
        Entry entry = cache.get(oldJti);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(oldJti);
            return Optional.empty();
        }
        return Optional.of(entry.tokens());
    }
}
