# 가족 생활기록 시스템 — 통합 설계 문서 (Master)

> **Family OS** — 부부(자녀 전제 설계)가 함께 쓰는 생활 기록·관리 시스템
> 가계부 + 일상기록 + 일정관리를 하나로 통합
> 작성 기준일: 2026-06 / 통합 대상: 기능명세 v4, DB설계 v3, JPA v1, DDL, API v1, 인증 v1, 프론트 v1

---

## 목차
1. 프로젝트 개요 / 설계 원칙
2. 기술 스택 (확정)
3. 핵심 도메인 개념 (인물·권한·정산)
4. 데이터 모델 (ERD·테이블 요약)
5. 백엔드 설계 (JPA·인증·인가)
6. API 설계 (요약)
7. 프론트엔드 설계
8. 개발 범위 / 로드맵
9. 확장 전략 (멀티가족·클라우드)
10. 후속 과제 / 미작성 문서
- 부록 A: 상세 문서 인덱스

---

## 1. 프로젝트 개요 / 설계 원칙

### 목적
가족 구성원이 일상의 **지출·기록·일정**을 한 곳에서 관리·기록. 세 기능을 날짜축으로 통합하고, 여행/이벤트 단위로 묶어 보는 경험 제공.

### 사용자
부부 2명 시작 → 자녀 확장. **설계는 처음부터 자녀를 전제**로 진행(데이터 이관 회피).

### 설계 원칙
- **Local-First, Cloud-Portable**: 로컬 서버에서 시작하되, 언제든 클라우드로 이식 가능한 상태로 구축
- **Fail-safe / 단계적**: 외부연동은 리스크 낮은 순으로, 중간에 막혀도 그 앞까지 사용 가능
- **구조는 열어두되 지금은 단순하게**: 미래 확장(멀티가족·ACL·정산)의 문은 열되 MVP는 단순 유지

---

## 2. 기술 스택 (확정)

| 영역 | 선택 |
|------|------|
| 백엔드 | Spring Boot 4.1 + Java 21 (LTS) |
| ORM | Spring Data JPA + MyBatis 혼용 (통계·타임라인=MyBatis) |
| DB | MySQL 8.x (기존 서버에 신규 스키마 `family_os`) |
| 마이그레이션 | Flyway |
| 인증 | Spring Security 7 + JWT (Access+Refresh) |
| 사진 저장 | 외장 SSD (StorageService 추상화, 상대 키) |
| 도메인/접근 | Cloudflare (Zero Trust) |
| 프론트 | React Native + Expo / TypeScript (웹=react-native-web) |
| 타겟 | iPhone(iOS) + Galaxy(Android) + 웹 |
| 외부 연동 | 개인 구글 캘린더(양방향), 텔레그램(Hermes Agent) |
| 배포 | Docker 컨테이너화 |

---

## 3. 핵심 도메인 개념

### 3-1. 인물 모델 — Person / UserAccount 분리
> 자녀의 "데이터 대상 → 로그인 사용자" 전환 시 **데이터 마이그레이션 0**을 위해 분리.

- **Person**: 가족 구성원 그 자체. 모든 author/subject가 가리키는 대상 (아빠·엄마·첫째)
- **UserAccount**: 로그인 수단(선택). 로그인하는 Person만 가짐. 자녀는 없음 → 성장 시 1행 추가
- **FamilyMembership**: Person ↔ Family 다대다 + 역할(PARENT/CHILD)

### 3-2. 권한 모델 — author / subject / visibility 3분리

| 축 | 의미 | 형태 |
|----|------|------|
| author | 누가 만들었나 | 단일 (created_by) |
| subject | 누구에 관한 것인가 | 일정·일상기록=다중 / 가계부=단일 |
| visibility | 누가 볼 수 있나 | enum 프리셋 |

**Visibility 레벨**

| 레벨 | 조회 가능자 | 적용 |
|------|------------|------|
| PRIVATE | author 본인만 | 일정·기록·가계부 |
| SHARED_PERSONAL | author + subject 지정자 | 일정·기록만 |
| PARENTS | 같은 family의 PARENT 전원 | 일정·기록·가계부 |
| FAMILY | 같은 family 전원 | 일정·기록 (가계부는 구조만) |

