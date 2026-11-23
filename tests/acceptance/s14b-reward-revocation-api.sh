#!/usr/bin/env bash
# S14-b acceptance: admin revokes excellent reward with audit, points deduction/debt,
# original month correction, idempotency, and revoke-before-award ordering.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
MARKETING_BASE="${MARKETING_BASE:-http://127.0.0.1:18087}"
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

buy_course() {
  local token="$1" course="$2" key="$3" status quote_id quote_hash order_id
  status=$(curl -s -o "$TMP_DIR/quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d "{\"productType\":\"COURSE\",\"productId\":$course,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
    "$BASE/api/v1/trade/quotes")
  expect_status 200 "$status" "course quote" "$TMP_DIR/quote.json"
  quote_id=$(field data.quoteId < "$TMP_DIR/quote.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/quote.json")
  status=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $key" -H "Authorization: Bearer $token" \
    -d "{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[]}" \
    "$BASE/api/v1/orders")
  expect_status 200 "$status" "course order" "$TMP_DIR/order.json"
  order_id=$(field data.id < "$TMP_DIR/order.json")
  status=$(curl -s -o "$TMP_DIR/payment.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d '{"channel":"MOCK"}' "$BASE/api/v1/orders/$order_id/payment")
  expect_status 200 "$status" "payment request" "$TMP_DIR/payment.json"
  status=$(curl -s -o "$TMP_DIR/pay.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $token" \
    "$BASE/api/v1/orders/$order_id/payment/mock-pay")
  expect_status 200 "$status" "mock pay" "$TMP_DIR/pay.json"
  for attempt in $(seq 1 40); do
    curl -s -o "$TMP_DIR/order-state.json" -H "Authorization: Bearer $token" "$BASE/api/v1/orders/$order_id"
    if [ "$(field data.fulfillmentState < "$TMP_DIR/order-state.json")" = "DONE" ]; then
      echo "PASS  course fulfilled"
      return 0
    fi
    sleep 0.4
  done
  echo "FAIL  course not fulfilled"; exit 1
}

create_note() {
  local token="$1" course="$2" body="$3" out="$4" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d "{\"body\":\"$body\",\"visibility\":\"COURSE_PUBLIC\"}" \
    "$BASE/api/v1/courses/$course/notes")
  expect_status 200 "$status" "create note $body" "$out"
  NOTE_ID=$(field data.id < "$out")
}

evaluate() {
  local token="$1" note="$2" out="$3" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d '{"contentRevision":0}' \
    "$BASE/api/v1/teacher/notes/$note/excellent")
  expect_status 200 "$status" "evaluate note $note" "$out"
}

revoke() {
  local token="$1" note="$2" reason="$3" label="$4" out="$5" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d "{\"reason\":\"$reason\"}" \
    "$BASE/api/v1/admin/notes/$note/revoke-reward")
  expect_status 200 "$status" "$label" "$out"
  REVOKE_STATUS=$status
}

wait_db() {
  local sql="$1" expected="$2" label="$3"
  local actual=""
  for attempt in $(seq 1 80); do
    actual=$(db "$sql")
    if [ "$actual" = "$expected" ]; then
      echo "PASS  $label  ($actual)"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  $label expected '$expected', got '$actual'"; exit 1
}

wait_excellent_points() {
  local note="$1" expected="$2"
  wait_db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$note'" "$expected" "excellent ledger $expected for note $note"
}

wait_revoke_points() {
  local note="$1" expected="$2"
  wait_db "SELECT COUNT(*) FROM bc_marketing.points_ledger r JOIN bc_marketing.points_ledger e ON r.original_ledger_id=e.id WHERE r.type='REVOKE' AND e.type='EXCELLENT' AND e.business_key='$note'" "$expected" "revoke ledger $expected for note $note"
}

current_month() {
  TZ=Asia/Shanghai date +%Y-%m
}

