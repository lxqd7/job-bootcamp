#!/usr/bin/env bash
# S14-a acceptance: teacher excellent note locks revision, awards 5 points once,
# author edit cancels display without deducting points, and multi-teacher evaluation is unique.
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

publish_course() {
  local title="$1" out="$2" chapter_json lesson_json
  STATUS=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S14A\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$STATUS" "create course $title" "$out"
  COURSE_ID=$(field data.id < "$out")
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
  expect_status 200 "$STATUS" "publish course $title" "$TMP_DIR/publish.json"
}

assign_teacher() {
  local teacher_id="$1" course_id="$2" out current
  out="$TMP_DIR/assign-$teacher_id.json"
  status=$(curl -s -o "$out" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$BASE/api/v1/admin/teachers/$teacher_id/assignments")
  expect_status 200 "$status" "read teacher $teacher_id assignments" "$out"
  current=$(field data.revision < "$out")
  status=$(curl -s -o "$TMP_DIR/assign-save-$teacher_id.json" -w '%{http_code}' -X PUT \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"courseIds\":[$course_id],\"routeIds\":[],\"revision\":$current}" \
    "$BASE/api/v1/admin/teachers/$teacher_id/assignments")
  expect_status 200 "$status" "assign teacher $teacher_id" "$TMP_DIR/assign-save-$teacher_id.json"
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
  local token="$1" course="$2" body="$3" visibility="$4" out="$5" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d "{\"body\":\"$body\",\"visibility\":\"$visibility\"}" \
    "$BASE/api/v1/courses/$course/notes")
  expect_status 200 "$status" "create note $body" "$out"
  NOTE_ID=$(field data.id < "$out")
}

evaluate() {
  local token="$1" note="$2" revision="$3" label="$4" out="$5" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d "{\"contentRevision\":$revision}" \
    "$BASE/api/v1/teacher/notes/$note/excellent")
  expect_status 200 "$status" "$label" "$out"
}

wait_points() {
  local note="$1" expected="$2"
  for attempt in $(seq 1 60); do
    if [ "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$note'")" = "$expected" ]; then
      echo "PASS  points ledger reached $expected for note $note"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  points ledger did not reach $expected for note $note"; exit 1
}

echo "== Setup course, teachers and students =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
ADMIN_ID=$USER_ID
publish_course "S14A course $STAMP" "$TMP_DIR/course1.json"
COURSE1=$COURSE_ID
publish_course "S14A unassigned $STAMP" "$TMP_DIR/course2.json"
COURSE2=$COURSE_ID

login teacher01 ADMIN "$TMP_DIR/t1.json"
T1_TOKEN=$TOKEN
T1_ID=$USER_ID
login teacher02 ADMIN "$TMP_DIR/t2.json"
T2_TOKEN=$TOKEN
T2_ID=$USER_ID
assign_teacher "$T1_ID" "$COURSE1"
assign_teacher "$T2_ID" "$COURSE1"

register "s14a_s1_$STAMP" "$TMP_DIR/reg1.json"
login "s14a_s1_$STAMP" STUDENT "$TMP_DIR/s1.json"
S1_TOKEN=$TOKEN
S1_ID=$USER_ID
buy_course "$S1_TOKEN" "$COURSE1" "s14a-s1-c1-$STAMP"
register "s14a_s2_$STAMP" "$TMP_DIR/reg2.json"
login "s14a_s2_$STAMP" STUDENT "$TMP_DIR/s2.json"
S2_TOKEN=$TOKEN
S2_ID=$USER_ID
buy_course "$S2_TOKEN" "$COURSE1" "s14a-s2-c1-$STAMP"
buy_course "$S2_TOKEN" "$COURSE2" "s14a-s2-c2-$STAMP"

