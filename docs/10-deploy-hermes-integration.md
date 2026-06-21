# 10 — 배포 · Hermes/MCP 연동 · 텔레그램 설계 (SSOT)

> 기존 `stock-account-v2` 스택에 **가계부+일정을 Family OS 로 대체**하고, 운영 중 Hermes(텔레그램 봇)에 신규 연동을 붙이는 배포·통합 설계.
> 기준: 실측(stock-account-v2 compose, mcp-server 소스, ~/.hermes Hermes 설정) + docs 08(외부연동)·09(인프라).
> 확정 결정: 가계부+일정 → Family OS 대체 / **데이터 이관 없음(fresh)** / **전용 MCP 분리** / **구성원별 귀속(모드 B)** / 별도 compose + stock-network-v2 공유 / 봇 일정 생성·수정까지 / 인프라(mysql·nginx·Cloudflare) 재사용.

---

## 1. 현행 운영 환경 (실측)

`stock-account-v2/docker-compose.yml` (네트워크 `stock-network-v2`, bridge):

| 컨테이너 | 역할 | 처리 |
|----------|------|------|
| mysql-server-v2 (mysql:8.0) | DB (stockdb_v2) | **재사용** — family_os 스키마 추가 |
| stock-app-v2 | 주식+가계부 백엔드 (내부 8080, /mcp 보유) | 주식 유지, 가계부 도구 비활성 |
| stock-invest-ui-v2 | 주식 프론트 | 유지 |
| stock-ledger-ui-v2 | 가계부 프론트 | **Family OS web 로 대체** |
| schedule-service (8085) | 일정 서비스 | **폐기** (일정→Family OS) |
| mcp-server (8086→8083) | MCP 게이트웨이 | 주식 전용으로 축소 |
| stock-nginx-v2 (80) | 리버스 프록시 (`/Volumes/ServerData/config/nginx-v2.conf`) | **재사용** — 가계부 도메인 라우팅 교체 |

- 사진/DB 볼륨 관례: `/Volumes/ServerData/docker/...`. config: `/Volumes/ServerData/config/`.
- Cloudflare: 도메인 `*.saristock.com`. 가계부 도메인 = `ledger.saristock.com`. (cloudflared 컨테이너 없음 — 오리진=nginx)

### Hermes (실측: `~/.hermes`)
- **별도 외부 봇** — `hermes-agent`(Python, launchd). 프로필별 게이트웨이: default/ledger/schedule (`--profile`, 프로필별 HERMES_HOME·텔레그램 봇·MCP 설정).
- MCP 연결: `mcp_servers.<name>.url: http://host:port/mcp` = **Streamable HTTP**. 다중 서버 + 서버별 `tools.include` 화이트리스트 지원. 헤더는 **`${ENV_VAR}` 치환만**(동적 발신자 주입 불가).
- 현재 ledger 프로필 → `:8082`(stock-app-v2), schedule 프로필 → `:8086`(mcp-server).
- stock-app-v2 `/mcp` 는 **단일 고정 사용자**(SARI_USER_NO=2, JWT 없음, Tailscale 신뢰망).
- 교체 대상 도구: 가계부 `add_ledger`/`get_recent_ledger`/`analyze_spending_with_claude`(stock-app-v2), 일정 `schedule_*`(mcp-server→schedule-service). **주식 도구는 유지.**

---

## 2. 목표 아키텍처

```
[텔레그램] → [Hermes Agent] (구성원별 프로필+봇) ←→ Claude (Anthropic)
       │ Tailscale 신뢰망 / MCP 다중 등록
       ├─ MCP#1 → 기존 mcp-server(:8086) → stock-app-v2  (주식 전용, 거의 그대로)
       └─ MCP#2 → ★ family-os-mcp(:8088)  [헤더 X-Telegram-User-Id = 멤버 고정]
                       │ REST + X-Service-Token + telegramUserId
                       ▼  stock-network-v2 (docker)
[Cloudflare ledger.saristock.com] → [stock-nginx-v2]
       ├─ /api/  → [family-os-api:8080] ─ [mysql-server-v2: family_os DB]
       └─ /      → [family-os-web:80 (expo static)]
```
신뢰 경계 3겹: Hermes→MCP = **Tailscale** / family-os-mcp→family-os-api = **X-Service-Token** / api↔db = **docker network**.

신규 컨테이너 3개: **family-os-api · family-os-web · family-os-mcp** (별도 compose, stock-network-v2 external 공유).

---

## 3. MCP 토폴로지 — 전용 분리 (결정)

기존 mcp-server(`com.hermes.mcp`)는 주식 도메인에 결합. Family OS는 별도 제품·레포·배포 수명주기 → **전용 `family-os-mcp` 분리**.
- 관심사/소유/버전 분리, 장애 격리, Cloud-Portable, 기존 stock 거의 무수정.
- Hermes가 MCP 다중 등록을 지원하므로 분리 단점 없음.
- 스택: **공식 MCP SDK (TypeScript), Streamable HTTP `/mcp`**. (기존 게이트웨이와 전송 호환)

