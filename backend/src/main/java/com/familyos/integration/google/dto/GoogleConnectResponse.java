package com.familyos.integration.google.dto;

/** 구글 동의 화면 URL. 클라이언트(앱/브라우저)가 이 URL 을 연다. */
public record GoogleConnectResponse(String authorizationUrl) {
}
