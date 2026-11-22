#!/usr/bin/env bash
# S09-b acceptance: refund state machine, query-before-retry, revoke/restore and coupon non-return.
# Requires RabbitMQ, OUTBOX_TRANSPORT=rabbit, OUTBOX_RELAY_ENABLED=true, PAYMENT_MOCK_SCENARIO_ENABLED=true.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
USERNAME="s09b_$STAMP"
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
  local title="$1" out="$2"
  local status body
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S09B\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create course $title" "$out"
  COURSE_ID=$(field data.id < "$out")
  COURSE_REV=$(field data.revision < "$out")
  status=$(curl -s -o "$out.chapter" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Chapter","sort":1}' \
    "$BASE/api/v1/admin/courses/$COURSE_ID/chapters")
  expect_status 200 "$status" "create chapter" "$out.chapter"
  CHAPTER_ID=$(field data.id < "$out.chapter")
  for i in $(seq 1 10); do
    body=$(curl -s -X POST -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -d "{\"title\":\"Video $i\",\"sort\":$i,\"mediaId\":1}" \
      "$BASE/api/v1/admin/chapters/$CHAPTER_ID/lessons")
    LESSON_IDS="$LESSON_IDS $(field data.id <<< "$body")"
  done
  status=$(curl -s -o "$out.publish" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$COURSE_REV}" \
    "$BASE/api/v1/admin/courses/$COURSE_ID/publish")
  expect_status 200 "$status" "publish course $title" "$out.publish"
}

