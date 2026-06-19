package com.familyos.schedule.repository;

import com.familyos.schedule.entity.ScheduleParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScheduleParticipantRepository extends JpaRepository<ScheduleParticipant, Long> {

    List<ScheduleParticipant> findByScheduleIdAndFamilyId(Long scheduleId, Long familyId);

    /** 목록 응답의 participant 일괄 로딩. */
    List<ScheduleParticipant> findByScheduleIdInAndFamilyId(Collection<Long> scheduleIds, Long familyId);
}
