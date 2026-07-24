# 가족 생활기록 시스템 — DB 설계 (v3)

> 기능 명세서 v4 기준 / 5~6차 리뷰 반영
> v2→v3 변경: Category is_system 플래그+기본 시딩, Account owner_type(공용통장 대비),
>   Schedule.location, Collection.cover_photo_id
> v1→v2 변경: Person.family_id 제거, BaseEntity/FamilyScopedEntity 분리, source/target_account_id,
>   Photo.original_filename, ViewerPreference→PersonSetting 단순화, 인덱스 보강
> 대상 DB: MySQL (기존 서버에 신규 스키마 `family_os` 추가)

---

## 1. 설계 원칙

### 1-1. 공통 상속 구조 — BaseEntity / FamilyScopedEntity 분리

상속을 2단계로 둔다. JPA에서 각각 `@MappedSuperclass`.

**BaseEntity** (모든 엔티티 공통)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT PK AUTO_INCREMENT | 대리키 |
| `created_at` | DATETIME(6) UTC | 생성 시각 |
| `created_by` | BIGINT FK→Person NULL | 생성자(=author) |
| `updated_at` | DATETIME(6) UTC | 수정 시각 |
| `updated_by` | BIGINT FK→Person NULL | 수정자 |
| `deleted_at` | DATETIME(6) UTC NULL | Soft Delete (NULL=정상) |
| `deleted_by` | BIGINT FK→Person NULL | 삭제자 |

**FamilyScopedEntity** (extends BaseEntity, family_id 추가)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| `family_id` | BIGINT FK→Family | 멀티테넌트 경계 |

**상속 구분**

| 상속 | 엔티티 |
|------|--------|
| BaseEntity만 | Family, Person, UserAccount, FamilyMembership |
| FamilyScopedEntity | Transaction, Account, Category, Schedule, ScheduleSubject, ScheduleParticipant, Diary, DiarySubject, Photo, Tag, TransactionTag, DiaryTag, Collection |

> family_id 멀티테넌트 필터(Hibernate `@Filter`)는 FamilyScopedEntity에만 일괄 적용 → 안전·일관

### 1-2. 시각(Timezone) 규칙
- **모든 시점(timestamp) 컬럼 UTC 저장** (created_at, updated_at, deleted_at, taken_at, started_at, ended_at, occurred_at, recorded_at 등)
- **⚠️ 날짜만 의미있는 값은 DATE로 별도 저장** (UTC 변환 시 날짜 밀림 방지)
  - 종일 일정: `start_date`/`end_date` (DATE), 시점 일정은 `started_at`/`ended_at` (UTC)
  - 일상기록 날짜축: `recorded_on` (DATE) — 타임라인 정렬 기준 / `recorded_at`(UTC)은 작성시각
  - 예: 한국 6/19 종일 일정을 UTC로 저장하면 6/18로 밀림 → DATE로 저장해 회피
- 표시 시점에 사용자 Timezone으로 변환 (기본 Asia/Seoul)
- 해외여행(발리/일본 등) 사진·일정 정렬 정확성 확보
- Person에 `timezone` 컬럼 (기본 Asia/Seoul)

### 1-3. Soft Delete 규칙
- 물리 삭제 금지. 모든 조회 `deleted_at IS NULL` 기본 적용
- JPA `@SQLDelete` + `@Where(clause="deleted_at IS NULL")` 또는 Hibernate `@SoftDelete`

### 1-4. 멀티테넌트 규칙
- FamilyScopedEntity 조회는 "현재 멤버인 family_id" 범위 강제
- 권한 2단계: ① Family 멤버 여부(FamilyMembership) → ② author/subject/visibility

---

## 2. ERD 개요 (엔티티 그룹) — 총 21개 테이블

```
[인물/가족]   Family, Person, UserAccount, FamilyMembership   (BaseEntity)
[가계부]      Transaction, Account, Category                  (FamilyScoped)
[일정]        Schedule, ScheduleSubject, ScheduleParticipant  (FamilyScoped)
[일상기록]    Diary, DiarySubject, Photo                      (FamilyScoped)
[태그]        Tag, TransactionTag, DiaryTag                   (FamilyScoped)
[묶음]        Collection  (+ 각 테이블 collection_id)          (FamilyScoped)
[설정]        PersonSetting                                   (BaseEntity)
[인증]        RefreshToken                                    (단순 — id/account_id/jti/expires/revoked)
[외부연동]    GoogleSyncState, TelegramPersonMap              (FamilyScoped)
```
> 코어 18 + 인증/외부연동 3 = **21개**. 상세 DDL은 04-V1__init.sql (MySQL 검증 완료)

