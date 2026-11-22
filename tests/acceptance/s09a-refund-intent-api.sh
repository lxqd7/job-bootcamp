#!/usr/bin/env bash
# S09-a acceptance: refund intent sequence, fixed lesson basis, source freeze and trade eligibility.
# Requires RabbitMQ and services started with OUTBOX_TRANSPORT=rabbit OUTBOX_RELAY_ENABLED=true.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
USERNAME="s09a_$STAMP"
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

login() {
  local username="$1" password="$2" app="$3" out="$4"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
  TOKEN=$(field data.accessToken < "$out")
  USER_ID=$(field data.user.id < "$out")
}

create_course() {
  local title="$1" count="$2" out="$3"
  local status body
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S09A\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create course $title" "$out"
  COURSE_ID=$(field data.id < "$out")
  COURSE_REV=$(field data.revision < "$out")
  status=$(curl -s -o "$out.chapter" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Chapter","sort":1}' \
    "$BASE/api/v1/admin/courses/$COURSE_ID/chapters")
  expect_status 200 "$status" "create chapter" "$out.chapter"
  CHAPTER_ID=$(field data.id < "$out.chapter")
  LESSON_IDS=""
  LESSON_REVS=""
  for i in $(seq 1 "$count"); do
    body=$(curl -s -X POST -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -d "{\"title\":\"Video $i\",\"sort\":$i,\"mediaId\":1}" \
      "$BASE/api/v1/admin/chapters/$CHAPTER_ID/lessons")
    LESSON_IDS="$LESSON_IDS $(field data.id <<< "$body")"
    LESSON_REVS="$LESSON_REVS $(field data.revision <<< "$body")"
  done
  status=$(curl -s -o "$out.publish" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$COURSE_REV}" \
    "$BASE/api/v1/admin/courses/$COURSE_ID/publish")
  expect_status 200 "$status" "publish course $title" "$out.publish"
}

buy_course() {
  local course_id="$1" key="$2"
  local status quote order_id order_no
  status=$(curl -s -o "$TMP_DIR/quote-$key.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"productType\":\"COURSE\",\"productId\":$course_id,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
    "$BASE/api/v1/trade/quotes")
  expect_status 200 "$status" "quote course $course_id" "$TMP_DIR/quote-$key.json"
  local quote_id quote_hash
  quote_id=$(field data.quoteId < "$TMP_DIR/quote-$key.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/quote-$key.json")
  status=$(curl -s -o "$TMP_DIR/order-$key.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s09a-$key-$STAMP" -H "Authorization: Bearer $TOKEN" \
    -d "{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[]}" \
    "$BASE/api/v1/orders")
  expect_status 200 "$status" "order course $course_id" "$TMP_DIR/order-$key.json"
  order_id=$(field data.id < "$TMP_DIR/order-$key.json")
  status=$(curl -s -o "$TMP_DIR/payment-$key.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" -d '{"channel":"MOCK"}' \
    "$BASE/api/v1/orders/$order_id/payment")
  expect_status 200 "$status" "payment request course $course_id" "$TMP_DIR/payment-$key.json"
  status=$(curl -s -o "$TMP_DIR/pay-$key.json" -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/orders/$order_id/payment/mock-pay")
  expect_status 200 "$status" "mock pay course $course_id" "$TMP_DIR/pay-$key.json"
  ORDER_ID=$order_id
  wait_order_done "$order_id" "course $course_id"
}

wait_order_done() {
  local order_id="$1" label="$2"
  for i in $(seq 1 40); do
    curl -s -o "$TMP_DIR/order-status.json" -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/v1/orders/$order_id"
    if [ "$(field data.state < "$TMP_DIR/order-status.json")" = "PAID" ] \
      && [ "$(field data.fulfillmentState < "$TMP_DIR/order-status.json")" = "DONE" ]; then
      echo "PASS  $label paid and fulfilled"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  $label not fulfilled"; cat "$TMP_DIR/order-status.json"; exit 1
}

complete_lessons() {
  local list="$1" count="$2" label="$3"
  local completed=0
  for lesson in $list; do
    [ "$completed" -ge "$count" ] && break
    local auth play status
    auth=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
      "$BASE/api/v1/lessons/$lesson/play-authorizations")
    play=$(field data.playSessionId <<< "$auth")
    if [ -z "$play" ]; then echo "FAIL  play authorization for $lesson"; echo "$auth"; exit 1; fi
    status=$(curl -s -o "$TMP_DIR/progress.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $TOKEN" \
      -d "{\"positionMs\":4000,\"playSessionId\":\"$play\"}" \
      "$BASE/api/v1/learning/lessons/$lesson/progress")
    expect_status 200 "$status" "$label progress lesson $lesson" "$TMP_DIR/progress.json"
    completed=$((completed + 1))
  done
}

apply_refund() {
  local order_id="$1" key="$2"
  local status
  status=$(curl -s -o "$TMP_DIR/refund-$key.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" -d "{\"reason\":\"S09A $key\"}" \
    "$BASE/api/v1/orders/$order_id/refund-applications")
  echo "$status"
}

source_state() {
  db "SELECT state, lifecycle_version FROM bc_learning.entitlement_source WHERE user_id=$USER_ID AND order_id=$1 AND type IN ('COURSE_ORDER','ROUTE_ORDER')" | tr '\t' '|'
}

intent_state() {
  db "SELECT state, completed_count, total_count, active_order_id FROM bc_learning.refund_intent WHERE order_id=$1 ORDER BY id DESC LIMIT 1" | tr '\t' '|'
}

db() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -N -B -e "$1"
}

