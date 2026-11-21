#!/usr/bin/env bash
# S08-b acceptance: coupon pricing (AUTO/MANUAL), category scope, same-activity exclusion,
# route allocation, quote cache, ownership and concurrent quote requests.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
TRADE_COURSE_ID="${TRADE_COURSE_ID:-11}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
STAMP="$(date +%s)"
TEST_USER="s08b_${STAMP}"
ROUTE_USER="s08br_${STAMP}"
TEST_PASSWORD="Passw0rd!"
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
  local username="$1" app="$2" out="$3" password="${4:-$DEMO_PASSWORD}"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
}

register() {
  local username="$1" out="$2"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$TEST_PASSWORD\",\"nickname\":\"PricingTester\"}" \
    "$BASE/api/v1/auth/register")
  expect_status 200 "$status" "register $username" "$out"
}

create_rule() {
  local key="$1" type="$2" scope="$3" target="$4" threshold="$5" amount="$6" rate="$7" max="$8" out="$9"
  local target_json="null"; [ "$target" != "null" ] && target_json="$target"
  local amount_json="null"; [ "$amount" != "null" ] && amount_json="$amount"
  local rate_json="null"; [ "$rate" != "null" ] && rate_json="$rate"
  local max_json="null"; [ "$max" != "null" ] && max_json="$max"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"ruleKey\":\"$key\",\"type\":\"$type\",\"scope\":\"$scope\",\"targetId\":$target_json,\"thresholdCent\":$threshold,\"amountCent\":$amount_json,\"payRateBps\":$rate_json,\"maxDiscountCent\":$max_json,\"validityType\":\"DAYS\",\"validDays\":7}" \
    "$BASE/api/v1/admin/coupon-rules")
  expect_status 200 "$status" "create rule $key" "$out"
  RULE_ID=$(field data.id < "$out")
}

create_activity() {
  local name="$1" out="$2"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"name\":\"$name\",\"openAt\":\"$NOW\",\"closeAt\":\"$END\"}" \
    "$BASE/api/v1/admin/activities")
  expect_status 200 "$status" "create activity $name" "$out"
  ACTIVITY_ID=$(field data.id < "$out")
}

add_item() {
  local stock="$1" limit="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"ruleId\":$RULE_ID,\"stockTotal\":$stock,\"perUserLimit\":$limit}" \
    "$BASE/api/v1/admin/activities/$ACTIVITY_ID/items")
  expect_status 200 "$status" "add activity item" "$out"
  ITEM_ID=$(field data.items.0.id < "$out")
  ACTIVITY_REVISION=$(field data.revision < "$out")
}

activate_activity() {
  local out="$1"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$ACTIVITY_REVISION}" \
    "$BASE/api/v1/admin/activities/$ACTIVITY_ID/activate")
  expect_status 200 "$status" "activate activity" "$out"
  expect_value "ACTIVE" "$(field data.state < "$out")" "activity active"
}

claim() {
  local token="$1" item="$2" key="$3" out="$4"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H "Idempotency-Key: $key" \
    -H "Authorization: Bearer $token" "$BASE/api/v1/activity-items/$item/claims")
  expect_status 200 "$status" "claim coupon" "$out"
  COUPON_ID=$(field data.id < "$out")
}

quote() {
  local token="$1" body="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d "$body" "$BASE/api/v1/trade/quotes")
  echo "$status"
}

plan_ids() {
  local file="$1"
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    console.log((d.data.couponPlan||[]).map(x=>String(x.userCouponId)).join(","));
  ' "$file"
}

plan_size() {
  local file="$1"
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    console.log((d.data.couponPlan||[]).length);
  ' "$file"
}

allocation_sum() {
  local file="$1"
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    console.log((d.data.couponPlan||[]).flatMap(x=>x.allocation||[]).reduce((s,x)=>s+Number(x.discountCent),0));
  ' "$file"
}

course_remaining_sum() {
  local file="$1"
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    console.log((d.data.courses||[]).reduce((s,x)=>s+Number(x.remainingCent),0));
  ' "$file"
}

NOW=$(node -e 'console.log(new Date(Date.now()-3600000).toISOString().slice(0,19))')
END=$(node -e 'console.log(new Date(Date.now()+86400000).toISOString().slice(0,19))')

echo "== Setup student and coupon sources =="
register "$TEST_USER" "$TMP_DIR/register.json"
login "$TEST_USER" STUDENT "$TMP_DIR/login.json" "$TEST_PASSWORD"
TOKEN=$(field data.accessToken < "$TMP_DIR/login.json")
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$(field data.accessToken < "$TMP_DIR/admin.json")
STATUS=$(curl -s -o "$TMP_DIR/course.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/courses/$TRADE_COURSE_ID")
expect_status 200 "$STATUS" "read course detail" "$TMP_DIR/course.json"
CATEGORY_ID=$(field data.categoryId < "$TMP_DIR/course.json")

