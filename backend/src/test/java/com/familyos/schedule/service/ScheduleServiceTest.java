package com.familyos.schedule.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.common.domain.Visibility;
import com.familyos.common.error.BusinessException;
import com.familyos.common.security.VisibilityGuard;
import com.familyos.person.entity.DetailLevel;
import com.familyos.person.entity.Person;
import com.familyos.person.entity.PersonSetting;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.person.repository.PersonRepository;
import com.familyos.person.repository.PersonSettingRepository;
import com.familyos.schedule.dto.ScheduleRequest;
import com.familyos.schedule.dto.ScheduleResponse;
import com.familyos.schedule.entity.Schedule;
import com.familyos.schedule.entity.ScheduleType;
import com.familyos.schedule.repository.ScheduleParticipantRepository;
import com.familyos.schedule.repository.ScheduleRepository;
import com.familyos.schedule.repository.ScheduleSubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * ScheduleService 핵심 규칙(필수): 시각/날짜 이원화, subject alive 검증, SUMMARY 마스킹.
 * 빌드/실행은 이 환경에서 미검증.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    private static final Long FAMILY_ID = 1L;
    private static final Long VIEWER = 1L;

    @Mock ScheduleRepository scheduleRepository;
    @Mock ScheduleSubjectRepository subjectRepository;
    @Mock ScheduleParticipantRepository participantRepository;
    @Mock FamilyMembershipRepository membershipRepository;
    @Mock com.familyos.collection.repository.CollectionRepository collectionRepository;
    @Mock PersonRepository personRepository;
    @Mock PersonSettingRepository personSettingRepository;
    @Mock VisibilityGuard visibilityGuard;
    @Mock SoftDeleteSupport softDeleteSupport;

    ScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(scheduleRepository, subjectRepository, participantRepository,
                membershipRepository, collectionRepository, personRepository, personSettingRepository,
                visibilityGuard, softDeleteSupport);
        FamilyContext.set(new AuthUser(VIEWER, 10L, FAMILY_ID, FamilyRole.PARENT));
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    private ScheduleRequest timed(Instant start, Instant end, List<Long> subjects) {
        return new ScheduleRequest("회의", null, null, start, end, null, null, false,
                Visibility.PRIVATE, ScheduleType.EVENT, null, null, subjects, List.of());
    }

    private ScheduleRequest allDay(LocalDate startDate, Instant startedAt) {
        return new ScheduleRequest("기념일", null, null, startedAt, null, startDate, null, true,
                Visibility.FAMILY, ScheduleType.EVENT, null, null, List.of(), List.of());
    }

    @Test
    void 종일일정인데_startDate_없으면_422() {
        assertThatThrownBy(() -> service.create(allDay(null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 종일일정인데_시각이_있으면_422() {
        assertThatThrownBy(() -> service.create(allDay(LocalDate.parse("2026-07-01"), Instant.parse("2026-07-01T00:00:00Z"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 시점일정인데_startedAt_없으면_422() {
        assertThatThrownBy(() -> service.create(timed(null, null, List.of())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void subject가_가족구성원이_아니면_422() {
        when(membershipRepository.existsByFamily_IdAndPerson_Id(FAMILY_ID, 99L)).thenReturn(false);
        assertThatThrownBy(() -> service.create(
                timed(Instant.parse("2026-07-01T01:00:00Z"), null, List.of(99L))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 타인의_SHARED_PERSONAL_은_SUMMARY면_바쁨으로_마스킹() {
        // createdBy=null(미설정) → 뷰어(1)와 다름 → "타인" 으로 간주
        Schedule shared = new Schedule(FAMILY_ID, "비밀상담", "상세내용", "병원",
                Instant.parse("2026-07-01T01:00:00Z"), null, null, null, false,
                Visibility.SHARED_PERSONAL, ScheduleType.EVENT, null, null);
        when(personRepository.findById(VIEWER)).thenReturn(Optional.of(new Person("나", null, "Asia/Seoul")));
        when(scheduleRepository.search(any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(shared));
        when(subjectRepository.findByScheduleIdInAndFamilyId(any(), any())).thenReturn(List.of());
        when(participantRepository.findByScheduleIdInAndFamilyId(any(), any())).thenReturn(List.of());
        when(personSettingRepository.findByPersonId(VIEWER))
                .thenReturn(Optional.of(new PersonSetting(VIEWER, DetailLevel.SUMMARY)));

        List<ScheduleResponse> result = service.list(null, null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("바쁨");
        assertThat(result.getFirst().description()).isNull();
        assertThat(result.getFirst().location()).isNull();
        assertThat(result.getFirst().startedAt()).isEqualTo(Instant.parse("2026-07-01T01:00:00Z")); // 시간은 유지
    }

    @Test
    void 타인의_SHARED_PERSONAL_도_FULL이면_마스킹_안함() {
        Schedule shared = new Schedule(FAMILY_ID, "비밀상담", "상세내용", "병원",
                Instant.parse("2026-07-01T01:00:00Z"), null, null, null, false,
                Visibility.SHARED_PERSONAL, ScheduleType.EVENT, null, null);
        when(personRepository.findById(VIEWER)).thenReturn(Optional.of(new Person("나", null, "Asia/Seoul")));
        when(scheduleRepository.search(any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(shared));
        when(subjectRepository.findByScheduleIdInAndFamilyId(any(), any())).thenReturn(List.of());
        when(participantRepository.findByScheduleIdInAndFamilyId(any(), any())).thenReturn(List.of());
        when(personSettingRepository.findByPersonId(VIEWER))
                .thenReturn(Optional.of(new PersonSetting(VIEWER, DetailLevel.FULL)));

        List<ScheduleResponse> result = service.list(null, null, null, null, null);

        assertThat(result.getFirst().title()).isEqualTo("비밀상담");
    }
}
