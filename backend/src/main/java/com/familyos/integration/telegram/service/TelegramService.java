package com.familyos.integration.telegram.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.ErrorCode;
import com.familyos.common.error.NotFoundException;
import com.familyos.integration.telegram.entity.TelegramPersonMap;
import com.familyos.integration.telegram.repository.TelegramPersonMapRepository;
import com.familyos.ledger.dto.StatisticsResponse;
import com.familyos.ledger.dto.TransactionRequest;
import com.familyos.ledger.dto.TransactionResponse;
import com.familyos.ledger.entity.TransactionSource;
import com.familyos.ledger.service.TransactionService;
import com.familyos.ledger.service.TransactionStatisticsService;
import com.familyos.person.entity.FamilyMembership;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.schedule.dto.ScheduleResponse;
import com.familyos.schedule.entity.ScheduleType;
import com.familyos.schedule.service.ScheduleService;
import com.familyos.timeline.dto.TimelineResponse;
import com.familyos.timeline.service.TimelineService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * 텔레그램/Hermes 연동.
 *
 * <ul>
 *   <li><b>연결</b>(link/unlink): 로그인 사용자(JWT, FamilyContext)가 본인 telegram_user_id 를 매핑</li>
 *   <li><b>행동 대행</b>(registerTransaction/statistics): 서비스 토큰으로 들어온 Hermes 호출에 대해
 *       telegram_user_id → Person 으로 행동 주체를 정한 뒤 FamilyContext 를 세팅하고 기존 도메인 서비스를 호출.
 *       family/visibility/거래규칙은 그대로 적용된다.</li>
 * </ul>
 */
@Service
public class TelegramService {

    private final TelegramPersonMapRepository mapRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final TransactionService transactionService;
    private final TransactionStatisticsService statisticsService;
    private final TimelineService timelineService;
    private final ScheduleService scheduleService;
    private final SoftDeleteSupport softDeleteSupport;

    public TelegramService(TelegramPersonMapRepository mapRepository,
                           FamilyMembershipRepository membershipRepository,
                           TransactionService transactionService,
                           TransactionStatisticsService statisticsService,
                           TimelineService timelineService,
                           ScheduleService scheduleService,
                           SoftDeleteSupport softDeleteSupport) {
        this.mapRepository = mapRepository;
        this.membershipRepository = membershipRepository;
        this.transactionService = transactionService;
        this.statisticsService = statisticsService;
        this.timelineService = timelineService;
        this.scheduleService = scheduleService;
        this.softDeleteSupport = softDeleteSupport;
    }

    // ---- 연결 (로그인 사용자) ----

    @Transactional
    public void link(Long telegramUserId) {
        AuthUser user = FamilyContext.require();
        if (mapRepository.existsByTelegramUserId(telegramUserId)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 연결된 텔레그램 사용자입니다.");
        }
        mapRepository.save(new TelegramPersonMap(user.familyId(), user.personId(), telegramUserId));
    }

    @Transactional
    public void unlink() {
        AuthUser user = FamilyContext.require();
        mapRepository.findByFamilyIdAndPersonId(user.familyId(), user.personId())
                .ifPresent(m -> softDeleteSupport.softDelete(m, mapRepository));
    }

    // ---- 행동 대행 (Hermes, 서비스 토큰) ----

    /** Hermes 거래 등록. source 는 TELEGRAM 으로 강제. 매핑된 Person 으로 동작. */
    public TransactionResponse registerTransaction(Long telegramUserId, TransactionRequest base) {
        TransactionRequest forced = withTelegramSource(base);
        return actingAs(telegramUserId, () -> transactionService.create(forced));
    }

    /** Hermes 조회(통계). 매핑된 Person 권한 범위로 집계. */
    public StatisticsResponse statistics(Long telegramUserId, @Nullable Instant from, @Nullable Instant to,
                                         @Nullable String scope) {
        return actingAs(telegramUserId, () -> statisticsService.statistics(from, to, scope));
    }

    /** Hermes 조회(통합 타임라인). "오늘 타임라인" 등. */
    public TimelineResponse timeline(Long telegramUserId, @Nullable LocalDate date,
                                     @Nullable LocalDate from, @Nullable LocalDate to) {
        return actingAs(telegramUserId, () -> timelineService.timeline(date, from, to));
    }

    /** Hermes 조회(일정). "내일 일정?" 등. visibility 권한 범위 내. */
    public List<ScheduleResponse> schedules(Long telegramUserId, @Nullable Instant from, @Nullable Instant to,
                                            @Nullable ScheduleType type, @Nullable String scope) {
        return actingAs(telegramUserId, () -> scheduleService.list(from, to, type, scope));
    }

    // ---- 내부 ----

    /** telegram_user_id 로 행동 주체(Person/family/role)를 결정해 FamilyContext 를 세팅하고 action 실행. */
    private <T> T actingAs(Long telegramUserId, Supplier<T> action) {
        AuthUser actor = resolveActor(telegramUserId);
        AuthUser previous = FamilyContext.getOrNull();
        FamilyContext.set(actor);
        try {
            return action.get();
        } finally {
            if (previous != null) {
                FamilyContext.set(previous);
            } else {
                FamilyContext.clear();
            }
        }
    }

    private AuthUser resolveActor(Long telegramUserId) {
        TelegramPersonMap map = mapRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new NotFoundException("연결된 텔레그램 사용자가 없습니다."));
        FamilyMembership membership = membershipRepository
                .findByFamily_IdAndPerson_Id(map.getFamilyId(), map.getPersonId())
                .orElseThrow(() -> new NotFoundException("구성원 정보를 찾을 수 없습니다."));
        // accountId 는 로그인 계정 개념이라 텔레그램 대행에는 불필요(null). 도메인 서비스는 personId/familyId/role 만 사용.
        return new AuthUser(map.getPersonId(), null, map.getFamilyId(), membership.getRole());
    }

    private TransactionRequest withTelegramSource(TransactionRequest b) {
        return new TransactionRequest(
                b.transactionType(), b.amount(), b.currency(),
                b.sourceAccountId(), b.targetAccountId(), b.categoryId(), b.subjectPersonId(),
                b.visibility(), b.occurredAt(), b.memo(), b.collectionId(),
                TransactionSource.TELEGRAM, b.tagIds());
    }
}
