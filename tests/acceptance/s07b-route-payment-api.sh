#!/usr/bin/env bash
# S07-b acceptance: route payment, route source, free member, route entitlement and future epochs.
# Requires RabbitMQ running and services started with OUTBOX_TRANSPORT=rabbit OUTBOX_RELAY_ENABLED=true.
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
  if [ "$expected" = "$actual" ]; then
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
  local password="${4:-$DEMO_PASSWORD}"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
}

new_student() {
  local username="s07b_${STAMP}"
  local status
  status=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"Passw0rd!\",\"nickname\":\"S07B\"}" \
    "$BASE/api/v1/auth/register")
  expect_status 200 "$status" "register $username" "$TMP_DIR/register.json"
  login "$username" STUDENT "$TMP_DIR/student.json" "Passw0rd!"
  STUDENT_TOKEN=$(field data.accessToken < "$TMP_DIR/student.json")
}

my_course_source() {
  local course_id="$1" file="$2"
  node -e '
    const fs=require("fs");
    const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    const item=(d.data.items||[]).find(i=>String(i.courseId)===process.argv[2]);
    console.log(item ? item.sourceType : "");
  ' "$file" "$course_id"
}

wait_my_course() {
  local course_id="$1" expected_source="$2" label="$3"
  for _ in $(seq 1 40); do
    curl -s -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/me/courses?page=1&pageSize=100" > "$TMP_DIR/my-courses.json"
    local source
    source=$(my_course_source "$course_id" "$TMP_DIR/my-courses.json")
    if [ "$source" = "$expected_source" ]; then
      echo "PASS  $label  ($source)"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  $label: course $course_id source not $expected_source"
  cat "$TMP_DIR/my-courses.json" 2>/dev/null || true
  exit 1
}

echo "== Admin route setup =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$(field data.accessToken < "$TMP_DIR/admin.json")
STATUS=$(curl -s -o "$TMP_DIR/route.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S07B Route $STAMP\",\"description\":\"route payment\",\"priceCent\":600,\"members\":[{\"courseId\":13,\"sort\":1},{\"courseId\":33,\"sort\":2}]}" \
  "$BASE/api/v1/admin/routes")
expect_status 200 "$STATUS" "create route 13+33" "$TMP_DIR/route.json"
ROUTE_ID=$(field data.id < "$TMP_DIR/route.json")
REVISION=$(field data.revision < "$TMP_DIR/route.json")
STATUS=$(curl -s -o "$TMP_DIR/publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$REVISION}" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID/publish")
expect_status 200 "$STATUS" "publish route" "$TMP_DIR/publish.json"

echo "== Route purchase grants paid + free members =="
new_student
STATUS=$(curl -s -o "$TMP_DIR/quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d "{\"productType\":\"ROUTE\",\"productId\":$ROUTE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
  "$BASE/api/v1/trade/quotes")
expect_status 200 "$STATUS" "quote route" "$TMP_DIR/quote.json"
QUOTE_ID=$(field data.quoteId < "$TMP_DIR/quote.json")
QUOTE_HASH=$(field data.quoteHash < "$TMP_DIR/quote.json")
STATUS=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s07b-route-'"$STAMP" -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d "{\"quoteId\":\"$QUOTE_ID\",\"quoteHash\":\"$QUOTE_HASH\",\"selectedCouponIds\":[]}" \
  "$BASE/api/v1/orders")
expect_status 200 "$STATUS" "create route order" "$TMP_DIR/order.json"
ORDER_ID=$(field data.id < "$TMP_DIR/order.json")
STATUS=$(curl -s -o "$TMP_DIR/payment.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $STUDENT_TOKEN" -d '{"channel":"MOCK"}' \
  "$BASE/api/v1/orders/$ORDER_ID/payment")
expect_status 200 "$STATUS" "create route payment request" "$TMP_DIR/payment.json"
STATUS=$(curl -s -o "$TMP_DIR/pay.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/orders/$ORDER_ID/payment/mock-pay")
expect_status 200 "$STATUS" "mock-pay route order" "$TMP_DIR/pay.json"
wait_fulfillment() {
  for _ in $(seq 1 40); do
    STATUS=$(curl -s -o "$TMP_DIR/order-poll.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT_TOKEN" \
      "$BASE/api/v1/orders/$ORDER_ID")
    if [ "$STATUS" = "200" ] && [ "$(field data.fulfillmentState < "$TMP_DIR/order-poll.json")" = "DONE" ]; then
      echo "PASS  route fulfillment DONE after outbox"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  route fulfillment did not become DONE"
  cat "$TMP_DIR/order-poll.json" 2>/dev/null || true
  exit 1
}
wait_fulfillment
wait_my_course 13 ROUTE_ORDER "paid route member has ROUTE_ORDER source"
wait_my_course 33 FREE "free route member has FREE source"
for _ in $(seq 1 20); do
  curl -s -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/me/routes?page=1&pageSize=100" > "$TMP_DIR/my-routes.json"
  if node -e '
    const d=require(process.argv[1]);
    console.log((d.data.items||[]).some(i=>String(i.routeId)===process.argv[2])?"yes":"no");
  ' "$TMP_DIR/my-routes.json" "$ROUTE_ID" | grep -q yes; then
    echo "PASS  route entitlement visible in my routes"
    break
  fi
  sleep 0.5
done

echo "== Future epoch add/remove keeps history =="
STATUS=$(curl -s -o "$TMP_DIR/detail.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID")
expect_status 200 "$STATUS" "read route detail before add" "$TMP_DIR/detail.json"
REVISION=$(field data.revision < "$TMP_DIR/detail.json")
STATUS=$(curl -s -o "$TMP_DIR/add.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S07B Route $STAMP\",\"description\":\"route payment plus 11\",\"priceCent\":600,\"members\":[{\"courseId\":13,\"sort\":1},{\"courseId\":33,\"sort\":2},{\"courseId\":11,\"sort\":3}],\"revision\":$REVISION}" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID")
expect_status 200 "$STATUS" "add course 11 creates new epoch" "$TMP_DIR/add.json"
wait_my_course 11 ROUTE_ORDER "future paid course gets route source"

STATUS=$(curl -s -o "$TMP_DIR/detail2.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID")
expect_status 200 "$STATUS" "read route detail before remove" "$TMP_DIR/detail2.json"
REVISION=$(field data.revision < "$TMP_DIR/detail2.json")
STATUS=$(curl -s -o "$TMP_DIR/remove.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S07B Route $STAMP\",\"description\":\"route payment\",\"priceCent\":600,\"members\":[{\"courseId\":13,\"sort\":1},{\"courseId\":33,\"sort\":2}],\"revision\":$REVISION}" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID")
expect_status 200 "$STATUS" "remove course 11 closes epoch" "$TMP_DIR/remove.json"
sleep 2
curl -s -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/me/courses?page=1&pageSize=100" > "$TMP_DIR/my-courses-after-remove.json"
SOURCE_AFTER_REMOVE=$(my_course_source 11 "$TMP_DIR/my-courses-after-remove.json")
expect_value "ROUTE_ORDER" "$SOURCE_AFTER_REMOVE" "removed course keeps historical grant"

echo "ALL S07-B ACCEPTANCE CHECKS PASSED"
echo "route=$ROUTE_ID order=$ORDER_ID"