#!/usr/bin/env bash
# S15-b acceptance: atomic points deduction + coupon issue, stock/limit concurrency, idempotency and source isolation.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DB_PASSWORD="${DB_PASSWORD:-1234}"
MYSQL_BIN="${MYSQL_BIN:-$(command -v mysql || true)}"
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
  else echo "FAIL  $label  expected HTTP $expected, got $actual"; cat "$body" 2>/dev/null || true; echo; exit 1; fi
}

expect_value() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then echo "PASS  $label  ($actual)";
  else echo "FAIL  $label  expected '$expected', got '$actual'"; exit 1; fi
}

db() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" --default-character-set=utf8mb4 -h127.0.0.1 -uroot -N -B -e "$1"
}

login() {
  local username="$1" app="$2" out="$3" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
  TOKEN=$(field data.accessToken < "$out")
  USER_ID=$(field data.user.id < "$out")
}

register() {
  local username="$1" out="$2" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$DEMO_PASSWORD\",\"nickname\":\"$username\"}" \
    "$BASE/api/v1/auth/register")
  expect_status 200 "$status" "register $username" "$out"
}

iso() {
  OFFSET_MS="$1" node -e 'console.log(new Date(Date.now()+Number(process.env.OFFSET_MS)).toISOString().slice(0,19))'
}

create_project() {
  local name="$1" price="$2" stock="$3" limit="$4" out="$5" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"name\":\"$name\",\"ruleId\":$RULE_ID,\"pointsPrice\":$price,\"stockTotal\":$stock,\"perUserLimit\":$limit,\"openAt\":\"$(iso -3600000)\",\"closeAt\":\"$(iso 604800000)\"}" \
    "$BASE/api/v1/admin/exchange-projects")
  expect_status 200 "$status" "create project $name" "$out"
  PROJECT_ID=$(field data.id < "$out")
  PROJECT_REV=$(field data.revision < "$out")
}

online_project() {
  local id="$1" rev="$2" out="$3" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$rev}" \
    "$BASE/api/v1/admin/exchange-projects/$id/online")
  expect_status 200 "$status" "online project $id" "$out"
  ONLINE_REV=$(field data.revision < "$out")
}

exchange() {
  local token="$1" project="$2" rev="$3" key="$4" label="$5" out="$6" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $key" -H "Authorization: Bearer $token" \
    -d "{\"expectedRevision\":$rev}" \
    "$BASE/api/v1/exchange-projects/$project/exchanges")
  expect_status 200 "$status" "$label" "$out"
}

points_available() {
  db "SELECT COALESCE(MAX(available),0) FROM bc_marketing.points_account WHERE user_id=$1"
}

echo "== Setup admin, student points and one coupon rule =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
STATUS=$(curl -s -o "$TMP_DIR/rule.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleKey\":\"S15B_RULE_$STAMP\",\"type\":\"NO_THRESHOLD\",\"scope\":\"ALL\",\"amountCent\":500,\"validityType\":\"DAYS\",\"validDays\":7}" \
  "$BASE/api/v1/admin/coupon-rules")
expect_status 200 "$STATUS" "create exchange rule" "$TMP_DIR/rule.json"
RULE_ID=$(field data.id < "$TMP_DIR/rule.json")

register "s15b_s1_$STAMP" "$TMP_DIR/reg1.json"
login "s15b_s1_$STAMP" STUDENT "$TMP_DIR/s1.json"
S1_TOKEN=$TOKEN
S1_ID=$USER_ID
register "s15b_s2_$STAMP" "$TMP_DIR/reg2.json"
login "s15b_s2_$STAMP" STUDENT "$TMP_DIR/s2.json"
S2_TOKEN=$TOKEN
S2_ID=$USER_ID
db "INSERT INTO bc_marketing.points_account(user_id,available,debt,revision,created_at,updated_at) VALUES($S1_ID,500,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE available=500,debt=0"
db "INSERT INTO bc_marketing.points_account(user_id,available,debt,revision,created_at,updated_at) VALUES($S2_ID,100,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE available=100,debt=0"
login teacher01 ADMIN "$TMP_DIR/t1.json"
T1_TOKEN=$TOKEN

