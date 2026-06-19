package com.familyos.tag.repository;

import com.familyos.tag.entity.TransactionTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TransactionTagRepository extends JpaRepository<TransactionTag, Long> {

    List<TransactionTag> findByTransactionIdAndFamilyId(Long transactionId, Long familyId);

    /** 목록 응답의 태그 연결 일괄 로딩. */
    List<TransactionTag> findByTransactionIdInAndFamilyId(Collection<Long> transactionIds, Long familyId);
}
