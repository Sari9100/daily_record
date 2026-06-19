# Hermes(텔레그램 봇) 연결 가이드 (운영)

> Family OS 백엔드 ↔ Hermes(텔레그램 봇) 연동을 켜는 방법. 설계 근거: `docs/08-integration-design.md` §2.
> 핵심: **Hermes 는 내부 신뢰 클라이언트**다. 사용자 JWT 가 아니라 **서비스 토큰**으로 백엔드를 호출하고,
> 각 호출에 **telegramUserId** 를 실어 보내면 백엔드가 그 텔레그램 사용자에 매핑된 Person 으로 대신 행동한다.
> family 격리·visibility·거래 규칙은 일반 API 와 똑같이 그대로 적용된다.

```
사용자 ──텔레그램──> Hermes(봇)  ──X-Service-Token + telegramUserId──>  Family OS 백엔드
                     (LLM 파싱·확인 게이트)                            (telegram_user_id → Person 대행)
```

---

## 1. 백엔드: 서비스 토큰 설정 (필수)

`backend/.env` 에 내부 API 전용 시크릿을 넣는다. **비어 있으면 텔레그램 내부 API 는 전부 401(fail-closed)** 이다.

```bash
# 임의의 강한 시크릿 (예시 생성: openssl rand -hex 32). 고정값으로 넣을 것.
HERMES_SERVICE_TOKEN=<랜덤_32바이트_이상_시크릿>
```

설정 후 백엔드 재기동:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew bootRun
```

- 헤더 이름은 **`X-Service-Token`** (상수시간 비교). 이 값이 `HERMES_SERVICE_TOKEN` 과 정확히 일치해야 한다.
- 이 토큰은 봇 서버에만 두고 **절대 클라/깃에 노출 금지**.

---

## 2. 텔레그램 봇 생성 (BotFather)

1. 텔레그램에서 **@BotFather** 대화 → `/newbot` → 이름/username 지정.
2. 발급된 **봇 토큰**(`123456:ABC-...`)을 받아둔다. (Hermes 가 텔레그램과 통신할 때 사용)

---

## 3. Hermes(봇 서비스) 설정

Hermes 는 별도 서비스(기존 자산 확장). 아래 값을 Hermes 환경변수로 설정한다:

| 키(예시) | 값 | 용도 |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | 2단계의 봇 토큰 | 텔레그램 수신/응답 |
| `FAMILYOS_API_BASE_URL` | `http://<백엔드호스트>:8080/api/v1` | 백엔드 내부 API 베이스 |
| `FAMILYOS_SERVICE_TOKEN` | 1단계 `HERMES_SERVICE_TOKEN` 과 **동일** | `X-Service-Token` 헤더로 전송 |
| (선택) `LLM_API_KEY` | Claude/GPT/Gemini 키 | 영수증 이미지·자연어 파싱 |

> 백엔드와 Hermes 가 다른 머신이면 `FAMILYOS_API_BASE_URL` 은 localhost 가 아니라 백엔드의 실제 IP/도메인.
> 로컬 동일 머신이면 `http://localhost:8080/api/v1`.

Hermes 의 책임(봇 쪽 로직):
- 텍스트("스타벅스 5000원") / 영수증 이미지 → **LLM 파싱** → 금액·상점·날짜 추출
- **확인 게이트**("이대로 등록할까요?") 후에만 백엔드 `POST .../transactions` 호출 (human-in-the-loop)
- 조회 질의("이번 달 식비?", "내일 일정?")는 아래 GET 엔드포인트 호출 후 결과 포맷팅

---

## 4. 구성원 ↔ 텔레그램 계정 연결 (1인 1회, 필수)

백엔드는 **telegram_user_id → Person** 매핑이 있어야 그 사람으로 대행한다. 각 가족 구성원이 **본인 로그인 상태(JWT)** 로 본인 텔레그램 숫자 ID 를 연결한다(타인 위장 방지 — 토큰 주인에게만 매핑).

1. 본인의 **텔레그램 숫자 user id** 확인: 봇에게 메시지를 보내면 Hermes 가 `update.message.from.id` 로 알 수 있다.
   (봇에 `/myid` 같은 명령을 두어 사용자에게 자기 id 를 알려주면 편하다.)
2. 앱에 로그인한 상태에서 본인 계정에 연결:

```bash
# $TOKEN = 본인 access token (앱 로그인)
curl -s -X POST localhost:8080/api/v1/me/telegram \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"telegramUserId": 123456789}'
```

- 해제: `curl -X DELETE localhost:8080/api/v1/me/telegram -H "Authorization: Bearer $TOKEN"`
- (프론트 미구현) 현재 앱엔 연결 UI 가 없다 — 위 curl 로 연결하거나, 추후 "더보기 → 외부연동"에 텔레그램 연결 화면을 추가하면 된다.

