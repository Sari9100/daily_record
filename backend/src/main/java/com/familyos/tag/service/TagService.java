package com.familyos.tag.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.ErrorCode;
import com.familyos.common.error.NotFoundException;
import com.familyos.tag.dto.TagRequest;
import com.familyos.tag.dto.TagResponse;
import com.familyos.tag.entity.Tag;
import com.familyos.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 태그 관리. 가족 전체 공유. name 중복(alive)은 409.
 * soft-delete 해도 과거 연결(transaction_tag)은 보존, 신규 연결 선택지에서만 제외.
 */
@Service
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;
    private final SoftDeleteSupport softDeleteSupport;

    public TagService(TagRepository tagRepository, SoftDeleteSupport softDeleteSupport) {
        this.tagRepository = tagRepository;
        this.softDeleteSupport = softDeleteSupport;
    }

    public List<TagResponse> list() {
        Long familyId = FamilyContext.getFamilyId();
        return tagRepository.findByFamilyIdOrderByNameAsc(familyId)
                .stream().map(TagResponse::from).toList();
    }

    @Transactional
    public TagResponse create(TagRequest req) {
        Long familyId = FamilyContext.getFamilyId();
        String name = req.name().trim();
        if (tagRepository.existsByFamilyIdAndName(familyId, name)) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 존재하는 태그입니다: " + name);
        }
        return TagResponse.from(tagRepository.save(new Tag(familyId, name)));
    }

    @Transactional
    public void delete(Long id) {
        Long familyId = FamilyContext.getFamilyId();
        Tag tag = tagRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("태그", id));
        softDeleteSupport.softDelete(tag, tagRepository);
    }
}
