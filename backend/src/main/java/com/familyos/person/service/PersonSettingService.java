package com.familyos.person.service;

import com.familyos.common.context.FamilyContext;
import com.familyos.person.dto.PersonSettingResponse;
import com.familyos.person.entity.DetailLevel;
import com.familyos.person.entity.PersonSetting;
import com.familyos.person.entity.ViewMode;
import com.familyos.person.repository.PersonSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 설정 — lazy 생성.
 *
 * <p>GET: 행이 없으면 기본값(FULL / 화면별 CALENDAR) 응답(404 아님). PUT: 최초 호출 시 행 생성(upsert).
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
        return settingRepository.findByPersonId(personId)
                .map(this::toResponse)
                .orElseGet(() -> new PersonSettingResponse(
                        DetailLevel.FULL, ViewMode.CALENDAR, ViewMode.CALENDAR, ViewMode.CALENDAR));
    }

    @Transactional
    public PersonSettingResponse updateMySettings(
            DetailLevel sharedScheduleDetailLevel,
            ViewMode ledgerDefaultView,
            ViewMode scheduleDefaultView,
            ViewMode diaryDefaultView) {
        Long personId = FamilyContext.getPersonId();
        PersonSetting setting = settingRepository.findByPersonId(personId)
                .orElseGet(() -> new PersonSetting(personId, sharedScheduleDetailLevel));
        setting.changeSharedScheduleDetailLevel(sharedScheduleDetailLevel);
        setting.changeLedgerDefaultView(ledgerDefaultView);
        setting.changeScheduleDefaultView(scheduleDefaultView);
        setting.changeDiaryDefaultView(diaryDefaultView);
        settingRepository.save(setting); // upsert
        return toResponse(setting);
    }

    private PersonSettingResponse toResponse(PersonSetting setting) {
        return new PersonSettingResponse(
                setting.getSharedScheduleDetailLevel(),
                setting.getLedgerDefaultView(),
                setting.getScheduleDefaultView(),
                setting.getDiaryDefaultView());
    }
}
