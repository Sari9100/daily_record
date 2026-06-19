package com.familyos.auth.service;

import com.familyos.auth.dto.LoginRequest;
import com.familyos.auth.dto.LoginResponse;
import com.familyos.auth.dto.MeResponse;
import com.familyos.auth.dto.TokenResponse;
import com.familyos.auth.entity.RefreshToken;
import com.familyos.auth.repository.RefreshTokenRepository;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.NotFoundException;
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
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 인증 — 로그인·토큰 회전·로그아웃·본인조회.
 *
 * <p>실패는 모두 401(UNAUTHENTICATED)로 통일하고 사유를 구체적으로 노출하지 않는다(계정 열거 방지).
 * 회전 시 reuse 감지 → 계정 전체 폐기, grace window 내 재제출 → 직전 발급 토큰 반환.
 */
@Service
public class AuthService {

    /** 로그인 실패 공통 메시지(아이디/비번 구분 노출 안 함). */
    private static final String LOGIN_FAILED = "아이디 또는 비밀번호가 올바르지 않습니다.";
    private static final String INVALID_REFRESH = "유효하지 않은 리프레시 토큰입니다.";

    private final UserAccountRepository userAccountRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final PersonRepository personRepository;
    private final FamilyRepository familyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshGraceCache graceCache;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository,
                       FamilyMembershipRepository membershipRepository,
                       PersonRepository personRepository,
                       FamilyRepository familyRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       RefreshGraceCache graceCache,
                       JwtProvider jwtProvider,
                       JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.personRepository = personRepository;
        this.familyRepository = familyRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.graceCache = graceCache;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public LoginResponse login(LoginRequest req) {
        UserAccount account = userAccountRepository.findByLoginId(req.loginId())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> unauthenticated(LOGIN_FAILED));

        if (!passwordEncoder.matches(req.password(), account.getPasswordHash())) {
            throw unauthenticated(LOGIN_FAILED);
        }

        Person person = account.getPerson();
        FamilyMembership membership = resolveMembership(person.getId());
        AuthUser authUser = new AuthUser(
                person.getId(), account.getId(), membership.getFamily().getId(), membership.getRole());

        IssuedTokens tokens = issue(authUser);
        account.markLoggedIn(Instant.now());

        return new LoginResponse(
                tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresInSeconds(),
                new LoginResponse.PersonSummary(
                        person.getId(), person.getName(), authUser.familyId(), authUser.role()));
    }

    /**
     * 토큰 회전. reuse 감지 시 계정 전체 폐기를 커밋해야 하므로 UnauthorizedException 에 대해 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenResponse refresh(String refreshTokenValue) {
        Claims claims;
        try {
            claims = jwtProvider.parseRefreshToken(refreshTokenValue);
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthenticated(INVALID_REFRESH);
        }

        String jti = claims.getId();
        Long accountId = claims.get("accountId", Number.class).longValue();
        Long personId = Long.valueOf(claims.getSubject());

        RefreshToken stored = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> unauthenticated(INVALID_REFRESH));

        if (stored.isRevoked()) {
            // grace window: 직전 회전으로 발급된 토큰을 그대로 반환(정상 동시요청 레이스)
            Optional<IssuedTokens> graced = graceCache.get(jti);
            if (graced.isPresent()) {
                return toTokenResponse(graced.get());
            }
            // 유예 밖의 revoked jti 재사용 → 탈취 의심 → 계정 전체 폐기
            refreshTokenRepository.revokeAllByAccountId(accountId);
            throw unauthenticated("리프레시 토큰이 재사용되었습니다. 다시 로그인해주세요.");
        }

        if (stored.isExpired(Instant.now())) {
            throw unauthenticated(INVALID_REFRESH);
        }

        // 계정 유효성 + 최신 family/role (refresh 에는 미포함 — DB 최신값으로 재발급)
        UserAccount account = userAccountRepository.findById(accountId)
                .filter(UserAccount::isActive)
                .orElseThrow(() -> unauthenticated(INVALID_REFRESH));
        FamilyMembership membership = resolveMembership(personId);

        stored.revoke();
        AuthUser authUser = new AuthUser(
                personId, account.getId(), membership.getFamily().getId(), membership.getRole());
        IssuedTokens tokens = issue(authUser);
        graceCache.put(jti, tokens, jwtProperties.refreshGrace());

        return toTokenResponse(tokens);
    }

    /** 제출된 refresh 의 jti 폐기. 멱등 — 유효하지 않은 토큰이면 조용히 무시. */
    @Transactional
    public void logout(String refreshTokenValue) {
        try {
            Claims claims = jwtProvider.parseRefreshToken(refreshTokenValue);
            refreshTokenRepository.findByJti(claims.getId()).ifPresent(RefreshToken::revoke);
        } catch (JwtException | IllegalArgumentException e) {
            // 무시: 로그아웃은 멱등
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AuthUser user = FamilyContext.require();
        Person person = personRepository.findById(user.personId())
                .orElseThrow(() -> NotFoundException.of("사용자", user.personId()));
        Family family = familyRepository.findById(user.familyId())
                .orElseThrow(() -> NotFoundException.of("가족", user.familyId()));
        return new MeResponse(
                new MeResponse.PersonDto(person.getId(), person.getName()),
                new MeResponse.FamilyDto(family.getId(), family.getName()),
                user.role());
    }

    // ---- 내부 ----

    private IssuedTokens issue(AuthUser authUser) {
        String access = jwtProvider.createAccessToken(authUser);
        JwtProvider.RefreshToken refresh = jwtProvider.createRefreshToken(
                authUser.personId(), authUser.accountId());
        refreshTokenRepository.save(
                new RefreshToken(authUser.accountId(), refresh.jti(), refresh.expiresAt()));
        return new IssuedTokens(access, refresh.token(), jwtProvider.accessTtlSeconds());
    }

    /**
     * 로그인/회전 시 family·role 결정. 멤버십 1개면 자동, 여러 개(미래 B)는 가장 먼저 가입한 가족을 기본 선택.
     * 멤버십이 없으면 토큰 발급 불가 → 인증 실패로 숨김.
     */
    private FamilyMembership resolveMembership(Long personId) {
        List<FamilyMembership> memberships = membershipRepository.findByPerson_Id(personId);
        return memberships.stream()
                .min(Comparator.comparing(FamilyMembership::getJoinedAt))
                .orElseThrow(() -> unauthenticated(LOGIN_FAILED));
        // TODO(미래 B): 멤버십 다수 시 가족 선택/switch-family 흐름으로 확장
    }

    private TokenResponse toTokenResponse(IssuedTokens tokens) {
        return new TokenResponse(
                tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresInSeconds());
    }

    private UnauthorizedException unauthenticated(String message) {
        return new UnauthorizedException(message);
    }
}
