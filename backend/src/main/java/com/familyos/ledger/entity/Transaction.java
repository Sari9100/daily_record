package com.familyos.ledger.entity;

import com.familyos.common.domain.Visibility;
import com.familyos.common.entity.FamilyScopedEntity;
import com.familyos.common.entity.Visible;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * 수입/지출/이체 통합 거래.
 *
 * <p>거래유형-계좌 규칙: EXPENSE=source만 / INCOME=target만 / TRANSFER=둘 다(서로 다름) — 서비스 검증.
 * settlement_status·family_id 는 서버 결정(클라 전송 무시). visibility 는 PRIVATE/PARENTS/FAMILY 만.
 */
@Entity
@Table(name = "transaction")
public class Transaction extends FamilyScopedEntity implements Visible {

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency = "KRW";

    @Column(name = "source_account_id")
    private @Nullable Long sourceAccountId;

    @Column(name = "target_account_id")
    private @Nullable Long targetAccountId;

    @Column(name = "category_id")
    private @Nullable Long categoryId;

    @Column(name = "subject_person_id")
    private @Nullable Long subjectPersonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false)
    private SettlementStatus settlementStatus;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "memo")
    private @Nullable String memo;

    @Column(name = "collection_id")
    private @Nullable Long collectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private TransactionSource source;

    protected Transaction() {
    }

    public Transaction(Long familyId, TransactionType transactionType, BigDecimal amount, String currency,
                       @Nullable Long sourceAccountId, @Nullable Long targetAccountId,
                       @Nullable Long categoryId, @Nullable Long subjectPersonId,
                       Visibility visibility, SettlementStatus settlementStatus,
                       Instant occurredAt, @Nullable String memo, @Nullable Long collectionId,
                       TransactionSource source) {
        setFamilyId(familyId);
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.categoryId = categoryId;
        this.subjectPersonId = subjectPersonId;
        this.visibility = visibility;
        this.settlementStatus = settlementStatus;
        this.occurredAt = occurredAt;
        this.memo = memo;
        this.collectionId = collectionId;
        this.source = source;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public @Nullable Long getSourceAccountId() {
        return sourceAccountId;
    }

    public @Nullable Long getTargetAccountId() {
        return targetAccountId;
    }

    public @Nullable Long getCategoryId() {
        return categoryId;
    }

    public @Nullable Long getSubjectPersonId() {
        return subjectPersonId;
    }

    @Override
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public Set<Long> getSubjectPersonIds() {
        return subjectPersonId == null ? Set.of() : Set.of(subjectPersonId);
    }

    public SettlementStatus getSettlementStatus() {
        return settlementStatus;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public @Nullable String getMemo() {
        return memo;
    }

    public @Nullable Long getCollectionId() {
        return collectionId;
    }

    public TransactionSource getSource() {
        return source;
    }

    /** 전체 수정. settlement_status 는 서버가 재결정하여 전달. */
    public void update(TransactionType transactionType, BigDecimal amount, String currency,
                       @Nullable Long sourceAccountId, @Nullable Long targetAccountId,
                       @Nullable Long categoryId, @Nullable Long subjectPersonId,
                       Visibility visibility, SettlementStatus settlementStatus,
                       Instant occurredAt, @Nullable String memo, @Nullable Long collectionId) {
        this.transactionType = transactionType;
        this.amount = amount;
        this.currency = currency;
        this.sourceAccountId = sourceAccountId;
        this.targetAccountId = targetAccountId;
        this.categoryId = categoryId;
        this.subjectPersonId = subjectPersonId;
        this.visibility = visibility;
        this.settlementStatus = settlementStatus;
        this.occurredAt = occurredAt;
        this.memo = memo;
        this.collectionId = collectionId;
    }
}
