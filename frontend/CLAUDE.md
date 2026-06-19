# frontend/CLAUDE.md — Family OS 프론트엔드

> 루트 `../CLAUDE.md`를 먼저 따른다. 이 파일은 RN+Expo 구조와 UI 규칙을 정의한다.
> 설계 SSOT: `docs/07-frontend-design.md`, API 계약: `docs/05-api-spec.md`.

---

## 0. 스택 고정값

- React Native + **Expo** / TypeScript / 웹=**react-native-web**
- 네비: **Expo Router**(파일 기반) · 서버상태: **TanStack Query** · 클라상태: **Zustand**
- 폼: **React Hook Form + Zod** · HTTP: **axios**(토큰 인터셉터)
- UI: **gluestack-ui (NativeWind/Tailwind 기반)** · 날짜: **date-fns + date-fns-tz** (Temporal 금지 — RN 폴리필 불안정)
- 토큰 저장: 앱=expo-secure-store(헤더 Bearer) / 웹=메모리+자동refresh(MVP)
- 전략: **로직 공유 + UI 표현만 플랫폼 분기** (앱 우선, 웹 따라오기)

> ⚠️ 세팅 시 확인: gluestack-ui 버전이 현재 Expo SDK·React 19·New Architecture와 호환되는지 `npx create-expo` 직후 검증.

---

## 1. 디렉토리 구조

```
frontend/src/
├── api/         ← 공유: axios 클라이언트, 엔드포인트별 훅(useTransactions 등)
├── domain/      ← 공유: 타입(Transaction, Schedule...), 권한·통화·날짜 유틸
├── store/       ← 공유: Zustand (auth, ui)
├── hooks/       ← 공유: TanStack Query 훅, 비즈니스 로직
├── components/  ← 분기 필요 시 .native.tsx / .web.tsx, 아니면 .tsx 공통
├── screens/     ← 대부분 공유, 레이아웃만 분기
├── navigation/  ← 분기: 모바일 탭바 / 웹 사이드바
└── theme/       ← 공유: 디자인 토큰(색·간격·타이포)
```

- **api/domain/store/hooks/폼검증 = ~100% 공유.** 플랫폼 분기는 레이아웃·네비·네이티브기능(카메라·사진·시큐어스토어)만.
- 분기 방법: `Component.native.tsx`/`Component.web.tsx` 또는 `Platform.OS`. 네이티브 전용은 web에서 no-op.

---

## 2. ⚠️ UI variant — 처음부터 넣어야 나중에 안 갈아엎는다

백엔드가 마스킹/삭제 데이터를 그대로 내려준다. 컴포넌트가 이 상태를 **처음부터** 처리해야 한다.

| 상태 | 처리 |
|------|------|
| 마스킹된 계좌 (타인 공유거래, `sourceAccount=null`) | "비공개" 회색 칩. **빈칸 금지** |
| 삭제된 계좌·카테고리 (과거 거래 참조) | "(삭제됨)" 접미사 + 흐린 색. 신규 입력 선택지엔 미노출 |
| SHARED_PERSONAL SUMMARY 일정 | 일정 카드 **FULL/SUMMARY 2-variant 필수**. SUMMARY는 title "바쁨"·시간만 |
| 빈 목록 (거래·일정·기록 0건) | 일관된 플레이스홀더 컴포넌트 |

---

## 3. 핵심 규칙

### 3-1. 날짜·시각 (date-fns-tz)
- 서버는 **UTC ISO-8601**(`...Z`)로 주고받는다. 표시할 때만 사용자 timezone으로 변환.
- 종일일정은 `startDate/endDate`(날짜), 시점일정은 `startedAt/endedAt`(UTC). **`allDay` 플래그로 분기** — 둘 중 하나만 채워 전송.
- 기록은 `recordedOn`(날짜, 필수) + `recordedAt`(시각, 선택).
- 절대 로컬 날짜를 그대로 UTC로 보내지 말 것(날짜 밀림).

### 3-2. 토큰 갱신 (axios 인터셉터)
- 401 → refresh 시도 → 성공 시 원요청 재시도, 실패 시 로그인 이동.
- **동시 401 다중요청은 refresh 1회로 큐잉**(무한루프·큐 누수 주의 — 검증된 패턴 사용). 백엔드 grace window와 정합.

### 3-3. visibility 입력 UX
- 거래: 개인/부모공유 토글(기본 개인).
- 일정·기록: 4단계(순수개인/공유개인/부모공유/가족공동). 공유개인 선택 시 subject 지정 UI.

### 3-4. 사진 업로드 (3단계)
- `presign → 바이너리 업로드 → complete`. 진행률 표시, 실패 재시도, photoHash 중복 시 기존 사용.
- 일상기록 작성 화면은 이 때문에 다른 화면보다 난이도 높음(일정 반영).

### 3-5. API 계약 준수
- 응답 래퍼 `{success, data, error}` 일관 처리. 페이지네이션 `{content, page, size, totalElements, totalPages}`.
- boolean은 `isXxx`(서버 직렬화 규칙). 타입은 `domain/`에 API 표기 기준으로 정의.
- family_id는 **클라가 보내지 않는다**(토큰 기반).

---

## 4. MVP 범위

- 화면 약 24개: 인증(3)·홈/타임라인(2)·가계부(6)·일정(3)·일상기록(4)·묶음(2)·가족/설정(4).
- 빠른입력은 **홈 FAB 버튼**으로 시작. **위젯은 MVP 제외**(네이티브 prebuild 함정 — 텔레그램 단계 이후).
- 오프라인 입력 큐잉 제외(TanStack Query 캐시로 조회만). 푸시 알림 없음.

---

## 5. 하지 말 것

- Temporal 사용(date-fns로 통일)
- 위젯을 MVP에 넣기
- family_id를 요청에 포함
- 마스킹/삭제/SUMMARY variant 없이 카드 컴포넌트 만들기
- visibility를 클라에서 임의 필터링(서버가 권한 범위 내 데이터만 줌 — 클라는 표시)
