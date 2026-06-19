package com.familyos.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.jspecify.annotations.Nullable;

/**
 * 가족 경계(family_id)를 가지는 엔티티의 상위 클래스 — 멀티테넌트 격리의 핵심.
 *
 * <p>Hibernate {@code @Filter("familyFilter")} 가 요청 단위로 활성화되어
 * ({@code FamilyFilterAspect}) FamilyScoped 엔티티 조회를 자동으로 family_id 범위로 한정한다.
 *
 * <p><b>★ MyBatis 함정</b>: @Filter 는 MyBatis 쿼리에 적용되지 않는다. MyBatis 매퍼는
 * 모든 WHERE 에 {@code family_id = #{familyId}} 와 {@code deleted_at IS NULL} 을 직접 명시해야 한다.
 *
 * <p>family_id 는 절대 요청 본문/쿼리/헤더로 받지 않는다. JWT 클레임에서만 추출
 * ({@code FamilyContext}) 하여 세팅한다 — 위변조 방지.
 */
@MappedSuperclass
@FilterDef(name = "familyFilter", parameters = @ParamDef(name = "familyId", type = Long.class))
@Filter(name = "familyFilter", condition = "family_id = :familyId")
public abstract class FamilyScopedEntity extends BaseEntity {

    @Column(name = "family_id", nullable = false, updatable = false)
    private @Nullable Long familyId;

    public @Nullable Long getFamilyId() {
        return familyId;
    }

    /** 생성 시 FamilyContext 의 familyId 로 1회 세팅. 이후 변경 불가(updatable=false). */
    public void setFamilyId(@Nullable Long familyId) {
        this.familyId = familyId;
    }
}
