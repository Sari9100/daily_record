package com.familyos.common.security;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer 토큰을 검증해 인증 컨텍스트를 채우는 필터.
 *
 * <p>처리: 헤더 토큰 추출 → Access 검증 → {@link AuthUser} 생성 →
 * SecurityContext(권한 ROLE_*) + {@link FamilyContext} 주입. 요청 종료 시 FamilyContext clear.
 *
 * <p>토큰이 없거나 유효하지 않으면 인증을 채우지 않고 통과시킨다(익명) — 보호 경로는
 * authorizeHttpRequests + AuthenticationEntryPoint(401)가 차단한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                AuthUser user = jwtProvider.parseAccessToken(token);
                authenticate(user);
            } catch (JwtException | IllegalArgumentException e) {
                // 위조·만료 토큰: 인증 미설정으로 통과 → EntryPoint 가 401 처리. SecurityContext 정리.
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            FamilyContext.clear();
        }
    }

    private void authenticate(AuthUser user) {
        var authority = new SimpleGrantedAuthority("ROLE_" + user.role().name());
        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        FamilyContext.set(user);
    }

    private @Nullable String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
