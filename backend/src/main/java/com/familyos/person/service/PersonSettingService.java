package com.familyos.person.service;

import com.familyos.common.context.FamilyContext;
import com.familyos.person.dto.PersonSettingResponse;
import com.familyos.person.entity.DetailLevel;
import com.familyos.person.entity.PersonSetting;
import com.familyos.person.repository.PersonSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 설정 — lazy 생성.
 *
 * <p>GET: 행이 없으면 기본값(FULL) 응답(404 아님). PUT: 최초 호출 시 행 생성(upsert).
 */
@Service
@Transactional(readOnly = true)
public class PersonSettingService {

    private final PersonSettingRepository settingRepository;

    public PersonSettingService(PersonSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public PersonSettingResponse getMySettings() {
        Long personId = FamilyContext.getPersonId();
        DetailLevel level = settingRepository.findByPersonId(personId)
                .map(PersonSetting::getSharedScheduleDetailLevel)
                .orElse(DetailLevel.FULL); // 기본값
        return new PersonSettingResponse(level);
    }

    @Transactional
    public PersonSettingResponse updateMySettings(DetailLevel level) {
        Long personId = FamilyContext.getPersonId();
        PersonSetting setting = settingRepository.findByPersonId(personId)
                .orElseGet(() -> new PersonSetting(personId, level));
        setting.changeSharedScheduleDetailLevel(level);
        settingRepository.save(setting); // upsert
        return new PersonSettingResponse(setting.getSharedScheduleDetailLevel());
    }
}
