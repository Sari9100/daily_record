package com.familyos.person.dto;

/** 계정 생성 결과 (password_hash 등 비밀값은 절대 노출하지 않는다). */
public record AccountCreatedResponse(
        Long accountId,
        Long personId,
        String loginId
) {
}