- visibility는 **ACL 프리셋**으로 취급 — 커스텀 조합 필요 시 AccessControl 테이블 확장
- 공유개인 일정 표시수준(상세/요약)은 **보는 쪽(뷰어)의 PersonSetting**이 결정

### 3-3. 가계부 핵심 — 계좌·정산
- 거래 유형: INCOME / EXPENSE / **TRANSFER**(이체, 통계 제외)
- 계좌: source_account_id(나간 곳) / target_account_id(들어온 곳)
- **결제계좌는 소유자 개인만 조회** — 공유 거래라도 배우자에겐 계좌 마스킹
  ("무엇에 썼나"는 공유, "어느 카드"는 개인)
- **정산(현재: 개인카드만 사용)**: 공동=정산필요(PENDING), 개인=NONE.
  MVP는 기록만(전부 NONE), 확장 B에서 건별 정산(공용계좌서 가져가면 SETTLED)

### 3-4. 통합 타임라인 (핵심 차별화)
특정 날짜/기간 = 그날의 **지출+일정+기록+사진**을 한 화면에 병합. MyBatis로 권한 필터 포함 구현.

### 3-5. 이벤트 묶음 (Collection)
여행/이벤트 단위로 지출·기록·일정·사진을 선택 연결(방식 A: collection_id). 묶음 리뷰 제공.

---

## 4. 데이터 모델

### 4-1. 엔티티 그룹 (21 테이블)
```
[인물/가족]  Family, Person, UserAccount, FamilyMembership, PersonSetting   (BaseEntity)
[가계부]     Transaction, Account, Category                                 (FamilyScoped)
[일정]       Schedule, ScheduleSubject, ScheduleParticipant                 (FamilyScoped)
[일상기록]   Diary, DiarySubject, Photo                                     (FamilyScoped)
[태그]       Tag, TransactionTag, DiaryTag                                  (FamilyScoped)
[묶음]       Collection                                                     (FamilyScoped)
[인증]       RefreshToken                                                   (단순)
[외부연동]   GoogleSyncState, TelegramPersonMap                             (FamilyScoped)
```

### 4-2. 공통 컬럼 (상속 2단계)
- **BaseEntity**: id, created_at/by, updated_at/by, deleted_at/by (Soft Delete)
  - created_by/updated_by/deleted_by는 **nullable** (가입·시딩 시 인증주체 없음)
- **FamilyScopedEntity** (extends Base): + family_id (멀티테넌트 경계)
- 시각은 **UTC(Instant)** 저장. **날짜만 의미있는 값(종일일정 start_date, 기록 recorded_on)은 DATE**로 별도 저장(날짜 밀림 방지)

### 4-3. 핵심 제약·규칙
- **Soft Delete**: 물리삭제 금지. UNIQUE는 **생성컬럼 alive_uk 방식**(살아있는 중복만 차단, 재사용 허용)
  - ※ `(col, deleted_at)` 복합은 MySQL NULL 처리 때문에 작동 안 함 → alive_uk 사용
- **SSOT**: 스키마·제약은 Flyway DDL(04)이 단일 진실 원천. JPA는 매핑만
- **인덱스**: 타임라인·통계용 (family_id, visibility, 시각) 복합, photo_hash, google_event_id
- 상세 DDL은 `04-V1__init.sql` 참조 (**sqlglot MySQL 방언 파싱 검증 완료: 21테이블·FK·유니크·생성컬럼 정상. 실제 MySQL 인스턴스 재검증 권장**)

