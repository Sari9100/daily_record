# backend/CLAUDE.md — Family OS 백엔드

> 루트 `../CLAUDE.md`의 절대 규칙(soft delete / family_id / alive_uk / alive검증 / visibility / 시각·날짜 / 시크릿)을 먼저 따른다.
> 이 파일은 **패키지 구조와 새 도메인 추가 절차**를 정의한다. (설계 문서 06-9, master 10장이 "구현 직전 작성"으로 미뤘던 부분)

---

## 0. 스택 고정값

- Spring Boot **4.1.0** / Java **21** / Hibernate **7.2** / MySQL **8.x** / Flyway / Spring Security **7**
- 빌드: Gradle. ID 전략: IDENTITY(AUTO_INCREMENT). Enum: `@Enumerated(STRING)` (ORDINAL 금지).
- Spring Framework 7 null 안정성: `org.jspecify.annotations` 사용.
- ORM 경계: **도메인 CRUD·단순조회 = JPA / 타임라인·통계·검색 = MyBatis**.

---

## 1. 패키지 구조 (계층 + 도메인 혼합)

```
com.familyos
├── FamilyOsApplication.java
├── common/                        ← 횡단 관심사 (도메인 무관, 가장 먼저 구축)
│   ├── entity/
│   │   ├── BaseEntity.java            (@MappedSuperclass: id, created/updated/deleted_*, @SoftDelete)
│   │   └── FamilyScopedEntity.java    (extends Base, + family_id, @Filter familyFilter)
│   ├── context/
│   │   ├── FamilyContext.java         (요청 스코프 ThreadLocal: familyId, personId, role)
│   │   └── AuthUser.java              (principal: personId, accountId, familyId, role)
│   ├── security/
│   │   ├── SecurityConfig.java        (SecurityFilterChain 빈, STATELESS, csrf.disable, authorizeHttpRequests)
│   │   ├── JwtAuthenticationFilter.java (토큰 추출·검증 → SecurityContext + FamilyContext 주입)
│   │   ├── JwtProvider.java           (생성·검증. Access/Refresh 시크릿 분리)
│   │   └── VisibilityGuard.java       (assertCanView / assertCanEdit — 위반 시 404)
│   ├── tenant/
│   │   ├── FamilyFilterAspect.java    (요청마다 Hibernate enableFilter("familyFilter") 활성화)
│   │   └── MyBatisTenantInterceptor.java (MyBatis 쿼리에 familyId 주입/강제 — ★ 자동 안 됨 보완)
│   ├── audit/
│   │   ├── AuditorAwareImpl.java       (@CreatedBy/@LastModifiedBy = 현재 personId)
│   │   └── SoftDeleteSupport.java      (삭제 직전 deleted_by set 공통 처리: AOP 또는 서비스 헬퍼)
│   ├── web/
│   │   ├── ApiResponse.java            ({success, data, error} 공통 래퍼)
│   │   ├── PageResponse.java           ({content, page, size, totalElements, totalPages})
│   │   └── GlobalExceptionHandler.java (@RestControllerAdvice: 400/401/403→404/409/422 → ApiResponse.error)
│   └── error/
│       ├── BusinessException.java      (code + message, 422)
│       ├── NotFoundException.java      (404 — 권한 위반·타가족도 여기로 숨김)
│       └── ErrorCode.java              (VALIDATION_ERROR, UNAUTHENTICATED, FORBIDDEN_VISIBILITY, NOT_FOUND, CONFLICT, BUSINESS_RULE)
│
├── person/                        ← 도메인 (각 도메인은 동일한 내부 구조)
│   ├── entity/        (Family, Person, UserAccount, FamilyMembership, PersonSetting)
│   ├── repository/    (Spring Data JPA)
│   ├── service/
│   ├── controller/
│   └── dto/           (request / response)
├── auth/              (login·refresh·logout·me, RefreshToken 엔티티·회전 로직)
├── ledger/            (Account, Category, Transaction + 통계)
│   ├── ...
│   └── mapper/        (MyBatis: TransactionStatisticsMapper)  ← resources/mapper/ XML과 짝
├── schedule/          (Schedule, ScheduleSubject, ScheduleParticipant)
├── diary/             (Diary, DiarySubject, Photo)
├── tag/               (Tag, TransactionTag, DiaryTag)
├── collection/        (Collection + summary)
├── timeline/          (★ MyBatis 전용 — 다중 테이블 날짜축 병합)
│   ├── controller/
│   ├── service/
│   └── mapper/
├── storage/           (StorageService 인터페이스 + LocalSsdStorage 구현)
└── integration/       (2·3단계: google/, telegram/ — 코어 완료 전 작업 금지)
```

규칙:
- **도메인 패키지 내부는 항상 `entity / repository / service / controller / dto` (+ 필요 시 mapper)** 동일 구조.
- `common/`을 **가장 먼저** 완성한다. BaseEntity·FamilyScoped·Security·Context 없이는 어떤 도메인도 못 짠다.
- 도메인 간 직접 의존 최소화. 다른 도메인 데이터가 필요하면 ID 참조 + 서비스 경유.

---

## 2. 횡단 관심사 — 어디서 무엇을 하는가

