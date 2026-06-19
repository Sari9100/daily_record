package com.familyos.person.entity;

import com.familyos.common.domain.FamilyRole;
import com.familyos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Person ↔ Family 다대다 + 가족 내 역할. UNIQUE(family_id, person_id, alive_uk) 는 DDL.
 */
@Entity
@Table(name = "family_membership")
public class FamilyMembership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id", nullable = false, updatable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false, updatable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private FamilyRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected FamilyMembership() {
    }

    public FamilyMembership(Family family, Person person, FamilyRole role, Instant joinedAt) {
        this.family = family;
        this.person = person;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public Family getFamily() {
        return family;
    }

    public Person getPerson() {
        return person;
    }

    public FamilyRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void changeRole(FamilyRole role) {
        this.role = role;
    }
}
