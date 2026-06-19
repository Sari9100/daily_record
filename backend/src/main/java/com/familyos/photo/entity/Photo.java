package com.familyos.photo.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 사진. diary 에 연결되거나(diaryId) 독립일 수 있다.
 *
 * <p>2단계 업로드: presign 시 행 생성(storageKey 발급), 바이너리 업로드 후 complete 에서 width/height 확정.
 * photo_hash(SHA-256) 중복 시 기존 행 재사용(중복 업로드 방지). 권한은 연결된 diary 의 visibility 를 따른다.
 */
@Entity
@Table(name = "photo")
public class Photo extends FamilyScopedEntity {

    @Column(name = "diary_id")
    private @Nullable Long diaryId;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "photo_hash", nullable = false, updatable = false)
    private String photoHash;

    @Column(name = "width")
    private @Nullable Integer width;

    @Column(name = "height")
    private @Nullable Integer height;

    @Column(name = "taken_at")
    private @Nullable Instant takenAt;

    @Column(name = "file_size")
    private @Nullable Long fileSize;

    @Column(name = "mime_type")
    private @Nullable String mimeType;

    @Column(name = "collection_id")
    private @Nullable Long collectionId;

    protected Photo() {
    }

    public Photo(Long familyId, @Nullable Long diaryId, String storageKey, String originalFilename,
                 String photoHash, @Nullable Instant takenAt, @Nullable Long fileSize, @Nullable String mimeType) {
        setFamilyId(familyId);
        this.diaryId = diaryId;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.photoHash = photoHash;
        this.takenAt = takenAt;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
    }

    public @Nullable Long getDiaryId() {
        return diaryId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getPhotoHash() {
        return photoHash;
    }

    public @Nullable Integer getWidth() {
        return width;
    }

    public @Nullable Integer getHeight() {
        return height;
    }

    public @Nullable Instant getTakenAt() {
        return takenAt;
    }

    public @Nullable Long getFileSize() {
        return fileSize;
    }

    public @Nullable String getMimeType() {
        return mimeType;
    }

    public @Nullable Long getCollectionId() {
        return collectionId;
    }

    /** complete 단계: 메타 확정. */
    public void completeMeta(@Nullable Integer width, @Nullable Integer height) {
        this.width = width;
        this.height = height;
    }

    public void linkDiary(@Nullable Long diaryId) {
        this.diaryId = diaryId;
    }
}
