package com.familyos.tag.dto;

import com.familyos.tag.entity.Tag;

public record TagResponse(Long id, String name) {
    public static TagResponse from(Tag t) {
        return new TagResponse(t.getId(), t.getName());
    }
}
