package com.familyos.ledger.dto;

import com.familyos.ledger.entity.Category;
import com.familyos.ledger.entity.CategoryType;
import org.jspecify.annotations.Nullable;

public record CategoryResponse(
        Long id,
        String name,
        CategoryType type,
        @Nullable Long parentId,
        boolean isSystem
) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getParentId(), c.isSystem());
    }
}
