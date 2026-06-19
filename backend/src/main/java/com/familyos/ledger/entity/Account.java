package com.familyos.ledger.entity;

import com.familyos.common.domain.Visibility;
import com.familyos.common.entity.FamilyScopedEntity;
import com.familyos.common.entity.Visible;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/**
 * 계좌/결제수단 (개인 또는 공용).
 *
 * <p>visibility 는 PRIVATE/PARENTS/FAMILY 만 사용(SHARED_PERSONAL 미사용 — 서비스에서 검증).
 * soft-delete 해도 과거 거래의 FK 는 유지되고, 신규 입력 선택지에서만 제외된다.
 */
@Entity
@Table(name = "account")
public class Account extends FamilyScopedEntity implements Visible {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private AccountOwnerType ownerType;

    @Column(name = "owner_person_id")
    private @Nullable Long ownerPersonId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility;

    protected Account() {
    }

    public Account(Long familyId, String name, AssetType assetType,
                   AccountOwnerType ownerType, @Nullable Long ownerPersonId, Visibility visibility) {
        setFamilyId(familyId);
        this.name = name;
        this.assetType = assetType;
        this.ownerType = ownerType;
        this.ownerPersonId = ownerPersonId;
        this.visibility = visibility;
    }

    public String getName() {
        return name;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public AccountOwnerType getOwnerType() {
        return ownerType;
    }

    public @Nullable Long getOwnerPersonId() {
        return ownerPersonId;
    }

    @Override
    public Visibility getVisibility() {
        return visibility;
    }

    public void update(String name, AssetType assetType, AccountOwnerType ownerType,
                       @Nullable Long ownerPersonId, Visibility visibility) {
        this.name = name;
        this.assetType = assetType;
        this.ownerType = ownerType;
        this.ownerPersonId = ownerPersonId;
        this.visibility = visibility;
    }
}
