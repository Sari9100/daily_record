package com.familyos.integration.google.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/** 구글 토큰 엔드포인트 응답. refresh_token 은 최초 동의(access_type=offline)에서만 옴. */
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") @Nullable String refreshToken,
        @JsonProperty("expires_in") @Nullable Long expiresIn,
        @JsonProperty("scope") @Nullable String scope,
        @JsonProperty("token_type") @Nullable String tokenType
) {
}
