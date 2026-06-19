package com.familyos.integration.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hermes(텔레그램 봇) 내부 연동 설정.
 *
 * @param serviceToken 내부 API 전용 서비스 토큰(.env HERMES_SERVICE_TOKEN). 비어있으면 텔레그램 내부 API 는 전부 차단(fail-closed).
 */
@ConfigurationProperties(prefix = "hermes")
public record HermesProperties(String serviceToken) {

    public boolean isConfigured() {
        return serviceToken != null && !serviceToken.isBlank();
    }
}
