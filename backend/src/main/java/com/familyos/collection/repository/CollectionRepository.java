package com.familyos.collection.repository;

import com.familyos.collection.entity.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionRepository extends JpaRepository<Collection, Long> {

    Optional<Collection> findByIdAndFamilyId(Long id, Long familyId);

    List<Collection> findByFamilyIdOrderByIdDesc(Long familyId);
}
