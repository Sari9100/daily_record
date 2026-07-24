package com.familyos.ledger.repository;

import com.familyos.common.domain.Visibility;
import com.familyos.ledger.entity.Transaction;
import com.familyos.ledger.entity.TransactionType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdAndFamilyId(Long id, Long familyId);

    /**
     * 거래 목록 — visibility 를 DB WHERE 에서 필터(메모리 필터 금지).
     * 권한 범위: FAMILY 전체 / PARENTS(부모) / PRIVATE(작성자 본인).
     * scopeVisibility 는 보기 필터(ALL=null, PRIVATE, PARENTS) — 권한 범위 내 추가 제한.
     * from/to/type/categoryId 는 선택 필터(null 이면 무시).
     */
    @Query("""
            select t from Transaction t
            where t.familyId = :familyId
              and (:from is null or t.occurredAt >= :from)
              and (:to is null or t.occurredAt <= :to)
              and (:type is null or t.transactionType = :type)
              and (:categoryId is null or t.categoryId = :categoryId)
              and (:collectionId is null or t.collectionId = :collectionId)
              and (:scopeVisibility is null or t.visibility = :scopeVisibility)
              and (t.visibility = com.familyos.common.domain.Visibility.FAMILY
                   or (t.visibility = com.familyos.common.domain.Visibility.PARENTS and :isParent = true)
                   or (t.visibility = com.familyos.common.domain.Visibility.PRIVATE and t.createdBy = :personId))
            order by t.occurredAt desc
            """)
    Page<Transaction> search(@Param("familyId") Long familyId,
                             @Param("personId") Long personId,
                             @Param("isParent") boolean isParent,
                             @Param("from") @Nullable Instant from,
                             @Param("to") @Nullable Instant to,
                             @Param("type") @Nullable TransactionType type,
                             @Param("categoryId") @Nullable Long categoryId,
                             @Param("collectionId") @Nullable Long collectionId,
                             @Param("scopeVisibility") @Nullable Visibility scopeVisibility,
                             Pageable pageable);
}
