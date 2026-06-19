package com.familyos.person.entity;

import com.familyos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * 로그인 수단. Person 1:1. login_id UNIQUE 는 alive_uk 방식(DDL) — 재가입 허용.
 * password_hash 는 BCrypt 해시만 저장(평문 금지).
 */
@Entity
@Table(name = "user_account")
public class UserAccount extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false, updatable = false)
    private Person person;

    @Column(name = "login_id", nullable = false)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "last_login_at")
    private @Nullable Instant lastLoginAt;

    protected UserAccount() {
    }

    public UserAccount(Person person, String loginId, String passwordHash) {
        this.person = person;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.status = AccountStatus.ACTIVE;
    }

    public Person getPerson() {
        return person;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public @Nullable Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void disable() {
        this.status = AccountStatus.DISABLED;
    }

    public void markLoggedIn(Instant when) {
        this.lastLoginAt = when;
    }
}
