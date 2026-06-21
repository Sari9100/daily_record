package com.familyos.integration.telegram.service;

import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.NotFoundException;
import com.familyos.integration.telegram.entity.TelegramPersonMap;
import com.familyos.integration.telegram.repository.TelegramPersonMapRepository;
import com.familyos.person.entity.FamilyMembership;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.schedule.dto.ScheduleRequest;
import com.familyos.schedule.dto.ScheduleResponse;
import com.familyos.schedule.service.ScheduleService;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Hermes(텔레그램) 일정 쓰기 대행. {@link TelegramService#registerTransaction} 의 actingAs 패턴을 따라
 * telegram_user_id → Person 으로 {@link FamilyContext} 를 세팅한 뒤 기존 {@link ScheduleService} 를 호출한다.
 *
 * <p>family 격리 · visibility(작성자/마스킹) · subject/participant·collection alive · 시각/날짜 이원화 검증은
 * 모두 {@link ScheduleService} 가 그대로 적용한다(별도 우회 없음).
 */
@Service
public class TelegramScheduleService {

    private final TelegramPersonMapRepository mapRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final ScheduleService scheduleService;

    public TelegramScheduleService(TelegramPersonMapRepository mapRepository,
                                   FamilyMembershipRepository membershipRepository,
                                   ScheduleService scheduleService) {
        this.mapRepository = mapRepository;
        this.membershipRepository = membershipRepository;
        this.scheduleService = scheduleService;
    }

    /** Hermes 일정 등록. 매핑된 Person 으로 동작. */
    public ScheduleResponse create(Long telegramUserId, ScheduleRequest req) {
        return actingAs(telegramUserId, () -> scheduleService.create(req));
    }

    /** Hermes 일정 수정. 작성자 본인만(위반 404 — ScheduleService 가 판정). */
    public ScheduleResponse update(Long telegramUserId, Long id, ScheduleRequest req) {
        return actingAs(telegramUserId, () -> scheduleService.update(id, req));
    }

    /** Hermes 일정 삭제(soft). 작성자 본인만. */
    public void delete(Long telegramUserId, Long id) {
        actingAs(telegramUserId, () -> {
            scheduleService.delete(id);
            return null;
        });
    }

    // ---- 내부 (TelegramService.actingAs 와 동일 패턴) ----

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
        return new AuthUser(map.getPersonId(), null, map.getFamilyId(), membership.getRole());
    }
}
