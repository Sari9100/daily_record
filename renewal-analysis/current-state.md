# Family OS — 프론트엔드 리뉴얼을 위한 현재 상태 정리

> 작성일: 2026-07-17. 소스 코드 직접 열람으로 조사(백엔드 엔티티/DDL/컨트롤러, 프론트 전 화면·컴포넌트·API 레이어·상태관리).
> 목적: 리뉴얼 착수 전 "무엇이 있고, 어떻게 연결돼 있는지" 기준선을 잡기 위함. 코드/설계 변경 없음 — 순수 조사 문서.

---

## 1. 백엔드 — 도메인 구조 (`backend/src/main/java/com/familyos/`)

| 패키지 | 하위 구조 | 설명 |
|---|---|---|
| `common/` | entity, context, security, tenant, audit, web, error, domain | 횡단 관심사: BaseEntity, FamilyScopedEntity, JWT 필터, 멀티테넌트 필터, 공통 응답/예외 |
| `person/` | entity, repository, service, controller, dto, bootstrap | 인물/가족 (Family, Person, UserAccount, FamilyMembership, PersonSetting) |
| `auth/` | entity, repository, service, controller, dto | 인증 (RefreshToken, 로그인/토큰 회전) |
| `ledger/` | entity, repository, service, controller, dto, mapper | 가계부 (Account, Category, Transaction) + MyBatis 통계 |
| `schedule/` | entity, repository, service, controller, dto | 일정 (Schedule, ScheduleSubject, ScheduleParticipant) |
| `diary/` | entity, repository, service, controller, dto | 일상기록 (Diary, DiarySubject) |
| `photo/` | entity, repository, service, controller, dto | 사진 (Photo) — diary와 별도 패키지 |
| `tag/` | entity, repository, service, controller, dto | 태그 (Tag, DiaryTag, TransactionTag) |
| `collection/` | entity, repository, service, controller, dto, mapper | 이벤트 묶음(Collection) + MyBatis 요약 |
| `timeline/` | controller, service, mapper (엔티티 없음) | MyBatis 전용, 다중 도메인 날짜축 병합 조회 |
| `integration/google/` | client, controller, crypto, dto, entity, oauth, repository, service | 구글 캘린더 연동 (GoogleSyncState, OAuth, Push 채널) |
| `integration/telegram/` | controller, dto, entity, repository, security, service | 텔레그램(Hermes) 연동 (TelegramPersonMap) |
| `storage/` | (엔티티 없음) | StorageService 인터페이스 + LocalSsdStorage 구현, URL 서명 |

> 비고: 루트 CLAUDE.md는 "코어 완료 전 구글/텔레그램 연동 착수 금지"를 규정하지만, 실제로는 두 연동 모두 상당 부분 구현되어 있음.

### 1-1. JPA 엔티티 및 주요 필드

