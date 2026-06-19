package com.familyos.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 태그 생성. family 내 name 중복 불가(alive 기준) → 409. */
public record TagRequest(
        @NotBlank @Size(max = 50) String name
) {
}
