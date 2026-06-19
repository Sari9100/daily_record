package com.familyos.person.repository;

import com.familyos.person.entity.PersonSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonSettingRepository extends JpaRepository<PersonSetting, Long> {

    Optional<PersonSetting> findByPersonId(Long personId);
}
