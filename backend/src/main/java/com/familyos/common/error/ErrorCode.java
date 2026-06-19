package com.familyos.common.error;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드 ↔ HTTP 상태 매핑.
 *
 * <p>권한 위반은 존재 자체를 숨기기 위해 {@link #NOT_FOUND}(404)로 응답한다(403 아님).
 * 메서드 보안(@PreAuthorize) 등 명시적 인가 거부만 {@link #FORBIDDEN}(403).
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),       // 400 — 입력 형식 오류
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),       // 401 — 토큰 없음/만료/위조
    FORBIDDEN_VISIBILITY(HttpStatus.FORBIDDEN),     // 403 — 명시적 인가 거부 (visibility 위반은 404로 숨김)
    NOT_FOUND(HttpStatus.NOT_FOUND),                // 404 — 미존재 + visibility/타가족 숨김
    CONFLICT(HttpStatus.CONFLICT),                  // 409 — 유니크 충돌 등
    BUSINESS_RULE(HttpStatus.UNPROCESSABLE_ENTITY), // 422 — 도메인 규칙 위반(거래유형-계좌, alive 검증)
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR); // 500

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
