# 가족 생활기록 시스템 — 외부연동 상세설계 (v1)

> 기준: 기능명세 v4, API명세 v1, DB설계 v3
> 범위: ① 구글 캘린더 양방향 동기화(가장 까다로움) ② 텔레그램/Hermes 연동
> 구현 단계: MVP 구현순서상 **마지막**(코어→텔레그램→구글)

---

## 1. 구글 캘린더 양방향 동기화

### 1-0. 핵심 원칙
- 양방향 = 우리 시스템 ↔ 구글, 양쪽 변경이 서로 반영
- **두 메커니즘 병용 필수**:
  - **푸시 알림(webhook)**: 구글→우리, 거의 즉시 변경 감지. 단 구글 공식상 100% 신뢰 불가
  - **증분 동기화(sync token)**: 신뢰성 있는 변경분 수령. 푸시의 폴백 + 정기 폴링
- 푸시는 "변경이 생겼다"는 신호만, 실제 데이터는 항상 **sync token 증분 호출**로 가져옴

### 1-1. 인증 (OAuth2)
- **개인 구글 계정** OAuth2 (서비스 계정 아님 — 개인 캘린더 접근)
- 스코프: `https://www.googleapis.com/auth/calendar` (읽기·쓰기)
- 토큰 관리:
  - access token 3600초 만료 → **refresh token으로 자동 갱신** (refresh token 영구 저장, 암호화)
  - refresh token은 환경변수 시크릿으로 암호화 저장 (DB)
- 연동 플로우: `/integrations/google/connect` → 구글 동의 → `/callback`에서 토큰 수령·저장

### 1-2. 동기화 상태 저장 (신규 테이블)

#### google_sync_state
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | |
| family_id | BIGINT FK | |
| person_id | BIGINT FK→Person | 어느 구성원의 구글 계정인지 |
| google_calendar_id | VARCHAR(255) | 대상 캘린더(보통 'primary') |
| sync_token | VARCHAR(1024) NULL | 증분 동기화 토큰(persist 필수) |
| channel_id | VARCHAR(255) NULL | 푸시 채널 ID |
| channel_resource_id | VARCHAR(255) NULL | 푸시 리소스 ID |
| channel_expires_at | DATETIME(6) NULL | 채널 만료(갱신 필요) |
| refresh_token_enc | VARBINARY(512) NULL | 암호화된 refresh token. 연동 해제 시 NULL로 무효화 |
| last_full_sync_at | DATETIME(6) NULL | |
| last_incremental_at | DATETIME(6) NULL | |
| (+ BaseEntity) | | |

> Schedule 테이블엔 이미 `google_event_id`, `sync_status`, `last_synced_at` 존재 (DB설계 v3)

### 1-3. 초기 전체 동기화 (1회)
```
1) events.list (syncToken 없이) 호출, 페이지네이션(nextPageToken)
2) 각 구글 이벤트 → Schedule 매핑(아래 1-6), google_event_id 저장
3) 마지막 페이지 응답의 nextSyncToken 저장 → google_sync_state.sync_token
4) events.watch로 푸시 채널 생성 (channel_id/resource_id/expires 저장)
```
- 유입 일정의 visibility 기본값: **PRIVATE**(순수 개인) — 사용자가 우리 앱에서 조정
- 유입 일정 author: 해당 person, source 표시 가능

### 1-4. 증분 동기화 (반복)
```
1) events.list(syncToken=저장된토큰)
2) 응답에 변경/삭제 이벤트만 포함 (status=cancelled → 삭제)
3) 각 변경 → Schedule upsert / soft-delete (google_event_id로 매칭)
4) 새 nextSyncToken 저장
5) [410 GONE 처리] syncToken 만료 시 → sync_token 폐기 + 전체 재동기화(1-3)
```
- **410 Gone 폴백 필수** — try/catch로 감싸고 전체 재동기화 경로 호출
- 트리거: ① 푸시 알림 수신 시 ② 정기 폴링(예: 15분, 푸시 누락 대비)

### 1-5. 푸시 알림 (webhook)
```
- events.watch로 채널 등록 → 구글이 변경 시 우리 수신 URL로 POST
- 수신 URL: POST /api/v1/integrations/google/notifications
- 헤더: X-Goog-Channel-ID, X-Goog-Resource-State, X-Goog-Resource-ID
- 동작: 알림은 "변경 발생" 신호 → 즉시 증분 동기화(1-4) 트리거 (본문에 데이터 없음)
- 채널 만료(channel_expires_at) 전 갱신(재watch) — 스케줄러
- 보안: X-Goog-Channel-Token 검증(우리가 등록한 토큰과 일치)
```
- Cloudflare 경유 공개 수신 URL 필요(Zero Trust 예외 경로 또는 별도 공개 엔드포인트)

### 1-6. 필드 매핑 (Schedule ↔ Google Event)

| our Schedule | Google Event |
|--------------|--------------|
| title | summary |
| description | description |
| location | location |
| started_at / ended_at | start / end (dateTime, UTC) |
| is_all_day | start/end (date) |
| recurrence_rule | recurrence (RRULE) |
| google_event_id | id |

- visibility/subject/participant 등 우리 고유 개념은 구글에 직접 대응 없음
  → 우리→구글 전송 시 손실(구글엔 title/시간 위주). description에 메모로 보존 가능(선택)
- 우리 4단계 visibility는 구글 동기화 대상 여부에만 영향(아래 1-8)

### 1-7. 양방향 충돌 처리
양쪽에서 같은 이벤트를 수정한 경우:

| 전략 | 내용 |
|------|------|
| **Last-write-wins (기본)** | updated 타임스탬프 비교, 최신 변경이 승리 |
| 충돌 표시 | sync_status=CONFLICT로 마킹, 사용자에게 알림(선택) |
| 삭제 우선 | 한쪽 삭제 + 다른쪽 수정 → 삭제 우선(또는 사용자 확인) |