echo "== Permission and project validation =="
create_project "S15B Main $STAMP" 40 10 2 "$TMP_DIR/p1.json"
P1=$PROJECT_ID
online_project "$P1" 0 "$TMP_DIR/p1-online.json"
P1_REV=$ONLINE_REV
STATUS=$(curl -s -o "$TMP_DIR/teacher-exchange.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: teacher-$STAMP" -H "Authorization: Bearer $T1_TOKEN" -d "{\"expectedRevision\":$P1_REV}" \
  "$BASE/api/v1/exchange-projects/$P1/exchanges")
expect_status 403 "$STATUS" "teacher cannot exchange" "$TMP_DIR/teacher-exchange.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-exchange.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: admin-$STAMP" -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"expectedRevision\":$P1_REV}" \
  "$BASE/api/v1/exchange-projects/$P1/exchanges")
expect_status 403 "$STATUS" "admin cannot exchange" "$TMP_DIR/admin-exchange.json"

echo "== Atomic deduction and coupon issue =="
exchange "$S1_TOKEN" "$P1" "$P1_REV" "main-$STAMP" "first exchange" "$TMP_DIR/e1.json"
EX1=$(field data.exchangeId < "$TMP_DIR/e1.json")
COUPON1=$(field data.userCouponId < "$TMP_DIR/e1.json")
expect_value "40|460|$P1_REV" "$(field data.pointsSpent < "$TMP_DIR/e1.json")|$(field data.balance < "$TMP_DIR/e1.json")|$(field data.projectRevision < "$TMP_DIR/e1.json")" "exchange result and balance"
expect_value "460" "$(points_available "$S1_ID")" "points deducted once"
expect_value "$EX1|40|$P1_REV|1" "$(db "SELECT CONCAT_WS('|',id,points_spent,project_revision,ledger_id>0) FROM bc_marketing.exchange_record WHERE user_id=$S1_ID AND request_key='main-$STAMP'")" "exchange record snapshot"
expect_value "$S1_ID|-40|-40|0" "$(db "SELECT CONCAT_WS('|',user_id,reward_points,balance_delta,rank_delta) FROM bc_marketing.points_ledger WHERE type='EXCHANGE' AND business_key='$S1_ID:main-$STAMP'")" "exchange ledger"
expect_value "$P1|EXCHANGE|$RULE_ID|AVAILABLE" "$(db "SELECT CONCAT_WS('|',source_group_id,source_type,rule_id,status) FROM bc_marketing.user_coupon WHERE id=$COUPON1")" "coupon source and status"
expect_value "1|7" "$(db "SELECT CONCAT_WS('|',(SELECT total_count FROM bc_marketing.acquisition_counter WHERE user_id=$S1_ID AND source_type='EXCHANGE' AND source_item_id=$P1),ROUND(TIMESTAMPDIFF(HOUR,valid_from,valid_until)/24)) FROM bc_marketing.user_coupon WHERE id=$COUPON1")" "per-user count and validity snapshot"
expect_value "1|1" "$(db "SELECT CONCAT_WS('|',stock_used,revision) FROM bc_marketing.exchange_project WHERE id=$P1")" "stock used without changing config revision"
expect_value "1" "$(db "SELECT issued FROM bc_marketing.coupon_rule WHERE id=$RULE_ID")" "rule marked issued after first exchange"
STATUS=$(curl -s -o "$TMP_DIR/rule-edit.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleKey\":\"S15B_RULE_$STAMP\",\"type\":\"NO_THRESHOLD\",\"scope\":\"ALL\",\"amountCent\":600,\"validityType\":\"DAYS\",\"validDays\":7}" \
  "$BASE/api/v1/admin/coupon-rules/$RULE_ID")
