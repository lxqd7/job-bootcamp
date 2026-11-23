#!/usr/bin/env bash
# S11-b acceptance: Qwen/DeepSeek HTTP routing, review-v1 prompt, strict schema,
# format repair, error classification and hard 6-call budget, using a local OpenAI-compatible stub.
# Services must be started with AI_MODE=http, QWEN/DEEPSEEK base URLs on 18999, dummy keys,
# AI_MOCK_POLL_MS=500, AI_HTTP_READ_TIMEOUT_MS=800, AI_RETRY_DELAYS_MS=200,400.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
STUB="${STUB:-http://127.0.0.1:18999}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
USERNAME="s11b_$STAMP"
PASSWORD="Passw0rd!"
TMP_DIR="$(mktemp -d)"
STUB_PID=""
cleanup() {
  if [ -n "$STUB_PID" ]; then kill "$STUB_PID" 2>/dev/null || true; fi
  rm -rf "$TMP_DIR"
}
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
    -d "{\"title\":\"S11B course $STAMP\",\"description\":\"S11B\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
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
    -H "Idempotency-Key: s11b-$STAMP" -H "Authorization: Bearer $TOKEN" \
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

control() {
  local scenario="$1" status
  status=$(curl -s -o "$TMP_DIR/control.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"scenario\":\"$scenario\"}" "$STUB/control")
  expect_status 200 "$status" "stub scenario $scenario" "$TMP_DIR/control.json"
}

