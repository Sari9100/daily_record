package com.familyos.collection.mapper;

import com.familyos.collection.dto.CollectionSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 묶음 요약 집계 — MyBatis (집계는 JPA 금지 영역).
 *
 * <p><b>★ 멀티테넌트/soft-delete</b>: Hibernate 필터 미적용 → 모든 WHERE 에
 * {@code family_id = #{familyId}} 와 {@code deleted_at IS NULL} 직접 명시.
 */
@Mapper
public interface CollectionSummaryMapper {

    CollectionSummaryResponse selectSummary(@Param("familyId") Long familyId,
                                            @Param("collectionId") Long collectionId);
}