expect_status 409 "$STATUS" "issued exchange rule cannot be edited" "$TMP_DIR/rule-edit.json"

echo "== Idempotent replay, key isolation and per-user limit =="
exchange "$S1_TOKEN" "$P1" "$P1_REV" "main-$STAMP" "replay same key" "$TMP_DIR/e1-replay.json"
expect_value "$EX1|$COUPON1|460" "$(field data.exchangeId < "$TMP_DIR/e1-replay.json")|$(field data.userCouponId < "$TMP_DIR/e1-replay.json")|$(field data.balance < "$TMP_DIR/e1-replay.json")" "replay returns same exchange"
expect_value "460|1|1|1" "$(points_available "$S1_ID")|$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCHANGE' AND business_key='$S1_ID:main-$STAMP'")|$(db "SELECT stock_used FROM bc_marketing.exchange_project WHERE id=$P1")|$(db "SELECT total_count FROM bc_marketing.acquisition_counter WHERE user_id=$S1_ID AND source_type='EXCHANGE' AND source_item_id=$P1")" "replay does not duplicate effects"
create_project "S15B Other $STAMP" 10 10 5 "$TMP_DIR/pother.json"
POTHER=$PROJECT_ID
online_project "$POTHER" 0 "$TMP_DIR/pother-online.json"
POTHER_REV=$ONLINE_REV
STATUS=$(curl -s -o "$TMP_DIR/key-other.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: main-$STAMP" -H "Authorization: Bearer $S1_TOKEN" -d "{\"expectedRevision\":$POTHER_REV}" \
  "$BASE/api/v1/exchange-projects/$POTHER/exchanges")
expect_status 409 "$STATUS" "same key cannot be reused on another project" "$TMP_DIR/key-other.json"
expect_value "460" "$(points_available "$S1_ID")" "key isolation does not deduct"
exchange "$S1_TOKEN" "$P1" "$P1_REV" "main2-$STAMP" "second valid exchange" "$TMP_DIR/e2.json"
expect_value "420|2|2" "$(points_available "$S1_ID")|$(db "SELECT stock_used FROM bc_marketing.exchange_project WHERE id=$P1")|$(db "SELECT total_count FROM bc_marketing.acquisition_counter WHERE user_id=$S1_ID AND source_type='EXCHANGE' AND source_item_id=$P1")" "second exchange updates counters"
STATUS=$(curl -s -o "$TMP_DIR/e3.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: main3-$STAMP" -H "Authorization: Bearer $S1_TOKEN" -d "{\"expectedRevision\":$P1_REV}" \
  "$BASE/api/v1/exchange-projects/$P1/exchanges")
expect_status 409 "$STATUS" "per-user limit stops third exchange" "$TMP_DIR/e3.json"
expect_value "420|2|2" "$(points_available "$S1_ID")|$(db "SELECT stock_used FROM bc_marketing.exchange_project WHERE id=$P1")|$(db "SELECT total_count FROM bc_marketing.acquisition_counter WHERE user_id=$S1_ID AND source_type='EXCHANGE' AND source_item_id=$P1")" "limit failure rolls back points and counters"
expect_value "2" "$(db "SELECT COUNT(*) FROM bc_marketing.user_coupon WHERE user_id=$S1_ID AND source_type='EXCHANGE' AND source_group_id=$P1")" "coupon count equals successful exchanges"

echo "== Revision, stock, insufficient points and unavailable project =="
STATUS=$(curl -s -o "$TMP_DIR/stale-exchange.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: stale-$STAMP" -H "Authorization: Bearer $S1_TOKEN" -d '{"expectedRevision":0}' \
  "$BASE/api/v1/exchange-projects/$P1/exchanges")
