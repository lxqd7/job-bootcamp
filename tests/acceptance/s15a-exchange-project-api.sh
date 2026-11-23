#!/usr/bin/env bash
# S15-a acceptance: exchange project management, optimistic revision, stock/limit bounds and online/offline.
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
}

iso() {
  OFFSET_MS="$1" node -e 'console.log(new Date(Date.now()+Number(process.env.OFFSET_MS)).toISOString().slice(0,19))'
}

create_rule() {
  local key="$1" out="$2" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"ruleKey\":\"$key\",\"type\":\"NO_THRESHOLD\",\"scope\":\"ALL\",\"amountCent\":500,\"validityType\":\"DAYS\",\"validDays\":7}" \
    "$BASE/api/v1/admin/coupon-rules")
  expect_status 200 "$status" "create coupon rule $key" "$out"
  RULE_ID=$(field data.id < "$out")
}

create_project() {
  local name="$1" rule="$2" open_at="$3" close_at="$4" out="$5" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"name\":\"$name\",\"ruleId\":$rule,\"pointsPrice\":100,\"stockTotal\":10,\"perUserLimit\":2,\"openAt\":\"$open_at\",\"closeAt\":\"$close_at\"}" \
    "$BASE/api/v1/admin/exchange-projects")
  expect_status 200 "$status" "create exchange project" "$out"
  PROJECT_ID=$(field data.id < "$out")
}

echo "== Setup admin, rules and permissions =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
create_rule "S15A_R1_$STAMP" "$TMP_DIR/rule1.json"
RULE1=$RULE_ID
create_rule "S15A_R2_$STAMP" "$TMP_DIR/rule2.json"
RULE2=$RULE_ID
NOW=$(iso -3600000)
END=$(iso 86400000)
PAST_END=$(iso -60000)

login teacher01 ADMIN "$TMP_DIR/t1.json"
T1_TOKEN=$TOKEN
login student01 STUDENT "$TMP_DIR/s1.json"
S1_TOKEN=$TOKEN
STATUS=$(curl -s -o "$TMP_DIR/student-list.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/admin/exchange-projects")
expect_status 403 "$STATUS" "student cannot manage exchange projects" "$TMP_DIR/student-list.json"
STATUS=$(curl -s -o "$TMP_DIR/teacher-list.json" -w '%{http_code}' -H "Authorization: Bearer $T1_TOKEN" \
  "$BASE/api/v1/admin/exchange-projects")
expect_status 403 "$STATUS" "teacher cannot manage exchange projects" "$TMP_DIR/teacher-list.json"
STATUS=$(curl -s -o "$TMP_DIR/anon-list.json" -w '%{http_code}' "$BASE/api/v1/admin/exchange-projects")
expect_status 401 "$STATUS" "unauthenticated list rejected" "$TMP_DIR/anon-list.json"

echo "== Create and read project =="
create_project "S15A Project $STAMP" "$RULE1" "$NOW" "$END" "$TMP_DIR/project.json"
expect_value "OFFLINE|0|0|2|100" "$(field data.state < "$TMP_DIR/project.json")|$(field data.revision < "$TMP_DIR/project.json")|$(field data.stockUsed < "$TMP_DIR/project.json")|$(field data.perUserLimit < "$TMP_DIR/project.json")|$(field data.pointsPrice < "$TMP_DIR/project.json")" "new project defaults"
expect_value "S15A_R1_$STAMP|1|NO_THRESHOLD|ALL" "$(field data.rule.ruleKey < "$TMP_DIR/project.json")|$(field data.rule.versionNo < "$TMP_DIR/project.json")|$(field data.rule.type < "$TMP_DIR/project.json")|$(field data.rule.scope < "$TMP_DIR/project.json")" "project rule snapshot"
STATUS=$(curl -s -o "$TMP_DIR/get.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/exchange-projects/$PROJECT_ID")
expect_status 200 "$STATUS" "get project" "$TMP_DIR/get.json"
expect_value "$PROJECT_ID" "$(field data.id < "$TMP_DIR/get.json")" "get returns project"
STATUS=$(curl -s -o "$TMP_DIR/list.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/exchange-projects?state=OFFLINE&page=1&pageSize=50")
expect_status 200 "$STATUS" "list offline projects" "$TMP_DIR/list.json"
expect_value "1" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.items.filter(x=>x.id===Number(process.argv[2])).length)' "$TMP_DIR/list.json" "$PROJECT_ID")" "offline filter includes project"
STATUS=$(curl -s -o "$TMP_DIR/bad-state.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/exchange-projects?state=BAD")
expect_status 400 "$STATUS" "invalid state filter rejected" "$TMP_DIR/bad-state.json"

