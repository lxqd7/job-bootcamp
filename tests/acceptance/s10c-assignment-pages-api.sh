#!/usr/bin/env bash
# S10-c acceptance: current-version score summary, analysis/correct answers, code blank guard,
# teacher read-only submissions within currently assigned course scope.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
USERNAME="s10c_$STAMP"
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
    -d "{\"title\":\"S10C course $STAMP\",\"description\":\"S10C\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
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
  expect_status 200 "$status" "student quote course" "$TMP_DIR/quote.json"
  quote_id=$(field data.quoteId < "$TMP_DIR/quote.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/quote.json")
  status=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s10c-$STAMP" -H "Authorization: Bearer $TOKEN" \
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

echo "== Setup paid course, student and teacher =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
setup_course
STATUS=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"S10CTester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register student" "$TMP_DIR/register.json"
login "$USERNAME" "$PASSWORD" STUDENT "$TMP_DIR/login.json"
STUDENT_TOKEN=$TOKEN
STUDENT_ID=$USER_ID
TOKEN=$STUDENT_TOKEN
buy_course
login teacher01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/teacher-login.json"
TEACHER_TOKEN=$TOKEN
TEACHER_ID=$USER_ID
TOKEN=$STUDENT_TOKEN

cat > "$TMP_DIR/choice-public.json" <<'JSON'
{"questions":[{"id":"q1","type":"SINGLE","body":"Java 常量关键字？","score":10,"options":[{"id":"A","text":"final"},{"id":"B","text":"static"}]},{"id":"q2","type":"MULTIPLE","body":"JVM 运行时区域？","score":20,"options":[{"id":"A","text":"堆"},{"id":"B","text":"栈"},{"id":"C","text":"方法区"}]}]}
JSON
cat > "$TMP_DIR/choice-answer.json" <<'JSON'
{"answers":{"q1":["A"],"q2":["A","B","C"]},"analysis":{"q1":"final 表示常量。","q2":"三者都是运行时数据区。"}}
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

echo "== Current-version progress and open attempt reuse =="
STATUS=$(curl -s -o "$TMP_DIR/progress0.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/progress")
expect_status 200 "$STATUS" "read initial progress" "$TMP_DIR/progress0.json"
expect_value "1" "$(field data.currentVersionNo < "$TMP_DIR/progress0.json")" "current version v1"
expect_value "false" "$(field data.currentVersionSubmitted < "$TMP_DIR/progress0.json")" "initial current version not submitted"
expect_value "" "$(field data.openAttemptId < "$TMP_DIR/progress0.json")" "no open attempt initially"
expect_value "" "$(field data.recent.id < "$TMP_DIR/progress0.json")" "no recent score initially"
STATUS=$(curl -s -o "$TMP_DIR/attempt1.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/attempts")
expect_status 200 "$STATUS" "start choice attempt" "$TMP_DIR/attempt1.json"
ATTEMPT1=$(field data.id < "$TMP_DIR/attempt1.json")
STATUS=$(curl -s -o "$TMP_DIR/attempt1-again.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/attempts")
expect_status 200 "$STATUS" "reopen assignment in another device" "$TMP_DIR/attempt1-again.json"
expect_value "$ATTEMPT1" "$(field data.id < "$TMP_DIR/attempt1-again.json")" "same open attempt reused"
STATUS=$(curl -s -o "$TMP_DIR/draft1.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" -d '{"answers":{"q1":["A"]},"draftRevision":0}' \
  "$BASE/api/v1/attempts/$ATTEMPT1/draft")
expect_status 200 "$STATUS" "save draft" "$TMP_DIR/draft1.json"
STATUS=$(curl -s -o "$TMP_DIR/progress1.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/progress")
expect_status 200 "$STATUS" "progress after draft" "$TMP_DIR/progress1.json"
expect_value "$ATTEMPT1" "$(field data.openAttemptId < "$TMP_DIR/progress1.json")" "progress exposes open attempt"
expect_value "1" "$(field data.openAttemptRevision < "$TMP_DIR/progress1.json")" "progress exposes draft revision"

echo "== Submit, recent vs highest on current version =="
STATUS=$(curl -s -o "$TMP_DIR/submit-high.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10c-high' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["A"],"q2":["A","B","C"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT1/submissions")
expect_status 200 "$STATUS" "submit full score" "$TMP_DIR/submit-high.json"
HIGH_ID=$(field data.id < "$TMP_DIR/submit-high.json")
expect_value "30" "$(field data.score < "$TMP_DIR/submit-high.json")" "full score"
expect_value "CHAPTER_CHOICE" "$(field data.kind < "$TMP_DIR/submit-high.json")" "submission kind"
expect_value "final 表示常量。" "$(json_get "$TMP_DIR/submit-high.json" "data.analysis.q1")" "analysis returned after submit"
expect_value "A" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1])); console.log(d.data.correctAnswers.q1[0])' "$TMP_DIR/submit-high.json")" "correct answers returned after submit"
if grep -q "analysis" "$TMP_DIR/progress1.json"; then echo "FAIL  progress leaked analysis"; exit 1; fi
echo "PASS  progress does not expose analysis"

