-- =====================================================================
-- 가족 생활기록 시스템 (family_os) — 초기 스키마
-- Flyway: V1__init.sql
-- 대상: MySQL 8.0.13+ / InnoDB / utf8mb4
-- 기준 문서: DB설계 v3, JPA Entity 설계 v1
-- 검증: sqlglot MySQL 방언 파싱 통과 (21 테이블). 실제 MySQL 인스턴스 재검증 권장.
--
-- 규칙
--  - 스키마 진실(SSOT)은 이 Flyway DDL. JPA는 매핑만, 유니크/제약은 여기서 단일 관리
--  - 모든 시각 컬럼은 UTC 저장 (DATETIME(6))
--  - 날짜만 의미있는 값(종일 일정, 기록 날짜)은 DATE로 별도 저장 (UTC 변환 시 날짜 밀림 방지)
--  - Enum은 VARCHAR로 저장 (애플리케이션 @Enumerated(STRING))
--  - created_by/updated_by/deleted_by는 NULL 허용 (가입·시딩 시 인증주체 없음. 시스템 작업은 예약 ID 가능)
--  - Soft Delete: deleted_at (NULL=정상)
--  - ★ Soft-Delete 유니크: MySQL은 NULL을 유니크에서 "서로 다름"으로 처리하므로
--    (col, deleted_at) 복합 유니크는 '살아있는 중복'을 못 막는다. 따라서
--    생성 컬럼 alive_uk = IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND,'1970-01-01',deleted_at)) 를 두고 (col, alive_uk)로 유니크.
--    (TIMESTAMPDIFF는 정수 반환 → 부동소수 정밀도 문제 없이 마이크로초 완전 보존)
--    → 살아있는 행끼리는 alive_uk=0으로 충돌(중복 차단), 삭제된 행은 삭제시각으로 분산(재사용 허용)
--    (id 참조 불가: MySQL 생성컬럼은 AUTO_INCREMENT 참조 금지 → deleted_at 마이크로초 사용)
--  - 공통 컬럼: BaseEntity(id, created_*, updated_*, deleted_*)
--               FamilyScopedEntity(+ family_id)
--  - FK는 명시하되, 멀티테넌트/소유 참조 일부는 인덱스만(논리 FK)
--  - ★ FK는 "물리 행 존재"만 보장하고 "alive(deleted_at IS NULL)"는 모른다.
--    soft-delete된 부모(category/tag/collection/person 등)를 신규 자식이 참조하는 것을
--    FK로는 못 막으므로, 서비스 계층에서 "참조 대상이 alive인지" 검증 필수.
--    (account는 05-2-1에 규칙 있음 → category/tag/collection/subject(person)도 동일 적용)
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- [인물 / 가족]  — BaseEntity (family_id 없음)
-- =====================================================================

