#!/usr/bin/env bash
# S11-c acceptance: review status polling is read-only, failure/success DTOs are safe,
# teacher sees the same read-only feedback, and no model-selection/retry endpoint exists.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
USERNAME="s11c_$STAMP"
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

json_get() {
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    let v=d; for(const k of process.argv[2].split(".")) v=v==null?undefined:v[k];
    console.log(v==null?"":v);
  ' "$1" "$2"
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
    -d "{\"title\":\"S11C course $STAMP\",\"description\":\"S11C\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
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
    -H "Idempotency-Key: s11c-$STAMP" -H "Authorization: Bearer $TOKEN" \
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
  local label="$1" status
  STATUS=$(curl -s -o "$TMP_DIR/attempt.json" -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/assignments/$CODE_ID/attempts")
  expect_status 200 "$STATUS" "start attempt $label" "$TMP_DIR/attempt.json"
  local attempt_id
  attempt_id=$(field data.id < "$TMP_DIR/attempt.json")
  STATUS=$(curl -s -o "$TMP_DIR/submit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s11c-$label-$STAMP" -H "Authorization: Bearer $TOKEN" \
    -d '{"answers":{"s1":"return quantity < 0 ? 0 : unit * quantity;"}}' \
    "$BASE/api/v1/attempts/$attempt_id/submissions")
  expect_status 200 "$STATUS" "submit code $label" "$TMP_DIR/submit.json"
  SUBMISSION_ID=$(field data.id < "$TMP_DIR/submit.json")
  TASK_ID=$(db "SELECT id FROM bc_assignment.ai_task WHERE submission_id=$SUBMISSION_ID")
}

no_provider_leak() {
  local file="$1" label="$2"
  if grep -Eqi "qwen|deepseek|provider|apiKey|promptVersion|qwen-plus|deepseek-chat" "$file"; then
    echo "FAIL  $label leaked provider/model/prompt"; cat "$file"; exit 1
  fi
  echo "PASS  $label has no provider/model/prompt fields"
}

echo "== Setup paid course, student and code assignment =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
setup_course
STATUS=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"S11CTester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register student" "$TMP_DIR/register.json"
login "$USERNAME" "$PASSWORD" STUDENT "$TMP_DIR/login.json"
STUDENT_TOKEN=$TOKEN
STUDENT_ID=$USER_ID
TOKEN=$STUDENT_TOKEN
buy_course
create_code_assignment

echo "== Queued status is read-only and polling does not create calls =="
submit_code queued
STATUS=$(curl -s -o "$TMP_DIR/review-queued.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION_ID/review")
expect_status 200 "$STATUS" "read queued review" "$TMP_DIR/review-queued.json"
expect_value "QUEUED" "$(field data.taskStatus < "$TMP_DIR/review-queued.json")" "queued status"
expect_value "" "$(field data.feedback < "$TMP_DIR/review-queued.json")" "no feedback while queued"
no_provider_leak "$TMP_DIR/review-queued.json" "queued review"
for attempt in 1 2 3 4 5; do
  curl -s -o /dev/null -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/submissions/$SUBMISSION_ID/review"
done
expect_value "QUEUED" "$(db "SELECT state FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "polling did not change task state"
expect_value "0" "$(db "SELECT COUNT(*) FROM bc_assignment.ai_attempt WHERE task_id=$TASK_ID")" "polling did not call a provider"

echo "== RUNNING status and no retry endpoint =="
db "UPDATE bc_assignment.ai_task SET state='RUNNING', lease_token='manual', lease_until=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 60 SECOND) WHERE id=$TASK_ID"
STATUS=$(curl -s -o "$TMP_DIR/review-running.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION_ID/review")
expect_status 200 "$STATUS" "read running review" "$TMP_DIR/review-running.json"
expect_value "RUNNING" "$(field data.taskStatus < "$TMP_DIR/review-running.json")" "running status"
STATUS=$(curl -s -o "$TMP_DIR/review-post.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION_ID/review")
if [ "$STATUS" != "405" ] && [ "$STATUS" != "404" ]; then echo "FAIL  review POST should not exist"; exit 1; fi
echo "PASS  no student retry endpoint  (HTTP $STATUS)"
STATUS=$(curl -s -o "$TMP_DIR/retry-post.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION_ID/retry")
if [ "$STATUS" != "405" ] && [ "$STATUS" != "404" ]; then echo "FAIL  retry POST should not exist"; exit 1; fi
echo "PASS  no independent AI retry endpoint  (HTTP $STATUS)"
STATUS=$(curl -s -o "$TMP_DIR/models.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/ai/models")
expect_status 404 "$STATUS" "no model selection endpoint" "$TMP_DIR/models.json"

echo "== Failed status exposes only a safe category =="
db "UPDATE bc_assignment.ai_task SET state='FAILED', safe_error_code='AI_UNAVAILABLE', lease_token=NULL, lease_until=NULL WHERE id=$TASK_ID"
db "UPDATE bc_assignment.submission SET review_state='FAILED' WHERE id=$SUBMISSION_ID"
db "UPDATE bc_assignment.ai_user_slot SET active_task_id=NULL WHERE active_task_id=$TASK_ID"
STATUS=$(curl -s -o "$TMP_DIR/review-failed.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION_ID/review")
expect_status 200 "$STATUS" "read failed review" "$TMP_DIR/review-failed.json"
expect_value "FAILED" "$(field data.taskStatus < "$TMP_DIR/review-failed.json")" "failed status"
expect_value "AI_UNAVAILABLE" "$(field data.safeErrorCode < "$TMP_DIR/review-failed.json")" "safe failure category"
expect_value "" "$(field data.feedback < "$TMP_DIR/review-failed.json")" "no feedback on failure"
no_provider_leak "$TMP_DIR/review-failed.json" "failed review"
STATUS=$(curl -s -o "$TMP_DIR/failed-submission.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION_ID")
expect_status 200 "$STATUS" "failed submission remains readable" "$TMP_DIR/failed-submission.json"
expect_value "FAILED" "$(field data.reviewState < "$TMP_DIR/failed-submission.json")" "submission failed state"

echo "== Teacher sees the same read-only failed feedback =="
login teacher01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/teacher-login.json"
TEACHER_TOKEN=$TOKEN
TEACHER_ID=$USER_ID
TOKEN=$STUDENT_TOKEN
STATUS=$(curl -s -o "$TMP_DIR/teacher-current.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "read teacher assignments" "$TMP_DIR/teacher-current.json"
STATUS=$(curl -s -o "$TMP_DIR/teacher-save.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[$COURSE_ID],\"routeIds\":[],\"revision\":$(field data.revision < "$TMP_DIR/teacher-current.json")}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "assign teacher" "$TMP_DIR/teacher-save.json"
STATUS=$(curl -s -o "$TMP_DIR/teacher-list.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/courses/$COURSE_ID/submissions?page=1&pageSize=20")
expect_status 200 "$STATUS" "teacher lists submissions" "$TMP_DIR/teacher-list.json"
expect_value "1" "$(field data.total < "$TMP_DIR/teacher-list.json")" "teacher sees failed submission"
STATUS=$(curl -s -o "$TMP_DIR/teacher-detail.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/submissions/$SUBMISSION_ID")
expect_status 200 "$STATUS" "teacher reads failed detail" "$TMP_DIR/teacher-detail.json"
expect_value "FAILED" "$(field data.review.taskStatus < "$TMP_DIR/teacher-detail.json")" "teacher sees failed status"
expect_value "AI_UNAVAILABLE" "$(field data.review.safeErrorCode < "$TMP_DIR/teacher-detail.json")" "teacher sees safe category"
no_provider_leak "$TMP_DIR/teacher-detail.json" "teacher detail"
STATUS=$(curl -s -o "$TMP_DIR/teacher-retry.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/submissions/$SUBMISSION_ID/retry")
if [ "$STATUS" != "405" ] && [ "$STATUS" != "404" ]; then echo "FAIL  teacher retry endpoint should not exist"; exit 1; fi
echo "PASS  teacher has no retry endpoint  (HTTP $STATUS)"
STATUS=$(curl -s -o "$TMP_DIR/student-teacher.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT_TOKEN" \
  "$BASE/api/v1/teacher/courses/$COURSE_ID/submissions")
expect_status 403 "$STATUS" "student cannot call teacher API" "$TMP_DIR/student-teacher.json"

echo "== A new submission creates a new task and succeeds; failed feedback stays =="
TOKEN=$STUDENT_TOKEN
submit_code success
STATUS=$(curl -s -o "$TMP_DIR/dev-poll.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/dev/assignments/mock-review")
expect_status 200 "$STATUS" "trigger mock review" "$TMP_DIR/dev-poll.json"
for attempt in $(seq 1 40); do
  STATE=$(db "SELECT state FROM bc_assignment.ai_task WHERE id=$TASK_ID")
  if [ "$STATE" = "SUCCEEDED" ]; then break; fi
  sleep 0.25
done
expect_value "SUCCEEDED" "$(db "SELECT state FROM bc_assignment.ai_task WHERE id=$TASK_ID")" "new task succeeds"
STATUS=$(curl -s -o "$TMP_DIR/review-success.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION_ID/review")
expect_status 200 "$STATUS" "read succeeded review" "$TMP_DIR/review-success.json"
expect_value "SUCCEEDED" "$(field data.taskStatus < "$TMP_DIR/review-success.json")" "succeeded status"
expect_value "true" "$(field data.isMock < "$TMP_DIR/review-success.json")" "mock review flagged"
expect_value "true" "$(field data.feedback.isMock < "$TMP_DIR/review-success.json")" "feedback mock flag"
expect_value "2" "$(db "SELECT COUNT(*) FROM bc_assignment.submission WHERE user_id=$STUDENT_ID AND assignment_id=$CODE_ID")" "old and new submissions retained"
STATUS=$(curl -s -o "$TMP_DIR/teacher-success.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/submissions/$SUBMISSION_ID")
expect_status 200 "$STATUS" "teacher reads succeeded detail" "$TMP_DIR/teacher-success.json"
expect_value "SUCCEEDED" "$(field data.review.taskStatus < "$TMP_DIR/teacher-success.json")" "teacher succeeded status"
EXPECTED_SUMMARY=$(json_get "$TMP_DIR/review-success.json" "data.feedback.summary")
ACTUAL_SUMMARY=$(json_get "$TMP_DIR/teacher-success.json" "data.review.feedback.summary")
expect_value "$EXPECTED_SUMMARY" "$ACTUAL_SUMMARY" "student and teacher see same feedback"
no_provider_leak "$TMP_DIR/review-success.json" "succeeded review"

echo "ALL S11-C ACCEPTANCE CHECKS PASSED"