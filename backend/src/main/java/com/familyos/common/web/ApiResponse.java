package com.familyos.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.familyos.common.error.ErrorCode;
import org.jspecify.annotations.Nullable;

/**
 * 공통 응답 래퍼: {@code {success, data, error}}.
 *
 * <p>성공 시 {@code error} 는 null, 실패 시 {@code data} 는 null (null 필드는 직렬화 생략).
 * 모든 컨트롤러는 이 래퍼로 감싸 반환하고, 예외는 {@code GlobalExceptionHandler} 가 변환한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        @Nullable T data,
        @Nullable ErrorBody error
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            String code,
            String message,
            @Nullable Object details
    ) {
    }

    public static <T> ApiResponse<T> ok(@Nullable T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return error(code, message, null);
    }

    public static ApiResponse<Void> error(ErrorCode code, String message, @Nullable Object details) {
        return new ApiResponse<>(false, null, new ErrorBody(code.name(), message, details));
    }
}
