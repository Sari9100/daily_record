package com.familyos.schedule.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 일정 실제 참석자 — 다중. UNIQUE(schedule_id, person_id, alive_uk) 는 DDL. */
@Entity
@Table(name = "schedule_participant")
public class ScheduleParticipant extends FamilyScopedEntity {

    @Column(name = "schedule_id", nullable = false, updatable = false)
    private Long scheduleId;

    @Column(name = "person_id", nullable = false, updatable = false)
    private Long personId;

    protected ScheduleParticipant() {
    }

    public ScheduleParticipant(Long familyId, Long scheduleId, Long personId) {
        setFamilyId(familyId);
        this.scheduleId = scheduleId;
        this.personId = personId;
    }

    public Long getScheduleId() {
        return scheduleId;
    }

    public Long getPersonId() {
        return personId;
    }
}
