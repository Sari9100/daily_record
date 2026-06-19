package com.familyos.common.error;

/**
 * 404 — 미존재 자원.
 *
 * <p><b>권한 위반·타 가족 자원 접근도 이 예외로 숨긴다</b> (403 아님 — 존재 자체를 노출하지 않음).
 * {@code VisibilityGuard} 위반 시 던진다.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    /** 흔한 형태: "{자원} 을(를) 찾을 수 없습니다 (id={id})". */
    public static NotFoundException of(String resourceName, Object id) {
        return new NotFoundException(resourceName + " 을(를) 찾을 수 없습니다 (id=" + id + ")");
    }
}
