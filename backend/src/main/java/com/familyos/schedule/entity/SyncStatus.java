package com.familyos.schedule.entity;

/** 구글 캘린더 동기화 상태. 서버·동기화 모듈만 관리(클라 수정 불가). */
public enum SyncStatus {
    SYNCED,
    PENDING,
    CONFLICT,
    DELETED_REMOTE
}