| 도메인 | 엔티티 | 상속 | 주요 필드 |
|---|---|---|---|
| common | `BaseEntity` | MappedSuperclass | id, createdAt/By, updatedAt/By, deletedBy (`deletedAt`은 `@SoftDelete`가 자동 관리) |
| common | `FamilyScopedEntity` | MappedSuperclass | familyId (+ `@Filter familyFilter`) |
| person | `Family` | BaseEntity | name |
| person | `Person` | BaseEntity | name, birthDate, timezone(기본 Asia/Seoul) |
| person | `UserAccount` | BaseEntity | person(1:1), loginId, passwordHash, status(ACTIVE/DISABLED), lastLoginAt |
| person | `FamilyMembership` | BaseEntity | family, person, role(PARENT/CHILD), joinedAt |
| person | `PersonSetting` | BaseEntity | personId, sharedScheduleDetailLevel(FULL/SUMMARY) |
| auth | `RefreshToken` | 독립(BaseEntity 미상속) | accountId, jti, expiresAt, revoked, createdAt |
| ledger | `Account` | FamilyScopedEntity, Visible | name, assetType(BANK/SECURITIES/CASH), ownerType(PERSON/FAMILY), ownerPersonId, visibility |
| ledger | `Category` | FamilyScopedEntity | name, type(INCOME/EXPENSE), parentId, isSystem |
| ledger | `Transaction` | FamilyScopedEntity, Visible | transactionType(INCOME/EXPENSE/TRANSFER), amount, currency, sourceAccountId, targetAccountId, categoryId, subjectPersonId, visibility, settlementStatus(NONE/PENDING/SETTLED), occurredAt, memo, collectionId, source(MANUAL/TEXT/TELEGRAM) |
| schedule | `Schedule` | FamilyScopedEntity | title, description, location, startedAt/endedAt, startDate/endDate, allDay, visibility, scheduleType(EVENT/TODO/REMINDER), done, recurrenceRule, collectionId, googleEventId, syncStatus, lastSyncedAt |
| schedule | `ScheduleSubject` / `ScheduleParticipant` | FamilyScopedEntity | scheduleId, personId (대상 vs 실제 참석자, 둘 다 다중) |
| diary | `Diary` | FamilyScopedEntity | title, content, visibility, recordedAt(Instant), recordedOn(LocalDate), collectionId |
| diary | `DiarySubject` | FamilyScopedEntity | diaryId, personId (다중) |
| photo | `Photo` | FamilyScopedEntity | diaryId, storageKey, originalFilename, photoHash(SHA-256), width, height, takenAt, fileSize, mimeType, collectionId |
| tag | `Tag` / `DiaryTag` / `TransactionTag` | FamilyScopedEntity | name / diaryId+tagId / transactionId+tagId |
| collection | `Collection` | FamilyScopedEntity | name, description, coverPhotoId, startedAt, endedAt |
| integration/google | `GoogleSyncState` | FamilyScopedEntity | personId, googleCalendarId, syncToken, channelId/channelResourceId/channelExpiresAt, refreshTokenEnc(암호화), lastFullSyncAt, lastIncrementalAt |
| integration/telegram | `TelegramPersonMap` | FamilyScopedEntity | personId, telegramUserId |

순수 Enum(엔티티 아님): `AccountOwnerType`, `AssetType`, `CategoryType`, `SettlementStatus`, `TransactionSource`, `TransactionType`, `ScheduleType`, `SyncStatus`, `AccountStatus`, `DetailLevel`.

---

## 2. 백엔드 — DB 스키마 (`db/migration/V1__init.sql`, 21 테이블)

V2 이후 마이그레이션 없음 — 스키마는 최초 설계 이후 변경 이력이 없다.

| # | 테이블 | 섹션 | 비고 |
|---|---|---|---|
| 1 | `family` | 인물/가족 | family_id 없음(자기 자신이 최상위 경계) |
| 2 | `person` | 인물/가족 | 소속은 `family_membership`으로 |
| 3 | `user_account` | 인물/가족 | person 1:1, alive_uk(login_id, person_id) |
| 4 | `family_membership` | 인물/가족 | person↔family N:N + role |
| 5 | `person_setting` | 인물/가족 | person 1:1 |
| 6 | `account` | 가계부 | 계좌/결제수단 |
| 7 | `category` | 가계부 | 계층형(parent_id 자기참조), is_system |
| 8 | `collection` | 가계부(공용) | cover_photo_id는 photo 생성 후 별도 ALTER로 FK 추가 |
| 9 | `transaction` | 가계부 | 수입/지출/이체 통합 |
| 10 | `schedule` | 일정 | 시각/날짜 이원화 + 구글 연동 필드 |
| 11 | `schedule_subject` | 일정 | 다중 대상 |
| 12 | `schedule_participant` | 일정 | 실제 참석자 |
| 13 | `diary` | 일상기록 | recorded_at(UTC)/recorded_on(DATE) 이원화 |
| 14 | `diary_subject` | 일상기록 | 다중 대상 |
| 15 | `photo` | 일상기록 | photo_hash(CHAR64) 중복방지 |
| 16 | `tag` | 태그 | family당 유니크 이름 |
| 17 | `transaction_tag` | 태그 | N:N |
| 18 | `diary_tag` | 태그 | N:N |
| 19 | `refresh_token` | 인증 | soft-delete 미적용(물리 관리), jti 유니크 |
| 20 | `google_sync_state` | 외부연동 | refresh_token_enc(암호화) |
| 21 | `telegram_person_map` | 외부연동 | telegram_user_id↔person |

