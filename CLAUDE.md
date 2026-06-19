# CLAUDE.md — Family OS (가족 생활기록 시스템)

> 이 파일은 Claude Code가 **모든 작업 전에 반드시 읽어야 하는** 최상위 규칙이다.
> 백엔드 세부 규칙은 `backend/CLAUDE.md`, 프론트 세부는 `frontend/CLAUDE.md` 참조.
> 설계의 단일 진실 원천(SSOT)은 `docs/` 의 설계 문서 9개다. 충돌 시 항상 설계 문서가 우선한다.

---

## 0. 이 프로젝트가 무엇인가

부부(자녀 전제 설계)가 함께 쓰는 **가계부 + 일상기록 + 일정관리 통합** 시스템.
세 기능을 **날짜축으로 통합한 타임라인**이 핵심 차별점. 로컬(Mac Mini) 우선, 클라우드 이식 가능하게 구축.

- **백엔드**: Spring Boot 4.1 + Java 21 + JPA(+MyBatis 혼용) + MySQL 8.x + Flyway + Spring Security 7(JWT)
- **프론트**: React Native + Expo + TypeScript (웹=react-native-web) + gluestack-ui(NativeWind)
- **인프라**: Docker Compose + Cloudflare Tunnel + 외장 SSD

설계 의도: **구조는 미래 확장(멀티가족·ACL·정산)에 열어두되, MVP는 단순하게.** 과설계 금지.

---

## 1. ⛔ 절대 위반 금지 규칙 (NON-NEGOTIABLE)

이 7가지는 한 번이라도 어기면 데이터 유출·손실·정합성 붕괴로 직결된다.
코드를 생성·수정할 때마다 **이 목록을 체크리스트처럼 확인**할 것.

### 1-1. 물리 삭제 금지 (Soft Delete 전용)
- `DELETE FROM` SQL, `repository.delete()`로 행을 **물리 삭제하지 않는다.**
- 모든 삭제는 `deleted_at`을 채우는 soft delete. JPA는 Hibernate 7 `@SoftDelete(columnName="deleted_at", strategy=TIMESTAMP)`로 처리.
- `deleted_by`는 `@SoftDelete`가 자동 관리하지 않으므로 **서비스 계층에서 직접 set 후** soft-delete 호출.
- 예외: `refresh_token` 테이블만 물리 관리(`revoked` 플래그). BaseEntity 비상속.

### 1-2. family_id 멀티테넌트 경계 — 절대 누락 금지
- 모든 `FamilyScopedEntity` 조회/쓰기는 **반드시 family_id로 격리**된다.
- **JPA**: Hibernate `@Filter("familyFilter")`가 요청 단위로 자동 적용 → 보통 신경 안 써도 됨.
- **★ MyBatis (함정)**: `@Filter`가 **적용 안 됨.** MyBatis 매퍼는 **모든 쿼리 WHERE에 `family_id = #{familyId}` 와 `deleted_at IS NULL` 을 직접 명시**해야 한다. 빠뜨리면 타 가족 데이터가 새어나간다.
- family_id는 **JWT 토큰 클레임에서만 추출**. 요청 본문/쿼리/헤더로 받지 않는다(위변조 방지).

### 1-3. UNIQUE 제약은 alive_uk 생성컬럼 방식 (DDL이 SSOT)
- soft delete 환경에서 `(col, deleted_at)` 복합 유니크는 **MySQL NULL 처리 때문에 작동 안 한다.** 절대 그렇게 만들지 말 것.
- 정답: `alive_uk BIGINT AS (IF(deleted_at IS NULL, 0, TIMESTAMPDIFF(MICROSECOND,'1970-01-01',deleted_at))) STORED` + `UNIQUE(col..., alive_uk)`.
- **유니크/제약은 Flyway DDL(`db/migration/V1__init.sql`)에서만 정의한다.** JPA `@Table(uniqueConstraints=...)`로 중복 정의하지 말 것 (alive_uk는 JPA가 모르는 DB 전용 컬럼).

