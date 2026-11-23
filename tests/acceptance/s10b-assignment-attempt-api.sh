#!/usr/bin/env bash
# S10-b acceptance: attempts, version-bound grading, draft conflict, submissions, mock code review.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
USERNAME="s10b_$STAMP"
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
  local status body
  status=$(curl -s -o "$TMP_DIR/course.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"S10B course $STAMP\",\"description\":\"S10B\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create course" "$TMP_DIR/course.json"
  COURSE_ID=$(field data.id < "$TMP_DIR/course.json")
  COURSE_REV=$(field data.revision < "$TMP_DIR/course.json")
  status=$(curl -s -o "$TMP_DIR/chapter.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Chapter","sort":1}' \
    "$BASE/api/v1/admin/courses/$COURSE_ID/chapters")
  expect_status 200 "$status" "create chapter" "$TMP_DIR/chapter.json"
  CHAPTER_ID=$(field data.id < "$TMP_DIR/chapter.json")
  body=$(curl -s -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{"title":"Video","sort":1,"mediaId":1}' \
    "$BASE/api/v1/admin/chapters/$CHAPTER_ID/lessons")
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
  expect_status 200 "$status" "student quote course" "$TMP_DIR/quote.json"
  quote_id=$(field data.quoteId < "$TMP_DIR/quote.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/quote.json")
  status=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s10b-$STAMP" -H "Authorization: Bearer $TOKEN" \
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

create_assignment() {
  local kind="$1" chapter="$2" public_file="$3" answer_file="$4" out="$5" status
  node -e '
    const fs=require("fs");
    fs.writeFileSync(process.argv[1], JSON.stringify({
      kind: process.argv[2], courseId:Number(process.argv[3]),
      chapterId:process.argv[4]===""?null:Number(process.argv[4]),
      publicBody:JSON.parse(fs.readFileSync(process.argv[5],"utf8")),
      answerBody:JSON.parse(fs.readFileSync(process.argv[6],"utf8"))
    }));
  ' "$out.body" "$kind" "$COURSE_ID" "$chapter" "$public_file" "$answer_file"
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" --data-binary @"$out.body" \
    "$BASE/api/v1/admin/assignments")
  echo "$status"
}

publish_assignment() {
  local id="$1" revision="$2" out="$3" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$revision}" \
    "$BASE/api/v1/admin/assignments/$id/publish")
  echo "$status"
}

echo "== Setup paid course and student =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
setup_course
STATUS=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"S10BTester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register student" "$TMP_DIR/register.json"
login "$USERNAME" "$PASSWORD" STUDENT "$TMP_DIR/login.json"
STUDENT_TOKEN=$TOKEN
STUDENT_ID=$USER_ID
TOKEN=$STUDENT_TOKEN
buy_course

cat > "$TMP_DIR/choice-public.json" <<'JSON'
{"questions":[{"id":"q1","type":"SINGLE","body":"Java 常量关键字？","score":10,"options":[{"id":"A","text":"final"},{"id":"B","text":"static"}]},{"id":"q2","type":"MULTIPLE","body":"JVM 运行时区域？","score":20,"options":[{"id":"A","text":"堆"},{"id":"B","text":"栈"},{"id":"C","text":"方法区"}]}]}
JSON
cat > "$TMP_DIR/choice-answer.json" <<'JSON'
{"answers":{"q1":["A"],"q2":["A","B","C"]},"analysis":{"q1":"final","q2":"三者都是"}}
JSON
cat > "$TMP_DIR/choice-v2-answer.json" <<'JSON'
{"answers":{"q1":["B"],"q2":["A","B","C"]},"analysis":{"q1":"static","q2":"三者都是"}}
JSON
STATUS=$(create_assignment CHAPTER_CHOICE "$CHAPTER_ID" "$TMP_DIR/choice-public.json" "$TMP_DIR/choice-answer.json" "$TMP_DIR/choice.json")
expect_status 200 "$STATUS" "create choice assignment" "$TMP_DIR/choice.json"
CHOICE_ID=$(field data.id < "$TMP_DIR/choice.json")
CHOICE_REV=$(field data.revision < "$TMP_DIR/choice.json")
STATUS=$(publish_assignment "$CHOICE_ID" "$CHOICE_REV" "$TMP_DIR/choice-publish1.json")
expect_status 200 "$STATUS" "publish choice v1" "$TMP_DIR/choice-publish1.json"
CHOICE_REV=$(field data.revision < "$TMP_DIR/choice-publish1.json")

cat > "$TMP_DIR/code-public.json" <<'JSON'
{"scenario":"订单金额计算","requirements":"补全 calculate，数量为负返回 0。","segments":[{"text":"public class Calc {\n"},{"slotId":"s1","initialCode":"// TODO\n"},{"text":"}\n"}]}
JSON
cat > "$TMP_DIR/code-answer.json" <<'JSON'
{"referenceBySlot":{"s1":"return quantity < 0 ? 0 : unit * quantity;\n"},"rubric":"负数、long、无编译错误。"}
JSON
STATUS=$(create_assignment COURSE_CODE "" "$TMP_DIR/code-public.json" "$TMP_DIR/code-answer.json" "$TMP_DIR/code.json")
expect_status 200 "$STATUS" "create code assignment" "$TMP_DIR/code.json"
CODE_ID=$(field data.id < "$TMP_DIR/code.json")
CODE_REV=$(field data.revision < "$TMP_DIR/code.json")
STATUS=$(publish_assignment "$CODE_ID" "$CODE_REV" "$TMP_DIR/code-publish.json")
expect_status 200 "$STATUS" "publish code assignment" "$TMP_DIR/code-publish.json"
CODE_REV=$(field data.revision < "$TMP_DIR/code-publish.json")

echo "== Student assignment listing and no answer leak =="
STATUS=$(curl -s -o "$TMP_DIR/list.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/assignments")
expect_status 200 "$STATUS" "student lists course assignments" "$TMP_DIR/list.json"
COUNT=$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1])); console.log((d.data||[]).length)' "$TMP_DIR/list.json")
expect_value "2" "$COUNT" "two formal assignments"
STATUS=$(curl -s -o "$TMP_DIR/assignment.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID")
expect_status 200 "$STATUS" "student reads assignment" "$TMP_DIR/assignment.json"
if grep -qi "correctOptionIds\|analysis\|answers" "$TMP_DIR/assignment.json"; then echo "FAIL  answer leaked in assignment DTO"; exit 1; fi
echo "PASS  assignment DTO has no standard answers"