family-os-mcp 도구(Family OS REST 매핑):
| 도구 | → family-os-api |
|------|------|
| `ledger_add` | POST `/integrations/telegram/transactions` |
| `ledger_stats` | GET `/integrations/telegram/query` |
| `timeline_get` | GET `/integrations/telegram/timeline` |
| `schedule_list` | GET `/integrations/telegram/schedules` |
| `schedule_create/update/delete` | POST/PUT/DELETE `/integrations/telegram/schedules` ✅ 구현됨(TelegramScheduleController/Service) |

요청마다 헤더 `X-Telegram-User-Id` → `telegramUserId` 주입(헤더 없으면 401 fail-closed), 아웃바운드 `X-Service-Token`.

---

## 4. 구성원별 귀속 (모드 B)

목표: 텔레그램 발신자마다 본인 Person 으로 기록·visibility 분리.

제약: Hermes는 메시지별 발신자 id를 MCP 헤더에 **동적 주입 불가**(env 치환만).
→ 결정: **구성원별 프로필+봇 (정적 헤더)**.
- 멤버마다 Hermes 프로필 1개 + 전용 텔레그램 봇.
- 프로필 `.env` 에 멤버 텔레그램 id 보관 → mcp_servers `headers: X-Telegram-User-Id: "${FAMILYOS_TG_UID}"`.
- family-os-mcp 가 그 값을 telegramUserId 로 family-os-api 에 전달. Hermes 코어 무수정.
- 멤버는 앱 텔레그램 연결 UI(구현됨) 또는 `POST /me/telegram` 로 본인 id 1회 등록(telegram_person_map). 미매핑 id → 404.

Hermes 멤버 프로필 `mcp_servers` 예:
```yaml
mcp_servers:
  family-os:
    url: http://localhost:8088/mcp
    enabled: true
    headers:
      X-Telegram-User-Id: "${FAMILYOS_TG_UID}"   # 프로필 .env
    tools:
      include: [ledger_add, ledger_stats, timeline_get, schedule_list, schedule_create, schedule_update, schedule_delete]
```

---

## 5. 인프라 (별도 compose + stock-network-v2 공유)

- **MySQL**: mysql-server-v2 에 `family_os` DB + 전용 user(`family_os`, family_os.* 한정). Flyway(V1__init.sql)가 테이블 생성, `ddl-auto=validate`.
- **네트워크**: `stock-network-v2` (external). family-os-api↔mysql, nginx↔api/web, family-os-mcp↔api 모두 컨테이너명 resolve.
- **nginx**: 서버 `nginx-v2.conf` 가계부 도메인 server 블록 upstream → `/api/`=family-os-api:8080, `/`=family-os-web:80. (`deploy/nginx-family-os.conf` 참고). 롤백=원복+reload. Cloudflare 무변경.
- **포트**: family-os-api 내부 8080(nginx 라우팅) / family-os-mcp 호스트 8088→컨테이너 3000(Hermes 접근, Tailscale).
- **볼륨**: 사진 `/Volumes/ServerData/docker/family-os/photos:/data/photos`(`STORAGE_LOCAL_ROOT=/data/photos`).
- **헬스체크**: actuator 미포함 → TCP/HTTP 단순 체크(또는 actuator 추가는 선택).
- 도메인: `ledger.saristock.com`. 프론트 빌드 시 `EXPO_PUBLIC_API_BASE_URL=https://ledger.saristock.com/api/v1` 주입.

---

## 6. 본 레포 신규 파일 (생성 완료)
```
daily_record/
├── .env.example / .gitignore
├── docker-compose.yml                 (별도 compose, external network)
├── backend/Dockerfile, .dockerignore  (멀티스테이지 bootJar)
├── frontend/Dockerfile, nginx.conf    (expo export static → nginx)
├── mcp/                               (family-os-mcp, 공식 SDK/TS/Streamable HTTP)
│   ├── package.json, tsconfig.json, Dockerfile, .dockerignore
│   └── src/server.ts
├── scripts/family-os-init.sql         (family_os DB/계정)
├── scripts/provision-member.sh        (구성원 자동 프로비저닝)
└── deploy/nginx-family-os.conf, README.md
```
> 실제 `.env`(시크릿)는 커밋 금지(.gitignore). DB 비번은 init.sql 에 박지 않고 .env 에서 주입.

---

## 7. 구현 순서 (10 Phase, 무중단 원칙)
기존 stock 시스템은 Phase 9 전까지 영향 0. 가계부 사용자 전환은 Phase 5(도메인 컷오버) 1회(롤백 즉시).