### 1-4. FK는 alive 상태를 막지 못한다 → 서비스에서 alive 검증
- FK는 "물리 행 존재"만 보장한다. **soft-delete된 부모(category·account·tag·person·collection)를 신규 자식이 참조하는 것을 FK는 못 막는다.**
- 신규 생성/수정 시 참조 대상이 **alive(`deleted_at IS NULL`)인지 서비스 계층에서 검증**하고, 위반 시 422 반환.
  - Transaction → category_id, source/target_account_id, subject_person_id, collection_id
  - Diary/Schedule → subject person, collection_id / Tag 연결 → tag_id
- 과거 데이터의 삭제된 참조는 **유지**(이력 보존)하고 UI에 "(삭제됨)" 표기.

### 1-5. visibility 권한 판정 — 2단계 방어, 메모리 필터링 금지
- **목록 조회**: DB 쿼리 WHERE에서 visibility 필터링. **앱 메모리에서 거르는 것 금지**(성능·누락 위험).
- **단건 조회/수정**: 서비스 계층 `VisibilityGuard`로 검증. 위반 시 **404로 숨김**(403 아님 — 존재 자체를 숨긴다).
- 타 가족 자원 접근도 **404**로 응답.
- 판정 규칙: PRIVATE(author만) / SHARED_PERSONAL(author+subject) / PARENTS(family의 PARENT 전원) / FAMILY(family 전원).

### 1-6. 시각/날짜 컬럼 이원화
- 시점 값(거래시각, 작성시각, 일정 시각)은 **UTC `Instant`** 저장.
- 날짜만 의미있는 값은 **별도 DATE 컬럼**: 종일일정 `start_date/end_date`, 기록 `recorded_on`. UTC 변환 시 날짜 밀림 방지용이므로 **Instant로 대체하지 말 것.**
- 일정은 `is_all_day=true`면 DATE 컬럼만, `false`면 Instant 컬럼만 채운다(둘 중 하나만).

### 1-7. 보안 입력은 절대 코드/로그에 남기지 않는다
- JWT 시크릿, 구글 OAuth refresh token, DB 비밀번호 등은 **환경변수(.env)로 외부화.** 하드코딩 금지, git 커밋 금지.
- `refresh_token_enc`는 암호화 저장, 로그 출력 금지. 비밀번호는 BCrypt 해시만 저장.

---

## 2. 구현 순서 (이 순서를 지킬 것 — fail-safe)

리스크 낮은 순. 중간에 막혀도 그 앞까지 동작하도록.

1. **코어** — 인물(Family/Person/UserAccount) → 인증(JWT) → 가계부 → 일정 → 일상기록(사진) → 통합 타임라인
2. **텔레그램** 입력 (Hermes 확장)
3. **구글 캘린더** 양방향 동기화 (가장 까다로움 — Phase 1 읽기 → Phase 2 쓰기 → Phase 3 양방향, 단계적)

> 코어를 끝내기 전에 구글 동기화에 손대지 말 것. 외부연동은 코어가 안정된 뒤.

MVP 제외(만들지 말 것): 감정/위치 기록, RSVP, 푸시 알림, 풀 감사로그, 풀 ACL 테이블, 위젯(차기), 오프라인 입력 큐잉(차기).

---

## 3. 작업 규칙 (Claude Code 행동 지침)

