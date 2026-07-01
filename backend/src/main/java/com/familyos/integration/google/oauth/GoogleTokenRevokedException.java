package com.familyos.integration.google.oauth;

/**
 * 구글 OAuth refresh token 이 만료되거나 사용자에 의해 취소된 경우 (invalid_grant).
 * syncForCurrentUser 에서 잡아 state.disconnect() 후 BusinessException 으로 변환한다.
 */
public class GoogleTokenRevokedException extends RuntimeException {

    public GoogleTokenRevokedException() {
        super("구글 refresh token 이 만료되었거나 취소되었습니다.");
    }
}