echo "== Setup course, teacher and students =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
ADMIN_ID=$USER_ID
STATUS=$(curl -s -o "$TMP_DIR/course.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S14B course $STAMP\",\"description\":\"S14B\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
  "$BASE/api/v1/admin/courses")
expect_status 200 "$STATUS" "create course" "$TMP_DIR/course.json"
COURSE_ID=$(field data.id < "$TMP_DIR/course.json")
STATUS=$(curl -s -o "$TMP_DIR/chapter.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Chapter","sort":1}' \
  "$BASE/api/v1/admin/courses/$COURSE_ID/chapters")
expect_status 200 "$STATUS" "create chapter" "$TMP_DIR/chapter.json"
CHAPTER_ID=$(field data.id < "$TMP_DIR/chapter.json")
STATUS=$(curl -s -o "$TMP_DIR/lesson.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Video","sort":1,"mediaId":1}' \
  "$BASE/api/v1/admin/chapters/$CHAPTER_ID/lessons")
expect_status 200 "$STATUS" "create lesson" "$TMP_DIR/lesson.json"
STATUS=$(curl -s -o "$TMP_DIR/publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":0}' \
  "$BASE/api/v1/admin/courses/$COURSE_ID/publish")
expect_status 200 "$STATUS" "publish course" "$TMP_DIR/publish.json"

login teacher01 ADMIN "$TMP_DIR/t1.json"
T1_TOKEN=$TOKEN
T1_ID=$USER_ID
STATUS=$(curl -s -o "$TMP_DIR/assign-current.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/teachers/$T1_ID/assignments")
expect_status 200 "$STATUS" "read teacher assignments" "$TMP_DIR/assign-current.json"
ASSIGN_REV=$(field data.revision < "$TMP_DIR/assign-current.json")
STATUS=$(curl -s -o "$TMP_DIR/assign.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[$COURSE_ID],\"routeIds\":[],\"revision\":$ASSIGN_REV}" \
  "$BASE/api/v1/admin/teachers/$T1_ID/assignments")
expect_status 200 "$STATUS" "assign teacher" "$TMP_DIR/assign.json"

register "s14b_s1_$STAMP" "$TMP_DIR/reg1.json"
login "s14b_s1_$STAMP" STUDENT "$TMP_DIR/s1.json"
S1_TOKEN=$TOKEN
S1_ID=$USER_ID
buy_course "$S1_TOKEN" "$COURSE_ID" "s14b-s1-$STAMP"
register "s14b_s2_$STAMP" "$TMP_DIR/reg2.json"
login "s14b_s2_$STAMP" STUDENT "$TMP_DIR/s2.json"
S2_TOKEN=$TOKEN
S2_ID=$USER_ID
buy_course "$S2_TOKEN" "$COURSE_ID" "s14b-s2-$STAMP"

create_note "$S1_TOKEN" "$COURSE_ID" "S14B sufficient $STAMP" "$TMP_DIR/n1.json"
N1=$NOTE_ID
create_note "$S1_TOKEN" "$COURSE_ID" "S14B cancel then revoke $STAMP" "$TMP_DIR/n2.json"
N2=$NOTE_ID
create_note "$S1_TOKEN" "$COURSE_ID" "S14B debt $STAMP" "$TMP_DIR/n3.json"
N3=$NOTE_ID
create_note "$S1_TOKEN" "$COURSE_ID" "S14B debt offset $STAMP" "$TMP_DIR/n4.json"
N4=$NOTE_ID
create_note "$S2_TOKEN" "$COURSE_ID" "S14B historical $STAMP" "$TMP_DIR/n5.json"
N5=$NOTE_ID
create_note "$S2_TOKEN" "$COURSE_ID" "S14B reverse order $STAMP" "$TMP_DIR/n6.json"
N6=$NOTE_ID

