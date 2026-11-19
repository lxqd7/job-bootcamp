#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
MARKETING="${MARKETING:-http://127.0.0.1:18087}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
TMP_DIR="$(mktemp -d)"
STAMP="$(date +%s)"
BASE_USER=$((100000 + STAMP % 700000))
BASE_LESSON=$((900000 + STAMP % 700000))
CURRENT_MONTH=$(node -e 'const p=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Shanghai",year:"numeric",month:"2-digit"}).format(new Date()); console.log(p)')
NOW_UTC=$(date -u +%Y-%m-%dT%H:%M:%SZ)

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

uuid() { node -e 'console.log(crypto.randomUUID())'; }

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

reward() {
  local event_id="$1" user_id="$2" lesson_id="$3" points="$4" occurred_at="$5" out="$6"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"$event_id\",\"userId\":$user_id,\"lessonId\":$lesson_id,\"points\":$points,\"occurredAt\":\"$occurred_at\"}" \
    "$MARKETING/dev/points/rewards")
  expect_status 200 "$status" "reward user=$user_id lesson=$lesson_id" "$out"
}

state() {
  local user_id="$1" out="$2" status
  status=$(curl -s -o "$out" -w '%{http_code}' "$MARKETING/dev/points/fixtures/accounts/$user_id")
  expect_status 200 "$status" "read test state user=$user_id" "$out"
}

