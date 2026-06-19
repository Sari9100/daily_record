package com.familyos.tag.repository;

import com.familyos.tag.entity.DiaryTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DiaryTagRepository extends JpaRepository<DiaryTag, Long> {

    List<DiaryTag> findByDiaryIdAndFamilyId(Long diaryId, Long familyId);

    List<DiaryTag> findByDiaryIdInAndFamilyId(Collection<Long> diaryIds, Long familyId);
}
