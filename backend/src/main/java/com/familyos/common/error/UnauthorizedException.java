package com.familyos.common.error;

/**
 * 401 — 인증 실패. 서비스 계층에서 던지는 인증 오류(로그인 실패, 리프레시 토큰 무효/재사용 등).
 *
 * <p>필터 단계의 미인증은 {@code RestAuthenticationEntryPoint} 가 처리하고,
 * 이 예외는 컨트롤러 도달 이후(서비스) 발생하는 인증 실패를 {@link ErrorCode#UNAUTHENTICATED}(401)로 변환한다.
 * 사유를 구체적으로 노출하지 않아 계정 열거를 방지한다.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
