# 가족 생활기록 시스템 — REST API 명세 (v1)

> 기준: 기능명세 v4, DB설계 v3, JPA Entity v1
> 스타일: REST / 인증: JWT(Access+Refresh) / family는 로그인 시 자동 결정(토큰에 포함)
> 범위: MVP 코어(인증·가계부·일정·일상기록·타임라인) 중심, 외부연동은 골격만

---

## 0. 공통 규약

### Base
- Base URL: `https://api.saristock.com` (예시, Cloudflare 경유)
- 버전: 경로 프리픽스 `/api/v1`
- 포맷: `application/json`, UTF-8
- 시각: 요청/응답 모두 **ISO-8601 UTC** (예: `2026-06-19T08:30:00Z`). 표시 변환은 클라이언트
- ID: 숫자(Long)

### 인증 헤더
```
Authorization: Bearer {accessToken}
```
- 모든 보호 엔드포인트 필수 (인증/회원 관련 일부 제외)
- **family_id는 토큰 클레임에서 추출** — 요청 본문/헤더로 받지 않음 (위변조 방지)

### 공통 응답 래퍼
```json
{ "success": true, "data": { ... }, "error": null }
{ "success": false, "data": null,
  "error": { "code": "FORBIDDEN_VISIBILITY", "message": "...", "detail": null } }
```

### 페이지네이션 (목록 공통)
- 쿼리: `?page=0&size=20&sort=occurredAt,desc`
- 응답 data: `{ "content": [...], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`

### 표준 에러 코드 (HTTP)
| HTTP | code | 의미 |
|------|------|------|
| 400 | VALIDATION_ERROR | 입력 검증 실패 |
| 401 | UNAUTHENTICATED | 토큰 없음/만료 |
| 403 | FORBIDDEN_VISIBILITY | visibility/소속 권한 없음 |
| 404 | NOT_FOUND | 리소스 없음(또는 타 가족 자원) |
| 409 | CONFLICT | 유니크 충돌 등 |
| 422 | BUSINESS_RULE | 비즈니스 규칙 위반(예: 이체 계좌 누락) |

> 타 가족 자원 접근은 정보 노출 방지 위해 403이 아닌 **404**로 응답(존재 자체를 숨김).

---

## 1. 인증 / 인가

### 1-1. 인증 흐름 개요
- 로그인 → AccessToken(단기, 15~30분) + RefreshToken(장기, 14일) 발급
- AccessToken 클레임: `sub`(personId), `accountId`, `familyId`, `role`(PARENT/CHILD), `exp`
- **familyId 자동 결정**: 로그인 시 사용자의 FamilyMembership에서 결정.
  멤버십이 1개면 자동, 여러 개(미래 B)면 기본 가족 또는 가족 선택 단계
- RefreshToken은 서버 저장(회전식, 재사용 감지 시 폐기) — 보안

### 1-2. 엔드포인트

#### POST /api/v1/auth/login
요청
```json
{ "loginId": "sari", "password": "••••••" }
```
응답
```json
{ "success": true, "data": {
  "accessToken": "eyJ...", "refreshToken": "eyJ...",
  "tokenType": "Bearer", "expiresIn": 1800,
  "person": { "id": 1, "name": "사리", "familyId": 1, "role": "PARENT" }
}}
```

#### POST /api/v1/auth/refresh
요청 `{ "refreshToken": "eyJ..." }` → 새 Access(+회전된 Refresh)

#### POST /api/v1/auth/logout
- RefreshToken 무효화. AccessToken 헤더 필요

#### GET /api/v1/auth/me
- 현재 토큰 기준 본인 정보(person, family, role)

#### PUT /api/v1/auth/password (2026-07 리뉴얼 추가)
- 요청: `{ "currentPassword": "...", "newPassword": "..." }` (newPassword 8자 이상)
- 현재 비밀번호 불일치 시 422(`BUSINESS_RULE`, "현재 비밀번호가 일치하지 않습니다.")
- 성공 시 이 계정의 **모든 refresh token 폐기**(현재 기기 포함) — 새 비밀번호로 다시 로그인해야 함. 더보기 메뉴에서 진입.

