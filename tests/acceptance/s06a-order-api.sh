#!/usr/bin/env bash
# S06-a acceptance: quote, order snapshot, pending lock and idempotency (no payment/coupon).
# Prerequisite: demo paid ONLINE course with media, default course 11. Override TRADE_COURSE_ID.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
TRADE_COURSE_ID="${TRADE_COURSE_ID:-11}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
STAMP="$(date +%s)"
TEST_USER="s06a_${STAMP}"
TEST_PASSWORD="Passw0rd!"
TMP_DIR="$(mktemp -d)"
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

register() {
  local out="$1"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASSWORD\",\"nickname\":\"TradeTester\"}" \
    "$BASE/api/v1/auth/register")
  expect_status 200 "$status" "register $TEST_USER" "$out"
}

login() {
  local username="$1" out="$2"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$TEST_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
}

quote() {
  local token="$1" out="$2" body="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d "$body" "$BASE/api/v1/trade/quotes")
  echo "$status"
}

echo "== Register unique student and create quote =="
register "$TMP_DIR/register.json"
login "$TEST_USER" "$TMP_DIR/login.json"
TOKEN=$(field data.accessToken < "$TMP_DIR/login.json")
QUOTE_BODY="{\"productType\":\"COURSE\",\"productId\":$TRADE_COURSE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}"
STATUS=$(quote "$TOKEN" "$TMP_DIR/quote.json" "$QUOTE_BODY")
expect_status 200 "$STATUS" "create NONE quote" "$TMP_DIR/quote.json"
QUOTE_ID=$(field data.quoteId < "$TMP_DIR/quote.json")
QUOTE_HASH=$(field data.quoteHash < "$TMP_DIR/quote.json")
expect_value "990" "$(field data.payableCent < "$TMP_DIR/quote.json")" "quote payable is 990"

printf '{"quoteId":"%s","quoteHash":"%s","selectedCouponIds":[]}' "$QUOTE_ID" "$QUOTE_HASH" > "$TMP_DIR/order-request.json"
IDEM_KEY="s06a-${STAMP}"

echo "== 100 concurrent requests with the same Idempotency-Key =="
mkdir -p "$TMP_DIR/idem"
seq 1 100 | xargs -P 25 -I{} sh -c '
  curl -s -o "'"$TMP_DIR"'/idem/{}.json" -w "%{http_code}\n" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: '"$IDEM_KEY"'" \
    -H "Authorization: Bearer '"$TOKEN"'" \
    --data-binary @'"$TMP_DIR"'/order-request.json \
    "'"$BASE"'/api/v1/orders" > "'"$TMP_DIR"'/idem/{}.status"
