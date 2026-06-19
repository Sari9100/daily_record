package com.familyos.common.audit;

import com.familyos.common.context.FamilyContext;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/**
 * @CreatedBy/@LastModifiedBy 에 현재 로그인 Person.id 를 주입.
 *
 * <p>인증 컨텍스트가 없으면(시스템 시딩·가입 직전 등) Optional.empty() → created_by/updated_by NULL.
 * DDL 에서 해당 컬럼은 NULL 허용.
 */
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        return Optional.ofNullable(FamilyContext.getPersonIdOrNull());
    }
}
