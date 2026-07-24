# 가족 생활기록 시스템 — JPA Entity 설계 (v1)

> DB 설계 v3 기준 / 스택: **Spring Boot 4.1 + Java 21 (LTS) + Hibernate 7.2 + MySQL**
> 빌드: Gradle (기존 멀티모듈 경험 활용) / 마이그레이션: Flyway

---

## 0. 스택 결정 사항

| 항목 | 선택 | 비고 |
|------|------|------|
| Spring Boot | 4.1.0 | 최신 안정, 지원 2027-07까지 |
| Java | 21 (LTS) | record/sealed/pattern matching 적극 활용 |
| Hibernate | 7.2 (Boot 4.1 번들) | `@SoftDelete` 정식 지원 |
| ORM 접근 | **Spring Data JPA + MyBatis 혼용** | CRUD·도메인=JPA, 통계·타임라인 복잡쿼리=MyBatis |
| 마이그레이션 | Flyway | DDL 버전 관리 |
| ID 전략 | IDENTITY (MySQL AUTO_INCREMENT) | |

> 참고: Spring Framework 7은 null 안정성에 JSpecify를 쓴다. `@Nullable`/`@NonNull`은 `org.jspecify.annotations` 사용.

---

## 1. 상속 구조 (추상 클래스)

### BaseEntity (모든 엔티티 공통)
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;      // UTC 저장 (Instant)

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;         // author = Person.id

    @LastModifiedDate
    private Instant updatedAt;

    @LastModifiedBy
    private Long updatedBy;

    // Soft Delete (Hibernate 7 @SoftDelete) — 아래 1-2 참고
}
```

### FamilyScopedEntity (extends BaseEntity, family_id 추가)
```java
@MappedSuperclass
@FilterDef(name = "familyFilter", parameters = @ParamDef(name = "familyId", type = Long.class))
@Filter(name = "familyFilter", condition = "family_id = :familyId")
public abstract class FamilyScopedEntity extends BaseEntity {