expect_status 409 "$STATUS" "stale expected revision rejected" "$TMP_DIR/stale-exchange.json"
expect_value "420" "$(points_available "$S1_ID")" "stale revision does not deduct"
create_project "S15B Stock $STAMP" 30 1 5 "$TMP_DIR/pstock.json"
PSTOCK=$PROJECT_ID
online_project "$PSTOCK" 0 "$TMP_DIR/pstock-online.json"
PSTOCK_REV=$ONLINE_REV
exchange "$S1_TOKEN" "$PSTOCK" "$PSTOCK_REV" "stock1-$STAMP" "stock exchange" "$TMP_DIR/stock1.json"
STATUS=$(curl -s -o "$TMP_DIR/stock2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: stock2-$STAMP" -H "Authorization: Bearer $S1_TOKEN" -d "{\"expectedRevision\":$PSTOCK_REV}" \
  "$BASE/api/v1/exchange-projects/$PSTOCK/exchanges")
expect_status 409 "$STATUS" "stock exhaustion rejected" "$TMP_DIR/stock2.json"
expect_value "390|1|1" "$(points_available "$S1_ID")|$(db "SELECT stock_used FROM bc_marketing.exchange_project WHERE id=$PSTOCK")|$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCHANGE' AND business_key LIKE '$S1_ID:stock%'")" "stock failure does not deduct"
create_project "S15B Insufficient $STAMP" 1000 5 5 "$TMP_DIR/poor.json"
PPOOR=$PROJECT_ID
online_project "$PPOOR" 0 "$TMP_DIR/poor-online.json"
PPOOR_REV=$ONLINE_REV
STATUS=$(curl -s -o "$TMP_DIR/poor-exchange.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: poor-$STAMP" -H "Authorization: Bearer $S1_TOKEN" -d "{\"expectedRevision\":$PPOOR_REV}" \
  "$BASE/api/v1/exchange-projects/$PPOOR/exchanges")
expect_status 422 "$STATUS" "insufficient points rejected" "$TMP_DIR/poor-exchange.json"
expect_value "390|0|0" "$(points_available "$S1_ID")|$(db "SELECT stock_used FROM bc_marketing.exchange_project WHERE id=$PPOOR")|$(db "SELECT COUNT(*) FROM bc_marketing.exchange_record WHERE user_id=$S1_ID AND project_id=$PPOOR")" "insufficient points leaves no side effects"
create_project "S15B Offline $STAMP" 10 5 5 "$TMP_DIR/poffline.json"
POFFLINE=$PROJECT_ID
STATUS=$(curl -s -o "$TMP_DIR/offline-exchange.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: offline-$STAMP" -H "Authorization: Bearer $S1_TOKEN" -d '{"expectedRevision":0}' \
  "$BASE/api/v1/exchange-projects/$POFFLINE/exchanges")
expect_status 409 "$STATUS" "offline project rejected" "$TMP_DIR/offline-exchange.json"
create_project "S15B Ended $STAMP" 10 5 5 "$TMP_DIR/pended.json"
PENDED=$PROJECT_ID
online_project "$PENDED" 0 "$TMP_DIR/pended-online.json"
PENDED_REV=$ONLINE_REV
db "UPDATE bc_marketing.exchange_project SET close_at=UTC_TIMESTAMP(3) - INTERVAL 1 SECOND WHERE id=$PENDED"
STATUS=$(curl -s -o "$TMP_DIR/ended-exchange.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ended-$STAMP" -H "Authorization: Bearer $S1_TOKEN" -d "{\"expectedRevision\":$PENDED_REV}" \
  "$BASE/api/v1/exchange-projects/$PENDED/exchanges")
expect_status 409 "$STATUS" "ended project rejected" "$TMP_DIR/ended-exchange.json"
expect_value "390" "$(points_available "$S1_ID")" "unavailable project does not deduct"

echo "== Activity and exchange coupons keep separate source types =="
STATUS=$(curl -s -o "$TMP_DIR/act-rule.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleKey\":\"S15B_ACT_$STAMP\",\"type\":\"NO_THRESHOLD\",\"scope\":\"ALL\",\"amountCent\":100,\"validityType\":\"DAYS\",\"validDays\":3}" \
  "$BASE/api/v1/admin/coupon-rules")