> 회원가입/계정 생성은 가족 관리(부모가 자녀 계정 생성)와 얽혀 별도 — 4장 참조.

### 1-3. 권한 판정 규칙 (visibility) — 모든 도메인 공통

조회 시 "이 자원을 현재 사용자가 볼 수 있는가" 판정:

| visibility | 볼 수 있는 사람 |
|------------|----------------|
| PRIVATE | author(created_by) 본인만 |
| SHARED_PERSONAL | author + subject로 지정된 사람 (일정·일상기록) |
| PARENTS | 같은 family의 role=PARENT 전원 |
| FAMILY | 같은 family 전원 |

- 모든 조회는 `familyId(토큰)` 범위 + 위 visibility 필터를 **서버에서** 적용
- 수정/삭제는 기본적으로 author만(또는 PARENT 관리 권한) — 도메인별 명시
- SHARED_PERSONAL 표시수준(상세/요약)은 PersonSetting 따라 응답 가공

---

## 2. 가계부 (Transaction / Account / Category)

### 2-1. Account

#### GET /api/v1/accounts
- 본인이 볼 수 있는 계좌 목록(개인 소유 + 공용)
- 응답 item: `{ id, name, assetType, ownerType, ownerPersonId, visibility }`

#### POST /api/v1/accounts
```json
{ "name": "신한카드", "assetType": "BANK",
  "ownerType": "PERSON", "ownerPersonId": 1, "visibility": "PRIVATE" }
```
- ownerType=FAMILY면 ownerPersonId 생략(null)

#### PUT /api/v1/accounts/{id}  ·  DELETE /api/v1/accounts/{id} (soft)
- 계좌 soft-delete 시 과거 거래의 source/target_account_id는 **유지**(FK 보존)
- 통계·타임라인은 삭제된 계좌의 과거 거래도 정상 집계, 계좌명은 "(삭제됨)" 표기
- 삭제된 계좌는 신규 거래 입력 선택지에서만 제외

### 2-2. Category

#### GET /api/v1/categories?type=EXPENSE
- 가족 카테고리(기본 시딩 + 커스텀). `isSystem` 포함
#### POST /api/v1/categories  ·  PUT  ·  DELETE
- isSystem=true는 삭제 불가(422)

### 2-3. Transaction

#### GET /api/v1/transactions
쿼리: `?from=2026-06-01T00:00:00Z&to=2026-06-30T23:59:59Z&type=EXPENSE&categoryId=3&scope=ALL&collectionId=7&page=0&size=20`
- `scope`: ALL | PRIVATE | PARENTS (보기 필터, 권한 범위 내)
- `collectionId`(2026-07 리뉴얼 추가): 지정 시 해당 묶음에 연결된 거래만(날짜 범위와 무관하게 전체 기간) 반환 — 묶음 요약 화면에서 사용
- 응답: 페이지네이션. item에 결제계좌는 **본인 것만 노출**, 타인 공유거래는 계좌 마스킹
```json
{ "id": 10, "transactionType": "EXPENSE", "amount": 50000, "currency": "KRW",
  "category": { "id": 3, "name": "식비" },
  "sourceAccount": { "id": 5, "name": "신한카드" },   // 타인 거래면 null/마스킹
  "subjectPersonId": 1, "visibility": "PARENTS",
  "settlementStatus": "PENDING", "occurredAt": "2026-06-19T08:30:00Z",
  "memo": "마트", "collectionId": null, "tags": ["#장보기"] }
```

#### POST /api/v1/transactions
```json
{ "transactionType": "EXPENSE", "amount": 50000, "currency": "KRW",
  "sourceAccountId": 5, "targetAccountId": null,
  "categoryId": 3, "subjectPersonId": 1,
  "visibility": "PARENTS", "occurredAt": "2026-06-19T08:30:00Z",
  "memo": "마트", "collectionId": null, "tagIds": [7],
  "source": "MANUAL" }
```
검증(422):
- EXPENSE: sourceAccountId 필수, targetAccountId 금지
- INCOME: targetAccountId 필수, sourceAccountId 금지
- TRANSFER: source·target 모두 필수, 서로 달라야 함
- settlementStatus는 서버가 규칙으로 결정(공동→PENDING, 개인→NONE). 클라 전송 무시