buy_course() {
  local course_id="$1" key="$2" coupon_ids="${3:-}"
  local status quote_id quote_hash
  status=$(curl -s -o "$TMP_DIR/q-$key.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"productType\":\"COURSE\",\"productId\":$course_id,\"couponSelection\":{\"mode\":\"AUTO\",\"ids\":[]}}" \
    "$BASE/api/v1/trade/quotes")
  expect_status 200 "$status" "quote $key" "$TMP_DIR/q-$key.json"
  quote_id=$(field data.quoteId < "$TMP_DIR/q-$key.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/q-$key.json")
  local ids
  ids=$(node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    console.log((d.data.couponPlan||[]).map(x=>x.userCouponId).join(","));
  ' "$TMP_DIR/q-$key.json")
  status=$(curl -s -o "$TMP_DIR/o-$key.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s09b-$key-$STAMP" -H "Authorization: Bearer $TOKEN" \
    -d "{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[$ids]}" \
    "$BASE/api/v1/orders")
  expect_status 200 "$status" "order $key" "$TMP_DIR/o-$key.json"
  ORDER_ID=$(field data.id < "$TMP_DIR/o-$key.json")
  status=$(curl -s -o "$TMP_DIR/pr-$key.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" -d '{"channel":"MOCK"}' \
    "$BASE/api/v1/orders/$ORDER_ID/payment")
  expect_status 200 "$status" "payment request $key" "$TMP_DIR/pr-$key.json"
  status=$(curl -s -o "$TMP_DIR/pay-$key.json" -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/orders/$ORDER_ID/payment/mock-pay")
  expect_status 200 "$status" "mock pay $key" "$TMP_DIR/pay-$key.json"
  wait_order_done "$ORDER_ID" "$key"
}

wait_order_done() {
  local order_id="$1" label="$2"
  for i in $(seq 1 40); do
    curl -s -o "$TMP_DIR/order-state.json" -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/orders/$order_id"
    if [ "$(field data.state < "$TMP_DIR/order-state.json")" = "PAID" ] \
      && [ "$(field data.fulfillmentState < "$TMP_DIR/order-state.json")" = "DONE" ]; then
      echo "PASS  $label paid and fulfilled"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  $label not fulfilled"; cat "$TMP_DIR/order-state.json"; exit 1
}

set_refund_scenario() {
  local order_id="$1" outcome="$2"
  local status
  status=$(curl -s -o "$TMP_DIR/scenario.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"outcome\":\"$outcome\"}" \
    "$BASE/api/v1/dev/refunds/$order_id/scenario")
  expect_status 200 "$status" "set refund scenario $outcome" "$TMP_DIR/scenario.json"
}

apply_refund() {
  local order_id="$1" key="$2"
  local status
  status=$(curl -s -o "$TMP_DIR/r-$key.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" -d "{\"reason\":\"S09B $key\"}" \
    "$BASE/api/v1/orders/$order_id/refund-applications")
  echo "$status"
}

refund_id_for_order() {
  db "SELECT id FROM bc_trade.refund WHERE order_id=$1 AND kind='USER' ORDER BY id DESC LIMIT 1"
}

refund_state() {
  db "SELECT state, amount_cent, channel_refund_no IS NULL FROM bc_trade.refund WHERE order_id=$1 AND kind='USER' ORDER BY id DESC LIMIT 1" | tr '\t' '|'
}

source_state() {
  db "SELECT state, lifecycle_version FROM bc_learning.entitlement_source WHERE user_id=$USER_ID AND order_id=$1 AND type IN ('COURSE_ORDER','ROUTE_ORDER')" | tr '\t' '|'
}

intent_state() {
  db "SELECT state, completed_count, total_count FROM bc_learning.refund_intent WHERE order_id=$1 ORDER BY id DESC LIMIT 1" | tr '\t' '|'
}

create_coupon() {
  local key="$1"
  local status
  status=$(curl -s -o "$TMP_DIR/crule.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"ruleKey\":\"$key\",\"type\":\"NO_THRESHOLD\",\"scope\":\"ALL\",\"thresholdCent\":0,\"amountCent\":100,\"validityType\":\"DAYS\",\"validDays\":7}" \
    "$BASE/api/v1/admin/coupon-rules")
  expect_status 200 "$status" "create coupon rule" "$TMP_DIR/crule.json"
  local rule_id
  rule_id=$(field data.id < "$TMP_DIR/crule.json")
  status=$(curl -s -o "$TMP_DIR/cact.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"name\":\"$key activity\",\"openAt\":\"2026-01-01T00:00:00\",\"closeAt\":\"2030-01-01T00:00:00\"}" \
    "$BASE/api/v1/admin/activities")
  expect_status 200 "$status" "create coupon activity" "$TMP_DIR/cact.json"
  local activity_id
  activity_id=$(field data.id < "$TMP_DIR/cact.json")
  status=$(curl -s -o "$TMP_DIR/citem.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"ruleId\":$rule_id,\"stockTotal\":10,\"perUserLimit\":1}" \
    "$BASE/api/v1/admin/activities/$activity_id/items")
  expect_status 200 "$status" "create coupon item" "$TMP_DIR/citem.json"
  local item_id rev
  item_id=$(field data.items.0.id < "$TMP_DIR/citem.json")
  rev=$(field data.revision < "$TMP_DIR/citem.json")
  status=$(curl -s -o "$TMP_DIR/cactive.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$rev}" \
    "$BASE/api/v1/admin/activities/$activity_id/activate")
  expect_status 200 "$status" "activate coupon activity" "$TMP_DIR/cactive.json"
  status=$(curl -s -o "$TMP_DIR/cclaim.json" -w '%{http_code}' -X POST \
    -H "Idempotency-Key: s09b-coupon-$STAMP" -H "Authorization: Bearer $TOKEN" \
    "$BASE/api/v1/activity-items/$item_id/claims")
  expect_status 200 "$status" "claim coupon" "$TMP_DIR/cclaim.json"
  COUPON_ID=$(field data.id < "$TMP_DIR/cclaim.json")
}

db() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -N -B -e "$1"
}

echo "== Setup =="
STATUS=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"S09BTester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register refund user" "$TMP_DIR/register.json"
login "$USERNAME" "$PASSWORD" STUDENT "$TMP_DIR/login.json"
STUDENT_TOKEN=$TOKEN
STUDENT_ID=$USER_ID
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
TOKEN=$STUDENT_TOKEN
USER_ID=$STUDENT_ID
LESSON_IDS=""

echo "== Successful channel refund completes and revokes source =="
create_course "S09B success $STAMP" "$TMP_DIR/ca.json"
COURSE_A=$COURSE_ID
buy_course "$COURSE_A" a
ORDER_A=$ORDER_ID
STATUS=$(apply_refund "$ORDER_A" a)
expect_status 202 "$STATUS" "apply success refund" "$TMP_DIR/r-a.json"
expect_value "COMPLETED" "$(field data.state < "$TMP_DIR/r-a.json")" "application completed"
REFUND_STATE=$(refund_state "$ORDER_A")
expect_value "COMPLETED|990|0" "$REFUND_STATE" "refund completed with amount"
SOURCE_STATE=$(source_state "$ORDER_A")
expect_value "REVOKED" "$(echo "$SOURCE_STATE" | cut -d"|" -f1)" "source revoked"
INTENT_STATE=$(intent_state "$ORDER_A")
expect_value "COMPLETED|0|10" "$INTENT_STATE" "intent completed and list fixed"
REFUND_ID=$(refund_id_for_order "$ORDER_A")
STATUS=$(curl -s -o "$TMP_DIR/reconcile-a.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/refunds/$REFUND_ID/reconcile")
expect_status 200 "$STATUS" "reconcile completed refund is idempotent" "$TMP_DIR/reconcile-a.json"
expect_value "COMPLETED" "$(field data.state < "$TMP_DIR/reconcile-a.json")" "reconcile retains completed"

echo "== UNKNOWN refund is queried before retry and then revoked =="
create_course "S09B unknown $STAMP" "$TMP_DIR/cb.json"
COURSE_B=$COURSE_ID
buy_course "$COURSE_B" b
ORDER_B=$ORDER_ID
set_refund_scenario "$ORDER_B" UNKNOWN
STATUS=$(apply_refund "$ORDER_B" b)
expect_status 202 "$STATUS" "apply unknown refund" "$TMP_DIR/r-b.json"
expect_value "PROCESSING" "$(field data.state < "$TMP_DIR/r-b.json")" "unknown keeps intent processing"
REFUND_B=$(refund_state "$ORDER_B")
expect_value "CHANNEL_UNKNOWN|990|1" "$REFUND_B" "channel unknown has no channel no"
SOURCE_B=$(source_state "$ORDER_B")
expect_value "FROZEN" "$(echo "$SOURCE_B" | cut -d"|" -f1)" "unknown keeps source frozen"
REFUND_ID_B=$(refund_id_for_order "$ORDER_B")
set_refund_scenario "$ORDER_B" COMPLETED
STATUS=$(curl -s -o "$TMP_DIR/reconcile-b.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/refunds/$REFUND_ID_B/reconcile")
expect_status 200 "$STATUS" "query unknown then complete" "$TMP_DIR/reconcile-b.json"
expect_value "COMPLETED" "$(field data.state < "$TMP_DIR/reconcile-b.json")" "refund completed after query"
SOURCE_B=$(source_state "$ORDER_B")
expect_value "REVOKED" "$(echo "$SOURCE_B" | cut -d"|" -f1)" "source revoked after query"

echo "== Explicit channel close restores source and closes intent =="
create_course "S09B closed $STAMP" "$TMP_DIR/cc.json"
COURSE_C=$COURSE_ID
buy_course "$COURSE_C" c
ORDER_C=$ORDER_ID
set_refund_scenario "$ORDER_C" CLOSED
STATUS=$(apply_refund "$ORDER_C" c)
expect_status 202 "$STATUS" "apply closed refund" "$TMP_DIR/r-c.json"
expect_value "CLOSED" "$(field data.state < "$TMP_DIR/r-c.json")" "closed refund intent"
REFUND_C=$(refund_state "$ORDER_C")
expect_value "CLOSED|990|1" "$REFUND_C" "refund closed"
SOURCE_C=$(source_state "$ORDER_C")
expect_value "ACTIVE" "$(echo "$SOURCE_C" | cut -d"|" -f1)" "source restored"
INTENT_C=$(intent_state "$ORDER_C")
expect_value "CLOSED|0|10" "$INTENT_C" "intent closed"

echo "== Temporary channel failure retries with same refund number =="
create_course "S09B retry $STAMP" "$TMP_DIR/cd.json"
COURSE_D=$COURSE_ID
buy_course "$COURSE_D" d
ORDER_D=$ORDER_ID
set_refund_scenario "$ORDER_D" FAILED
STATUS=$(apply_refund "$ORDER_D" d)
expect_status 202 "$STATUS" "apply failed refund" "$TMP_DIR/r-d.json"
expect_value "PROCESSING" "$(field data.state < "$TMP_DIR/r-d.json")" "failed keeps processing"
REFUND_D=$(refund_state "$ORDER_D")
expect_value "CHANNEL_PENDING|990|1" "$REFUND_D" "failed leaves channel pending"
REFUND_ID_D=$(refund_id_for_order "$ORDER_D")
REFUND_NO_D=$(db "SELECT refund_no FROM bc_trade.refund WHERE id=$REFUND_ID_D")
set_refund_scenario "$ORDER_D" COMPLETED
STATUS=$(curl -s -o "$TMP_DIR/reconcile-d.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/refunds/$REFUND_ID_D/reconcile")
expect_status 200 "$STATUS" "retry failed refund" "$TMP_DIR/reconcile-d.json"
expect_value "COMPLETED" "$(field data.state < "$TMP_DIR/reconcile-d.json")" "retry completed"
expect_value "$REFUND_NO_D" "$(db "SELECT refund_no FROM bc_trade.refund WHERE id=$REFUND_ID_D")" "same refund number reused"

echo "== Revoke retry does not refund a second time =="
create_course "S09B revoke retry $STAMP" "$TMP_DIR/cf.json"
COURSE_F=$COURSE_ID
buy_course "$COURSE_F" f
ORDER_F=$ORDER_ID
set_refund_scenario "$ORDER_F" UNKNOWN
STATUS=$(apply_refund "$ORDER_F" f)
expect_status 202 "$STATUS" "apply revoke-retry refund" "$TMP_DIR/r-f.json"
REFUND_ID_F=$(refund_id_for_order "$ORDER_F")
set_refund_scenario "$ORDER_F" COMPLETED
# 模拟撤权暂时不可用：来源被外部改成 ACTIVE，撤权命令会被学习拒绝。
db "UPDATE bc_learning.entitlement_source SET state='ACTIVE' WHERE user_id=$USER_ID AND order_id=$ORDER_F AND type IN ('COURSE_ORDER','ROUTE_ORDER')"
STATUS=$(curl -s -o "$TMP_DIR/reconcile-f1.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/refunds/$REFUND_ID_F/reconcile")
expect_status 200 "$STATUS" "reconcile with revoke failure" "$TMP_DIR/reconcile-f1.json"
expect_value "REVOKE_PENDING" "$(field data.state < "$TMP_DIR/reconcile-f1.json")" "refund waits for revoke"
CHANNEL_NO_F=$(db "SELECT channel_refund_no FROM bc_trade.refund WHERE id=$REFUND_ID_F")
db "UPDATE bc_learning.entitlement_source SET state='FROZEN' WHERE user_id=$USER_ID AND order_id=$ORDER_F AND type IN ('COURSE_ORDER','ROUTE_ORDER')"
STATUS=$(curl -s -o "$TMP_DIR/reconcile-f2.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/refunds/$REFUND_ID_F/reconcile")
expect_status 200 "$STATUS" "reconcile revoke retry" "$TMP_DIR/reconcile-f2.json"
expect_value "COMPLETED" "$(field data.state < "$TMP_DIR/reconcile-f2.json")" "revoke retry completed"
expect_value "$CHANNEL_NO_F" "$(db "SELECT channel_refund_no FROM bc_trade.refund WHERE id=$REFUND_ID_F")" "same channel refund reused"

echo "== Coupon remains consumed after refund =="
create_course "S09B coupon $STAMP" "$TMP_DIR/ce.json"
COURSE_E=$COURSE_ID
create_coupon "S09B_COUPON_$STAMP"
buy_course "$COURSE_E" e
ORDER_E=$ORDER_ID
STATUS=$(apply_refund "$ORDER_E" e)
expect_status 202 "$STATUS" "apply coupon order refund" "$TMP_DIR/r-e.json"
expect_value "COMPLETED" "$(field data.state < "$TMP_DIR/r-e.json")" "coupon order refund completed"
COUPON_STATE=$(db "SELECT status, used_order_id FROM bc_marketing.user_coupon WHERE id=$COUPON_ID" | tr '\t' '|')
expect_value "USED|$ORDER_E" "$COUPON_STATE" "coupon remains used"
LOCK_STATE=$(db "SELECT state FROM bc_marketing.coupon_lock WHERE order_id=$ORDER_E AND user_coupon_id=$COUPON_ID")
expect_value "USED" "$LOCK_STATE" "coupon lock remains used"

echo "ALL S09-B ACCEPTANCE CHECKS PASSED"
echo "orderSuccess=$ORDER_A orderUnknown=$ORDER_B orderClosed=$ORDER_C orderRetry=$ORDER_D orderCoupon=$ORDER_E"