package com.familyos.ledger.mapper;

import com.familyos.ledger.dto.CategoryStat;
import com.familyos.ledger.dto.MonthStat;
import com.familyos.ledger.dto.StatTotals;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * 가계부 통계 — MyBatis (집계는 JPA 금지 영역).
 *
 * <p><b>★ 멀티테넌트/soft-delete 함정</b>: Hibernate @Filter·@SoftDelete 가 적용되지 않으므로
 * 모든 쿼리 WHERE 에 {@code family_id = #{familyId}} 와 {@code deleted_at IS NULL} 을 직접 명시한다.
 * 또한 통계에서 <b>TRANSFER 는 제외</b>하고, visibility 권한 조건을 WHERE 로 적용한다.
 *
 * @param scope    보기 필터 visibility (null=ALL). 권한 범위 내 추가 제한.
 * @param zoneOffset 뷰어 timezone offset("+09:00" 등) — 월 그룹을 로컬 기준으로.
 */
@Mapper
public interface TransactionStatisticsMapper {

    StatTotals selectTotals(@Param("familyId") Long familyId,
                            @Param("personId") Long personId,
                            @Param("isParent") boolean isParent,
                            @Param("from") @Nullable Instant from,
                            @Param("to") @Nullable Instant to,
                            @Param("scope") @Nullable String scope);

    List<CategoryStat> selectByCategory(@Param("familyId") Long familyId,
                                        @Param("personId") Long personId,
                                        @Param("isParent") boolean isParent,
                                        @Param("from") @Nullable Instant from,
                                        @Param("to") @Nullable Instant to,
                                        @Param("scope") @Nullable String scope);

    List<MonthStat> selectByMonth(@Param("familyId") Long familyId,
                                  @Param("personId") Long personId,
                                  @Param("isParent") boolean isParent,
                                  @Param("from") @Nullable Instant from,
                                  @Param("to") @Nullable Instant to,
                                  @Param("scope") @Nullable String scope,
                                  @Param("zoneOffset") String zoneOffset);
}