'
cat "$TMP_DIR"/idem/*.status | sort | uniq -c
if [ "$(cat "$TMP_DIR"/idem/*.status | sort -u)" != "200" ]; then
  echo "FAIL  concurrent order creation returned a non-200 status"
  exit 1
fi
ORDER_IDS=$(node -e '
  const fs=require("fs"); const dir=process.argv[1]; const ids=new Set();
  for (const f of fs.readdirSync(dir)) {
    if (!f.endsWith(".json")) continue;
    const d=JSON.parse(fs.readFileSync(dir+"/"+f,"utf8"));
    if (d.code === "OK") ids.add(String(d.data.id));
  }
  console.log([...ids].join(","));
' "$TMP_DIR/idem")
expect_value "1" "$(echo "$ORDER_IDS" | tr ',' '\n' | wc -l | tr -d ' ')" "concurrent requests produced one order id"
ORDER_ID="$ORDER_IDS"
echo "PASS  order id=$ORDER_ID"

echo "== Order snapshot and state =="
STATUS=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/orders/$ORDER_ID")
expect_status 200 "$STATUS" "GET own order" "$TMP_DIR/order.json"
expect_value "WAIT_PAY" "$(field data.state < "$TMP_DIR/order.json")" "order state"
expect_value "PENDING" "$(field data.fulfillmentState < "$TMP_DIR/order.json")" "fulfillment state"
expect_value "$TRADE_COURSE_ID" "$(field data.items.0.courseId < "$TMP_DIR/order.json")" "order item course"
expect_value "990" "$(field data.pricing.payableCent < "$TMP_DIR/order.json")" "order payable"
STATUS=$(curl -s -o "$TMP_DIR/quote-after.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/trade/quotes/$QUOTE_ID")
expect_status 200 "$STATUS" "GET quote after order" "$TMP_DIR/quote-after.json"
expect_value "CONSUMED" "$(field data.status < "$TMP_DIR/quote-after.json")" "quote consumed"
STATUS=$(curl -s -o "$TMP_DIR/list.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/orders?state=WAIT_PAY&page=1&pageSize=20")
expect_status 200 "$STATUS" "GET order list" "$TMP_DIR/list.json"
expect_value "$ORDER_ID" "$(field data.items.0.id < "$TMP_DIR/list.json")" "order list contains order"

echo "== Overlapping pending order conflict =="
STATUS=$(quote "$TOKEN" "$TMP_DIR/quote-overlap.json" "$QUOTE_BODY")
expect_status 200 "$STATUS" "second quote for same course" "$TMP_DIR/quote-overlap.json"
Q2=$(field data.quoteId < "$TMP_DIR/quote-overlap.json")
H2=$(field data.quoteHash < "$TMP_DIR/quote-overlap.json")
printf '{"quoteId":"%s","quoteHash":"%s","selectedCouponIds":[]}' "$Q2" "$H2" > "$TMP_DIR/overlap.json"
STATUS=$(curl -s -o "$TMP_DIR/overlap-resp.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s06a-overlap-'"$STAMP" -H "Authorization: Bearer $TOKEN" \
  --data-binary @"$TMP_DIR/overlap.json" "$BASE/api/v1/orders")
expect_status 409 "$STATUS" "overlapping pending order is rejected" "$TMP_DIR/overlap-resp.json"
expect_value "ORDER_CONFLICT" "$(field code < "$TMP_DIR/overlap-resp.json")" "overlap error code"
expect_value "$ORDER_ID" "$(field data.resourceId < "$TMP_DIR/overlap-resp.json")" "overlap returns existing order id"

echo "== Quote hash mismatch and idempotency-key reuse =="
STATUS=$(quote "$TOKEN" "$TMP_DIR/quote-mismatch.json" "$QUOTE_BODY")
expect_status 200 "$STATUS" "third quote for mismatch test" "$TMP_DIR/quote-mismatch.json"
QM=$(field data.quoteId < "$TMP_DIR/quote-mismatch.json")
printf '{"quoteId":"%s","quoteHash":"%s","selectedCouponIds":[]}' "$QM" "0000000000000000000000000000000000000000000000000000000000000000" > "$TMP_DIR/mismatch.json"
STATUS=$(curl -s -o "$TMP_DIR/mismatch-resp.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s06a-mismatch-'"$STAMP" -H "Authorization: Bearer $TOKEN" \
  --data-binary @"$TMP_DIR/mismatch.json" "$BASE/api/v1/orders")
expect_status 409 "$STATUS" "wrong quoteHash is rejected" "$TMP_DIR/mismatch-resp.json"
expect_value "QUOTE_CHANGED" "$(field code < "$TMP_DIR/mismatch-resp.json")" "wrong hash error code"
STATUS=$(curl -s -o "$TMP_DIR/reuse-resp.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: '"$IDEM_KEY" -H "Authorization: Bearer $TOKEN" \
  --data-binary @"$TMP_DIR/overlap.json" "$BASE/api/v1/orders")
expect_status 409 "$STATUS" "same key with different request is rejected" "$TMP_DIR/reuse-resp.json"

echo "== Ownership and role boundaries =="
STATUS=$(curl -s -o "$TMP_DIR/student01-login.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"student01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"STUDENT\"}" "$BASE/api/v1/auth/login")
expect_status 200 "$STATUS" "login student01" "$TMP_DIR/student01-login.json"
T1=$(field data.accessToken < "$TMP_DIR/student01-login.json")
STATUS=$(curl -s -o "$TMP_DIR/foreign.json" -w '%{http_code}' -H "Authorization: Bearer $T1" "$BASE/api/v1/orders/$ORDER_ID")
expect_status 404 "$STATUS" "other student cannot read order" "$TMP_DIR/foreign.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-login.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"ADMIN\"}" "$BASE/api/v1/auth/login")
expect_status 200 "$STATUS" "login admin01" "$TMP_DIR/admin-login.json"
TA=$(field data.accessToken < "$TMP_DIR/admin-login.json")
STATUS=$(curl -s -o "$TMP_DIR/admin-order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s06a-admin-'"$STAMP" -H "Authorization: Bearer $TA" \
  --data-binary @"$TMP_DIR/order-request.json" "$BASE/api/v1/orders")
expect_status 403 "$STATUS" "admin cannot create student order" "$TMP_DIR/admin-order.json"

echo "== Coupon mode is NONE-only in S06-a =="
STATUS=$(quote "$TOKEN" "$TMP_DIR/coupon.json" "{\"productType\":\"COURSE\",\"productId\":$TRADE_COURSE_ID,\"couponSelection\":{\"mode\":\"AUTO\",\"ids\":[]}}")
expect_status 409 "$STATUS" "AUTO coupon mode is unavailable" "$TMP_DIR/coupon.json"
expect_value "COUPON_UNAVAILABLE" "$(field code < "$TMP_DIR/coupon.json")" "coupon error code"

echo "ALL S06-A ACCEPTANCE CHECKS PASSED"
echo "orderId=$ORDER_ID quoteId=$QUOTE_ID"