---

## 3. 테이블 상세

### 3-1. 인물 / 가족

#### Family
> 최상위 경계. BaseEntity만 상속(family_id 없음).

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(100) | 가족 이름 (예: "우리 가족") |
| (+ BaseEntity) | | |

#### Person
> 가족 구성원 그 자체. author/subject가 가리키는 대상.
> **family_id 없음** — 소속은 FamilyMembership으로만 표현 (다가족 대응). BaseEntity만 상속.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| name | VARCHAR(100) | 이름 |
| birth_date | DATE NULL | 자녀 성장기록·나이 계산용 |
| timezone | VARCHAR(50) | 기본 'Asia/Seoul' |
| (+ BaseEntity) | | |

> role(PARENT/CHILD)은 Person이 아니라 FamilyMembership에 둠 — 가족마다 역할이 다를 수 있음

#### UserAccount
> 로그인 수단(선택). 로그인하는 Person만 가짐. BaseEntity만 상속.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| person_id | BIGINT FK→Person UNIQUE | 1:1 (Person당 최대 1계정) |
| login_id | VARCHAR(100) UNIQUE | 로그인 ID |
| password_hash | VARCHAR(255) | 해시 (BCrypt 등) |
| status | ENUM('ACTIVE','DISABLED') | |
| last_login_at | DATETIME(6) UTC NULL | |
| (+ BaseEntity) | | |

> 자녀: Person만 존재, UserAccount 없음 → 성장 후 row 1개 추가로 전환 (데이터 이관 0)

#### FamilyMembership
> Person ↔ Family 다대다 + 가족 내 역할. BaseEntity만 상속.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK→Family | |
| person_id | BIGINT FK→Person | |
| role | ENUM('PARENT','CHILD') | 해당 가족 내 역할 |
| joined_at | DATETIME(6) UTC | |
| UNIQUE(family_id, person_id) | | 중복 가입 방지 |

---

### 3-2. 가계부

#### Account (계좌/결제수단)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK | |
| name | VARCHAR(100) | 예: 신한카드, 카카오페이 |
| asset_type | ENUM('BANK','SECURITIES','CASH') | 자산 유형 |
| **owner_type** | ENUM('PERSON','FAMILY') | 개인 계좌 / 공용 계좌 |
| **owner_person_id** | BIGINT FK→Person NULL | 개인 계좌일 때 소유자 (FAMILY면 NULL) |
| visibility | ENUM('PRIVATE','PARENTS','FAMILY') | |
| (+ FamilyScopedEntity) | | |

> **공용 통장 대비**: owner_type=FAMILY면 공용계좌(생활비 통장·공용 CMA 등), owner_person_id NULL.
> 현재 공용계좌는 정산 저수지로만 쓰이지만, B 정산 구현 시 모델에 등록 가능하도록 미리 대응.

> **계좌 삭제 정책**: Account는 soft-delete(deleted_at). 삭제해도 과거 거래의 source/target_account_id는
> 유지됨(FK 보존). 통계·타임라인에서 삭제된 계좌의 과거 거래는 정상 집계하되, 계좌명은 "(삭제됨)" 표기.
> 삭제된 계좌는 신규 거래 입력 선택지에서만 제외. 거래 이력 보존이 원칙.

#### Category
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK | |
| name | VARCHAR(100) | 식비, 공과금 등 |
| type | ENUM('INCOME','EXPENSE') | |
| parent_id | BIGINT FK→Category NULL | 계층 카테고리 |
| **is_system** | BOOLEAN DEFAULT FALSE | 기본 제공 카테고리(삭제 방지) |
| (+ FamilyScopedEntity) | | |

> **기본 시딩**: 가족 생성 시 기본 카테고리 세트(식비/교통/통신/공과금 등)를 자동 생성하며 is_system=TRUE.
> 사용자 추가분은 is_system=FALSE. 테이블 분리 대신 플래그로 "기본/커스텀" 구분 → 조회 단순.

