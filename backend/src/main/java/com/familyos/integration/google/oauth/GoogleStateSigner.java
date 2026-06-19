package com.familyos.integration.google.oauth;

import com.familyos.integration.google.GoogleProperties;
import com.familyos.common.error.BusinessException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * OAuth state 서명/검증. 콜백은 인증(JWT) 없이 호출되므로, 연결 시작 시 personId/familyId 를
 * HMAC 서명한 state 로 실어 보내고 콜백에서 위변조·만료를 검증해 행동 주체를 신뢰한다(CSRF/포저리 방지).
 *
 * <p>형식: {@code base64url(personId:familyId:exp) + "." + hmacHex}. HMAC 키는 OAuth client secret.
 */
@Component
public class GoogleStateSigner {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final byte[] secret;

    public GoogleStateSigner(GoogleProperties props) {
        String s = props.clientSecret() == null || props.clientSecret().isBlank() ? "unconfigured" : props.clientSecret();
        this.secret = s.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(Long personId, Long familyId) {
        String payload = personId + ":" + familyId + ":" + Instant.now().plus(TTL).getEpochSecond();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + hmac(encoded);
    }

    /** 검증 통과 시 [personId, familyId] 반환. 위변조/만료는 401(BusinessException UNAUTHENTICATED). */
    public long[] verify(String state) {
        int dot = state.lastIndexOf('.');
        if (dot < 0) {
            throw invalid();
        }
        String encoded = state.substring(0, dot);
        String sig = state.substring(dot + 1);
        if (!MessageDigest.isEqual(hmac(encoded).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8))) {
            throw invalid();
        }
        String payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        String[] parts = payload.split(":");
        if (parts.length != 3) {
            throw invalid();
        }
        long exp = Long.parseLong(parts[2]);
        if (Instant.now().getEpochSecond() > exp) {
            throw invalid();
        }
        return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("state 서명 실패", e);
        }
    }

    private BusinessException invalid() {
        return new BusinessException(com.familyos.common.error.ErrorCode.UNAUTHENTICATED, "유효하지 않은 OAuth state 입니다.");
    }
}
