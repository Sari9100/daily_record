package com.familyos.integration.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 캘린더 연동 설정 (.env). 개인 구글 계정 OAuth2.
 *
 * @param clientId     OAuth 클라이언트 ID
 * @param clientSecret OAuth 클라이언트 시크릿
 * @param redirectUri  콜백 URI (구글 콘솔의 승인된 리디렉션 URI 와 정확히 일치)
 * @param tokenEncKey  refresh token 암호화 키 (Base64, 32바이트=AES-256). 미설정 시 연동 비활성
 */
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String tokenEncKey
) {

    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri) && notBlank(tokenEncKey);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
