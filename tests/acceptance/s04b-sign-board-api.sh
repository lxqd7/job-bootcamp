#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
MARKETING="${MARKETING:-http://127.0.0.1:18087}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
REDIS_CLI="${REDIS_CLI:-D:/Develop/JAVA/IdeaProjects/tjxt-ai-master/.local-migration/native-windows/redis/Redis-7.4.2-Windows-x64-msys2/redis-cli.exe}"
TMP_DIR="$(mktemp -d)"
STAMP="$(date +%s)"
BASE_USER=$((800000 + STAMP % 100000))
BOARD_MONTH="2019-01"
CURRENT_MONTH=$(node -e 'const p=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Shanghai",year:"numeric",month:"2-digit"}).format(new Date()); console.log(p)')
YESTERDAY=$(node -e '
  const now = new Date();
  const sh = new Date(now.toLocaleString("en-US", {timeZone: "Asia/Shanghai"}));
  sh.setDate(sh.getDate() - 1);
  const y = sh.getFullYear(), m = String(sh.getMonth()+1).padStart(2,"0"), d = String(sh.getDate()).padStart(2,"0");
  console.log(`${y}-${m}-${d}`);
')
PREV_MONTH_END=$(node -e '
  const now = new Date();
  const sh = new Date(now.toLocaleString("en-US", {timeZone: "Asia/Shanghai"}));
  const d = new Date(Date.UTC(sh.getFullYear(), sh.getMonth(), 0));
  const y = d.getUTCFullYear(), m = String(d.getUTCMonth()+1).padStart(2,"0"), day = String(d.getUTCDate()).padStart(2,"0");
  console.log(`${y}-${m}-${day}`);
')

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

field() {
  node -e '
    const fs = require("fs");
    const data = JSON.parse(fs.readFileSync(0, "utf8"));
    let value = data;
    for (const key of process.argv[1].split(".")) {
      value = value == null ? undefined : value[key];
    }
    if (Array.isArray(value)) { console.log(JSON.stringify(value)); }
    else { console.log(value == null ? "" : value); }
  ' "$1"
}

expect_status() {
  local expected="$1" actual="$2" label="$3" body="$4"
  if [ "$actual" = "$expected" ]; then
    echo "PASS  $label  (HTTP $actual)"
  else
    echo "FAIL  $label  expected HTTP $expected, got $actual"
    echo "--- response ---"; cat "$body" 2>/dev/null || true; echo
    exit 1
  fi
}

expect_value() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    echo "PASS  $label  ($actual)"
  else
    echo "FAIL  $label  expected '$expected', got '$actual'"
    exit 1
  fi
}

sign_fixture() {
  local user_id="$1" out="$2"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"userId\":$user_id}" "$MARKETING/dev/points/fixtures/sign")
  expect_status 200 "$status" "sign fixture user=$user_id" "$out"
}

seed_sign_days() {
  local user_id="$1" days="$2" end_date="$3" out="$4"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"userId\":$user_id,\"days\":$days,\"endDate\":\"$end_date\"}" \
    "$MARKETING/dev/points/fixtures/sign-days")
  expect_status 200 "$status" "seed $days sign days for user=$user_id" "$out"
}

state() {
  local user_id="$1" out="$2"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' "$MARKETING/dev/points/fixtures/accounts/$user_id")
  expect_status 200 "$status" "read test state user=$user_id" "$out"
}

login() {
  local username="$1" app="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
}

echo "== Redis and API access =="
if [ ! -x "$REDIS_CLI" ]; then
  echo "FAIL  redis-cli not found: $REDIS_CLI"; exit 1
fi
PING=$("$REDIS_CLI" -p 6379 ping 2>/dev/null || true)
expect_value "PONG" "$PING" "Redis is reachable"

login student01 STUDENT "$TMP_DIR/student01.json"
login student02 STUDENT "$TMP_DIR/student02.json"
login admin01 ADMIN "$TMP_DIR/admin01.json"
T1=$(field data.accessToken < "$TMP_DIR/student01.json")
T2=$(field data.accessToken < "$TMP_DIR/student02.json")
TA=$(field data.accessToken < "$TMP_DIR/admin01.json")

