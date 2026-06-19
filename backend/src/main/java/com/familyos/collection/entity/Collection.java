package com.familyos.collection.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 이벤트 묶음(앨범/여행 등). 가족 전체 공유(visibility 없음).
 *
 * <p>cover_photo_id 는 photo 도메인 구현 시 설정(현재 보류 — 검증 불가한 참조 미수용).
 */
@Entity
@Table(name = "collection")
public class Collection extends FamilyScopedEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private @Nullable String description;

    @Column(name = "cover_photo_id")
    private @Nullable Long coverPhotoId;

    @Column(name = "started_at")
    private @Nullable Instant startedAt;

    @Column(name = "ended_at")
    private @Nullable Instant endedAt;

    protected Collection() {
    }

    public Collection(Long familyId, String name, @Nullable String description,
                      @Nullable Instant startedAt, @Nullable Instant endedAt) {
        setFamilyId(familyId);
        this.name = name;
        this.description = description;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public @Nullable Long getCoverPhotoId() {
        return coverPhotoId;
    }

    public @Nullable Instant getStartedAt() {
        return startedAt;
    }

    public @Nullable Instant getEndedAt() {
        return endedAt;
    }

    public void update(String name, @Nullable String description,
                       @Nullable Instant startedAt, @Nullable Instant endedAt) {
        this.name = name;
        this.description = description;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }
}