#### POST /api/v1/transactions/link-collection (2026-07 리뉴얼 추가)
가계부 다중선택 → 묶음 일괄연결. 다른 필드는 건드리지 않음(서버가 로드한 엔티티에 collectionId만 변경).
```json
{ "transactionIds": [10, 11, 12], "collectionId": 3 }
```
- `collectionId`는 null 허용(연결 해제 용도로도 재사용). 각 거래는 작성자 본인만(VisibilityGuard, 위반 시 해당 id에서 404).
- 응답: `{ "success": true, "data": 3 }` (실제 갱신된 건수)

#### PUT /api/v1/transactions/{id}  ·  DELETE (soft)
- author 본인만 수정/삭제

#### GET /api/v1/transactions/statistics
쿼리: `?from=...&to=...&groupBy=category&scope=PARENTS`
- 집계(MyBatis): 카테고리별/월별 합계. **TRANSFER 제외**
```json
{ "totalIncome": 0, "totalExpense": 520000,
  "byCategory": [ { "categoryId": 3, "name": "식비", "amount": 167000 } ],
  "byMonth": [ { "month": "2026-06", "income": 0, "expense": 520000 } ] }
```

---

## 3. 일정 (Schedule)

#### GET /api/v1/schedules
쿼리: `?from=...&to=...&type=EVENT&scope=ALL&q=검색어`
- 권한 범위 내 일정. SHARED_PERSONAL 타인 일정은 PersonSetting=SUMMARY면 title 일부/시간만
- `q`(2026-07 리뉴얼 추가): title/description/location 부분일치(대소문자 무시). from/to 없이 `q`만 보내면 전체기간 검색(일정 화면 검색 UI에서 사용).
- 응답 item: `{ id, title, location, startedAt, endedAt, startDate, endDate, allDay, visibility, scheduleType, isDone, recurrenceRule, subjects:[personId], participants:[personId], collectionId, syncStatus }`
  - allDay=true → startDate/endDate 사용(startedAt/endedAt null), allDay=false → 반대

#### POST /api/v1/schedules
```json
{ "title": "부산 출장", "description": null, "location": "부산역",
  "startedAt": "2026-07-01T00:00:00Z", "endedAt": "2026-07-02T09:00:00Z",
  "allDay": false, "visibility": "SHARED_PERSONAL", "scheduleType": "EVENT",
  "recurrenceRule": null, "subjectPersonIds": [1], "participantPersonIds": [1],
  "collectionId": null }
```
> 종일 일정은 `"allDay": true, "startDate": "2026-07-01", "endDate": "2026-07-01"` (startedAt/endedAt 생략).
> 시점 일정은 startedAt/endedAt(UTC) 사용. 둘 중 하나만 채움 — 날짜 밀림 방지
#### PUT /api/v1/schedules/{id}  ·  DELETE (soft)
#### PATCH /api/v1/schedules/{id}/done  (TODO 완료 토글)

> google 동기화 필드(googleEventId/syncStatus)는 서버·동기화 모듈이 관리, 클라 직접 수정 불가

> **API ↔ 엔티티 필드명 규칙**: JSON은 camelCase, boolean은 `isXxx` 형태로 노출.
> 엔티티 `done`(boolean) → API `isDone`, `allDay` → `allDay`. 프론트 타입(07 /domain)은 API 표기 기준.
> 직렬화 규칙을 ObjectMapper에서 일관 적용해 엔티티 필드명과 API 표기 차이를 흡수.

---

## 4. 가족 / 구성원 / 계정 관리

#### GET /api/v1/family
- 현재 가족 정보 + 구성원(Person) 목록 `{ family, members:[{personId, name, role, hasAccount}] }`

#### POST /api/v1/family/members  (PARENT만)
- 자녀 등 Person 추가(계정 없이). `{ "name": "첫째", "role": "CHILD", "birthDate": "2027-01-01" }`

#### POST /api/v1/family/members/{personId}/account  (PARENT만)
- 기존 Person에 로그인 계정 부여(자녀 성장 시). `{ "loginId": "...", "password": "..." }`
- 데이터 이관 없음 — UserAccount row만 추가