#### Transaction (수입/지출/이체 통합)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK | |
| transaction_type | ENUM('INCOME','EXPENSE','TRANSFER') | **이체는 통계 제외** |
| amount | DECIMAL(15,2) | |
| currency | VARCHAR(3) | 기본 'KRW' |
| **source_account_id** | BIGINT FK→Account NULL | **나간 계좌** (지출·이체 출금) |
| **target_account_id** | BIGINT FK→Account NULL | **들어온 계좌** (수입·이체 입금) |
| category_id | BIGINT FK→Category NULL | |
| subject_person_id | BIGINT FK→Person NULL | **가계부 subject=단일** |
| visibility | ENUM('PRIVATE','PARENTS','FAMILY') | 기본 PRIVATE |
| settlement_status | ENUM('NONE','PENDING','SETTLED') DEFAULT 'NONE' | 정산 상태 (MVP=전부 NONE) |
| occurred_at | DATETIME(6) UTC | 거래 시각 |
| memo | VARCHAR(500) NULL | |
| collection_id | BIGINT FK→Collection NULL | 이벤트 묶음 (방식 A) |
| source | ENUM('MANUAL','TEXT','TELEGRAM') | 입력 경로 |
| (+ FamilyScopedEntity: author=created_by) | | |

**source/target 계좌 규칙** (거래 유형별 명확화)

| type | source_account | target_account |
|------|----------------|----------------|
| EXPENSE | 신한카드 | NULL |
| INCOME | NULL | 신한은행 |
| TRANSFER | 신한은행 | CMA |

#### Account / Transaction visibility 독립 규칙
- **거래 내용·금액·카테고리**: transaction.visibility 따름 (PARENTS면 배우자도 봄)
- **결제 계좌(source/target)**: 계좌 소유자(개인)만 조회. 공유 거래라도 배우자에겐 계좌 숨김
- 운영 방식: 각자 개인 카드로 선지출 → 공용계좌서 정산. "무엇에 썼나"는 공유, "어느 카드"는 개인
- 거래 visibility가 계좌 visibility를 넘을 수 있음 (의도된 동작)

#### 정산(Settlement) 모델 — 건별, 확장(B)

**현재 전제: 공용계좌 카드 없음 — 실제 결제 100% 개인 카드**
- 결제 계좌가 항상 개인카드라 정산 여부를 가르는 변수 아님
- **정산 필요 여부 = visibility만으로 결정** (공동→PENDING, 개인→NONE)
- 입력 UX: 사용자는 visibility만 선택, settlement_status 자동 추천
- 저장: settlement_status 독립 컬럼 유지 ("공동이지만 정산 포기" 예외, PENDING/SETTLED 구분)

**단계별**
- MVP(A): 전부 NONE, 정산 미구현 = 공동 지출 기록만
- 확장(B): **건별 정산** — 공동 1건 PENDING → 공용계좌서 그 건 가져가면 SETTLED
  - 전환: PENDING 단계선 개인↔공동 자유 / SETTLED는 정산 취소 후 변경
  - B 구현 시 추가: `settled_at`, `settlement_account_id`
  - 묶음 아님, 1:1 건별

**미래 대비**: 공용카드 생기면 "공동+공용카드=정산불필요" → 규칙을 visibility+account 기반으로 확장 (구조 변경 불필요)

---

### 3-3. 일정

#### Schedule
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK | |
| title | VARCHAR(200) | |
| description | TEXT NULL | |
| **location** | VARCHAR(255) NULL | 장소 (예: 동탄 제일병원). 구글 동기화 매핑 |
| started_at | DATETIME(6) UTC NULL | 시점 일정(UTC). 종일이면 NULL |
| ended_at | DATETIME(6) UTC NULL | |
| **start_date** | DATE NULL | 종일 일정 시작일(날짜밀림 방지). 시점일정이면 NULL |
| **end_date** | DATE NULL | 종일 일정 종료일 |
| is_all_day | BOOLEAN | 1→date 사용, 0→datetime 사용 |
| visibility | ENUM('PRIVATE','SHARED_PERSONAL','PARENTS','FAMILY') | |
| schedule_type | ENUM('EVENT','TODO','REMINDER') | |
| is_done | BOOLEAN | TODO용 |
| recurrence_rule | VARCHAR(255) NULL | 반복(RRULE) |
| collection_id | BIGINT FK→Collection NULL | |
| google_event_id | VARCHAR(255) NULL | 구글 연동 |
| sync_status | ENUM('SYNCED','PENDING','CONFLICT','DELETED_REMOTE') NULL | |
| last_synced_at | DATETIME(6) UTC NULL | |
| (+ FamilyScopedEntity) | | |

