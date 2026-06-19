package com.familyos.integration.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

/**
 * 구글 캘린더 연동 설정 (.env). 개인 구글 계정 OAuth2.
 *
 * @param clientId     OAuth 클라이언트 ID
 * @param clientSecret OAuth 클라이언트 시크릿
 * @param redirectUri  콜백 URI (구글 콘솔의 승인된 리디렉션 URI 와 정확히 일치)
 * @param tokenEncKey  refresh token 암호화 키 (Base64, 32바이트=AES-256). 미설정 시 연동 비활성
 * @param push         푸시 webhook(Phase 3) 설정. 공개 HTTPS 수신 URL·도메인 인증 필요 → 기본 비활성
 */
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String tokenEncKey,
        @Nullable Push push
) {

    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri) && notBlank(tokenEncKey);
    }

    /** 푸시 채널이 사용 가능한가 — 활성 플래그 + 수신 URL + 채널 토큰이 모두 갖춰진 경우. */
    public boolean isPushEnabled() {
        return push != null && push.enabled() && notBlank(push.callbackUrl()) && notBlank(push.token());
    }

    /**
     * @param enabled     푸시 채널 등록 여부. 공개 URL/도메인 인증 전에는 false 로 두고 폴링에 의존
     * @param callbackUrl 구글이 변경 알림을 POST 할 공개 HTTPS URL (.../integrations/google/notifications)
     * @param token       채널 토큰 — 수신 시 X-Goog-Channel-Token 으로 되돌아옴(위조 방지 비교값)
     */
    public record Push(
            boolean enabled,
            @Nullable String callbackUrl,
            @Nullable String token
    ) {
    }

    private static boolean notBlank(@Nullable String s) {
        return s != null && !s.isBlank();
    }
}
