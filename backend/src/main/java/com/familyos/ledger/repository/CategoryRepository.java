package com.familyos.ledger.repository;

import com.familyos.ledger.entity.Category;
import com.familyos.ledger.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByIdAndFamilyId(Long id, Long familyId);

    /** 거래 응답의 카테고리명 해석용 일괄 로딩(alive 만). */
    List<Category> findByIdInAndFamilyId(Collection<Long> ids, Long familyId);

    /** 가족 카테고리 목록(기본 시딩 + 커스텀). type 미지정이면 전체. */
    List<Category> findByFamilyIdOrderByIdAsc(Long familyId);

    List<Category> findByFamilyIdAndTypeOrderByIdAsc(Long familyId, CategoryType type);
}