### 4-4. 주요 엔티티 핵심 필드
- **Account**: owner_type(PERSON/FAMILY), owner_person_id(nullable), visibility
- **Transaction**: type, amount, source/target_account_id, subject_person_id(단일), visibility, settlement_status, occurred_at, source(MANUAL/TEXT/TELEGRAM)
- **Schedule**: title, location, started/ended_at(시점) 또는 start/end_date(종일), visibility(4단계), schedule_type, recurrence_rule, google_event_id, sync_status, last_synced_at
- **Diary**: content, visibility(4단계), recorded_at(시각)+recorded_on(날짜축) / **Photo**: storage_key, original_filename, photo_hash, width/height, taken_at
- **Collection**: name, cover_photo_id, started/ended_at

---

## 5. 백엔드 설계

### 5-1. JPA / ORM
- 연관관계 LAZY 기본, 양방향 최소화, 단순 참조는 Long ID
- Enum은 STRING 매핑(ORDINAL 금지)
- Soft Delete: Hibernate 7 `@SoftDelete`(deleted_at), deleted_by는 서비스 보조
- 멀티테넌트: Hibernate `@Filter`(FamilyScoped) — **MyBatis엔 미적용 → family_id 수동 명시**
- 경계: 도메인 CRUD=JPA / 타임라인·통계·검색=MyBatis

### 5-2. 인증 (JWT)
- **Access**(30분): sub(personId), accountId, **familyId**, role 클레임 포함
- **Refresh**(14일): jti 포함, familyId 불포함(재발급 시 DB 최신 조회) — 회전식
- familyId는 로그인 시 FamilyMembership으로 자동 결정, 토큰에서만 추출(위변조 방지)
- RefreshToken 서버 저장(DB), 재사용 감지 시 계정 전체 폐기

### 5-3. Spring Security 7 (Boot 4)
- `SecurityFilterChain` 빈 + `authorizeHttpRequests()` (구 authorizeRequests 제거됨)
- STATELESS 세션, **CSRF 비활성**(헤더 기반 JWT라 안전)
- 커스텀 JwtAuthenticationFilter → 인증 컨텍스트 + FamilyContext 주입

### 5-4. 인가 (visibility) — 2단계 방어
- 목록: 쿼리 레벨 WHERE 필터 (메모리 필터링 금지)
- 단건: 서비스 레벨 VisibilityGuard (위반 시 404로 숨김)
- 수정/삭제: author 본인 (PARENT 관리권은 도메인별)

---

## 6. API 설계 (요약)

- 스타일: REST, `/api/v1`, JSON, 시각 ISO-8601 UTC
- 인증: `Authorization: Bearer`, family_id는 토큰 클레임
- 공통 응답 래퍼 `{success, data, error}`, 페이지네이션, 타가족 자원은 404
- 주요 그룹:
  - `/auth/*` (login, refresh, logout, me)
  - `/accounts`, `/categories`, `/transactions`(+/statistics)
  - `/schedules`(+/done)
  - `/diaries`, `/photos`(presign→complete)
  - `/collections`(+/summary)
  - `/family`(+members, member account), `/me/settings`
  - `/timeline` (날짜축 통합)
  - `/integrations/google/*` (골격)
- 거래 검증: EXPENSE=source만 / INCOME=target만 / TRANSFER=둘 다, settlement_status는 서버 결정
- 상세는 `api-spec-v1.md` 참조

---

## 7. 프론트엔드 설계

### 7-1. 스택·전략
- React Native + Expo / TypeScript, 웹=react-native-web
- **로직 공유 + UI 표현 분기** (앱 우선, 웹 따라오기)
- Expo Router, TanStack Query(서버상태), Zustand(클라상태), RHF+Zod(폼), gluestack-ui(NativeWind), date-fns(+tz)

### 7-2. 네비게이션
- 모바일: 하단 탭바 / 웹: 사이드바 — 동일 5섹션
- [홈/타임라인] [가계부] [일정] [기록] [더보기]

### 7-3. 화면 (MVP ~24개)
- 인증(3), 홈/타임라인(2), 가계부(6), 일정(3), 일상기록(4), 묶음(2), 가족/설정(4)

### 7-4. 코드 공유
- 공유(~100%): api, domain, store, hooks, 폼검증
- 분기: 레이아웃·네비게이션, 네이티브 기능(카메라·사진·시큐어스토어)
- 방법: `.native.tsx`/`.web.tsx`, `Platform.OS`