echo "== Auth and student points API =="
STUDENT_LOGIN="$TMP_DIR/student-login.json"
STUDENT_STATUS=$(curl -s -o "$STUDENT_LOGIN" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"student01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$STUDENT_STATUS" "student login" "$STUDENT_LOGIN"
STUDENT_TOKEN=$(field data.accessToken < "$STUDENT_LOGIN")

SUMMARY_BODY="$TMP_DIR/summary.json"
SUMMARY_STATUS=$(curl -s -o "$SUMMARY_BODY" -w '%{http_code}' -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/me/points")
expect_status 200 "$SUMMARY_STATUS" "student points summary via gateway" "$SUMMARY_BODY"
for key in available debt currentMonthScore; do
  value=$(field "data.$key" < "$SUMMARY_BODY")
  if [ -z "$value" ]; then echo "FAIL  summary missing $key"; exit 1; fi
done
echo "PASS  summary contains available/debt/currentMonthScore"

LEDGER_BODY="$TMP_DIR/ledger.json"
LEDGER_STATUS=$(curl -s -o "$LEDGER_BODY" -w '%{http_code}' -H "Authorization: Bearer $STUDENT_TOKEN" \
  "$BASE/api/v1/me/points/ledger?page=1&pageSize=5")
expect_status 200 "$LEDGER_STATUS" "student points ledger via gateway" "$LEDGER_BODY"
LEDGER_ITEMS=$(field data.items < "$LEDGER_BODY")
if [ "$LEDGER_ITEMS" = "[]" ] || [ -n "$LEDGER_ITEMS" ]; then
  echo "PASS  ledger returns an items array"
else
  echo "FAIL  ledger items missing"; exit 1
fi

ADMIN_LOGIN="$TMP_DIR/admin-login.json"
ADMIN_STATUS=$(curl -s -o "$ADMIN_LOGIN" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"ADMIN\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$ADMIN_STATUS" "admin login" "$ADMIN_LOGIN"
ADMIN_TOKEN=$(field data.accessToken < "$ADMIN_LOGIN")
ADMIN_POINTS_BODY="$TMP_DIR/admin-points.json"
ADMIN_POINTS_STATUS=$(curl -s -o "$ADMIN_POINTS_BODY" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/me/points")
expect_status 403 "$ADMIN_POINTS_STATUS" "admin cannot read student points" "$ADMIN_POINTS_BODY"
NO_TOKEN_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/me/points")
expect_status 401 "$NO_TOKEN_STATUS" "points endpoint requires login" /dev/null

echo
echo "== Reward, duplicate event and duplicate business key =="
USER_A=$((BASE_USER + 1))
EVENT_A=$(uuid)
REWARD_A="$TMP_DIR/reward-a.json"
reward "$EVENT_A" "$USER_A" $((BASE_LESSON + 1)) 10 "$NOW_UTC" "$REWARD_A"
expect_value "10" "$(field data.available < "$REWARD_A")" "first reward adds 10 to balance"
expect_value "10" "$(field data.monthScore < "$REWARD_A")" "first reward adds 10 to month score"
expect_value "false" "$(field data.ledgerDuplicate < "$REWARD_A")" "first reward is not a duplicate"

REWARD_DUP="$TMP_DIR/reward-dup.json"
reward "$EVENT_A" "$USER_A" $((BASE_LESSON + 1)) 10 "$NOW_UTC" "$REWARD_DUP"
expect_value "true" "$(field data.inboxDuplicate < "$REWARD_DUP")" "same eventId is ignored by inbox"
expect_value "true" "$(field data.ledgerDuplicate < "$REWARD_DUP")" "same eventId returns original ledger"

REWARD_OTHER_EVENT="$TMP_DIR/reward-other-event.json"
reward "$(uuid)" "$USER_A" $((BASE_LESSON + 1)) 10 "$NOW_UTC" "$REWARD_OTHER_EVENT"
expect_value "true" "$(field data.ledgerDuplicate < "$REWARD_OTHER_EVENT")" "same lesson with another eventId is deduplicated by ledger key"

STATE_A="$TMP_DIR/state-a.json"
state "$USER_A" "$STATE_A"
expect_value "1" "$(field data.ledgerCount < "$STATE_A")" "duplicate events produced one ledger row"
expect_value "10" "$(field data.available < "$STATE_A")" "duplicate events did not double the balance"

EVENT_CONFLICT_BODY="$TMP_DIR/event-conflict.json"
EVENT_CONFLICT_STATUS=$(curl -s -o "$EVENT_CONFLICT_BODY" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"$EVENT_A\",\"userId\":$USER_A,\"lessonId\":$((BASE_LESSON + 2)),\"points\":10,\"occurredAt\":\"$NOW_UTC\"}" \
  "$MARKETING/dev/points/rewards")
expect_status 409 "$EVENT_CONFLICT_STATUS" "same eventId with another lesson is rejected" "$EVENT_CONFLICT_BODY"
STATE_A2="$TMP_DIR/state-a2.json"
state "$USER_A" "$STATE_A2"
expect_value "1" "$(field data.ledgerCount < "$STATE_A2")" "conflicting eventId did not create another ledger"

echo
echo "== Daily cap =="
USER_B=$((BASE_USER + 2))
for i in 1 2 3 4 5 6; do
  reward "$(uuid)" "$USER_B" $((BASE_LESSON + 100 + i)) 10 "$NOW_UTC" "$TMP_DIR/cap-$i.json"
done
STATE_B="$TMP_DIR/state-b.json"
state "$USER_B" "$STATE_B"
expect_value "50" "$(field data.available < "$STATE_B")" "six video rewards are capped at 50"
expect_value "6" "$(field data.ledgerCount < "$STATE_B")" "cap-skipped lessons still keep ledger rows"
expect_value "50" "$(field data.currentMonthScore < "$STATE_B")" "month score stops at the daily cap"
CAP_SKIPPED=$(grep -l 'SKIPPED_DAILY_CAP' "$TMP_DIR"/cap-*.json | wc -l | tr -d ' ')
expect_value "1" "$CAP_SKIPPED" "exactly one reward is recorded as SKIPPED_DAILY_CAP"

echo
echo "== Debt offset and Beijing month attribution =="
USER_C=$((BASE_USER + 3))
DEBT_BODY="$TMP_DIR/debt.json"
DEBT_STATUS=$(curl -s -o "$DEBT_BODY" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d "{\"userId\":$USER_C,\"debt\":3}" "$MARKETING/dev/points/fixtures/debt")
expect_status 200 "$DEBT_STATUS" "seed pending debt for test user" "$DEBT_BODY"

OCT_REWARD="$TMP_DIR/october-reward.json"
reward "$(uuid)" "$USER_C" $((BASE_LESSON + 200)) 10 "2026-10-31T15:30:00Z" "$OCT_REWARD"
expect_value "7" "$(field data.available < "$OCT_REWARD")" "debt 3 offsets a 10 point reward"
expect_value "0" "$(field data.debt < "$OCT_REWARD")" "debt is cleared after offset"
expect_value "2026-10-31" "$(field data.businessDate < "$OCT_REWARD")" "23:30 Beijing stays on October 31"
expect_value "2026-10" "$(field data.businessMonth < "$OCT_REWARD")" "late October event belongs to October"

NOV_REWARD="$TMP_DIR/november-reward.json"
reward "$(uuid)" "$USER_C" $((BASE_LESSON + 201)) 10 "2026-10-31T16:30:00Z" "$NOV_REWARD"
expect_value "2026-11-01" "$(field data.businessDate < "$NOV_REWARD")" "00:30 Beijing rolls into November 1"
expect_value "2026-11" "$(field data.businessMonth < "$NOV_REWARD")" "early November event belongs to November"
expect_value "10" "$(field data.monthScore < "$NOV_REWARD")" "November score is independent from October"

echo
echo "== Concurrent idempotency =="
USER_D=$((BASE_USER + 4))
EVENT_D="$(uuid)"
for i in $(seq 1 10); do
  curl -s -X POST -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"$EVENT_D\",\"userId\":$USER_D,\"lessonId\":$((BASE_LESSON + 300)),\"points\":10,\"occurredAt\":\"2026-11-20T04:00:00Z\"}" \
    "$MARKETING/dev/points/rewards" > "$TMP_DIR/concurrent-same-event-$i.log" &
done
wait
STATE_D="$TMP_DIR/state-d.json"
state "$USER_D" "$STATE_D"
expect_value "1" "$(field data.ledgerCount < "$STATE_D")" "10 concurrent same-event requests create one ledger"
expect_value "10" "$(field data.available < "$STATE_D")" "concurrent same-event requests add points once"

USER_E=$((BASE_USER + 5))
for i in $(seq 1 6); do
  curl -s -X POST -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"$(uuid)\",\"userId\":$USER_E,\"lessonId\":$((BASE_LESSON + 400 + i)),\"points\":10,\"occurredAt\":\"2026-11-20T05:00:00Z\"}" \
    "$MARKETING/dev/points/rewards" > "$TMP_DIR/concurrent-cap-$i.log" &
done
wait
STATE_E="$TMP_DIR/state-e.json"
state "$USER_E" "$STATE_E"
expect_value "50" "$(field data.available < "$STATE_E")" "concurrent rewards never exceed the 50 point day cap"
expect_value "6" "$(field data.ledgerCount < "$STATE_E")" "concurrent cap test keeps six ledger rows"
CAP_RESPONSES=$(grep -l 'SKIPPED_DAILY_CAP' "$TMP_DIR"/concurrent-cap-*.log | wc -l | tr -d ' ')
expect_value "1" "$CAP_RESPONSES" "one concurrent reward is skipped by the cap"

USER_F=$((BASE_USER + 6))
for i in 1 2; do
  curl -s -X POST -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"$(uuid)\",\"userId\":$USER_F,\"lessonId\":$((BASE_LESSON + 500)),\"points\":10,\"occurredAt\":\"2026-11-20T06:00:00Z\"}" \
    "$MARKETING/dev/points/rewards" > "$TMP_DIR/concurrent-same-lesson-$i.log" &
done
wait
STATE_F="$TMP_DIR/state-f.json"
state "$USER_F" "$STATE_F"
expect_value "1" "$(field data.ledgerCount < "$STATE_F")" "two events for one lesson serialize to one ledger"

echo
echo "== Outbox and inbox =="
USER_G=$((BASE_USER + 7))
for i in 1 2 3; do
  reward "$(uuid)" "$USER_G" $((BASE_LESSON + 600 + i)) 10 "2026-11-20T07:00:00Z" "$TMP_DIR/outbox-$i.json"
done
STATE_G="$TMP_DIR/state-g.json"
state "$USER_G" "$STATE_G"
expect_value "3" "$(field data.outboxPending < "$STATE_G")" "each reward writes a pending outbox event"
expect_value "0" "$(field data.outboxAttemptMax < "$STATE_G")" "outbox starts with zero attempts"

RELAY_BODY="$TMP_DIR/relay.json"
RELAY_STATUS=$(curl -s -o "$RELAY_BODY" -w '%{http_code}' -X POST "$MARKETING/dev/outbox/relay?limit=100")
expect_status 200 "$RELAY_STATUS" "outbox relay claims pending events" "$RELAY_BODY"
RELAY_CLAIMED=$(field data.claimed < "$RELAY_BODY")
if [ "$RELAY_CLAIMED" -ge 3 ]; then
  echo "PASS  relay claimed at least the three new events ($RELAY_CLAIMED)"
else
  echo "FAIL  relay claimed only $RELAY_CLAIMED events"; exit 1
fi
expect_value "0" "$(field data.sent < "$RELAY_BODY")" "no message is reported sent without a transport"

STATE_G2="$TMP_DIR/state-g2.json"
state "$USER_G" "$STATE_G2"
expect_value "3" "$(field data.outboxPending < "$STATE_G2")" "outbox rows stay pending without Rabbit"
expect_value "1" "$(field data.outboxAttemptMax < "$STATE_G2")" "claimed outbox rows increment attempt"
expect_value "TRANSPORT_DISABLED" "$(field data.outboxErrorCode < "$STATE_G2")" "missing transport is explicit"

echo
echo "== Dev API isolation =="
if curl -s -o /dev/null -w '%{http_code}' "$MARKETING/dev/points/fixtures/accounts/1" | grep -q '^200$'; then
  echo "PASS  test fixtures are reachable when POINTS_TEST_API_ENABLED=true"
else
  echo "FAIL  test fixture endpoint is not reachable; is POINTS_TEST_API_ENABLED=true set?"
  exit 1
fi

echo
echo "ALL S04-a POINTS CHECKS PASSED"