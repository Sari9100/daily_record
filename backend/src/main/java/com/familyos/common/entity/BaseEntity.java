package com.familyos.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 모든 엔티티 공통 상위 클래스.
 *
 * <p><b>물리 삭제 금지 — Soft Delete 전용.</b> Hibernate 7 {@link SoftDelete}(TIMESTAMP)가
 * {@code deleted_at} 컬럼을 자동 관리한다. 조회 시 {@code deleted_at IS NULL} 이 자동 적용되며,
 * {@code repository.delete()} 호출은 물리 DELETE 대신 {@code deleted_at} 을 채우는 UPDATE 로 변환된다.
 *
 * <p>주의:
 * <ul>
 *   <li>{@code deleted_at} 은 @SoftDelete 가 관리하므로 <b>필드로 매핑하지 않는다</b>.</li>
 *   <li>{@code deleted_by} 는 @SoftDelete 가 채워주지 않으므로 <b>서비스 계층에서 직접 set</b>
 *       (SoftDeleteSupport 헬퍼) 후 삭제를 호출한다.</li>
 *   <li>UNIQUE 제약은 JPA 에 적지 않는다 — alive_uk 생성컬럼 방식으로 Flyway DDL(SSOT)에서만 정의.</li>
 * </ul>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private @Nullable Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private @Nullable Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private @Nullable Long updatedBy;

    /** 삭제자 Person.id. @SoftDelete 가 자동 관리하지 않으므로 서비스에서 set 한다. */
    @Column(name = "deleted_by")
    private @Nullable Long deletedBy;

    public @Nullable Long getId() {
        return id;
    }

    public @Nullable Instant getCreatedAt() {
        return createdAt;
    }

    /** 작성자(author) = Person.id. visibility 판정의 기준. */
    public @Nullable Long getCreatedBy() {
        return createdBy;
    }

    public @Nullable Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable Long getUpdatedBy() {
        return updatedBy;
    }

    public @Nullable Long getDeletedBy() {
        return deletedBy;
    }

    /** soft-delete 직전 서비스 계층에서 호출 (SoftDeleteSupport 경유 권장). */
    public void setDeletedBy(@Nullable Long deletedBy) {
        this.deletedBy = deletedBy;
    }
}
