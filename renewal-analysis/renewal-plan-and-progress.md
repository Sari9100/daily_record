# 프론트엔드 리뉴얼 — 진행 계획 및 진행 상황

> 작성일: 2026-07-17. `renewal-analysis/current-state.md`(기존 구조 조사) 다음 단계 문서.
> 이 문서는 **왜 이렇게 하기로 했는지(결정 사항)**와 **지금까지 무엇을 끝냈는지 / 무엇이 남았는지**를 추적한다.

---

## 1. 배경 — 왜 리뉴얼하나

`current-state.md` 조사 결과 실제 구현이 설계 문서(`docs/07-frontend-design.md`)와 두 지점에서 어긋나 있었다.

- UI는 gluestack-ui/NativeWind로 "확정"돼 있었지만 실제로는 미도입, 전 화면(20개)이 인라인 `StyleSheet`를 반복 정의(FAB·모달·칩 스타일이 8곳 이상 복붙).
- 일정의 "캘린더·목록", 기록의 "피드/캘린더/앨범 뷰(전환)"이 설계에 있었고 API도 이미 지원(`GET /diaries?view=`)하지만, 프론트가 이를 구현하지 않고 항상 리스트만 렌더링.

사용자 요구사항(논의를 통해 확정):
1. 디자인 시스템이 없는 것이 가장 큰 문제 — UI가 촌스럽고 사용성이 떨어짐.
2. 월간 기록은 달력 기준 표기가 필요 — **인라인/캘린더 둘 다 구현**, 기본 포맷은 **화면별로 설정 가능**해야 함.
3. 기록·일정·가계부를 **묶음(Collection)으로 연결하는 흐름이 더 편리해야** 함.

---

## 2. 확정된 의사결정

| 항목 | 결정 | 근거 |
|---|---|---|
| 디자인 시스템 방식 | **자체 경량 디자인 시스템** (gluestack-ui/NativeWind 대신) | RN 0.85/React 19/New Architecture 호환성 리스크 회피, 기존 코드가 이미 StyleSheet라 전환 비용 최소화 |
| SSOT 처리 | `docs/07-frontend-design.md`의 gluestack-ui "확정" 문구를 자체 시스템으로 갱신 | 설계 문서와 실제 구현이 어긋나면 안 됨(루트 CLAUDE.md 규칙) — 완료 |
| 작업 순서 | **디자인 시스템 먼저 → 화면 전체 교체** | 사용자가 "디자인 시스템 먼저" 명시적으로 선택 |
| 뷰모드 기본값 저장 위치 | **서버(PersonSetting) 영구 저장**, 화면 내 토글은 세션 로컬 | 기기 간 동기화 필요 + 기존 `sharedScheduleDetailLevel` 패턴과 일관성 |
| 캘린더 구현 | **외부 라이브러리 없이 date-fns로 직접 구현** | 이미 date-fns 의존성 보유, 번들 증가 없음, 네이티브/웹 동일 동작 |
| 묶음 연결 개선 범위 | 폼 내 인라인 생성 + 묶음 요약 화면 퀵액션까지만. **타임라인→묶음 이동은 범위 밖**(백엔드 `TimelineItem`에 collectionId 없음) | 백엔드 변경 없이 처리 가능한 범위로 1단계를 제한 |

---

## 3. 1단계 진행 상황 — ✅ 완료

### 3-1. 디자인 토큰 & 프리미티브 컴포넌트
| 파일 | 내용 |
|---|---|
| `frontend/src/constants/theme.ts` | `Colors.light/dark`에 `primary/danger/success/border/textMuted/surfaceMuted/overlay` 등 의미 토큰 추가, `Radius`(sm/md/lg/pill) 신설 |
| `frontend/src/components/ui/*` (신규 11개 + `index.ts`) | `Button`, `Fab`, `Card`, `Chip`, `Badge`, `TextField`, `BottomSheetModal`, `EmptyState`, `SegmentedControl`, `ListRow`, `ScreenHeader` — 전부 `useTheme()`로 라이트/다크 대응 |
| `frontend/src/components/ChipGroup.tsx` | 내부를 `Chip` 프리미티브로 교체(공개 API 동일 유지, 8개 화면 호출부 영향 없음) |

### 3-2. 캘린더 컴포넌트
| 파일 | 내용 |
|---|---|
| `frontend/src/components/calendar/MonthCalendar.tsx` (신규) | date-fns 기반 월간 그리드. `markersByDate`로 날짜별 dot 표시, 월 이동, 날짜 선택 — 순수 프레젠테이션(데이터는 화면이 주입) |