**공통 규칙**: 모든 시각은 `DATETIME(6)`(UTC), 날짜는 별도 `DATE` 컬럼. Soft-delete는 `deleted_at`(NULL=정상) + `alive_uk` 생성 컬럼(`IF(deleted_at IS NULL,0,TIMESTAMPDIFF(MICROSECOND,'1970-01-01',deleted_at))`)로 "살아있는 행끼리만 유니크 충돌".

---

## 3. 백엔드 — API 엔드포인트 (`/api/v1`)

### 인증 (`AuthController`)
| Method | Path | 기능 | 인증 |
|---|---|---|---|
| POST | `/auth/login` | 로그인 | permitAll |
| POST | `/auth/refresh` | 토큰 재발급(회전) | permitAll |
| POST | `/auth/logout` | 리프레시 토큰 폐기 | JWT |
| GET | `/auth/me` | 내 정보 | JWT |

### 가계부
| Method | Path | 기능 |
|---|---|---|
| GET/POST | `/accounts`, PUT/DELETE `/accounts/{id}` | 계좌 CRUD |
| GET/POST | `/categories`, PUT/DELETE `/categories/{id}` | 카테고리 CRUD (`?type=`) |
| GET | `/transactions?from&to&type&categoryId&scope&page&size` | 거래 목록(페이지네이션) |
| GET | `/transactions/statistics?from&to&groupBy&scope` | 통계(MyBatis, TRANSFER 제외) |
| POST/PUT/DELETE | `/transactions`, `/transactions/{id}` | 거래 CRUD |

### 일정 (`ScheduleController`)
| Method | Path | 기능 |
|---|---|---|
| GET | `/schedules?from&to&type&scope` | 목록 |
| POST/PUT/DELETE | `/schedules`, `/schedules/{id}` | CRUD |
| PATCH | `/schedules/{id}/done` | TODO 완료 토글 |

### 일상기록 (`DiaryController`)
| Method | Path | 기능 |
|---|---|---|
| GET | `/diaries?from&to&view&scope` | 목록(`view`은 클라 렌더 힌트, 서버 무시) |
| POST/PUT/DELETE | `/diaries`, `/diaries/{id}` | CRUD |

### 사진 (`PhotoController`) — presign→binary→complete 3단계
| Method | Path | 기능 | 인증 |
|---|---|---|---|
| POST | `/photos/presign` | 업로드 의도 등록 + 중복 제거(photoHash) | JWT |
| PUT | `/photos/{id}/binary` | 바이너리 업로드(서버 경유) | JWT |
| POST | `/photos/{id}/complete` | 확정 + width/height | JWT |
| GET | `/photos/{id}` | 메타 + 서명 URL | JWT |
| GET | `/photos/{id}/raw?exp&sig` | 서명 URL raw 서빙 | **permitAll** |
| DELETE | `/photos/{id}` | 삭제 | JWT |

### 태그 / 컬렉션 / 타임라인
| Method | Path | 기능 |
|---|---|---|
| GET/POST/DELETE | `/tags`, `/tags/{id}` | 태그 CRUD(수정 없음) |
| GET/POST/PUT/DELETE | `/collections`, `/collections/{id}` | 묶음 CRUD |
| GET | `/collections/{id}/summary` | 연결 거래·일정·기록·사진 집계 |
| GET | `/timeline?date=` 또는 `?from&to` | 통합 타임라인(MyBatis) |