echo "== Choice attempt, draft conflict and version-bound grading =="
STATUS=$(curl -s -o "$TMP_DIR/attempt1.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/attempts")
expect_status 200 "$STATUS" "start choice attempt" "$TMP_DIR/attempt1.json"
ATTEMPT1=$(field data.id < "$TMP_DIR/attempt1.json")
ATTEMPT1_VERSION=$(field data.versionId < "$TMP_DIR/attempt1.json")
STATUS=$(curl -s -o "$TMP_DIR/draft1.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["A"]},"draftRevision":0}' \
  "$BASE/api/v1/attempts/$ATTEMPT1/draft")
expect_status 200 "$STATUS" "save draft revision 1" "$TMP_DIR/draft1.json"
expect_value "1" "$(field data.draftRevision < "$TMP_DIR/draft1.json")" "draft revision"
STATUS=$(curl -s -o "$TMP_DIR/draft-conflict.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["B"]},"draftRevision":0}' \
  "$BASE/api/v1/attempts/$ATTEMPT1/draft")
expect_status 409 "$STATUS" "stale draft revision rejected" "$TMP_DIR/draft-conflict.json"
DRAFT_STATE=$(db "SELECT draft_revision, JSON_UNQUOTE(JSON_EXTRACT(draft_json,'$.q1[0]')) FROM bc_assignment.attempt WHERE id=$ATTEMPT1" | tr '\t' '|')
expect_value "1|A" "$DRAFT_STATE" "draft content not overwritten"
SUBMISSIONS_AFTER_DRAFT=$(db "SELECT COUNT(*) FROM bc_assignment.submission WHERE attempt_id=$ATTEMPT1")
expect_value "0" "$SUBMISSIONS_AFTER_DRAFT" "draft does not create submission"

node -e '
  const fs=require("fs"); const p=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  p.questions[0].body="Java 常量关键字（v2）？";
  fs.writeFileSync(process.argv[1], JSON.stringify(p));
' "$TMP_DIR/choice-public.json"
node -e '
  const fs=require("fs"); fs.writeFileSync(process.argv[1], JSON.stringify({
    kind:"CHAPTER_CHOICE", courseId:Number(process.argv[2]), chapterId:Number(process.argv[3]),
    publicBody:JSON.parse(fs.readFileSync(process.argv[4],"utf8")),
    answerBody:JSON.parse(fs.readFileSync(process.argv[5],"utf8")),
    revision:Number(process.argv[6])
  }));
' "$TMP_DIR/choice-update.body" "$COURSE_ID" "$CHAPTER_ID" "$TMP_DIR/choice-public.json" "$TMP_DIR/choice-v2-answer.json" "$CHOICE_REV"
STATUS=$(curl -s -o "$TMP_DIR/choice-update.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" --data-binary @"$TMP_DIR/choice-update.body" \
  "$BASE/api/v1/admin/assignments/$CHOICE_ID")
expect_status 200 "$STATUS" "update choice v2 draft" "$TMP_DIR/choice-update.json"
STATUS=$(publish_assignment "$CHOICE_ID" "$(field data.revision < "$TMP_DIR/choice-update.json")" "$TMP_DIR/choice-publish2.json")
expect_status 200 "$STATUS" "publish choice v2" "$TMP_DIR/choice-publish2.json"
STATUS=$(curl -s -o "$TMP_DIR/submit1.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-choice-1' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["A","B"],"q2":["A","B","C"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT1/submissions")
# q1 was single; sending two values is invalid, verify 422 then use valid v1 answer
expect_status 422 "$STATUS" "invalid option set rejected" "$TMP_DIR/submit1.json"
STATUS=$(curl -s -o "$TMP_DIR/submit1.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-choice-1' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["A"],"q2":["A","B","C"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT1/submissions")
expect_status 200 "$STATUS" "submit old version attempt" "$TMP_DIR/submit1.json"
SUBMISSION1=$(field data.id < "$TMP_DIR/submit1.json")
expect_value "30" "$(field data.score < "$TMP_DIR/submit1.json")" "v1 score"
expect_value "30" "$(field data.maxScore < "$TMP_DIR/submit1.json")" "v1 max score"
expect_value "GRADED" "$(field data.reviewState < "$TMP_DIR/submit1.json")" "choice graded"
STATUS=$(curl -s -o "$TMP_DIR/submit-replay.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-choice-1' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["A"],"q2":["A","B","C"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT1/submissions")
expect_status 200 "$STATUS" "same idempotency key replay" "$TMP_DIR/submit-replay.json"
expect_value "$SUBMISSION1" "$(field data.id < "$TMP_DIR/submit-replay.json")" "same submission id"
STATUS=$(curl -s -o "$TMP_DIR/submit-newkey.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-choice-2' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["A"],"q2":["A"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT1/submissions")
expect_status 409 "$STATUS" "second submission of attempt rejected" "$TMP_DIR/submit-newkey.json"

echo "== Wrong answers score zero per question; new attempt uses v2 answer =="
STATUS=$(curl -s -o "$TMP_DIR/attempt2.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/attempts")
expect_status 200 "$STATUS" "start v2 attempt" "$TMP_DIR/attempt2.json"
ATTEMPT2=$(field data.id < "$TMP_DIR/attempt2.json")
STATUS=$(curl -s -o "$TMP_DIR/submit2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-choice-v2' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["B"],"q2":["A","B","C"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT2/submissions")
expect_status 200 "$STATUS" "submit v2 attempt" "$TMP_DIR/submit2.json"
expect_value "30" "$(field data.score < "$TMP_DIR/submit2.json")" "v2 answer score"
STATUS=$(curl -s -o "$TMP_DIR/history.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/submissions?page=1&pageSize=20")
expect_status 200 "$STATUS" "submission history" "$TMP_DIR/history.json"
HISTORY_COUNT=$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1])); console.log((d.data.items||[]).length)' "$TMP_DIR/history.json")
expect_value "2" "$HISTORY_COUNT" "two history records"

