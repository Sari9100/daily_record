package com.familyos.ledger.entity;

/** 계좌 소유 유형. PERSON=개인 계좌(owner_person_id 지정), FAMILY=공용 계좌(owner_person_id NULL). */
public enum AccountOwnerType {
    PERSON,
    FAMILY
}
