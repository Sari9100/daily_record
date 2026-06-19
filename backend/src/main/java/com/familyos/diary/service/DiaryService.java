package com.familyos.diary.service;

import com.familyos.collection.repository.CollectionRepository;
import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.AuthUser;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.domain.Visibility;
import com.familyos.common.entity.VisibleResource;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.common.security.VisibilityGuard;
import com.familyos.diary.dto.DiaryRequest;
import com.familyos.diary.dto.DiaryResponse;
import com.familyos.diary.dto.DiaryResponse.PhotoRef;
import com.familyos.diary.entity.Diary;
import com.familyos.diary.entity.DiarySubject;
import com.familyos.diary.repository.DiaryRepository;
import com.familyos.diary.repository.DiarySubjectRepository;
import com.familyos.person.repository.FamilyMembershipRepository;
import com.familyos.photo.entity.Photo;
import com.familyos.photo.repository.PhotoRepository;
import com.familyos.storage.UrlSigner;
import com.familyos.tag.entity.DiaryTag;
import com.familyos.tag.entity.Tag;
import com.familyos.tag.repository.DiaryTagRepository;
import com.familyos.tag.repository.TagRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 일상기록 관리. visibility 4단계(SHARED_PERSONAL=author+subject, DB WHERE 필터).
 * subject/collection/tag alive 검증(1-4). 수정/삭제는 작성자 본인만. photos 는 photo 도메인에서 채움.
 */
@Service
@Transactional(readOnly = true)
public class DiaryService {

    private final DiaryRepository diaryRepository;
    private final DiarySubjectRepository subjectRepository;
    private final DiaryTagRepository diaryTagRepository;
    private final TagRepository tagRepository;
    private final FamilyMembershipRepository membershipRepository;
    private final CollectionRepository collectionRepository;
    private final PhotoRepository photoRepository;
    private final UrlSigner urlSigner;
    private final VisibilityGuard visibilityGuard;
    private final SoftDeleteSupport softDeleteSupport;

    public DiaryService(DiaryRepository diaryRepository,
                        DiarySubjectRepository subjectRepository,
                        DiaryTagRepository diaryTagRepository,
                        TagRepository tagRepository,
                        FamilyMembershipRepository membershipRepository,
                        CollectionRepository collectionRepository,
                        PhotoRepository photoRepository,
                        UrlSigner urlSigner,
                        VisibilityGuard visibilityGuard,
                        SoftDeleteSupport softDeleteSupport) {
        this.diaryRepository = diaryRepository;
        this.subjectRepository = subjectRepository;
        this.diaryTagRepository = diaryTagRepository;
        this.tagRepository = tagRepository;
        this.membershipRepository = membershipRepository;
        this.collectionRepository = collectionRepository;
        this.photoRepository = photoRepository;
        this.urlSigner = urlSigner;
        this.visibilityGuard = visibilityGuard;
        this.softDeleteSupport = softDeleteSupport;
    }

    public List<DiaryResponse> list(@Nullable LocalDate from, @Nullable LocalDate to, @Nullable String scope) {
        AuthUser user = FamilyContext.require();
        Visibility scopeVisibility = parseScope(scope);
        List<Diary> diaries = diaryRepository.search(
                user.familyId(), user.personId(), user.isParent(), scopeVisibility, from, to);
        if (diaries.isEmpty()) {
            return List.of();
        }

        List<Long> ids = diaries.stream().map(Diary::getId).toList();
        Map<Long, List<Long>> subjectsByDiary = subjectRepository.findByDiaryIdInAndFamilyId(ids, user.familyId())
                .stream().collect(Collectors.groupingBy(DiarySubject::getDiaryId,
                        Collectors.mapping(DiarySubject::getPersonId, Collectors.toList())));
        Map<Long, List<String>> tagsByDiary = loadTagNames(user.familyId(), ids);
        Map<Long, List<PhotoRef>> photosByDiary = loadPhotos(user.familyId(), ids);

        return diaries.stream()
                .map(d -> toResponse(d,
                        subjectsByDiary.getOrDefault(d.getId(), List.of()),
                        tagsByDiary.getOrDefault(d.getId(), List.of()),
                        photosByDiary.getOrDefault(d.getId(), List.of())))
                .toList();
    }

