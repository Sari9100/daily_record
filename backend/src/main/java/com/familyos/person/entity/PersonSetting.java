package com.familyos.person.entity;

import com.familyos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * 개인별 표시 설정. family_id 없음.
 * lazy 생성 — 행이 없으면 기본값(FULL)으로 간주, PUT 최초 시 upsert.
 */
@Entity
@Table(name = "person_setting")
public class PersonSetting extends BaseEntity {

    @Column(name = "person_id", nullable = false, updatable = false)
    private Long personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "shared_schedule_detail_level", nullable = false)
    private DetailLevel sharedScheduleDetailLevel = DetailLevel.FULL;

    protected PersonSetting() {
    }

    public PersonSetting(Long personId, DetailLevel sharedScheduleDetailLevel) {
        this.personId = personId;
        this.sharedScheduleDetailLevel = sharedScheduleDetailLevel;
    }

    public Long getPersonId() {
        return personId;
    }

    public DetailLevel getSharedScheduleDetailLevel() {
        return sharedScheduleDetailLevel;
    }

    public void changeSharedScheduleDetailLevel(DetailLevel level) {
        this.sharedScheduleDetailLevel = level;
    }
}
