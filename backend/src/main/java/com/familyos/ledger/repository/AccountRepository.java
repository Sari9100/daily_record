package com.familyos.ledger.repository;

import com.familyos.ledger.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * ★ 단건 조회는 family_id 를 명시한다. Hibernate @Filter 는 em.find(by id) 에 적용되지 않으므로
 * findByIdAndFamilyId 로 멀티테넌트 격리를 직접 보장한다. @SoftDelete 가 alive 는 자동 처리.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByIdAndFamilyId(Long id, Long familyId);

    /** 거래 응답의 계좌 표시/마스킹 해석용 일괄 로딩(alive 만). */
    List<Account> findByIdInAndFamilyId(Collection<Long> ids, Long familyId);

    /**
     * 본인이 볼 수 있는 계좌 목록 (visibility 를 DB WHERE 에서 필터 — 메모리 필터 금지).
     * FAMILY 전체 / PARENTS(부모만) / PRIVATE(작성자 본인).
     */
    @Query("""
            select a from Account a
            where a.familyId = :familyId
              and (a.visibility = com.familyos.common.domain.Visibility.FAMILY
                   or (a.visibility = com.familyos.common.domain.Visibility.PARENTS and :isParent = true)
                   or (a.visibility = com.familyos.common.domain.Visibility.PRIVATE and a.createdBy = :personId))
            order by a.id asc
            """)
    List<Account> findVisible(@Param("familyId") Long familyId,
                              @Param("personId") Long personId,
                              @Param("isParent") boolean isParent);
}
