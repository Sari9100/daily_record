package com.familyos.collection.service;

import com.familyos.collection.dto.CollectionRequest;
import com.familyos.collection.dto.CollectionResponse;
import com.familyos.collection.dto.CollectionSummaryResponse;
import com.familyos.collection.entity.Collection;
import com.familyos.collection.mapper.CollectionSummaryMapper;
import com.familyos.collection.repository.CollectionRepository;
import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.tag.entity.CollectionTag;
import com.familyos.tag.entity.Tag;
import com.familyos.tag.repository.CollectionTagRepository;
import com.familyos.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이벤트 묶음 관리. 가족 전체 공유(visibility 없음) — 같은 가족 구성원이 관리.
 * 단건은 family_id 명시 조회로 격리. 삭제는 soft-delete. 태그는 검색/분류 목적(1-4 alive 검증).
 */
@Service
@Transactional(readOnly = true)
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionSummaryMapper summaryMapper;
    private final CollectionTagRepository collectionTagRepository;
    private final TagRepository tagRepository;
    private final SoftDeleteSupport softDeleteSupport;

    public CollectionService(CollectionRepository collectionRepository,
                             CollectionSummaryMapper summaryMapper,
                             CollectionTagRepository collectionTagRepository,
                             TagRepository tagRepository,
                             SoftDeleteSupport softDeleteSupport) {
        this.collectionRepository = collectionRepository;
        this.summaryMapper = summaryMapper;
        this.collectionTagRepository = collectionTagRepository;
        this.tagRepository = tagRepository;
        this.softDeleteSupport = softDeleteSupport;
    }

    public List<CollectionResponse> list() {
        Long familyId = FamilyContext.getFamilyId();
        List<Collection> collections = collectionRepository.findByFamilyIdOrderByIdDesc(familyId);
        if (collections.isEmpty()) {
            return List.of();
        }
        List<Long> ids = collections.stream().map(Collection::getId).toList();
        Map<Long, List<String>> tagsByCollection = loadTagNames(familyId, ids);
        return collections.stream()
                .map(c -> CollectionResponse.from(c, tagsByCollection.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    /** 묶음 요약 — 연결된 거래·일정·기록·사진 집계(MyBatis). 존재/가족 검증 후 조회. */
    public CollectionSummaryResponse summary(Long id) {
        Long familyId = FamilyContext.getFamilyId();
        load(id, familyId); // 존재·가족 격리 검증(없으면 404)
        return summaryMapper.selectSummary(familyId, id);
    }

    @Transactional
    public CollectionResponse create(CollectionRequest req) {
        Long familyId = FamilyContext.getFamilyId();
        Set<Long> tagIds = new LinkedHashSet<>(req.tagIdsOrEmpty());
        validateTagsAlive(familyId, tagIds);

        Collection collection = collectionRepository.save(new Collection(
                familyId, req.name(), req.description(), req.startedAt(), req.endedAt()));
        tagIds.forEach(tagId -> collectionTagRepository.save(new CollectionTag(familyId, collection.getId(), tagId)));

        return CollectionResponse.from(collection, resolveTagNames(familyId, tagIds));
    }

    @Transactional
    public CollectionResponse update(Long id, CollectionRequest req) {
        Long familyId = FamilyContext.getFamilyId();
        Collection collection = load(id, familyId);
        Set<Long> tagIds = new LinkedHashSet<>(req.tagIdsOrEmpty());
        validateTagsAlive(familyId, tagIds);

        collection.update(req.name(), req.description(), req.startedAt(), req.endedAt());
        syncTags(familyId, id, tagIds);

        return CollectionResponse.from(collection, resolveTagNames(familyId, tagIds));
    }

    @Transactional
    public void delete(Long id) {
        Long familyId = FamilyContext.getFamilyId();
        Collection collection = load(id, familyId);
        collectionTagRepository.findByCollectionIdAndFamilyId(id, familyId)
                .forEach(ct -> softDeleteSupport.softDelete(ct, collectionTagRepository));
        softDeleteSupport.softDelete(collection, collectionRepository);
        // 과거 참조(거래·일정 등)의 collection_id 는 보존(이력) — 신규 입력 선택지에서만 제외
    }

    private Collection load(Long id, Long familyId) {
        return collectionRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("묶음", id));
    }

    // ---- 태그 ----

    private void validateTagsAlive(Long familyId, Set<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return;
        }
        if (tagRepository.findByIdInAndFamilyId(tagIds, familyId).size() != tagIds.size()) {
            throw new BusinessException("태그가 존재하지 않거나 삭제되었습니다.");
        }
    }

    private void syncTags(Long familyId, Long collectionId, Set<Long> target) {
        List<CollectionTag> existing = collectionTagRepository.findByCollectionIdAndFamilyId(collectionId, familyId);
        Set<Long> existingIds = existing.stream().map(CollectionTag::getTagId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        existing.stream().filter(ct -> !target.contains(ct.getTagId()))
                .forEach(ct -> softDeleteSupport.softDelete(ct, collectionTagRepository));
        target.stream().filter(tagId -> !existingIds.contains(tagId))
                .forEach(tagId -> collectionTagRepository.save(new CollectionTag(familyId, collectionId, tagId)));
    }

    private List<String> resolveTagNames(Long familyId, Set<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findByIdInAndFamilyId(tagIds, familyId).stream().map(Tag::getName).toList();
    }

    private Map<Long, List<String>> loadTagNames(Long familyId, List<Long> collectionIds) {
        List<CollectionTag> links = collectionTagRepository.findByCollectionIdInAndFamilyId(collectionIds, familyId);
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = links.stream().map(CollectionTag::getTagId).collect(Collectors.toSet());
        Map<Long, String> nameById = tagRepository.findByIdInAndFamilyId(tagIds, familyId).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        return links.stream()
                .filter(ct -> nameById.containsKey(ct.getTagId()))
                .collect(Collectors.groupingBy(CollectionTag::getCollectionId,
                        Collectors.mapping(ct -> nameById.get(ct.getTagId()), Collectors.toList())));
    }
}