### 3-3. 화면별 기본 뷰(인라인/캘린더) 설정
| 계층 | 파일 | 내용 |
|---|---|---|
| DB | `backend/.../db/migration/V2__person_setting_default_view.sql` (신규) | `person_setting`에 `ledger_default_view`/`schedule_default_view`/`diary_default_view` (VARCHAR, 기본 'INLINE') 3컬럼 추가 |
| Entity | `person/entity/ViewMode.java`(신규 enum), `person/entity/PersonSetting.java` | `INLINE`/`CALENDAR`, lazy-upsert 패턴을 기존 `sharedScheduleDetailLevel`과 동일하게 적용 |
| DTO/서비스 | `PersonSettingResponse`, `UpdatePersonSettingRequest`, `PersonSettingService`, `MeSettingsController` | GET은 행 없으면 기본값(FULL/INLINE×3) 응답, PUT은 4개 필드 전체를 항상 받음(부분 업데이트 아님) |
| 테스트 | `person/service/PersonSettingServiceTest.java`(신규) | lazy 기본값 응답 + upsert 검증 2건 |
| 프론트 API | `frontend/src/api/settings.ts` | `PersonSetting` 타입에 3필드 추가, `useUpdateSettings`가 현재 캐시값 + 변경분(patch)을 합쳐 항상 전체를 PUT |
| 프론트 화면 | `frontend/src/app/settings.tsx` | 기존 공유일정 표시수준 + 화면별 기본 뷰 3개, 총 4개 `SegmentedControl`로 재구성 |
| 프론트 훅 | `frontend/src/hooks/use-view-mode.ts`(신규) | 화면(`ledger`/`schedule`/`diary`) 단위로 서버 기본값을 최초 1회 반영, 이후 토글은 로컬 상태(`[mode, setMode]` 반환) — **아직 실제 화면(가계부/일정/기록)에 연결은 안 함, 2단계에서 사용 예정** |

### 3-4. 묶음(Collection) 연결 UX
| 파일 | 내용 |
|---|---|
| `frontend/src/components/CollectionPicker.tsx` | "+ 새 묶음" 옵션 추가 — 탭하면 `BottomSheetModal`로 이름만 입력해 즉시 생성 후 자동 선택 |
| `frontend/src/app/collection/[id].tsx` | 상단에 "+ 거래 / + 일정 / + 기록" 퀵액션 3개 추가, `router.push({ ..., params: { collectionId } })`로 이동 |
| `frontend/src/app/transaction/new.tsx`, `schedule/new.tsx`, `diary/new.tsx` | `useLocalSearchParams`로 `collectionId`를 읽어 해당 폼의 초기값으로 전달 |
| `frontend/src/components/ScheduleForm.tsx`, `DiaryForm.tsx` | `initial` prop 타입을 `Partial<Schedule>`/`Partial<Diary>`로 완화(신규 작성 시 collectionId만 prefill 가능하도록) |

### 3-5. 설계 문서 갱신 (SSOT 정합성)
- `docs/07-frontend-design.md`: UI 스택 문구를 gluestack-ui → 자체 디자인 시스템으로 교체(2곳)
- `docs/02-db-design.md`, `docs/03-jpa-entity.md`: `person_setting` 신규 3컬럼/필드 반영
- `docs/05-api-spec.md`: `/me/settings` 응답 스키마에 3필드 + "PUT은 전체를 보낸다" 규칙 명시

### 3-6. 검증
- 백엔드: `./gradlew test` 전체 스위트 `BUILD SUCCESSFUL` (신규 `PersonSettingServiceTest` 포함)
- 프론트: `npx tsc --noEmit` 클린, `expo start --web` 번들 정상 컴파일(1925 모듈), 헤드리스 브라우저(Playwright)로 로그인 화면 렌더 확인·콘솔 에러 없음
- **미검증(한계)**: 이 환경엔 로컬 백엔드/DB·로그인 계정이 없어 로그인 이후 화면(설정 화면의 4개 SegmentedControl 실동작, CollectionPicker 새 묶음 생성, 묶음 퀵액션 이동)은 브라우저 클릭으로 확인하지 못함. 로컬 서버 기동 후 실제 클릭 검증 필요.

---

## 4. 2단계 진행 상황 — ✅ 완료 (화면 20개 전체 + 공유 폼 3종 교체)

1단계에서 만든 컴포넌트/설정을 실제 화면에 반영. 원칙대로 **기존 로직(TanStack Query 훅, 폼 검증, API 연동)은 그대로 두고 마크업/스타일만 `components/ui/*`·`components/calendar/*`로 치환**했다 — 데이터 레이어 변경 없음.

