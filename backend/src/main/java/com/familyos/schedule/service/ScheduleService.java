package com.familyos.schedule.service;

import com.familyos.collection.repository.CollectionRepository;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.domain.Visibility;
import com.familyos.common.entity.VisibleResource;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
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
import com.familyos.schedule.entity.ScheduleParticipant;
import com.familyos.schedule.entity.ScheduleSubject;
import com.familyos.schedule.entity.ScheduleType;
import com.familyos.schedule.repository.ScheduleParticipantRepository;
import com.familyos.schedule.repository.ScheduleRepository;
import com.familyos.schedule.repository.ScheduleSubjectRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일정 관리.
 *
 * <p>핵심: 시각/날짜 이원화 검증(1-6), visibility 4단계(SHARED_PERSONAL 포함, DB WHERE 필터),
 * subject/participant alive 검증, 타인 SHARED_PERSONAL 의 SUMMARY 마스킹.
 * google 동기화 필드는 클라가 못 바꾼다(요청 DTO 미포함).
 */
@Service
@Transactional(readOnly = true)
public class ScheduleService {

    private static final String BUSY_LABEL = "바쁨";

    private final ScheduleRepository scheduleRepository;
    private final ScheduleSubjectRepository subjectRepository;
    private final ScheduleParticipantRepository participantRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final CollectionRepository collectionRepository;
    private final PersonRepository personRepository;
    private final PersonSettingRepository personSettingRepository;
    private final VisibilityGuard visibilityGuard;
    private final SoftDeleteSupport softDeleteSupport;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           ScheduleSubjectRepository subjectRepository,
                           ScheduleParticipantRepository participantRepository,
                           FamilyMembershipRepository membershipRepository,
                           CollectionRepository collectionRepository,
                           PersonRepository personRepository,
                           PersonSettingRepository personSettingRepository,
                           VisibilityGuard visibilityGuard,
                           SoftDeleteSupport softDeleteSupport) {
        this.scheduleRepository = scheduleRepository;
        this.subjectRepository = subjectRepository;
        this.participantRepository = participantRepository;
        this.membershipRepository = membershipRepository;
        this.collectionRepository = collectionRepository;
        this.personRepository = personRepository;
        this.personSettingRepository = personSettingRepository;
        this.visibilityGuard = visibilityGuard;
        this.softDeleteSupport = softDeleteSupport;
    }

    public List<ScheduleResponse> list(@Nullable Instant from, @Nullable Instant to,
                                       @Nullable ScheduleType type, @Nullable String scope) {
        AuthUser user = FamilyContext.require();
        ZoneId zone = viewerZone(user.personId());
        LocalDate fromDate = from == null ? null : from.atZone(zone).toLocalDate();
        LocalDate toDate = to == null ? null : to.atZone(zone).toLocalDate();
        Visibility scopeVisibility = parseScope(scope);

        List<Schedule> schedules = scheduleRepository.search(
                user.familyId(), user.personId(), user.isParent(), type, scopeVisibility,
                from, to, fromDate, toDate);
        if (schedules.isEmpty()) {
            return List.of();
        }

        List<Long> ids = schedules.stream().map(Schedule::getId).toList();
        Map<Long, List<Long>> subjectsBySchedule = groupPersonIds(
                subjectRepository.findByScheduleIdInAndFamilyId(ids, user.familyId()),
                ScheduleSubject::getScheduleId, ScheduleSubject::getPersonId);
        Map<Long, List<Long>> participantsBySchedule = groupPersonIds(
                participantRepository.findByScheduleIdInAndFamilyId(ids, user.familyId()),
                ScheduleParticipant::getScheduleId, ScheduleParticipant::getPersonId);

        DetailLevel level = viewerDetailLevel(user.personId());
        return schedules.stream()
                .map(s -> toResponse(s,
                        subjectsBySchedule.getOrDefault(s.getId(), List.of()),
                        participantsBySchedule.getOrDefault(s.getId(), List.of()),
                        user.personId(), level))
                .toList();
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest req) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();

        validateDualization(req);
        Set<Long> subjects = new LinkedHashSet<>(req.subjectsOrEmpty());
        Set<Long> participants = new LinkedHashSet<>(req.participantsOrEmpty());
        validatePersonsAlive(familyId, subjects);
        validatePersonsAlive(familyId, participants);
        validateCollectionAlive(familyId, req.collectionId());

        Schedule schedule = scheduleRepository.save(new Schedule(
                familyId, req.title(), req.description(), req.location(),
                req.startedAt(), req.endedAt(), req.startDate(), endDateOrStart(req), req.allDay(),
                req.visibility(), req.scheduleType(), req.recurrenceRule(), req.collectionId()));
        schedule.markPendingSync(); // 로컬 생성 → 구글 전송 대기(연결된 경우 push 시 전송)

        insertSubjects(familyId, schedule.getId(), subjects);
        insertParticipants(familyId, schedule.getId(), participants);

        return toResponse(schedule, new ArrayList<>(subjects), new ArrayList<>(participants),
                user.personId(), DetailLevel.FULL); // 작성자 본인이므로 마스킹 없음
    }

    @Transactional
    public ScheduleResponse update(Long id, ScheduleRequest req) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();
        Schedule schedule = scheduleRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("일정", id));

        // 작성자 본인만 수정 (위반 404). subject 는 보기 권한만.
        Set<Long> currentSubjects = personIdSet(subjectRepository.findByScheduleIdAndFamilyId(id, familyId),
                ScheduleSubject::getPersonId);
        visibilityGuard.assertCanEdit(new VisibleResource(schedule.getVisibility(), schedule.getCreatedBy(), currentSubjects), user);

        validateDualization(req);
        Set<Long> subjects = new LinkedHashSet<>(req.subjectsOrEmpty());
        Set<Long> participants = new LinkedHashSet<>(req.participantsOrEmpty());
        validatePersonsAlive(familyId, subjects);
        validatePersonsAlive(familyId, participants);
        validateCollectionAlive(familyId, req.collectionId());

        schedule.update(req.title(), req.description(), req.location(),
                req.startedAt(), req.endedAt(), req.startDate(), endDateOrStart(req), req.allDay(),
                req.visibility(), req.scheduleType(), req.recurrenceRule(), req.collectionId());
        schedule.markPendingSync(); // 로컬 수정 → 구글 재전송 대기

        syncSubjects(familyId, id, subjects);
        syncParticipants(familyId, id, participants);

        return toResponse(schedule, new ArrayList<>(subjects), new ArrayList<>(participants),
                user.personId(), DetailLevel.FULL);
    }

    @Transactional
    public void delete(Long id) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();
        Schedule schedule = scheduleRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("일정", id));
        Set<Long> currentSubjects = personIdSet(subjectRepository.findByScheduleIdAndFamilyId(id, familyId),
                ScheduleSubject::getPersonId);
        visibilityGuard.assertCanEdit(new VisibleResource(schedule.getVisibility(), schedule.getCreatedBy(), currentSubjects), user);

        // 연결 행도 함께 soft-delete
        subjectRepository.findByScheduleIdAndFamilyId(id, familyId)
                .forEach(s -> softDeleteSupport.softDelete(s, subjectRepository));
        participantRepository.findByScheduleIdAndFamilyId(id, familyId)
                .forEach(p -> softDeleteSupport.softDelete(p, participantRepository));
        softDeleteSupport.softDelete(schedule, scheduleRepository);
    }

    /** TODO 완료 토글. 작성자 본인만. */
    @Transactional
    public ScheduleResponse toggleDone(Long id) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();
        Schedule schedule = scheduleRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("일정", id));
        Set<Long> currentSubjects = personIdSet(subjectRepository.findByScheduleIdAndFamilyId(id, familyId),
                ScheduleSubject::getPersonId);
        visibilityGuard.assertCanEdit(new VisibleResource(schedule.getVisibility(), schedule.getCreatedBy(), currentSubjects), user);

        schedule.toggleDone();

        List<Long> subjects = new ArrayList<>(currentSubjects);
        List<Long> participants = personIdSet(participantRepository.findByScheduleIdAndFamilyId(id, familyId),
                ScheduleParticipant::getPersonId).stream().toList();
        return toResponse(schedule, subjects, participants, user.personId(), DetailLevel.FULL);
    }

    // ---- 검증 ----

    /** 시각/날짜 이원화(1-6). allDay → date 만, 시점 → datetime 만. 위반 422. */
    private void validateDualization(ScheduleRequest req) {
        if (req.allDay()) {
            if (req.startDate() == null) {
                throw new BusinessException("종일 일정은 startDate 가 필요합니다.");
            }
            if (req.startedAt() != null || req.endedAt() != null) {
                throw new BusinessException("종일 일정은 시각(startedAt/endedAt)을 가질 수 없습니다.");
            }
            if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
                throw new BusinessException("endDate 는 startDate 보다 빠를 수 없습니다.");
            }
        } else {
            if (req.startedAt() == null) {
                throw new BusinessException("시점 일정은 startedAt 이 필요합니다.");
            }
            if (req.startDate() != null || req.endDate() != null) {
                throw new BusinessException("시점 일정은 날짜(startDate/endDate)를 가질 수 없습니다.");
            }
            if (req.endedAt() != null && req.endedAt().isBefore(req.startedAt())) {
                throw new BusinessException("endedAt 은 startedAt 보다 빠를 수 없습니다.");
            }
        }
    }

    private void validatePersonsAlive(Long familyId, Set<Long> personIds) {
        for (Long personId : personIds) {
            if (!membershipRepository.existsByFamily_IdAndPerson_Id(familyId, personId)) {
                throw new BusinessException("대상/참석자는 현재 가족의 구성원(유효)이어야 합니다: personId=" + personId);
            }
        }
    }

    /** collection_id 참조 alive 검증(1-4). FK 가 못 막는 soft-delete 묶음 참조 차단. */
    private void validateCollectionAlive(Long familyId, @Nullable Long collectionId) {
        if (collectionId != null && collectionRepository.findByIdAndFamilyId(collectionId, familyId).isEmpty()) {
            throw new BusinessException("묶음(collection)이 존재하지 않거나 삭제되었습니다.");
        }
    }

    private @Nullable LocalDate endDateOrStart(ScheduleRequest req) {
        if (!req.allDay()) {
            return null;
        }
        return req.endDate() != null ? req.endDate() : req.startDate();
    }

    // ---- subject/participant 동기화 ----

    private void insertSubjects(Long familyId, Long scheduleId, Set<Long> personIds) {
        personIds.forEach(pid -> subjectRepository.save(new ScheduleSubject(familyId, scheduleId, pid)));
    }

    private void insertParticipants(Long familyId, Long scheduleId, Set<Long> personIds) {
        personIds.forEach(pid -> participantRepository.save(new ScheduleParticipant(familyId, scheduleId, pid)));
    }

    private void syncSubjects(Long familyId, Long scheduleId, Set<Long> target) {
        List<ScheduleSubject> existing = subjectRepository.findByScheduleIdAndFamilyId(scheduleId, familyId);
        Set<Long> existingIds = personIdSet(existing, ScheduleSubject::getPersonId);
        existing.stream().filter(s -> !target.contains(s.getPersonId()))
                .forEach(s -> softDeleteSupport.softDelete(s, subjectRepository));
        target.stream().filter(pid -> !existingIds.contains(pid))
                .forEach(pid -> subjectRepository.save(new ScheduleSubject(familyId, scheduleId, pid)));
    }

    private void syncParticipants(Long familyId, Long scheduleId, Set<Long> target) {
        List<ScheduleParticipant> existing = participantRepository.findByScheduleIdAndFamilyId(scheduleId, familyId);
        Set<Long> existingIds = personIdSet(existing, ScheduleParticipant::getPersonId);
        existing.stream().filter(p -> !target.contains(p.getPersonId()))
                .forEach(p -> softDeleteSupport.softDelete(p, participantRepository));
        target.stream().filter(pid -> !existingIds.contains(pid))
                .forEach(pid -> participantRepository.save(new ScheduleParticipant(familyId, scheduleId, pid)));
    }

    // ---- 응답/마스킹 ----

    private ScheduleResponse toResponse(Schedule s, List<Long> subjects, List<Long> participants,
                                        Long viewerPersonId, DetailLevel level) {
        boolean mask = s.getVisibility() == Visibility.SHARED_PERSONAL
                && !viewerPersonId.equals(s.getCreatedBy())
                && level == DetailLevel.SUMMARY;

        return new ScheduleResponse(
                s.getId(),
                mask ? BUSY_LABEL : s.getTitle(),
                mask ? null : s.getDescription(),
                mask ? null : s.getLocation(),
                s.getStartedAt(), s.getEndedAt(), s.getStartDate(), s.getEndDate(), s.isAllDay(),
                s.getVisibility(), s.getScheduleType(), s.isDone(), s.getRecurrenceRule(),
                subjects, participants, s.getCollectionId(), s.getSyncStatus());
    }

    // ---- 헬퍼 ----

    private @Nullable Visibility parseScope(@Nullable String scope) {
        if (scope == null || scope.isBlank() || scope.equalsIgnoreCase("ALL")) {
            return null;
        }
        return switch (scope.toUpperCase()) {
            case "PRIVATE" -> Visibility.PRIVATE;
            case "PARENTS" -> Visibility.PARENTS;
            case "SHARED_PERSONAL" -> Visibility.SHARED_PERSONAL;
            case "FAMILY" -> Visibility.FAMILY;
            default -> throw new BusinessException("scope 가 올바르지 않습니다.");
        };
    }

    private ZoneId viewerZone(Long personId) {
        return personRepository.findById(personId)
                .map(Person::getTimezone)
                .map(ZoneId::of)
                .orElse(ZoneId.of("Asia/Seoul"));
    }

    private DetailLevel viewerDetailLevel(Long personId) {
        return personSettingRepository.findByPersonId(personId)
                .map(PersonSetting::getSharedScheduleDetailLevel)
                .orElse(DetailLevel.FULL);
    }

    private <T> Map<Long, List<Long>> groupPersonIds(List<T> rows,
                                                     java.util.function.Function<T, Long> keyFn,
                                                     java.util.function.Function<T, Long> personFn) {
        return rows.stream().collect(Collectors.groupingBy(keyFn,
                Collectors.mapping(personFn, Collectors.toList())));
    }

    private <T> Set<Long> personIdSet(List<T> rows, java.util.function.Function<T, Long> personFn) {
        return rows.stream().map(personFn).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