1. family_os DB/계정 (mysql-server-v2)
2. family-os-api 컨테이너 기동(격리) → Flyway·부트스트랩·로그인 스모크
3. **(백엔드 신규)** 텔레그램 일정 쓰기 엔드포인트(POST/PUT/DELETE `/integrations/telegram/schedules`)
4. family-os-web 빌드
5. **nginx 가계부 도메인 컷오버** (롤백=원복+reload)
6. family-os-mcp 구현·기동(Streamable HTTP, 헤더→telegramUserId, fail-closed)
7. 구성원 telegram_person_map 등록
8. Hermes 멤버 프로필 연결(`provision-member.sh`) + 구성원별 귀속 검증
9. stock 측 정리(가계부/일정 도구 비활성, schedule-service·ledger UI 폐기)
10. (나중) 구글 캘린더 — 폴링부터, 푸시는 공개 URL 갖춘 뒤

의존성: 1→2→(3,4)→5 / 3→6→8 / 7→8 / 8→9 / 10 독립.

---

## 8. 구성원 자동 프로비저닝

봇 생성(BotFather)은 자동화 불가(공개 API 없음) → **봇만 수동, 나머지 자동**.
`scripts/provision-member.sh <member_key> <bot_token> <tg_user_id> <login_id> <password>`:
1. `~/.hermes/profiles/member-template` 복제 → `profiles/<member>` (모델 자격·mcp_servers 구조 상속)
2. `.env` 작성(TELEGRAM_BOT_TOKEN, FAMILYOS_TG_UID)
3. launchd plist 생성 + `launchctl bootstrap` (게이트웨이 기동)
4. 멤버 자격 로그인 → `POST /me/telegram` 매핑

호스트(Mac Mini) 실행 필수(컨테이너는 호스트 launchctl 접근 불가). 사전: `member-template` 프로필 1회 정비.

---

## 9. 후속/미완
- **Phase 3 백엔드 일정 쓰기 엔드포인트** — ✅ 구현 완료(추가 파일만, 기존 무수정): `TelegramScheduleController`(POST/PUT/DELETE `/integrations/telegram/schedules`) + `TelegramScheduleService`(actingAs 패턴) + DTO 2종. 검증은 기존 `ScheduleService` 가 그대로 적용(family 격리·alive 422·VisibilityGuard 404·종일/시점 이원화). 컴파일 확인됨. (권한/격리 테스트 추가는 후속)
- ✅ stock 측 정리(D4) 완료 — **stock 소스/컨테이너 무수정**으로 수행(rebuild 회피):
  - 가계부 도구 비활성: stock-app-v2 `@Component` 대신 **Hermes default 프로필 `tools.include` 에서 가계부 6종 제외**(stock-account 12 selected). stock-app-v2 `/mcp` 자체는 그대로 두되 LLM 에 미노출.
  - schedule Hermes 프로필 폐기(launchctl bootout, plist/프로필 보존이름변경).
  - 잉여 컨테이너 폐기: schedule-service · mcp-server(8086, 사용처 없음) · ledger-app-v2 · (구)stock-ledger-ui → `restart=no`+stop, stock compose 에서 서비스 제거(백업).
  - nginx stock 블록 `/ledger/`·`ledger-app-v2` upstream 제거(백업, restart).
  - 롤백: 각 백업(.bak.familyos-*) 복원 + 컨테이너 재기동.
- 구글 캘린더(docs 08): 폴링 우선, 푸시 webhook 은 공개 URL·Cloudflare 예외 후.

### 구글 캘린더 설정 (컨테이너 — 실서비스 시 필수)
컨테이너 `.env` 에 `GOOGLE_*` 누락 시: sync → **"토큰 복호화 실패"**(빈 `GOOGLE_TOKEN_ENC_KEY`), connect → OAuth URL 생성 불가(빈 `GOOGLE_CLIENT_ID`).
필요 값: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_TOKEN_ENC_KEY`(Base64 32B), `GOOGLE_REDIRECT_URI=https://ledger.saristock.com/api/v1/integrations/google/callback`.
- ★ `GOOGLE_REDIRECT_URI` 는 **Google Cloud Console > 사용자 인증 정보 > OAuth 클라이언트 > "승인된 리디렉션 URI"** 에 **전체 URL(https+경로) 그대로** 등록해야 함. 안 하면 `redirect_uri_mismatch`(400).
- ⚠️ consent 화면의 **"승인된 도메인"** 칸은 `saristock.com` 만(스키마/경로 금지) — 리디렉션 URI 칸과 혼동 주의.
- 연동 해제 시 `refresh_token_enc=NULL` → 재연결 필요. 재연결 후 폴링(15분)·수동 sync 정상.

## 10. 보안/시크릿
- `.env` 커밋 금지(.gitignore). 시크릿 `openssl rand -hex 32`. JWT 2종/STORAGE_URL_SECRET/서비스토큰/DB비번 = 기계 생성.
- `HERMES_SERVICE_TOKEN` == `FAMILYOS_SERVICE_TOKEN`(api↔mcp 동일). family-os-mcp 는 X-Telegram-User-Id 없으면 401.
- 부트스트랩 비번은 첫 로그인용 — 초기값 사용 후 변경 권장.
