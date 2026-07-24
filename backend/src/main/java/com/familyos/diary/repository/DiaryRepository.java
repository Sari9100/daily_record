package com.familyos.diary.repository;

import com.familyos.common.domain.Visibility;
import com.familyos.diary.entity.Diary;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByIdAndFamilyId(Long id, Long familyId);

    /**
     * 권한 범위 내 일상기록 목록 — visibility 를 DB WHERE 에서 필터.
     * SHARED_PERSONAL 은 작성자 또는 subject 지정자에게만(EXISTS). 날짜축은 recorded_on.
     */
    @Query("""
            select d from Diary d
            where d.familyId = :familyId
              and (:scopeVisibility is null or d.visibility = :scopeVisibility)
              and (:fromDate is null or d.recordedOn >= :fromDate)
              and (:toDate is null or d.recordedOn <= :toDate)
              and (:collectionId is null or d.collectionId = :collectionId)
              and (:q is null or :q = ''
                    or lower(d.title) like lower(concat('%', :q, '%'))
                    or lower(d.content) like lower(concat('%', :q, '%'))
                    or exists (select dt.id from DiaryTag dt, Tag tg
                               where dt.tagId = tg.id
                                 and dt.diaryId = d.id
                                 and dt.familyId = :familyId
                                 and lower(tg.name) like lower(concat('%', :q, '%'))))
              and (
                    d.visibility = com.familyos.common.domain.Visibility.FAMILY
                    or (d.visibility = com.familyos.common.domain.Visibility.PARENTS and :isParent = true)
                    or (d.visibility = com.familyos.common.domain.Visibility.PRIVATE and d.createdBy = :personId)
                    or (d.visibility = com.familyos.common.domain.Visibility.SHARED_PERSONAL
                        and (d.createdBy = :personId
                             or exists (select s.id from DiarySubject s
                                        where s.diaryId = d.id
                                          and s.familyId = :familyId
                                          and s.personId = :personId)))
                  )
            order by d.recordedOn desc, d.recordedAt desc
            """)
    List<Diary> search(@Param("familyId") Long familyId,
                       @Param("personId") Long personId,
                       @Param("isParent") boolean isParent,
                       @Param("scopeVisibility") @Nullable Visibility scopeVisibility,
                       @Param("fromDate") @Nullable LocalDate fromDate,
                       @Param("toDate") @Nullable LocalDate toDate,
                       @Param("collectionId") @Nullable Long collectionId,
                       @Param("q") @Nullable String q);
}