| 관심사 | 위치 | 동작 |
|--------|------|------|
| 인증(JWT 검증) | `JwtAuthenticationFilter` | 헤더 토큰 → 검증 → `AuthUser` 생성 → SecurityContext + FamilyContext 저장 |
| family 격리 (JPA) | `FamilyFilterAspect` | 요청 시작 시 `enableFilter("familyFilter")` |
| family 격리 (MyBatis) | `MyBatisTenantInterceptor` + 매퍼 규칙 | familyId 파라미터 주입. **매퍼 WHERE에도 직접 명시** |
| created_by/updated_by | `AuditorAwareImpl` + `@EnableJpaAuditing` | 현재 personId 자동 주입 |
| deleted_by | `SoftDeleteSupport` | soft-delete 직전 서비스에서 set |
| 단건 권한 | `VisibilityGuard` | 서비스에서 호출, 위반 시 `NotFoundException`(404) |
| 목록 권한 | 각 Repository/Mapper 쿼리 | WHERE 절에 visibility 조건 (메모리 필터 금지) |
| alive 검증 | 각 도메인 서비스 | 참조 대상 `deleted_at IS NULL` 확인, 위반 시 `BusinessException`(422) |
| 예외→응답 | `GlobalExceptionHandler` | 모든 예외를 `ApiResponse.error`로 변환 |

---

## 3. 🟢 골든 패스 — 새 도메인/엔드포인트 추가 순서

새 기능을 만들 때 **항상 이 순서**로, 기존 `ledger`(Transaction) 패턴을 모방한다:

1. **설계 문서 확인** — 04(DDL)에서 테이블 구조, 05(API)에서 엔드포인트·검증 규칙, 03(JPA)에서 매핑 규칙.
2. **엔티티** — `FamilyScopedEntity`(또는 `BaseEntity`) 상속. Enum은 STRING. 연관은 LAZY, 단순 참조는 Long ID. **유니크는 JPA에 적지 않는다**(DDL이 SSOT).
3. **DTO** — request/response 분리. 응답 boolean은 `isXxx` 표기(05 직렬화 규칙). family_id·settlement_status 등 **서버 결정 값은 request에서 받지 않는다.**
4. **Repository** — JPA. 목록 조회는 visibility WHERE 포함(또는 Specification).
5. **Service** — 비즈니스 검증 순서:
   a. 입력 검증(거래유형-계좌 규칙 등) → 422
   b. **참조 대상 alive 검증** → 422
   c. **VisibilityGuard**(단건 수정/삭제 시) → 404
   d. 서버 결정 값 세팅(settlement_status, source 등)
   e. 저장 / soft-delete(+deleted_by)
6. **Controller** — `/api/v1/...`, `ApiResponse`로 감싸기. family_id는 받지 않고 `FamilyContext`에서.
7. **MyBatis가 필요하면**(통계/타임라인/검색) `mapper/` + `resources/mapper/*.xml`. **WHERE에 family_id·deleted_at 직접 명시.**
8. **테스트** — family 격리, visibility, alive 검증, 도메인 규칙 각각.

---

## 4. 도메인별 핵심 규칙 (구현 시 참조)

- **auth**: Access 30분(sub=personId, accountId, familyId, role) / Refresh 14일(jti, familyId·role 불포함). 회전식 + 재사용 감지 시 계정 전체 폐기. **단, revoked 직후 5~10초 grace window**(정상 동시요청은 레이스로 간주). `refresh_token`은 BaseEntity 비상속·물리관리.
- **ledger/Transaction**: EXPENSE=source만 / INCOME=target만 / TRANSFER=둘 다(서로 다름) → 위반 422. `settlement_status` 서버 결정(공동→PENDING, 개인→NONE). 통계는 **TRANSFER 제외**, MyBatis 집계. 결제계좌는 **소유자 본인에게만 노출**(타인 공유거래는 계좌 마스킹).
- **ledger/Account**: soft-delete해도 과거 거래 FK 유지, 신규 입력 선택지에서만 제외.
- **schedule**: visibility 4단계. 종일=DATE/시점=Instant 이원화. google 동기화 필드(google_event_id/sync_status)는 **클라가 못 고친다** — 서버·동기화 모듈만.
- **diary/Photo**: 업로드 2단계(presign→complete). photo_hash 중복 시 기존 반환. 사진도 **권한 통제 대상** — 직접 URL 노출 금지(인증 후 서빙).
- **timeline**: 전부 MyBatis. 날짜 그룹 키(일정=start_date/started_at 로컬날짜, 기록=recorded_on, 거래=occurred_at 로컬날짜, 사진=taken_at 로컬날짜). 시각 없는 종일/DATE 항목은 그룹 맨 위. visibility+family WHERE 필수.
- **PersonSetting**: lazy — GET 시 행 없으면 기본값(FULL) 응답(404 아님), PUT 최초 시 upsert.

---

## 5. 하지 말 것

- `common/` 없이 도메인부터 만들기 (불가능 — 먼저 구축)
- 코어 완료 전 `integration/`(구글·텔레그램) 손대기
- DDL `V1__init.sql` 직접 수정 (새 V2 마이그레이션으로)
- JPA에 유니크 제약 중복 정의
- 통계/타임라인을 JPA로 짜기 (MyBatis 영역)
- 한 커밋에 여러 도메인 섞기
