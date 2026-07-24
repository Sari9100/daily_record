package com.familyos.auth.service;

import com.familyos.auth.dto.TokenResponse;
import com.familyos.auth.entity.RefreshToken;
import com.familyos.auth.repository.RefreshTokenRepository;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.UnauthorizedException;
import com.familyos.common.security.JwtProperties;
import com.familyos.common.security.JwtProvider;
import com.familyos.person.entity.Family;
import com.familyos.person.entity.FamilyMembership;
import com.familyos.person.entity.Person;
import com.familyos.person.entity.UserAccount;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.person.repository.FamilyRepository;
import com.familyos.person.repository.PersonRepository;
import com.familyos.person.repository.UserAccountRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 회전 보안 규칙(필수): 정상 회전 / grace window 레이스 / reuse 탈취 감지.
 *
 * <p>주의: 이 환경에서는 빌드/실행 미검증.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String OLD_JTI = "old-jti";
    private static final Long ACCOUNT_ID = 10L;
    private static final Long PERSON_ID = 1L;

    @Mock UserAccountRepository userAccountRepository;
    @Mock FamilyMembershipRepository membershipRepository;
    @Mock PersonRepository personRepository;
    @Mock FamilyRepository familyRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock RefreshGraceCache graceCache;
    @Mock JwtProvider jwtProvider;
    @Mock PasswordEncoder passwordEncoder;
    @Mock Claims claims;

    AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                "access-secret", "refresh-secret",
                Duration.ofMinutes(30), Duration.ofDays(14), Duration.ofSeconds(10));
        authService = new AuthService(userAccountRepository, membershipRepository, personRepository,
                familyRepository, refreshTokenRepository, graceCache, jwtProvider, props, passwordEncoder);
    }

    private void stubRefreshClaims() {
        when(jwtProvider.parseRefreshToken("token")).thenReturn(claims);
        when(claims.getId()).thenReturn(OLD_JTI);
        when(claims.get("accountId", Number.class)).thenReturn(ACCOUNT_ID);
        when(claims.getSubject()).thenReturn(String.valueOf(PERSON_ID));
    }

    private void stubRotationDependencies() {
        Person person = new Person("사리", null, null);
        UserAccount account = new UserAccount(person, "sari", "hash");
        FamilyMembership membership = new FamilyMembership(
                new Family("우리집"), person, FamilyRole.PARENT, Instant.now());
        when(userAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(membershipRepository.findByPerson_Id(PERSON_ID)).thenReturn(List.of(membership));
        when(jwtProvider.createAccessToken(any())).thenReturn("new-access");
        when(jwtProvider.createRefreshToken(any(), any())).thenReturn(
                new JwtProvider.RefreshToken("new-refresh", "new-jti", Instant.now().plusSeconds(1000)));
        when(jwtProvider.accessTtlSeconds()).thenReturn(1800L);
    }

    @Test
    void 정상_refresh_는_기존토큰을_폐기하고_새토큰을_발급한다() {
        stubRefreshClaims();
        RefreshToken stored = new RefreshToken(ACCOUNT_ID, OLD_JTI, Instant.now().plusSeconds(1000)); // alive
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(stored));
        stubRotationDependencies();

        TokenResponse res = authService.refresh("token");

        assertThat(stored.isRevoked()).isTrue();                 // 기존 jti 폐기
        verify(refreshTokenRepository).save(any(RefreshToken.class)); // 새 jti 저장
        verify(graceCache).put(eq(OLD_JTI), any(), any());       // grace 등록
        assertThat(res.accessToken()).isEqualTo("new-access");
        assertThat(res.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void revoked_토큰이_grace_내면_직전_발급토큰을_반환한다() {
        stubRefreshClaims();
        RefreshToken revoked = new RefreshToken(ACCOUNT_ID, OLD_JTI, Instant.now().plusSeconds(1000));
        revoked.revoke();
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(revoked));
        when(graceCache.get(OLD_JTI)).thenReturn(
                Optional.of(new IssuedTokens("graced-access", "graced-refresh", 1800L)));

        TokenResponse res = authService.refresh("token");

        assertThat(res.accessToken()).isEqualTo("graced-access");
        verify(refreshTokenRepository, never()).revokeAllByAccountId(any()); // 탈취로 보지 않음
    }

    @Test
    void revoked_토큰이_grace_밖이면_계정_전체를_폐기하고_401() {
        stubRefreshClaims();
        RefreshToken revoked = new RefreshToken(ACCOUNT_ID, OLD_JTI, Instant.now().plusSeconds(1000));
        revoked.revoke();
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(revoked));
        when(graceCache.get(OLD_JTI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("token"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository).revokeAllByAccountId(ACCOUNT_ID); // 탈취 의심 → 전체 폐기
    }

    @Test
    void 알수없는_jti_는_401() {
        stubRefreshClaims();
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("token"))
                .isInstanceOf(UnauthorizedException.class);

        verify(jwtProvider, never()).createAccessToken(any());
    }

    @Test
    void 비밀번호_변경_성공시_해시를_바꾸고_모든_세션을_폐기한다() {
        FamilyContext.set(new AuthUser(PERSON_ID, ACCOUNT_ID, 1L, FamilyRole.PARENT));
        try {
            Person person = new Person("사리", null, null);
            UserAccount account = new UserAccount(person, "sari", "old-hash");
            when(userAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(passwordEncoder.matches("current", "old-hash")).thenReturn(true);
            when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");

            authService.changePassword("current", "newpassword");

            assertThat(account.getPasswordHash()).isEqualTo("new-hash");
            verify(refreshTokenRepository).revokeAllByAccountId(ACCOUNT_ID);
        } finally {
            FamilyContext.clear();
        }
    }

    @Test
    void 비밀번호_변경시_현재비밀번호_불일치면_422이고_변경되지_않는다() {
        FamilyContext.set(new AuthUser(PERSON_ID, ACCOUNT_ID, 1L, FamilyRole.PARENT));
        try {
            Person person = new Person("사리", null, null);
            UserAccount account = new UserAccount(person, "sari", "old-hash");
            when(userAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
            when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword("wrong", "newpassword"))
                    .isInstanceOf(BusinessException.class);

            assertThat(account.getPasswordHash()).isEqualTo("old-hash");
            verify(refreshTokenRepository, never()).revokeAllByAccountId(any());
        } finally {
            FamilyContext.clear();
        }
    }
}
