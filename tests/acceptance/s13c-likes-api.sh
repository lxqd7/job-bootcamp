#!/usr/bin/env bash
# S13-c acceptance: like uniqueness, idempotent cancel, no self-like, access/hidden boundaries and counts.
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

echo "== Setup course and students =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
STATUS=$(curl -s -o "$TMP_DIR/course.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S13C course $STAMP\",\"description\":\"S13C\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
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

register "s13c_s1_$STAMP" "S13CStudent1" "$TMP_DIR/reg1.json"
login "s13c_s1_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/s1.json"
S1_TOKEN=$TOKEN
S1_ID=$USER_ID
buy_course "$S1_TOKEN" "s13c-s1-$STAMP"
register "s13c_s2_$STAMP" "S13CStudent2" "$TMP_DIR/reg2.json"
login "s13c_s2_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/s2.json"
S2_TOKEN=$TOKEN
S2_ID=$USER_ID
buy_course "$S2_TOKEN" "s13c-s2-$STAMP"
register "s13c_s3_$STAMP" "S13CStudent3" "$TMP_DIR/reg3.json"
login "s13c_s3_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/s3.json"
S3_TOKEN=$TOKEN

echo "== Create question, answer and comment =="
STATUS=$(curl -s -o "$TMP_DIR/q.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" -d '{"title":"Like question","body":"Like body","anonymous":false}' \
  "$BASE/api/v1/courses/$COURSE_ID/questions")
expect_status 200 "$STATUS" "create question" "$TMP_DIR/q.json"
QUESTION_ID=$(field data.id < "$TMP_DIR/q.json")
STATUS=$(curl -s -o "$TMP_DIR/a.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" -d '{"body":"Answer to like"}' \
  "$BASE/api/v1/questions/$QUESTION_ID/answers")
expect_status 200 "$STATUS" "create answer" "$TMP_DIR/a.json"
ANSWER_ID=$(field data.id < "$TMP_DIR/a.json")
STATUS=$(curl -s -o "$TMP_DIR/c.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" -d '{"body":"Comment to like"}' \
  "$BASE/api/v1/answers/$ANSWER_ID/comments")
expect_status 200 "$STATUS" "create comment" "$TMP_DIR/c.json"
COMMENT_ID=$(field data.id < "$TMP_DIR/c.json")

echo "== Like uniqueness, idempotent cancel and self-like =="
STATUS=$(curl -s -o "$TMP_DIR/like1.json" -w '%{http_code}' -X PUT -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/likes/ANSWER/$ANSWER_ID")
expect_status 200 "$STATUS" "like answer" "$TMP_DIR/like1.json"
expect_value "true|1" "$(field data.liked < "$TMP_DIR/like1.json")|$(field data.likeCount < "$TMP_DIR/like1.json")" "like count one"
STATUS=$(curl -s -o "$TMP_DIR/like2.json" -w '%{http_code}' -X PUT -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/likes/ANSWER/$ANSWER_ID")
expect_status 200 "$STATUS" "repeat like idempotent" "$TMP_DIR/like2.json"
expect_value "1" "$(field data.likeCount < "$TMP_DIR/like2.json")" "repeat like keeps one"
STATUS=$(curl -s -o "$TMP_DIR/self-like.json" -w '%{http_code}' -X PUT -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/likes/ANSWER/$ANSWER_ID")
expect_status 400 "$STATUS" "cannot self-like answer" "$TMP_DIR/self-like.json"
STATUS=$(curl -s -o "$TMP_DIR/thread-liked.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/questions/$QUESTION_ID")
expect_status 200 "$STATUS" "thread liked flag" "$TMP_DIR/thread-liked.json"
expect_value "1" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.answers[0].likeCount)' "$TMP_DIR/thread-liked.json")" "thread likes"
expect_value "true" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.answers[0].liked)' "$TMP_DIR/thread-liked.json")" "thread liked"
STATUS=$(curl -s -o "$TMP_DIR/unlike1.json" -w '%{http_code}' -X DELETE -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/likes/ANSWER/$ANSWER_ID")
expect_status 200 "$STATUS" "unlike answer" "$TMP_DIR/unlike1.json"
expect_value "false|0" "$(field data.liked < "$TMP_DIR/unlike1.json")|$(field data.likeCount < "$TMP_DIR/unlike1.json")" "unlike count zero"
STATUS=$(curl -s -o "$TMP_DIR/unlike2.json" -w '%{http_code}' -X DELETE -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/likes/ANSWER/$ANSWER_ID")
expect_status 200 "$STATUS" "repeat unlike idempotent" "$TMP_DIR/unlike2.json"
expect_value "0" "$(field data.likeCount < "$TMP_DIR/unlike2.json")" "repeat unlike zero"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.like_record WHERE user_id=$S1_ID AND target_type='ANSWER' AND target_id=$ANSWER_ID")" "one like record retained"

echo "== Concurrent likes from one user stay unique =="
STATUS=$(curl -s -o "$TMP_DIR/a2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" -d '{"body":"Second answer for concurrency"}' \
  "$BASE/api/v1/questions/$QUESTION_ID/answers")
expect_status 200 "$STATUS" "create second answer" "$TMP_DIR/a2.json"
ANSWER2_ID=$(field data.id < "$TMP_DIR/a2.json")
for i in $(seq 1 8); do
  curl -s -o "$TMP_DIR/concurrent-$i.json" -w '%{http_code}' -X PUT \
    -H "Authorization: Bearer $S1_TOKEN" "$BASE/api/v1/likes/ANSWER/$ANSWER2_ID" > "$TMP_DIR/concurrent-$i.status" &
done
wait
for i in $(seq 1 8); do
  expect_value "200" "$(cat "$TMP_DIR/concurrent-$i.status")" "concurrent like $i accepted"
done
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.like_record WHERE user_id=$S1_ID AND target_type='ANSWER' AND target_id=$ANSWER2_ID AND active=TRUE")" "one active like under concurrency"
expect_value "1" "$(db "SELECT like_count FROM bc_interaction.answer WHERE id=$ANSWER2_ID")" "answer count one under concurrency"

echo "== Comment likes and access boundaries =="
STATUS=$(curl -s -o "$TMP_DIR/comment-like.json" -w '%{http_code}' -X PUT -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/likes/COMMENT/$COMMENT_ID")
expect_status 200 "$STATUS" "like comment" "$TMP_DIR/comment-like.json"
expect_value "1" "$(field data.likeCount < "$TMP_DIR/comment-like.json")" "comment like count"
STATUS=$(curl -s -o "$TMP_DIR/comment-self.json" -w '%{http_code}' -X PUT -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/likes/COMMENT/$COMMENT_ID")
expect_status 400 "$STATUS" "cannot self-like comment" "$TMP_DIR/comment-self.json"
STATUS=$(curl -s -o "$TMP_DIR/s3-like.json" -w '%{http_code}' -X PUT -H "Authorization: Bearer $S3_TOKEN" \
  "$BASE/api/v1/likes/ANSWER/$ANSWER_ID")
expect_status 403 "$STATUS" "entitlement required to like" "$TMP_DIR/s3-like.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-like.json" -w '%{http_code}' -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/likes/ANSWER/$ANSWER_ID")
expect_status 403 "$STATUS" "admin cannot like by default" "$TMP_DIR/admin-like.json"

echo "== Deleted comment cannot be liked =="
STATUS=$(curl -s -o "$TMP_DIR/delete-comment.json" -w '%{http_code}' -X DELETE -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/comments/$COMMENT_ID")
expect_status 200 "$STATUS" "delete comment" "$TMP_DIR/delete-comment.json"
STATUS=$(curl -s -o "$TMP_DIR/deleted-comment-like.json" -w '%{http_code}' -X PUT -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/likes/COMMENT/$COMMENT_ID")
expect_status 404 "$STATUS" "cannot like deleted comment" "$TMP_DIR/deleted-comment-like.json"

echo "== Hidden answer cannot be liked =="
STATUS=$(curl -s -o "$TMP_DIR/hide-answer.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"reason":"policy"}' \
  "$BASE/api/v1/admin/content/ANSWER/$ANSWER_ID/hide")
expect_status 200 "$STATUS" "hide answer" "$TMP_DIR/hide-answer.json"
STATUS=$(curl -s -o "$TMP_DIR/hidden-like.json" -w '%{http_code}' -X PUT -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/likes/ANSWER/$ANSWER_ID")
expect_status 404 "$STATUS" "cannot like hidden answer" "$TMP_DIR/hidden-like.json"

echo "ALL S13-C LIKE ACCEPTANCE CHECKS PASSED"