### 가족/설정
| Method | Path | 기능 | 인가 |
|---|---|---|---|
| GET | `/family` | 가족 개요 | JWT |
| POST | `/family/members` | 구성원 추가 | `hasRole('PARENT')` |
| POST | `/family/members/{personId}/account` | 구성원 계정 생성 | `hasRole('PARENT')` |
| GET/PUT | `/me/settings` | 개인 설정(공유일정 표시 수준) | JWT |

### 텔레그램 연동
| Method | Path | 기능 | 인증 |
|---|---|---|---|
| POST/DELETE | `/me/telegram` | 본인 텔레그램 계정 연결/해제 | JWT |
| POST | `/integrations/telegram/transactions` | Hermes→거래 등록(source=TELEGRAM) | 서비스 토큰 |
| GET | `/integrations/telegram/query`, `/timeline`, `/schedules` | Hermes 조회 | 서비스 토큰 |
| POST/PUT/DELETE | `/integrations/telegram/schedules[/{id}]` | Hermes 일정 CRUD | 서비스 토큰 |

### 구글 캘린더 연동 (`GoogleController`)
| Method | Path | 기능 | 인증 |
|---|---|---|---|
| POST | `/integrations/google/connect` | 동의 URL 발급 | JWT |
| POST | `/integrations/google/sync` | 구글→우리 동기화(최초 전체/이후 증분) | JWT |
| POST | `/integrations/google/push` | 우리→구글 전송 | JWT |
| POST | `/integrations/google/notifications` | 구글 푸시 webhook | permitAll |
| GET | `/integrations/google/callback` | OAuth 콜백 | permitAll(state HMAC 검증) |
| DELETE | `/integrations/google` | 연동 해제 | JWT |

### 공통 응답
- `ApiResponse<T>` = `{success, data, error}`, null 필드 생략.
- `PageResponse<T>` = `{content, page, size, totalElements, totalPages}`.
- 에러코드: `VALIDATION_ERROR(400)`, `UNAUTHENTICATED(401)`, `FORBIDDEN_VISIBILITY(403)`, `NOT_FOUND(404)`, `CONFLICT(409)`, `BUSINESS_RULE(422)`, `INTERNAL_ERROR(500)`.

---

## 4. 백엔드 — 인증/인가 구조

- **필터 체인**: `HermesServiceTokenFilter`(텔레그램 경로, `X-Service-Token` 상수시간 비교) → `JwtAuthenticationFilter`(Bearer 파싱, 실패해도 익명 통과 후 `authorizeHttpRequests`가 401 차단).
- **family_id 격리**: JPA는 `@Filter(familyFilter)` + `FamilyFilterAspect`가 서비스 메서드 진입 시 자동 활성화(JWT 클레임에서만 추출). MyBatis는 `@Filter`가 안 먹으므로 `MyBatisTenantInterceptor`가 파라미터에 `familyId`를 자동 주입하지만, **매퍼 XML의 WHERE절에 `family_id`/`deleted_at IS NULL` 명시는 여전히 개발자 책임**(안전망일 뿐).
- **visibility 판정**: `VisibilityGuard`가 단건 조회/수정 시 `assertCanView`/`assertCanEdit` 호출, 위반 시 404(자원 존재 자체를 숨김). 목록 조회는 가드가 아니라 각 쿼리 WHERE절에서 필터링.

---

## 5. 프론트엔드 — 실제 디렉토리 구조

```
frontend/src/
├── api/         axios 클라이언트 + 도메인별 TanStack Query 훅 (client, auth, session, ledger, schedule, diary, collection, photo, timeline, family, google, telegram, settings)
├── app/         Expo Router 파일기반 화면 (아래 §6)
├── components/  ChipGroup, CollectionPicker, DiaryForm, PhotoSection, ScheduleForm, TransactionForm, Placeholder, themed-text/view
├── constants/   theme.ts
├── domain/      타입: types, ledger, schedule, diary, collection, config
├── hooks/       use-color-scheme(.web), use-theme — 거의 비어있음
├── lib/         confirm, pickImage, query(QueryClient), storage(토큰)
└── store/       auth (Zustand)
```

