# Family OS — 배포 / Hermes 연동 운영 가이드 (초안)

> 기존 stock-account-v2 스택에 **가계부+일정을 Family OS 로 대체**하는 배포.
> 상세 설계는 작업 중 산출물(scratchpad)의 다음 문서 참조 → 최종 확정 시 `docs/` 로 이관 예정:
> deploy-integration-plan.md / implementation-order.md / phase-drafts.md / member-provisioning.md / readiness.md

## 구성 요약
- 신규 컨테이너 3개: `family-os-api`(Spring Boot), `family-os-web`(Expo web static), `family-os-mcp`(MCP, Streamable HTTP)
- 재사용: `mysql-server-v2`(family_os 스키마), `stock-nginx-v2`(가계부 도메인 라우팅 교체), Cloudflare(무변경)
- 네트워크: 기존 `stock-network-v2`(external) 공유
- MCP 토폴로지: 전용 `family-os-mcp` 분리. Hermes 는 구성원별 프로필+봇, 정적 헤더 `X-Telegram-User-Id` 로 귀속.

## 배포 순서 (요약 — implementation-order.md 의 10 Phase)
1. family_os DB/계정 생성 (mysql-server-v2) — 비번은 .env 에서 주입:
   `set -a; . ./.env; set +a; sed "s/__DB_PASSWORD__/$DB_PASSWORD/" scripts/family-os-init.sql | docker exec -i mysql-server-v2 mysql -uroot -p<ROOT_PW>`
2. `cp .env.example .env` 후 값 채우기 → `docker compose up -d family-os-api` (Flyway 마이그레이션·부트스트랩 시드)
3. (백엔드) 텔레그램 일정 쓰기 엔드포인트 추가 — Phase 3, 별도 구현
4. `docker compose up -d family-os-web` (Expo web)
5. 서버 `nginx-v2.conf` 가계부 도메인 → family-os 로 교체(`deploy/nginx-family-os.conf` 참고) → reload
6. `docker compose up -d family-os-mcp` (호스트 localhost:8088 노출, Tailscale 로 Hermes 접근)
7. 구성원 텔레그램 매핑 + Hermes 프로필: `scripts/provision-member.sh`
8. Hermes 멤버 프로필 동작 확인(구성원별 귀속)
9. stock 측 정리: 가계부/일정 도구 비활성, schedule-service·ledger UI 폐기

## 포트
- family-os-api: 내부 8080 (nginx 라우팅; 디버그 시 8087 노출)
- family-os-mcp: 호스트 8088 → 컨테이너 3000 (Hermes 접근)

## 환경 설정 — .env 2 컨텍스트 (혼동 주의)

이 레포에는 **목적이 다른 .env 가 둘** 있다. 섞지 말 것.

| 파일 | 용도 | 실행 | DB 접속 | API base |
|------|------|------|---------|----------|
| **루트 `.env`** (← `.env.example`) | **docker-compose 배포** | `docker compose up` | `DB_HOST=mysql-server-v2:3306` (컨테이너명) | 도메인 `https://ledger.saristock.com/api/v1` |
| **`backend/.env`** (← `backend/.env.example`) | **로컬 dev** | `./gradlew bootRun` | `localhost:3308` (호스트 노출 포트) | `localhost:8080` |

- 컨테이너 내부에선 MySQL 을 **컨테이너명(mysql-server-v2)·내부포트(3306)** 로, 호스트 dev 에선 **localhost·노출포트(3308)** 로 접근한다(같은 DB, 경로만 다름).
- 두 .env 모두 **커밋 금지**(.gitignore). 시크릿(JWT·STORAGE·서비스토큰)은 컨텍스트별로 따로 둔다.
- `family-os-mcp` 는 컨테이너 내부에서 `FAMILYOS_API_BASE_URL=http://family-os-api:8080/api/v1` 로 api 를 부른다(루트 .env / compose). 로컬 dev 로 MCP 를 띄워 검증할 땐 `FAMILYOS_API_BASE_URL=http://localhost:8080/api/v1`.

## 주의
- `.env` 는 커밋 금지(.gitignore 처리). 시크릿은 `openssl rand -hex 32`.
- `HERMES_SERVICE_TOKEN` == `FAMILYOS_SERVICE_TOKEN` (api ↔ mcp 동일값).
- 일정 쓰기(schedule_create/update/delete)는 백엔드 Phase 3 엔드포인트 추가 후 동작.
