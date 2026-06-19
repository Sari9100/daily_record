package com.familyos.integration.telegram.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * 텔레그램 user_id ↔ Person 매핑 (Hermes 연동). 행동 주체 결정의 근거.
 * UNIQUE(telegram_user_id, alive_uk) 는 DDL — telegram_user_id 가 이미 가족을 함의하므로 family_id 는 유니크에 미포함.
 */
@Entity
@Table(name = "telegram_person_map")
public class TelegramPersonMap extends FamilyScopedEntity {

    @Column(name = "person_id", nullable = false, updatable = false)
    private Long personId;

    @Column(name = "telegram_user_id", nullable = false, updatable = false)
    private Long telegramUserId;

    protected TelegramPersonMap() {
    }

    public TelegramPersonMap(Long familyId, Long personId, Long telegramUserId) {
        setFamilyId(familyId);
        this.personId = personId;
        this.telegramUserId = telegramUserId;
    }

    public Long getPersonId() {
        return personId;
    }

    public Long getTelegramUserId() {
        return telegramUserId;
    }
}
