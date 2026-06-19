package com.familyos.person.entity;

import com.familyos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * 가족 구성원 그 자체. family_id 없음 — 소속은 {@link FamilyMembership}.
 * 자녀는 UserAccount 가 없을 수 있다(부모가 대신 관리).
 */
@Entity
@Table(name = "person")
public class Person extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "birth_date")
    private @Nullable LocalDate birthDate;

    @Column(name = "timezone", nullable = false)
    private String timezone = "Asia/Seoul";

    protected Person() {
    }

    public Person(String name, @Nullable LocalDate birthDate, @Nullable String timezone) {
        this.name = name;
        this.birthDate = birthDate;
        if (timezone != null && !timezone.isBlank()) {
            this.timezone = timezone;
        }
    }

    public String getName() {
        return name;
    }

    public @Nullable LocalDate getBirthDate() {
        return birthDate;
    }

    public String getTimezone() {
        return timezone;
    }

    public void update(@Nullable String name, @Nullable LocalDate birthDate, @Nullable String timezone) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (timezone != null && !timezone.isBlank()) {
            this.timezone = timezone;
        }
    }
}