echo "== Create validation =="
STATUS=$(curl -s -o "$TMP_DIR/missing-rule.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"No rule\",\"ruleId\":99999999,\"pointsPrice\":10,\"stockTotal\":1,\"perUserLimit\":1,\"openAt\":\"$NOW\",\"closeAt\":\"$END\"}" \
  "$BASE/api/v1/admin/exchange-projects")
expect_status 404 "$STATUS" "missing rule rejected" "$TMP_DIR/missing-rule.json"
STATUS=$(curl -s -o "$TMP_DIR/bad-price.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"Bad price\",\"ruleId\":$RULE1,\"pointsPrice\":0,\"stockTotal\":1,\"perUserLimit\":1,\"openAt\":\"$NOW\",\"closeAt\":\"$END\"}" \
  "$BASE/api/v1/admin/exchange-projects")
expect_status 400 "$STATUS" "non-positive price rejected" "$TMP_DIR/bad-price.json"
STATUS=$(curl -s -o "$TMP_DIR/bad-limit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"Bad limit\",\"ruleId\":$RULE1,\"pointsPrice\":10,\"stockTotal\":1,\"perUserLimit\":0,\"openAt\":\"$NOW\",\"closeAt\":\"$END\"}" \
  "$BASE/api/v1/admin/exchange-projects")
expect_status 400 "$STATUS" "non-positive limit rejected" "$TMP_DIR/bad-limit.json"
STATUS=$(curl -s -o "$TMP_DIR/bad-window.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"Bad window\",\"ruleId\":$RULE1,\"pointsPrice\":10,\"stockTotal\":1,\"perUserLimit\":1,\"openAt\":\"$END\",\"closeAt\":\"$NOW\"}" \
  "$BASE/api/v1/admin/exchange-projects")
expect_status 400 "$STATUS" "inverted time window rejected" "$TMP_DIR/bad-window.json"

echo "== Revision, stock and limit changes =="
STATUS=$(curl -s -o "$TMP_DIR/update.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"S15A Project updated $STAMP\",\"ruleId\":$RULE1,\"pointsPrice\":150,\"stockTotal\":8,\"perUserLimit\":3,\"openAt\":\"$NOW\",\"closeAt\":\"$END\",\"revision\":0}" \
  "$BASE/api/v1/admin/exchange-projects/$PROJECT_ID")
expect_status 200 "$STATUS" "update project config" "$TMP_DIR/update.json"
expect_value "150|8|3|1" "$(field data.pointsPrice < "$TMP_DIR/update.json")|$(field data.stockTotal < "$TMP_DIR/update.json")|$(field data.perUserLimit < "$TMP_DIR/update.json")|$(field data.revision < "$TMP_DIR/update.json")" "config updated and revision incremented"
STATUS=$(curl -s -o "$TMP_DIR/stale.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"Stale\",\"ruleId\":$RULE1,\"pointsPrice\":150,\"stockTotal\":8,\"perUserLimit\":3,\"openAt\":\"$NOW\",\"closeAt\":\"$END\",\"revision\":0}" \
  "$BASE/api/v1/admin/exchange-projects/$PROJECT_ID")
expect_status 409 "$STATUS" "stale revision rejected" "$TMP_DIR/stale.json"
db "UPDATE bc_marketing.exchange_project SET stock_used=5 WHERE id=$PROJECT_ID"
STATUS=$(curl -s -o "$TMP_DIR/below-used.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"Too low stock\",\"ruleId\":$RULE1,\"pointsPrice\":150,\"stockTotal\":3,\"perUserLimit\":3,\"openAt\":\"$NOW\",\"closeAt\":\"$END\",\"revision\":1}" \
  "$BASE/api/v1/admin/exchange-projects/$PROJECT_ID")
