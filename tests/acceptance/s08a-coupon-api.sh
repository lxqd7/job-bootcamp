#!/usr/bin/env bash
# S08-a acceptance: coupon rules, activity/item config, free claim idempotency and per-user limits.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
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
  else echo "FAIL  $label  expected HTTP $expected, got $actual"; cat "$body" 2>/dev/null || true; exit 1; fi
}

expect_value() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then echo "PASS  $label  ($actual)";
  else echo "FAIL  $label  expected '$expected', got '$actual'"; exit 1; fi
}

login() {
  local username="$1" app="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
}

NOW=$(node -e 'console.log(new Date(Date.now()-3600000).toISOString().slice(0,19))')
END=$(node -e 'console.log(new Date(Date.now()+86400000).toISOString().slice(0,19))')
RULE_KEY="S08A_FULL_${STAMP}"

echo "== Admin creates rule/activity/item =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$(field data.accessToken < "$TMP_DIR/admin.json")
STATUS=$(curl -s -o "$TMP_DIR/rule.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleKey\":\"$RULE_KEY\",\"type\":\"FULL_REDUCTION\",\"scope\":\"ALL\",\"thresholdCent\":1000,\"amountCent\":200,\"validityType\":\"DAYS\",\"validDays\":7}" \
  "$BASE/api/v1/admin/coupon-rules")
expect_status 200 "$STATUS" "create coupon rule" "$TMP_DIR/rule.json"
RULE_ID=$(field data.id < "$TMP_DIR/rule.json")
expect_value "false" "$(field data.issued < "$TMP_DIR/rule.json")" "new rule not issued"
STATUS=$(curl -s -o "$TMP_DIR/activity.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"S08A Activity $STAMP\",\"openAt\":\"$NOW\",\"closeAt\":\"$END\"}" \
  "$BASE/api/v1/admin/activities")
expect_status 200 "$STATUS" "create activity draft" "$TMP_DIR/activity.json"
ACTIVITY_ID=$(field data.id < "$TMP_DIR/activity.json")
STATUS=$(curl -s -o "$TMP_DIR/item.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleId\":$RULE_ID,\"stockTotal\":10,\"perUserLimit\":2}" \
  "$BASE/api/v1/admin/activities/$ACTIVITY_ID/items")
expect_status 200 "$STATUS" "add activity item" "$TMP_DIR/item.json"
ITEM_ID=$(field data.items.0.id < "$TMP_DIR/item.json")
REV=$(field data.revision < "$TMP_DIR/item.json")
STATUS=$(curl -s -o "$TMP_DIR/active.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$REV}" \
  "$BASE/api/v1/admin/activities/$ACTIVITY_ID/activate")
expect_status 200 "$STATUS" "activate activity" "$TMP_DIR/active.json"
expect_value "ACTIVE" "$(field data.state < "$TMP_DIR/active.json")" "activity active"

echo "== Student lists activity and claims idempotently =="
login student03 STUDENT "$TMP_DIR/student.json"
STUDENT_TOKEN=$(field data.accessToken < "$TMP_DIR/student.json")
STATUS=$(curl -s -o "$TMP_DIR/activities.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT_TOKEN" \
  "$BASE/api/v1/activities?page=1&pageSize=50")
