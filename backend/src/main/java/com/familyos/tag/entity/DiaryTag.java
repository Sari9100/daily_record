package com.familyos.tag.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 일상기록-태그 연결. UNIQUE(diary_id, tag_id, alive_uk) 는 DDL. */
@Entity
@Table(name = "diary_tag")
public class DiaryTag extends FamilyScopedEntity {

    @Column(name = "diary_id", nullable = false, updatable = false)
    private Long diaryId;

    @Column(name = "tag_id", nullable = false, updatable = false)
    private Long tagId;

    protected DiaryTag() {
    }

    public DiaryTag(Long familyId, Long diaryId, Long tagId) {
        setFamilyId(familyId);
        this.diaryId = diaryId;
        this.tagId = tagId;
    }

    public Long getDiaryId() {
        return diaryId;
    }

    public Long getTagId() {
        return tagId;
    }
}
