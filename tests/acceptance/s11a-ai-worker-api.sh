#!/usr/bin/env bash
# S11-a acceptance: MQ wakeup, lease/fencing, lease recovery, 10-minute deadline and call budget.
# Start assignment with AI_WAKEUP_ENABLED=true, AI_MOCK_POLL_MS=600000, AI_MOCK_DELAY_MS=4000,
# AI_LEASE_SECONDS=6, AI_LEASE_RENEW_MS=1000.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
USERNAME="s11a_$STAMP"
PASSWORD="Passw0rd!"
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

login() {
  local username="$1" password="$2" app="$3" out="$4" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
  TOKEN=$(field data.accessToken < "$out")
  USER_ID=$(field data.user.id < "$out")
}

setup_course() {
  local status
  status=$(curl -s -o "$TMP_DIR/course.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"S11A course $STAMP\",\"description\":\"S11A\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create course" "$TMP_DIR/course.json"
  COURSE_ID=$(field data.id < "$TMP_DIR/course.json")
  COURSE_REV=$(field data.revision < "$TMP_DIR/course.json")
  status=$(curl -s -o "$TMP_DIR/chapter.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Chapter","sort":1}' \
    "$BASE/api/v1/admin/courses/$COURSE_ID/chapters")
  expect_status 200 "$status" "create chapter" "$TMP_DIR/chapter.json"
  CHAPTER_ID=$(field data.id < "$TMP_DIR/chapter.json")
  status=$(curl -s -o "$TMP_DIR/lesson.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Video","sort":1,"mediaId":1}' \
    "$BASE/api/v1/admin/chapters/$CHAPTER_ID/lessons")
  expect_status 200 "$status" "create lesson" "$TMP_DIR/lesson.json"
  status=$(curl -s -o "$TMP_DIR/publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$COURSE_REV}" \
    "$BASE/api/v1/admin/courses/$COURSE_ID/publish")
  expect_status 200 "$status" "publish course" "$TMP_DIR/publish.json"
}

buy_course() {
  local status quote_id quote_hash
  status=$(curl -s -o "$TMP_DIR/quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"productType\":\"COURSE\",\"productId\":$COURSE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
    "$BASE/api/v1/trade/quotes")
  expect_status 200 "$status" "student quote" "$TMP_DIR/quote.json"
  quote_id=$(field data.quoteId < "$TMP_DIR/quote.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/quote.json")
  status=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s11a-$STAMP" -H "Authorization: Bearer $TOKEN" \
    -d "{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[]}" \
    "$BASE/api/v1/orders")
  expect_status 200 "$status" "student order" "$TMP_DIR/order.json"
  ORDER_ID=$(field data.id < "$TMP_DIR/order.json")
  status=$(curl -s -o "$TMP_DIR/payment.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" -d '{"channel":"MOCK"}' \
    "$BASE/api/v1/orders/$ORDER_ID/payment")
  expect_status 200 "$status" "payment request" "$TMP_DIR/payment.json"
  status=$(curl -s -o "$TMP_DIR/pay.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
    "$BASE/api/v1/orders/$ORDER_ID/payment/mock-pay")
  expect_status 200 "$status" "mock pay" "$TMP_DIR/pay.json"
  for attempt in $(seq 1 40); do
    curl -s -o "$TMP_DIR/order-state.json" -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/orders/$ORDER_ID"
    if [ "$(field data.fulfillmentState < "$TMP_DIR/order-state.json")" = "DONE" ]; then
      echo "PASS  order fulfilled"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  order not fulfilled"; cat "$TMP_DIR/order-state.json"; exit 1
}

create_code_assignment() {
  cat > "$TMP_DIR/code-public.json" <<'JSON'
{"scenario":"订单金额计算","requirements":"补全 calculate，数量为负返回 0。","segments":[{"text":"public class Calc {\n"},{"slotId":"s1","initialCode":"// TODO\n"},{"text":"}\n"}]}
JSON
  cat > "$TMP_DIR/code-answer.json" <<'JSON'
{"referenceBySlot":{"s1":"return quantity < 0 ? 0 : unit * quantity;\n"},"rubric":"负数、long、无编译错误。"}
JSON
  node -e '
    const fs=require("fs");
    fs.writeFileSync(process.argv[1], JSON.stringify({
      kind:"COURSE_CODE", courseId:Number(process.argv[2]), chapterId:null,
      publicBody:JSON.parse(fs.readFileSync(process.argv[3],"utf8")),
      answerBody:JSON.parse(fs.readFileSync(process.argv[4],"utf8"))
    }));
  ' "$TMP_DIR/code.body" "$COURSE_ID" "$TMP_DIR/code-public.json" "$TMP_DIR/code-answer.json"
  STATUS=$(curl -s -o "$TMP_DIR/code.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" --data-binary @"$TMP_DIR/code.body" \
    "$BASE/api/v1/admin/assignments")
  expect_status 200 "$STATUS" "create code assignment" "$TMP_DIR/code.json"
  CODE_ID=$(field data.id < "$TMP_DIR/code.json")
  CODE_REV=$(field data.revision < "$TMP_DIR/code.json")
  STATUS=$(curl -s -o "$TMP_DIR/code-publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$CODE_REV}" \
    "$BASE/api/v1/admin/assignments/$CODE_ID/publish")
  expect_status 200 "$STATUS" "publish code assignment" "$TMP_DIR/code-publish.json"
}

submit_code() {
  local label="$1" out="$TMP_DIR/$1-submit.json" status
  STATUS=$(curl -s -o "$TMP_DIR/attempt.json" -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/assignments/$CODE_ID/attempts")
  expect_status 200 "$STATUS" "start attempt $label" "$TMP_DIR/attempt.json"
  local attempt_id
  attempt_id=$(field data.id < "$TMP_DIR/attempt.json")
  STATUS=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s11a-$label-$STAMP" -H "Authorization: Bearer $TOKEN" \
    -d '{"answers":{"s1":"return quantity < 0 ? 0 : unit * quantity;"}}' \
    "$BASE/api/v1/attempts/$attempt_id/submissions")
  expect_status 200 "$STATUS" "submit code $label" "$out"
  SUBMISSION_ID=$(field data.id < "$out")
  TASK_ID=$(db "SELECT id FROM bc_assignment.ai_task WHERE submission_id=$SUBMISSION_ID")
  echo "INFO  $label submission=$SUBMISSION_ID task=$TASK_ID"
}

wait_task_state() {
  local task_id="$1" expected="$2" label="$3" limit="${4:-80}"
  for attempt in $(seq 1 "$limit"); do
    local state
    state=$(db "SELECT state FROM bc_assignment.ai_task WHERE id=$task_id")
    if [ "$state" = "$expected" ]; then echo "PASS  $label  ($state)"; return 0; fi
    sleep 0.25
  done
  echo "FAIL  $label  expected state $expected, current $(db "SELECT state FROM bc_assignment.ai_task WHERE id=$task_id")"; exit 1
}

wait_task_running() {
  local task_id="$1" label="$2"
  for attempt in $(seq 1 60); do
    if [ "$(db "SELECT state FROM bc_assignment.ai_task WHERE id=$task_id")" = "RUNNING" ]; then
      echo "PASS  $label  (RUNNING)"; return 0
    fi
    sleep 0.1
  done
  echo "FAIL  $label  task did not reach RUNNING"; exit 1
}

expect_slot_free() {
  local label="$1"
  local active
  active=$(db "SELECT COALESCE(active_task_id, '') FROM bc_assignment.ai_user_slot WHERE user_id=$STUDENT_ID")
  expect_value "" "$active" "$label"
}

trigger_worker() {
  local status
  status=$(curl -s -o "$TMP_DIR/dev-poll.json" -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/dev/assignments/mock-review")
  expect_status 200 "$status" "trigger worker poll" "$TMP_DIR/dev-poll.json"
}

echo "== Setup paid course and student =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
setup_course
STATUS=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"S11ATester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register student" "$TMP_DIR/register.json"
login "$USERNAME" "$PASSWORD" STUDENT "$TMP_DIR/login.json"
STUDENT_TOKEN=$TOKEN
STUDENT_ID=$USER_ID
TOKEN=$STUDENT_TOKEN
buy_course
create_code_assignment

echo "== MQ wakeup processes a queued task while scan interval is long =="
STARTED=$(date +%s)
submit_code mq
wait_task_state "$TASK_ID" SUCCEEDED "MQ wakeup completes task" 120
ELAPSED=$(( $(date +%s) - STARTED ))
if [ "$ELAPSED" -gt 15 ]; then echo "FAIL  MQ wakeup took ${ELAPSED}s"; exit 1; fi
echo "PASS  MQ wakeup latency ${ELAPSED}s"
FEEDBACK=$(db "SELECT CONCAT(verdict, '|', is_mock) FROM bc_assignment.ai_feedback WHERE task_id=$TASK_ID")
expect_value "SATISFIED|1" "$FEEDBACK" "mock feedback stored"
DEADLINE_SECONDS=$(db "SELECT TIMESTAMPDIFF(SECOND, created_at, deadline_at) FROM bc_assignment.ai_task WHERE id=$TASK_ID")
expect_value "600" "$DEADLINE_SECONDS" "task deadline is 10 minutes"
expect_slot_free "slot released after success"

echo "== Stale worker result is discarded by lease fencing =="
submit_code fence
wait_task_running "$TASK_ID" "fence task claimed"
db "UPDATE bc_assignment.ai_task SET lease_token='stale-worker', lease_until=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 120 SECOND) WHERE id=$TASK_ID"
sleep 6
STATE=$(db "SELECT state FROM bc_assignment.ai_task WHERE id=$TASK_ID")
REVIEW=$(db "SELECT review_state FROM bc_assignment.submission WHERE id=$SUBMISSION_ID")
FEEDBACK_COUNT=$(db "SELECT COUNT(*) FROM bc_assignment.ai_feedback WHERE task_id=$TASK_ID")
expect_value "RUNNING" "$STATE" "stale worker did not finish task"
expect_value "PENDING_REVIEW" "$REVIEW" "stale worker did not grade submission"
expect_value "0" "$FEEDBACK_COUNT" "stale worker wrote no feedback"
db "UPDATE bc_assignment.ai_task SET state='QUEUED', lease_token=NULL, lease_until=NULL, next_retry_at=UTC_TIMESTAMP(3) WHERE id=$TASK_ID"
trigger_worker
wait_task_state "$TASK_ID" SUCCEEDED "recovered task succeeds" 120
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_assignment.ai_feedback WHERE task_id=$TASK_ID")" "one final feedback after fencing"
expect_slot_free "slot released after fencing recovery"

echo "== Deadline failure releases the slot =="
submit_code deadline
wait_task_running "$TASK_ID" "deadline task claimed"
db "UPDATE bc_assignment.ai_task SET deadline_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE id=$TASK_ID"
wait_task_state "$TASK_ID" FAILED "deadline task fails" 120
expect_value "AI_TIMEOUT" "$(db "SELECT safe_error_code FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "deadline safe error"
expect_value "FAILED" "$(db "SELECT review_state FROM bc_assignment.submission WHERE id=$SUBMISSION_ID")" "submission marked failed"
expect_value "0" "$(db "SELECT COUNT(*) FROM bc_assignment.ai_feedback WHERE task_id=$TASK_ID")" "no feedback on deadline failure"
expect_slot_free "slot released after deadline"

echo "== Expired lease is recovered by another worker =="
submit_code lease
wait_task_running "$TASK_ID" "lease task claimed"
db "UPDATE bc_assignment.ai_task SET lease_token='crashed-worker', lease_until=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE id=$TASK_ID"
trigger_worker
wait_task_state "$TASK_ID" SUCCEEDED "expired lease recovered" 120
expect_slot_free "slot released after lease recovery"

echo "== Exhausted call budget fails instead of retrying forever =="
submit_code budget
wait_task_running "$TASK_ID" "budget task claimed"
for index in 1 2 3 4 5 6; do
  db "INSERT INTO bc_assignment.ai_attempt(task_id, call_index, provider, state, started_at, created_at) VALUES($TASK_ID, $index, 'QWEN', 'PLANNED', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))"
done
db "UPDATE bc_assignment.ai_task SET lease_token='crashed-budget', lease_until=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE id=$TASK_ID"
trigger_worker
wait_task_state "$TASK_ID" FAILED "call budget exhaustion fails" 120
expect_value "AI_UNAVAILABLE" "$(db "SELECT safe_error_code FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "budget safe error"
expect_slot_free "slot released after budget exhaustion"

echo "== A new code submission is allowed after terminal failure =="
submit_code retry
STATUS=$(curl -s -o "$TMP_DIR/retry.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION_ID")
expect_status 200 "$STATUS" "new submission after failure" "$TMP_DIR/retry.json"
wait_task_state "$TASK_ID" SUCCEEDED "submission after failure succeeds" 120
expect_slot_free "slot released after final success"

echo "ALL S11-A ACCEPTANCE CHECKS PASSED"