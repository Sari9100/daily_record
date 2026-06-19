package com.familyos.schedule.repository;

import com.familyos.schedule.entity.ScheduleSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScheduleSubjectRepository extends JpaRepository<ScheduleSubject, Long> {

    List<ScheduleSubject> findByScheduleIdAndFamilyId(Long scheduleId, Long familyId);

    /** 목록 응답의 subject 일괄 로딩. */
    List<ScheduleSubject> findByScheduleIdInAndFamilyId(Collection<Long> scheduleIds, Long familyId);
}