    @Transactional
    public DiaryResponse create(DiaryRequest req) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();

        Set<Long> subjects = new LinkedHashSet<>(req.subjectsOrEmpty());
        Set<Long> tagIds = new LinkedHashSet<>(req.tagIdsOrEmpty());
        validatePersonsAlive(familyId, subjects);
        validateCollectionAlive(familyId, req.collectionId());
        validateTagsAlive(familyId, tagIds);

        Instant recordedAt = req.recordedAt() == null ? Instant.now() : req.recordedAt();
        Diary diary = diaryRepository.save(new Diary(
                familyId, req.title(), req.content(), req.visibility(),
                recordedAt, req.recordedOn(), req.collectionId()));

        subjects.forEach(pid -> subjectRepository.save(new DiarySubject(familyId, diary.getId(), pid)));
        tagIds.forEach(tid -> diaryTagRepository.save(new DiaryTag(familyId, diary.getId(), tid)));

        // 신규 기록은 사진이 아직 없음(사진은 presign 시 diaryId 로 연결)
        return toResponse(diary, List.copyOf(subjects), resolveTagNames(familyId, tagIds), List.of());
    }

    @Transactional
    public DiaryResponse update(Long id, DiaryRequest req) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();
        Diary diary = diaryRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("일상기록", id));

        // 작성자 본인만 수정
        Set<Long> currentSubjects = personIdSet(subjectRepository.findByDiaryIdAndFamilyId(id, familyId));
        visibilityGuard.assertCanEdit(new VisibleResource(diary.getVisibility(), diary.getCreatedBy(), currentSubjects), user);

        Set<Long> subjects = new LinkedHashSet<>(req.subjectsOrEmpty());
        Set<Long> tagIds = new LinkedHashSet<>(req.tagIdsOrEmpty());
        validatePersonsAlive(familyId, subjects);
        validateCollectionAlive(familyId, req.collectionId());
        validateTagsAlive(familyId, tagIds);

        Instant recordedAt = req.recordedAt() == null ? diary.getRecordedAt() : req.recordedAt();
        diary.update(req.title(), req.content(), req.visibility(), recordedAt, req.recordedOn(), req.collectionId());

        syncSubjects(familyId, id, subjects);
        syncTags(familyId, id, tagIds);

        return toResponse(diary, List.copyOf(subjects), resolveTagNames(familyId, tagIds),
                photosFor(familyId, id));
    }

    @Transactional
    public void delete(Long id) {
        AuthUser user = FamilyContext.require();
        Long familyId = user.familyId();
        Diary diary = diaryRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("일상기록", id));
        Set<Long> currentSubjects = personIdSet(subjectRepository.findByDiaryIdAndFamilyId(id, familyId));
        visibilityGuard.assertCanEdit(new VisibleResource(diary.getVisibility(), diary.getCreatedBy(), currentSubjects), user);

        subjectRepository.findByDiaryIdAndFamilyId(id, familyId)
                .forEach(s -> softDeleteSupport.softDelete(s, subjectRepository));
        diaryTagRepository.findByDiaryIdAndFamilyId(id, familyId)
                .forEach(t -> softDeleteSupport.softDelete(t, diaryTagRepository));
        softDeleteSupport.softDelete(diary, diaryRepository);
        // photo 연결 해제는 photo 도메인에서 처리
    }

    // ---- 검증 ----

    private void validatePersonsAlive(Long familyId, Set<Long> personIds) {
        for (Long personId : personIds) {
            if (!membershipRepository.existsByFamily_IdAndPerson_Id(familyId, personId)) {
                throw new BusinessException("대상 인물이 현재 가족의 구성원(유효)이 아닙니다: personId=" + personId);
            }
        }
    }

    private void validateCollectionAlive(Long familyId, @Nullable Long collectionId) {
        if (collectionId != null && collectionRepository.findByIdAndFamilyId(collectionId, familyId).isEmpty()) {
            throw new BusinessException("묶음(collection)이 존재하지 않거나 삭제되었습니다.");
        }
    }

    private void validateTagsAlive(Long familyId, Set<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        if (tagRepository.findByIdInAndFamilyId(tagIds, familyId).size() != tagIds.size()) {
            throw new BusinessException("태그가 존재하지 않거나 삭제되었습니다.");
        }
    }

    // ---- 연결 동기화 ----

    private void syncSubjects(Long familyId, Long diaryId, Set<Long> target) {
        List<DiarySubject> existing = subjectRepository.findByDiaryIdAndFamilyId(diaryId, familyId);
        Set<Long> existingIds = personIdSet(existing);
        existing.stream().filter(s -> !target.contains(s.getPersonId()))
                .forEach(s -> softDeleteSupport.softDelete(s, subjectRepository));
        target.stream().filter(pid -> !existingIds.contains(pid))
                .forEach(pid -> subjectRepository.save(new DiarySubject(familyId, diaryId, pid)));
    }

    private void syncTags(Long familyId, Long diaryId, Set<Long> target) {
        List<DiaryTag> existing = diaryTagRepository.findByDiaryIdAndFamilyId(diaryId, familyId);
        Set<Long> existingIds = existing.stream().map(DiaryTag::getTagId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        existing.stream().filter(t -> !target.contains(t.getTagId()))
                .forEach(t -> softDeleteSupport.softDelete(t, diaryTagRepository));
        target.stream().filter(tid -> !existingIds.contains(tid))
                .forEach(tid -> diaryTagRepository.save(new DiaryTag(familyId, diaryId, tid)));
    }

    // ---- 응답 ----

    private DiaryResponse toResponse(Diary d, List<Long> subjects, List<String> tags, List<PhotoRef> photos) {
        return new DiaryResponse(
                d.getId(), d.getTitle(), d.getContent(), d.getVisibility(),
                d.getRecordedAt(), d.getRecordedOn(), subjects, d.getCollectionId(), tags, photos);
    }

    /** 단건 기록의 사진(서명 URL 포함). */
    private List<PhotoRef> photosFor(Long familyId, Long diaryId) {
        return photoRepository.findByDiaryIdAndFamilyId(diaryId, familyId).stream()
                .map(this::toPhotoRef).toList();
    }

    /** 목록: diaryId → 사진 목록(서명 URL). */
    private Map<Long, List<PhotoRef>> loadPhotos(Long familyId, List<Long> diaryIds) {
        return photoRepository.findByDiaryIdInAndFamilyId(diaryIds, familyId).stream()
                .collect(Collectors.groupingBy(Photo::getDiaryId,
                        Collectors.mapping(this::toPhotoRef, Collectors.toList())));
    }

    private PhotoRef toPhotoRef(Photo p) {
        long exp = urlSigner.expiryEpochSecond();
        String url = "/api/v1/photos/" + p.getId() + "/raw?exp=" + exp + "&sig=" + urlSigner.sign(p.getId(), exp);
        return new PhotoRef(p.getId(), url, p.getWidth(), p.getHeight(), p.getTakenAt());
    }

    private Map<Long, List<String>> loadTagNames(Long familyId, List<Long> diaryIds) {
        List<DiaryTag> links = diaryTagRepository.findByDiaryIdInAndFamilyId(diaryIds, familyId);
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = links.stream().map(DiaryTag::getTagId).collect(Collectors.toSet());
        Map<Long, String> nameById = tagRepository.findByIdInAndFamilyId(tagIds, familyId).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        return links.stream()
                .filter(t -> nameById.containsKey(t.getTagId()))
                .collect(Collectors.groupingBy(DiaryTag::getDiaryId,
                        Collectors.mapping(t -> nameById.get(t.getTagId()), Collectors.toList())));
    }

    private List<String> resolveTagNames(Long familyId, Set<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findByIdInAndFamilyId(tagIds, familyId).stream().map(Tag::getName).toList();
    }

    private Set<Long> personIdSet(List<DiarySubject> rows) {
        return rows.stream().map(DiarySubject::getPersonId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

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
}