STATUS=$(curl -s -o "$TMP_DIR/attempt2.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/attempts")
expect_status 200 "$STATUS" "start second v1 attempt" "$TMP_DIR/attempt2.json"
ATTEMPT2=$(field data.id < "$TMP_DIR/attempt2.json")
STATUS=$(curl -s -o "$TMP_DIR/submit-low.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10c-low' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"q1":["A"],"q2":["A"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT2/submissions")
expect_status 200 "$STATUS" "submit partial score" "$TMP_DIR/submit-low.json"
LOW_ID=$(field data.id < "$TMP_DIR/submit-low.json")
STATUS=$(curl -s -o "$TMP_DIR/progress2.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/progress")
expect_status 200 "$STATUS" "progress after two submissions" "$TMP_DIR/progress2.json"
expect_value "$LOW_ID" "$(field data.recent.id < "$TMP_DIR/progress2.json")" "recent is latest submission"
expect_value "$HIGH_ID" "$(field data.highest.id < "$TMP_DIR/progress2.json")" "highest keeps best score"
expect_value "10" "$(field data.recent.score < "$TMP_DIR/progress2.json")" "recent score"
expect_value "30" "$(field data.highest.score < "$TMP_DIR/progress2.json")" "highest score"

echo "== New version resets current-page scores but keeps history =="
node -e '
  const fs=require("fs"); const p=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  p.questions[0].body="Java 常量关键字（v2）？";
  fs.writeFileSync(process.argv[1], JSON.stringify(p));
' "$TMP_DIR/choice-public.json"
node -e '
  const fs=require("fs"); fs.writeFileSync(process.argv[1], JSON.stringify({
    kind:"CHAPTER_CHOICE", courseId:Number(process.argv[2]), chapterId:Number(process.argv[3]),
    publicBody:JSON.parse(fs.readFileSync(process.argv[4],"utf8")),
    answerBody:{"answers":{"q1":["B"],"q2":["A","B","C"]},"analysis":{"q1":"static 是静态修饰符。","q2":"三者都是运行时数据区。"}},
    revision:Number(process.argv[5])
  }));
' "$TMP_DIR/choice-update.body" "$COURSE_ID" "$CHAPTER_ID" "$TMP_DIR/choice-public.json" "$CHOICE_REV"
STATUS=$(curl -s -o "$TMP_DIR/choice-update.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" --data-binary @"$TMP_DIR/choice-update.body" \
  "$BASE/api/v1/admin/assignments/$CHOICE_ID")
expect_status 200 "$STATUS" "update choice v2" "$TMP_DIR/choice-update.json"
STATUS=$(publish_assignment "$CHOICE_ID" "$(field data.revision < "$TMP_DIR/choice-update.json")" "$TMP_DIR/choice-publish2.json")
expect_status 200 "$STATUS" "publish choice v2" "$TMP_DIR/choice-publish2.json"
STATUS=$(curl -s -o "$TMP_DIR/progress3.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/progress")
expect_status 200 "$STATUS" "progress after v2" "$TMP_DIR/progress3.json"
expect_value "2" "$(field data.currentVersionNo < "$TMP_DIR/progress3.json")" "current version v2"
expect_value "false" "$(field data.currentVersionSubmitted < "$TMP_DIR/progress3.json")" "v2 not submitted"
expect_value "" "$(field data.recent.id < "$TMP_DIR/progress3.json")" "v2 recent empty"
expect_value "" "$(field data.highest.id < "$TMP_DIR/progress3.json")" "v2 highest empty"
STATUS=$(curl -s -o "$TMP_DIR/history.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CHOICE_ID/submissions?page=1&pageSize=20")
expect_status 200 "$STATUS" "history after v2" "$TMP_DIR/history.json"
expect_value "2" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1])); console.log(d.data.items.length)' "$TMP_DIR/history.json")" "history keeps v1 records"
STATUS=$(curl -s -o "$TMP_DIR/old-detail.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/submissions/$HIGH_ID")
expect_status 200 "$STATUS" "old submission still readable" "$TMP_DIR/old-detail.json"
expect_value "1" "$(field data.versionNo < "$TMP_DIR/old-detail.json")" "old submission keeps v1"
expect_value "final 表示常量。" "$(json_get "$TMP_DIR/old-detail.json" "data.analysis.q1")" "old analysis unchanged"