echo "== Create notes =="
create_note "$S1_TOKEN" "$COURSE1" "Public original note $STAMP" "COURSE_PUBLIC" "$TMP_DIR/n1.json"
N1=$NOTE_ID
create_note "$S1_TOKEN" "$COURSE1" "Private original note $STAMP" "PRIVATE" "$TMP_DIR/n2.json"
N2=$NOTE_ID
create_note "$S2_TOKEN" "$COURSE1" "Collectable note $STAMP" "COURSE_PUBLIC" "$TMP_DIR/n3.json"
N3=$NOTE_ID
create_note "$S2_TOKEN" "$COURSE2" "Unassigned course note $STAMP" "COURSE_PUBLIC" "$TMP_DIR/n4.json"
N4=$NOTE_ID
create_note "$S2_TOKEN" "$COURSE1" "Hidden note $STAMP" "COURSE_PUBLIC" "$TMP_DIR/n5.json"
N5=$NOTE_ID

STATUS=$(curl -s -o "$TMP_DIR/collect.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/notes/$N3/collect")
expect_status 200 "$STATUS" "collect note" "$TMP_DIR/collect.json"
COLLECTED=$(field data.id < "$TMP_DIR/collect.json")
STATUS=$(curl -s -o "$TMP_DIR/hide.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"reason":"S14A hidden"}' \
  "$BASE/api/v1/admin/content/NOTE/$N5/hide")
expect_status 200 "$STATUS" "hide note" "$TMP_DIR/hide.json"
expect_value "0" "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT'")" "note creation did not award points"

echo "== Evaluation validates target, scope and revision =="
STATUS=$(curl -s -o "$TMP_DIR/private-eval.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $T1_TOKEN" -d '{"contentRevision":0}' \
  "$BASE/api/v1/teacher/notes/$N2/excellent")
expect_status 400 "$STATUS" "private note cannot be excellent" "$TMP_DIR/private-eval.json"
STATUS=$(curl -s -o "$TMP_DIR/collected-eval.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $T1_TOKEN" -d '{"contentRevision":0}' \
  "$BASE/api/v1/teacher/notes/$COLLECTED/excellent")
expect_status 400 "$STATUS" "collected copy cannot be excellent" "$TMP_DIR/collected-eval.json"
STATUS=$(curl -s -o "$TMP_DIR/hidden-eval.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $T1_TOKEN" -d '{"contentRevision":1}' \
  "$BASE/api/v1/teacher/notes/$N5/excellent")
expect_status 400 "$STATUS" "hidden note cannot be excellent" "$TMP_DIR/hidden-eval.json"
STATUS=$(curl -s -o "$TMP_DIR/unassigned-eval.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $T1_TOKEN" -d '{"contentRevision":0}' \
  "$BASE/api/v1/teacher/notes/$N4/excellent")
expect_status 403 "$STATUS" "teacher outside course scope rejected" "$TMP_DIR/unassigned-eval.json"
STATUS=$(curl -s -o "$TMP_DIR/student-eval.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" -d '{"contentRevision":0}' \
  "$BASE/api/v1/teacher/notes/$N1/excellent")
expect_status 403 "$STATUS" "student cannot evaluate" "$TMP_DIR/student-eval.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-eval.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"contentRevision":0}' \
  "$BASE/api/v1/teacher/notes/$N1/excellent")
expect_status 403 "$STATUS" "admin cannot evaluate as teacher" "$TMP_DIR/admin-eval.json"
STATUS=$(curl -s -o "$TMP_DIR/stale-eval.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $T1_TOKEN" -d '{"contentRevision":9}' \
  "$BASE/api/v1/teacher/notes/$N1/excellent")
expect_status 409 "$STATUS" "stale content revision rejected" "$TMP_DIR/stale-eval.json"

echo "== First evaluation locks revision and awards once =="
evaluate "$T1_TOKEN" "$N1" 0 "teacher01 evaluates public note" "$TMP_DIR/eval1.json"
expect_value "true|0" "$(field data.excellent < "$TMP_DIR/eval1.json")|$(field data.excellentRevision < "$TMP_DIR/eval1.json")" "excellent revision locked"
wait_points "$N1" 1
for attempt in $(seq 1 40); do
  STATUS=$(db "SELECT COALESCE(MAX(available),0) FROM bc_marketing.points_account WHERE user_id=$S1_ID")
  [ "$STATUS" = "5" ] && break
  sleep 0.5
