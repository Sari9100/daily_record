package com.familyos.person.repository;

import com.familyos.person.entity.FamilyMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Family/Person 은 BaseEntity(가족 필터 미적용)이므로, 멀티테넌트 격리는
 * 여기서 family_id 조건을 명시적으로 건다.
 */
public interface FamilyMembershipRepository extends JpaRepository<FamilyMembership, Long> {

    /** 가족 구성원 멤버십 + Person 동시 로딩(N+1 방지). */
    @Query("""
            select m from FamilyMembership m
            join fetch m.person
            where m.family.id = :familyId
            order by m.joinedAt asc
            """)
    List<FamilyMembership> findAliveByFamilyIdFetchPerson(@Param("familyId") Long familyId);

    /** 특정 가족 내 특정 person 의 멤버십(권한·소속 검증용). */
    Optional<FamilyMembership> findByFamily_IdAndPerson_Id(Long familyId, Long personId);

    boolean existsByFamily_IdAndPerson_Id(Long familyId, Long personId);

    /** 로그인 시 person 의 멤버십(들). 멤버십 1개면 자동 family 결정. */
    List<FamilyMembership> findByPerson_Id(Long personId);
}