    @Column(name = "family_id", nullable = false, updatable = false)
    private Long familyId;
}
```

- `@CreatedBy`/`@LastModifiedBy`는 `AuditorAware<Long>` 구현으로 현재 로그인 Person.id 주입
- `@CreatedDate`/`@LastModifiedDate`는 `Instant` → DB에 UTC 저장. `@EnableJpaAuditing` 필요

### 1-2. Soft Delete 방식 (Hibernate 7.2)

Hibernate 7의 `@SoftDelete`를 BaseEntity에 적용:
```java
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
```
- `deleted_at`에 삭제 시각 자동 기록, 조회 시 자동으로 `deleted_at IS NULL` 필터
- `@SQLDelete`+`@Where` 수동 조합 불필요 (구버전 방식 폐기)
- 단, `deleted_by`(삭제자)는 `@SoftDelete`가 자동 관리 안 하므로 **서비스 계층에서 명시적으로 set 후 soft-delete 호출**하거나, 별도 처리 필요

> 주의: `@SoftDelete` + 유니크 제약 동시 사용 시, soft-deleted row가 남아 유니크 충돌 가능.
> UNIQUE 컬럼(login_id 등)은 부분 인덱스 또는 deleted 포함 복합 유니크로 회피 (1-3 참고).

### 1-4. UNIQUE + Soft Delete 충돌 해법 (확정: 생성 컬럼 alive_uk)

`@SoftDelete`(TIMESTAMP)로 삭제 시 row가 남으므로, 단순 UNIQUE는 재사용을 막는다.

**⚠️ 함정 (MySQL 재검증서 발견)**: `(col, deleted_at)` 복합 유니크는 작동하지 않는다.
MySQL은 NULL을 유니크에서 "서로 다름"으로 처리하므로, 살아있는 행 둘 다 deleted_at=NULL이면
`(sari, NULL)` vs `(sari, NULL)`을 다른 키로 봐서 **중복 가입이 허용**된다 (의도와 반대).

**확정 해법 — 생성 컬럼(alive_uk)**:
```sql
alive_uk BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND, '1970-01-01', deleted_at))) STORED
UNIQUE KEY uk_xxx (col..., alive_uk)
```
- 살아있는 행: alive_uk=0 → (sari, 0) vs (sari, 0) **충돌 → 중복 차단** (의도대로)
- 삭제된 행: alive_uk=삭제시각(μs) → 서로 분산 → 재사용(재가입) 허용
- **id 참조 금지**: MySQL 생성컬럼은 AUTO_INCREMENT 컬럼 참조 불가 → deleted_at 사용
- **TIMESTAMPDIFF 사용 이유**: `UNIX_TIMESTAMP()*1000000`은 부동소수 경로로 정밀도 손실 위험. `TIMESTAMPDIFF(MICROSECOND,...)`는 정수(BIGINT) 반환이라 마이크로초 완전 보존 (같은 초 다중 삭제도 구분)

| 테이블 | 유니크 제약 |
|--------|-------------|
| UserAccount | UNIQUE(login_id, alive_uk), UNIQUE(person_id, alive_uk) |
| Tag | UNIQUE(family_id, name, alive_uk) |
| FamilyMembership | UNIQUE(family_id, person_id, alive_uk) |
| ScheduleSubject/DiarySubject/Participant | UNIQUE(부모_id, person_id, alive_uk) |
| TransactionTag/DiaryTag/CollectionTag | UNIQUE(부모_id, tag_id, alive_uk) |
| GoogleSyncState | UNIQUE(person_id, google_calendar_id, alive_uk) |
| TelegramPersonMap | UNIQUE(telegram_user_id, alive_uk) |

> **분류 노트**: GoogleSyncState/TelegramPersonMap은 FamilyScoped(family_id 보유)지만, 유니크 키엔
> person_id/telegram_user_id가 이미 가족을 함의하므로 family_id를 유니크에 넣지 않음(중복 불필요).
> RefreshToken은 **BaseEntity 비상속** — soft-delete 아님(revoked 플래그), family_id 없음(account_id 기반), 단순 UNIQUE(jti).

**SSOT(단일 진실 원천)**: 유니크·제약은 **Flyway DDL(04)에서만** 관리한다. JPA는 매핑만 담당하고
`@Table(uniqueConstraints=...)`로 중복 정의하지 않는다 (생성컬럼은 JPA가 모르는 DB 전용).

> 동일 마이크로초 2회 삭제만 이론상 충돌하나 실무상 무시 가능.

### 1-5. 멀티테넌트 필터 적용 (JPA + MyBatis 혼용 주의)
- **JPA 측**: 요청마다 인터셉터/AOP에서 `session.enableFilter("familyFilter").setParameter("familyId", currentFamilyId)`
  → FamilyScopedEntity 상속 엔티티는 자동으로 family_id 범위 한정
- **MyBatis 측 (★중요 함정)**: Hibernate `@Filter`는 MyBatis 쿼리에 **적용되지 않음**.
  MyBatis 매퍼는 별도 SQL이라 세션 필터를 거치지 않으므로, **모든 MyBatis 쿼리에 family_id 조건을
  명시적으로 WHERE에 넣어야** 한다. (공통 파라미터로 familyId 주입 + 매퍼 규칙화)
- BaseEntity만 상속하는 Family/Person/UserAccount/FamilyMembership은 필터 미적용 (별도 권한 체크)

### 1-6. MyBatis 혼용 경계
- **JPA 담당**: 엔티티 CRUD, 도메인 로직, 단건·단순 조회, 트랜잭션 경계
- **MyBatis 담당**: 통합 타임라인(다중 테이블 날짜축 병합), 가계부 통계(집계·그룹핑), 검색(V1.5)
- soft-delete: MyBatis 쿼리에도 `deleted_at IS NULL` 명시 필요 (JPA `@SoftDelete` 자동필터 미적용)
- 두 경로가 같은 테이블을 보므로, 컬럼·Enum 문자열 값 일관성 유지

---

## 2. Enum 정의

```java
public enum FamilyRole { PARENT, CHILD }

public enum AssetType { BANK, SECURITIES, CASH }
public enum AccountOwnerType { PERSON, FAMILY }

public enum TransactionType { INCOME, EXPENSE, TRANSFER }
public enum SettlementStatus { NONE, PENDING, SETTLED }
public enum TransactionSource { MANUAL, TEXT, TELEGRAM }
public enum CategoryType { INCOME, EXPENSE }

public enum Visibility { PRIVATE, SHARED_PERSONAL, PARENTS, FAMILY }
// 가계부는 PRIVATE/PARENTS/FAMILY만 사용 (SHARED_PERSONAL 미사용) — 검증은 서비스 계층

public enum ScheduleType { EVENT, TODO, REMINDER }
public enum SyncStatus { SYNCED, PENDING, CONFLICT, DELETED_REMOTE }

