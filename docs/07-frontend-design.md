# 가족 생활기록 시스템 — 프론트엔드 설계 (v1)

> 기준: 기능명세 v4, API명세 v1, 인증설계 v1
> 스택: **React Native + Expo / TypeScript**, 웹은 react-native-web
> 전략: **로직 공유 + UI 표현 분기** (앱 우선, 웹 따라오기)

---

## 0. 기술 스택

| 영역 | 선택 | 비고 |
|------|------|------|
| 프레임워크 | React Native + **Expo** | 기존 React 19 경험 활용 |
| 언어 | TypeScript | 타입 안정성 |
| 웹 | react-native-web | 로직 공유, HTML 기반 경량 웹 |
| 네비게이션 | Expo Router (파일 기반) | 웹/앱 라우팅 통합 |
| 서버 상태 | TanStack Query | API 캐싱·동기화·낙관적 업데이트 |
| 클라 상태 | Zustand | 인증·전역 UI 상태 (가벼움) |
| 폼 | React Hook Form + Zod | 검증(거래유형별 계좌 규칙 등) |
| HTTP | axios (인터셉터: 토큰·리프레시) | |
| 보안 저장 | expo-secure-store | 앱=헤더+시큐어스토어. 웹=메모리+자동refresh(MVP) 또는 httpOnly쿠키+CSRF(전환) — 06 참조 |
| 날짜 | **date-fns + date-fns-tz** | UTC↔로컬 변환. Temporal은 RN 폴리필 불안정이라 보류 |
| UI | **gluestack-ui (NativeWind/Tailwind 기반)** | RN+웹 동시 지원, 기존 Tailwind 경험 활용, 복사-붙여넣기 소유 |

---

## 1. 정보 구조 / 네비게이션

### 1-1. 인증 분기
```
앱 시작 → 토큰 확인
  ├─ 없음/만료 → [로그인 스택]
  └─ 유효 → [메인 앱]
```

### 1-2. 메인 네비게이션 (플랫폼별 표현 분기)
- **모바일**: 하단 탭 바 (5 탭)
- **웹**: 좌측 사이드바 (동일 5 섹션)

```
[홈/타임라인]  [가계부]  [일정]  [기록]  [더보기]
```

| 탭 | 핵심 화면 |
|----|-----------|
| 홈/타임라인 | 통합 타임라인(날짜축), 빠른 입력 진입 |
| 가계부 | 거래 목록·통계, 계좌, 카테고리 |
| 일정 | 캘린더·목록, 일정 상세 |
| 기록 | 피드/캘린더/앨범 뷰, 기록 상세 |
| 더보기 | 이벤트 묶음, 가족·구성원, 설정 |

---

## 2. 화면 목록 (MVP)

### 2-1. 인증 (3)
| 화면 | 설명 | API |
|------|------|-----|
| 로그인 | loginId/password | POST /auth/login |
| 스플래시/토큰체크 | 자동 로그인 판정 | GET /auth/me |
| (자녀계정 생성 — 2-7 가족/설정 탭에 위치) | — | — |

### 2-2. 홈 / 타임라인 (2)
| 화면 | 설명 | API |
|------|------|-----|
| 통합 타임라인 | 날짜별 지출+일정+기록+사진 병합. 핵심 화면 | GET /timeline |
| 빠른 입력 | 금액·메모 빠른 거래 입력(홈 화면 FAB 버튼 진입). 위젯은 차기 | POST /transactions |

### 2-3. 가계부 (6)
| 화면 | 설명 | API |
|------|------|-----|
| 거래 목록 | 기간·유형·scope 필터, 페이지네이션 | GET /transactions |
| 거래 상세/편집 | 조회·수정·삭제 | GET/PUT/DELETE /transactions/{id} |
| 거래 작성 | 유형별 계좌·카테고리·visibility·태그 | POST /transactions |
| 통계 | 카테고리/월별, scope별 차트 | GET /transactions/statistics |
| 계좌 관리 | 개인/공용 계좌 목록·CRUD | /accounts |
| 카테고리 관리 | 기본(삭제불가)+커스텀 | /categories |

### 2-4. 일정 (3)
| 화면 | 설명 | API |
|------|------|-----|
| 캘린더/목록 | 월·주 뷰, scope 필터 | GET /schedules |
| 일정 상세/편집 | subject·participant·location·반복 | GET/PUT/DELETE /schedules/{id} |
| 일정 작성 | visibility(4단계)·구글연동 표시 | POST /schedules |

