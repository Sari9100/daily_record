package com.familyos.integration.google.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.BusinessException;
import com.familyos.integration.google.GoogleProperties;
import com.familyos.integration.google.crypto.TokenCipher;
import com.familyos.integration.google.entity.GoogleSyncState;
import com.familyos.integration.google.oauth.GoogleOAuthClient;
import com.familyos.integration.google.oauth.GoogleStateSigner;
import com.familyos.integration.google.oauth.GoogleTokenResponse;
import com.familyos.integration.google.repository.GoogleSyncStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구글 캘린더 OAuth 연결/해제 (Phase 1 - Step A).
 *
 * <p>connect: 로그인 사용자(JWT)가 동의 URL 을 받아 브라우저에서 동의 → callback 으로 code 수령.
 * callback: state 검증으로 행동 주체 확정 → code 를 토큰으로 교환 → refresh token 암호화 저장.
 */
@Service
public class GoogleConnectService {

    private static final String PRIMARY = "primary";

    private final GoogleProperties props;
    private final GoogleStateSigner stateSigner;
    private final GoogleOAuthClient oauthClient;
    private final TokenCipher tokenCipher;
    private final GoogleSyncStateRepository repository;

    public GoogleConnectService(GoogleProperties props,
                                GoogleStateSigner stateSigner,
                                GoogleOAuthClient oauthClient,
                                TokenCipher tokenCipher,
                                GoogleSyncStateRepository repository) {
        this.props = props;
        this.stateSigner = stateSigner;
        this.oauthClient = oauthClient;
        this.tokenCipher = tokenCipher;
        this.repository = repository;
    }

    /** 동의 화면 URL 발급 (현재 로그인 사용자 기준). */
    public String connect() {
        requireConfigured();
        AuthUser user = FamilyContext.require();
        return oauthClient.buildAuthorizationUrl(stateSigner.sign(user.personId(), user.familyId()));
    }

    /** OAuth 콜백 처리 — code → 토큰 교환 → refresh token 암호화 저장(upsert). */
    @Transactional
    public void handleCallback(String code, String state) {
        requireConfigured();
        long[] ids = stateSigner.verify(state);
        Long personId = ids[0];
        Long familyId = ids[1];

        GoogleTokenResponse tokens = oauthClient.exchangeCode(code);
        if (tokens.refreshToken() == null) {
            throw new BusinessException("구글 refresh token 을 받지 못했습니다. 동의 화면에서 권한을 다시 허용해주세요.");
        }
        byte[] encrypted = tokenCipher.encrypt(tokens.refreshToken());

        repository.findByPersonIdAndGoogleCalendarId(personId, PRIMARY)
                .ifPresentOrElse(
                        s -> s.updateRefreshToken(encrypted),
                        () -> repository.save(new GoogleSyncState(familyId, personId, PRIMARY, encrypted)));
    }

    /** 연동 해제 — refresh token·sync token·채널 무효화(행은 이력으로 보존). */
    @Transactional
    public void disconnect() {
        AuthUser user = FamilyContext.require();
        repository.findByPersonIdAndGoogleCalendarId(user.personId(), PRIMARY)
                .ifPresent(GoogleSyncState::disconnect);
        // TODO(Phase 3): 푸시 채널 stop 호출
    }

    private void requireConfigured() {
        if (!props.isConfigured()) {
            throw new BusinessException("구글 연동이 설정되지 않았습니다(GOOGLE_* 환경변수 확인).");
        }
    }
}