#### ScheduleSubject (일정 subject=다중)
| 컬럼 | 타입 |
|------|------|
| id | BIGINT PK |
| schedule_id | BIGINT FK→Schedule |
| person_id | BIGINT FK→Person |
| UNIQUE(schedule_id, person_id) | |

> 복합키(@EmbeddedId) 대신 id PK + UNIQUE 유지 — JPA 현실적 선택

#### ScheduleParticipant (실제 참석자)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| schedule_id | BIGINT FK→Schedule | |
| person_id | BIGINT FK→Person | |
| UNIQUE(schedule_id, person_id) | | RSVP 컬럼은 확장 시 추가 |

> subject(누구에 관한가)와 participant(실제 참석)는 별개 개념

---

### 3-4. 일상기록

#### Diary
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK | |
| title | VARCHAR(200) NULL | |
| content | TEXT | 글 |
| visibility | ENUM('PRIVATE','SHARED_PERSONAL','PARENTS','FAMILY') | |
| recorded_at | DATETIME(6) UTC | 작성/기록 시각(UTC) |
| recorded_on | DATE | 기록 대상 날짜(로컬). 타임라인 날짜축 정렬 기준 |
| collection_id | BIGINT FK→Collection NULL | |
| (+ FamilyScopedEntity) | | |

#### DiarySubject (일상기록 subject=다중)
| 컬럼 | 타입 |
|------|------|
| id | BIGINT PK |
| diary_id | BIGINT FK→Diary |
| person_id | BIGINT FK→Person |
| UNIQUE(diary_id, person_id) | |

#### Photo
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK | |
| diary_id | BIGINT FK→Diary NULL | 소속 기록(없을 수도) |
| storage_key | VARCHAR(500) | **상대 키** (family_3/2026/05/photo_abc.jpg) |
| **original_filename** | VARCHAR(255) | **원본 파일명** (IMG_0234.JPG) — 다운로드·복원용 |
| photo_hash | CHAR(64) | SHA-256, 중복제거·무결성 |
| width | INT | |
| height | INT | |
| taken_at | DATETIME(6) UTC NULL | EXIF 촬영시각 (타임라인 정렬) |
| file_size | BIGINT | bytes |
| mime_type | VARCHAR(50) | image/jpeg 등 |
| collection_id | BIGINT FK→Collection NULL | |
| (+ FamilyScopedEntity) | | |

> **확장 메모**: 영수증 PDF·증명서·가족 문서 저장 니즈가 구체화되면 Attachment(상위, 타입 무관) +
> Photo(하위, 이미지 메타) 구조로 분리. 지금 일반화는 이른 추상화 위험 → MVP는 Photo 유지.

---

### 3-5. 태그

#### Tag (공통)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK | |
| name | VARCHAR(50) | #여행, #육아 |
| UNIQUE(family_id, name) | | |

#### TransactionTag / DiaryTag (브릿지)
| 컬럼 | 타입 |
|------|------|
| id | BIGINT PK |
| {transaction_id / diary_id} | BIGINT FK |
| tag_id | BIGINT FK→Tag |
| UNIQUE(대상_id, tag_id) | |

> **확장 메모**: 향후 ScheduleTag / PhotoTag / CollectionTag 추가 가능. MVP는 Transaction·Diary만.

---

### 3-6. 이벤트 묶음

#### Collection
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK | |
| name | VARCHAR(200) | 예: 2026 여름휴가 |
| description | TEXT NULL | |
| **cover_photo_id** | BIGINT FK→Photo NULL | 앨범 썸네일 (순환 참조 방지 위해 nullable) |
| started_at | DATETIME(6) UTC NULL | 기간 시작 |
| ended_at | DATETIME(6) UTC NULL | 기간 끝 |
| (+ FamilyScopedEntity) | | |

> 연결: **방식 A** — Transaction/Schedule/Diary/Photo가 각각 `collection_id`.
> 한 항목이 여러 Collection 소속 필요 시 CollectionItem 브릿지로 전환.

