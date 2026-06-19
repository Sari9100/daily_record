package com.familyos.common.tenant;

import com.familyos.common.context.FamilyContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 요청 단위로 Hibernate {@code familyFilter} 를 활성화 — FamilyScoped 엔티티 조회를 family_id 로 자동 격리.
 *
 * <p>서비스 계층 진입 시점에 현재 트랜잭션 세션에 필터를 켠다. 트랜잭션 어드바이스보다 안쪽에서
 * 실행되도록 가장 낮은 우선순위로 둔다(세션이 열려 있어야 하므로 서비스는 @Transactional 이어야 함).
 *
 * <p><b>주의</b>: 이 필터는 JPA(Hibernate) 경로에만 적용된다. MyBatis 쿼리에는 적용되지 않으므로
 * MyBatis 매퍼는 WHERE 에 family_id·deleted_at 을 직접 명시해야 한다 ({@link MyBatisTenantInterceptor} 참조).
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE) // 트랜잭션 어드바이스(HIGHEST_PRECEDENCE) 안쪽에서 실행 → 세션 보장
public class FamilyFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.familyos..service..*(..))")
    public void enableFamilyFilter() {
        Long familyId = FamilyContext.getFamilyIdOrNull();
        if (familyId == null) {
            return; // 인증 컨텍스트 없음(permitAll 등) → 필터 미적용
        }
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("familyFilter").setParameter("familyId", familyId);
    }
}
