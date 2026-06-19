package com.familyos.tag.entity;

import com.familyos.common.entity.FamilyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 태그. 가족 전체 공유. UNIQUE(family_id, name, alive_uk) 는 DDL. */
@Entity
@Table(name = "tag")
public class Tag extends FamilyScopedEntity {

    @Column(name = "name", nullable = false)
    private String name;

    protected Tag() {
    }

    public Tag(Long familyId, String name) {
        setFamilyId(familyId);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }
}
