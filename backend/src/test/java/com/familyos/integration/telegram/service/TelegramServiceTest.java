package com.familyos.integration.telegram.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.FamilyRole;
import com.familyos.common.domain.Visibility;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.integration.telegram.entity.TelegramPersonMap;
import com.familyos.integration.telegram.repository.TelegramPersonMapRepository;
import com.familyos.ledger.dto.TransactionRequest;
import com.familyos.ledger.entity.TransactionSource;
import com.familyos.ledger.entity.TransactionType;
import com.familyos.ledger.service.TransactionService;
import com.familyos.ledger.service.TransactionStatisticsService;
import com.familyos.person.entity.Family;
import com.familyos.person.entity.FamilyMembership;
import com.familyos.person.entity.Person;
import com.familyos.person.repository.FamilyMembershipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TelegramService: 행동 대행(매핑된 Person 으로 FamilyContext 세팅) + source=TELEGRAM 강제 + 연결 규칙.
 */
@ExtendWith(MockitoExtension.class)
class TelegramServiceTest {

    private static final Long TG_USER = 999L;
    private static final Long PERSON_ID = 5L;
    private static final Long FAMILY_ID = 1L;

    @Mock TelegramPersonMapRepository mapRepository;
    @Mock FamilyMembershipRepository membershipRepository;
    @Mock TransactionService transactionService;
    @Mock TransactionStatisticsService statisticsService;
    @Mock com.familyos.timeline.service.TimelineService timelineService;
    @Mock com.familyos.schedule.service.ScheduleService scheduleService;
    @Mock SoftDeleteSupport softDeleteSupport;

    TelegramService service;

    @BeforeEach
    void setUp() {
        service = new TelegramService(mapRepository, membershipRepository,
                transactionService, statisticsService, timelineService, scheduleService, softDeleteSupport);
    }

    @AfterEach
    void tearDown() {
        FamilyContext.clear();
    }

    private TransactionRequest baseExpense() {
        return new TransactionRequest(TransactionType.EXPENSE, new BigDecimal("5000"), "KRW",
                1L, null, null, null, Visibility.PRIVATE, Instant.parse("2026-06-19T02:30:00Z"),
                "스타벅스", null, TransactionSource.MANUAL, null);
    }

    @Test
    void 거래등록시_매핑된_Person으로_대행하고_source는_TELEGRAM으로_강제한다() {
        when(mapRepository.findByTelegramUserId(TG_USER))
                .thenReturn(Optional.of(new TelegramPersonMap(FAMILY_ID, PERSON_ID, TG_USER)));
        when(membershipRepository.findByFamily_IdAndPerson_Id(FAMILY_ID, PERSON_ID))
                .thenReturn(Optional.of(new FamilyMembership(
                        new Family("우리집"), new Person("사리", null, null), FamilyRole.PARENT, Instant.now())));

        // create 호출 시점의 FamilyContext 를 포착
        AtomicReference<AuthUser> ctxDuring = new AtomicReference<>();
        when(transactionService.create(any())).thenAnswer(inv -> {
            ctxDuring.set(FamilyContext.getOrNull());
            return null;
        });

        service.registerTransaction(TG_USER, baseExpense());

        // 대행 주체 = 매핑된 Person/family
        assertThat(ctxDuring.get()).isNotNull();
        assertThat(ctxDuring.get().personId()).isEqualTo(PERSON_ID);
        assertThat(ctxDuring.get().familyId()).isEqualTo(FAMILY_ID);
        // source 강제 TELEGRAM
        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).create(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(TransactionSource.TELEGRAM);
        // 호출 후 컨텍스트 정리
        assertThat(FamilyContext.getOrNull()).isNull();
    }

    @Test
    void 연결되지_않은_텔레그램_사용자는_404() {
        when(mapRepository.findByTelegramUserId(TG_USER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.registerTransaction(TG_USER, baseExpense()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 이미_연결된_텔레그램_사용자면_link는_409() {
        FamilyContext.set(new AuthUser(PERSON_ID, 10L, FAMILY_ID, FamilyRole.PARENT));
        when(mapRepository.existsByTelegramUserId(TG_USER)).thenReturn(true);

        assertThatThrownBy(() -> service.link(TG_USER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(com.familyos.common.error.ErrorCode.CONFLICT);
    }
}