echo "== Course assignment list shows per-user state =="
STATUS=$(curl -s -o "$TMP_DIR/course-assignments.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/assignments")
expect_status 200 "$STATUS" "course assignments" "$TMP_DIR/course-assignments.json"
STATE=$(node -e '
  const d=JSON.parse(require("fs").readFileSync(process.argv[1]));
  const item=d.data.find(x=>String(x.id)===process.argv[2]);
  console.log(`${item.currentVersionSubmitted}|${item.openAttemptId??""}`);
' "$TMP_DIR/course-assignments.json" "$CHOICE_ID")
expect_value "false|" "$STATE" "v2 listed as not submitted"

echo "== Code all-blank guard and reference =="
STATUS=$(curl -s -o "$TMP_DIR/code-attempt.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/assignments/$CODE_ID/attempts")
expect_status 200 "$STATUS" "start code attempt" "$TMP_DIR/code-attempt.json"
CODE_ATTEMPT=$(field data.id < "$TMP_DIR/code-attempt.json")
STATUS=$(curl -s -o "$TMP_DIR/code-blank.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10c-code-blank' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"s1":"   \n"}}' \
  "$BASE/api/v1/attempts/$CODE_ATTEMPT/submissions")
expect_status 422 "$STATUS" "all-blank code rejected" "$TMP_DIR/code-blank.json"
STATUS=$(curl -s -o "$TMP_DIR/code-submit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s10c-code-1' -H "Authorization: Bearer $TOKEN" \
  -d '{"answers":{"s1":"return quantity < 0 ? 0 : unit * quantity;"}}' \
  "$BASE/api/v1/attempts/$CODE_ATTEMPT/submissions")
expect_status 200 "$STATUS" "submit code" "$TMP_DIR/code-submit.json"
CODE_SUBMISSION=$(field data.id < "$TMP_DIR/code-submit.json")
expect_value "COURSE_CODE" "$(field data.kind < "$TMP_DIR/code-submit.json")" "code submission kind"
expect_value "return quantity < 0 ? 0 : unit * quantity;" "$(json_get "$TMP_DIR/code-submit.json" "data.referenceBySlot.s1")" "reference unlocked after submit"
STATUS=$(curl -s -o "$TMP_DIR/mock-run.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/dev/assignments/mock-review")
expect_status 200 "$STATUS" "trigger mock review" "$TMP_DIR/mock-run.json"
for attempt in $(seq 1 30); do
  curl -s -o "$TMP_DIR/review.json" -H "Authorization: Bearer $TOKEN" \
    "$BASE/api/v1/submissions/$CODE_SUBMISSION/review"
  if [ "$(field data.taskStatus < "$TMP_DIR/review.json")" = "SUCCEEDED" ]; then break; fi
  sleep 0.5
done
expect_value "SUCCEEDED" "$(field data.taskStatus < "$TMP_DIR/review.json")" "mock review succeeded"
FEEDBACK_OK=$(node -e '
  const d=JSON.parse(require("fs").readFileSync(process.argv[1]));
  const f=d.data.feedback;
  console.log(Boolean(f && typeof f.summary==="string" && f.summary.length>0 && Array.isArray(f.issues) && f.isMock===true));
' "$TMP_DIR/review.json")
expect_value "true" "$FEEDBACK_OK" "feedback serialized as plain object"

echo "== Teacher read-only scope and detail =="
STATUS=$(curl -s -o "$TMP_DIR/admin-teacher-setup.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "admin reads teacher assignments" "$TMP_DIR/admin-teacher-setup.json"
TEACHER_REV=$(field data.revision < "$TMP_DIR/admin-teacher-setup.json")
STATUS=$(curl -s -o "$TMP_DIR/admin-teacher-save.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[$COURSE_ID],\"routeIds\":[],\"revision\":$TEACHER_REV}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "assign teacher to course" "$TMP_DIR/admin-teacher-save.json"

STATUS=$(curl -s -o "$TMP_DIR/teacher-courses.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/courses")
expect_status 200 "$STATUS" "teacher lists courses" "$TMP_DIR/teacher-courses.json"
if ! grep -q "\"id\":\"$COURSE_ID\"" "$TMP_DIR/teacher-courses.json"; then echo "FAIL  assigned course missing"; exit 1; fi
echo "PASS  assigned course visible to teacher"
STATUS=$(curl -s -o "$TMP_DIR/teacher-assignments.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/courses/$COURSE_ID/assignments")
expect_status 200 "$STATUS" "teacher lists course assignments" "$TMP_DIR/teacher-assignments.json"
expect_value "2" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1])); console.log(d.data.length)' "$TMP_DIR/teacher-assignments.json")" "two assignments for teacher"
STATUS=$(curl -s -o "$TMP_DIR/teacher-submissions.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/courses/$COURSE_ID/submissions?page=1&pageSize=20")
expect_status 200 "$STATUS" "teacher lists course submissions" "$TMP_DIR/teacher-submissions.json"
TOTAL=$(field data.total < "$TMP_DIR/teacher-submissions.json")
if [ "$TOTAL" -lt 3 ]; then echo "FAIL  expected at least 3 submissions, got $TOTAL"; exit 1; fi
echo "PASS  teacher sees course submissions  ($TOTAL)"
NICK=$(node -e '
  const d=JSON.parse(require("fs").readFileSync(process.argv[1]));
  console.log(d.data.items.find(x=>x.studentNickname==="S10CTester")?.studentNickname??"");
' "$TMP_DIR/teacher-submissions.json")
expect_value "S10CTester" "$NICK" "teacher list resolves student nickname"
STATUS=$(curl -s -o "$TMP_DIR/teacher-filter.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/courses/$COURSE_ID/submissions?assignmentId=$CODE_ID&page=1&pageSize=20")
expect_status 200 "$STATUS" "teacher filters by assignment" "$TMP_DIR/teacher-filter.json"
expect_value "1" "$(field data.total < "$TMP_DIR/teacher-filter.json")" "one code submission"
STATUS=$(curl -s -o "$TMP_DIR/teacher-code-detail.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/submissions/$CODE_SUBMISSION")
expect_status 200 "$STATUS" "teacher reads code detail" "$TMP_DIR/teacher-code-detail.json"
expect_value "COURSE_CODE" "$(field data.kind < "$TMP_DIR/teacher-code-detail.json")" "teacher detail kind"
expect_value "SUCCEEDED" "$(field data.review.taskStatus < "$TMP_DIR/teacher-code-detail.json")" "teacher sees AI review"
expect_value "true" "$(field data.review.isMock < "$TMP_DIR/teacher-code-detail.json")" "teacher sees mock flag"
TEACHER_FEEDBACK=$(node -e '
  const d=JSON.parse(require("fs").readFileSync(process.argv[1]));
  const f=d.data.review.feedback;
  console.log(Boolean(f && typeof f.summary==="string" && Array.isArray(f.suggestions)));
' "$TMP_DIR/teacher-code-detail.json")
expect_value "true" "$TEACHER_FEEDBACK" "teacher sees feedback fields"
expect_value "return quantity < 0 ? 0 : unit * quantity;" "$(json_get "$TMP_DIR/teacher-code-detail.json" "data.submission.referenceBySlot.s1")" "teacher sees reference"
STATUS=$(curl -s -o "$TMP_DIR/teacher-choice-detail.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/submissions/$HIGH_ID")
expect_status 200 "$STATUS" "teacher reads choice detail" "$TMP_DIR/teacher-choice-detail.json"
expect_value "final 表示常量。" "$(json_get "$TMP_DIR/teacher-choice-detail.json" "data.submission.analysis.q1")" "teacher sees analysis"
expect_value "A" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1])); console.log(d.data.submission.correctAnswers.q1[0])' "$TMP_DIR/teacher-choice-detail.json")" "teacher sees correct answers"

echo "== Teacher scope, role and removal boundaries =="
STATUS=$(curl -s -o "$TMP_DIR/student-teacher.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT_TOKEN" \
  "$BASE/api/v1/teacher/courses/$COURSE_ID/submissions")
expect_status 403 "$STATUS" "student cannot call teacher API" "$TMP_DIR/student-teacher.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-teacher.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/teacher/courses/$COURSE_ID/submissions")
expect_status 403 "$STATUS" "admin cannot use teacher read API by default" "$TMP_DIR/admin-teacher.json"
STATUS=$(curl -s -o "$TMP_DIR/teacher-unassigned.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/courses/999999/submissions")
expect_status 403 "$STATUS" "teacher cannot read unassigned course" "$TMP_DIR/teacher-unassigned.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-teacher-remove.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[],\"routeIds\":[],\"revision\":$(field data.revision < "$TMP_DIR/admin-teacher-save.json")}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "remove teacher assignment" "$TMP_DIR/admin-teacher-remove.json"
STATUS=$(curl -s -o "$TMP_DIR/teacher-after-removal.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/submissions/$CODE_SUBMISSION")
expect_status 403 "$STATUS" "teacher removed from course is rejected" "$TMP_DIR/teacher-after-removal.json"

echo "ALL S10-C ACCEPTANCE CHECKS PASSED"
echo "choice=$CHOICE_ID code=$CODE_ID course=$COURSE_ID highSubmission=$HIGH_ID codeSubmission=$CODE_SUBMISSION"