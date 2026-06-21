package com.familyos.integration.telegram.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.common.domain.Visibility;
import com.familyos.common.error.NotFoundException;
import com.familyos.integration.telegram.entity.TelegramPersonMap;
import com.familyos.integration.telegram.repository.TelegramPersonMapRepository;
import com.familyos.person.entity.Family;
import com.familyos.person.entity.FamilyMembership;
import com.familyos.person.entity.Person;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.schedule.dto.ScheduleRequest;
import com.familyos.schedule.entity.ScheduleType;
import com.familyos.schedule.service.ScheduleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TelegramScheduleService: 텔레그램 일정 쓰기 대행.
 *
 * <p>이 클래스의 책임만 단위 검증한다 — telegram_user_id → 매핑된 Person 으로 FamilyContext 세팅(= family 격리 스코프),
 * ScheduleService 위임, 미매핑 404, 호출 후 컨텍스트 정리/복원.
 * 일정 규칙(이원화·subject/collection alive 422·visibility 404)은 위임 대상 {@link ScheduleService}
 * 가 적용하며 {@code ScheduleServiceTest} 가 별도로 검증한다(여기서 재검증하지 않음 — TelegramServiceTest 와 동일 철학).
 */
@ExtendWith(MockitoExtension.class)
class TelegramScheduleServiceTest {

    private static final Long TG_USER = 999L;
    private static final Long PERSON_ID = 5L;
    private static final Long FAMILY_ID = 1L;
    private static final Long SCHEDULE_ID = 42L;

    @Mock TelegramPersonMapRepository mapRepository;
    @Mock FamilyMembershipRepository membershipRepository;
    @Mock ScheduleService scheduleService;

    TelegramScheduleService service;

    @BeforeEach
    void setUp() {
        service = new TelegramScheduleService(mapRepository, membershipRepository, scheduleService);
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    private void mappedAsParent() {
        when(mapRepository.findByTelegramUserId(TG_USER))
                .thenReturn(Optional.of(new TelegramPersonMap(FAMILY_ID, PERSON_ID, TG_USER)));
        when(membershipRepository.findByFamily_IdAndPerson_Id(FAMILY_ID, PERSON_ID))
                .thenReturn(Optional.of(new FamilyMembership(
                        new Family("우리집"), new Person("사리", null, null), FamilyRole.PARENT, Instant.now())));
    }

    private ScheduleRequest timedEvent() {
        return new ScheduleRequest("회의", null, null,
                Instant.parse("2026-06-21T01:00:00Z"), null, null, null, false,
                Visibility.PRIVATE, ScheduleType.EVENT, null, null, null, null);
    }

    @Test
    void create_매핑된_Person으로_대행하고_컨텍스트를_정리한다() {
        mappedAsParent();
        AtomicReference<AuthUser> ctxDuring = new AtomicReference<>();
        when(scheduleService.create(any())).thenAnswer(inv -> {
            ctxDuring.set(FamilyContext.getOrNull());
            return null;
        });

        service.create(TG_USER, timedEvent());

        assertThat(ctxDuring.get()).isNotNull();
        assertThat(ctxDuring.get().personId()).isEqualTo(PERSON_ID);
        assertThat(ctxDuring.get().familyId()).isEqualTo(FAMILY_ID);
        assertThat(ctxDuring.get().role()).isEqualTo(FamilyRole.PARENT);
        verify(scheduleService).create(any());
        assertThat(FamilyContext.getOrNull()).isNull(); // 대행 후 정리
    }

    @Test
    void update_매핑된_Person으로_대행하고_id와_요청을_그대로_위임한다() {
        mappedAsParent();
        AtomicReference<AuthUser> ctxDuring = new AtomicReference<>();
        when(scheduleService.update(anyLong(), any())).thenAnswer(inv -> {
            ctxDuring.set(FamilyContext.getOrNull());
            return null;
        });

        service.update(TG_USER, SCHEDULE_ID, timedEvent());

        assertThat(ctxDuring.get()).isNotNull();
        assertThat(ctxDuring.get().personId()).isEqualTo(PERSON_ID);
        verify(scheduleService).update(eq(SCHEDULE_ID), any());
        assertThat(FamilyContext.getOrNull()).isNull();
    }

    @Test
    void delete_매핑된_Person으로_대행하고_scheduleService_delete를_호출한다() {
        mappedAsParent();
        AtomicReference<AuthUser> ctxDuring = new AtomicReference<>();
        doAnswer(inv -> {
            ctxDuring.set(FamilyContext.getOrNull());
            return null;
        }).when(scheduleService).delete(SCHEDULE_ID);

        service.delete(TG_USER, SCHEDULE_ID);

        assertThat(ctxDuring.get()).isNotNull();
        assertThat(ctxDuring.get().familyId()).isEqualTo(FAMILY_ID);
        verify(scheduleService).delete(SCHEDULE_ID);
        assertThat(FamilyContext.getOrNull()).isNull();
    }

    @Test
    void 연결되지_않은_텔레그램_사용자는_404_이고_위임하지_않는다() {
        when(mapRepository.findByTelegramUserId(TG_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TG_USER, timedEvent()))
                .isInstanceOf(NotFoundException.class);
        verify(scheduleService, never()).create(any());
    }

    @Test
    void 구성원_정보가_없으면_404() {
        when(mapRepository.findByTelegramUserId(TG_USER))
                .thenReturn(Optional.of(new TelegramPersonMap(FAMILY_ID, PERSON_ID, TG_USER)));
        when(membershipRepository.findByFamily_IdAndPerson_Id(FAMILY_ID, PERSON_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(TG_USER, timedEvent()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 대행_전_컨텍스트는_대행_후_복원된다() {
        mappedAsParent();
        AuthUser previous = new AuthUser(100L, 200L, 9L, FamilyRole.PARENT);
        FamilyContext.set(previous);
        when(scheduleService.create(any())).thenReturn(null);

        service.create(TG_USER, timedEvent());

        assertThat(FamilyContext.getOrNull()).isSameAs(previous);
    }
}
