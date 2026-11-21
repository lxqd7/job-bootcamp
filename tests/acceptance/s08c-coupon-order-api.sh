#!/usr/bin/env bash
# S08-c acceptance: all-or-none coupon lock/use/release, expiry boundaries, recovery and order coupon snapshots.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
MARKETING_BASE="${MARKETING_BASE:-http://127.0.0.1:18087}"
COURSE_ID="${COURSE_ID:-11}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
INTERNAL_SECRET="${INTERNAL_TOKEN_KEY:-dev-only-internal-secret}"
STAMP="$(date +%s)"
USERNAME="s08c_$STAMP"
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
}

create_coupon() {
  local key="$1" type="$2" threshold="$3" amount="$4" rate="$5" max="$6"
  local amount_json="null"; [ "$amount" != "null" ] && amount_json="$amount"
  local rate_json="null"; [ "$rate" != "null" ] && rate_json="$rate"
  local max_json="null"; [ "$max" != "null" ] && max_json="$max"
  local status
  STATUS=$(curl -s -o "$TMP_DIR/rule.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"ruleKey\":\"$key\",\"type\":\"$type\",\"scope\":\"ALL\",\"thresholdCent\":$threshold,\"amountCent\":$amount_json,\"payRateBps\":$rate_json,\"maxDiscountCent\":$max_json,\"validityType\":\"DAYS\",\"validDays\":7}" \
    "$BASE/api/v1/admin/coupon-rules")
  expect_status 200 "$STATUS" "create coupon rule $key" "$TMP_DIR/rule.json"
  RULE_ID=$(field data.id < "$TMP_DIR/rule.json")
  STATUS=$(curl -s -o "$TMP_DIR/activity.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"name\":\"$key activity\",\"openAt\":\"$NOW\",\"closeAt\":\"$END\"}" \
    "$BASE/api/v1/admin/activities")
  expect_status 200 "$STATUS" "create activity $key" "$TMP_DIR/activity.json"
  ACTIVITY_ID=$(field data.id < "$TMP_DIR/activity.json")
  STATUS=$(curl -s -o "$TMP_DIR/item.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"ruleId\":$RULE_ID,\"stockTotal\":10,\"perUserLimit\":1}" \
    "$BASE/api/v1/admin/activities/$ACTIVITY_ID/items")
  expect_status 200 "$STATUS" "add item $key" "$TMP_DIR/item.json"
  local item_id revision
  item_id=$(field data.items.0.id < "$TMP_DIR/item.json")
  revision=$(field data.revision < "$TMP_DIR/item.json")
  STATUS=$(curl -s -o "$TMP_DIR/active.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$revision}" \
    "$BASE/api/v1/admin/activities/$ACTIVITY_ID/activate")
  expect_status 200 "$STATUS" "activate $key" "$TMP_DIR/active.json"
  STATUS=$(curl -s -o "$TMP_DIR/claim.json" -w '%{http_code}' -X POST \
    -H "Idempotency-Key: $key-claim" -H "Authorization: Bearer $TOKEN" \
    "$BASE/api/v1/activity-items/$item_id/claims")
  expect_status 200 "$STATUS" "claim $key" "$TMP_DIR/claim.json"
  COUPON_ID=$(field data.id < "$TMP_DIR/claim.json")
}

quote() {
  local mode="$1" ids="$2" out="$3"
  local body="{\"productType\":\"COURSE\",\"productId\":$COURSE_ID,\"couponSelection\":{\"mode\":\"$mode\",\"ids\":[$ids]}}"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TOKEN" -d "$body" "$BASE/api/v1/trade/quotes")
  echo "$status"
}

plan_ids() {
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    console.log((d.data.couponPlan||[]).map(x=>x.userCouponId).join(","));
  ' "$1"
}

plan_count() {
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    console.log((d.data.couponPlan||[]).length);
  ' "$1"
}