-- Family : 최상위 경계
CREATE TABLE family (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NULL,
    updated_at  DATETIME(6)  NOT NULL,
    updated_by  BIGINT       NULL,
    deleted_at  DATETIME(6)  NULL,
    deleted_by  BIGINT       NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Person : 가족 구성원 그 자체 (family_id 없음, 소속은 family_membership)
CREATE TABLE person (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    birth_date  DATE         NULL,
    timezone    VARCHAR(50)  NOT NULL DEFAULT 'Asia/Seoul',
    created_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NULL,
    updated_at  DATETIME(6)  NOT NULL,
    updated_by  BIGINT       NULL,
    deleted_at  DATETIME(6)  NULL,
    deleted_by  BIGINT       NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- UserAccount : 로그인 수단 (선택). 자녀는 없을 수 있음
CREATE TABLE user_account (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    person_id      BIGINT       NOT NULL,
    login_id       VARCHAR(100) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at  DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    created_by     BIGINT       NULL,
    updated_at     DATETIME(6)  NOT NULL,
    updated_by     BIGINT       NULL,
    deleted_at     DATETIME(6)  NULL,
    deleted_by     BIGINT       NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    -- UNIQUE + Soft Delete: deleted_at 포함 (재가입 허용)
    UNIQUE KEY uk_user_account_login (login_id, alive_uk),
    UNIQUE KEY uk_user_account_person (person_id, alive_uk),
    CONSTRAINT fk_user_account_person FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FamilyMembership : Person ↔ Family 다대다 + 가족 내 역할
CREATE TABLE family_membership (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    family_id   BIGINT       NOT NULL,
    person_id   BIGINT       NOT NULL,
    role        VARCHAR(20)  NOT NULL,             -- PARENT / CHILD
    joined_at   DATETIME(6)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NULL,
    updated_at  DATETIME(6)  NOT NULL,
    updated_by  BIGINT       NULL,
    deleted_at  DATETIME(6)  NULL,
    deleted_by  BIGINT       NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership (family_id, person_id, alive_uk),
    KEY idx_membership_person (person_id),
    CONSTRAINT fk_membership_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_membership_person FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- PersonSetting : 개인별 표시 설정 (family_id 없음)
CREATE TABLE person_setting (
    id                             BIGINT       NOT NULL AUTO_INCREMENT,
    person_id                      BIGINT       NOT NULL,
    shared_schedule_detail_level   VARCHAR(20)  NOT NULL DEFAULT 'FULL',  -- FULL / SUMMARY
    created_at                     DATETIME(6)  NOT NULL,
    created_by                     BIGINT       NULL,
    updated_at                     DATETIME(6)  NOT NULL,
    updated_by                     BIGINT       NULL,
    deleted_at                     DATETIME(6)  NULL,
    deleted_by                     BIGINT       NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_person_setting (person_id, alive_uk),
    CONSTRAINT fk_person_setting_person FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- [가계부]  — FamilyScopedEntity
-- =====================================================================

-- Account : 계좌/결제수단 (개인 또는 공용)
CREATE TABLE account (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    family_id        BIGINT       NOT NULL,
    name             VARCHAR(100) NOT NULL,
    asset_type       VARCHAR(20)  NOT NULL,            -- BANK / SECURITIES / CASH
    owner_type       VARCHAR(20)  NOT NULL,            -- PERSON / FAMILY
    owner_person_id  BIGINT       NULL,                -- FAMILY면 NULL
    visibility       VARCHAR(20)  NOT NULL,            -- PRIVATE / PARENTS / FAMILY
    created_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT       NULL,
    updated_at       DATETIME(6)  NOT NULL,
    updated_by       BIGINT       NULL,
    deleted_at       DATETIME(6)  NULL,
    deleted_by       BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_account_family (family_id),
    KEY idx_account_owner (owner_person_id),
    CONSTRAINT fk_account_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_account_owner FOREIGN KEY (owner_person_id) REFERENCES person (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Category : 가계부 카테고리 (계층, 기본 시딩 플래그)
CREATE TABLE category (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    family_id   BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL,                -- INCOME / EXPENSE
    parent_id   BIGINT       NULL,
    is_system   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    created_by  BIGINT       NULL,
    updated_at  DATETIME(6)  NOT NULL,
    updated_by  BIGINT       NULL,
    deleted_at  DATETIME(6)  NULL,
    deleted_by  BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_category_family (family_id),
    KEY idx_category_parent (parent_id),
    CONSTRAINT fk_category_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Collection : 이벤트 묶음 (cover_photo_id는 photo 생성 후 FK 추가)
CREATE TABLE collection (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    family_id       BIGINT       NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT         NULL,
    cover_photo_id  BIGINT       NULL,
    started_at      DATETIME(6)  NULL,
    ended_at        DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME(6)  NOT NULL,
    updated_by      BIGINT       NULL,
    deleted_at      DATETIME(6)  NULL,
    deleted_by      BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_collection_family (family_id),
    CONSTRAINT fk_collection_family FOREIGN KEY (family_id) REFERENCES family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Transaction : 수입/지출/이체 통합
CREATE TABLE `transaction` (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    family_id           BIGINT        NOT NULL,
    transaction_type    VARCHAR(20)   NOT NULL,        -- INCOME / EXPENSE / TRANSFER
    amount              DECIMAL(15,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL DEFAULT 'KRW',
    source_account_id   BIGINT        NULL,            -- 나간 계좌
    target_account_id   BIGINT        NULL,            -- 들어온 계좌
    category_id         BIGINT        NULL,
    subject_person_id   BIGINT        NULL,            -- 가계부 subject=단일
    visibility          VARCHAR(20)   NOT NULL DEFAULT 'PRIVATE',
    settlement_status   VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    occurred_at         DATETIME(6)   NOT NULL,
    memo                VARCHAR(500)  NULL,
    collection_id       BIGINT        NULL,
    source              VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',  -- MANUAL/TEXT/TELEGRAM
    created_at          DATETIME(6)   NOT NULL,
    created_by          BIGINT        NULL,
    updated_at          DATETIME(6)   NOT NULL,
    updated_by          BIGINT        NULL,
    deleted_at          DATETIME(6)   NULL,
    deleted_by          BIGINT        NULL,
    PRIMARY KEY (id),
    KEY idx_tx_family_occurred (family_id, occurred_at),
    KEY idx_tx_family_category (family_id, category_id),
    KEY idx_tx_family_vis_occurred (family_id, visibility, occurred_at),
    KEY idx_tx_collection (collection_id),
    KEY idx_tx_source_account (source_account_id),
    KEY idx_tx_target_account (target_account_id),
    CONSTRAINT fk_tx_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_tx_source_account FOREIGN KEY (source_account_id) REFERENCES account (id),
    CONSTRAINT fk_tx_target_account FOREIGN KEY (target_account_id) REFERENCES account (id),
    CONSTRAINT fk_tx_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_tx_subject FOREIGN KEY (subject_person_id) REFERENCES person (id),
    CONSTRAINT fk_tx_collection FOREIGN KEY (collection_id) REFERENCES collection (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- [일정]  — FamilyScopedEntity
-- =====================================================================

CREATE TABLE schedule (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    family_id        BIGINT       NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      TEXT         NULL,
    location         VARCHAR(255) NULL,
    -- 시점(timed) 일정: UTC 저장. 종일 일정은 NULL
    started_at       DATETIME(6)  NULL,
    ended_at         DATETIME(6)  NULL,
    -- 종일(all-day) 일정: 날짜만 의미. UTC 변환 시 날짜 밀림 방지 위해 DATE로 별도 저장
    start_date       DATE         NULL,
    end_date         DATE         NULL,
    is_all_day       TINYINT(1)   NOT NULL DEFAULT 0,
    -- 규칙: is_all_day=1 → start_date/end_date 사용(started_at/ended_at NULL)
    --       is_all_day=0 → started_at/ended_at 사용(start_date/end_date NULL)
    visibility       VARCHAR(20)  NOT NULL,    -- PRIVATE/SHARED_PERSONAL/PARENTS/FAMILY
    schedule_type    VARCHAR(20)  NOT NULL,    -- EVENT/TODO/REMINDER
    is_done          TINYINT(1)   NOT NULL DEFAULT 0,
    recurrence_rule  VARCHAR(255) NULL,
    collection_id    BIGINT       NULL,
    google_event_id  VARCHAR(255) NULL,
    sync_status      VARCHAR(20)  NULL,        -- SYNCED/PENDING/CONFLICT/DELETED_REMOTE
    last_synced_at   DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    created_by       BIGINT       NULL,
    updated_at       DATETIME(6)  NOT NULL,
    updated_by       BIGINT       NULL,
    deleted_at       DATETIME(6)  NULL,
    deleted_by       BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_sch_family_started (family_id, started_at),
    KEY idx_sch_family_vis_started (family_id, visibility, started_at),
    KEY idx_sch_family_start_date (family_id, start_date),   -- 종일 일정 타임라인(started_at NULL)
    KEY idx_sch_google_event (google_event_id),
    KEY idx_sch_collection (collection_id),
    CONSTRAINT fk_sch_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_sch_collection FOREIGN KEY (collection_id) REFERENCES collection (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ScheduleSubject : 일정 subject=다중
CREATE TABLE schedule_subject (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    family_id    BIGINT      NOT NULL,
    schedule_id  BIGINT      NOT NULL,
    person_id    BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    created_by   BIGINT      NULL,
    updated_at   DATETIME(6) NOT NULL,
    updated_by   BIGINT      NULL,
    deleted_at   DATETIME(6) NULL,
    deleted_by   BIGINT      NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sch_subject (schedule_id, person_id, alive_uk),
    KEY idx_sch_subject_person (person_id),
    CONSTRAINT fk_sch_subject_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id),
    CONSTRAINT fk_sch_subject_person FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ScheduleParticipant : 실제 참석자
CREATE TABLE schedule_participant (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    family_id    BIGINT      NOT NULL,
    schedule_id  BIGINT      NOT NULL,
    person_id    BIGINT      NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    created_by   BIGINT      NULL,
    updated_at   DATETIME(6) NOT NULL,
    updated_by   BIGINT      NULL,
    deleted_at   DATETIME(6) NULL,
    deleted_by   BIGINT      NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sch_participant (schedule_id, person_id, alive_uk),
    KEY idx_sch_participant_person (person_id),
    CONSTRAINT fk_sch_participant_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id),
    CONSTRAINT fk_sch_participant_person FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- [일상기록]  — FamilyScopedEntity
-- =====================================================================

CREATE TABLE diary (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    family_id    BIGINT       NOT NULL,
    title        VARCHAR(200) NULL,
    content      TEXT         NOT NULL,
    visibility   VARCHAR(20)  NOT NULL,    -- PRIVATE/SHARED_PERSONAL/PARENTS/FAMILY
    recorded_at  DATETIME(6)  NOT NULL,    -- 시점(UTC). 작성/기록 시각
    recorded_on  DATE         NOT NULL,    -- 기록 대상 날짜(로컬). 타임라인 날짜축 정렬 기준
    collection_id BIGINT      NULL,
    created_at   DATETIME(6)  NOT NULL,
    created_by   BIGINT       NULL,
    updated_at   DATETIME(6)  NOT NULL,
    updated_by   BIGINT       NULL,
    deleted_at   DATETIME(6)  NULL,
    deleted_by   BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_diary_family_recorded (family_id, recorded_at),
    KEY idx_diary_family_vis_recorded (family_id, visibility, recorded_at),
    KEY idx_diary_collection (collection_id),
    CONSTRAINT fk_diary_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_diary_collection FOREIGN KEY (collection_id) REFERENCES collection (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- DiarySubject : 일상기록 subject=다중
CREATE TABLE diary_subject (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    family_id   BIGINT      NOT NULL,
    diary_id    BIGINT      NOT NULL,
    person_id   BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NULL,
    updated_at  DATETIME(6) NOT NULL,
    updated_by  BIGINT      NULL,
    deleted_at  DATETIME(6) NULL,
    deleted_by  BIGINT      NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diary_subject (diary_id, person_id, alive_uk),
    KEY idx_diary_subject_person (person_id),
    CONSTRAINT fk_diary_subject_diary FOREIGN KEY (diary_id) REFERENCES diary (id),
    CONSTRAINT fk_diary_subject_person FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Photo
CREATE TABLE photo (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    family_id          BIGINT       NOT NULL,
    diary_id           BIGINT       NULL,
    storage_key        VARCHAR(500) NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    photo_hash         CHAR(64)     NOT NULL,
    width              INT          NULL,
    height             INT          NULL,
    taken_at           DATETIME(6)  NULL,
    file_size          BIGINT       NULL,
    mime_type          VARCHAR(50)  NULL,
    collection_id      BIGINT       NULL,
    created_at         DATETIME(6)  NOT NULL,
    created_by         BIGINT       NULL,
    updated_at         DATETIME(6)  NOT NULL,
    updated_by         BIGINT       NULL,
    deleted_at         DATETIME(6)  NULL,
    deleted_by         BIGINT       NULL,
    PRIMARY KEY (id),
    KEY idx_photo_family_taken (family_id, taken_at),
    KEY idx_photo_hash (photo_hash),
    KEY idx_photo_diary (diary_id),
    KEY idx_photo_collection (collection_id),
    CONSTRAINT fk_photo_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_photo_diary FOREIGN KEY (diary_id) REFERENCES diary (id),
    CONSTRAINT fk_photo_collection FOREIGN KEY (collection_id) REFERENCES collection (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Collection.cover_photo_id → photo FK (photo 생성 후 추가하여 순환 회피)
ALTER TABLE collection
    ADD CONSTRAINT fk_collection_cover_photo
    FOREIGN KEY (cover_photo_id) REFERENCES photo (id);

-- =====================================================================
-- [태그]  — FamilyScopedEntity
-- =====================================================================

CREATE TABLE tag (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    family_id   BIGINT      NOT NULL,
    name        VARCHAR(50) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NULL,
    updated_at  DATETIME(6) NOT NULL,
    updated_by  BIGINT      NULL,
    deleted_at  DATETIME(6) NULL,
    deleted_by  BIGINT      NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag (family_id, name, alive_uk),
    CONSTRAINT fk_tag_family FOREIGN KEY (family_id) REFERENCES family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE transaction_tag (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    family_id       BIGINT      NOT NULL,
    transaction_id  BIGINT      NOT NULL,
    tag_id          BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    created_by      BIGINT      NULL,
    updated_at      DATETIME(6) NOT NULL,
    updated_by      BIGINT      NULL,
    deleted_at      DATETIME(6) NULL,
    deleted_by      BIGINT      NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tx_tag (transaction_id, tag_id, alive_uk),
    KEY idx_tx_tag_tag (tag_id),
    CONSTRAINT fk_tx_tag_tx FOREIGN KEY (transaction_id) REFERENCES `transaction` (id),
    CONSTRAINT fk_tx_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE diary_tag (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    family_id   BIGINT      NOT NULL,
    diary_id    BIGINT      NOT NULL,
    tag_id      BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NULL,
    updated_at  DATETIME(6) NOT NULL,
    updated_by  BIGINT      NULL,
    deleted_at  DATETIME(6) NULL,
    deleted_by  BIGINT      NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_diary_tag (diary_id, tag_id, alive_uk),
    KEY idx_diary_tag_tag (tag_id),
    CONSTRAINT fk_diary_tag_diary FOREIGN KEY (diary_id) REFERENCES diary (id),
    CONSTRAINT fk_diary_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- [인증 / 외부연동]
-- =====================================================================

-- RefreshToken : JWT 리프레시 토큰 (회전·재사용 감지). BaseEntity 일부만 사용
CREATE TABLE refresh_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    account_id  BIGINT       NOT NULL,            -- user_account.id
    jti         VARCHAR(64)  NOT NULL,            -- 토큰 고유 ID
    expires_at  DATETIME(6)  NOT NULL,
    revoked     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_jti (jti),
    KEY idx_refresh_account (account_id),
    CONSTRAINT fk_refresh_account FOREIGN KEY (account_id) REFERENCES user_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- GoogleSyncState : 구글 캘린더 동기화 상태 (sync token·푸시채널·refresh token)
CREATE TABLE google_sync_state (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    family_id             BIGINT        NOT NULL,
    person_id             BIGINT        NOT NULL,      -- 구글 계정 소유 구성원
    google_calendar_id    VARCHAR(255)  NOT NULL,      -- 보통 'primary'
    sync_token            VARCHAR(1024) NULL,          -- 증분 동기화 토큰
    channel_id            VARCHAR(255)  NULL,          -- 푸시 채널
    channel_resource_id   VARCHAR(255)  NULL,
    channel_expires_at    DATETIME(6)   NULL,
    refresh_token_enc     VARBINARY(512) NULL,         -- 암호화된 refresh token. 연동 해제 시 NULL로 무효화
    last_full_sync_at     DATETIME(6)   NULL,
    last_incremental_at   DATETIME(6)   NULL,
    created_at            DATETIME(6)   NOT NULL,
    created_by            BIGINT        NULL,
    updated_at            DATETIME(6)   NOT NULL,
    updated_by            BIGINT        NULL,
    deleted_at            DATETIME(6)   NULL,
    deleted_by            BIGINT        NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gsync (person_id, google_calendar_id, alive_uk),
    KEY idx_gsync_family (family_id),
    CONSTRAINT fk_gsync_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_gsync_person FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- TelegramPersonMap : 텔레그램 user_id ↔ Person 매핑 (Hermes 연동)
CREATE TABLE telegram_person_map (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    family_id         BIGINT       NOT NULL,
    person_id         BIGINT       NOT NULL,
    telegram_user_id  BIGINT       NOT NULL,        -- 텔레그램 사용자 ID
    created_at        DATETIME(6)  NOT NULL,
    created_by        BIGINT       NULL,
    updated_at        DATETIME(6)  NOT NULL,
    updated_by        BIGINT       NULL,
    deleted_at        DATETIME(6)  NULL,
    deleted_by        BIGINT       NULL,
    alive_uk    BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tg_user (telegram_user_id, alive_uk),
    KEY idx_tg_family (family_id),
    KEY idx_tg_person (person_id),
    CONSTRAINT fk_tg_family FOREIGN KEY (family_id) REFERENCES family (id),
    CONSTRAINT fk_tg_person FOREIGN KEY (person_id) REFERENCES person (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
