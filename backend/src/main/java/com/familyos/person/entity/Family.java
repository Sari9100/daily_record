package com.familyos.person.entity;

import com.familyos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

/** 최상위 경계. family_id 없음(자기 자신이 가족). */
@Entity
@Table(name = "family")
public class Family extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    protected Family() {
    }

    public Family(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void rename(@Nullable String name) {
        if (name != null) {
            this.name = name;
        }
    }
}