NO_TOKEN_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/boards/monthly")
expect_status 401 "$NO_TOKEN_STATUS" "board requires login" /dev/null
ADMIN_BOARD_BODY="$TMP_DIR/admin-board.json"
ADMIN_BOARD_STATUS=$(curl -s -o "$ADMIN_BOARD_BODY" -w '%{http_code}' -H "Authorization: Bearer $TA" "$BASE/api/v1/boards/monthly?month=$BOARD_MONTH")
expect_status 200 "$ADMIN_BOARD_STATUS" "admin can view board" "$ADMIN_BOARD_BODY"
ADMIN_SIGN_BODY="$TMP_DIR/admin-sign.json"
ADMIN_SIGN_STATUS=$(curl -s -o "$ADMIN_SIGN_BODY" -w '%{http_code}' -X POST -H "Authorization: Bearer $TA" "$BASE/api/v1/sign-ins")
expect_status 403 "$ADMIN_SIGN_STATUS" "admin cannot sign" "$ADMIN_SIGN_BODY"

echo
echo "== Daily sign, idempotency and bonuses =="
USER_SIGN=$((BASE_USER + 1))
SIGN_1="$TMP_DIR/sign-1.json"
STATUS_1=$(curl -s -o "$SIGN_1" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d "{\"userId\":$USER_SIGN}" "$MARKETING/dev/points/fixtures/sign")
expect_status 200 "$STATUS_1" "first daily sign" "$SIGN_1"
expect_value "1" "$(field data.basePoints < "$SIGN_1")" "base sign points is 1"
expect_value "0" "$(field data.bonusPoints < "$SIGN_1")" "day 1 has no bonus"
expect_value "1" "$(field data.continuousDays < "$SIGN_1")" "day 1 starts a streak"
SIGN_2="$TMP_DIR/sign-2.json"
sign_fixture "$USER_SIGN" "$SIGN_2"
expect_value "true" "$(field data.alreadySigned < "$SIGN_2")" "same day second sign is idempotent"
STATE_SIGN="$TMP_DIR/state-sign.json"
state "$USER_SIGN" "$STATE_SIGN"
expect_value "1" "$(field data.ledgerCount < "$STATE_SIGN")" "same day creates one ledger row"
expect_value "1" "$(field data.available < "$STATE_SIGN")" "same day awards only one point"

for spec in "7 6 11" "14 13 21" "28 27 41"; do
  set -- $spec
  USER_BONUS=$((BASE_USER + 10 + $1))
  seed_sign_days "$USER_BONUS" "$2" "$YESTERDAY" "$TMP_DIR/seed-$1.json"
  sign_fixture "$USER_BONUS" "$TMP_DIR/bonus-$1.json"
  expect_value "$1" "$(field data.continuousDays < "$TMP_DIR/bonus-$1.json")" "continuous day $1"
  expect_value "$3" "$(field data.rewardPoints < "$TMP_DIR/bonus-$1.json")" "day $1 awards $3 points"
done

USER_MONTH=$((BASE_USER + 50))
seed_sign_days "$USER_MONTH" 5 "$PREV_MONTH_END" "$TMP_DIR/seed-month.json"
sign_fixture "$USER_MONTH" "$TMP_DIR/sign-month.json"
expect_value "1" "$(field data.continuousDays < "$TMP_DIR/sign-month.json")" "new month resets streak to day 1"
expect_value "1" "$(field data.rewardPoints < "$TMP_DIR/sign-month.json")" "new month has no stale bonus"

echo
echo "== 100 concurrent same-day sign requests =="
USER_CONCURRENT=$((BASE_USER + 100))
for i in $(seq 1 100); do
  curl -s -X POST -H 'Content-Type: application/json' \
    -d "{\"userId\":$USER_CONCURRENT}" "$MARKETING/dev/points/fixtures/sign" \
    > "$TMP_DIR/concurrent-sign-$i.json" &
done
wait
OK_COUNT=$(grep -l '"code":"OK"' "$TMP_DIR"/concurrent-sign-*.json | wc -l | tr -d ' ')
ALREADY_COUNT=$(grep -l '"alreadySigned":true' "$TMP_DIR"/concurrent-sign-*.json | wc -l | tr -d ' ')
expect_value "100" "$OK_COUNT" "all 100 sign requests return success"
expect_value "99" "$ALREADY_COUNT" "exactly one request actually signs"
STATE_CONCURRENT="$TMP_DIR/state-concurrent.json"
state "$USER_CONCURRENT" "$STATE_CONCURRENT"
expect_value "1" "$(field data.ledgerCount < "$STATE_CONCURRENT")" "100 requests create one sign ledger"
expect_value "1" "$(field data.available < "$STATE_CONCURRENT")" "100 requests award one point"