create_rule "S08B_R1_${STAMP}" FULL_REDUCTION ALL null 100 200 null null "$TMP_DIR/r1.json"
R1=$RULE_ID
create_activity "S08B A $STAMP" "$TMP_DIR/a1.json"
add_item 10 1 "$TMP_DIR/i1.json"
activate_activity "$TMP_DIR/a1a.json"
claim "$TOKEN" "$ITEM_ID" "s08b-a1-$STAMP" "$TMP_DIR/c1.json"
C1=$COUPON_ID

create_rule "S08B_R2_${STAMP}" DISCOUNT ALL null 0 null 9000 500 "$TMP_DIR/r2.json"
R2=$RULE_ID
create_activity "S08B B $STAMP" "$TMP_DIR/a2.json"
add_item 10 1 "$TMP_DIR/i2.json"
activate_activity "$TMP_DIR/a2a.json"
claim "$TOKEN" "$ITEM_ID" "s08b-a2-$STAMP" "$TMP_DIR/c2.json"
C2=$COUPON_ID

create_rule "S08B_R3_${STAMP}" NO_THRESHOLD CATEGORY "$CATEGORY_ID" 0 100 null null "$TMP_DIR/r3.json"
R3=$RULE_ID
create_activity "S08B C $STAMP" "$TMP_DIR/a3.json"
add_item 10 1 "$TMP_DIR/i3.json"
activate_activity "$TMP_DIR/a3a.json"
claim "$TOKEN" "$ITEM_ID" "s08b-a3-$STAMP" "$TMP_DIR/c3.json"
C3=$COUPON_ID

create_rule "S08B_R4_${STAMP}" NO_THRESHOLD ALL null 0 50 null null "$TMP_DIR/r4.json"
R4=$RULE_ID
create_activity "S08B D $STAMP" "$TMP_DIR/a4.json"
add_item 10 1 "$TMP_DIR/i4.json"
activate_activity "$TMP_DIR/a4a.json"
claim "$TOKEN" "$ITEM_ID" "s08b-a4-$STAMP" "$TMP_DIR/c4.json"
C4=$COUPON_ID

echo "== Same activity only one coupon may apply =="
create_activity "S08B E $STAMP" "$TMP_DIR/a5.json"
create_rule "S08B_R5_${STAMP}" NO_THRESHOLD ALL null 0 300 null null "$TMP_DIR/r5.json"
R5=$RULE_ID
add_item 10 2 "$TMP_DIR/i5.json"
create_rule "S08B_R6_${STAMP}" NO_THRESHOLD ALL null 0 250 null null "$TMP_DIR/r6.json"
R6=$RULE_ID
add_item 10 2 "$TMP_DIR/i6.json"
activate_activity "$TMP_DIR/a5a.json"
CLAIM_ITEM=$(field data.items.0.id < "$TMP_DIR/i5.json")
claim "$TOKEN" "$CLAIM_ITEM" "s08b-a5-$STAMP" "$TMP_DIR/c5.json"
C5=$COUPON_ID
CLAIM_ITEM=$(field data.items.1.id < "$TMP_DIR/i6.json")
claim "$TOKEN" "$CLAIM_ITEM" "s08b-a5b-$STAMP" "$TMP_DIR/c6.json"
C6=$COUPON_ID

echo "== Category scope does not match an unrelated category =="
create_rule "S08B_R7_${STAMP}" NO_THRESHOLD CATEGORY 999999 0 500 null null "$TMP_DIR/r7.json"
R7=$RULE_ID
create_activity "S08B F $STAMP" "$TMP_DIR/a7.json"
add_item 10 1 "$TMP_DIR/i7.json"
activate_activity "$TMP_DIR/a7a.json"
claim "$TOKEN" "$ITEM_ID" "s08b-a7-$STAMP" "$TMP_DIR/c7.json"
C7=$COUPON_ID

echo "== NONE quote keeps full price =="
NONE_BODY="{\"productType\":\"COURSE\",\"productId\":$TRADE_COURSE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}"
STATUS=$(quote "$TOKEN" "$NONE_BODY" "$TMP_DIR/none.json")
expect_status 200 "$STATUS" "NONE quote" "$TMP_DIR/none.json"
expect_value "990" "$(field data.payableCent < "$TMP_DIR/none.json")" "NONE payable"
expect_value "0" "$(plan_size "$TMP_DIR/none.json")" "NONE has no coupon"