> `frontend/CLAUDE.md`가 규정한 `screens/`·`navigation/`·`theme/` 구조와는 다르다. 실제로는 Expo Router 관례상 화면이 `app/`에 있고, `theme/`는 `constants/theme.ts` 하나, `lib/`는 설계 문서에 없던 폴더.

---

## 6. 프론트엔드 — 화면 목록 (`app/`, 총 20개 + 로그인)

### 탭 (`app/(tabs)/`, `Tabs`=네이티브 / `WebSidebarLayout`=웹, `_layout.tsx`에서 `Platform.OS` 분기)
| 파일 | 화면 | 기능 |
|---|---|---|
| `index.tsx` | 타임라인 | 이번 달 통합 타임라인(`useTimeline`), 유형별 뱃지, FAB→거래입력 |
| `ledger.tsx` | 가계부 | 거래 목록(페이지네이션 30건), 통계/계좌/카테고리 링크, FAB→거래입력 |
| `schedule.tsx` | 일정 | 이번 달 일정 날짜별 그룹, TODO 완료 토글, 구글 동기화 상태 뱃지 |
| `diary.tsx` | 기록 | 이번 달 일기 카드(커버 사진), FAB→작성 |
| `more.tsx` | 더보기 | 프로필 요약, 이벤트묶음/가족/외부연동/설정 메뉴, 로그아웃 |

### 스택/모달 (`app/_layout.tsx`의 `AuthGate`가 인증 게이트 + 스택 정의)
| 파일 | 화면 |
|---|---|
| `login.tsx` | 로그인(RHF+Zod) |
| `transaction/new.tsx`, `transaction/[id].tsx` | 거래 생성/편집(모달) |
| `schedule/new.tsx`, `schedule/[id].tsx` | 일정 생성/편집(모달) |
| `diary/new.tsx`, `diary/[id].tsx` | 기록 생성/편집(모달, 편집엔 사진 섹션 포함) |
| `accounts.tsx` | 계좌 관리(목록+모달 폼) |
| `categories.tsx` | 카테고리 관리(목록+모달 폼) |
| `statistics.tsx` | 통계(기간/범위 선택, 카테고리별 바그래프, 월별) |
| `collections.tsx`, `collection/[id].tsx` | 이벤트 묶음 목록/요약 |
| `family.tsx` | 가족 구성원 목록, 구성원 추가·계정 생성(PARENT만) |
| `integrations.tsx` | 구글 캘린더(연결/동기화/전송/해제), 텔레그램(연결/해제) |
| `settings.tsx` | 개인 설정(공유일정 표시 수준) |

**공통 UI 패턴 관찰**: 모든 목록 화면이 FlatList + FAB(우하단 원형 버튼) + Modal 하단시트 폼을 거의 동일한 인라인 `StyleSheet`로 반복 정의(계좌/카테고리/컬렉션/가족 화면이 특히 유사). 재사용 컴포넌트는 `ChipGroup`(단일선택 칩)과 `CollectionPicker`뿐.

---

## 7. 프론트엔드 — 데이터 타입 (`domain/*.ts`, API 계약과 1:1)

- **공통**(`types.ts`): `ApiResponse<T>`, `PageResponse<T>`, `Visibility`(PRIVATE/SHARED_PERSONAL/PARENTS/FAMILY), `FamilyRole`, `Me`/`PersonSummary`, `TimelineItem`/`TimelineDay`(마스킹 시 title=null).
- **가계부**(`ledger.ts`): `Transaction`(sourceAccount/targetAccount는 마스킹 시 null), `Account`, `Category`, `Statistics`(byCategory/byMonth).
- **일정**(`schedule.ts`): `Schedule`(allDay로 시각/날짜 필드 이원화), `FamilyMember`.
- **일기**(`diary.ts`): `Diary`, `DiaryPhoto`.
- **컬렉션**(`collection.ts`): `Collection`, `CollectionSummary`(집계 카운트).