expect_status 200 "$STATUS" "create activity rule" "$TMP_DIR/act-rule.json"
ACT_RULE=$(field data.id < "$TMP_DIR/act-rule.json")
STATUS=$(curl -s -o "$TMP_DIR/activity.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"S15B Activity $STAMP\",\"openAt\":\"$(iso -3600000)\",\"closeAt\":\"$(iso 86400000)\"}" \
  "$BASE/api/v1/admin/activities")
expect_status 200 "$STATUS" "create activity" "$TMP_DIR/activity.json"
ACT_ID=$(field data.id < "$TMP_DIR/activity.json")
STATUS=$(curl -s -o "$TMP_DIR/act-item.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleId\":$ACT_RULE,\"stockTotal\":5,\"perUserLimit\":2}" \
  "$BASE/api/v1/admin/activities/$ACT_ID/items")
expect_status 200 "$STATUS" "add activity item" "$TMP_DIR/act-item.json"
ACT_ITEM=$(field data.items.0.id < "$TMP_DIR/act-item.json")
STATUS=$(curl -s -o "$TMP_DIR/act-active.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":0}' \
  "$BASE/api/v1/admin/activities/$ACT_ID/activate")
expect_status 200 "$STATUS" "activate activity" "$TMP_DIR/act-active.json"
STATUS=$(curl -s -o "$TMP_DIR/act-claim.json" -w '%{http_code}' -X POST \
  -H "Idempotency-Key: act-$STAMP" -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/activity-items/$ACT_ITEM/claims")
expect_status 200 "$STATUS" "student claims activity coupon" "$TMP_DIR/act-claim.json"
ACT_COUPON=$(field data.id < "$TMP_DIR/act-claim.json")
expect_value "$ACT_ID|$ACT_ITEM|ACTIVITY" "$(db "SELECT CONCAT_WS('|',source_group_id,source_item_id,source_type) FROM bc_marketing.user_coupon WHERE id=$ACT_COUPON")" "activity coupon source"
expect_value "ACTIVITY,EXCHANGE" "$(db "SELECT GROUP_CONCAT(source_type ORDER BY source_type) FROM bc_marketing.acquisition_counter WHERE user_id=$S1_ID AND ((source_type='ACTIVITY' AND source_item_id=$ACT_ITEM) OR (source_type='EXCHANGE' AND source_item_id=$P1))")" "counters isolated by source type"

echo "== Concurrent per-user limit and stock =="
create_project "S15B LimitRace $STAMP" 20 5 1 "$TMP_DIR/plimit.json"
PLIMIT=$PROJECT_ID
online_project "$PLIMIT" 0 "$TMP_DIR/plimit-online.json"
PLIMIT_REV=$ONLINE_REV
for key in limitA-$STAMP limitB-$STAMP; do
  ( curl -s -o "$TMP_DIR/$key.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
      -H "Idempotency-Key: $key" -H "Authorization: Bearer $S1_TOKEN" \
      -d "{\"expectedRevision\":$PLIMIT_REV}" \
      "$BASE/api/v1/exchange-projects/$PLIMIT/exchanges" > "$TMP_DIR/$key.status" ) &
done
wait
STATUSES=$(printf '%s\n' "$(cat "$TMP_DIR"/limitA-$STAMP.status)" "$(cat "$TMP_DIR"/limitB-$STAMP.status)" | sort | tr '\n' ' ')
expect_value "200 409 " "$STATUSES" "concurrent per-user limit allows one"
expect_value "370|1|1|1" "$(points_available "$S1_ID")|$(db "SELECT stock_used FROM bc_marketing.exchange_project WHERE id=$PLIMIT")|$(db "SELECT total_count FROM bc_marketing.acquisition_counter WHERE user_id=$S1_ID AND source_type='EXCHANGE' AND source_item_id=$PLIMIT")|$(db "SELECT COUNT(*) FROM bc_marketing.exchange_record WHERE user_id=$S1_ID AND project_id=$PLIMIT")" "limit race deducts once"

create_project "S15B StockRace $STAMP" 20 1 5 "$TMP_DIR/prace.json"
PRACE=$PROJECT_ID
online_project "$PRACE" 0 "$TMP_DIR/prace-online.json"
PRACE_REV=$ONLINE_REV
for pair in "a:$S1_TOKEN" "b:$S2_TOKEN"; do
  label=${pair%%:*}
  token=${pair#*:}
  ( curl -s -o "$TMP_DIR/race-$label.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
      -H "Idempotency-Key: race-$label-$STAMP" -H "Authorization: Bearer $token" \
      -d "{\"expectedRevision\":$PRACE_REV}" \
      "$BASE/api/v1/exchange-projects/$PRACE/exchanges" > "$TMP_DIR/race-$label.status" ) &
done
wait
STATUSES=$(printf '%s\n' "$(cat "$TMP_DIR"/race-a.status)" "$(cat "$TMP_DIR"/race-b.status)" | sort | tr '\n' ' ')
expect_value "200 409 " "$STATUSES" "concurrent stock allows one"
expect_value "1|1|1" "$(db "SELECT stock_used FROM bc_marketing.exchange_project WHERE id=$PRACE")|$(db "SELECT COUNT(*) FROM bc_marketing.exchange_record WHERE project_id=$PRACE")|$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCHANGE' AND business_key IN ('$S1_ID:race-a-$STAMP','$S2_ID:race-b-$STAMP')")" "stock race issues one coupon"
TOTAL=$(db "SELECT (SELECT available FROM bc_marketing.points_account WHERE user_id=$S1_ID)+(SELECT available FROM bc_marketing.points_account WHERE user_id=$S2_ID)")
expect_value "450" "$TOTAL" "stock race deducts only winner"

echo "== Concurrent same request key is idempotent =="
create_project "S15B KeyRace $STAMP" 10 2 2 "$TMP_DIR/pkey.json"
PKEY=$PROJECT_ID
online_project "$PKEY" 0 "$TMP_DIR/pkey-online.json"
PKEY_REV=$ONLINE_REV
BAL_BEFORE=$(points_available "$S1_ID")
for n in 1 2; do
  ( curl -s -o "$TMP_DIR/keyrace-$n.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
      -H "Idempotency-Key: same-key-$STAMP" -H "Authorization: Bearer $S1_TOKEN" \
      -d "{\"expectedRevision\":$PKEY_REV}" \
      "$BASE/api/v1/exchange-projects/$PKEY/exchanges" > "$TMP_DIR/keyrace-$n.status" ) &
done
wait
expect_value "200 200 " "$(printf '%s\n' "$(cat "$TMP_DIR"/keyrace-1.status)" "$(cat "$TMP_DIR"/keyrace-2.status)" | sort | tr '\n' ' ')" "same key both return success"
ID1=$(field data.exchangeId < "$TMP_DIR/keyrace-1.json")
ID2=$(field data.exchangeId < "$TMP_DIR/keyrace-2.json")
expect_value "$ID1|$ID1" "$ID1|$ID2" "same key returns same exchange"
expect_value "$((BAL_BEFORE - 10))|1|1|1" "$(points_available "$S1_ID")|$(db "SELECT stock_used FROM bc_marketing.exchange_project WHERE id=$PKEY")|$(db "SELECT COUNT(*) FROM bc_marketing.exchange_record WHERE user_id=$S1_ID AND project_id=$PKEY")|$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCHANGE' AND business_key='$S1_ID:same-key-$STAMP'")" "same key deducts once"

echo "ALL S15-B EXCHANGE ACCEPTANCE CHECKS PASSED"