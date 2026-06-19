package com.familyos.schedule.repository;

import com.familyos.common.domain.Visibility;
import com.familyos.schedule.entity.Schedule;
import com.familyos.schedule.entity.ScheduleType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByIdAndFamilyId(Long id, Long familyId);

    /** 구글 동기화 upsert 용 — google_event_id 로 기존 일정 매칭. */
    Optional<Schedule> findByGoogleEventIdAndFamilyId(String googleEventId, Long familyId);

    /** 우리→구글 전송 대기(PENDING) 목록 — 작성자 본인 것만 자신의 구글 캘린더로. */
    List<Schedule> findByFamilyIdAndCreatedByAndSyncStatus(Long familyId, Long createdBy,
                                                           com.familyos.schedule.entity.SyncStatus syncStatus);

    /**
     * 삭제 전송 대상 — soft-delete 되었지만 구글엔 아직 살아있는(google_event_id 보유) PENDING 일정.
     * {@code @SoftDelete} 가 deleted_at IS NULL 을 자동 부가해 일반 조회로는 안 잡히므로 네이티브로 deleted_at IS NOT NULL 을 직접 명시.
     * family_id 격리도 WHERE 에 직접 명시(네이티브는 필터 미적용).
     */
    @Query(value = """
            SELECT * FROM schedule
            WHERE family_id = :familyId
              AND created_by = :personId
              AND sync_status = 'PENDING'
              AND google_event_id IS NOT NULL
              AND deleted_at IS NOT NULL
            """, nativeQuery = true)
    List<Schedule> findDeletedPendingForGoogle(@Param("familyId") Long familyId, @Param("personId") Long personId);

    /** 삭제 전송 완료 표시 — 삭제된 행이라 JPA 더티체킹 대신 네이티브 UPDATE 로 sync_status 만 전이. */
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "UPDATE schedule SET sync_status = 'SYNCED', last_synced_at = :now WHERE id = :id", nativeQuery = true)
    void markDeletionPushed(@Param("id") Long id, @Param("now") Instant now);

    /**
     * 권한 범위 내 일정 목록 — visibility 를 DB WHERE 에서 필터.
     * SHARED_PERSONAL 은 작성자 또는 subject 지정자에게만(EXISTS 서브쿼리).
     * 기간 필터는 시점일정(started_at) / 종일일정(start_date) 이원 처리.
     */
    @Query("""
            select t from Schedule t
            where t.familyId = :familyId
              and (:type is null or t.scheduleType = :type)
              and (:scopeVisibility is null or t.visibility = :scopeVisibility)
              and (
                    (t.allDay = false
                        and (:from is null or t.startedAt >= :from)
                        and (:to is null or t.startedAt <= :to))
                    or (t.allDay = true
                        and (:fromDate is null or t.startDate >= :fromDate)
                        and (:toDate is null or t.startDate <= :toDate))
                  )
              and (
                    t.visibility = com.familyos.common.domain.Visibility.FAMILY
                    or (t.visibility = com.familyos.common.domain.Visibility.PARENTS and :isParent = true)
                    or (t.visibility = com.familyos.common.domain.Visibility.PRIVATE and t.createdBy = :personId)
                    or (t.visibility = com.familyos.common.domain.Visibility.SHARED_PERSONAL
                        and (t.createdBy = :personId
                             or exists (select s.id from ScheduleSubject s
                                        where s.scheduleId = t.id
                                          and s.familyId = :familyId
                                          and s.personId = :personId)))
                  )
            order by t.startDate asc, t.startedAt asc
            """)
    List<Schedule> search(@Param("familyId") Long familyId,
                          @Param("personId") Long personId,
                          @Param("isParent") boolean isParent,
                          @Param("type") @Nullable ScheduleType type,
                          @Param("scopeVisibility") @Nullable Visibility scopeVisibility,
                          @Param("from") @Nullable Instant from,
                          @Param("to") @Nullable Instant to,
                          @Param("fromDate") @Nullable LocalDate fromDate,
                          @Param("toDate") @Nullable LocalDate toDate);
}
