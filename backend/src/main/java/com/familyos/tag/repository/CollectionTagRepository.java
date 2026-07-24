package com.familyos.tag.repository;

import com.familyos.tag.entity.CollectionTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CollectionTagRepository extends JpaRepository<CollectionTag, Long> {

    List<CollectionTag> findByCollectionIdAndFamilyId(Long collectionId, Long familyId);

    List<CollectionTag> findByCollectionIdInAndFamilyId(Collection<Long> collectionIds, Long familyId);
}
