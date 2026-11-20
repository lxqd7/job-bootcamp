#!/usr/bin/env bash
# S06-b acceptance: mock payment, signed callback, duplicate receipt, outbox grant and SYSTEM_RETURN.
# Prerequisite: RabbitMQ running and services started with OUTBOX_TRANSPORT=rabbit OUTBOX_RELAY_ENABLED=true.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
TRADE_COURSE_ID="${TRADE_COURSE_ID:-11}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
PAYMENT_SIGNING_KEY="${PAYMENT_MOCK_SIGNING_KEY:-dev-mock-payment-key}"
STAMP="$(date +%s)"
TMP_DIR="$(mktemp -d)"
COUNTER=0
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

field() {
  node -e '
    const fs = require("fs");
    const data = JSON.parse(fs.readFileSync(0, "utf8"));
    let value = data;
    for (const key of process.argv[1].split(".")) {
      value = value == null ? undefined : value[key];
    }
    console.log(value == null ? "" : value);
  ' "$1"
}

expect_status() {
  local expected="$1" actual="$2" label="$3" body="$4"
  if [ "$actual" = "$expected" ]; then
    echo "PASS  $label  (HTTP $actual)"
  else
    echo "FAIL  $label  expected HTTP $expected, got $actual"
    echo "--- response ---"; cat "$body" 2>/dev/null || true; echo
    exit 1
  fi
}

expect_value() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    echo "PASS  $label  ($actual)"
  else
    echo "FAIL  $label  expected '$expected', got '$actual'"
    exit 1
  fi
}

login() {
  local username="$1" app="$2" out="$3" password="$4"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
}

new_student() {
  COUNTER=$((COUNTER + 1))
  local username="s06b_${STAMP}_${COUNTER}"
  local password="Passw0rd!"
  local out="$TMP_DIR/student-$COUNTER.json"
  local status
  status=$(curl -s -o "$TMP_DIR/register-$COUNTER.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"nickname\":\"PayTester$COUNTER\"}" \
    "$BASE/api/v1/auth/register")
  expect_status 200 "$status" "register $username" "$TMP_DIR/register-$COUNTER.json"
  login "$username" STUDENT "$out" "$password"
  TOKEN=$(field data.accessToken < "$out")
  STUDENT_NAME="$username"
}

create_order() {
  local token="$1" out="$2"
  local quote_body="{\"productType\":\"COURSE\",\"productId\":$TRADE_COURSE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}"
  local status
  status=$(curl -s -o "$out.quote" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d "$quote_body" "$BASE/api/v1/trade/quotes")
  expect_status 200 "$status" "create quote for $STUDENT_NAME" "$out.quote"
  local quote_id quote_hash
  quote_id=$(field data.quoteId < "$out.quote")
  quote_hash=$(field data.quoteHash < "$out.quote")
  local request="{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[]}"
  local key="s06b-${STAMP}-${COUNTER}-${RANDOM}"
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $key" -H "Authorization: Bearer $token" \
    --data-binary "$request" "$BASE/api/v1/orders")
  expect_status 200 "$status" "create order for $STUDENT_NAME" "$out"
  ORDER_ID=$(field data.id < "$out")
  expect_value "WAIT_PAY" "$(field data.state < "$out")" "order starts WAIT_PAY"
}

initiate_payment() {
  local token="$1" order_id="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d '{"channel":"MOCK","paidAt":"2099-01-01T00:00:00Z"}' \
    "$BASE/api/v1/orders/$order_id/payment")
  expect_status 200 "$status" "initiate mock payment" "$out"
  PAYMENT_ID=$(field data.paymentId < "$out")
  PAY_ACTION=$(field data.payAction < "$out")
  expect_value "MOCK" "$(field data.mode < "$out")" "payment mode"
}

callback_signature() {
  local body="$1"
  BODY="$body" KEY="$PAYMENT_SIGNING_KEY" node -e '
    const crypto = require("crypto");
    console.log(crypto.createHmac("sha256", process.env.KEY).update(process.env.BODY).digest("hex"));
  '
}

scenario() {
  local admin_token="$1" payment_id="$2" scenario="$3" paid_at="$4" out="$5"
  local body
  if [ -n "$paid_at" ]; then
    body="{\"scenario\":\"$scenario\",\"paidAt\":\"$paid_at\"}"
  else
    body="{\"scenario\":\"$scenario\"}"
  fi
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $admin_token" --data-binary "$body" \
    "$BASE/api/v1/dev/payments/$payment_id/scenario")
  expect_status 200 "$status" "scenario $scenario" "$out"
}

