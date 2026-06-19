package com.familyos.ledger.service;

import com.familyos.common.audit.SoftDeleteSupport;
import com.familyos.common.context.FamilyContext;
import com.familyos.common.error.BusinessException;
import com.familyos.common.error.NotFoundException;
import com.familyos.ledger.dto.CategoryRequest;
import com.familyos.ledger.dto.CategoryResponse;
import com.familyos.ledger.dto.CategoryUpdateRequest;
import com.familyos.ledger.entity.Category;
import com.familyos.ledger.entity.CategoryType;
import com.familyos.ledger.repository.CategoryRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 카테고리 관리. 가족 전체 공유(visibility 없음).
 *
 * <p>isSystem(기본 시딩)은 삭제 불가(422). 신규는 항상 isSystem=false. parentId 는 alive 검증.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final SoftDeleteSupport softDeleteSupport;

    public CategoryService(CategoryRepository categoryRepository, SoftDeleteSupport softDeleteSupport) {
        this.categoryRepository = categoryRepository;
        this.softDeleteSupport = softDeleteSupport;
    }

    public List<CategoryResponse> list(@Nullable CategoryType type) {
        Long familyId = FamilyContext.getFamilyId();
        List<Category> categories = (type == null)
                ? categoryRepository.findByFamilyIdOrderByIdAsc(familyId)
                : categoryRepository.findByFamilyIdAndTypeOrderByIdAsc(familyId, type);
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        Long familyId = FamilyContext.getFamilyId();
        validateParentAlive(familyId, req.parentId());
        Category category = categoryRepository.save(
                new Category(familyId, req.name(), req.type(), req.parentId(), false));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest req) {
        Long familyId = FamilyContext.getFamilyId();
        Category category = load(id, familyId);
        validateParentAlive(familyId, req.parentId());
        if (req.parentId() != null && req.parentId().equals(id)) {
            throw new BusinessException("카테고리는 자기 자신을 부모로 가질 수 없습니다.");
        }
        category.update(req.name(), req.parentId());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Long familyId = FamilyContext.getFamilyId();
        Category category = load(id, familyId);
        if (category.isSystem()) {
            throw new BusinessException("기본(시스템) 카테고리는 삭제할 수 없습니다.");
        }
        softDeleteSupport.softDelete(category, categoryRepository);
    }

    // ---- 내부 ----

    private Category load(Long id, Long familyId) {
        return categoryRepository.findByIdAndFamilyId(id, familyId)
                .orElseThrow(() -> NotFoundException.of("카테고리", id));
    }

    /** 참조 부모가 alive(같은 가족) 인지 검증 — FK 가 막지 못하는 soft-delete 부모 참조 차단. */
    private void validateParentAlive(Long familyId, @Nullable Long parentId) {
        if (parentId == null) {
            return;
        }
        if (categoryRepository.findByIdAndFamilyId(parentId, familyId).isEmpty()) {
            throw new BusinessException("상위 카테고리가 존재하지 않거나 삭제되었습니다.");
        }
    }
}