echo
echo "== Monthly board, ties and student filter =="
for pair in "1 100" "2 100" "3 50" "6 999"; do
  set -- $pair
  SCORE_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"userId\":$1,\"month\":\"$BOARD_MONTH\",\"score\":$2}" \
    "$MARKETING/dev/points/fixtures/month-score")
  expect_status 200 "$SCORE_STATUS" "seed board score user=$1" /dev/null
done

"$REDIS_CLI" -p 6379 DEL \
  "bc:dev:board:$BOARD_MONTH:rank" \
  "bc:dev:board:$BOARD_MONTH:names" \
  "bc:dev:board:$BOARD_MONTH:revision" >/dev/null

BOARD_1="$TMP_DIR/board-1.json"
BOARD_1_STATUS=$(curl -s -o "$BOARD_1" -w '%{http_code}' -H "Authorization: Bearer $T1" \
  "$BASE/api/v1/boards/monthly?month=$BOARD_MONTH&page=1&pageSize=20")
expect_status 200 "$BOARD_1_STATUS" "student board query" "$BOARD_1"
expect_value "3" "$(field data.total < "$BOARD_1")" "only students with positive score participate"
expect_value "1" "$(field data.myRank < "$BOARD_1")" "student01 rank is returned"
BOARD_ORDER=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  console.log(d.data.items.map(i=>`${i.score}:${i.rank}`).join("|"));
' "$BOARD_1")
expect_value "100:1|100:2|50:3" "$BOARD_ORDER" "tie 100/100 ordered by user id, admin excluded"

BOARD_2="$TMP_DIR/board-2.json"
BOARD_2_STATUS=$(curl -s -o "$BOARD_2" -w '%{http_code}' -H "Authorization: Bearer $T2" \
  "$BASE/api/v1/boards/monthly?month=$BOARD_MONTH&page=1&pageSize=20")
expect_status 200 "$BOARD_2_STATUS" "student02 board query" "$BOARD_2"
expect_value "2" "$(field data.myRank < "$BOARD_2")" "student02 is second on the tie"

for suffix in rank names revision; do
  expect_value "1" "$("$REDIS_CLI" -p 6379 exists "bc:dev:board:$BOARD_MONTH:$suffix" | tr -d '\r')" "Redis board $suffix exists"
done

SCORE_CHANGE_STATUS=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d "{\"userId\":3,\"month\":\"$BOARD_MONTH\",\"score\":200}" \
  "$MARKETING/dev/points/fixtures/month-score")
expect_status 200 "$SCORE_CHANGE_STATUS" "change one board score" /dev/null
BOARD_3="$TMP_DIR/board-3.json"
BOARD_3_STATUS=$(curl -s -o "$BOARD_3" -w '%{http_code}' -H "Authorization: Bearer $T1" \
  "$BASE/api/v1/boards/monthly?month=$BOARD_MONTH&page=1&pageSize=20")
expect_status 200 "$BOARD_3_STATUS" "board query after score change" "$BOARD_3"
BOARD_ORDER_2=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  console.log(d.data.items.map(i=>`${i.score}:${i.rank}`).join("|"));
' "$BOARD_3")
expect_value "200:1|100:2|100:3" "$BOARD_ORDER_2" "board cache refreshes after score revision"
expect_value "2" "$(field data.myRank < "$BOARD_3")" "student01 myRank updates after score change"

echo
echo "== Sign bitmap rebuild =="
SIGN_STUDENT_BODY="$TMP_DIR/student02-sign.json"
SIGN_STUDENT_CODE=$(curl -s -o "$SIGN_STUDENT_BODY" -w '%{http_code}' -X POST -H "Authorization: Bearer $T2" "$BASE/api/v1/sign-ins")
expect_status 200 "$SIGN_STUDENT_CODE" "student signs via gateway" "$SIGN_STUDENT_BODY"
"$REDIS_CLI" -p 6379 DEL "bc:dev:sign:2:$CURRENT_MONTH" >/dev/null
SIGN_STATUS_BODY="$TMP_DIR/student02-sign-status.json"
SIGN_STATUS_CODE=$(curl -s -o "$SIGN_STATUS_BODY" -w '%{http_code}' -H "Authorization: Bearer $T2" "$BASE/api/v1/sign-ins")
expect_status 200 "$SIGN_STATUS_CODE" "student sign status via gateway" "$SIGN_STATUS_BODY"
expect_value "1" "$("$REDIS_CLI" -p 6379 exists "bc:dev:sign:2:$CURRENT_MONTH" | tr -d '\r')" "sign bitmap rebuilt from MySQL"

echo
echo "ALL S04-b SIGN AND BOARD CHECKS PASSED"