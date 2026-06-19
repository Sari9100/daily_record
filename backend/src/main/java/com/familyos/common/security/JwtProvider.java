package com.familyos.common.security;

import com.familyos.common.context.AuthUser;
import com.familyos.common.domain.FamilyRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 생성·검증. Access/Refresh 서로 다른 시크릿.
 *
 * <ul>
 *   <li>Access(30분): sub=personId, accountId, familyId, role, type=ACCESS</li>
 *   <li>Refresh(14일): sub=personId, accountId, jti, type=REFRESH (familyId/role 불포함 — 재발급 시 DB 최신값)</li>
 * </ul>
 */
@Component
public class JwtProvider {

    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_REFRESH = "REFRESH";

    private static final String CLAIM_ACCOUNT_ID = "accountId";
    private static final String CLAIM_FAMILY_ID = "familyId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final JwtProperties props;

    public JwtProvider(JwtProperties props) {
        this.props = props;
        this.accessKey = Keys.hmacShaKeyFor(props.accessSecret().getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(props.refreshSecret().getBytes(StandardCharsets.UTF_8));
    }

    // ---- 생성 ----

    public String createAccessToken(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.personId()))
                .claim(CLAIM_ACCOUNT_ID, user.accountId())
                .claim(CLAIM_FAMILY_ID, user.familyId())
                .claim(CLAIM_ROLE, user.role().name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.accessTtl())))
                .signWith(accessKey)
                .compact();
    }

    /**
     * Refresh 토큰 생성. jti 는 호출측에서 DB(refresh_token)에 저장하여 회전·재사용 감지에 사용.
     */
    public RefreshToken createRefreshToken(Long personId, Long accountId) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.refreshTtl());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .subject(String.valueOf(personId))
                .claim(CLAIM_ACCOUNT_ID, accountId)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(refreshKey)
                .compact();
        return new RefreshToken(token, jti, exp);
    }

    // ---- 검증/파싱 ----

    /**
     * Access 토큰 파싱 → AuthUser. 서명/만료/타입 위반 시 {@link JwtException}.
     */
    public AuthUser parseAccessToken(String token) {
        Claims c = parse(token, accessKey);
        if (!TYPE_ACCESS.equals(c.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("ACCESS 토큰이 아닙니다.");
        }
        return new AuthUser(
                Long.valueOf(c.getSubject()),
                c.get(CLAIM_ACCOUNT_ID, Number.class).longValue(),
                c.get(CLAIM_FAMILY_ID, Number.class).longValue(),
                FamilyRole.valueOf(c.get(CLAIM_ROLE, String.class))
        );
    }

    /**
     * Refresh 토큰 파싱 → claims. 서명/만료/타입 위반 시 {@link JwtException}.
     * familyId/role 은 들어있지 않으므로 호출측이 DB 에서 최신값 조회.
     */
    public Claims parseRefreshToken(String token) {
        Claims c = parse(token, refreshKey);
        if (!TYPE_REFRESH.equals(c.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("REFRESH 토큰이 아닙니다.");
        }
        return c;
    }

    private Claims parse(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Refresh 토큰 발급 결과 (저장용 jti·만료 포함). */
    public record RefreshToken(String token, String jti, Instant expiresAt) {
    }
}
