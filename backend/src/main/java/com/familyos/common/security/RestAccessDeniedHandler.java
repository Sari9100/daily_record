package com.familyos.common.security;

import com.familyos.common.error.ErrorCode;
import com.familyos.common.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인가 거부(인증은 됐으나 권한 부족) → 403 JSON.
 *
 * <p>주의: <b>visibility 위반은 여기로 오지 않는다</b> — 서비스 계층 {@code VisibilityGuard} 가
 * 404(NotFoundException)로 숨긴다. 이 핸들러는 메서드 보안 등 명시적 인가 거부에만 동작.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN_VISIBILITY.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(ErrorCode.FORBIDDEN_VISIBILITY, "접근 권한이 없습니다.")
        );
    }
}