- **설계 문서를 먼저 읽는다.** 작업과 관련된 `docs/` 문서를 확인하고, 거기 규칙을 따른다. 임의 판단으로 설계를 바꾸지 않는다.
- **불명확하면 임의로 정하지 말고 질문한다.** 특히 권한·삭제·정산·동기화 규칙은 추측 금지.
- **DDL을 직접 고치지 않는다.** 스키마 변경이 필요하면 새 Flyway 마이그레이션(`V2__`, `V3__`...)을 추가한다. `V1__init.sql`은 운영 적용 후 수정 금지.
- **새 도메인을 추가할 때는 `backend/CLAUDE.md`의 "골든 패스" 순서를 따른다.** 기존 도메인(예: Transaction)의 패키지/네이밍 패턴을 그대로 모방한다.
- **테스트**: 권한 판정(visibility), family 격리, alive 검증, 거래유형-계좌 규칙은 반드시 테스트를 동반한다.
- 한 번에 한 도메인씩. 거대한 PR보다 작은 단위로 검증하며 진행.
- 커밋 메시지·주석·문서는 한국어 OK, 코드 식별자는 영어.

---

## 4. 디렉토리 구조 (모노레포)

```
family-os/
├── CLAUDE.md                 ← (이 파일) 전역 규칙
├── docs/                     ← 설계 문서 SSOT (00~09)
├── backend/
│   ├── CLAUDE.md             ← 백엔드 패키지 구조·골든 패스·횡단 관심사 위치
│   └── src/main/
│       ├── java/com/familyos/
│       └── resources/
│           ├── db/migration/ ← Flyway (V1__init.sql = DDL SSOT)
│           └── mapper/       ← MyBatis XML (타임라인·통계·검색)
└── frontend/
    ├── CLAUDE.md             ← 프론트 구조·UI variant·날짜/토큰 규칙
    └── src/
```

세부 구조는 각 하위 CLAUDE.md 참조.

---

## 5. 설계 문서 인덱스 (docs/)

| # | 파일 | 내용 | 언제 읽나 |
|---|------|------|-----------|
| 00 | `00-master.md` | 전체 통합·요약 | 항상 먼저 |
| 01 | `01-functional-spec.md` | 기능 명세 | 기능 의미가 헷갈릴 때 |
| 02 | `02-db-design.md` | DB 설계 | 스키마 작업 시 |
| 03 | `03-jpa-entity.md` | JPA 엔티티 매핑·ORM 전략 | 엔티티 작성 시 |
| 04 | `04-V1__init.sql` | **DDL SSOT** (21테이블) | 스키마 진실 확인 |
| 05 | `05-api-spec.md` | REST API 명세 | API 구현 시 |
| 06 | `06-auth-design.md` | 인증(JWT)·인가(visibility) | 보안 작업 시 |
| 07 | `07-frontend-design.md` | 프론트 화면·구조 | 프론트 작업 시 |
| 08 | `08-integration-design.md` | 구글·텔레그램 연동 | 2·3단계 |
| 09 | `09-infra-design.md` | Docker·Cloudflare·백업 | 배포 작업 시 |

> 설계 문서와 이 CLAUDE.md가 충돌하면 **설계 문서가 우선**이며, 충돌을 발견하면 사용자에게 알린다.

---

## 6. 자주 틀리는 함정 모음 (빠른 참조)

| 함정 | 올바른 처리 |
|------|-------------|
| MyBatis 쿼리에 family_id/deleted_at 누락 | 모든 MyBatis WHERE에 직접 명시 |
| `(col, deleted_at)` 유니크 | alive_uk 생성컬럼 사용 |
| soft-delete된 부모 참조 허용 | 서비스에서 alive 검증(422) |
| 권한 위반에 403 응답 | 404로 숨김 |
| 메모리에서 visibility 필터 | DB WHERE에서 필터 |
| 종일일정/기록 날짜를 Instant로 | DATE 컬럼 별도 사용 |
| settlement_status를 클라가 결정 | 서버가 규칙으로 결정(공동→PENDING, 개인→NONE) |
| 거래유형-계좌 규칙 무시 | EXPENSE=source만/INCOME=target만/TRANSFER=둘 다 |
| TRANSFER를 통계에 포함 | 통계에서 TRANSFER 제외 |
| JPA로 통계·타임라인 복잡쿼리 | MyBatis로 구현 |
