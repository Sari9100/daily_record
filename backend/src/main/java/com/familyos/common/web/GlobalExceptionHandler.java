package com.familyos.common.web;

import com.familyos.common.error.BusinessException;
import com.familyos.common.error.ErrorCode;
import com.familyos.common.error.NotFoundException;
import com.familyos.common.error.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 모든 예외를 {@link ApiResponse#error} 로 변환하는 전역 핸들러.
 *
 * <p>매핑:
 * <ul>
 *   <li>검증 실패 → 400 VALIDATION_ERROR (필드 상세 포함)</li>
 *   <li>{@link NotFoundException} → 404 (권한 위반·타가족 숨김 포함)</li>
 *   <li>{@link AccessDeniedException} → 403 FORBIDDEN (명시적 인가 거부)</li>
 *   <li>{@link DataIntegrityViolationException} → 409 CONFLICT (유니크 충돌 등)</li>
 *   <li>{@link BusinessException} → 422 (도메인 규칙)</li>
 *   <li>그 외 → 500</li>
 * </ul>
 * 401(인증)은 SecurityFilterChain 의 AuthenticationEntryPoint 에서 처리된다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException e) {
        return build(ErrorCode.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
        return build(ErrorCode.UNAUTHENTICATED, e.getMessage(), null);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return build(e.getErrorCode(), e.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return build(ErrorCode.FORBIDDEN_VISIBILITY, "접근 권한이 없습니다.", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgNotValid(MethodArgumentNotValidException e) {
        List<FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toDetail)
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "입력값이 올바르지 않습니다.", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return build(ErrorCode.VALIDATION_ERROR, e.getMessage(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return build(ErrorCode.VALIDATION_ERROR, "요청 본문을 해석할 수 없습니다.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        // 유니크 충돌(alive_uk 등) 같은 무결성 위반. 상세 메시지는 노출하지 않음.
        log.warn("데이터 무결성 위반", e);
        return build(ErrorCode.CONFLICT, "이미 존재하거나 충돌하는 데이터입니다.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return build(ErrorCode.INTERNAL_ERROR, "서버 오류가 발생했습니다.", null);
    }

    private static ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message, Object details) {
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, message, details));
    }

    private static FieldErrorDetail toDetail(FieldError fe) {
        return new FieldErrorDetail(fe.getField(), fe.getDefaultMessage());
    }

    private record FieldErrorDetail(String field, String message) {
    }
}
