#!/usr/bin/env bash
# S13-a acceptance: anonymous projection, teacher identity, owner edit/delete tombstones,
# admin hide with reason and anonymous trace audit, and access boundaries.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
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

login() {
  local username="$1" password="$2" app="$3" out="$4" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
  TOKEN=$(field data.accessToken < "$out")
  USER_ID=$(field data.user.id < "$out")
}

register() {
  local username="$1" nickname="$2" out="$3" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"Passw0rd!\",\"nickname\":\"$nickname\"}" \
    "$BASE/api/v1/auth/register")
  expect_status 200 "$status" "register $username" "$out"
}

buy_course() {
  local token="$1" key="$2" status quote_id quote_hash order_id
  status=$(curl -s -o "$TMP_DIR/quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d "{\"productType\":\"COURSE\",\"productId\":$COURSE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
    "$BASE/api/v1/trade/quotes")
  expect_status 200 "$status" "course quote" "$TMP_DIR/quote.json"
  quote_id=$(field data.quoteId < "$TMP_DIR/quote.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/quote.json")
  status=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $key" -H "Authorization: Bearer $token" \
    -d "{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[]}" \
    "$BASE/api/v1/orders")
  expect_status 200 "$status" "course order" "$TMP_DIR/order.json"
  order_id=$(field data.id < "$TMP_DIR/order.json")
  status=$(curl -s -o "$TMP_DIR/payment.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d '{"channel":"MOCK"}' "$BASE/api/v1/orders/$order_id/payment")
  expect_status 200 "$status" "payment request" "$TMP_DIR/payment.json"
  status=$(curl -s -o "$TMP_DIR/pay.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $token" \
    "$BASE/api/v1/orders/$order_id/payment/mock-pay")
  expect_status 200 "$status" "mock pay" "$TMP_DIR/pay.json"
  for attempt in $(seq 1 40); do
    curl -s -o "$TMP_DIR/order-state.json" -H "Authorization: Bearer $token" "$BASE/api/v1/orders/$order_id"
    if [ "$(field data.fulfillmentState < "$TMP_DIR/order-state.json")" = "DONE" ]; then
      echo "PASS  course fulfilled"
      return 0
    fi
    sleep 0.4
  done
  echo "FAIL  course not fulfilled"; exit 1
}

ask() {
  local token="$1" title="$2" body="$3" anonymous="$4" out="$5" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d "{\"title\":\"$title\",\"body\":\"$body\",\"anonymous\":$anonymous}" \
    "$BASE/api/v1/courses/$COURSE_ID/questions")
  echo "$status"
}

echo "== Setup course, students and teacher =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
STATUS=$(curl -s -o "$TMP_DIR/course.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S13A course $STAMP\",\"description\":\"S13A\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
  "$BASE/api/v1/admin/courses")
expect_status 200 "$STATUS" "create course" "$TMP_DIR/course.json"
COURSE_ID=$(field data.id < "$TMP_DIR/course.json")
STATUS=$(curl -s -o "$TMP_DIR/chapter.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Chapter","sort":1}' \
  "$BASE/api/v1/admin/courses/$COURSE_ID/chapters")
expect_status 200 "$STATUS" "create chapter" "$TMP_DIR/chapter.json"
CHAPTER_ID=$(field data.id < "$TMP_DIR/chapter.json")
STATUS=$(curl -s -o "$TMP_DIR/lesson.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Video","sort":1,"mediaId":1}' \
  "$BASE/api/v1/admin/chapters/$CHAPTER_ID/lessons")
expect_status 200 "$STATUS" "create lesson" "$TMP_DIR/lesson.json"
STATUS=$(curl -s -o "$TMP_DIR/publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":0}' \
  "$BASE/api/v1/admin/courses/$COURSE_ID/publish")
expect_status 200 "$STATUS" "publish course" "$TMP_DIR/publish.json"

register "s13a_s1_$STAMP" "S13AStudent1" "$TMP_DIR/reg1.json"
login "s13a_s1_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/s1.json"
S1_TOKEN=$TOKEN
S1_ID=$USER_ID
buy_course "$S1_TOKEN" "s13a-s1-$STAMP"
register "s13a_s2_$STAMP" "S13AStudent2" "$TMP_DIR/reg2.json"
login "s13a_s2_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/s2.json"
S2_TOKEN=$TOKEN
S2_ID=$USER_ID
buy_course "$S2_TOKEN" "s13a-s2-$STAMP"
register "s13a_s3_$STAMP" "S13AStudent3" "$TMP_DIR/reg3.json"
login "s13a_s3_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/s3.json"
S3_TOKEN=$TOKEN

login teacher01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/teacher1.json"
TEACHER_TOKEN=$TOKEN
TEACHER_ID=$USER_ID
STATUS=$(curl -s -o "$TMP_DIR/t-current.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "read teacher scope" "$TMP_DIR/t-current.json"
STATUS=$(curl -s -o "$TMP_DIR/t-save.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[$COURSE_ID],\"routeIds\":[],\"revision\":$(field data.revision < "$TMP_DIR/t-current.json")}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "assign teacher course" "$TMP_DIR/t-save.json"
login teacher02 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/teacher2.json"
TEACHER2_TOKEN=$TOKEN

echo "== Anonymous question projection =="
STATUS=$(ask "$S1_TOKEN" "Anonymous question" "Anonymous body" true "$TMP_DIR/qa.json")
expect_status 200 "$STATUS" "anonymous question created" "$TMP_DIR/qa.json"
QA=$(field data.id < "$TMP_DIR/qa.json")
expect_value "" "$(field data.authorId < "$TMP_DIR/qa.json")" "anonymous question hides authorId"
expect_value "匿名学员" "$(field data.authorDisplay < "$TMP_DIR/qa.json")" "anonymous display name"
expect_value "true" "$(field data.mine < "$TMP_DIR/qa.json")" "owner sees mine"
STATUS=$(curl -s -o "$TMP_DIR/qa-other.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/questions/$QA")
expect_status 200 "$STATUS" "other student reads anonymous question" "$TMP_DIR/qa-other.json"
expect_value "" "$(field data.question.authorId < "$TMP_DIR/qa-other.json")" "other student sees no authorId"
expect_value "匿名学员" "$(field data.question.authorDisplay < "$TMP_DIR/qa-other.json")" "other student sees anonymous name"
expect_value "false" "$(field data.question.mine < "$TMP_DIR/qa-other.json")" "other student not mine"
STATUS=$(curl -s -o "$TMP_DIR/qa-teacher.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/questions/$QA")
expect_status 200 "$STATUS" "teacher reads anonymous question" "$TMP_DIR/qa-teacher.json"
expect_value "" "$(field data.question.authorId < "$TMP_DIR/qa-teacher.json")" "teacher sees no authorId"
STATUS=$(curl -s -o "$TMP_DIR/qa-list.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/questions")
expect_status 200 "$STATUS" "question list" "$TMP_DIR/qa-list.json"
expect_value "1" "$(field data.total < "$TMP_DIR/qa-list.json")" "one visible question"
expect_value "匿名学员" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.items[0].authorDisplay)' "$TMP_DIR/qa-list.json")" "list anonymous name"

echo "== Owner edit, stale revision and non-owner rejection =="
STATUS=$(curl -s -o "$TMP_DIR/qa-edit.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" \
  -d '{"title":"Anonymous question v2","body":"Anonymous body v2","anonymous":true,"revision":0}' \
  "$BASE/api/v1/questions/$QA")
expect_status 200 "$STATUS" "owner edits question" "$TMP_DIR/qa-edit.json"
QA_REV=$(field data.revision < "$TMP_DIR/qa-edit.json")
STATUS=$(curl -s -o "$TMP_DIR/qa-stale.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" \
  -d '{"title":"stale","body":"stale","anonymous":true,"revision":0}' \
  "$BASE/api/v1/questions/$QA")
expect_status 409 "$STATUS" "stale question revision rejected" "$TMP_DIR/qa-stale.json"
STATUS=$(curl -s -o "$TMP_DIR/qa-nonowner.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" \
  -d "{\"title\":\"hack\",\"body\":\"hack\",\"anonymous\":true,\"revision\":$QA_REV}" \
  "$BASE/api/v1/questions/$QA")
expect_status 403 "$STATUS" "non-owner cannot edit question" "$TMP_DIR/qa-nonowner.json"

echo "== Student answer, teacher answer, comments =="
STATUS=$(curl -s -o "$TMP_DIR/ans-student.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" -d '{"body":"Student answer."}' \
  "$BASE/api/v1/questions/$QA/answers")
expect_status 200 "$STATUS" "student answer" "$TMP_DIR/ans-student.json"
ANS_STUDENT=$(field data.id < "$TMP_DIR/ans-student.json")
expect_value "STUDENT" "$(field data.authorRole < "$TMP_DIR/ans-student.json")" "student answer role"
expect_value "S13AStudent2" "$(field data.authorDisplay < "$TMP_DIR/ans-student.json")" "student answer name"
STATUS=$(curl -s -o "$TMP_DIR/ans-teacher.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEACHER_TOKEN" -d '{"body":"Teacher answer."}' \
  "$BASE/api/v1/questions/$QA/answers")
expect_status 200 "$STATUS" "teacher answer" "$TMP_DIR/ans-teacher.json"
ANS_TEACHER=$(field data.id < "$TMP_DIR/ans-teacher.json")
expect_value "TEACHER" "$(field data.authorRole < "$TMP_DIR/ans-teacher.json")" "teacher answer role"
expect_value "教师一" "$(field data.authorDisplay < "$TMP_DIR/ans-teacher.json")" "teacher identity displayed"
STATUS=$(curl -s -o "$TMP_DIR/thread.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/questions/$QA")
expect_status 200 "$STATUS" "thread view" "$TMP_DIR/thread.json"
expect_value "2" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.answers.length)' "$TMP_DIR/thread.json")" "two answers"
STATUS=$(curl -s -o "$TMP_DIR/comment.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" -d '{"body":"Follow-up question."}' \
  "$BASE/api/v1/answers/$ANS_TEACHER/comments")
expect_status 200 "$STATUS" "student comment" "$TMP_DIR/comment.json"
COMMENT=$(field data.id < "$TMP_DIR/comment.json")
STATUS=$(curl -s -o "$TMP_DIR/comments.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/answers/$ANS_TEACHER/comments")
expect_status 200 "$STATUS" "list comments" "$TMP_DIR/comments.json"
expect_value "1" "$(field data.total < "$TMP_DIR/comments.json")" "one comment"
STATUS=$(curl -s -o "$TMP_DIR/teacher2-answer.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEACHER2_TOKEN" -d '{"body":"Out of scope answer"}' \
  "$BASE/api/v1/questions/$QA/answers")
expect_status 403 "$STATUS" "unassigned teacher cannot answer" "$TMP_DIR/teacher2-answer.json"
STATUS=$(curl -s -o "$TMP_DIR/s3-list.json" -w '%{http_code}' -H "Authorization: Bearer $S3_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/questions")
expect_status 403 "$STATUS" "student without entitlement rejected" "$TMP_DIR/s3-list.json"

echo "== Owner edits/deletes answer; deleted answer blocks comments =="
STATUS=$(curl -s -o "$TMP_DIR/ans-edit.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" -d '{"body":"Student answer v2","revision":0}' \
  "$BASE/api/v1/answers/$ANS_STUDENT")
expect_status 200 "$STATUS" "owner edits answer" "$TMP_DIR/ans-edit.json"
ANS_STUDENT_REV=$(field data.revision < "$TMP_DIR/ans-edit.json")
STATUS=$(curl -s -o "$TMP_DIR/ans-del.json" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $S2_TOKEN" "$BASE/api/v1/answers/$ANS_STUDENT")
expect_status 200 "$STATUS" "owner deletes answer" "$TMP_DIR/ans-del.json"
STATUS=$(curl -s -o "$TMP_DIR/thread2.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/questions/$QA")
expect_status 200 "$STATUS" "thread after answer delete" "$TMP_DIR/thread2.json"
expect_value "1" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.answers.length)' "$TMP_DIR/thread2.json")" "deleted answer removed from list"
expect_value "DELETED" "$(db "SELECT state FROM bc_interaction.answer WHERE id=$ANS_STUDENT")" "answer tombstone state"

echo "== Admin hide with reason and anonymous trace audit =="
STATUS=$(ask "$S2_TOKEN" "Hidden question" "Hidden body" false "$TMP_DIR/q2.json")
expect_status 200 "$STATUS" "create normal question" "$TMP_DIR/q2.json"
Q2=$(field data.id < "$TMP_DIR/q2.json")
STATUS=$(curl -s -o "$TMP_DIR/q2-answer.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" -d '{"body":"Hidden answer"}' \
  "$BASE/api/v1/questions/$Q2/answers")
expect_status 200 "$STATUS" "answer for hide" "$TMP_DIR/q2-answer.json"
Q2_ANSWER=$(field data.id < "$TMP_DIR/q2-answer.json")
STATUS=$(curl -s -o "$TMP_DIR/hide-noreason.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{}' \
  "$BASE/api/v1/admin/content/ANSWER/$Q2_ANSWER/hide")
expect_status 400 "$STATUS" "hide requires reason" "$TMP_DIR/hide-noreason.json"
STATUS=$(curl -s -o "$TMP_DIR/hide-student.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" -d '{"reason":"x"}' \
  "$BASE/api/v1/admin/content/ANSWER/$Q2_ANSWER/hide")
expect_status 403 "$STATUS" "student cannot hide content" "$TMP_DIR/hide-student.json"
STATUS=$(curl -s -o "$TMP_DIR/hide-answer.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"reason":"violation"}' \
  "$BASE/api/v1/admin/content/ANSWER/$Q2_ANSWER/hide")
expect_status 200 "$STATUS" "admin hides answer" "$TMP_DIR/hide-answer.json"
expect_value "HIDDEN" "$(db "SELECT state FROM bc_interaction.answer WHERE id=$Q2_ANSWER")" "answer hidden"
STATUS=$(curl -s -o "$TMP_DIR/q2-thread.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/questions/$Q2")
expect_status 200 "$STATUS" "thread after answer hide" "$TMP_DIR/q2-thread.json"
expect_value "0" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.answers.length)' "$TMP_DIR/q2-thread.json")" "hidden answer removed"
STATUS=$(curl -s -o "$TMP_DIR/hide-question.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"reason":"question violation"}' \
  "$BASE/api/v1/admin/content/QUESTION/$Q2/hide")
expect_status 200 "$STATUS" "admin hides question" "$TMP_DIR/hide-question.json"
STATUS=$(curl -s -o "$TMP_DIR/q2-after.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/questions/$Q2")
expect_status 404 "$STATUS" "hidden question inaccessible" "$TMP_DIR/q2-after.json"
STATUS=$(curl -s -o "$TMP_DIR/trace-teacher.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/admin/questions/$QA/author?reason=x")
expect_status 403 "$STATUS" "teacher cannot trace anonymous author" "$TMP_DIR/trace-teacher.json"
STATUS=$(curl -s -o "$TMP_DIR/trace.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/questions/$QA/author?reason=report")
expect_status 200 "$STATUS" "admin traces anonymous author" "$TMP_DIR/trace.json"
expect_value "$S1_ID" "$(field data.authorId < "$TMP_DIR/trace.json")" "trace returns real author"
expect_value "S13AStudent1" "$(field data.nickname < "$TMP_DIR/trace.json")" "trace returns nickname"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.moderation_action WHERE action='TRACE_AUTHOR' AND target_id=$QA AND admin_id=$(field data.user.id < "$TMP_DIR/admin.json")")" "trace audit recorded"
expect_value "2" "$(db "SELECT COUNT(*) FROM bc_interaction.moderation_action WHERE action='HIDE' AND target_id IN ($Q2,$Q2_ANSWER)")" "hide audit recorded"

echo "== Owner deletes question: thread tombstone and children retained =="
STATUS=$(ask "$S1_TOKEN" "Delete question" "Delete body" false "$TMP_DIR/q3.json")
expect_status 200 "$STATUS" "create question for delete" "$TMP_DIR/q3.json"
Q3=$(field data.id < "$TMP_DIR/q3.json")
STATUS=$(curl -s -o "$TMP_DIR/q3-answer.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" -d '{"body":"Retained answer"}' \
  "$BASE/api/v1/questions/$Q3/answers")
expect_status 200 "$STATUS" "answer retained on delete" "$TMP_DIR/q3-answer.json"
Q3_ANSWER=$(field data.id < "$TMP_DIR/q3-answer.json")
STATUS=$(curl -s -o "$TMP_DIR/q3-delete.json" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $S1_TOKEN" "$BASE/api/v1/questions/$Q3")
expect_status 200 "$STATUS" "owner deletes question" "$TMP_DIR/q3-delete.json"
STATUS=$(curl -s -o "$TMP_DIR/q3-after.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/questions/$Q3")
expect_status 404 "$STATUS" "deleted question inaccessible" "$TMP_DIR/q3-after.json"
STATUS=$(curl -s -o "$TMP_DIR/q3-answers.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/questions/$Q3/answers")
expect_status 404 "$STATUS" "deleted question blocks answers" "$TMP_DIR/q3-answers.json"
expect_value "VISIBLE" "$(db "SELECT state FROM bc_interaction.answer WHERE id=$Q3_ANSWER")" "answer physically retained"
expect_value "DELETED" "$(db "SELECT state FROM bc_interaction.question WHERE id=$Q3")" "question tombstone"

echo "== Input and role boundaries =="
STATUS=$(ask "$S1_TOKEN" "" "empty body" true "$TMP_DIR/invalid-title.json")
expect_status 400 "$STATUS" "empty title rejected" "$TMP_DIR/invalid-title.json"
STATUS=$(ask "$S1_TOKEN" "$(printf 'x%.0s' $(seq 1 101))" "long body" true "$TMP_DIR/long-title.json")
expect_status 400 "$STATUS" "too-long title rejected" "$TMP_DIR/long-title.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-read.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/questions/$QA")
expect_status 403 "$STATUS" "admin has no default student read" "$TMP_DIR/admin-read.json"

echo "ALL S13-A ACCEPTANCE CHECKS PASSED"