public enum AccountStatus { ACTIVE, DISABLED }
public enum DetailLevel { FULL, SUMMARY }
```

- 모든 Enum은 `@Enumerated(EnumType.STRING)`으로 매핑 (ORDINAL 금지 — 순서 변경 위험)
- DB에는 VARCHAR로 저장 (MySQL ENUM 타입 대신 — 값 추가 유연성)

---

## 3. 엔티티 매핑 (핵심)

> 연관관계는 **모두 지연 로딩(LAZY)** 기본. 양방향 최소화, 단방향 `@ManyToOne` 위주.
> FK는 객체 참조 대신 **ID 참조(Long)** 를 선호하는 부분과 객체 참조를 혼용 — 아래 기준 따름.
>
> **기준**: 같은 Aggregate 내부거나 자주 함께 조회되면 객체 참조(@ManyToOne LAZY),
> 단순 소유자/생성자처럼 ID만 필요하면 Long 컬럼. (createdBy/updatedBy는 Long 유지)

### 3-1. Person / 가족
```java
@Entity
public class Person extends BaseEntity {       // family_id 없음
    private String name;
    private LocalDate birthDate;
    private String timezone;                    // 기본 "Asia/Seoul"
}

@Entity
public class UserAccount extends BaseEntity {
    @OneToOne(fetch = LAZY)
    @JoinColumn(name = "person_id", unique = true)
    private Person person;
    private String loginId;                     // UK는 04 DDL 참조 (alive_uk 방식)
    private String passwordHash;
    @Enumerated(STRING) private AccountStatus status;
    private Instant lastLoginAt;
}

@Entity
public class FamilyMembership extends BaseEntity {
    @ManyToOne(fetch = LAZY) @JoinColumn(name = "family_id") private Family family;
    @ManyToOne(fetch = LAZY) @JoinColumn(name = "person_id") private Person person;
    @Enumerated(STRING) private FamilyRole role;
    private Instant joinedAt;
    // UK는 04 DDL 참조
}
```

### 3-2. 가계부
```java
@Entity
public class Account extends FamilyScopedEntity {
    private String name;
    @Enumerated(STRING) private AssetType assetType;
    @Enumerated(STRING) private AccountOwnerType ownerType;   // PERSON/FAMILY
    private Long ownerPersonId;                                // FAMILY면 null
    @Enumerated(STRING) private Visibility visibility;
}

@Entity
public class Transaction extends FamilyScopedEntity {
    @Enumerated(STRING) private TransactionType transactionType;
    private BigDecimal amount;
    private String currency;                    // 기본 "KRW"
    private Long sourceAccountId;               // 나간 계좌 (nullable)
    private Long targetAccountId;               // 들어온 계좌 (nullable)
    private Long categoryId;
    private Long subjectPersonId;               // 가계부 subject=단일
    @Enumerated(STRING) private Visibility visibility;
    @Enumerated(STRING) private SettlementStatus settlementStatus;
    private Instant occurredAt;
    private String memo;
    private Long collectionId;
    @Enumerated(STRING) private TransactionSource source;
}

@Entity
public class Category extends FamilyScopedEntity {
    private String name;
    @Enumerated(STRING) private CategoryType type;
    private Long parentId;                       // 계층
    private boolean isSystem;                     // 기본 시딩 여부
}
```

### 3-3. 일정
```java
@Entity
public class Schedule extends FamilyScopedEntity {
    private String title;
    @Column(columnDefinition = "TEXT") private String description;
    private String location;                     // 신규
    private Instant startedAt;                   // 시점 일정(UTC). 종일이면 null
    private Instant endedAt;
    private LocalDate startDate;                  // 종일 일정(날짜밀림 방지). 시점일정이면 null
    private LocalDate endDate;
    private boolean allDay;                       // true→date 사용, false→datetime 사용
    @Enumerated(STRING) private Visibility visibility;
    @Enumerated(STRING) private ScheduleType scheduleType;
    private boolean done;
    private String recurrenceRule;               // RRULE
    private Long collectionId;
    private String googleEventId;
    @Enumerated(STRING) private SyncStatus syncStatus;
    private Instant lastSyncedAt;
}

@Entity   // subject=다중
public class ScheduleSubject extends FamilyScopedEntity {
    private Long scheduleId;
    private Long personId;
    // UK는 04 DDL 참조
}

@Entity   // 실제 참석자
public class ScheduleParticipant extends FamilyScopedEntity {
    private Long scheduleId;
    private Long personId;
    // UK는 04 DDL 참조
}
```

### 3-4. 일상기록
```java
@Entity
public class Diary extends FamilyScopedEntity {
    private String title;
    @Column(columnDefinition = "TEXT") private String content;
    @Enumerated(STRING) private Visibility visibility;
    private Instant recordedAt;                  // 작성/기록 시각(UTC)
    private LocalDate recordedOn;                 // 기록 대상 날짜(로컬). 타임라인 날짜축 정렬 기준
    private Long collectionId;
}

@Entity   // subject=다중
public class DiarySubject extends FamilyScopedEntity {
    private Long diaryId;
    private Long personId;
    // UK는 04 DDL 참조
}