set_refund_scenario() {
  local order_id="$1" outcome="$2" status
  status=$(curl -s -o "$TMP_DIR/scenario-$order_id.json" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"outcome\":\"$outcome\"}" "$BASE/api/v1/dev/refunds/$order_id/scenario")
  expect_status 200 "$status" "set refund scenario $outcome" "$TMP_DIR/scenario-$order_id.json"
}

create_route() {
  local title="$1" course_a="$2" out="$3"
  local status revision
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S09A\",\"priceCent\":1200,\"members\":[{\"courseId\":$course_a,\"sort\":1},{\"courseId\":13,\"sort\":2}]}" \
    "$BASE/api/v1/admin/routes")
  expect_status 200 "$status" "create route $title" "$out"
  ROUTE_ID=$(field data.id < "$out")
  revision=$(field data.revision < "$out")
  status=$(curl -s -o "$out.publish" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$revision}" \
    "$BASE/api/v1/admin/routes/$ROUTE_ID/publish")
  expect_status 200 "$status" "publish route $title" "$out.publish"
}

route_order() {
  local key="$1"
  local status quote_id quote_hash
  status=$(curl -s -o "$TMP_DIR/rq-$key.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"productType\":\"ROUTE\",\"productId\":$ROUTE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
    "$BASE/api/v1/trade/quotes")
  expect_status 200 "$status" "route quote $key" "$TMP_DIR/rq-$key.json"
  quote_id=$(field data.quoteId < "$TMP_DIR/rq-$key.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/rq-$key.json")
  status=$(curl -s -o "$TMP_DIR/ro-$key.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s09a-route-$key-$STAMP" -H "Authorization: Bearer $TOKEN" \
    -d "{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[]}" \
    "$BASE/api/v1/orders")
  expect_status 200 "$status" "route order $key" "$TMP_DIR/ro-$key.json"
  ROUTE_ORDER_ID=$(field data.id < "$TMP_DIR/ro-$key.json")
}

echo "== Setup =="
STATUS=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"S09ATester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register refund user" "$TMP_DIR/register.json"
login "$USERNAME" "$PASSWORD" STUDENT "$TMP_DIR/login.json"
STUDENT_TOKEN=$TOKEN
STUDENT_ID=$USER_ID
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
TOKEN=$STUDENT_TOKEN
USER_ID=$STUDENT_ID

create_course "S09A course A $STAMP" 10 "$TMP_DIR/course-a.json"
COURSE_A=$COURSE_ID
LESSONS_A=$LESSON_IDS
LESSON_REVS_A=$LESSON_REVS
create_course "S09A course B $STAMP" 10 "$TMP_DIR/course-b.json"
COURSE_B=$COURSE_ID
LESSONS_B=$LESSON_IDS
create_course "S09A course C $STAMP" 10 "$TMP_DIR/course-c.json"
COURSE_C=$COURSE_ID
create_course "S09A course D $STAMP" 10 "$TMP_DIR/course-d.json"
COURSE_D=$COURSE_ID
create_course "S09A course E $STAMP" 10 "$TMP_DIR/course-e.json"
COURSE_E=$COURSE_ID

