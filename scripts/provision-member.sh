#!/usr/bin/env bash
# 구성원 자동 프로비저닝 — Hermes 프로필 생성 + launchd 등록 + telegram_person_map 매핑
# 설계: scratchpad/member-provisioning.md
#
# 봇 생성(BotFather)만 수동. 그 외(프로필/plist/매핑)는 이 스크립트가 자동 수행.
# 실행 위치: 호스트(Mac Mini). family-os-api 컨테이너는 호스트 launchctl 접근 불가하므로 호스트에서 실행.
#
# 사용:
#   scripts/provision-member.sh <member_key> <bot_token> <tg_user_id> <member_login_id> <member_password>
# 사전:
#   - ~/.hermes/profiles/member-template 프로필이 1회 정비돼 있어야 함
#     (모델/provider/auth.json + mcp_servers.family-os(url=http://localhost:8088/mcp,
#      headers X-Telegram-User-Id=${FAMILYOS_TG_UID}, tools.include 목록))
#   - family-os 구성원 계정이 먼저 생성돼 있어야 함(login_id/password)

set -euo pipefail

if [ "$#" -ne 5 ]; then
  echo "usage: $0 <member_key> <bot_token> <tg_user_id> <member_login_id> <member_password>" >&2
  exit 2
fi

MEMBER="$1"; BOT_TOKEN="$2"; TG_UID="$3"; LOGIN_ID="$4"; PW="$5"

HERMES_HOME_BASE="$HOME/.hermes"
PROFILES="$HERMES_HOME_BASE/profiles"
TEMPLATE="$PROFILES/member-template"
DEST="$PROFILES/$MEMBER"
VENV="$HERMES_HOME_BASE/hermes-agent/venv"
PLIST="$HOME/Library/LaunchAgents/ai.hermes.gateway-$MEMBER.plist"

# 가계부 도메인(또는 내부 주소). family-os-api REST base.
API="${FAMILYOS_API_BASE:-https://ledger.saristock.com/api/v1}"

[ -d "$TEMPLATE" ] || { echo "템플릿 프로필 없음: $TEMPLATE" >&2; exit 1; }
[ -d "$DEST" ] && { echo "이미 존재: $DEST" >&2; exit 1; }

# a. 템플릿 복제 (모델 자격·mcp_servers 구조 상속)
cp -R "$TEMPLATE" "$DEST"
rm -rf "$DEST/logs"; mkdir -p "$DEST/logs"

# b. .env — 봇토큰 + 멤버 telegram id (config.yaml headers의 ${FAMILYOS_TG_UID} 참조)
cat > "$DEST/.env" <<EOF
TELEGRAM_BOT_TOKEN=$BOT_TOKEN
FAMILYOS_TG_UID=$TG_UID
EOF

# c. launchd plist 생성
cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>ai.hermes.gateway-$MEMBER</string>
  <key>ProgramArguments</key><array>
    <string>$VENV/bin/python</string><string>-m</string><string>hermes_cli.main</string>
    <string>--profile</string><string>$MEMBER</string>
    <string>gateway</string><string>run</string><string>--replace</string>
  </array>
  <key>WorkingDirectory</key><string>$HERMES_HOME_BASE/hermes-agent</string>
  <key>EnvironmentVariables</key><dict>
    <key>VIRTUAL_ENV</key><string>$VENV</string>
    <key>HERMES_HOME</key><string>$DEST</string>
    <key>PATH</key><string>$VENV/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>
  </dict>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><dict><key>SuccessfulExit</key><false/></dict>
  <key>StandardOutPath</key><string>$DEST/logs/gateway.log</string>
  <key>StandardErrorPath</key><string>$DEST/logs/gateway.error.log</string>
</dict></plist>
EOF

# d. 게이트웨이 기동 (launchd)
launchctl bootstrap "gui/$(id -u)" "$PLIST" 2>/dev/null || launchctl load "$PLIST"
echo "✓ Hermes 프로필 기동: $MEMBER"

# e. telegram_person_map 매핑 — 멤버 자격으로(토큰 주인만 매핑 규칙 준수)
TOKEN="$(curl -fsS -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d "{\"loginId\":\"$LOGIN_ID\",\"password\":\"$PW\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')"

curl -fsS -X POST "$API/me/telegram" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"telegramUserId\": $TG_UID}" >/dev/null

echo "✓ telegram 매핑 완료: $MEMBER (tg=$TG_UID)"
echo "완료. (auth/login·me/telegram 응답 필드는 실제 AuthController/LoginResponse 기준으로 확인)"
