package com.familyos.common.error;

/**
 * 도메인 비즈니스 규칙 위반 → 기본 422.
 *
 * <p>예: 거래유형-계좌 규칙 위반, 참조 대상 alive 검증 실패, settlement 규칙 위반 등.
 * 코드를 명시하지 않으면 {@link ErrorCode#BUSINESS_RULE}.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(String message) {
        this(ErrorCode.BUSINESS_RULE, message);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