expect_status 400 "$STATUS" "stock below used rejected" "$TMP_DIR/below-used.json"
STATUS=$(curl -s -o "$TMP_DIR/stock-ok.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"S15A Project updated $STAMP\",\"ruleId\":$RULE1,\"pointsPrice\":150,\"stockTotal\":6,\"perUserLimit\":3,\"openAt\":\"$NOW\",\"closeAt\":\"$END\",\"revision\":1}" \
  "$BASE/api/v1/admin/exchange-projects/$PROJECT_ID")
expect_status 200 "$STATUS" "stock equal to used accepted" "$TMP_DIR/stock-ok.json"
expect_value "6|5|2" "$(field data.stockTotal < "$TMP_DIR/stock-ok.json")|$(field data.stockUsed < "$TMP_DIR/stock-ok.json")|$(field data.revision < "$TMP_DIR/stock-ok.json")" "stock and revision after update"

echo "== Online/offline preserves counters =="
STATUS=$(curl -s -o "$TMP_DIR/online.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":2}' \
  "$BASE/api/v1/admin/exchange-projects/$PROJECT_ID/online")
expect_status 200 "$STATUS" "project online" "$TMP_DIR/online.json"
expect_value "ONLINE|3" "$(field data.state < "$TMP_DIR/online.json")|$(field data.revision < "$TMP_DIR/online.json")" "online revision"
STATUS=$(curl -s -o "$TMP_DIR/online-again.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":3}' \
  "$BASE/api/v1/admin/exchange-projects/$PROJECT_ID/online")
expect_status 200 "$STATUS" "online idempotent" "$TMP_DIR/online-again.json"
expect_value "ONLINE|3" "$(field data.state < "$TMP_DIR/online-again.json")|$(field data.revision < "$TMP_DIR/online-again.json")" "online idempotent revision"
STATUS=$(curl -s -o "$TMP_DIR/offline.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":3}' \
  "$BASE/api/v1/admin/exchange-projects/$PROJECT_ID/offline")
expect_status 200 "$STATUS" "project offline" "$TMP_DIR/offline.json"
expect_value "OFFLINE|4" "$(field data.state < "$TMP_DIR/offline.json")|$(field data.revision < "$TMP_DIR/offline.json")" "offline revision"
STATUS=$(curl -s -o "$TMP_DIR/back-online.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":4}' \
  "$BASE/api/v1/admin/exchange-projects/$PROJECT_ID/online")
expect_status 200 "$STATUS" "project re-online" "$TMP_DIR/back-online.json"
expect_value "ONLINE|5|5|3" "$(field data.state < "$TMP_DIR/back-online.json")|$(field data.revision < "$TMP_DIR/back-online.json")|$(field data.stockUsed < "$TMP_DIR/back-online.json")|$(field data.perUserLimit < "$TMP_DIR/back-online.json")" "re-online preserves stock used and limit"

echo "== Ended project cannot be online; rule can change for future exchanges =="
MAIN_ID=$PROJECT_ID
create_project "S15A Ended $STAMP" "$RULE1" "$(iso -7200000)" "$PAST_END" "$TMP_DIR/ended.json"
ENDED_ID=$PROJECT_ID
STATUS=$(curl -s -o "$TMP_DIR/ended-online.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":0}' \
  "$BASE/api/v1/admin/exchange-projects/$ENDED_ID/online")
expect_status 409 "$STATUS" "ended project cannot be online" "$TMP_DIR/ended-online.json"
STATUS=$(curl -s -o "$TMP_DIR/rule-change.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"name\":\"S15A Project updated $STAMP\",\"ruleId\":$RULE2,\"pointsPrice\":180,\"stockTotal\":6,\"perUserLimit\":3,\"openAt\":\"$NOW\",\"closeAt\":\"$END\",\"revision\":5}" \
  "$BASE/api/v1/admin/exchange-projects/$MAIN_ID")
expect_status 200 "$STATUS" "project can switch rule for future exchanges" "$TMP_DIR/rule-change.json"
expect_value "$RULE2|1|6|5" "$(field data.rule.id < "$TMP_DIR/rule-change.json")|$(field data.rule.versionNo < "$TMP_DIR/rule-change.json")|$(field data.revision < "$TMP_DIR/rule-change.json")|$(field data.stockUsed < "$TMP_DIR/rule-change.json")" "rule switch keeps state and used stock"

echo "ALL S15-A EXCHANGE PROJECT ACCEPTANCE CHECKS PASSED"