| 그룹 | 화면 | 주요 작업 |
|---|---|---|
| 타임라인 | `app/(tabs)/index.tsx` | `Badge`/`EmptyState`/`Fab`로 교체, 항목 탭 시 해당 거래/일정/기록 상세로 이동하도록 개선 |
| 가계부 | `ledger.tsx`, `statistics.tsx`, `accounts.tsx`, `categories.tsx` | `use-view-mode('ledger')` 연결로 인라인↔`MonthCalendar` 토글 실장(월 범위 조회로 변경), CRUD 화면은 `BottomSheetModal`/`ListRow`/`Fab`/`TextField`로 교체, 통계는 `Card`+토큰 색상 |
| 일정 | `schedule.tsx`, `schedule/[id].tsx` | `use-view-mode('schedule')` 연결, 캘린더에서 날짜 선택 시 하단 아젠다 필터링 |
| 기록 | `diary.tsx`, `diary/[id].tsx`, `PhotoSection.tsx` | `use-view-mode('diary')` 연결, `Card` 기반 그리드/캘린더 전환 |
| 공유 폼 | `TransactionForm.tsx`, `ScheduleForm.tsx`, `DiaryForm.tsx` | 로컬 `Field`+`TextInput`+저장/삭제 버튼 반복 정의를 `TextField`/`Button`/`Chip`(다중선택 subject)로 통일 |
| 나머지 | `more.tsx`, `collections.tsx`, `family.tsx`, `integrations.tsx`, `login.tsx`, `(tabs)/_layout.tsx`(사이드바/탭바) | `ListRow`/`Button`/`BottomSheetModal`/`TextField`로 마크업 교체, 로그인 폼은 `Controller`+`TextField` 결합 |

가계부 목록은 기존 "최근 30건" 조회(`page/size`만)에서 **월 단위 `from/to` 조회(size=200)**로 바뀌었다 — 캘린더 뷰가 그 달 전체 데이터를 알아야 마커를 그릴 수 있어서다(일정·기록 화면이 이미 쓰던 패턴과 통일).

### 검증
- `npx tsc --noEmit` 클린 (그룹별로 3회 분할 실행하며 즉시 확인)
- `expo start --web` 번들 정상 컴파일(2069 모듈), 헤드리스 브라우저로 로그인 화면 재확인 — 콘솔 에러 없음, 기존과 픽셀 동일하게 렌더(마크업 교체가 시각적으로 회귀 없음을 방증)
- **미검증(한계)**: 로컬 백엔드/DB·로그인 계정이 없어 인증 이후 전 화면(캘린더 뷰 마커/토글 실동작, 가계부·일정·기록 각 목록, CRUD 모달, 설정 SegmentedControl, 폼 3종)은 브라우저 클릭으로 확인 못함. **로컬 서버 기동 후 최소 한 번 실기기/브라우저로 훑어보는 것을 권장.**

---

## 5. 참고 — 변경 파일 전체 목록 (git status 기준)

**백엔드(신규/수정)**: `PersonSetting.java`, `ViewMode.java`(신규), `PersonSettingResponse.java`, `UpdatePersonSettingRequest.java`, `PersonSettingService.java`, `MeSettingsController.java`, `V2__person_setting_default_view.sql`(신규), `PersonSettingServiceTest.java`(신규)

**프론트 — 신규**: `components/ui/*`(11개+index), `components/calendar/MonthCalendar.tsx`, `hooks/use-view-mode.ts`

**프론트 — 수정(전체 20개 화면 + 공유 컴포넌트)**: `constants/theme.ts`, `components/{ChipGroup,CollectionPicker,TransactionForm,ScheduleForm,DiaryForm,PhotoSection}.tsx`, `api/settings.ts`, `app/(tabs)/{_layout,index,ledger,schedule,diary,more}.tsx`, `app/{accounts,categories,collections,statistics,family,integrations,login,settings}.tsx`, `app/{transaction,schedule,diary}/{new,[id]}.tsx`, `app/collection/[id].tsx`

**문서**: `docs/02-db-design.md`, `docs/03-jpa-entity.md`, `docs/05-api-spec.md`, `docs/07-frontend-design.md`, `renewal-analysis/current-state.md`(기존), `renewal-analysis/renewal-plan-and-progress.md`(이 문서)

> 아직 커밋되지 않은 워킹 디렉토리 변경사항입니다 — git status 기준.

---

## 6. 다음에 볼 만한 것 (남은 개선 여지, 강제 아님)

- 타임라인 항목에서 묶음(Collection)으로 바로 이동하는 기능 — `TimelineItem`에 `collectionId`가 없어 백엔드 `TimelineMapper`/DTO 변경 필요(1단계 문서에서 범위 밖으로 명시했던 항목).
- 가계부 캘린더 뷰의 월 단위 재조회(size=200)가 실제 거래량이 매우 많은 가족에서 충분한지 — 필요시 서버 페이지네이션을 캘린더 모드에도 적용하는 방안 검토.
- 네이티브(iOS/Android) 사진 업로드 미구현 상태는 그대로 — 이번 리뉴얼 범위 밖.