@Entity
public class Photo extends FamilyScopedEntity {
    private Long diaryId;                         // nullable
    private String storageKey;                    // 상대 키
    private String originalFilename;
    private String photoHash;                     // SHA-256
    private Integer width;
    private Integer height;
    private Instant takenAt;
    private Long fileSize;
    private String mimeType;
    private Long collectionId;
}
```

### 3-5. 태그 / 묶음 / 설정
```java
@Entity
public class Tag extends FamilyScopedEntity {
    private String name;
    // UK는 04 DDL 참조
}

@Entity
public class TransactionTag extends FamilyScopedEntity {
    private Long transactionId;
    private Long tagId;
    // UK는 04 DDL 참조
}

@Entity
public class DiaryTag extends FamilyScopedEntity {
    private Long diaryId;
    private Long tagId;
    // UK는 04 DDL 참조
}

@Entity
public class CollectionTag extends FamilyScopedEntity {  // V4(2026-07 리뉴얼) — 묶음 태그. Schedule엔 태그를 붙이지 않고 Collection에만
    private Long collectionId;
    private Long tagId;
    // UK는 04 DDL 참조(diary_tag/transaction_tag와 동일 패턴)
}

@Entity
public class Collection extends FamilyScopedEntity {
    private String name;
    @Column(columnDefinition = "TEXT") private String description;
    private Long coverPhotoId;                    // 앨범 썸네일 (nullable)
    private Instant startedAt;
    private Instant endedAt;
}

@Entity
public class PersonSetting extends BaseEntity {   // family_id 없음
    private Long personId;                         // UK는 04 DDL 참조. soft-delete 비대상(설정은 물리 관리)
    @Enumerated(STRING) private DetailLevel sharedScheduleDetailLevel;
    @Enumerated(STRING) private ViewMode ledgerDefaultView;    // INLINE/CALENDAR, V2(2026-07)
    @Enumerated(STRING) private ViewMode scheduleDefaultView;  // INLINE/CALENDAR, V2
    @Enumerated(STRING) private ViewMode diaryDefaultView;     // INLINE/CALENDAR, V2
}
```

---

## 4. 설계 규칙 요약

1. **시각은 모두 `Instant`** → DB에 UTC. 표시 변환은 프레젠테이션 계층(사용자 timezone)
2. **Enum은 STRING 매핑** (ORDINAL 금지)
3. **연관관계 LAZY 기본**, 양방향 최소화, 단순 참조는 Long ID 컬럼
4. **Soft Delete는 Hibernate 7 `@SoftDelete`** (BaseEntity), deleted_by는 서비스에서 보조 처리
5. **UNIQUE는 생성컬럼 alive_uk 방식** (deleted_at 마이크로초) — 살아있는 중복만 차단, 재사용 허용. Flyway DDL이 SSOT
6. **멀티테넌트는 Hibernate `@Filter`** (FamilyScopedEntity), 요청별 familyId 주입
   — 단, **MyBatis 쿼리는 family_id·deleted_at 조건 수동 명시** (자동필터 미적용)
7. **JPA+MyBatis 경계**: 도메인 CRUD=JPA, 타임라인·통계·검색=MyBatis
8. **Auditing**(createdBy 등)은 `AuditorAware<Long>` + `@EnableJpaAuditing`

---

## 5. 검토 필요 / 열린 항목

**해결됨**
- ~~UNIQUE+soft-delete 충돌~~ → 생성컬럼 alive_uk 방식 (1-4). MySQL 재검증 완료
- ~~MyBatis 병행 여부~~ → JPA+MyBatis 혼용 확정 (1-6)
- ~~createdBy NULL 시점~~ → **created_by/updated_by/deleted_by 모두 nullable**. 시스템 시딩은 NULL(또는 예약 ID). DDL 확정
- ~~Flyway 초기 마이그레이션~~ → V1__init.sql 작성·MySQL 검증 완료 (04)
- ~~유니크 제약 SSOT~~ → **Flyway DDL 단일 관리**, JPA는 매핑만 (1-4)

**남은 항목 (구현 단계)**
1. **deleted_by 보조 처리** — `@SoftDelete`가 deleted_at만 관리 → 삭제 직전 서비스/AOP에서 deleted_by set
2. **PersonSetting 생성 시점** — 조회 시 없으면 기본값 응답(lazy), 변경 시 행 생성 (upsert). 06 참조

---

## 6. 다음 단계
**백엔드 레이어/패키지 구조** → 인증(JWT) → Transaction API → Schedule → Diary → Timeline → Photo → Telegram → Google Calendar
