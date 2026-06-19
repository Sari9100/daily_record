# Family OS

부부(자녀 전제 설계)가 함께 쓰는 **가계부 + 일상기록 + 일정관리 통합** 시스템.
세 기능을 날짜축으로 합친 **통합 타임라인**이 핵심. Local-First, Cloud-Portable.

## 구조 (모노레포)

```
family-os/
├── CLAUDE.md          ← Claude Code 전역 규칙 (★ 작업 전 필독)
├── docs/              ← 설계 문서 SSOT (00~09 + DDL)
├── backend/           ← Spring Boot 4.1 / Java 21 (CLAUDE.md 별도)
└── frontend/          ← React Native + Expo (CLAUDE.md 별도)
```

## Claude Code로 개발하기

1. 루트에서 Claude Code 실행. `CLAUDE.md`가 자동 로드된다.
2. 작업 디렉토리에 따라 `backend/CLAUDE.md` 또는 `frontend/CLAUDE.md`가 추가로 적용된다.
3. **구현 순서를 지킨다**: 코어(인물→인증→가계부→일정→기록→타임라인) → 텔레그램 → 구글.
4. 백엔드는 `common/`(횡단 관심사)부터. `backend/CLAUDE.md`의 "골든 패스" 참조.

### 첫 작업 추천 프롬프트 예시
```
backend/CLAUDE.md를 읽고, common 패키지(BaseEntity, FamilyScopedEntity,
SecurityConfig, FamilyContext, VisibilityGuard, ApiResponse,
GlobalExceptionHandler)를 먼저 구축해줘. docs/03, docs/06을 근거로.
```

## 절대 규칙 (요약 — 상세는 CLAUDE.md)

물리삭제 금지 · family_id 멀티테넌트(MyBatis 수동 명시) · alive_uk 유니크 ·
FK 못 막는 alive는 서비스 검증 · visibility 2단계 방어(404 숨김) ·
시각=UTC/날짜=DATE 이원화 · 시크릿 환경변수.

## 환경 준비

- `.env.example`을 복사해 `.env` 작성 (커밋 금지).
- DDL은 `backend/src/main/resources/db/migration/V1__init.sql` (= `docs/04-V1__init.sql`).
  스키마 변경은 `V2__`, `V3__`... 새 마이그레이션으로. V1 수정 금지.

## 설계 문서

`docs/00-master.md`부터 번호순으로. 충돌 시 **설계 문서가 CLAUDE.md보다 우선**.
