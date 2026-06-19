package com.familyos.integration.google;

import com.familyos.common.error.BusinessException;
import com.familyos.integration.google.crypto.TokenCipher;
import com.familyos.integration.google.oauth.GoogleStateSigner;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구글 연동의 검증 가능한 부분(암호화·state 서명) 단위 테스트.
 * OAuth code↔token 교환은 실제 구글 필요로 테스트 불가(통합/수동).
 */
class GoogleCryptoTest {

    private static final String KEY_B64 = Base64.getEncoder().encodeToString(new byte[32]); // 32바이트 AES-256 키
    private final GoogleProperties props = new GoogleProperties("cid", "client-secret", "http://localhost/cb", KEY_B64, null);

    @Test
    void refresh_token_은_암복호화_왕복이_일치한다() {
        TokenCipher cipher = new TokenCipher(props);
        byte[] enc = cipher.encrypt("1//refresh-token-abc123");

        assertThat(cipher.decrypt(enc)).isEqualTo("1//refresh-token-abc123");
        assertThat(new String(enc)).doesNotContain("refresh-token"); // 평문 노출 없음
    }

    @Test
    void state_는_서명_검증_왕복으로_personId_familyId_를_복원한다() {
        GoogleStateSigner signer = new GoogleStateSigner(props);
        String state = signer.sign(5L, 1L);

        assertThat(signer.verify(state)).containsExactly(5L, 1L);
    }

    @Test
    void 변조된_state_는_거부된다() {
        GoogleStateSigner signer = new GoogleStateSigner(props);
        String state = signer.sign(5L, 1L);
        String tampered = state.substring(0, state.lastIndexOf('.') + 1) + "deadbeef";

        assertThatThrownBy(() -> signer.verify(tampered)).isInstanceOf(BusinessException.class);
    }
}