plan_contains() {
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    console.log((d.data.couponPlan||[]).some(x=>String(x.userCouponId)===process.argv[2]) ? "yes" : "no");
  ' "$1" "$2"
}

create_order() {
  local quote_file="$1" key="$2" out="$3"
  local quote_id quote_hash ids
  quote_id=$(field data.quoteId < "$quote_file")
  quote_hash=$(field data.quoteHash < "$quote_file")
  ids=$(plan_ids "$quote_file")
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $key" -H "Authorization: Bearer $TOKEN" \
    -d "{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[$ids]}" \
    "$BASE/api/v1/orders")
  echo "$status"
}

db() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -N -B -e "$1"
}

internal_token() {
  node -e '
    const crypto=require("crypto");
    const payload=["v1","acceptance","bootcamp-marketing","coupon:recover",Math.floor(Date.now()/1000)+60,crypto.randomUUID()].join("|");
    const sig=crypto.createHmac("sha256",process.argv[1]).update(payload).digest("base64")
      .replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");
    const body=Buffer.from(payload).toString("base64").replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");
    console.log(body+"."+sig);
  ' "$INTERNAL_SECRET"
}

NOW=$(node -e 'console.log(new Date(Date.now()-3600000).toISOString().slice(0,19))')
END=$(node -e 'console.log(new Date(Date.now()+86400000).toISOString().slice(0,19))')

echo "== Setup =="
STATUS=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"nickname\":\"CouponOrderTester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register $USERNAME" "$TMP_DIR/register.json"
login "$USERNAME" "$PASSWORD" STUDENT "$TMP_DIR/login.json"
STUDENT_TOKEN=$TOKEN
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
TOKEN=$STUDENT_TOKEN

create_coupon "S08C_A_$STAMP" NO_THRESHOLD 0 200 null null
C1=$COUPON_ID
create_coupon "S08C_B_$STAMP" DISCOUNT 0 null 9000 500
C2=$COUPON_ID
create_coupon "S08C_C_$STAMP" NO_THRESHOLD 0 100 null null
C3=$COUPON_ID
create_coupon "S08C_ZERO_$STAMP" DISCOUNT 0 null 9999 500
C4=$COUPON_ID

echo "== AUTO quote excludes zero-discount coupon and recommends three == "
STATUS=$(quote AUTO "" "$TMP_DIR/auto.json")
expect_status 200 "$STATUS" "AUTO quote" "$TMP_DIR/auto.json"
expect_value "READY" "$(field data.status < "$TMP_DIR/auto.json")" "AUTO ready"
expect_value "399" "$(field data.discountCent < "$TMP_DIR/auto.json")" "AUTO discount"
expect_value "591" "$(field data.payableCent < "$TMP_DIR/auto.json")" "AUTO payable"
expect_value "3" "$(plan_count "$TMP_DIR/auto.json")" "AUTO uses three coupons"
expect_value "no" "$(plan_contains "$TMP_DIR/auto.json" "$C4")" "zero-discount coupon excluded"
AUTO_IDS=$(plan_ids "$TMP_DIR/auto.json")

