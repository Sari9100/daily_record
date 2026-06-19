package com.familyos.ledger.dto;

import com.familyos.common.domain.Visibility;
import com.familyos.ledger.entity.Account;
import com.familyos.ledger.entity.AccountOwnerType;
import com.familyos.ledger.entity.AssetType;
import org.jspecify.annotations.Nullable;

public record AccountResponse(
        Long id,
        String name,
        AssetType assetType,
        AccountOwnerType ownerType,
        @Nullable Long ownerPersonId,
        Visibility visibility
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
                a.getId(), a.getName(), a.getAssetType(),
                a.getOwnerType(), a.getOwnerPersonId(), a.getVisibility());
    }
}