echo "== Course A: fixed 10-lesson basis, delete completed lesson, freeze source =="
buy_course "$COURSE_A" a
ORDER_A=$ORDER_ID
complete_lessons "$LESSONS_A" 2 "course A"
DELETE_LESSON=$(echo "$LESSONS_A" | awk '{print $2}')
DELETE_REV=$(echo "$LESSON_REVS_A" | awk '{print $2}')
STATUS=$(curl -s -o "$TMP_DIR/delete-lesson.json" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/lessons/$DELETE_LESSON?revision=$DELETE_REV")
expect_status 200 "$STATUS" "delete completed lesson after purchase" "$TMP_DIR/delete-lesson.json"
set_refund_scenario "$ORDER_A" UNKNOWN
STATUS=$(apply_refund "$ORDER_A" a)
expect_status 202 "$STATUS" "refund application accepted" "$TMP_DIR/refund-a.json"
REQUEST_A=$(field data.requestId < "$TMP_DIR/refund-a.json")
expect_value "PROCESSING" "$(field data.state < "$TMP_DIR/refund-a.json")" "refund A qualified"
expect_value "2" "$(field data.completedCount < "$TMP_DIR/refund-a.json")" "fixed basis completed count"
expect_value "10" "$(field data.totalCount < "$TMP_DIR/refund-a.json")" "fixed basis total count"
SOURCE_A=$(source_state "$ORDER_A")
expect_value "FROZEN" "$(echo "$SOURCE_A" | cut -d"|" -f1)" "course A source frozen"
INTENT_A=$(intent_state "$ORDER_A")
expect_value "PROCESSING|2|10|$ORDER_A" "$INTENT_A" "refund intent processing"
STATUS=$(curl -s -o "$TMP_DIR/access-a.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/lessons/$(echo "$LESSONS_A" | awk '{print $3}')/play-authorizations")
expect_status 403 "$STATUS" "frozen source cannot issue new playback" "$TMP_DIR/access-a.json"
STATUS=$(apply_refund "$ORDER_A" a-dup)
expect_status 202 "$STATUS" "duplicate refund application returns accepted" "$TMP_DIR/refund-a-dup.json"
expect_value "$REQUEST_A" "$(field data.requestId < "$TMP_DIR/refund-a-dup.json")" "same request id"
INTENT_COUNT=$(db "SELECT COUNT(*) FROM bc_learning.refund_intent WHERE order_id=$ORDER_A")
expect_value "1" "$INTENT_COUNT" "no duplicate intent"
STATUS=$(curl -s -o "$TMP_DIR/refund-a-get.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/refund-applications/$REQUEST_A")
expect_status 200 "$STATUS" "query refund application" "$TMP_DIR/refund-a-get.json"
expect_value "PROCESSING" "$(field data.state < "$TMP_DIR/refund-a-get.json")" "queried intent state"

echo "== Course B: 3/10 exceeds 20%, source restored =="
buy_course "$COURSE_B" b
ORDER_B=$ORDER_ID
complete_lessons "$LESSONS_B" 3 "course B"
STATUS=$(apply_refund "$ORDER_B" b)
expect_status 202 "$STATUS" "refund B accepted then rejected" "$TMP_DIR/refund-b.json"
expect_value "REJECTED" "$(field data.state < "$TMP_DIR/refund-b.json")" "refund B rejected"
expect_value "REFUND_PROGRESS" "$(field data.reason < "$TMP_DIR/refund-b.json")" "refund B reason"
SOURCE_B=$(source_state "$ORDER_B")
expect_value "ACTIVE" "$(echo "$SOURCE_B" | cut -d"|" -f1)" "course B source restored"
INTENT_B=$(intent_state "$ORDER_B")
expect_value "REJECTED|3|10|NULL" "$INTENT_B" "rejected intent releases active order"

echo "== Course C: application after 7 days is rejected by acceptedAt window =="
buy_course "$COURSE_C" c
ORDER_C=$ORDER_ID
db "UPDATE bc_trade.payment SET paid_at=UTC_TIMESTAMP(3)-INTERVAL 8 DAY WHERE order_id=$ORDER_C AND status='SUCCEEDED'"
db "UPDATE bc_trade.trade_order SET paid_at=UTC_TIMESTAMP(3)-INTERVAL 8 DAY WHERE id=$ORDER_C"
STATUS=$(apply_refund "$ORDER_C" c)
expect_status 202 "$STATUS" "expired refund application persisted" "$TMP_DIR/refund-c.json"
expect_value "REJECTED" "$(field data.state < "$TMP_DIR/refund-c.json")" "refund C rejected"
expect_value "REFUND_EXPIRED" "$(field data.reason < "$TMP_DIR/refund-c.json")" "refund C reason"
SOURCE_C=$(source_state "$ORDER_C")
expect_value "ACTIVE" "$(echo "$SOURCE_C" | cut -d"|" -f1)" "course C source restored"

echo "== Course F: just inside the seven-day window is accepted =="
create_course "S09A course F $STAMP" 10 "$TMP_DIR/course-f.json"
COURSE_F=$COURSE_ID
buy_course "$COURSE_F" f
ORDER_F=$ORDER_ID
db "UPDATE bc_trade.payment SET paid_at=UTC_TIMESTAMP(3)-INTERVAL 167 HOUR WHERE order_id=$ORDER_F AND status='SUCCEEDED'"
db "UPDATE bc_trade.trade_order SET paid_at=UTC_TIMESTAMP(3)-INTERVAL 167 HOUR WHERE id=$ORDER_F"
set_refund_scenario "$ORDER_F" UNKNOWN
STATUS=$(apply_refund "$ORDER_F" f)
expect_status 202 "$STATUS" "near-deadline refund accepted" "$TMP_DIR/refund-f.json"
expect_value "PROCESSING" "$(field data.state < "$TMP_DIR/refund-f.json")" "refund F within window qualified"
SOURCE_F=$(source_state "$ORDER_F")
expect_value "FROZEN" "$(echo "$SOURCE_F" | cut -d"|" -f1)" "source F frozen"

echo "== Pending dependent route is closed before source refund =="
buy_course "$COURSE_D" d
ORDER_D=$ORDER_ID
create_route "S09A route D $STAMP" "$COURSE_D" "$TMP_DIR/route-d.json"
route_order "d-pending"
ROUTE_ORDER_D=$ROUTE_ORDER_ID
set_refund_scenario "$ORDER_D" UNKNOWN
STATUS=$(apply_refund "$ORDER_D" d)
expect_status 202 "$STATUS" "refund D accepted" "$TMP_DIR/refund-d.json"
expect_value "PROCESSING" "$(field data.state < "$TMP_DIR/refund-d.json")" "pending dependent closed and refund qualified"
ROUTE_STATE=$(db "SELECT state FROM bc_trade.trade_order WHERE id=$ROUTE_ORDER_D")
expect_value "CLOSED" "$ROUTE_STATE" "pending dependent route closed"
SOURCE_D=$(source_state "$ORDER_D")
expect_value "FROZEN" "$(echo "$SOURCE_D" | cut -d"|" -f1)" "source D frozen"

echo "== Paid dependent route blocks source refund =="
buy_course "$COURSE_E" e
ORDER_E=$ORDER_ID
create_route "S09A route E $STAMP" "$COURSE_E" "$TMP_DIR/route-e.json"
route_order "e-paid"
ROUTE_ORDER_E=$ROUTE_ORDER_ID
STATUS=$(curl -s -o "$TMP_DIR/rpay-e.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" -d '{"channel":"MOCK"}' \
  "$BASE/api/v1/orders/$ROUTE_ORDER_E/payment")
expect_status 200 "$STATUS" "route E payment request" "$TMP_DIR/rpay-e.json"
STATUS=$(curl -s -o "$TMP_DIR/rpay-e2.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/orders/$ROUTE_ORDER_E/payment/mock-pay")
expect_status 200 "$STATUS" "route E paid" "$TMP_DIR/rpay-e2.json"
wait_order_done "$ROUTE_ORDER_E" "route E"
STATUS=$(apply_refund "$ORDER_E" e)
expect_status 202 "$STATUS" "refund E persisted" "$TMP_DIR/refund-e.json"
expect_value "REJECTED" "$(field data.state < "$TMP_DIR/refund-e.json")" "refund E rejected"
expect_value "REFUND_DEPENDENCY" "$(field data.reason < "$TMP_DIR/refund-e.json")" "refund E dependency reason"
SOURCE_E=$(source_state "$ORDER_E")
expect_value "ACTIVE" "$(echo "$SOURCE_E" | cut -d"|" -f1)" "source E restored"
ROUTE_E_STATE=$(db "SELECT state FROM bc_trade.trade_order WHERE id=$ROUTE_ORDER_E")
expect_value "PAID" "$ROUTE_E_STATE" "paid dependent remains paid"

echo "ALL S09-A ACCEPTANCE CHECKS PASSED"
echo "intentA=$REQUEST_A orderA=$ORDER_A orderB=$ORDER_B orderC=$ORDER_C orderD=$ORDER_D"