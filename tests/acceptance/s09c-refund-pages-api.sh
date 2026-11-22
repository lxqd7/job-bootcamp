#!/usr/bin/env bash
# S09-c acceptance API: student refund status by order and admin abnormal-refund list/reconcile.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
USERNAME="s09c_$STAMP"
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

db() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -N -B -e "$1"
}

create_course() {
  local status body
  status=$(curl -s -o "$TMP_DIR/course.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"S09C course $STAMP\",\"description\":\"S09C\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create course" "$TMP_DIR/course.json"
  COURSE_ID=$(field data.id < "$TMP_DIR/course.json")
  local rev
  rev=$(field data.revision < "$TMP_DIR/course.json")
  status=$(curl -s -o "$TMP_DIR/chapter.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Chapter","sort":1}' \
    "$BASE/api/v1/admin/courses/$COURSE_ID/chapters")
  expect_status 200 "$status" "create chapter" "$TMP_DIR/chapter.json"
  CHAPTER_ID=$(field data.id < "$TMP_DIR/chapter.json")
  for i in $(seq 1 10); do
    body=$(curl -s -X POST -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $ADMIN_TOKEN" \
      -d "{\"title\":\"Video $i\",\"sort\":$i,\"mediaId\":1}" \
      "$BASE/api/v1/admin/chapters/$CHAPTER_ID/lessons")
  done
  status=$(curl -s -o "$TMP_DIR/publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$rev}" \
    "$BASE/api/v1/admin/courses/$COURSE_ID/publish")
  expect_status 200 "$status" "publish course" "$TMP_DIR/publish.json"
}

buy_and_apply_unknown() {
  local status quote_id quote_hash order_id
  status=$(curl -s -o "$TMP_DIR/quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"productType\":\"COURSE\",\"productId\":$COURSE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
    "$BASE/api/v1/trade/quotes")
  expect_status 200 "$status" "quote" "$TMP_DIR/quote.json"
  quote_id=$(field data.quoteId < "$TMP_DIR/quote.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/quote.json")
  status=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: s09c-$STAMP" -H "Authorization: Bearer $TOKEN" \
    -d "{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[]}" \
    "$BASE/api/v1/orders")
  expect_status 200 "$status" "order" "$TMP_DIR/order.json"
  ORDER_ID=$(field data.id < "$TMP_DIR/order.json")
  status=$(curl -s -o "$TMP_DIR/payment.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" -d '{"channel":"MOCK"}' \
    "$BASE/api/v1/orders/$ORDER_ID/payment")
  expect_status 200 "$status" "payment request" "$TMP_DIR/payment.json"
  status=$(curl -s -o "$TMP_DIR/pay.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
    "$BASE/api/v1/orders/$ORDER_ID/payment/mock-pay")
  expect_status 200 "$status" "mock pay" "$TMP_DIR/pay.json"
  for i in $(seq 1 40); do
    curl -s -o "$TMP_DIR/order-state.json" -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/orders/$ORDER_ID"
    if [ "$(field data.fulfillmentState < "$TMP_DIR/order-state.json")" = "DONE" ]; then break; fi
    sleep 0.5
  done
  expect_value "DONE" "$(field data.fulfillmentState < "$TMP_DIR/order-state.json")" "order fulfilled"
  status=$(curl -s -o "$TMP_DIR/scenario.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"outcome":"UNKNOWN"}' \
    "$BASE/api/v1/dev/refunds/$ORDER_ID/scenario")
  expect_status 200 "$status" "set refund unknown" "$TMP_DIR/scenario.json"
  status=$(curl -s -o "$TMP_DIR/refund.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" -d '{"reason":"S09C"}' \
    "$BASE/api/v1/orders/$ORDER_ID/refund-applications")
  expect_status 202 "$status" "apply refund" "$TMP_DIR/refund.json"
  REQUEST_ID=$(field data.requestId < "$TMP_DIR/refund.json")
}

echo "== Setup student, admin and course =="
STATUS=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"S09CTester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register student" "$TMP_DIR/register.json"
login "$USERNAME" "$PASSWORD" STUDENT "$TMP_DIR/login.json"
STUDENT_TOKEN=$TOKEN
STUDENT_ID=$USER_ID
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
TOKEN=$STUDENT_TOKEN
USER_ID=$STUDENT_ID
create_course
buy_and_apply_unknown
REFUND_ID=$(db "SELECT id FROM bc_trade.refund WHERE order_id=$ORDER_ID AND kind='USER'")
REFUND_NO=$(db "SELECT refund_no FROM bc_trade.refund WHERE id=$REFUND_ID")

echo "== Student refund status by order =="
STATUS=$(curl -s -o "$TMP_DIR/by-order.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/refunds/by-order/$ORDER_ID")
expect_status 200 "$STATUS" "student reads refund by order" "$TMP_DIR/by-order.json"
expect_value "CHANNEL_UNKNOWN" "$(field data.state < "$TMP_DIR/by-order.json")" "student sees channel unknown"
expect_value "990" "$(field data.amountCent < "$TMP_DIR/by-order.json")" "refund amount"
STATUS=$(curl -s -o "$TMP_DIR/by-id.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/refunds/$REFUND_ID")
expect_status 200 "$STATUS" "student reads refund by id" "$TMP_DIR/by-id.json"
expect_value "$REFUND_NO" "$(field data.refundNo < "$TMP_DIR/by-id.json")" "refund number"
STATUS=$(curl -s -o "$TMP_DIR/application.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/refund-applications/$REQUEST_ID")
expect_status 200 "$STATUS" "student reads refund application" "$TMP_DIR/application.json"
expect_value "PROCESSING" "$(field data.state < "$TMP_DIR/application.json")" "application processing"

echo "== Cross-user and role boundaries =="
OTHER="s09co_$STAMP"
STATUS=$(curl -s -o "$TMP_DIR/other-register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$OTHER\",\"password\":\"$PASSWORD\",\"nickname\":\"Other\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register other student" "$TMP_DIR/other-register.json"
login "$OTHER" "$PASSWORD" STUDENT "$TMP_DIR/other-login.json"
OTHER_TOKEN=$TOKEN
STATUS=$(curl -s -o "$TMP_DIR/foreign-refund.json" -w '%{http_code}' -H "Authorization: Bearer $OTHER_TOKEN" \
  "$BASE/api/v1/refunds/by-order/$ORDER_ID")
expect_status 404 "$STATUS" "other student cannot read refund" "$TMP_DIR/foreign-refund.json"
STATUS=$(curl -s -o "$TMP_DIR/student-admin.json" -w '%{http_code}' -H "Authorization: Bearer $OTHER_TOKEN" \
  "$BASE/api/v1/admin/refunds?state=CHANNEL_UNKNOWN")
expect_status 403 "$STATUS" "student cannot list admin refunds" "$TMP_DIR/student-admin.json"
TOKEN=$STUDENT_TOKEN
USER_ID=$STUDENT_ID

echo "== Admin abnormal list and reconcile =="
STATUS=$(curl -s -o "$TMP_DIR/admin-list.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/refunds?state=CHANNEL_UNKNOWN&page=1&pageSize=50")
expect_status 200 "$STATUS" "admin lists unknown refunds" "$TMP_DIR/admin-list.json"
FOUND=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  console.log((d.data.items||[]).some(x=>String(x.id)===process.argv[2]) ? "yes" : "no");
' "$TMP_DIR/admin-list.json" "$REFUND_ID")
expect_value "yes" "$FOUND" "admin list contains refund"
ADMIN_STATE=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  const row=(d.data.items||[]).find(x=>String(x.id)===process.argv[2]);
  console.log(row ? row.state : "");
' "$TMP_DIR/admin-list.json" "$REFUND_ID")
expect_value "CHANNEL_UNKNOWN" "$ADMIN_STATE" "admin list state"
STATUS=$(curl -s -o "$TMP_DIR/scenario2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"outcome":"COMPLETED"}' \
  "$BASE/api/v1/dev/refunds/$ORDER_ID/scenario")
expect_status 200 "$STATUS" "set refund completed" "$TMP_DIR/scenario2.json"
STATUS=$(curl -s -o "$TMP_DIR/reconcile.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/refunds/$REFUND_ID/reconcile")
expect_status 200 "$STATUS" "admin reconcile" "$TMP_DIR/reconcile.json"
expect_value "COMPLETED" "$(field data.state < "$TMP_DIR/reconcile.json")" "reconcile completed"
STATUS=$(curl -s -o "$TMP_DIR/by-order-done.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/refunds/by-order/$ORDER_ID")
expect_status 200 "$STATUS" "student sees completed refund" "$TMP_DIR/by-order-done.json"
expect_value "COMPLETED" "$(field data.state < "$TMP_DIR/by-order-done.json")" "student completed state"
STATUS=$(curl -s -o "$TMP_DIR/admin-completed.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/refunds?state=COMPLETED&page=1&pageSize=50")
expect_status 200 "$STATUS" "admin lists completed refunds" "$TMP_DIR/admin-completed.json"
FOUND=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  console.log((d.data.items||[]).some(x=>String(x.id)===process.argv[2]) ? "yes" : "no");
' "$TMP_DIR/admin-completed.json" "$REFUND_ID")
expect_value "yes" "$FOUND" "completed list contains refund"

echo "ALL S09-C API CHECKS PASSED"
echo "order=$ORDER_ID refund=$REFUND_ID request=$REQUEST_ID"