### 2-5. 일상기록 (4)
| 화면 | 설명 | API |
|------|------|-----|
| 기록 피드/캘린더/앨범 | 뷰 전환(혼합형) | GET /diaries |
| 기록 상세 | 글+사진, subject | GET /diaries/{id} |
| 기록 작성/편집 | 사진 업로드(presign→complete) | POST /diaries, /photos/* |
| 앨범(사진 전용) | taken_at 정렬 그리드 | GET /diaries?view=album |

### 2-6. 이벤트 묶음 (2)
| 화면 | 설명 | API |
|------|------|-----|
| 묶음 목록 | Collection 리스트 | GET /collections |
| 묶음 상세/리뷰 | 총지출·사진수·기록수 요약, 항목 연결 | GET /collections/{id}/summary |

### 2-7. 가족 / 설정 (4)
| 화면 | 설명 | API |
|------|------|-----|
| 가족·구성원 | 멤버 목록, 자녀 추가(PARENT) | GET /family, POST /family/members |
| 자녀 계정 생성 | 기존 Person에 계정 부여 | POST /family/members/{id}/account |
| 개인 설정 | 공유개인 일정 표시수준 등 | /me/settings |
| 외부연동 | 구글 캘린더 연결 상태 | /integrations/google/* |

**합계: 약 24개 화면 (MVP)**

---

## 3. 코드 공유 구조 (로직 공유 + UI 분기)

### 3-1. 디렉토리 (모노레포 또는 단일 Expo 프로젝트)
```
/src
  /api          ← 공유: axios 클라이언트, 엔드포인트별 훅(useTransactions 등)
  /domain       ← 공유: 타입(Transaction, Schedule...), 권한·통화·날짜 유틸
  /store        ← 공유: Zustand (auth, ui)
  /hooks        ← 공유: TanStack Query 훅, 비즈니스 로직
  /components   ← 분기: .tsx(공통) / .native.tsx / .web.tsx (필요시)
  /screens      ← 대부분 공유, 레이아웃만 플랫폼 분기
  /navigation   ← 분기: 모바일 탭바 / 웹 사이드바
  /theme        ← 공유: 토큰(색·간격·타이포)
```

### 3-2. 공유 vs 분기 기준
| 영역 | 공유율 | 비고 |
|------|--------|------|
| API·도메인·상태·훅 | ~100% | 플랫폼 무관 |
| 폼 로직·검증 | ~100% | Zod 스키마 공유 |
| 화면 구성 로직 | ~80% | 데이터 흐름 공유 |
| 레이아웃·네비게이션 | 분기 | 탭바 vs 사이드바 |
| 네이티브 기능 | 분기 | 카메라·사진·시큐어스토어 |

### 3-3. 플랫폼 분기 방법
- 파일 확장자: `Component.native.tsx` / `Component.web.tsx`
- `Platform.OS` 런타임 분기 (간단한 경우)
- 네이티브 전용 모듈은 web에서 no-op 또는 대체 구현

---

## 4. 핵심 UX 흐름

### 4-1. 빠른 입력 (MVP: 앱 내 버튼 / 위젯은 차기)
- **MVP**: 홈 화면 FAB(플로팅 버튼) → 빠른입력 화면. 위젯 없이 시작
  - 이유: Expo 관리형에서 위젯은 네이티브 모듈(prebuild/config plugin) 필요 → RN 첫 도전에 함정
- **차기(텔레그램 단계 이후)**: iOS WidgetKit/Android 위젯 탭 → 빠른입력 딥링크
- 빠른입력: 금액·메모·계좌·visibility(기본 개인) 최소 입력 → 저장

### 4-2. 사진 업로드 (3단계)
```
선택 → POST /photos/presign (storageKey 수신)
     → 바이너리 업로드
     → POST /photos/{id}/complete
```
- 진행률 표시, 실패 재시도, photoHash 중복 시 기존 사용

### 4-3. visibility 입력 UX
- 거래: 개인/부모공유 토글 (기본 개인)
- 일정·기록: 4단계 선택 (순수개인/공유개인/부모공유/가족공동)
- 공유개인 선택 시 subject 지정 UI

### 4-3-1. 빈 상태·마스킹 컴포넌트 규칙 (디자인 단계 필수)
백엔드가 마스킹/삭제 데이터를 내려주므로, 컴포넌트가 이 variant를 처음부터 가져야 나중에 안 갈아엎음:
- **마스킹된 계좌**(타인 공유거래, sourceAccount=null): "비공개" 회색 칩으로 표시(빈칸 금지)
- **삭제된 계좌·카테고리**(과거 거래 참조): "(삭제됨)" 접미사 + 흐린 색. 신규 입력 선택지엔 미노출
- **SHARED_PERSONAL SUMMARY 일정**: 일정 카드 컴포넌트는 **FULL/SUMMARY 2-variant** 필수.
  SUMMARY는 title 마스킹("바쁨")·시간만 표시. 같은 카드가 뷰어 PersonSetting에 따라 분기
- 공통: 빈 목록(거래·일정·기록 0건) 플레이스홀더 일관 컴포넌트

### 4-4. 토큰 갱신 (axios 인터셉터)
- 401 응답 → refresh 시도 → 성공 시 원요청 재시도, 실패 시 로그인 이동
- 동시 401 다중요청은 refresh 1회로 큐잉

---

## 5. 오프라인 / 동기화 (MVP 범위 판단)
- MVP: 기본 온라인 전제. TanStack Query 캐시로 일시적 오프라인 조회만
- 오프라인 입력 큐잉·충돌 해결은 **차기**(복잡도 높음) — 지금은 범위 외

---

## 6. 열린 항목

**해결됨 (착수 블로커 해소)**
- ~~디자인 시스템~~ → **gluestack-ui (NativeWind/Tailwind)** 확정. RN+웹 지원, 기존 Tailwind 경험 활용
- ~~날짜 유틸 date-fns vs Temporal~~ → **date-fns + date-fns-tz 단일화** (Temporal은 RN 폴리필 불안정)
- ~~위젯 MVP 포함 여부~~ → **MVP 제외**, 앱 내 FAB 버튼으로 시작. 위젯은 텔레그램 단계 이후

**남은 항목 (구현 중 결정)**
1. 토큰 갱신 인터셉터의 동시 401 큐잉 구현 — 검증된 패턴 사용(무한루프·큐누수 주의)
2. 사진 업로드 진행률/재시도 — 일상기록 작성 화면 난이도 ↑ (일정 반영)
3. 웹 SEO/접근성 수준 (가족용 비공개라 낮음)
4. 푸시 알림 — 명세상 없음, 추후 Expo Notifications

---

## 7. 다음 단계 (남은 문서 — 우선순위 5·6)
- **외부연동 상세설계**: 구글 캘린더 양방향 동기화(충돌·증분), 텔레그램/Hermes
- **파일저장·인프라·배포**: StorageService 구체화, Docker/Cloudflare/Flyway/백업 자동화
- (이후) 백엔드 레이어/패키지 구조 — 구현 착수 직전
