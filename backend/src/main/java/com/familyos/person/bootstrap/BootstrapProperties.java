package com.familyos.person.bootstrap;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 최초 부트스트랩 자격증명·설정. 모두 .env(환경변수)로 외부화 — 비밀번호는 코드/Git 에 남기지 않는다.
 *
 * @param enabled    부트스트랩 시도 여부 (기본 true). DB 가 비어있을 때만 실제로 동작.
 * @param loginId    최초 부모 계정 로그인 ID (미설정 시 시드 생략)
 * @param password   최초 부모 계정 평문 비밀번호 (BCrypt 해시는 런타임 생성. 절대 로그/커밋 금지)
 * @param parentName 최초 부모 Person 이름
 * @param familyName 가족 이름
 * @param timezone   부모 timezone
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(
        @DefaultValue("true") boolean enabled,
        @Nullable String loginId,
        @Nullable String password,
        @DefaultValue("부모") String parentName,
        @DefaultValue("우리 가족") String familyName,
        @DefaultValue("Asia/Seoul") String timezone
) {

    public boolean hasCredentials() {
        return loginId != null && !loginId.isBlank()
                && password != null && !password.isBlank();
    }
}