프론트 타입은 백엔드 `docs/05-api-spec.md` 응답 형태를 그대로 반영 — 서버 소유 필드(family_id, settlementStatus 등)는 `*Create` 타입에서 제외되어 있어 클라가 임의로 못 보냄.

---

## 8. 프론트엔드 — 상태관리 / API 레이어

- **서버 상태**: TanStack Query. 도메인별 훅이 `api/*.ts` 파일 안에 query+mutation 형태로 같이 정의됨(별도 `hooks/` 폴더 미사용). `staleTime 30s`, `refetchOnWindowFocus: false`, `retry: 1`.
- **클라 상태**: Zustand `store/auth.ts` — accessToken/refreshToken/me/status만 보관. axios 인터셉터 등 React 밖에서는 `getState()`로 접근.
- **axios 클라이언트**(`api/client.ts`): 요청 인터셉터가 Bearer 자동 부착. 응답 인터셉터가 401 시 refresh 1회만 실행되도록 promise 공유(`refreshing` 변수)로 큐잉 후 원요청 재시도, 실패 시 로그아웃.
- **토큰 영속화**(`lib/storage.ts`): 네이티브=`expo-secure-store`, 웹=메모리 객체(새로고침 시 로그아웃, XSS 방지 목적의 의도된 MVP 트레이드오프).
- **세션 부트스트랩**(`api/session.ts`): 앱 시작 시 저장된 refresh token으로 재발급 + `/auth/me` 조회 → `AuthGate`가 로그인/메인 라우팅.
- **캐시 무효화 패턴**: 거래/일정/일기 생성·수정·삭제 시 해당 목록 쿼리와 `['timeline']`을 함께 invalidate.
- **단건 조회 대체 패턴**: 백엔드에 단건 GET이 없어 `useCachedTransaction`/`useCachedSchedule`/`useCachedDiary`/`useCachedCollection`이 목록 쿼리 캐시를 순회해서 찾음 → 캐시가 비어있으면(딥링크, 새로고침 직후) 편집 화면이 "찾을 수 없음"을 표시.

### package.json 실사용 스택
`expo-router`, `@tanstack/react-query`, `zustand`, `axios`, `react-hook-form`+`@hookform/resolvers`+`zod`, `date-fns`(+tz), `expo-secure-store`, `@expo/vector-icons`. **gluestack-ui, NativeWind/Tailwind는 의존성에 없음** (§9 참고).

---

## 9. 프론트-백엔드 연결 매핑