done
expect_value "5" "$STATUS" "author available points +5"
expect_value "5" "$(db "SELECT reward_points FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N1'")" "ledger reward is 5"
expect_value "5" "$(db "SELECT rank_delta FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N1'")" "month rank delta is 5"
expect_value "$S1_ID|$T1_ID|0|1" "$(db "SELECT CONCAT_WS('|',author_id,teacher_id,content_revision,reward_version) FROM bc_interaction.excellent_reward WHERE note_id=$N1")" "reward audit fields"
for attempt in $(seq 1 40); do
  OUTBOX_STATUS=$(db "SELECT status FROM bc_interaction.outbox_event WHERE aggregate_id='$N1' AND type='interaction.note-excellent.v1'")
  [ "$OUTBOX_STATUS" = "SENT" ] && break
  sleep 0.5
done
expect_value "SENT" "$OUTBOX_STATUS" "excellent event sent"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_marketing.inbox_event WHERE consumer='interaction-note-excellent-v1' AND business_key='EXCELLENT:$N1' AND state='PROCESSED'")" "marketing inbox processed once"
expect_value "true" "$(field data.excellent < <(curl -s -H "Authorization: Bearer $S2_TOKEN" "$BASE/api/v1/notes/$N1"))" "public note shows excellent"

echo "== Repeat and multi-teacher evaluation do not double award =="
evaluate "$T1_TOKEN" "$N1" 0 "repeat evaluation" "$TMP_DIR/eval-repeat.json"
evaluate "$T2_TOKEN" "$N1" 0 "second teacher evaluates" "$TMP_DIR/eval-t2.json"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.excellent_reward WHERE note_id=$N1")" "one reward row"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.outbox_event WHERE aggregate_id='$N1'")" "one excellent event"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N1'")" "one points ledger"
expect_value "5" "$(db "SELECT available FROM bc_marketing.points_account WHERE user_id=$S1_ID")" "points unchanged after repeats"

echo "== Cancel keeps points; re-evaluate does not re-award =="
STATUS=$(curl -s -o "$TMP_DIR/cancel.json" -w '%{http_code}' -X DELETE -H "Authorization: Bearer $T2_TOKEN" \
  "$BASE/api/v1/teacher/notes/$N1/excellent")
expect_status 200 "$STATUS" "teacher cancels excellent" "$TMP_DIR/cancel.json"
expect_value "false" "$(field data.excellent < "$TMP_DIR/cancel.json")" "excellent display cancelled"
expect_value "5" "$(db "SELECT available FROM bc_marketing.points_account WHERE user_id=$S1_ID")" "cancel does not deduct points"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N1'")" "cancel keeps reward ledger"
evaluate "$T1_TOKEN" "$N1" 0 "re-evaluate after cancel" "$TMP_DIR/eval-again.json"
expect_value "true" "$(field data.excellent < "$TMP_DIR/eval-again.json")" "excellent restored"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N1'")" "restore does not re-award"

echo "== Author edit cancels display without deducting points =="
STATUS=$(curl -s -o "$TMP_DIR/edit.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" \
  -d "{\"body\":\"Edited after excellent $STAMP\",\"visibility\":\"COURSE_PUBLIC\",\"revision\":0}" \
  "$BASE/api/v1/notes/$N1")
