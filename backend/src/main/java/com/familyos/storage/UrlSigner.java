package com.familyos.storage;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 사진 조회용 서명 URL 생성/검증 (HMAC-SHA256).
 *
 * <p>직접 URL 노출 금지 원칙: 조회 권한은 인증된 {@code GET /photos/{id}} 시점에 visibility 로 판정하고,
 * 그때 짧은 유효시간의 서명 URL 을 발급한다. raw 서빙 엔드포인트는 서명+만료만 검증(capability URL).
 */
@Component
public class UrlSigner {

    private final byte[] secret;
    private final Duration ttl;

    public UrlSigner(StorageProperties props) {
        this.secret = props.urlSecret().getBytes(StandardCharsets.UTF_8);
        this.ttl = props.urlTtl();
    }

    /** photoId 에 대한 만료시각(epoch second). */
    public long expiryEpochSecond() {
        return Instant.now().plus(ttl).getEpochSecond();
    }

    public String sign(long photoId, long expEpochSecond) {
        return hmacHex(photoId + ":" + expEpochSecond);
    }

    /** 만료 미경과 + 서명 일치 여부(상수시간 비교). */
    public boolean isValid(long photoId, long expEpochSecond, String signature) {
        if (Instant.now().getEpochSecond() > expEpochSecond) {
            return false;
        }
        String expected = hmacHex(photoId + ":" + expEpochSecond);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("서명 생성 실패", e);
        }
    }
}
