package com.familyos.collection.service;

import com.familyos.collection.dto.CollectionRequest;
import com.familyos.collection.dto.CollectionResponse;
import com.familyos.collection.dto.CollectionSummaryResponse;
import com.familyos.collection.entity.Collection;
import com.familyos.collection.mapper.CollectionSummaryMapper;
import com.familyos.collection.repository.CollectionRepository;
import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 이벤트 묶음 관리. 가족 전체 공유(visibility 없음) — 같은 가족 구성원이 관리.
 * 단건은 family_id 명시 조회로 격리. 삭제는 soft-delete.
 */
@Service
@Transactional(readOnly = true)
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionSummaryMapper summaryMapper;
    private final SoftDeleteSupport softDeleteSupport;

    public CollectionService(CollectionRepository collectionRepository,
                             CollectionSummaryMapper summaryMapper,
                             SoftDeleteSupport softDeleteSupport) {
        this.collectionRepository = collectionRepository;
        this.summaryMapper = summaryMapper;
        this.softDeleteSupport = softDeleteSupport;
    }

    public List<CollectionResponse> list() {
        Long familyId = FamilyContext.getFamilyId();
        return collectionRepository.findByFamilyIdOrderByIdDesc(familyId)
                .stream().map(CollectionResponse::from).toList();
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
        Collection collection = collectionRepository.save(new Collection(
                familyId, req.name(), req.description(), req.startedAt(), req.endedAt()));
        return CollectionResponse.from(collection);
    }

    @Transactional
    public CollectionResponse update(Long id, CollectionRequest req) {
        Long familyId = FamilyContext.getFamilyId();
        Collection collection = load(id, familyId);
        collection.update(req.name(), req.description(), req.startedAt(), req.endedAt());
        return CollectionResponse.from(collection);
    }

    @Transactional
    public void delete(Long id) {
        Long familyId = FamilyContext.getFamilyId();
        Collection collection = load(id, familyId);
        softDeleteSupport.softDelete(collection, collectionRepository);
        // 과거 참조(거래·일정 등)의 collection_id 는 보존(이력) — 신규 입력 선택지에서만 제외
    }

    private Collection load(Long id, Long familyId) {
        return collectionRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("묶음", id));
    }
}
