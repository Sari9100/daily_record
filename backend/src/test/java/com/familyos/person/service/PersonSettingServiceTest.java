package com.familyos.person.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.person.dto.PersonSettingResponse;
import com.familyos.person.entity.DetailLevel;
import com.familyos.person.entity.PersonSetting;
import com.familyos.person.entity.ViewMode;
import com.familyos.person.repository.PersonSettingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PersonSettingService lazy-upsert 검증: 행 없을 때 기본값(FULL/CALENDAR) 응답, PUT으로 4개 필드 upsert.
 *
 * <p>주의: 이 환경에서는 빌드/실행 미검증. 의도와 규칙을 코드로 고정하는 용도.
 */
@ExtendWith(MockitoExtension.class)
class PersonSettingServiceTest {

    private static final Long PERSON_ID = 10L;

    @Mock PersonSettingRepository settingRepository;

    PersonSettingService service;

    @BeforeEach
    void setUp() {
        service = new PersonSettingService(settingRepository);
        FamilyContext.set(new AuthUser(PERSON_ID, 1L, 1L, FamilyRole.PARENT));
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    @Test
    void getMySettings_returnsDefaults_whenNoRowExists() {
        when(settingRepository.findByPersonId(PERSON_ID)).thenReturn(Optional.empty());

        PersonSettingResponse response = service.getMySettings();

        assertThat(response.sharedScheduleDetailLevel()).isEqualTo(DetailLevel.FULL);
        assertThat(response.ledgerDefaultView()).isEqualTo(ViewMode.CALENDAR);
        assertThat(response.scheduleDefaultView()).isEqualTo(ViewMode.CALENDAR);
        assertThat(response.diaryDefaultView()).isEqualTo(ViewMode.CALENDAR);
    }

    @Test
    void updateMySettings_upsertsAllFourFields() {
        when(settingRepository.findByPersonId(PERSON_ID)).thenReturn(Optional.empty());
        when(settingRepository.save(any(PersonSetting.class))).thenAnswer(inv -> inv.getArgument(0));

        PersonSettingResponse response = service.updateMySettings(
                DetailLevel.SUMMARY, ViewMode.CALENDAR, ViewMode.CALENDAR, ViewMode.INLINE);

        assertThat(response.sharedScheduleDetailLevel()).isEqualTo(DetailLevel.SUMMARY);
        assertThat(response.ledgerDefaultView()).isEqualTo(ViewMode.CALENDAR);
        assertThat(response.scheduleDefaultView()).isEqualTo(ViewMode.CALENDAR);
        assertThat(response.diaryDefaultView()).isEqualTo(ViewMode.INLINE);
        verify(settingRepository).save(any(PersonSetting.class));
    }
}
