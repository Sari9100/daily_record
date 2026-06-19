package com.familyos.tag.repository;

import com.familyos.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByIdAndFamilyId(Long id, Long familyId);

    List<Tag> findByFamilyIdOrderByNameAsc(Long familyId);

    boolean existsByFamilyIdAndName(Long familyId, String name);

    /** 거래 응답의 태그명 해석 + 연결 alive 검증용 일괄 로딩(alive 만). */
    List<Tag> findByIdInAndFamilyId(Collection<Long> ids, Long familyId);
}
