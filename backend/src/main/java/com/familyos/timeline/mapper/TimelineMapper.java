package com.familyos.timeline.mapper;

import com.familyos.timeline.dto.TimelineRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 통합 타임라인 — MyBatis (다중 테이블 날짜축 병합은 JPA 금지 영역).
 *
 * <p><b>★ 함정</b>: Hibernate @Filter·@SoftDelete 미적용 → 모든 서브쿼리 WHERE 에 family_id·deleted_at 직접 명시.
 * visibility 권한 조건도 도메인별로 WHERE 에 적용(메모리 필터 금지).
 *
 * @param zoneOffset 뷰어 timezone offset("+09:00") — Instant 컬럼을 로컬 날짜로 그룹화
 */
@Mapper
public interface TimelineMapper {

    List<TimelineRow> timeline(@Param("familyId") Long familyId,
                               @Param("personId") Long personId,
                               @Param("isParent") boolean isParent,
                               @Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate,
                               @Param("zoneOffset") String zoneOffset);
}
