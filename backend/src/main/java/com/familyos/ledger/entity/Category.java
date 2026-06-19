package com.familyos.ledger.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/**
 * 가계부 카테고리 (계층, 기본 시딩 플래그).
 *
 * <p>가족 전체가 공유(visibility 없음). isSystem=true(기본 시딩)는 삭제 불가.
 */
@Entity
@Table(name = "category")
public class Category extends FamilyScopedEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CategoryType type;

    @Column(name = "parent_id")
    private @Nullable Long parentId;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    protected Category() {
    }

    public Category(Long familyId, String name, CategoryType type, @Nullable Long parentId, boolean isSystem) {
        setFamilyId(familyId);
        this.name = name;
        this.type = type;
        this.parentId = parentId;
        this.isSystem = isSystem;
    }

    public String getName() {
        return name;
    }

    public CategoryType getType() {
        return type;
    }

    public @Nullable Long getParentId() {
        return parentId;
    }

    public boolean isSystem() {
        return isSystem;
    }

    public void update(String name, @Nullable Long parentId) {
        this.name = name;
        this.parentId = parentId;
    }
}
