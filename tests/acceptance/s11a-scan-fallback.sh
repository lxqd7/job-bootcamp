#!/usr/bin/env bash
# S11-a scan fallback: with MQ wakeup disabled and worker poll interval 1s, a queued task
# must be picked up and completed by the scheduled scan without any dev trigger.
# Start assignment with AI_WAKEUP_ENABLED=false, AI_MOCK_POLL_MS=1000, AI_MOCK_DELAY_MS=1000.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

field() {
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(0,"utf8"));
    let v=d; for(const k of process.argv[1].split(".")) v=v==null?undefined:v[k];
    console.log(v==null?"":v);
  ' "$1"
}

expect_status() {
  local expected="$1" actual="$2" label="$3" body="$4"
  if [ "$expected" = "$actual" ]; then echo "PASS  $label  (HTTP $actual)";
  else echo "FAIL  $label  expected HTTP $expected, got $actual"; cat "$body" 2>/dev/null || true; echo; exit 1; fi
}

expect_value() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then echo "PASS  $label  ($actual)";
  else echo "FAIL  $label  expected '$expected', got '$actual'"; exit 1; fi
}

db() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" --default-character-set=utf8mb4 -h127.0.0.1 -uroot -N -B -e "$1"
}

USERNAME=$(db "SELECT username FROM bc_user.user_account WHERE username LIKE 's11a\\_%' ORDER BY id DESC LIMIT 1")
COURSE_ID=$(db "SELECT id FROM bc_course.course WHERE title LIKE 'S11A course %' ORDER BY id DESC LIMIT 1")
CODE_ID=$(db "SELECT id FROM bc_assignment.assignment WHERE course_id=$COURSE_ID AND kind='COURSE_CODE' ORDER BY id DESC LIMIT 1")
if [ -z "$USERNAME" ] || [ -z "$CODE_ID" ]; then
  echo "FAIL  S11-a setup data not found; run s11a-ai-worker-api.sh first"; exit 1
fi
echo "INFO  using student=$USERNAME course=$COURSE_ID code=$CODE_ID"

STATUS=$(curl -s -o "$TMP_DIR/login.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"Passw0rd!\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$STATUS" "student login" "$TMP_DIR/login.json"
TOKEN=$(field data.accessToken < "$TMP_DIR/login.json")

STATUS=$(curl -s -o "$TMP_DIR/attempt.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/assignments/$CODE_ID/attempts")
expect_status 200 "$STATUS" "start scan attempt" "$TMP_DIR/attempt.json"
ATTEMPT_ID=$(field data.id < "$TMP_DIR/attempt.json")
STARTED=$(date +%s)
STATUS=$(curl -s -o "$TMP_DIR/submit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: s11a-scan-$STAMP" -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"s1":"return quantity < 0 ? 0 : unit * quantity;"}}' \
  "$BASE/api/v1/attempts/$ATTEMPT_ID/submissions")
expect_status 200 "$STATUS" "submit scan task" "$TMP_DIR/submit.json"
SUBMISSION_ID=$(field data.id < "$TMP_DIR/submit.json")
TASK_ID=$(db "SELECT id FROM bc_assignment.ai_task WHERE submission_id=$SUBMISSION_ID")

for attempt in $(seq 1 80); do
  STATE=$(db "SELECT state FROM bc_assignment.ai_task WHERE id=$TASK_ID")
  if [ "$STATE" = "SUCCEEDED" ]; then break; fi
  sleep 0.25
done
ELAPSED=$(( $(date +%s) - STARTED ))
expect_value "SUCCEEDED" "$(db "SELECT state FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "scheduled scan completes task"
if [ "$ELAPSED" -gt 20 ]; then echo "FAIL  scan fallback took ${ELAPSED}s"; exit 1; fi
echo "PASS  scan fallback latency ${ELAPSED}s without MQ or dev trigger"
expect_value "1" "$(db "SELECT is_mock FROM bc_assignment.ai_feedback WHERE task_id=$TASK_ID")" "scan feedback is mock"
expect_value "" "$(db "SELECT COALESCE(active_task_id, '') FROM bc_assignment.ai_user_slot WHERE user_id=(SELECT user_id FROM bc_assignment.ai_task WHERE id=$TASK_ID)")" "slot released after scan"

echo "ALL S11-A SCAN FALLBACK CHECKS PASSED"