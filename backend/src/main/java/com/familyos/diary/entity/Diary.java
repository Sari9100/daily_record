package com.familyos.diary.entity;

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
 * 일상기록.
 *
 * <p><b>시각/날짜 이원화</b>: recordedAt(작성/기록 시각 UTC Instant) + recordedOn(기록 대상 날짜 DATE).
 * recordedOn 은 타임라인 날짜축 정렬 기준 — Instant 로 대체하지 않는다(1-6).
 * visibility 4단계(SHARED_PERSONAL=author+subject). subject 는 별도 테이블({@code DiarySubject}).
 */
@Entity
@Table(name = "diary")
public class Diary extends FamilyScopedEntity {

    @Column(name = "title")
    private @Nullable String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_on", nullable = false)
    private LocalDate recordedOn;

    @Column(name = "collection_id")
    private @Nullable Long collectionId;

    protected Diary() {
    }

    public Diary(Long familyId, @Nullable String title, String content, Visibility visibility,
                 Instant recordedAt, LocalDate recordedOn, @Nullable Long collectionId) {
        setFamilyId(familyId);
        this.title = title;
        this.content = content;
        this.visibility = visibility;
        this.recordedAt = recordedAt;
        this.recordedOn = recordedOn;
        this.collectionId = collectionId;
    }

    public @Nullable String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public LocalDate getRecordedOn() {
        return recordedOn;
    }

    public @Nullable Long getCollectionId() {
        return collectionId;
    }

    public void update(@Nullable String title, String content, Visibility visibility,
                       Instant recordedAt, LocalDate recordedOn, @Nullable Long collectionId) {
        this.title = title;
        this.content = content;
        this.visibility = visibility;
        this.recordedAt = recordedAt;
        this.recordedOn = recordedOn;
        this.collectionId = collectionId;
    }
}
