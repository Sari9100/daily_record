package com.familyos.integration.google.client;

/** 410 Gone — syncToken 만료. 호출측이 전체 재동기화로 폴백해야 한다(docs/08 §1-4). */
public class SyncTokenExpiredException extends RuntimeException {
    public SyncTokenExpiredException() {
        super("구글 syncToken 만료(410) — 전체 재동기화 필요");
    }
}