#### GET /api/v1/me/settings  ·  PUT /api/v1/me/settings
- PersonSetting: `{ "sharedScheduleDetailLevel": "SUMMARY", "ledgerDefaultView": "CALENDAR", "scheduleDefaultView": "CALENDAR", "diaryDefaultView": "CALENDAR" }`
- `ledgerDefaultView`/`scheduleDefaultView`/`diaryDefaultView`: `INLINE | CALENDAR` — 가계부/일정/기록 화면을 열었을 때 기본으로 보여줄 뷰(화면 내에서는 언제든 전환 가능, 여기 값은 "기본값"만). 2026-07 리뉴얼에서 추가(V2), 기본값은 CALENDAR(월간)로 통일(V3).
- PUT은 4개 필드 전체를 보낸다(부분 업데이트 아님) — 클라이언트가 현재 GET 응답에 변경분만 덮어써 전송.

---

## 5. 일상기록 (Diary / Photo)

#### GET /api/v1/diaries?from=...&to=...&view=feed&scope=ALL&collectionId=3&q=검색어
- view: feed | calendar | album (응답 형태 동일, 클라가 렌더 구분)
- `collectionId`(2026-07 리뉴얼 추가): 지정 시 해당 묶음에 연결된 기록만(날짜 범위와 무관하게 전체 기간) 반환 — 기록 화면의 "묶음 선택" 진입점에서 사용.
- `q`(2026-07 리뉴얼 추가): title/content/연결된 tag명 부분일치(대소문자 무시). from/to 없이 `q`만 보내면 전체기간 검색(기록 화면 검색 UI에서 사용).
- item: `{ id, title, content, visibility, recordedAt, recordedOn, subjects:[personId], collectionId, tags:[], photos:[{id, storageKey→signedUrl, width, height, takenAt}] }`
  - recordedOn(날짜)이 타임라인 정렬 기준, recordedAt은 작성 시각

#### POST /api/v1/diaries
```json
{ "title": "주말", "content": "...", "visibility": "FAMILY",
  "recordedOn": "2026-06-15", "recordedAt": "2026-06-15T09:00:00Z",
  "subjectPersonIds": [1,2,3], "collectionId": null, "tagIds": [] }
```
> recordedOn(날짜)이 필수 — 타임라인 날짜축. recordedAt(시각)은 선택(없으면 서버가 작성시각)
#### PUT /api/v1/diaries/{id}  ·  DELETE (soft)

### 5-1. Photo (업로드 2단계 — 직접 업로드 방식)

#### POST /api/v1/photos/presign
- 업로드 의도 등록 → 서버가 storage_key 발급 + 업로드 URL/방식 반환
```json
{ "originalFilename": "IMG_0234.JPG", "mimeType": "image/jpeg", "fileSize": 2400000,
  "photoHash": "ab12...", "diaryId": 10, "takenAt": "2026-06-15T10:00:00Z" }
```
응답: `{ "photoId": 55, "uploadUrl": "...", "storageKey": "family_1/2026/06/uuid.jpg" }`
- photoHash 중복 시 기존 photo 반환(중복 업로드 방지)

#### PUT {uploadUrl}  (실제 바이너리 업로드 — 서버 또는 스토리지 직접)
#### POST /api/v1/photos/{photoId}/complete  (업로드 확정, width/height 등 메타 확정)
#### GET /api/v1/photos/{photoId}  (서명된 조회 URL 반환)
#### DELETE /api/v1/photos/{photoId} (soft)

> 외장 SSD StorageService 추상화 — presign/complete는 로컬 구현. 향후 R2면 동일 인터페이스로 교체

---

## 6. 이벤트 묶음 (Collection)

#### GET /api/v1/collections  ·  POST  ·  PUT  ·  DELETE
- POST/PUT: `{ "name": "2026 여름휴가", "description": null, "startedAt": ..., "endedAt": ..., "tagIds": [5] }`
- 응답에 `tags: string[]` 포함(2026-07 리뉴얼 추가 — `collection_tag`, `tag` 도메인 재사용). 태그로 검색하면 그 묶음에 연결된 일정·가계부·기록까지 요약 화면에서 확인 가능.
- 묶음 생성은 일정(Schedule) 작성 화면을 베이스로 함 — 그 일정의 날짜가 묶음 `startedAt/endedAt`에도 반영됨.

