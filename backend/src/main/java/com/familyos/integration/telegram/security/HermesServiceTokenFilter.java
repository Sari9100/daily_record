package com.familyos.integration.telegram.security;

import com.familyos.common.error.ErrorCode;
import com.familyos.common.web.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Hermes 내부 API({@code /api/v1/integrations/telegram/**})의 서비스 토큰 검증.
 *
 * <p>사용자 JWT 와 분리된 전용 인증. {@code X-Service-Token} 헤더를 설정된 시크릿과 상수시간 비교.
 * 미설정(fail-closed)·불일치·누락은 모두 401. 다른 경로는 통과(여기서 관여하지 않음).
 */
public class HermesServiceTokenFilter extends OncePerRequestFilter {

    private static final String PATH_PREFIX = "/api/v1/integrations/telegram/";
    private static final String HEADER = "X-Service-Token";

    private final @Nullable String expectedToken;
    private final ObjectMapper objectMapper;

    public HermesServiceTokenFilter(@Nullable String expectedToken, ObjectMapper objectMapper) {
        this.expectedToken = expectedToken;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!isValid(request.getHeader(HEADER))) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isValid(@Nullable String provided) {
        if (expectedToken == null || expectedToken.isBlank() || provided == null) {
            return false; // 미설정 시 fail-closed
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.UNAUTHENTICATED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(ErrorCode.UNAUTHENTICATED, "유효한 서비스 토큰이 필요합니다."));
    }
}