- MVP: **Last-write-wins + CONFLICT 마킹** (자동 해결하되 흔적 남김)
- sync_status: SYNCED(정상) / PENDING(전송 대기) / CONFLICT(충돌) / DELETED_REMOTE(구글서 삭제됨)

### 1-8. 우리→구글 방향 (로컬 변경 전송)
```
- Schedule 생성/수정/삭제 시 → sync_status=PENDING
- 비동기 워커가 PENDING을 구글 API로 전송(events.insert/update/delete)
- 성공 → google_event_id 저장, sync_status=SYNCED
```
- **동기화 대상 scope 정책**: 어떤 visibility를 구글에 올릴지 사용자 선택
  (예: 전체 동기화 / 공동만 / 순수개인 제외 등) — 기본은 전체(순수개인 포함, 사용자 결정)
- **무한 루프 방지**: 구글→우리 반영 시 다시 우리→구글로 안 나가게 sync origin 플래그/타임스탬프 가드

### 1-9. 운영 제약 (구글 API)
- access token 3600초 만료 → refresh 자동 갱신 로직 필수
- sync token 장기 미사용 시 만료(410) → 전체 재동기화 폴백
- Rate limit: 사용자당 분당 600쿼리, 프로젝트당 일 100만 — 가족 규모라 여유, 단 폴링 주기 과하지 않게
- 푸시 채널 만료 → 정기 재등록 스케줄러

### 1-10. 단계적 구현 (리스크 관리)
```
Phase 1: 단방향 읽기 (구글→우리) — 초기 전체 + 증분, 표시만
Phase 2: 단방향 쓰기 (우리→구글) — 로컬 변경 전송
Phase 3: 양방향 + 충돌 처리 + 푸시 알림
```
- 가장 안전한 순서. Phase 1만으로도 "구글 일정이 앱에 보임" 가치 제공

---

## 2. 텔레그램 / Hermes 연동

> 기존 Hermes Agent(텔레그램 봇) 자산 확장. 신규 봇 프로필 또는 기존 확장.

### 2-1. 구조
```
사용자 ──텔레그램──> Hermes(봇) ──내부 API 호출──> Family OS 백엔드
```
- Hermes는 **내부 신뢰 클라이언트**로서 API 호출 (전용 인증: 서비스 토큰 또는 내부망)
- source=TELEGRAM으로 기록 구분

### 2-2. 기능

**① 가계부 입력 (텍스트)**
- "스타벅스 5000원" 형태 → Hermes가 파싱 → POST /transactions (확인 게이트 후)

**② 영수증 이미지 입력**
```
1) 사용자가 영수증 사진 전송
2) Hermes가 멀티모달 LLM(비전)에 이미지 → 금액·상점·날짜 추출(JSON)
3) 추출 결과를 사용자에게 표시 "이대로 등록할까요?" (human-in-the-loop)
4) 확인 → POST /transactions (source=TELEGRAM)
```
- 추출 방식: 멀티모달 LLM(기존 Claude/GPT/Gemini API 자산) — OCR보다 한글 영수증 안정적
- 기본 visibility=PRIVATE(개인), 사용자가 봇에서 공동으로 변경 가능

**③ 조회 질의**
- "이번 달 식비 얼마?" → Hermes가 GET /transactions/statistics 호출 → 응답 포맷팅
- "내일 일정?" → GET /schedules → 포맷팅
- "오늘 타임라인" → GET /timeline

### 2-3. 인증/권한
- Hermes는 어느 Person으로 행동하는지 매핑 필요(텔레그램 user_id ↔ Person)
- 봇 사용자별 매핑 테이블(또는 설정)로 author/family 결정
- 내부 호출도 family_id·visibility 규칙 동일 적용

### 2-4. MVP 위치
- 구현순서 2단계(코어 다음). 기존 Hermes 자산이 있어 구글보다 빠름
- 영수증 이미지 추출은 LLM 파이프라인이라 검증 필요 — 확인 게이트로 안전장치

---

## 3. 신규 엔드포인트 (외부연동)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | /api/v1/integrations/google/connect | OAuth 시작 |
| GET | /api/v1/integrations/google/callback | OAuth 콜백·토큰 저장 |
| POST | /api/v1/integrations/google/sync | 수동 증분 동기화 트리거 |
| POST | /api/v1/integrations/google/notifications | 푸시 webhook 수신(공개) |
| DELETE | /api/v1/integrations/google | 연동 해제(채널 stop, **refresh_token_enc=NULL로 토큰 무효화**, sync_token 폐기) |
| POST | /api/v1/integrations/telegram/transactions | Hermes 거래 등록(내부) |
| GET | /api/v1/integrations/telegram/query | Hermes 조회(내부) |

---

## 4. 신규 테이블 요약 (DB 추가분)
- `google_sync_state` (1-2)
- `telegram_person_map` (텔레그램 user_id ↔ person_id, family_id)
- (Schedule의 google_event_id/sync_status/last_synced_at는 기존)

---

## 5. 열린 항목
1. 동기화 대상 scope 사용자 설정 UI (어떤 visibility를 구글에 올릴지)
2. 푸시 webhook 공개 URL의 Cloudflare 설정(Zero Trust 예외)
3. refresh token 암호화 키 관리(KMS vs 환경변수)
4. 우리 고유 개념(subject/participant)의 구글 description 보존 여부
5. 무한 동기화 루프 가드 상세(origin 플래그 설계)

---

## 6. 다음 단계
**인프라 / 배포 설계** (StorageService 구현, Docker/Cloudflare/Flyway, DB+사진 백업 자동화) — 우선순위 마지막 문서