#### GET /api/v1/collections/{id}/summary
- 묶음 리뷰: `{ collection, totalExpense, transactionCount, photoCount, diaryCount, scheduleCount }`

#### PATCH /api/v1/{transactions|schedules|diaries|photos}/{id}/collection
- 항목을 묶음에 연결/해제: `{ "collectionId": 3 }` (방식 A — collection_id 변경)
- 가계부는 다중선택 일괄연결 전용 엔드포인트(`POST /transactions/link-collection`, §2-3) 사용.

#### GET /api/v1/transactions?collectionId=3 (2026-07 리뉴얼 추가)
- 지정 시 해당 묶음에 연결된 거래만(날짜 범위와 무관) 반환 — 묶음 요약 화면의 "연결된 거래 목록"에서 사용.

---

## 7. 통합 타임라인 (핵심)

#### GET /api/v1/timeline?date=2026-06-19  (단일 날짜)
#### GET /api/v1/timeline?from=2026-06-01&to=2026-06-30  (기간)
- MyBatis로 transaction/schedule/diary/photo를 날짜축 병합(권한 필터 적용)
- 응답: 날짜별 그룹, 각 항목에 type 태그
```json
{ "days": [ { "date": "2026-06-19", "items": [
    { "type": "SCHEDULE", "id": 3, "time": "09:00", "title": "예방접종", "...": "..." },
    { "type": "TRANSACTION", "id": 10, "time": "11:30", "amount": 35000, "...": "..." },
    { "type": "PHOTO", "id": 55, "time": "13:00", "thumbnailUrl": "...", "...": "..." },
    { "type": "DIARY", "id": 7, "time": "18:00", "title": "...", "...": "..." }
] } ] }
```
- 정렬: 같은 날짜 그룹 내에서 시각(UTC) 기준. 사진은 takenAt 우선
  - **종일 일정·DATE 기록(시각 없음)**: 해당 날짜 그룹의 **맨 위**에 배치(시각 항목보다 앞). 그룹 내 부 정렬은 type 우선순위(일정→기록→거래→사진)
  - 시각 있는 항목은 그 아래 시각순
- 날짜 그룹 키: 일정=start_date 또는 started_at의 로컬 날짜, 기록=recorded_on, 거래=occurred_at 로컬 날짜, 사진=taken_at 로컬 날짜
- visibility 필터 + familyId 범위 서버 적용

---

## 8. 외부 연동 (골격 — 상세는 별도 설계 문서)

### 8-1. 구글 캘린더 (V1 단계)
- `POST /api/v1/integrations/google/connect` — OAuth 연결 시작
- `GET /api/v1/integrations/google/callback` — 콜백
- `POST /api/v1/integrations/google/sync` — 수동 동기화 트리거
- 양방향 충돌·증분 동기화는 **별도 상세설계 문서 필요**(Open)

### 8-2. 텔레그램 / Hermes (V1 단계)
- 입력은 Hermes(봇)가 내부 API 호출 형태 → `POST /api/v1/transactions` (source=TELEGRAM)
- 영수증 이미지: Hermes가 비전 추출 → 확인 게이트 후 등록
- 조회 질의: Hermes가 통계/타임라인 API 호출

---

## 9. 열린 항목
1. RefreshToken 저장소(서버 DB/Redis) — 회전·재사용 감지 방식 확정
2. 사진 업로드: 서버 경유 vs 스토리지 직접 — 외장 SSD 환경 특성상 서버 경유 유력
3. 구글 양방향 동기화 상세설계(별도 문서)
4. 타임라인 권한 필터의 MyBatis 구현 — family_id+visibility 수동 조건 규칙화
5. Rate limiting / 요청 크기 제한(사진)

---

## 10. 다음 단계
**인증·인가 상세설계** (JWT 구조, Spring Security 필터 체인, family/visibility 권한 판정 구현, RefreshToken 회전) → 백엔드 레이어 구조 → 프론트 기술결정·화면정의