echo "== Wrong multi and unanswered questions score zero =="
STATUS=$(curl -s -o "$TMP_DIR/attempt-wrong.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/attempts")
expect_status 200 "$STATUS" "start wrong-answer attempt" "$TMP_DIR/attempt-wrong.json"
ATTEMPT_WRONG=$(field data.id < "$TMP_DIR/attempt-wrong.json")
STATUS=$(curl -s -o "$TMP_DIR/submit-wrong.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-choice-wrong' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["B"],"q2":["A"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT_WRONG/submissions")
expect_status 200 "$STATUS" "submit partial multi choice" "$TMP_DIR/submit-wrong.json"
expect_value "10" "$(field data.score < "$TMP_DIR/submit-wrong.json")" "multi must be all-correct"
STATUS=$(curl -s -o "$TMP_DIR/attempt-empty.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/attempts")
expect_status 200 "$STATUS" "start empty-answer attempt" "$TMP_DIR/attempt-empty.json"
ATTEMPT_EMPTY=$(field data.id < "$TMP_DIR/attempt-empty.json")
STATUS=$(curl -s -o "$TMP_DIR/submit-empty.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-choice-empty' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{}}' \
  "$BASE/api/v1/attempts/$ATTEMPT_EMPTY/submissions")
expect_status 200 "$STATUS" "submit empty choice" "$TMP_DIR/submit-empty.json"
expect_value "0" "$(field data.score < "$TMP_DIR/submit-empty.json")" "unanswered score zero"
expect_value "30" "$(field data.maxScore < "$TMP_DIR/submit-empty.json")" "max score remains"

echo "== Offline rejects a started-but-unsubmitted attempt =="
STATUS=$(curl -s -o "$TMP_DIR/attempt3.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/attempts")
expect_status 200 "$STATUS" "start attempt before offline" "$TMP_DIR/attempt3.json"
ATTEMPT3=$(field data.id < "$TMP_DIR/attempt3.json")
CURRENT_REV=$(field data.revision < "$TMP_DIR/choice-publish2.json")
STATUS=$(curl -s -o "$TMP_DIR/offline.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$CURRENT_REV}" \
  "$BASE/api/v1/admin/assignments/$CHOICE_ID/offline")
expect_status 200 "$STATUS" "offline choice" "$TMP_DIR/offline.json"
STATUS=$(curl -s -o "$TMP_DIR/submit3.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-choice-offline' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["B"],"q2":["A","B","C"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT3/submissions")
expect_status 409 "$STATUS" "offline assignment rejects submission" "$TMP_DIR/submit3.json"

echo "== Code attempt, mock async review, reference unlock and single-task slot =="
STATUS=$(curl -s -o "$TMP_DIR/code-attempt.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CODE_ID/attempts")
expect_status 200 "$STATUS" "start code attempt" "$TMP_DIR/code-attempt.json"
CODE_ATTEMPT=$(field data.id < "$TMP_DIR/code-attempt.json")
STATUS=$(curl -s -o "$TMP_DIR/code-draft.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"s1":"return 0;"},"draftRevision":0}' \
  "$BASE/api/v1/attempts/$CODE_ATTEMPT/draft")
expect_status 200 "$STATUS" "save code draft" "$TMP_DIR/code-draft.json"
SLOT_BEFORE=$(db "SELECT active_task_id IS NULL FROM bc_assignment.ai_user_slot WHERE user_id=$STUDENT_ID" || true)
if [ -n "$SLOT_BEFORE" ]; then expect_value "1" "$SLOT_BEFORE" "draft does not occupy AI slot"; fi
STATUS=$(curl -s -o "$TMP_DIR/code-submit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-code-1' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"s1":"return quantity < 0 ? 0 : unit * quantity;"}}' \
  "$BASE/api/v1/attempts/$CODE_ATTEMPT/submissions")
expect_status 200 "$STATUS" "submit code" "$TMP_DIR/code-submit.json"
CODE_SUBMISSION=$(field data.id < "$TMP_DIR/code-submit.json")
expect_value "PENDING_REVIEW" "$(field data.reviewState < "$TMP_DIR/code-submit.json")" "code pending review"
REFERENCE=$(json_get "$TMP_DIR/code-submit.json" "data.referenceBySlot.s1")
if [ -z "$REFERENCE" ]; then echo "FAIL  code submission does not unlock reference"; exit 1; fi
echo "PASS  code submission unlocks reference"
STATUS=$(curl -s -o "$TMP_DIR/code-attempt2.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CODE_ID/attempts")
expect_status 200 "$STATUS" "start second code attempt" "$TMP_DIR/code-attempt2.json"
CODE_ATTEMPT2=$(field data.id < "$TMP_DIR/code-attempt2.json")
STATUS=$(curl -s -o "$TMP_DIR/code-submit2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-code-2' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"s1":"return quantity < 0 ? 0 : unit * quantity;"}}' \
  "$BASE/api/v1/attempts/$CODE_ATTEMPT2/submissions")
expect_status 409 "$STATUS" "second code task blocked by active slot" "$TMP_DIR/code-submit2.json"
expect_value "AI_REVIEW_BUSY" "$(field code < "$TMP_DIR/code-submit2.json")" "AI busy error code"
STATUS=$(curl -s -o "$TMP_DIR/mock-run.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/dev/assignments/mock-review")
expect_status 200 "$STATUS" "trigger explicit mock review" "$TMP_DIR/mock-run.json"
for attempt in $(seq 1 30); do
  curl -s -o "$TMP_DIR/review.json" -H "Authorization: Bearer $TOKEN" \
    "$BASE/api/v1/submissions/$CODE_SUBMISSION/review"
  if [ "$(field data.taskStatus < "$TMP_DIR/review.json")" = "SUCCEEDED" ]; then break; fi
  sleep 0.5
done
expect_value "SUCCEEDED" "$(field data.taskStatus < "$TMP_DIR/review.json")" "mock review succeeded"
expect_value "true" "$(field data.isMock < "$TMP_DIR/review.json")" "mock review marked"
STATUS=$(curl -s -o "$TMP_DIR/code-submission.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$CODE_SUBMISSION")
expect_status 200 "$STATUS" "read code submission" "$TMP_DIR/code-submission.json"
expect_value "GRADED" "$(field data.reviewState < "$TMP_DIR/code-submission.json")" "code submission graded by mock"
ACTIVE_SLOT=$(db "SELECT active_task_id IS NULL FROM bc_assignment.ai_user_slot WHERE user_id=$STUDENT_ID")
expect_value "1" "$ACTIVE_SLOT" "slot released after review"

echo "== Access and role boundaries =="
OTHER="s10bo_$STAMP"
STATUS=$(curl -s -o "$TMP_DIR/other-register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$OTHER\",\"password\":\"$PASSWORD\",\"nickname\":\"Other\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register other student" "$TMP_DIR/other-register.json"
login "$OTHER" "$PASSWORD" STUDENT "$TMP_DIR/other-login.json"
OTHER_TOKEN=$TOKEN
STATUS=$(curl -s -o "$TMP_DIR/no-access.json" -w '%{http_code}' -H "Authorization: Bearer $OTHER_TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID")
expect_status 403 "$STATUS" "student without entitlement rejected" "$TMP_DIR/no-access.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-submit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10b-admin' -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"answers":{}}' "$BASE/api/v1/attempts/$ATTEMPT1/submissions")
expect_status 403 "$STATUS" "admin cannot submit student attempt" "$TMP_DIR/admin-submit.json"

echo "ALL S10-B ACCEPTANCE CHECKS PASSED"
echo "choice=$CHOICE_ID code=$CODE_ID course=$COURSE_ID submission=$SUBMISSION1"