echo "== Duplicate coupon IDs are rejected without locking =="
AUTO_QUOTE_ID=$(field data.quoteId < "$TMP_DIR/auto.json")
AUTO_HASH=$(field data.quoteHash < "$TMP_DIR/auto.json")
STATUS=$(curl -s -o "$TMP_DIR/dup.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "Idempotency-Key: s08c-dup-$STAMP" -H "Authorization: Bearer $TOKEN" \
  -d "{\"quoteId\":\"$AUTO_QUOTE_ID\",\"quoteHash\":\"$AUTO_HASH\",\"selectedCouponIds\":[$C1,$C1]}" \
  "$BASE/api/v1/orders")
expect_status 422 "$STATUS" "duplicate coupon ids rejected" "$TMP_DIR/dup.json"
DUP_LOCKS=$(db "SELECT COUNT(*) FROM bc_marketing.coupon_lock WHERE user_coupon_id=$C1")
expect_value "0" "$DUP_LOCKS" "duplicate request did not lock"

echo "== Order locks all selected coupons atomically =="
STATUS=$(create_order "$TMP_DIR/auto.json" "s08c-order-1-$STAMP" "$TMP_DIR/order1.json")
expect_status 200 "$STATUS" "create coupon order" "$TMP_DIR/order1.json"
expect_value "WAIT_PAY" "$(field data.state < "$TMP_DIR/order1.json")" "order state"
expect_value "399" "$(field data.pricing.discountCent < "$TMP_DIR/order1.json")" "order discount"
expect_value "591" "$(field data.pricing.payableCent < "$TMP_DIR/order1.json")" "order payable"
expect_value "399" "$(field data.items.0.couponCent < "$TMP_DIR/order1.json")" "course coupon amount"
expect_value "591" "$(field data.items.0.payableCent < "$TMP_DIR/order1.json")" "course payable"
ORDER1=$(field data.id < "$TMP_DIR/order1.json")
ORDER_NO=$(field data.orderNo < "$TMP_DIR/order1.json")
LOCKED_COUNT=$(db "SELECT COUNT(*) FROM bc_marketing.user_coupon WHERE id IN ($AUTO_IDS) AND status='LOCKED' AND locked_order_id=$ORDER1")
expect_value "3" "$LOCKED_COUNT" "three user coupons locked"
LOCK_ROW=$(db "SELECT COUNT(*), MIN(state) FROM bc_marketing.coupon_lock WHERE order_id=$ORDER1" | tr '\t' '|')
expect_value "3|LOCKED" "$LOCK_ROW" "three coupon_lock rows"
ORDER_COUPON_ROWS=$(db "SELECT COUNT(*), SUM(discount_cent) FROM bc_trade.order_coupon WHERE order_id=$ORDER1" | tr '\t' '|')
expect_value "3|399" "$ORDER_COUPON_ROWS" "order coupon snapshot rows"

echo "== Same idempotency key does not double lock =="
STATUS=$(create_order "$TMP_DIR/auto.json" "s08c-order-1-$STAMP" "$TMP_DIR/order1-replay.json")
expect_status 200 "$STATUS" "replay order create" "$TMP_DIR/order1-replay.json"
expect_value "$ORDER1" "$(field data.id < "$TMP_DIR/order1-replay.json")" "same order id"
LOCK_COUNT=$(db "SELECT COUNT(*) FROM bc_marketing.coupon_lock WHERE order_id=$ORDER1")
expect_value "3" "$LOCK_COUNT" "lock rows not duplicated"

echo "== Cancel releases every coupon, preserving validity =="
STATUS=$(curl -s -o "$TMP_DIR/cancel1.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/orders/$ORDER1/cancel")
expect_status 200 "$STATUS" "cancel coupon order" "$TMP_DIR/cancel1.json"
expect_value "CLOSED" "$(field data.state < "$TMP_DIR/cancel1.json")" "order closed"
RELEASED=$(db "SELECT COUNT(*) FROM bc_marketing.user_coupon WHERE id IN ($AUTO_IDS) AND status='AVAILABLE' AND locked_order_id IS NULL")
expect_value "3" "$RELEASED" "three coupons available again"
LOCK_ROW=$(db "SELECT COUNT(*), MIN(state) FROM bc_marketing.coupon_lock WHERE order_id=$ORDER1" | tr '\t' '|')
expect_value "3|RELEASED" "$LOCK_ROW" "lock rows released"
ORDER_STATE=$(db "SELECT coupon_state FROM bc_trade.trade_order WHERE id=$ORDER1")
expect_value "RELEASED" "$ORDER_STATE" "order coupon state released"

echo "== Re-lock after release, then pay after coupon expiry =="
STATUS=$(quote AUTO "" "$TMP_DIR/auto2.json")
expect_status 200 "$STATUS" "second AUTO quote" "$TMP_DIR/auto2.json"
STATUS=$(create_order "$TMP_DIR/auto2.json" "s08c-order-2-$STAMP" "$TMP_DIR/order2.json")
expect_status 200 "$STATUS" "second coupon order" "$TMP_DIR/order2.json"
ORDER2=$(field data.id < "$TMP_DIR/order2.json")
ORDER2_IDS=$(plan_ids "$TMP_DIR/auto2.json")
db "UPDATE bc_marketing.user_coupon SET valid_until=UTC_TIMESTAMP(3) - INTERVAL 1 MINUTE WHERE id IN ($ORDER2_IDS)"
STATUS=$(curl -s -o "$TMP_DIR/payment2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" -d '{"channel":"MOCK"}' \
  "$BASE/api/v1/orders/$ORDER2/payment")
expect_status 200 "$STATUS" "create mock payment request" "$TMP_DIR/payment2.json"
STATUS=$(curl -s -o "$TMP_DIR/pay2.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/orders/$ORDER2/payment/mock-pay")
expect_status 200 "$STATUS" "pay locked order after coupon expiry" "$TMP_DIR/pay2.json"
expect_value "PAID" "$(field data.state < "$TMP_DIR/pay2.json")" "order paid"
USED=$(db "SELECT COUNT(*) FROM bc_marketing.user_coupon WHERE id IN ($ORDER2_IDS) AND status='USED' AND used_order_id=$ORDER2")
expect_value "3" "$USED" "locked coupons consumed even after expiry"
LOCK_ROW=$(db "SELECT COUNT(*), MIN(state) FROM bc_marketing.coupon_lock WHERE order_id=$ORDER2" | tr '\t' '|')
expect_value "3|USED" "$LOCK_ROW" "lock rows used"
ORDER_STATE=$(db "SELECT coupon_state FROM bc_trade.trade_order WHERE id=$ORDER2")
expect_value "USED" "$ORDER_STATE" "order coupon state used"

echo "== Zero-discount coupon stays available and cannot be ordered =="
STATUS=$(quote MANUAL "$C4" "$TMP_DIR/zero.json")
expect_status 422 "$STATUS" "zero-discount manual quote rejected" "$TMP_DIR/zero.json"
ZERO_STATE=$(db "SELECT status FROM bc_marketing.user_coupon WHERE id=$C4")
expect_value "AVAILABLE" "$ZERO_STATE" "zero-discount coupon not consumed"

echo "== Released coupon that later expires cannot be reused =="
create_coupon "S08C_EXPIRE_$STAMP" NO_THRESHOLD 0 100 null null
C5=$COUPON_ID
STATUS=$(quote MANUAL "$C5" "$TMP_DIR/c5.json")
expect_status 200 "$STATUS" "quote with expiring coupon" "$TMP_DIR/c5.json"
STATUS=$(create_order "$TMP_DIR/c5.json" "s08c-order-3-$STAMP" "$TMP_DIR/order3.json")
expect_status 200 "$STATUS" "order with expiring coupon" "$TMP_DIR/order3.json"
ORDER3=$(field data.id < "$TMP_DIR/order3.json")
STATUS=$(curl -s -o "$TMP_DIR/cancel3.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/orders/$ORDER3/cancel")
expect_status 200 "$STATUS" "cancel expiring coupon order" "$TMP_DIR/cancel3.json"
db "UPDATE bc_marketing.user_coupon SET valid_until=UTC_TIMESTAMP(3) - INTERVAL 1 MINUTE WHERE id=$C5"
STATUS=$(quote MANUAL "$C5" "$TMP_DIR/c5-expired.json")
expect_status 422 "$STATUS" "released expired coupon cannot be quoted" "$TMP_DIR/c5-expired.json"
C5_STATE=$(db "SELECT status FROM bc_marketing.user_coupon WHERE id=$C5")
expect_value "AVAILABLE" "$C5_STATE" "expired released coupon remains available projection"

echo "== Marketing recovery releases a lock whose order was already closed =="
create_coupon "S08C_RECOVER_$STAMP" NO_THRESHOLD 0 100 null null
C6=$COUPON_ID
STATUS=$(quote MANUAL "$C6" "$TMP_DIR/c6.json")
expect_status 200 "$STATUS" "quote for recovery" "$TMP_DIR/c6.json"
STATUS=$(create_order "$TMP_DIR/c6.json" "s08c-order-4-$STAMP" "$TMP_DIR/order4.json")
expect_status 200 "$STATUS" "order for recovery" "$TMP_DIR/order4.json"
ORDER4=$(field data.id < "$TMP_DIR/order4.json")
db "UPDATE bc_trade.trade_order SET state='CLOSED' WHERE id=$ORDER4"
db "UPDATE bc_marketing.coupon_lock SET updated_at=UTC_TIMESTAMP(3)-INTERVAL 5 MINUTE WHERE order_id=$ORDER4"
RECOVER_TOKEN=$(internal_token)
STATUS=$(curl -s -o "$TMP_DIR/recover.json" -w '%{http_code}' -X POST \
  -H "X-Service-Token: $RECOVER_TOKEN" "$MARKETING_BASE/int/v1/coupon-locks/recover")
expect_status 200 "$STATUS" "internal coupon recovery" "$TMP_DIR/recover.json"
C6_STATE=$(db "SELECT status, locked_order_id IS NULL FROM bc_marketing.user_coupon WHERE id=$C6" | tr '\t' '|')
expect_value "AVAILABLE|1" "$C6_STATE" "recovery released coupon"
RECOVER_STATE=$(db "SELECT state FROM bc_marketing.coupon_lock WHERE order_id=$ORDER4")
expect_value "RELEASED" "$RECOVER_STATE" "recovery release lock row"

echo "== Route order allocates coupon across member courses =="
ROUTE_USER="s08cr_$STAMP"
STATUS=$(curl -s -o "$TMP_DIR/route-register.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ROUTE_USER\",\"password\":\"$PASSWORD\",\"nickname\":\"RouteCouponTester\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$STATUS" "register route user" "$TMP_DIR/route-register.json"
login "$ROUTE_USER" "$PASSWORD" STUDENT "$TMP_DIR/route-login.json"
STATUS=$(curl -s -o "$TMP_DIR/route.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S08C route $STAMP\",\"description\":\"S08C\",\"priceCent\":1200,\"members\":[{\"courseId\":13,\"sort\":1},{\"courseId\":11,\"sort\":2}]}" \
  "$BASE/api/v1/admin/routes")
expect_status 200 "$STATUS" "create route" "$TMP_DIR/route.json"
ROUTE_ID=$(field data.id < "$TMP_DIR/route.json")
ROUTE_REV=$(field data.revision < "$TMP_DIR/route.json")
STATUS=$(curl -s -o "$TMP_DIR/route-pub.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$ROUTE_REV}" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID/publish")
expect_status 200 "$STATUS" "publish route" "$TMP_DIR/route-pub.json"

STATUS=$(curl -s -o "$TMP_DIR/route-rule.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleKey\":\"S08C_ROUTE_$STAMP\",\"type\":\"NO_THRESHOLD\",\"scope\":\"ROUTE\",\"targetId\":$ROUTE_ID,\"thresholdCent\":0,\"amountCent\":200,\"validityType\":\"DAYS\",\"validDays\":7}" \
  "$BASE/api/v1/admin/coupon-rules")
expect_status 200 "$STATUS" "create route coupon rule" "$TMP_DIR/route-rule.json"
ROUTE_RULE_ID=$(field data.id < "$TMP_DIR/route-rule.json")
STATUS=$(curl -s -o "$TMP_DIR/route-activity.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"S08C_ROUTE_ACT_$STAMP\",\"openAt\":\"$NOW\",\"closeAt\":\"$END\"}" \
  "$BASE/api/v1/admin/activities")
expect_status 200 "$STATUS" "create route coupon activity" "$TMP_DIR/route-activity.json"
ROUTE_ACTIVITY_ID=$(field data.id < "$TMP_DIR/route-activity.json")
STATUS=$(curl -s -o "$TMP_DIR/route-item.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleId\":$ROUTE_RULE_ID,\"stockTotal\":10,\"perUserLimit\":1}" \
  "$BASE/api/v1/admin/activities/$ROUTE_ACTIVITY_ID/items")
expect_status 200 "$STATUS" "create route coupon item" "$TMP_DIR/route-item.json"
ROUTE_ITEM_ID=$(field data.items.0.id < "$TMP_DIR/route-item.json")
ROUTE_ITEM_REV=$(field data.revision < "$TMP_DIR/route-item.json")
STATUS=$(curl -s -o "$TMP_DIR/route-active.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$ROUTE_ITEM_REV}" \
  "$BASE/api/v1/admin/activities/$ROUTE_ACTIVITY_ID/activate")
expect_status 200 "$STATUS" "activate route coupon activity" "$TMP_DIR/route-active.json"
STATUS=$(curl -s -o "$TMP_DIR/route-claim.json" -w '%{http_code}' -X POST \
  -H "Idempotency-Key: s08c-route-$STAMP" -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/activity-items/$ROUTE_ITEM_ID/claims")
expect_status 200 "$STATUS" "claim route coupon" "$TMP_DIR/route-claim.json"

STATUS=$(curl -s -o "$TMP_DIR/route-quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"productType\":\"ROUTE\",\"productId\":$ROUTE_ID,\"couponSelection\":{\"mode\":\"AUTO\",\"ids\":[]}}" \
  "$BASE/api/v1/trade/quotes")
expect_status 200 "$STATUS" "route AUTO quote" "$TMP_DIR/route-quote.json"
expect_value "200" "$(field data.discountCent < "$TMP_DIR/route-quote.json")" "route coupon discount"
expect_value "1000" "$(field data.payableCent < "$TMP_DIR/route-quote.json")" "route coupon payable"
expect_value "1" "$(plan_count "$TMP_DIR/route-quote.json")" "route coupon plan"
STATUS=$(create_order "$TMP_DIR/route-quote.json" "s08c-route-order-$STAMP" "$TMP_DIR/route-order.json")
expect_status 200 "$STATUS" "create route coupon order" "$TMP_DIR/route-order.json"
ROUTE_ORDER=$(field data.id < "$TMP_DIR/route-order.json")
expect_value "1000" "$(field data.pricing.payableCent < "$TMP_DIR/route-order.json")" "route order payable"
ROUTE_ALLOC=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  console.log((d.data.items||[]).reduce((s,x)=>s+Number(x.couponCent),0));
' "$TMP_DIR/route-order.json")
expect_value "200" "$ROUTE_ALLOC" "route order coupon allocation"
ROUTE_LOCKS=$(db "SELECT COUNT(*) FROM bc_marketing.coupon_lock WHERE order_id=$ROUTE_ORDER AND state='LOCKED'")
expect_value "1" "$ROUTE_LOCKS" "route coupon locked"
STATUS=$(curl -s -o "$TMP_DIR/route-cancel.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/orders/$ROUTE_ORDER/cancel")
expect_status 200 "$STATUS" "cancel route coupon order" "$TMP_DIR/route-cancel.json"
ROUTE_RELEASED=$(db "SELECT state FROM bc_marketing.coupon_lock WHERE order_id=$ROUTE_ORDER")
expect_value "RELEASED" "$ROUTE_RELEASED" "route coupon released"

echo "== 100 concurrent students compete for 10 stock =="
create_rule() {
  local key="$1" out="$2"
  STATUS=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"ruleKey\":\"$key\",\"type\":\"NO_THRESHOLD\",\"scope\":\"ALL\",\"thresholdCent\":0,\"amountCent\":100,\"validityType\":\"DAYS\",\"validDays\":7}" \
    "$BASE/api/v1/admin/coupon-rules")
  expect_status 200 "$STATUS" "create stock rule" "$out"
  STOCK_RULE_ID=$(field data.id < "$out")
  STATUS=$(curl -s -o "$out.activity" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"name\":\"S08C_STOCK_$STAMP\",\"openAt\":\"$NOW\",\"closeAt\":\"$END\"}" \
    "$BASE/api/v1/admin/activities")
  expect_status 200 "$STATUS" "create stock activity" "$out.activity"
  STOCK_ACTIVITY_ID=$(field data.id < "$out.activity")
  STATUS=$(curl -s -o "$out.item" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"ruleId\":$STOCK_RULE_ID,\"stockTotal\":10,\"perUserLimit\":1}" \
    "$BASE/api/v1/admin/activities/$STOCK_ACTIVITY_ID/items")
  expect_status 200 "$STATUS" "create stock item" "$out.item"
  STOCK_ITEM_ID=$(field data.items.0.id < "$out.item")
  STOCK_REV=$(field data.revision < "$out.item")
  STATUS=$(curl -s -o "$out.active" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$STOCK_REV}" \
    "$BASE/api/v1/admin/activities/$STOCK_ACTIVITY_ID/activate")
  expect_status 200 "$STATUS" "activate stock activity" "$out.active"
}
create_rule "S08C_STOCK_$STAMP" "$TMP_DIR/stock.json"
mkdir -p "$TMP_DIR/stock"
for i in $(seq 1 100); do
  local_user="s08cs_${STAMP}_$i"
  STATUS=$(curl -s -o "$TMP_DIR/stock/reg-$i.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$local_user\",\"password\":\"$PASSWORD\",\"nickname\":\"StockTester\"}" \
    "$BASE/api/v1/auth/register")
  if [ "$STATUS" != "200" ]; then echo "FAIL  register stock user $i (HTTP $STATUS)"; exit 1; fi
  STATUS=$(curl -s -o "$TMP_DIR/stock/login-$i.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$local_user\",\"password\":\"$PASSWORD\",\"clientApp\":\"STUDENT\"}" \
    "$BASE/api/v1/auth/login")
  if [ "$STATUS" != "200" ]; then echo "FAIL  login stock user $i (HTTP $STATUS)"; exit 1; fi
  field data.accessToken < "$TMP_DIR/stock/login-$i.json" > "$TMP_DIR/stock/token-$i"
done
echo "PASS  100 stock users registered and logged in"
for i in $(seq 1 100); do
  (
    curl -s -o "$TMP_DIR/stock/claim-$i.json" -w '%{http_code}' -X POST \
      -H "Idempotency-Key: s08c-stock-$STAMP-$i" \
      -H "Authorization: Bearer $(cat "$TMP_DIR/stock/token-$i")" \
      "$BASE/api/v1/activity-items/$STOCK_ITEM_ID/claims" > "$TMP_DIR/stock/status-$i"
  ) &
done
wait
OK_COUNT=$(grep -l '^200$' "$TMP_DIR"/stock/status-* | wc -l | tr -d ' ')
CONFLICT_COUNT=$(grep -l '^409$' "$TMP_DIR"/stock/status-* | wc -l | tr -d ' ')
expect_value "10" "$OK_COUNT" "ten claims succeeded"
expect_value "90" "$CONFLICT_COUNT" "ninety claims rejected"
STOCK_ISSUED=$(db "SELECT issued_count, stock_total FROM bc_marketing.activity_item WHERE id=$STOCK_ITEM_ID" | tr '\t' '|')
expect_value "10|10" "$STOCK_ISSUED" "stock issued count"

echo "ALL S08-C ACCEPTANCE CHECKS PASSED"
echo "orderLocked=$ORDER1 orderPaid=$ORDER2 orderRecovered=$ORDER4"