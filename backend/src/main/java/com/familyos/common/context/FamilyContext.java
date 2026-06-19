package com.familyos.common.context;

import org.jspecify.annotations.Nullable;

/**
 * 요청 스코프 인증 컨텍스트 (ThreadLocal).
 *
 * <p>{@code JwtAuthenticationFilter} 가 토큰 검증 후 {@link AuthUser} 를 세팅하고,
 * 요청 종료 시 반드시 {@link #clear()} 한다(스레드 재사용 누수 방지).
 *
 * <p>familyId 는 여기(=토큰)에서만 가져온다. JPA 필터 활성화·family_id 세팅·MyBatis 파라미터 주입의 단일 출처.
 */
public final class FamilyContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private FamilyContext() {
    }

    public static void set(AuthUser authUser) {
        HOLDER.set(authUser);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 인증 컨텍스트가 있으면 반환, 없으면 null (permitAll 경로 등). */
    public static @Nullable AuthUser getOrNull() {
        return HOLDER.get();
    }

    /** 인증이 보장된 지점에서 사용. 없으면 IllegalStateException. */
    public static AuthUser require() {
        AuthUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("인증 컨텍스트가 없습니다 (FamilyContext 미설정).");
        }
        return user;
    }

    public static Long getFamilyId() {
        return require().familyId();
    }

    public static Long getPersonId() {
        return require().personId();
    }

    public static @Nullable Long getFamilyIdOrNull() {
        AuthUser user = HOLDER.get();
        return user == null ? null : user.familyId();
    }

    public static @Nullable Long getPersonIdOrNull() {
        AuthUser user = HOLDER.get();
        return user == null ? null : user.personId();
    }
}
