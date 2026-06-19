package com.familyos.integration.google.crypto;

import com.familyos.integration.google.GoogleProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * refresh token 암호화 저장용 AES-256-GCM.
 *
 * <p>출력 형식: {@code [12B IV][ciphertext + 16B tag]}. 키는 {@code google.token-enc-key}(Base64 32바이트).
 * 평문 토큰은 메모리에서만 다루고 로그/DB 평문 저장 금지.
 */
@Component
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(GoogleProperties props) {
        byte[] keyBytes = props.tokenEncKey() == null || props.tokenEncKey().isBlank()
                ? new byte[32] // 미설정 시 더미 키(연동 비활성 전제 — 실제 사용 전 isConfigured 로 차단)
                : Base64.getDecoder().decode(props.tokenEncKey());
        if (keyBytes.length != 32) {
            throw new IllegalStateException("google.token-enc-key 는 Base64 인코딩된 32바이트(AES-256)여야 합니다.");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("토큰 암호화 실패", e);
        }
    }

    public String decrypt(byte[] stored) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(stored, 0, iv, 0, IV_LENGTH);
            byte[] ct = new byte[stored.length - IV_LENGTH];
            System.arraycopy(stored, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("토큰 복호화 실패", e);
        }
    }
}