---

## 5. Hermes 가 호출하는 내부 API 계약

모든 요청에 헤더 **`X-Service-Token: <HERMES_SERVICE_TOKEN>`** 필수. `telegramUserId` 로 행동 주체 결정.
응답은 공통 래퍼 `{success, data, error}`.

| 메서드 | 경로 | 용도 | 핵심 파라미터/본문 |
|---|---|---|---|
| POST | `/integrations/telegram/transactions` | 거래 등록(source=TELEGRAM 강제) | body: `{ telegramUserId, transaction: {TransactionRequest} }` |
| GET | `/integrations/telegram/query` | 통계 조회 | `telegramUserId, from?, to?(Instant), scope?` |
| GET | `/integrations/telegram/timeline` | 통합 타임라인 | `telegramUserId, date? | from?,to?(날짜)` |
| GET | `/integrations/telegram/schedules` | 일정 조회 | `telegramUserId, from?,to?(Instant), type?, scope?` |

`transaction`(TransactionRequest) 형식 — 거래유형-계좌 규칙 그대로 적용:
- `transactionType`: `EXPENSE`(출금=sourceAccountId만) / `INCOME`(입금=targetAccountId만) / `TRANSFER`(둘 다, 서로 다름)
- `amount`(>0), `currency?`, `categoryId?`, `visibility`(PRIVATE/PARENTS/FAMILY), `occurredAt`(ISO8601 UTC), `memo?`
- `source` 는 보내도 무시되고 서버가 `TELEGRAM` 으로 강제.

### 거래 등록 예시 (curl 스모크 테스트)
```bash
SVC=<HERMES_SERVICE_TOKEN>
curl -s -X POST localhost:8080/api/v1/integrations/telegram/transactions \
  -H "X-Service-Token: $SVC" -H 'Content-Type: application/json' \
  -d '{
        "telegramUserId": 123456789,
        "transaction": {
          "transactionType": "EXPENSE",
          "amount": 5000,
          "currency": "KRW",
          "sourceAccountId": 1,
          "visibility": "PRIVATE",
          "occurredAt": "2026-06-20T01:00:00Z",
          "memo": "스타벅스"
        }
      }' | python3 -m json.tool
```

### 조회 예시
```bash
# 이번 달 통계
curl -s "localhost:8080/api/v1/integrations/telegram/query?telegramUserId=123456789&from=2026-06-01T00:00:00Z&to=2026-06-30T23:59:59Z" \
  -H "X-Service-Token: $SVC" | python3 -m json.tool

# 오늘 타임라인
curl -s "localhost:8080/api/v1/integrations/telegram/timeline?telegramUserId=123456789&date=2026-06-20" \
  -H "X-Service-Token: $SVC" | python3 -m json.tool

# 내일 일정
curl -s "localhost:8080/api/v1/integrations/telegram/schedules?telegramUserId=123456789&from=2026-06-21T00:00:00Z&to=2026-06-21T23:59:59Z" \
  -H "X-Service-Token: $SVC" | python3 -m json.tool
```

> 인증 실패(토큰 누락/불일치)는 401, 매핑 안 된 telegramUserId 는 404("연결된 텔레그램 사용자가 없습니다").

---

## 6. 보안·운영 메모

- `HERMES_SERVICE_TOKEN` 은 봇 서버에만. 노출 시 즉시 교체(양쪽 동시 변경 후 재기동).
- 토큰만 맞으면 누구나 호출 가능하므로 **내부망/방화벽 또는 신뢰 네트워크**에서만 노출. 공개 인터넷에 그대로 열지 말 것.
- 대행이라도 **family 격리·visibility·거래유형 규칙은 그대로** 적용된다(매핑된 Person 권한 범위 내에서만 동작).
- 영수증/자연어 파싱은 **Hermes 쪽 LLM** 책임. 백엔드는 검증된 최종 값만 받는다. 반드시 **확인 게이트** 후 등록.
- source=TELEGRAM 으로 기록돼 입력 출처를 구분할 수 있다.

---

## 7. 연결 체크리스트

1. [ ] `backend/.env` 에 `HERMES_SERVICE_TOKEN` 설정 → 백엔드 재기동
2. [ ] BotFather 로 봇 생성 → 봇 토큰 확보
3. [ ] Hermes 에 봇 토큰 / API base URL / 서비스 토큰 설정
4. [ ] 각 구성원 `POST /me/telegram` 으로 telegram_user_id 연결
5. [ ] `curl` 스모크 테스트(거래 등록 1건) 성공 확인
6. [ ] Hermes 에서 텍스트→파싱→확인→등록 / 조회 플로우 연결
