#!/usr/bin/env bash
# S15-c acceptance: student exchange catalog, exchange records and coupon source names.
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
  local token="$1" project="$2" rev="$3" key="$4" out="$5" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $key" -H "Authorization: Bearer $token" \
    -d "{\"expectedRevision\":$rev}" \
    "$BASE/api/v1/exchange-projects/$project/exchanges")
  expect_status 200 "$status" "exchange $key" "$out"
}

echo "== Setup project and points =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
STATUS=$(curl -s -o "$TMP_DIR/rule.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"ruleKey\":\"S15C_RULE_$STAMP\",\"type\":\"NO_THRESHOLD\",\"scope\":\"ALL\",\"amountCent\":500,\"validityType\":\"DAYS\",\"validDays\":7}" \
  "$BASE/api/v1/admin/coupon-rules")
expect_status 200 "$STATUS" "create exchange rule" "$TMP_DIR/rule.json"
RULE_ID=$(field data.id < "$TMP_DIR/rule.json")
register "s15c_s1_$STAMP" "$TMP_DIR/reg1.json"
login "s15c_s1_$STAMP" STUDENT "$TMP_DIR/s1.json"
S1_TOKEN=$TOKEN
S1_ID=$USER_ID
register "s15c_s2_$STAMP" "$TMP_DIR/reg2.json"
login "s15c_s2_$STAMP" STUDENT "$TMP_DIR/s2.json"
S2_TOKEN=$TOKEN
db "INSERT INTO bc_marketing.points_account(user_id,available,debt,revision,created_at,updated_at) VALUES($S1_ID,300,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE available=300,debt=0"
create_project "S15C Catalog $STAMP" 40 5 2 "$TMP_DIR/p1.json"
P1=$PROJECT_ID
online_project "$P1" 0 "$TMP_DIR/p1-online.json"
P1_REV=$ONLINE_REV
create_project "S15C Offline $STAMP" 20 5 1 "$TMP_DIR/p2.json"
POFF=$PROJECT_ID
login teacher01 ADMIN "$TMP_DIR/t1.json"
T1_TOKEN=$TOKEN

echo "== Student catalog =="
STATUS=$(curl -s -o "$TMP_DIR/catalog.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/exchange-projects?page=1&pageSize=100")
expect_status 200 "$STATUS" "student catalog" "$TMP_DIR/catalog.json"
expect_value "true|40|5|2|0|$RULE_ID" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));const p=d.data.items.find(x=>x.id===Number(process.argv[2]));console.log([p.canExchange,p.pointsPrice,p.stockRemaining,p.perUserLimit,p.userExchangedCount,p.rule.id].join("|"))' "$TMP_DIR/catalog.json" "$P1")" "catalog project fields"
expect_value "0" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.items.filter(x=>x.id===Number(process.argv[2])).length)' "$TMP_DIR/catalog.json" "$POFF")" "offline project excluded"
STATUS=$(curl -s -o "$TMP_DIR/teacher-catalog.json" -w '%{http_code}' -H "Authorization: Bearer $T1_TOKEN" \
  "$BASE/api/v1/exchange-projects")
expect_status 403 "$STATUS" "teacher cannot read student catalog" "$TMP_DIR/teacher-catalog.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-catalog.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/exchange-projects")
expect_status 403 "$STATUS" "admin cannot use student catalog" "$TMP_DIR/admin-catalog.json"

echo "== Exchange records and coupon source =="
exchange "$S1_TOKEN" "$P1" "$P1_REV" "ui1-$STAMP" "$TMP_DIR/e1.json"
exchange "$S1_TOKEN" "$P1" "$P1_REV" "ui2-$STAMP" "$TMP_DIR/e2.json"
STATUS=$(curl -s -o "$TMP_DIR/catalog-after.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/exchange-projects?page=1&pageSize=100")
expect_status 200 "$STATUS" "catalog after exchange" "$TMP_DIR/catalog-after.json"
expect_value "false|3|2" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));const p=d.data.items.find(x=>x.id===Number(process.argv[2]));console.log([p.canExchange,p.stockRemaining,p.userExchangedCount].join("|"))' "$TMP_DIR/catalog-after.json" "$P1")" "catalog reflects stock and limit"
STATUS=$(curl -s -o "$TMP_DIR/records.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/me/exchanges?page=1&pageSize=20")
expect_status 200 "$STATUS" "my exchanges" "$TMP_DIR/records.json"
expect_value "2|S15C Catalog $STAMP|40|40|220|AVAILABLE" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));const r=d.data.items[0];console.log([d.data.total,r.projectName,r.pointsSpent,d.data.items[1].pointsSpent,r.balanceAfter,r.couponStatus].join("|"))' "$TMP_DIR/records.json")" "exchange record fields"
expect_value "NO_THRESHOLD|ALL" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));const r=d.data.items[0];console.log([r.rule.type,r.rule.scope].join("|"))' "$TMP_DIR/records.json")" "exchange record rule snapshot"
STATUS=$(curl -s -o "$TMP_DIR/records-s2.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/me/exchanges")
expect_status 200 "$STATUS" "other student records" "$TMP_DIR/records-s2.json"
expect_value "0" "$(field data.total < "$TMP_DIR/records-s2.json")" "records isolated per user"
STATUS=$(curl -s -o "$TMP_DIR/teacher-records.json" -w '%{http_code}' -H "Authorization: Bearer $T1_TOKEN" \
  "$BASE/api/v1/me/exchanges")
expect_status 403 "$STATUS" "teacher cannot read student records" "$TMP_DIR/teacher-records.json"
STATUS=$(curl -s -o "$TMP_DIR/coupons.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/me/coupons?page=1&pageSize=20")
expect_status 200 "$STATUS" "my coupons" "$TMP_DIR/coupons.json"
expect_value "EXCHANGE|S15C Catalog $STAMP|2" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));const c=d.data.items.find(x=>x.sourceType==="EXCHANGE");console.log([c.sourceType,c.sourceName,d.data.items.filter(x=>x.sourceType==="EXCHANGE").length].join("|"))' "$TMP_DIR/coupons.json")" "coupon source name and count"

echo "ALL S15-C EXCHANGE PAGE API CHECKS PASSED"