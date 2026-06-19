package com.familyos.diary.repository;

import com.familyos.diary.entity.DiarySubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DiarySubjectRepository extends JpaRepository<DiarySubject, Long> {

    List<DiarySubject> findByDiaryIdAndFamilyId(Long diaryId, Long familyId);

    List<DiarySubject> findByDiaryIdInAndFamilyId(Collection<Long> diaryIds, Long familyId);
}