### 7-5. 핵심 UX
- 빠른입력(위젯 탭→딥링크), 사진 3단계 업로드, visibility 입력, 토큰 자동갱신 인터셉터

---

## 8. 개발 범위 / 로드맵

### MVP (구글·텔레그램 포함)
Family/Person/UserAccount, 가계부(계좌·이체), 일정(구글 양방향 포함), 사진 포함 일상기록, 통합 타임라인, 텔레그램 입력+영수증 추출

**구현 순서 (리스크 낮은 순)**
1. 코어 (인물·가계부·일정·일상기록 직접입력·타임라인)
2. 텔레그램 입력 (Hermes 확장)
3. 구글 캘린더 양방향 동기화 (가장 까다로움, 마지막)

### V1.5
- 통합 검색 (일정/가계부/기록/사진 횡단)

### V2
- AI 주간/월간 가족 리포트, 육아 마일스톤

### 제외
- 감정/위치 기록, RSVP, 푸시 알림, 풀 감사로그, 풀 ACL(MVP)

---

## 9. 확장 전략 (멀티가족 B / 클라우드)

| 단계 | 환경 | 내용 |
|------|------|------|
| 1. MVP | 로컬 | 우리 가족만. 단 family_id 멀티테넌트 + StorageService + Docker = Cloud-Portable |
| 2. 동생네 오픈 | 로컬/클라우드 | Family 격리 검증, 백업·보안·인증 강화 |
| 3. 정식 수익화 B | **클라우드** | 객체스토리지 교체, 관리형 DB + 결제·약관·컴플라이언스(별도 사업영역) |

> 정식 B는 "기능"이 아니라 "사업" 영역(타인 금융·사진 데이터 운영 책임). 가능성 낮게 보되 문은 닫지 않음. MVP 설계를 복잡하게 하지 않음.

---

## 10. 후속 과제 / 미작성 문서

### 완료 (우선순위 5·6 — 작성됨)
- **외부연동 상세설계 v1**: 구글 양방향(푸시+증분, 410폴백, 충돌 LWW, 단계적 Phase 1~3), 텔레그램/Hermes
- **인프라/배포 v1**: Docker Compose, StorageService(상대키), Cloudflare 터널, **DB 매일+사진 2중화 백업 자동화**

### 미작성 (구현 직전)
- 백엔드 레이어/패키지 구조 (Gradle 모듈, Controller/Service/Repository, 공통 예외)

### 열린 항목 (구현 단계 결정)
- deleted_by 보조 처리(AOP) — `@SoftDelete`가 deleted_at만 관리
- MyBatis family_id 자동주입 Interceptor
- 디자인 시스템 선택, 위젯 네이티브 연동 공수
- 사진 업로드 경로(서버 경유 vs 직접), RefreshToken 저장소(DB/Redis)

> createdBy NULL 시점은 **해결됨**(created_by/updated_by/deleted_by nullable 확정, 시스템 시딩 NULL) — 03-5·04 DDL 참조

---

## 부록 A: 상세 문서 인덱스

> 문서는 번호 순서대로 읽으면 됨 (설계 흐름 순). 버전은 각 문서 내부에 기재.

| # | 문서 | 파일 |
|---|------|------|
| 00 | (본 문서) 통합 마스터 | 00-master.md |
| 01 | 기능 명세 | 01-functional-spec.md |
| 02 | DB 설계 | 02-db-design.md |
| 03 | JPA Entity 설계 | 03-jpa-entity.md |
| 04 | 초기 DDL (검증완료) | 04-V1__init.sql |
| 05 | REST API 명세 | 05-api-spec.md |
| 06 | 인증·인가 설계 | 06-auth-design.md |
| 07 | 프론트엔드 설계 | 07-frontend-design.md |
| 08 | 외부연동 상세설계 | 08-integration-design.md |
| 09 | 인프라·배포 설계 | 09-infra-design.md |

> 구버전(기능명세 v2/v3, DB설계 v1)은 별도 보관(설계 변천 기록용).
