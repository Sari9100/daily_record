package com.familyos.ledger.dto;

import com.familyos.common.domain.Visibility;
import com.familyos.ledger.entity.AccountOwnerType;
import com.familyos.ledger.entity.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * 계좌 생성/수정 요청. family_id 는 받지 않는다(토큰).
 * ownerType=FAMILY 면 ownerPersonId 생략(null). visibility 는 PRIVATE/PARENTS/FAMILY 만 허용(서비스 검증).
 */
public record AccountRequest(
        @NotBlank String name,
        @NotNull AssetType assetType,
        @NotNull AccountOwnerType ownerType,
        @Nullable Long ownerPersonId,
        @NotNull Visibility visibility
) {
}