get_order() {
  local token="$1" order_id="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H "Authorization: Bearer $token" "$BASE/api/v1/orders/$order_id")
  expect_status 200 "$status" "get order $order_id" "$out"
}

wait_fulfillment() {
  local token="$1" order_id="$2" out="$3"
  for _ in $(seq 1 40); do
    get_order "$token" "$order_id" "$out" >/dev/null
    if [ "$(field data.fulfillmentState < "$out")" = "DONE" ]; then
      echo "PASS  order $order_id fulfillment DONE after outbox"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  order $order_id fulfillment did not become DONE"
  cat "$out" 2>/dev/null || true
  exit 1
}

echo "== Admin login =="
login admin01 ADMIN "$TMP_DIR/admin.json" "$DEMO_PASSWORD"
ADMIN_TOKEN=$(field data.accessToken < "$TMP_DIR/admin.json")

echo "== Mock payment request and signed callback =="
new_student
create_order "$TOKEN" "$TMP_DIR/success-order.json"
SUCCESS_ORDER=$ORDER_ID
initiate_payment "$TOKEN" "$SUCCESS_ORDER" "$TMP_DIR/payment.json"
STATUS=$(curl -s -o "$TMP_DIR/student-scenario.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" -d '{"scenario":"SUCCESS"}' \
  "$BASE/api/v1/dev/payments/$PAYMENT_ID/scenario")
expect_status 403 "$STATUS" "student cannot use dev payment scenario" "$TMP_DIR/student-scenario.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-payment.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"channel":"MOCK"}' \
  "$BASE/api/v1/orders/$SUCCESS_ORDER/payment")