#### collection_tag (V4, 2026-07 리뉴얼 추가)
`diary_tag`/`transaction_tag`와 동일 패턴(`collection_id`, `tag_id`, alive_uk 유니크). 묶음을 태그로 검색/분류하기 위함 — Schedule 자체엔 태그를 붙이지 않고, 묶음 태그를 통해 연결된 일정·가계부·기록까지 함께 조회.

---

### 3-7. 개인 설정

#### PersonSetting (ViewerPreference 단순화)
> 공유개인 일정 표시 수준 등 개인별 환경설정. BaseEntity만 상속.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| person_id | BIGINT FK→Person UNIQUE | 설정 소유자 |
| shared_schedule_detail_level | ENUM('FULL','SUMMARY') | 타인 공유개인 일정 기본 표시 수준 |
| ledger_default_view | ENUM('INLINE','CALENDAR') DEFAULT 'CALENDAR' | 가계부 화면 기본 뷰 (V2 추가, V3에서 기본값 CALENDAR로 변경) |
| schedule_default_view | ENUM('INLINE','CALENDAR') DEFAULT 'CALENDAR' | 일정 화면 기본 뷰 (V2, V3) |
| diary_default_view | ENUM('INLINE','CALENDAR') DEFAULT 'CALENDAR' | 기록 화면 기본 뷰 (V2, V3) |
| (+ BaseEntity) | | |

> **단순화 근거**: 부부 2명 규모에선 "배우자 일정 상세/요약" 전역 설정 하나로 충분.
> **확장**: "배우자는 상세, 자녀는 요약" 같은 대상별 차등이 필요해지면
> ViewerPreference(viewer_person_id × target_person_id × detail_level) 테이블 추가.

---

## 4. 주요 인덱스 (초안)

| 테이블 | 인덱스 | 용도 |
|--------|--------|------|
| Transaction | (family_id, occurred_at) | 기간 조회·통계 |
| Transaction | (family_id, category_id) | 카테고리 통계 |
| Transaction | **(family_id, visibility, occurred_at)** | 타임라인(권한 필터 포함) |
| Schedule | (family_id, started_at) | 캘린더 조회 |
| Schedule | **(family_id, visibility, started_at)** | 타임라인(권한 필터 포함) |
| Schedule | **(family_id, start_date)** | 종일 일정 타임라인(started_at NULL일 때) |
| Schedule | (google_event_id) | 동기화 매칭 |
| Diary | (family_id, recorded_at) | 타임라인 |
| Diary | **(family_id, visibility, recorded_at)** | 타임라인(권한 필터 포함) |
| Photo | (family_id, taken_at) | 앨범·타임라인 정렬 |
| Photo | (photo_hash) | 중복 검출 |
| Tag | (family_id, name) | 태그 검색 (V1.5) |
| * 전체 | deleted_at | Soft Delete 필터 |

---

## 5. 확장 시 추가 예정 (지금 미생성, 문만 열어둠)

| 테이블 | 트리거 시점 |
|--------|-------------|
| AccessControl | visibility 프리셋으로 표현 안 되는 커스텀 공개("배우자+첫째만") 필요 시 |
| ViewerPreference | 대상별 표시 수준 차등 필요 시 (PersonSetting 확장) |
| CollectionItem | 한 항목이 여러 Collection 소속 필요 시 (방식 B 전환) |
| ChangeHistory | 변경 이력 상세 추적 강화 시 (간소화 Audit 확장) |
| Attachment | 영수증 PDF·증명서·문서 저장 니즈 구체화 시 (Photo 상위 일반화) |
| ScheduleTag / PhotoTag / CollectionTag | 해당 항목 태그 니즈 발생 시 |
| 가계부 FAMILY visibility | 자녀 로그인(용돈 가계부) 시점 |
| RSVP status | 큰 행사 참석 관리 필요 시 (ScheduleParticipant에 컬럼) |
| 정산 기능(B) | settlement_status 활성화 + settled_at/settlement_account_id, 건별 정산 UI |

---

## 6. 다음 단계
**JPA Entity 설계** — BaseEntity/FamilyScopedEntity 추상클래스, 각 엔티티 매핑, 연관관계 fetch 전략(지연로딩 기본), Soft Delete 적용 방식, 멀티테넌트 필터 → API 명세 → 구현
