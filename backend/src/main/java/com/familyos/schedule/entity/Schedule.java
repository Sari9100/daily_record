package com.familyos.schedule.entity;

import com.familyos.common.domain.Visibility;
import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 일정.
 *
 * <p><b>시각/날짜 이원화</b>(1-6): is_all_day=true → start_date/end_date 만(started_at/ended_at NULL),
 * is_all_day=false → started_at/ended_at(UTC) 만(start_date/end_date NULL). 서비스에서 강제.
 *
 * <p><b>google 동기화 필드</b>(googleEventId/syncStatus/lastSyncedAt)는 서버·동기화 모듈만 설정한다.
 * 클라 요청으로 변경하지 않는다(별도 setter, 일반 update 에서 제외).
 */
@Entity
@Table(name = "schedule")
public class Schedule extends FamilyScopedEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private @Nullable String description;

    @Column(name = "location")
    private @Nullable String location;

    @Column(name = "started_at")
    private @Nullable Instant startedAt;

    @Column(name = "ended_at")
    private @Nullable Instant endedAt;

    @Column(name = "start_date")
    private @Nullable LocalDate startDate;

    @Column(name = "end_date")
    private @Nullable LocalDate endDate;

    @Column(name = "is_all_day", nullable = false)
    private boolean allDay;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false)
    private ScheduleType scheduleType;

    @Column(name = "is_done", nullable = false)
    private boolean done;

    @Column(name = "recurrence_rule")
    private @Nullable String recurrenceRule;

    @Column(name = "collection_id")
    private @Nullable Long collectionId;

    @Column(name = "google_event_id")
    private @Nullable String googleEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status")
    private @Nullable SyncStatus syncStatus;

    @Column(name = "last_synced_at")
    private @Nullable Instant lastSyncedAt;

    protected Schedule() {
    }

    public Schedule(Long familyId, String title, @Nullable String description, @Nullable String location,
                    @Nullable Instant startedAt, @Nullable Instant endedAt,
                    @Nullable LocalDate startDate, @Nullable LocalDate endDate, boolean allDay,
                    Visibility visibility, ScheduleType scheduleType, @Nullable String recurrenceRule,
                    @Nullable Long collectionId) {
        setFamilyId(familyId);
        this.title = title;
        this.description = description;
        this.location = location;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.startDate = startDate;
        this.endDate = endDate;
        this.allDay = allDay;
        this.visibility = visibility;
        this.scheduleType = scheduleType;
        this.done = false;
        this.recurrenceRule = recurrenceRule;
        this.collectionId = collectionId;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable String getLocation() {
        return location;
    }

    public @Nullable Instant getStartedAt() {
        return startedAt;
    }

    public @Nullable Instant getEndedAt() {
        return endedAt;
    }

    public @Nullable LocalDate getStartDate() {
        return startDate;
    }

    public @Nullable LocalDate getEndDate() {
        return endDate;
    }

    public boolean isAllDay() {
        return allDay;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public ScheduleType getScheduleType() {
        return scheduleType;
    }

    public boolean isDone() {
        return done;
    }

    public @Nullable String getRecurrenceRule() {
        return recurrenceRule;
    }

    public @Nullable Long getCollectionId() {
        return collectionId;
    }

    public @Nullable SyncStatus getSyncStatus() {
        return syncStatus;
    }

    public @Nullable String getGoogleEventId() {
        return googleEventId;
    }

    /** 클라 수정 가능한 필드만 갱신. google 동기화 필드는 제외(서버·동기화 모듈 전용). */
    public void update(String title, @Nullable String description, @Nullable String location,
                       @Nullable Instant startedAt, @Nullable Instant endedAt,
                       @Nullable LocalDate startDate, @Nullable LocalDate endDate, boolean allDay,
                       Visibility visibility, ScheduleType scheduleType, @Nullable String recurrenceRule,
                       @Nullable Long collectionId) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.startDate = startDate;
        this.endDate = endDate;
        this.allDay = allDay;
        this.visibility = visibility;
        this.scheduleType = scheduleType;
        this.recurrenceRule = recurrenceRule;
        this.collectionId = collectionId;
    }

    public void toggleDone() {
        this.done = !this.done;
    }

    // ---- 구글 동기화 (서버·동기화 모듈 전용) ----

    /**
     * 구글 이벤트로부터 동기화된 필드만 갱신. visibility/scheduleType/collection 은 우리 고유 개념이라 보존
     * (사용자가 우리 앱에서 설정한 값을 동기화가 덮지 않는다).
     */
    public void applyGoogleEvent(String title, @Nullable String description, @Nullable String location,
                                 @Nullable Instant startedAt, @Nullable Instant endedAt,
                                 @Nullable LocalDate startDate, @Nullable LocalDate endDate,
                                 boolean allDay, @Nullable String recurrenceRule) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.startDate = startDate;
        this.endDate = endDate;
        this.allDay = allDay;
        this.recurrenceRule = recurrenceRule;
    }

    public void markGoogleSynced(String googleEventId, Instant lastSyncedAt) {
        this.googleEventId = googleEventId;
        this.syncStatus = SyncStatus.SYNCED;
        this.lastSyncedAt = lastSyncedAt;
    }

    /**
     * 양쪽 변경 충돌(1-7) — last-write-wins 로 내용은 이미 정해진 뒤, 흔적을 남긴다(CONFLICT).
     * 다음 증분 동기화에서 정상 변경이 들어오면 SYNCED 로 자가 치유된다.
     */
    public void markConflict(String googleEventId, Instant lastSyncedAt) {
        this.googleEventId = googleEventId;
        this.syncStatus = SyncStatus.CONFLICT;
        this.lastSyncedAt = lastSyncedAt;
    }

    /** 구글에서 삭제됨(우리는 보존). 표시용 — 현재 사용처는 inbound cancelled→soft-delete 가 처리. */
    public void markDeletedRemote() {
        this.syncStatus = SyncStatus.DELETED_REMOTE;
    }

    /**
     * 로컬(우리 앱) 변경 → 구글로 전송 대기(PENDING). 무한루프 가드: 구글 유입 변경은
     * {@link #markGoogleSynced}(SYNCED)로 처리되어 절대 PENDING 이 되지 않으므로 되돌아 나가지 않는다.
     */
    public void markPendingSync() {
        this.syncStatus = SyncStatus.PENDING;
    }
}
