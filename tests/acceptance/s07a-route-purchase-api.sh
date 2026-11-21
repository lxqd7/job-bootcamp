#!/usr/bin/env bash
# S07-a acceptance: route quote, deduction source selection, prepare/release and no zero orders.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
STAMP="$(date +%s)"
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

login() {
  local username="$1" app="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
}

create_route() {
  local title="$1" price="$2" members="$3" out="$4"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S07A\",\"priceCent\":$price,\"members\":$members}" \
    "$BASE/api/v1/admin/routes")
  expect_status 200 "$status" "create route $title" "$out"
  local route_id revision
  route_id=$(field data.id < "$out")
  revision=$(field data.revision < "$out")
  status=$(curl -s -o "$out.publish" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$revision}" \
    "$BASE/api/v1/admin/routes/$route_id/publish")
  expect_status 200 "$status" "publish route $title" "$out.publish"
  ROUTE_ID="$route_id"
}

route_line_value() {
  local file="$1" course_id="$2" field_name="$3"
  node -e '
    const fs=require("fs");
    const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    const line=(d.data.courses||[]).find(item=>String(item.courseId)===process.argv[2]);
    console.log(line ? line[process.argv[3]] : "");
  ' "$file" "$course_id" "$field_name"
}

echo "== Admin and route setup =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$(field data.accessToken < "$TMP_DIR/admin.json")
PARTIAL_MEMBERS='[{"courseId":15,"sort":1},{"courseId":11,"sort":2}]'
create_route "S07A partial $STAMP" 1200 "$PARTIAL_MEMBERS" "$TMP_DIR/partial-route.json"
PARTIAL_ROUTE=$ROUTE_ID

OWNED_MEMBERS='[{"courseId":15,"sort":1},{"courseId":17,"sort":2}]'
create_route "S07A owned $STAMP" 1200 "$OWNED_MEMBERS" "$TMP_DIR/owned-route.json"
OWNED_ROUTE=$ROUTE_ID

echo "== Route quote selects earliest active source =="
login student03 STUDENT "$TMP_DIR/student.json"
STUDENT_TOKEN=$(field data.accessToken < "$TMP_DIR/student.json")
QUOTE_STATUS=$(curl -s -o "$TMP_DIR/quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d "{\"productType\":\"ROUTE\",\"productId\":$PARTIAL_ROUTE,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
  "$BASE/api/v1/trade/quotes")
expect_status 200 "$QUOTE_STATUS" "route quote" "$TMP_DIR/quote.json"
expect_value "ROUTE" "$(field data.product.type < "$TMP_DIR/quote.json")" "quote product type"
expect_value "600" "$(field data.creditCent < "$TMP_DIR/quote.json")" "route credit"
expect_value "600" "$(field data.payableCent < "$TMP_DIR/quote.json")" "route payable"
expect_value "600" "$(route_line_value "$TMP_DIR/quote.json" 15 creditCent)" "owned course credit"
expect_value "600" "$(route_line_value "$TMP_DIR/quote.json" 11 remainingCent)" "new course remaining"
QUOTE_ID=$(field data.quoteId < "$TMP_DIR/quote.json")
QUOTE_HASH=$(field data.quoteHash < "$TMP_DIR/quote.json")
CACHED_STATUS=$(curl -s -o "$TMP_DIR/cached-quote.json" -w '%{http_code}' \
  -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/trade/quotes/$QUOTE_ID")
expect_status 200 "$CACHED_STATUS" "route quote cache read" "$TMP_DIR/cached-quote.json"
expect_value "ROUTE" "$(field data.product.type < "$TMP_DIR/cached-quote.json")" "cached route product"

echo "== Route order creates holds and deduction edge =="
ORDER_STATUS=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s07a-'"$STAMP" -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d "{\"quoteId\":\"$QUOTE_ID\",\"quoteHash\":\"$QUOTE_HASH\",\"selectedCouponIds\":[]}" \
  "$BASE/api/v1/orders")
expect_status 200 "$ORDER_STATUS" "route order" "$TMP_DIR/order.json"
expect_value "WAIT_PAY" "$(field data.state < "$TMP_DIR/order.json")" "route order state"
expect_value "ROUTE" "$(field data.mode < "$TMP_DIR/order.json")" "route order mode"
expect_value "600" "$(field data.pricing.payableCent < "$TMP_DIR/order.json")" "route order payable"
EXPECTED_ITEMS='[{"courseId":"11","payableCent":600},{"courseId":"15","payableCent":0}]'
EXPECTED_ITEMS=$(printf '%s' "$EXPECTED_ITEMS" | tr -d ' ')
ACTUAL_ITEMS=$(node -e '
  const fs=require("fs");
  const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  console.log(JSON.stringify((d.data.items||[]).map(i=>({courseId:String(i.courseId),payableCent:i.payableCent}))));
' "$TMP_DIR/order.json")
expect_value "$EXPECTED_ITEMS" "$ACTUAL_ITEMS" "route order per-course amounts"
ORDER_ID=$(field data.id < "$TMP_DIR/order.json")

echo "== Cancel releases the prepared hold and deduction edge =="
CANCEL_STATUS=$(curl -s -o "$TMP_DIR/cancel.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/orders/$ORDER_ID/cancel")
expect_status 200 "$CANCEL_STATUS" "cancel route order" "$TMP_DIR/cancel.json"
expect_value "CLOSED" "$(field data.state < "$TMP_DIR/cancel.json")" "route order closed"

echo "== All paid members owned: no zero-yuan order =="
OWNED_STATUS=$(curl -s -o "$TMP_DIR/owned.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d "{\"productType\":\"ROUTE\",\"productId\":$OWNED_ROUTE,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
  "$BASE/api/v1/trade/quotes")
expect_status 409 "$OWNED_STATUS" "all-owned route quote is rejected" "$TMP_DIR/owned.json"
expect_value "ALREADY_OWNED" "$(field code < "$TMP_DIR/owned.json")" "all-owned error code"

echo "ALL S07-A ACCEPTANCE CHECKS PASSED"
echo "partialRoute=$PARTIAL_ROUTE order=$ORDER_ID"