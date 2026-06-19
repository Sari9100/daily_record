package com.familyos.photo.repository;

import com.familyos.photo.entity.Photo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    Optional<Photo> findByIdAndFamilyId(Long id, Long familyId);

    /** photo_hash 중복 제거용(alive). */
    Optional<Photo> findByFamilyIdAndPhotoHash(Long familyId, String photoHash);

    /** diary 응답의 사진 일괄 로딩. */
    List<Photo> findByDiaryIdInAndFamilyId(Collection<Long> diaryIds, Long familyId);

    List<Photo> findByDiaryIdAndFamilyId(Long diaryId, Long familyId);
}
