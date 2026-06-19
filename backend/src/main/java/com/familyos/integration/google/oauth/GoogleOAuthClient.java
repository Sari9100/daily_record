package com.familyos.integration.google.oauth;

import com.familyos.common.error.BusinessException;
import com.familyos.integration.google.GoogleProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 구글 OAuth2/토큰 호출 (무거운 Google 클라이언트 대신 RestClient 직접 호출).
 * Phase 1 은 읽기 전용 → calendar 스코프, access_type=offline(refresh token 확보).
 */
@Component
public class GoogleOAuthClient {

    private static final String AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar";

    private final GoogleProperties props;
    private final RestClient restClient = RestClient.create();

    public GoogleOAuthClient(GoogleProperties props) {
        this.props = props;
    }

    /** 동의 화면 URL. 사용자가 브라우저로 열어 동의하면 redirectUri 로 code 와 함께 돌아온다. */
    public String buildAuthorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_URI)
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", encode(props.redirectUri()))
                .queryParam("response_type", "code")
                .queryParam("scope", encode(SCOPE))
                .queryParam("access_type", "offline")   // refresh token 확보
                .queryParam("prompt", "consent")        // 재동의 시에도 refresh token 재발급
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    public GoogleTokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("redirect_uri", props.redirectUri());
        form.add("grant_type", "authorization_code");
        return postToken(form);
    }

    /** refresh token 으로 access token 재발급(증분 동기화 호출 직전 사용). 응답엔 보통 새 refresh token 없음. */
    public GoogleTokenResponse refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());
        form.add("grant_type", "refresh_token");
        return postToken(form);
    }

    private GoogleTokenResponse postToken(MultiValueMap<String, String> form) {
        GoogleTokenResponse res = restClient.post()
                .uri(TOKEN_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(GoogleTokenResponse.class);
        if (res == null || res.accessToken() == null) {
            throw new BusinessException("구글 토큰 발급에 실패했습니다.");
        }
        return res;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
