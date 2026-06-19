package com.familyos.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 설정. 시크릿은 환경변수(application.yml의 ${JWT_*})로 외부화 — 하드코딩·커밋 금지.
 *
 * @param accessSecret  Access 토큰 서명 시크릿 (HS256: 최소 32바이트)
 * @param refreshSecret Refresh 토큰 서명 시크릿 (Access 와 분리)
 * @param accessTtl     Access 만료 (기본 30분)
 * @param refreshTtl    Refresh 만료 (기본 14일)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String accessSecret,
        String refreshSecret,
        Duration accessTtl,
        Duration refreshTtl
) {
}