echo "== AUTO finds global optimum, max 3 coupons, category scope and same-activity exclusion =="
AUTO_BODY="{\"productType\":\"COURSE\",\"productId\":$TRADE_COURSE_ID,\"couponSelection\":{\"mode\":\"AUTO\",\"ids\":[]}}"
STATUS=$(quote "$TOKEN" "$AUTO_BODY" "$TMP_DIR/auto.json")
expect_status 200 "$STATUS" "AUTO quote" "$TMP_DIR/auto.json"
expect_value "READY" "$(field data.status < "$TMP_DIR/auto.json")" "AUTO quote ready"
expect_value "600" "$(field data.discountCent < "$TMP_DIR/auto.json")" "AUTO discount"
expect_value "390" "$(field data.payableCent < "$TMP_DIR/auto.json")" "AUTO payable"
expect_value "3" "$(plan_size "$TMP_DIR/auto.json")" "AUTO uses exactly 3 coupons"
AUTO_IDS=$(plan_ids "$TMP_DIR/auto.json")
case ",$AUTO_IDS," in
  *",$C5,"*",$C6,"*|*",$C6,"*",$C5,"*) echo "FAIL  same activity used twice: $AUTO_IDS"; exit 1;;
  *) echo "PASS  same activity at most one coupon  ($AUTO_IDS)";;
esac
case ",$AUTO_IDS," in
  *",$C7,"*) echo "FAIL  unrelated category coupon was applied: $AUTO_IDS"; exit 1;;
  *) echo "PASS  unrelated category coupon excluded";;
esac
expect_value "600" "$(allocation_sum "$TMP_DIR/auto.json")" "AUTO allocation sums to discount"
QUOTE_ID=$(field data.quoteId < "$TMP_DIR/auto.json")
QUOTE_HASH=$(field data.quoteHash < "$TMP_DIR/auto.json")

echo "== Cached quote returns the same coupon plan =="
STATUS=$(curl -s -o "$TMP_DIR/get.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
  "$BASE/api/v1/trade/quotes/$QUOTE_ID")
expect_status 200 "$STATUS" "read cached quote" "$TMP_DIR/get.json"
expect_value "$QUOTE_HASH" "$(field data.quoteHash < "$TMP_DIR/get.json")" "cached quote hash"
expect_value "600" "$(field data.discountCent < "$TMP_DIR/get.json")" "cached discount"
expect_value "$AUTO_IDS" "$(plan_ids "$TMP_DIR/get.json")" "cached coupon plan"

echo "== MANUAL enumerates permutations of only the selected coupons =="
MANUAL_BODY="{\"productType\":\"COURSE\",\"productId\":$TRADE_COURSE_ID,\"couponSelection\":{\"mode\":\"MANUAL\",\"ids\":[$C1,$C2]}}"
STATUS=$(quote "$TOKEN" "$MANUAL_BODY" "$TMP_DIR/manual.json")
expect_status 200 "$STATUS" "MANUAL quote" "$TMP_DIR/manual.json"
expect_value "299" "$(field data.discountCent < "$TMP_DIR/manual.json")" "MANUAL best order discount"
expect_value "691" "$(field data.payableCent < "$TMP_DIR/manual.json")" "MANUAL payable"
expect_value "$C2,$C1" "$(plan_ids "$TMP_DIR/manual.json")" "MANUAL order applies discount first"

echo "== Invalid coupon selections are rejected =="
STATUS=$(quote "$TOKEN" "{\"productType\":\"COURSE\",\"productId\":$TRADE_COURSE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[$C1]}}" "$TMP_DIR/bad-none.json")
expect_status 422 "$STATUS" "NONE with ids rejected" "$TMP_DIR/bad-none.json"
STATUS=$(quote "$TOKEN" "{\"productType\":\"COURSE\",\"productId\":$TRADE_COURSE_ID,\"couponSelection\":{\"mode\":\"MANUAL\",\"ids\":[$C1,$C2,$C3,$C4]}}" "$TMP_DIR/bad-four.json")
expect_status 422 "$STATUS" "more than 3 manual coupons rejected" "$TMP_DIR/bad-four.json"
STATUS=$(quote "$TOKEN" "{\"productType\":\"COURSE\",\"productId\":$TRADE_COURSE_ID,\"couponSelection\":{\"mode\":\"MANUAL\",\"ids\":[999999]}}" "$TMP_DIR/bad-id.json")
expect_status 422 "$STATUS" "unknown coupon rejected" "$TMP_DIR/bad-id.json"