| 프론트 파일 | 호출 백엔드 엔드포인트 |
|---|---|
| `api/auth.ts`, `api/session.ts` | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me` |
| `api/client.ts` | `POST /auth/refresh` (401 인터셉터에서 bare axios로 직접 호출) |
| `api/ledger.ts` | `GET/POST/PUT/DELETE /transactions[/{id}]`, `GET /transactions/statistics`, `GET/POST/PUT/DELETE /accounts[/{id}]`, `GET/POST/PUT/DELETE /categories[/{id}]` |
| `api/schedule.ts` | `GET/POST/PUT/DELETE /schedules[/{id}]`, `PATCH /schedules/{id}/done`, `GET /family`(멤버 목록 추출) |
| `api/diary.ts` | `GET/POST/PUT/DELETE /diaries[/{id}]` |
| `api/photo.ts` | `POST /photos/presign` → `PUT /photos/{id}/binary` → `POST /photos/{id}/complete`, `DELETE /photos/{id}` |
| `api/collection.ts` | `GET/POST/PUT/DELETE /collections[/{id}]`, `GET /collections/{id}/summary` |
| `api/timeline.ts` | `GET /timeline` |
| `api/family.ts` | `POST /family/members`, `POST /family/members/{personId}/account` |
| `api/settings.ts` | `GET/PUT /me/settings` |
| `api/google.ts` | `POST /integrations/google/connect|sync|push`, `DELETE /integrations/google` |
| `api/telegram.ts` | `POST/DELETE /me/telegram` |

**사진 업로드 3단계 플로우** (`api/photo.ts` ↔ `PhotoController`):
1. 프론트가 파일의 SHA-256을 계산해 `POST /photos/presign`으로 전송 → 백엔드가 `photoHash` 중복이면 `duplicated:true` 반환(재업로드 생략).
2. 중복이 아니면 프론트가 `PUT /photos/{id}/binary`로 바이너리를 서버 경유 업로드.
3. 프론트가 `POST /photos/{id}/complete`로 확정 → 백엔드가 서명된 `url`(=`/photos/{id}/raw?exp&sig`, permitAll)을 반환, 프론트는 `mediaUrl()`로 절대경로 조립.

현재 **웹(`Platform.OS === 'web'`)에서만 동작** — 네이티브에서는 `uploadPhotoToDiary`가 즉시 throw.

---

## 10. 설계 문서 대비 실제 구현 갭 — 리뉴얼 판단 포인트

| 갭 | 설계 문서(`frontend/CLAUDE.md`) | 실제 구현 | 리뉴얼 시 고려사항 |
|---|---|---|---|
| UI 라이브러리 | gluestack-ui(NativeWind) 명시 | `package.json`에 없음. 전 화면 순수 `StyleSheet.create` | 디자인 시스템 부재 — FAB/모달/칩/인풋 스타일이 화면마다 반복 정의(계좌/카테고리/컬렉션/가족 화면이 90% 동일 코드). 리뉴얼 시 공통 컴포넌트화 우선순위 높음 |
| 디렉토리 구조 | `screens/`, `navigation/`, `theme/` | `app/`(Expo Router), `constants/theme.ts`, `lib/`(문서에 없던 폴더) | 문서 자체를 실제 구조에 맞게 갱신할지, 실제 구조를 문서에 맞출지 결정 필요 |
| `hooks/` 역할 | "TanStack Query 훅, 비즈니스 로직" | 색상 스킴 훅만 존재, 쿼리 훅은 전부 `api/*.ts`에 있음 | 계층 분리(순수 API 호출 vs 화면용 훅)를 리뉴얼에서 도입할지 검토 |
| 사진 업로드 | 3단계 플로우, "일정 반영" 명시 | 웹만 동작, 네이티브는 미구현(throw) | 네이티브 카메라/앨범 플로우가 리뉴얼 스코프에 포함되는지 확인 필요 |
| 단건 조회 | — | 백엔드에 단건 GET 없음 → 프론트가 목록 캐시에서 검색(`useCached*`) | 딥링크/새로고침 직후 편집 진입 실패 가능성. 백엔드에 단건 GET 추가 여부 판단 필요 |
| 외부연동 착수 순서 | 루트 CLAUDE.md: "코어 완료 전 착수 금지" | 구글/텔레그램 연동 이미 상당 부분 구현됨 | 리뉴얼 스코프에 연동 화면(`integrations.tsx`) 포함 여부 명확히 |
| 컴포넌트 재사용 | — | `ChipGroup`, `CollectionPicker` 외 공용 컴포넌트 거의 없음 | Form 3종(Transaction/Schedule/Diary)이 각자 Field/버튼/에러 스타일을 중복 정의 |

---

## 참고: 조사 방법

- 백엔드 엔티티/DDL/컨트롤러/보안: 소스 직접 열람(서브에이전트 2건, 각 완료).
- 프론트 전체(화면 20개, 컴포넌트 8개, API 파일 11개, domain 5개, store/lib): 소스 전량 직접 열람(서브에이전트 2건은 30분 이상 무응답으로 중단, 동일 범위를 직접 읽어 대체).
