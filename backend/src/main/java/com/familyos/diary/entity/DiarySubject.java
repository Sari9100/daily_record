package com.familyos.diary.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 일상기록 대상(subject) — 다중. UNIQUE(diary_id, person_id, alive_uk) 는 DDL. */
@Entity
@Table(name = "diary_subject")
public class DiarySubject extends FamilyScopedEntity {

    @Column(name = "diary_id", nullable = false, updatable = false)
    private Long diaryId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private Long personId;

    protected DiarySubject() {
    }

    public DiarySubject(Long familyId, Long diaryId, Long personId) {
        setFamilyId(familyId);
        this.diaryId = diaryId;
        this.personId = personId;
    }

    public Long getDiaryId() {
        return diaryId;
    }

    public Long getPersonId() {
        return personId;
    }
}
