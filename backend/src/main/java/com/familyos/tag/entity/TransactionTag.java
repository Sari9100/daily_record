package com.familyos.tag.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 거래-태그 연결. UNIQUE(transaction_id, tag_id, alive_uk) 는 DDL. */
@Entity
@Table(name = "transaction_tag")
public class TransactionTag extends FamilyScopedEntity {

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private Long transactionId;

    @Column(name = "tag_id", nullable = false, updatable = false)
    private Long tagId;

    protected TransactionTag() {
    }

    public TransactionTag(Long familyId, Long transactionId, Long tagId) {
        setFamilyId(familyId);
        this.transactionId = transactionId;
        this.tagId = tagId;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getTagId() {
        return tagId;
    }
}