expect_status 200 "$STATUS" "student lists activities" "$TMP_DIR/activities.json"
STATUS=$(curl -s -o "$TMP_DIR/claim1.json" -w '%{http_code}' -X POST -H 'Idempotency-Key: s08a-claim-1-'"$STAMP" \
  -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/activity-items/$ITEM_ID/claims")
expect_status 200 "$STATUS" "claim coupon" "$TMP_DIR/claim1.json"
COUPON_ID=$(field data.id < "$TMP_DIR/claim1.json")
expect_value "AVAILABLE" "$(field data.displayStatus < "$TMP_DIR/claim1.json")" "coupon available"
STATUS=$(curl -s -o "$TMP_DIR/claim2.json" -w '%{http_code}' -X POST -H 'Idempotency-Key: s08a-claim-1-'"$STAMP" \
  -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/activity-items/$ITEM_ID/claims")
expect_status 200 "$STATUS" "same key returns same coupon" "$TMP_DIR/claim2.json"
expect_value "$COUPON_ID" "$(field data.id < "$TMP_DIR/claim2.json")" "same coupon id"
STATUS=$(curl -s -o "$TMP_DIR/claim3.json" -w '%{http_code}' -X POST -H 'Idempotency-Key: s08a-claim-2-'"$STAMP" \
  -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/activity-items/$ITEM_ID/claims")
expect_status 200 "$STATUS" "second claim within limit" "$TMP_DIR/claim3.json"
STATUS=$(curl -s -o "$TMP_DIR/claim4.json" -w '%{http_code}' -X POST -H 'Idempotency-Key: s08a-claim-3-'"$STAMP" \
  -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/activity-items/$ITEM_ID/claims")
expect_status 409 "$STATUS" "third claim rejected by per-user limit" "$TMP_DIR/claim4.json"
expect_value "ACQUISITION_LIMIT" "$(field code < "$TMP_DIR/claim4.json")" "limit error code"

echo "== My coupons and immutable rule =="
STATUS=$(curl -s -o "$TMP_DIR/mine.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT_TOKEN" \
  "$BASE/api/v1/me/coupons?displayStatus=AVAILABLE&page=1&pageSize=20")
expect_status 200 "$STATUS" "student lists own coupons" "$TMP_DIR/mine.json"
STATUS=$(curl -s -o "$TMP_DIR/rule-update.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleKey\":\"$RULE_KEY\",\"type\":\"FULL_REDUCTION\",\"scope\":\"ALL\",\"thresholdCent\":1000,\"amountCent\":300,\"validityType\":\"DAYS\",\"validDays\":7}" \
  "$BASE/api/v1/admin/coupon-rules/$RULE_ID")
expect_status 409 "$STATUS" "issued rule cannot be edited" "$TMP_DIR/rule-update.json"

echo "== Stock exhaustion =="
RULE_KEY2="S08A_STOCK_${STAMP}"
STATUS=$(curl -s -o "$TMP_DIR/rule2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleKey\":\"$RULE_KEY2\",\"type\":\"NO_THRESHOLD\",\"scope\":\"ALL\",\"amountCent\":100,\"validityType\":\"DAYS\",\"validDays\":1}" \
  "$BASE/api/v1/admin/coupon-rules")
expect_status 200 "$STATUS" "create stock rule" "$TMP_DIR/rule2.json"
RULE2_ID=$(field data.id < "$TMP_DIR/rule2.json")
STATUS=$(curl -s -o "$TMP_DIR/activity2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"S08A Stock $STAMP\",\"openAt\":\"$NOW\",\"closeAt\":\"$END\"}" \
  "$BASE/api/v1/admin/activities")
expect_status 200 "$STATUS" "create stock activity" "$TMP_DIR/activity2.json"
ACTIVITY2_ID=$(field data.id < "$TMP_DIR/activity2.json")
STATUS=$(curl -s -o "$TMP_DIR/item2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleId\":$RULE2_ID,\"stockTotal\":1,\"perUserLimit\":1}" \
  "$BASE/api/v1/admin/activities/$ACTIVITY2_ID/items")
expect_status 200 "$STATUS" "add stock item" "$TMP_DIR/item2.json"
ITEM2_ID=$(field data.items.0.id < "$TMP_DIR/item2.json")
STATUS=$(curl -s -o "$TMP_DIR/active2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":0}' \
  "$BASE/api/v1/admin/activities/$ACTIVITY2_ID/activate")
expect_status 200 "$STATUS" "activate stock activity" "$TMP_DIR/active2.json"

login student01 STUDENT "$TMP_DIR/student1.json"
S1=$(field data.accessToken < "$TMP_DIR/student1.json")
STATUS=$(curl -s -o "$TMP_DIR/stock1.json" -w '%{http_code}' -X POST -H 'Idempotency-Key: s08a-stock-1-'"$STAMP" \
  -H "Authorization: Bearer $S1" "$BASE/api/v1/activity-items/$ITEM2_ID/claims")
expect_status 200 "$STATUS" "first student gets last stock" "$TMP_DIR/stock1.json"
login student02 STUDENT "$TMP_DIR/student2.json"
S2=$(field data.accessToken < "$TMP_DIR/student2.json")
STATUS=$(curl -s -o "$TMP_DIR/stock2.json" -w '%{http_code}' -X POST -H 'Idempotency-Key: s08a-stock-2-'"$STAMP" \
  -H "Authorization: Bearer $S2" "$BASE/api/v1/activity-items/$ITEM2_ID/claims")
expect_status 409 "$STATUS" "stock exhaustion rejected" "$TMP_DIR/stock2.json"
expect_value "STOCK_EXHAUSTED" "$(field code < "$TMP_DIR/stock2.json")" "stock error code"

echo "ALL S08-A ACCEPTANCE CHECKS PASSED"
echo "rule=$RULE_ID activity=$ACTIVITY_ID item=$ITEM_ID coupon=$COUPON_ID"