expect_status 200 "$STATUS" "author edits excellent note" "$TMP_DIR/edit.json"
expect_value "1|false|0" "$(field data.revision < "$TMP_DIR/edit.json")|$(field data.excellent < "$TMP_DIR/edit.json")|$(field data.excellentRevision < "$TMP_DIR/edit.json")" "edit cancels display and retains locked revision"
expect_value "5" "$(db "SELECT available FROM bc_marketing.points_account WHERE user_id=$S1_ID")" "edit does not deduct points"
expect_value "0" "$(db "SELECT content_revision FROM bc_interaction.excellent_reward WHERE note_id=$N1")" "reward keeps original version"
evaluate "$T2_TOKEN" "$N1" 1 "re-evaluate edited note" "$TMP_DIR/eval-edited.json"
expect_value "true|0" "$(field data.excellent < "$TMP_DIR/eval-edited.json")|$(field data.excellentRevision < "$TMP_DIR/eval-edited.json")" "re-evaluation reuses original reward version"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='$N1'")" "edited re-evaluation does not re-award"

STATUS=$(curl -s -o "$TMP_DIR/private-edit.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" \
  -d "{\"visibility\":\"PRIVATE\",\"revision\":1}" "$BASE/api/v1/notes/$N1")
expect_status 200 "$STATUS" "author makes note private" "$TMP_DIR/private-edit.json"
expect_value "false" "$(field data.excellent < "$TMP_DIR/private-edit.json")" "private change cancels display"
STATUS=$(curl -s -o "$TMP_DIR/private-eval2.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $T1_TOKEN" -d '{"contentRevision":2}' \
  "$BASE/api/v1/teacher/notes/$N1/excellent")
expect_status 400 "$STATUS" "private note cannot be re-evaluated" "$TMP_DIR/private-eval2.json"
STATUS=$(curl -s -o "$TMP_DIR/public-edit.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" \
  -d "{\"visibility\":\"COURSE_PUBLIC\",\"revision\":2}" "$BASE/api/v1/notes/$N1")
expect_status 200 "$STATUS" "author makes note public again" "$TMP_DIR/public-edit.json"
evaluate "$T1_TOKEN" "$N1" 3 "re-evaluate after public restore" "$TMP_DIR/eval-public.json"
expect_value "true" "$(field data.excellent < "$TMP_DIR/eval-public.json")" "public note evaluated again"
expect_value "5" "$(db "SELECT available FROM bc_marketing.points_account WHERE user_id=$S1_ID")" "still only one reward"

echo "== Concurrent teachers on one note create one reward =="
create_note "$S2_TOKEN" "$COURSE1" "Concurrent evaluation note $STAMP" "COURSE_PUBLIC" "$TMP_DIR/n6.json"
N6=$NOTE_ID
for teacher in "$T1_TOKEN" "$T2_TOKEN"; do
  (
    curl -s -o "$TMP_DIR/concurrent-$(echo "$teacher" | md5sum | cut -c1-6).json" \
      -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $teacher" -d '{"contentRevision":0}' \
      "$BASE/api/v1/teacher/notes/$N6/excellent" > "$TMP_DIR/concurrent-$(echo "$teacher" | md5sum | cut -c1-6).status"
  ) &
done
wait
for f in "$TMP_DIR"/concurrent-*.status; do
  expect_value "200" "$(cat "$f")" "concurrent evaluation accepted"
done
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.excellent_reward WHERE note_id=$N6")" "one reward under concurrent teachers"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.outbox_event WHERE aggregate_id='$N6'")" "one event under concurrent teachers"
wait_points "$N6" 1
expect_value "5" "$(db "SELECT available FROM bc_marketing.points_account WHERE user_id=$S2_ID")" "second author total points"

echo "== Teacher note list exposes excellent state =="
STATUS=$(curl -s -o "$TMP_DIR/teacher-notes.json" -w '%{http_code}' \
  -H "Authorization: Bearer $T1_TOKEN" "$BASE/api/v1/courses/$COURSE1/notes?page=1&pageSize=50")
expect_status 200 "$STATUS" "teacher reads course notes" "$TMP_DIR/teacher-notes.json"
expect_value "true" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));const n=d.data.items.find(x=>x.id===Number(process.argv[2]));console.log(n && n.excellent)' "$TMP_DIR/teacher-notes.json" "$N6")" "teacher list excellent flag"

echo "ALL S14-A EXCELLENT NOTE ACCEPTANCE CHECKS PASSED"