submit_code() {
  local label="$1" status
  STATUS=$(curl -s -o "$TMP_DIR/attempt.json" -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/assignments/$CODE_ID/attempts")
  expect_status 200 "$STATUS" "start attempt $label" "$TMP_DIR/attempt.json"
  local attempt_id
  attempt_id=$(field data.id < "$TMP_DIR/attempt.json")
  STATUS=$(curl -s -o "$TMP_DIR/submit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s11b-$label-$STAMP" -H "Authorization: Bearer $TOKEN" \
    -d '{"answers":{"s1":"return quantity < 0 ? 0 : unit * quantity;"}}' \
    "$BASE/api/v1/attempts/$attempt_id/submissions")
  expect_status 200 "$STATUS" "submit code $label" "$TMP_DIR/submit.json"
  SUBMISSION_ID=$(field data.id < "$TMP_DIR/submit.json")
  TASK_ID=$(db "SELECT id FROM bc_assignment.ai_task WHERE submission_id=$SUBMISSION_ID")
  TASKS="${TASKS:-},$TASK_ID"
}

wait_terminal() {
  local task_id="$1" label="$2"
  for attempt in $(seq 1 120); do
    STATE=$(db "SELECT state FROM bc_assignment.ai_task WHERE id=$task_id")
    if [ "$STATE" = "SUCCEEDED" ] || [ "$STATE" = "FAILED" ]; then
      echo "PASS  $label terminal  ($STATE)"; return 0
    fi
    sleep 0.25
  done
  echo "FAIL  $label did not reach terminal"; exit 1
}

attempts_of() {
  db "SELECT COALESCE(GROUP_CONCAT(CONCAT(provider, ':', state, ':', COALESCE(error_class, ''), ':', call_index) ORDER BY call_index SEPARATOR ','), '') FROM bc_assignment.ai_attempt WHERE task_id=$1"
}

stub_calls() {
  curl -s "$STUB/state" > "$TMP_DIR/stub-state.json"
  node -e '
    const d=JSON.parse(require("fs").readFileSync(process.argv[1]));
    console.log(d.calls.map(c=>c.provider).join(","));
  ' "$TMP_DIR/stub-state.json"
}

expect_slot_free() {
  local active
  active=$(db "SELECT COALESCE(active_task_id, '') FROM bc_assignment.ai_user_slot WHERE user_id=$STUDENT_ID")
  expect_value "" "$active" "$1"
}

expect_no_feedback() {
  expect_value "0" "$(db "SELECT COUNT(*) FROM bc_assignment.ai_feedback WHERE task_id=$1")" "no feedback"
}

echo "== Start local AI provider stub =="
if curl -s --max-time 1 "$STUB/state" >/dev/null 2>&1; then
  echo "FAIL  port 18999 already has a stub; stop it first"; exit 1
fi
node tests/acceptance/s11b-ai-stub.mjs > "$TMP_DIR/stub.log" 2>&1 &
STUB_PID=$!
for attempt in $(seq 1 40); do
  if curl -s --max-time 1 "$STUB/state" >/dev/null 2>&1; then break; fi
  sleep 0.25
done
curl -s "$STUB/state" >/dev/null
echo "PASS  AI stub started"

echo "== Setup paid course and student =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
setup_course
STATUS=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"S11BTester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register student" "$TMP_DIR/register.json"
login "$USERNAME" "$PASSWORD" STUDENT "$TMP_DIR/login.json"
STUDENT_ID=$USER_ID
TOKEN=$TOKEN
buy_course
create_code_assignment

echo "== Qwen first success and review-v1 request shape =="
control qwen_success
submit_code qwen-success
wait_terminal "$TASK_ID" "qwen success"
expect_value "SUCCEEDED" "$STATE" "task succeeds"
expect_value "GRADED" "$(db "SELECT review_state FROM bc_assignment.submission WHERE id=$SUBMISSION_ID")" "submission graded"
expect_value "SATISFIED|0" "$(db "SELECT CONCAT(verdict,'|',is_mock) FROM bc_assignment.ai_feedback WHERE task_id=$TASK_ID")" "non-mock feedback"
expect_value "QWEN:SUCCESS::1" "$(attempts_of "$TASK_ID")" "one Qwen success call"
expect_value "QWEN" "$(stub_calls)" "DeepSeek not called"
REQUEST_CHECK=$(node -e '
  const d=JSON.parse(require("fs").readFileSync(process.argv[1]));
  const q=d.requests.QWEN;
  const sys=q?.messages?.[0]?.content ?? "";
  const user=q?.messages?.[1]?.content ?? "";
  console.log([
    q?.model, q?.temperature, q?.stream, q?.max_tokens,
    sys.includes("Java课程代码自测点评器"),
    user.includes("\"problem\""), user.includes("\"rubric\""),
    user.includes("\"reference\""), user.includes("\"submission\""), user.includes("s1")
  ].join("|"));
' "$TMP_DIR/stub-state.json")
expect_value "qwen-plus|0|false|2000|true|true|true|true|true|true" "$REQUEST_CHECK" "review-v1 request shape"
expect_slot_free "slot released after Qwen success"

echo "== Qwen unavailable falls back to DeepSeek in the same round =="
control qwen_503_deepseek_success
submit_code qwen-fallback
wait_terminal "$TASK_ID" "qwen fallback"
expect_value "SUCCEEDED" "$STATE" "fallback task succeeds"
expect_value "QWEN:FAILED:TEMPORARY:1,DEEPSEEK:SUCCESS::2" "$(attempts_of "$TASK_ID")" "DeepSeek fallback calls"
expect_value "QWEN,DEEPSEEK" "$(stub_calls)" "both providers called"
expect_value "SATISFIED|0" "$(db "SELECT CONCAT(verdict,'|',is_mock) FROM bc_assignment.ai_feedback WHERE task_id=$TASK_ID")" "fallback feedback non-mock"
expect_slot_free "slot released after fallback"

echo "== Qwen format error repairs Qwen once and never switches =="
control qwen_format_repair_success
submit_code qwen-repair
wait_terminal "$TASK_ID" "qwen repair"
expect_value "SUCCEEDED" "$STATE" "repair task succeeds"
expect_value "QWEN:FAILED:FORMAT:1,QWEN:SUCCESS::2" "$(attempts_of "$TASK_ID")" "Qwen repair calls"
expect_value "1" "$(db "SELECT repair_used FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "repair flag set"
expect_value "QWEN,QWEN" "$(stub_calls)" "DeepSeek not called for Qwen format error"
expect_slot_free "slot released after Qwen repair"

echo "== Qwen format error twice fails without DeepSeek or new round =="
control qwen_format_twice
submit_code qwen-format-fail
wait_terminal "$TASK_ID" "qwen format fail"
expect_value "FAILED" "$STATE" "format failure task failed"
expect_value "AI_INVALID_RESPONSE" "$(db "SELECT safe_error_code FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "format safe error"
expect_value "QWEN:FAILED:FORMAT:1,QWEN:FAILED:FORMAT:2" "$(attempts_of "$TASK_ID")" "only two Qwen calls"
expect_value "QWEN,QWEN" "$(stub_calls)" "no DeepSeek after Qwen format error"
expect_no_feedback "$TASK_ID"
expect_slot_free "slot released after format failure"

echo "== Both providers temporary: three rounds and hard six-call cap =="
control both_503
submit_code both-temporary
wait_terminal "$TASK_ID" "both temporary"
expect_value "FAILED" "$STATE" "temporary retries exhausted"
expect_value "AI_UNAVAILABLE" "$(db "SELECT safe_error_code FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "temporary safe error"
expect_value "6" "$(db "SELECT COUNT(*) FROM bc_assignment.ai_attempt WHERE task_id=$TASK_ID")" "exactly six outbound calls"
EXPECTED_ATTEMPTS="QWEN:FAILED:TEMPORARY:1,DEEPSEEK:FAILED:TEMPORARY:2,QWEN:FAILED:TEMPORARY:3,DEEPSEEK:FAILED:TEMPORARY:4,QWEN:FAILED:TEMPORARY:5,DEEPSEEK:FAILED:TEMPORARY:6"
expect_value "$EXPECTED_ATTEMPTS" "$(attempts_of "$TASK_ID")" "three alternating rounds"
expect_no_feedback "$TASK_ID"
expect_slot_free "slot released after temporary exhaustion"

echo "== HTTP 400 is permanent input error and is not retried =="
control qwen_400
submit_code qwen-400
wait_terminal "$TASK_ID" "qwen 400"
expect_value "FAILED" "$STATE" "input error fails"
expect_value "AI_INVALID_RESPONSE" "$(db "SELECT safe_error_code FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "input safe error"
expect_value "QWEN:FAILED:INPUT:1" "$(attempts_of "$TASK_ID")" "no retry after 400"
expect_value "QWEN" "$(stub_calls)" "no DeepSeek after 400"
expect_no_feedback "$TASK_ID"

echo "== Qwen config error falls back to DeepSeek =="
control qwen_401_deepseek_success
submit_code qwen-401
wait_terminal "$TASK_ID" "qwen 401 fallback"
expect_value "SUCCEEDED" "$STATE" "config fallback succeeds"
expect_value "QWEN:FAILED:CONFIG:1,DEEPSEEK:SUCCESS::2" "$(attempts_of "$TASK_ID")" "config fallback calls"
expect_slot_free "slot released after config fallback"

echo "== DeepSeek format error repairs DeepSeek once and never returns to Qwen =="
control deepseek_format_twice
submit_code deepseek-format
wait_terminal "$TASK_ID" "deepseek format"
expect_value "FAILED" "$STATE" "DeepSeek format failure"
expect_value "AI_INVALID_RESPONSE" "$(db "SELECT safe_error_code FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "DeepSeek format safe error"
expect_value "QWEN:FAILED:TEMPORARY:1,DEEPSEEK:FAILED:FORMAT:2,DEEPSEEK:FAILED:FORMAT:3" "$(attempts_of "$TASK_ID")" "DeepSeek repair only"
expect_no_feedback "$TASK_ID"

echo "== Read timeout is temporary and falls back to DeepSeek =="
control qwen_timeout_deepseek_success
submit_code qwen-timeout
wait_terminal "$TASK_ID" "qwen timeout"
expect_value "SUCCEEDED" "$STATE" "timeout fallback succeeds"
expect_value "QWEN:TIMEOUT:TIMEOUT:1,DEEPSEEK:SUCCESS::2" "$(attempts_of "$TASK_ID")" "timeout classified and fallback"
expect_slot_free "slot released after timeout fallback"

echo "== Empty and truncated provider output use Qwen format repair =="
control qwen_empty_repair
submit_code qwen-empty
wait_terminal "$TASK_ID" "qwen empty"
expect_value "SUCCEEDED" "$STATE" "empty content repaired"
expect_value "QWEN:FAILED:FORMAT:1,QWEN:SUCCESS::2" "$(attempts_of "$TASK_ID")" "empty content calls"

control qwen_truncated_repair
submit_code qwen-truncated
wait_terminal "$TASK_ID" "qwen truncated"
expect_value "SUCCEEDED" "$STATE" "truncated output repaired"
expect_value "QWEN:FAILED:FORMAT:1,QWEN:SUCCESS::2" "$(attempts_of "$TASK_ID")" "truncated calls"

echo "== Schema validation rejects unknown fields/unknown slot and repairs once =="
control qwen_schema_bad_repair
submit_code qwen-schema
wait_terminal "$TASK_ID" "qwen schema repair"
expect_value "SUCCEEDED" "$STATE" "schema error repaired"
expect_value "QWEN:FAILED:FORMAT:1,QWEN:SUCCESS::2" "$(attempts_of "$TASK_ID")" "schema repair calls"

control qwen_schema_bad_twice
submit_code qwen-schema-fail
wait_terminal "$TASK_ID" "qwen schema fail"
expect_value "FAILED" "$STATE" "schema error twice fails"
expect_value "AI_INVALID_RESPONSE" "$(db "SELECT safe_error_code FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "schema safe error"

echo "== Final attempt bookkeeping =="
TASK_LIST=${TASKS#,}
PLANNED=$(db "SELECT COUNT(*) FROM bc_assignment.ai_attempt WHERE task_id IN ($TASK_LIST) AND state='PLANNED'")
expect_value "0" "$PLANNED" "no PLANNED attempts left"
MISSING_HASH=$(db "SELECT COUNT(*) FROM bc_assignment.ai_attempt WHERE task_id IN ($TASK_LIST) AND state='SUCCESS' AND response_hash IS NULL")
expect_value "0" "$MISSING_HASH" "successful calls have response hash"
BAD_CODES=$(db "SELECT COUNT(*) FROM bc_assignment.ai_task WHERE id IN ($TASK_LIST) AND safe_error_code IS NOT NULL AND safe_error_code NOT IN ('AI_TIMEOUT','AI_UNAVAILABLE','AI_INVALID_RESPONSE')")
expect_value "0" "$BAD_CODES" "only safe error categories"

echo "ALL S11-B ACCEPTANCE CHECKS PASSED"