package com.familyos.integration.google.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 구글 캘린더 동기화 상태 (구성원 개인 계정별). UNIQUE(person_id, google_calendar_id, alive_uk) 는 DDL.
 *
 * <p>refresh_token_enc 는 암호화 저장(평문/로그 금지). 연동 해제 시 NULL 로 무효화.
 * sync_token 은 증분 동기화의 핵심 — persist 필수.
 */
@Entity
@Table(name = "google_sync_state")
public class GoogleSyncState extends FamilyScopedEntity {

    @Column(name = "person_id", nullable = false, updatable = false)
    private Long personId;

    @Column(name = "google_calendar_id", nullable = false, updatable = false)
    private String googleCalendarId;

    @Column(name = "sync_token")
    private @Nullable String syncToken;

    @Column(name = "channel_id")
    private @Nullable String channelId;

    @Column(name = "channel_resource_id")
    private @Nullable String channelResourceId;

    @Column(name = "channel_expires_at")
    private @Nullable Instant channelExpiresAt;

    @Column(name = "refresh_token_enc")
    private byte @Nullable [] refreshTokenEnc;

    @Column(name = "last_full_sync_at")
    private @Nullable Instant lastFullSyncAt;

    @Column(name = "last_incremental_at")
    private @Nullable Instant lastIncrementalAt;

    protected GoogleSyncState() {
    }

    public GoogleSyncState(Long familyId, Long personId, String googleCalendarId, byte[] refreshTokenEnc) {
        setFamilyId(familyId);
        this.personId = personId;
        this.googleCalendarId = googleCalendarId;
        this.refreshTokenEnc = refreshTokenEnc;
    }

    public Long getPersonId() {
        return personId;
    }

    public String getGoogleCalendarId() {
        return googleCalendarId;
    }

    public @Nullable String getSyncToken() {
        return syncToken;
    }

    public byte @Nullable [] getRefreshTokenEnc() {
        return refreshTokenEnc;
    }

    public @Nullable Instant getChannelExpiresAt() {
        return channelExpiresAt;
    }

    public void updateRefreshToken(byte[] refreshTokenEnc) {
        this.refreshTokenEnc = refreshTokenEnc;
    }

    public void updateSyncToken(@Nullable String syncToken, Instant when) {
        this.syncToken = syncToken;
        this.lastIncrementalAt = when;
    }

    public void markFullSynced(@Nullable String syncToken, Instant when) {
        this.syncToken = syncToken;
        this.lastFullSyncAt = when;
        this.lastIncrementalAt = when;
    }

    /** 연동 해제 — 토큰 무효화. */
    public void disconnect() {
        this.refreshTokenEnc = null;
        this.syncToken = null;
        this.channelId = null;
        this.channelResourceId = null;
        this.channelExpiresAt = null;
    }
}
