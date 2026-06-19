package com.familyos.common.audit;

import com.familyos.common.context.FamilyContext;
import com.familyos.common.entity.BaseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

/**
 * Soft-delete 보조 헬퍼.
 *
 * <p>Hibernate {@code @SoftDelete} 는 {@code deleted_at} 만 채운다. {@code deleted_by} 는
 * 자동 관리되지 않으므로, 삭제 직전 현재 Person.id 를 set 하여 먼저 영속화한 뒤 soft-delete 를 호출한다.
 *
 * <p>사용:
 * <pre>{@code
 *   softDeleteSupport.softDelete(transaction, transactionRepository);
 * }</pre>
 *
 * 물리 삭제 금지 — 모든 삭제는 이 경로(또는 동등하게 deleted_by set 후 delete)를 거친다.
 */
@Component
public class SoftDeleteSupport {

    /** deleted_by(현재 사용자) 기록 후 soft-delete. */
    public <T extends BaseEntity, ID> void softDelete(T entity, JpaRepository<T, ID> repository) {
        softDelete(entity, repository, FamilyContext.getPersonIdOrNull());
    }

    /** deleted_by 를 명시 지정하여 soft-delete (시스템 작업 등). */
    public <T extends BaseEntity, ID> void softDelete(T entity, JpaRepository<T, ID> repository,
                                                      @Nullable Long deletedBy) {
        entity.setDeletedBy(deletedBy);
        // deleted_by UPDATE 를 먼저 확정한 뒤, @SoftDelete 가 deleted_at 을 채우는 UPDATE 를 수행하게 한다.
        repository.saveAndFlush(entity);
        repository.delete(entity);
    }
}