expect_status 403 "$STATUS" "admin cannot initiate student payment" "$TMP_DIR/admin-payment.json"
get_order "$TOKEN" "$SUCCESS_ORDER" "$TMP_DIR/before-pay.json"
expect_value "" "$(field data.paidAt < "$TMP_DIR/before-pay.json")" "student cannot set paidAt"
expect_value "PENDING" "$(field data.fulfillmentState < "$TMP_DIR/before-pay.json")" "fulfillment pending before callback"
MERCHANT=$(node -e 'console.log(process.argv[1].replace("mock://pay/", ""))' "$PAY_ACTION")
PAID_AT=$(node -e 'console.log(new Date().toISOString())')
PAID_AT_LOCAL=$(node -e 'let s=new Date(process.argv[1]).toISOString().replace("Z",""); s=s.replace(/(\.\d*?)0+$/,"$1").replace(/\.$/,""); console.log(s)' "$PAID_AT")
BAD_BODY="{\"merchantRequestNo\":\"$MERCHANT\",\"channelTradeNo\":\"MOCK-BAD-SIG\",\"amountCent\":990,\"currency\":\"CNY\",\"paidAt\":\"$PAID_AT\"}"
STATUS=$(curl -s -o "$TMP_DIR/bad-signature.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H 'X-Payment-Signature: bad-signature' --data-binary "$BAD_BODY" "$BASE/callbacks/payments/MOCK")
expect_status 403 "$STATUS" "callback rejects invalid signature" "$TMP_DIR/bad-signature.json"
get_order "$TOKEN" "$SUCCESS_ORDER" "$TMP_DIR/after-bad-signature.json"
expect_value "WAIT_PAY" "$(field data.state < "$TMP_DIR/after-bad-signature.json")" "invalid signature leaves order WAIT_PAY"

WRONG_AMOUNT_BODY="{\"merchantRequestNo\":\"$MERCHANT\",\"channelTradeNo\":\"MOCK-WRONG-AMOUNT\",\"amountCent\":1,\"currency\":\"CNY\",\"paidAt\":\"$PAID_AT\"}"
WRONG_AMOUNT_SIG=$(callback_signature "$WRONG_AMOUNT_BODY")
STATUS=$(curl -s -o "$TMP_DIR/wrong-amount.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "X-Payment-Signature: $WRONG_AMOUNT_SIG" --data-binary "$WRONG_AMOUNT_BODY" "$BASE/callbacks/payments/MOCK")
expect_status 400 "$STATUS" "callback rejects amount mismatch" "$TMP_DIR/wrong-amount.json"
get_order "$TOKEN" "$SUCCESS_ORDER" "$TMP_DIR/after-wrong-amount.json"
expect_value "WAIT_PAY" "$(field data.state < "$TMP_DIR/after-wrong-amount.json")" "amount mismatch leaves order WAIT_PAY"

scenario "$ADMIN_TOKEN" "$PAYMENT_ID" SUCCESS "$PAID_AT" "$TMP_DIR/success.json"
expect_value "true" "$(field data.valid < "$TMP_DIR/success.json")" "successful callback valid"
get_order "$TOKEN" "$SUCCESS_ORDER" "$TMP_DIR/paid.json"
expect_value "PAID" "$(field data.state < "$TMP_DIR/paid.json")" "order PAID after callback"
expect_value "$PAID_AT_LOCAL" "$(field data.paidAt < "$TMP_DIR/paid.json")" "paidAt comes from channel callback"
wait_fulfillment "$TOKEN" "$SUCCESS_ORDER" "$TMP_DIR/fulfilled.json"

scenario "$ADMIN_TOKEN" "$PAYMENT_ID" DUPLICATE "$PAID_AT" "$TMP_DIR/duplicate.json"
expect_value "true" "$(field data.duplicate < "$TMP_DIR/duplicate.json")" "duplicate callback is idempotent"

echo "== Cancellation and unknown payment handling =="
new_student
create_order "$TOKEN" "$TMP_DIR/cancel-order.json"
CANCEL_ORDER=$ORDER_ID
initiate_payment "$TOKEN" "$CANCEL_ORDER" "$TMP_DIR/cancel-payment.json"
STATUS=$(curl -s -o "$TMP_DIR/cancel.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  -X POST "$BASE/api/v1/orders/$CANCEL_ORDER/cancel")
expect_status 200 "$STATUS" "cancel WAIT_PAY order" "$TMP_DIR/cancel.json"
expect_value "CLOSED" "$(field data.state < "$TMP_DIR/cancel.json")" "cancelled order CLOSED"

new_student
create_order "$TOKEN" "$TMP_DIR/unknown-order.json"
UNKNOWN_ORDER=$ORDER_ID
initiate_payment "$TOKEN" "$UNKNOWN_ORDER" "$TMP_DIR/unknown-payment.json"
scenario "$ADMIN_TOKEN" "$PAYMENT_ID" UNKNOWN "" "$TMP_DIR/unknown.json"
expect_value "UNKNOWN" "$(field data.state < "$TMP_DIR/unknown.json")" "unknown scenario state"
get_order "$TOKEN" "$UNKNOWN_ORDER" "$TMP_DIR/unknown-order-after.json"
expect_value "CLOSING" "$(field data.state < "$TMP_DIR/unknown-order-after.json")" "unknown payment keeps order CLOSING"

echo "== Late/extra payment becomes SYSTEM_RETURN =="
new_student
create_order "$TOKEN" "$TMP_DIR/return-order.json"
RETURN_ORDER=$ORDER_ID
initiate_payment "$TOKEN" "$RETURN_ORDER" "$TMP_DIR/return-payment.json"
FUTURE_PAID_AT=$(node -e 'console.log(new Date(Date.now() + 86400000).toISOString())')
scenario "$ADMIN_TOKEN" "$PAYMENT_ID" LATE_NOTIFICATION "$FUTURE_PAID_AT" "$TMP_DIR/return.json"
expect_value "false" "$(field data.valid < "$TMP_DIR/return.json")" "payment after expiry is invalid"
expect_value "CLOSED" "$(field data.orderState < "$TMP_DIR/return.json")" "invalid payment closes order"
REFUND_ID=$(field data.refundId < "$TMP_DIR/return.json")
if [ -z "$REFUND_ID" ]; then echo "FAIL  SYSTEM_RETURN refund id missing"; exit 1; fi
echo "PASS  SYSTEM_RETURN refund created id=$REFUND_ID"
STATUS=$(curl -s -o "$TMP_DIR/refund.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/refunds/$REFUND_ID")
expect_status 200 "$STATUS" "student reads own SYSTEM_RETURN" "$TMP_DIR/refund.json"
expect_value "SYSTEM_RETURN" "$(field data.kind < "$TMP_DIR/refund.json")" "refund kind"
expect_value "COMPLETED" "$(field data.state < "$TMP_DIR/refund.json")" "mock refund completed"
expect_value "990" "$(field data.amountCent < "$TMP_DIR/refund.json")" "refund amount"

echo "ALL S06-B ACCEPTANCE CHECKS PASSED"