echo "== A quote is private to its owner =="
login student01 STUDENT "$TMP_DIR/other.json"
OTHER=$(field data.accessToken < "$TMP_DIR/other.json")
STATUS=$(curl -s -o "$TMP_DIR/foreign.json" -w '%{http_code}' -H "Authorization: Bearer $OTHER" \
  "$BASE/api/v1/trade/quotes/$QUOTE_ID")
expect_status 404 "$STATUS" "another student cannot read quote" "$TMP_DIR/foreign.json"

echo "== ROUTE coupon applies to both courses and allocates exactly =="
login admin01 ADMIN "$TMP_DIR/admin2.json"
ADMIN_TOKEN=$(field data.accessToken < "$TMP_DIR/admin2.json")
STATUS=$(curl -s -o "$TMP_DIR/route.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S08B route $STAMP\",\"description\":\"S08B\",\"priceCent\":1200,\"members\":[{\"courseId\":13,\"sort\":1},{\"courseId\":11,\"sort\":2}]}" \
  "$BASE/api/v1/admin/routes")
expect_status 200 "$STATUS" "create route" "$TMP_DIR/route.json"
ROUTE_ID=$(field data.id < "$TMP_DIR/route.json")
ROUTE_REV=$(field data.revision < "$TMP_DIR/route.json")
STATUS=$(curl -s -o "$TMP_DIR/route-pub.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$ROUTE_REV}" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID/publish")
expect_status 200 "$STATUS" "publish route" "$TMP_DIR/route-pub.json"

register "$ROUTE_USER" "$TMP_DIR/route-register.json"
login "$ROUTE_USER" STUDENT "$TMP_DIR/route-login.json" "$TEST_PASSWORD"
ROUTE_TOKEN=$(field data.accessToken < "$TMP_DIR/route-login.json")
create_rule "S08B_R8_${STAMP}" NO_THRESHOLD ROUTE "$ROUTE_ID" 0 200 null null "$TMP_DIR/r8.json"
R8=$RULE_ID
create_activity "S08B G $STAMP" "$TMP_DIR/a8.json"
add_item 10 1 "$TMP_DIR/i8.json"
activate_activity "$TMP_DIR/a8a.json"
claim "$ROUTE_TOKEN" "$ITEM_ID" "s08b-a8-$STAMP" "$TMP_DIR/c8.json"
C8=$COUPON_ID

ROUTE_BODY="{\"productType\":\"ROUTE\",\"productId\":$ROUTE_ID,\"couponSelection\":{\"mode\":\"AUTO\",\"ids\":[]}}"
STATUS=$(quote "$ROUTE_TOKEN" "$ROUTE_BODY" "$TMP_DIR/route-quote.json")
expect_status 200 "$STATUS" "ROUTE AUTO quote" "$TMP_DIR/route-quote.json"
expect_value "200" "$(field data.discountCent < "$TMP_DIR/route-quote.json")" "ROUTE discount"
expect_value "1000" "$(field data.payableCent < "$TMP_DIR/route-quote.json")" "ROUTE payable"
expect_value "200" "$(allocation_sum "$TMP_DIR/route-quote.json")" "ROUTE allocation sum"
expect_value "1000" "$(course_remaining_sum "$TMP_DIR/route-quote.json")" "ROUTE line remaining sum"
ROUTE_QUOTE_ID=$(field data.quoteId < "$TMP_DIR/route-quote.json")
STATUS=$(curl -s -o "$TMP_DIR/route-get.json" -w '%{http_code}' -H "Authorization: Bearer $ROUTE_TOKEN" \
  "$BASE/api/v1/trade/quotes/$ROUTE_QUOTE_ID")
expect_status 200 "$STATUS" "read cached route quote" "$TMP_DIR/route-get.json"
expect_value "200" "$(field data.discountCent < "$TMP_DIR/route-get.json")" "cached route discount"
expect_value "$(plan_ids "$TMP_DIR/route-quote.json")" "$(plan_ids "$TMP_DIR/route-get.json")" "cached route plan"

echo "== Concurrent AUTO quotes remain independent and valid =="
mkdir -p "$TMP_DIR/concurrent"
for i in $(seq 1 10); do
  (
    curl -s -o "$TMP_DIR/concurrent/$i.json" -w '%{http_code}' \
      -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
      -d "$AUTO_BODY" "$BASE/api/v1/trade/quotes" > "$TMP_DIR/concurrent/$i.status"
  ) &
done
wait
CONCURRENT_STATUS=$(for f in "$TMP_DIR"/concurrent/*.status; do cat "$f"; echo; done | sort -u)
if [ "$CONCURRENT_STATUS" != "200" ]; then
  echo "FAIL  concurrent quote returned a non-200 status: $CONCURRENT_STATUS"; exit 1
fi
CONCURRENT_OK=$(node -e '
  const fs=require("fs"),dir=process.argv[1]; let ok=0;
  for (const f of fs.readdirSync(dir)) {
    if (!f.endsWith(".json")) continue;
    const d=JSON.parse(fs.readFileSync(dir+"/"+f,"utf8"));
    if (d.data && d.data.status==="READY" && d.data.discountCent===600) ok++;
  }
  console.log(ok);
' "$TMP_DIR/concurrent")
expect_value "10" "$CONCURRENT_OK" "10 concurrent AUTO quotes ready at 600 discount"

echo "== Persistence: quote plan and pricing job =="
if [ -z "$MYSQL_BIN" ]; then echo "FAIL  mysql client not found; persistence check cannot run"; exit 1; fi
QUOTE_ROW=$(MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -N -B -e \
  "SELECT status, discount_cent, payable_cent, JSON_LENGTH(coupon_plan_json), CHAR_LENGTH(result_hash) FROM bc_trade.price_quote WHERE quote_id='$QUOTE_ID'" | tr '\t' '|')
expect_value "READY|600|390|3|64" "$QUOTE_ROW" "price_quote persisted plan"
JOB_ROW=$(MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -N -B -e \
  "SELECT status, JSON_LENGTH(result_json) > 0 FROM bc_marketing.pricing_job WHERE quote_id='$QUOTE_ID'" | tr '\t' '|')
expect_value "READY|1" "$JOB_ROW" "pricing_job persisted result"
REQUEST_MODE=$(MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -N -B -e \
  "SELECT JSON_UNQUOTE(JSON_EXTRACT(request_json, '$.couponSelection.mode')) FROM bc_marketing.pricing_job WHERE quote_id='$QUOTE_ID'")
expect_value "AUTO" "$REQUEST_MODE" "pricing_job request mode"

echo "== Expired pricing lease recovers through the real trade polling path =="
MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -e \
  "UPDATE bc_trade.price_quote SET status='CALCULATING', discount_cent=0, payable_cent=total_cent-credit_cent, coupon_plan_json=NULL, result_hash=REPEAT('0',64) WHERE quote_id='$QUOTE_ID';
   UPDATE bc_marketing.pricing_job SET status='CALCULATING', result_json=NULL, result_hash=NULL, error_code=NULL, error_message=NULL, lease_token='dead-worker', lease_until=UTC_TIMESTAMP(3) - INTERVAL 1 MINUTE, attempts=1 WHERE quote_id='$QUOTE_ID';"
RECOVERED=0
for attempt in $(seq 1 50); do
  STATUS=$(curl -s -o "$TMP_DIR/recover.json" -w '%{http_code}' -H "Authorization: Bearer $TOKEN" \
    "$BASE/api/v1/trade/quotes/$QUOTE_ID")
  if [ "$STATUS" = "200" ] && [ "$(field data.status < "$TMP_DIR/recover.json")" = "READY" ]; then
    RECOVERED=1
    break
  fi
  sleep 0.1
done
expect_value "1" "$RECOVERED" "trade polling observed recovered READY"
expect_value "600" "$(field data.discountCent < "$TMP_DIR/recover.json")" "recovered discount"
expect_value "3" "$(plan_size "$TMP_DIR/recover.json")" "recovered coupon plan"
RECOVERY_ROW=$(MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -N -B -e \
  "SELECT status, attempts, lease_token IS NULL, CHAR_LENGTH(result_hash) FROM bc_marketing.pricing_job WHERE quote_id='$QUOTE_ID'" | tr '\t' '|')
expect_value "READY|2|1|64" "$RECOVERY_ROW" "lease reclaimed once and old token fenced"
QUOTE_RECOVERY_ROW=$(MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" -h127.0.0.1 -uroot -N -B -e \
  "SELECT status, discount_cent, payable_cent, JSON_LENGTH(coupon_plan_json) FROM bc_trade.price_quote WHERE quote_id='$QUOTE_ID'" | tr '\t' '|')
expect_value "READY|600|390|3" "$QUOTE_RECOVERY_ROW" "trade quote updated after recovery"

echo "ALL S08-B ACCEPTANCE CHECKS PASSED"
echo "autoQuote=$QUOTE_ID autoCoupons=$AUTO_IDS routeQuote=$ROUTE_QUOTE_ID category=$CATEGORY_ID"