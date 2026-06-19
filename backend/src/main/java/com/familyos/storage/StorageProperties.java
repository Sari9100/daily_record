package com.familyos.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 스토리지 설정. 로컬 SSD 우선, 클라우드 이식 가능하게 추상화.
 *
 * @param localRoot 외장 SSD 등 파일 저장 루트 경로
 * @param urlSecret 서명 URL HMAC 시크릿(.env 외부화 — 하드코딩/커밋 금지)
 * @param urlTtl    서명 URL 유효시간(기본 10분)
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @DefaultValue("./var/storage") String localRoot,
        String urlSecret,
        @DefaultValue("PT10M") Duration urlTtl
) {
}