echo "== Permission, audit required and missing reward =="
STATUS=$(curl -s -o "$TMP_DIR/student-revoke.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" -d '{"reason":"student"}' \
  "$BASE/api/v1/admin/notes/$N1/revoke-reward")
expect_status 403 "$STATUS" "student cannot revoke reward" "$TMP_DIR/student-revoke.json"
STATUS=$(curl -s -o "$TMP_DIR/teacher-revoke.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $T1_TOKEN" -d '{"reason":"teacher"}' \
  "$BASE/api/v1/admin/notes/$N1/revoke-reward")
expect_status 403 "$STATUS" "teacher cannot revoke reward" "$TMP_DIR/teacher-revoke.json"
STATUS=$(curl -s -o "$TMP_DIR/no-reward.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"reason":"no reward"}' \
  "$BASE/api/v1/admin/notes/$N1/revoke-reward")
expect_status 404 "$STATUS" "note without reward has nothing to revoke" "$TMP_DIR/no-reward.json"

echo "== Sufficient balance: deduct 5, preserve original month ledger =="
evaluate "$T1_TOKEN" "$N1" "$TMP_DIR/eval1.json"
wait_excellent_points "$N1" 1
STATUS=$(curl -s -o "$TMP_DIR/blank-reason.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"reason":""}' \
  "$BASE/api/v1/admin/notes/$N1/revoke-reward")
expect_status 400 "$STATUS" "blank revoke reason rejected" "$TMP_DIR/blank-reason.json"
revoke "$ADMIN_TOKEN" "$N1" "violation $STAMP" "admin revokes reward" "$TMP_DIR/revoke1.json"
expect_value "true|1" "$(field data.revoked < "$TMP_DIR/revoke1.json")|$(field data.rewardVersion < "$TMP_DIR/revoke1.json")" "revoke result"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.excellent_reward WHERE note_id=$N1 AND revoked=TRUE AND revoke_reason='violation $STAMP'")" "reward marked revoked with reason"
expect_value "0" "$(db "SELECT excellent FROM bc_interaction.note WHERE id=$N1")" "excellent display removed"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.moderation_action WHERE action='REVOKE_EXCELLENT' AND target_type='NOTE' AND target_id=$N1 AND admin_id=$ADMIN_ID AND reason='violation $STAMP'")" "revoke audit recorded"
for attempt in $(seq 1 40); do
  EVENT_STATE=$(db "SELECT status FROM bc_interaction.outbox_event WHERE aggregate_id='$N1' AND type='interaction.reward-revoked.v1'")
  [ "$EVENT_STATE" = "SENT" ] && break
  sleep 0.5
done
expect_value "SENT" "$EVENT_STATE" "reward revoked event sent"
wait_revoke_points "$N1" 1
wait_db "SELECT COUNT(*) FROM bc_marketing.inbox_event WHERE consumer='interaction-reward-revoked-v1' AND business_key='REVOKE:$N1' AND state='PROCESSED'" "1" "revoke inbox processed"
wait_db "SELECT available FROM bc_marketing.points_account WHERE user_id=$S1_ID" "0" "available reduced to zero"
wait_db "SELECT debt FROM bc_marketing.points_account WHERE user_id=$S1_ID" "0" "no debt when balance sufficient"
wait_db "SELECT COALESCE(MAX(score),0) FROM bc_marketing.points_month WHERE month='$(current_month)' AND user_id=$S1_ID" "0" "current month score corrected"
EXCELLENT_LEDGER=$(db "SELECT id FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N1'")
expect_value "$EXCELLENT_LEDGER|-5|-5|0|-5|REVOKED" "$(db "SELECT CONCAT_WS('|',original_ledger_id,reward_points,balance_delta,debt_delta,rank_delta,result_code) FROM bc_marketing.points_ledger WHERE type='REVOKE' AND business_key='$EXCELLENT_LEDGER'")" "revoke ledger amounts and original link"
STATUS=$(curl -s -o "$TMP_DIR/repeat-revoke.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"reason":"again"}' \
  "$BASE/api/v1/admin/notes/$N1/revoke-reward")
expect_status 409 "$STATUS" "repeat revoke rejected" "$TMP_DIR/repeat-revoke.json"
STATUS=$(curl -s -o "$TMP_DIR/reevaluate.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $T1_TOKEN" -d '{"contentRevision":0}' \
  "$BASE/api/v1/teacher/notes/$N1/excellent")
expect_status 409 "$STATUS" "revoked reward cannot be re-evaluated" "$TMP_DIR/reevaluate.json"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger r WHERE r.type='REVOKE' AND r.original_ledger_id=$EXCELLENT_LEDGER")" "duplicate revoke did not duplicate ledger"

echo "== Ordinary teacher cancel does not block later violation revoke =="
evaluate "$T1_TOKEN" "$N2" "$TMP_DIR/eval2.json"
wait_excellent_points "$N2" 1
STATUS=$(curl -s -o "$TMP_DIR/cancel2.json" -w '%{http_code}' -X DELETE -H "Authorization: Bearer $T1_TOKEN" \
  "$BASE/api/v1/teacher/notes/$N2/excellent")
expect_status 200 "$STATUS" "teacher cancels display" "$TMP_DIR/cancel2.json"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N2'")" "cancel keeps reward"
revoke "$ADMIN_TOKEN" "$N2" "violation after cancel $STAMP" "admin revokes after ordinary cancel" "$TMP_DIR/revoke2.json"
wait_revoke_points "$N2" 1
wait_db "SELECT available FROM bc_marketing.points_account WHERE user_id=$S1_ID" "0" "cancel then revoke deducts reward"

echo "== Insufficient balance becomes debt; next reward offsets debt without double month deduction =="
evaluate "$T1_TOKEN" "$N3" "$TMP_DIR/eval3.json"
wait_excellent_points "$N3" 1
wait_db "SELECT available FROM bc_marketing.points_account WHERE user_id=$S1_ID" "5" "third award available 5"
db "UPDATE bc_marketing.points_account SET available=2 WHERE user_id=$S1_ID"
revoke "$ADMIN_TOKEN" "$N3" "violation with low balance $STAMP" "revoke with insufficient balance" "$TMP_DIR/revoke3.json"
wait_revoke_points "$N3" 1
wait_db "SELECT CONCAT_WS('|',available,debt) FROM bc_marketing.points_account WHERE user_id=$S1_ID" "0|3" "insufficient balance creates 3 debt"
EXCELLENT3=$(db "SELECT id FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N3'")
expect_value "-2|3|-5" "$(db "SELECT CONCAT_WS('|',balance_delta,debt_delta,rank_delta) FROM bc_marketing.points_ledger WHERE type='REVOKE' AND business_key='$EXCELLENT3'")" "revoke takes 2 and records 3 debt"
evaluate "$T1_TOKEN" "$N4" "$TMP_DIR/eval4.json"
wait_excellent_points "$N4" 1
wait_db "SELECT CONCAT_WS('|',available,debt) FROM bc_marketing.points_account WHERE user_id=$S1_ID" "2|0" "new reward offsets debt before balance"
expect_value "5|2|-3|5" "$(db "SELECT CONCAT_WS('|',reward_points,balance_delta,debt_delta,rank_delta) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N4'")" "new reward keeps full rank delta but only adds net balance"
wait_db "SELECT COALESCE(MAX(score),0) FROM bc_marketing.points_month WHERE month='$(current_month)' AND user_id=$S1_ID" "5" "new month score is 5 after debt offset"

echo "== Historical month correction uses original reward month =="
evaluate "$T1_TOKEN" "$N5" "$TMP_DIR/eval5.json"
wait_excellent_points "$N5" 1
wait_db "SELECT available FROM bc_marketing.points_account WHERE user_id=$S2_ID" "5" "historical author starts with 5"
HIST_EXCELLENT=$(db "SELECT id FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N5'")
ORIGINAL_MONTH=$(db "SELECT business_month FROM bc_marketing.points_ledger WHERE id=$HIST_EXCELLENT")
db "UPDATE bc_marketing.points_ledger SET business_date='2025-01-15', business_month='2025-01' WHERE id=$HIST_EXCELLENT"
db "INSERT INTO bc_marketing.points_month(month,user_id,score,revision) VALUES('2025-01',$S2_ID,5,1) ON DUPLICATE KEY UPDATE score=5, revision=revision+1"
db "UPDATE bc_marketing.points_month SET score=0, revision=revision+1 WHERE month='$ORIGINAL_MONTH' AND user_id=$S2_ID"
revoke "$ADMIN_TOKEN" "$N5" "historical violation $STAMP" "revoke historical reward" "$TMP_DIR/revoke5.json"
wait_revoke_points "$N5" 1
wait_db "SELECT COALESCE(MAX(score),0) FROM bc_marketing.points_month WHERE month='2025-01' AND user_id=$S2_ID" "0" "historical month corrected"
wait_db "SELECT COALESCE(MAX(score),0) FROM bc_marketing.points_month WHERE month='$ORIGINAL_MONTH' AND user_id=$S2_ID" "0" "current month not double deducted"
expect_value "2025-01" "$(db "SELECT business_month FROM bc_marketing.points_ledger WHERE type='REVOKE' AND business_key='$HIST_EXCELLENT'")" "revoke ledger keeps original business month"
for attempt in $(seq 1 40); do
  PROJECTION=$(db "SELECT COUNT(*) FROM bc_marketing.outbox_event WHERE type='marketing.projection-changed.v1' AND payload LIKE '%2025-01%' AND aggregate_id='$S2_ID'")
  [ "$PROJECTION" -ge 1 ] && break
  sleep 0.5
done
expect_value "1" "$PROJECTION" "historical month projection emitted"

echo "== Revoke before award: pending then net zero in one award transaction =="
REVOKE_EVENT_ID=$(node -e 'console.log(require("crypto").randomUUID())')
OCCURRED_AT=$(node -e 'console.log(new Date().toISOString())')
STATUS=$(curl -s -o "$TMP_DIR/pending.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d "{\"noteId\":$N6,\"originalEventId\":\"$REVOKE_EVENT_ID\",\"occurredAt\":\"$OCCURRED_AT\",\"reason\":\"reverse order $STAMP\"}" \
  "$MARKETING_BASE/dev/points/fixtures/excellent-revocation")
expect_status 200 "$STATUS" "revoke fixture arrives before award" "$TMP_DIR/pending.json"
expect_value "PENDING" "$(db "SELECT state FROM bc_marketing.excellent_revocation WHERE note_id=$N6")" "revocation stored pending"
expect_value "0" "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N6'")" "no award ledger before award event"
evaluate "$T1_TOKEN" "$N6" "$TMP_DIR/eval6.json"
wait_excellent_points "$N6" 1
wait_revoke_points "$N6" 1
wait_db "SELECT state FROM bc_marketing.excellent_revocation WHERE note_id=$N6" "APPLIED" "pending revocation applied after award"
wait_db "SELECT CONCAT_WS('|',available,debt) FROM bc_marketing.points_account WHERE user_id=$S2_ID" "0|0" "reverse order nets zero points and debt"
wait_db "SELECT COALESCE(MAX(score),0) FROM bc_marketing.points_month WHERE month='$(current_month)' AND user_id=$S2_ID" "0" "reverse order nets zero month score"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='REVOKE' AND business_key=(SELECT id FROM (SELECT id FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N6') x)")" "reverse order writes one revoke ledger"

echo "ALL S14-B REWARD REVOCATION ACCEPTANCE CHECKS PASSED"