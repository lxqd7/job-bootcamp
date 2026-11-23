#!/usr/bin/env bash
# S13-b acceptance: original public/private notes, collection uniqueness/snapshot,
# owner management after refund, logical deletion and admin hide.
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
  local username="$1" password="$2" app="$3" out="$4" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
  TOKEN=$(field data.accessToken < "$out")
  USER_ID=$(field data.user.id < "$out")
}

register() {
  local username="$1" nickname="$2" out="$3" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"Passw0rd!\",\"nickname\":\"$nickname\"}" \
    "$BASE/api/v1/auth/register")
  expect_status 200 "$status" "register $username" "$out"
}

buy_course() {
  local token="$1" key="$2" status quote_id quote_hash order_id
  status=$(curl -s -o "$TMP_DIR/quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d "{\"productType\":\"COURSE\",\"productId\":$COURSE_ID,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
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
  local token="$1" body="$2" visibility="$3" out="$4" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d "{\"lessonId\":null,\"positionMs\":0,\"body\":\"$body\",\"visibility\":\"$visibility\"}" \
    "$BASE/api/v1/courses/$COURSE_ID/notes")
  echo "$status"
}

echo "== Setup course, students, teacher =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
STATUS=$(curl -s -o "$TMP_DIR/course.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S13B course $STAMP\",\"description\":\"S13B\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
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

register "s13b_s1_$STAMP" "S13BStudent1" "$TMP_DIR/reg1.json"
login "s13b_s1_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/s1.json"
S1_TOKEN=$TOKEN
S1_ID=$USER_ID
buy_course "$S1_TOKEN" "s13b-s1-$STAMP"
register "s13b_s2_$STAMP" "S13BStudent2" "$TMP_DIR/reg2.json"
login "s13b_s2_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/s2.json"
S2_TOKEN=$TOKEN
S2_ID=$USER_ID
buy_course "$S2_TOKEN" "s13b-s2-$STAMP"
register "s13b_s3_$STAMP" "S13BStudent3" "$TMP_DIR/reg3.json"
login "s13b_s3_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/s3.json"
S3_TOKEN=$TOKEN

login teacher01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/teacher.json"
TEACHER_TOKEN=$TOKEN
TEACHER_ID=$USER_ID
STATUS=$(curl -s -o "$TMP_DIR/t-current.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "read teacher scope" "$TMP_DIR/t-current.json"
STATUS=$(curl -s -o "$TMP_DIR/t-save.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[$COURSE_ID],\"routeIds\":[],\"revision\":$(field data.revision < "$TMP_DIR/t-current.json")}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "assign teacher" "$TMP_DIR/t-save.json"
login teacher02 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/teacher2.json"
TEACHER2_TOKEN=$TOKEN

echo "== Public/private originals and listing scope =="
STATUS=$(create_note "$S1_TOKEN" "public note body" COURSE_PUBLIC "$TMP_DIR/p1.json")
expect_status 200 "$STATUS" "create public note" "$TMP_DIR/p1.json"
P1=$(field data.id < "$TMP_DIR/p1.json")
P1_REV=$(field data.revision < "$TMP_DIR/p1.json")
STATUS=$(create_note "$S1_TOKEN" "private note body" PRIVATE "$TMP_DIR/p2.json")
expect_status 200 "$STATUS" "create private note" "$TMP_DIR/p2.json"
P2=$(field data.id < "$TMP_DIR/p2.json")
P2_REV=$(field data.revision < "$TMP_DIR/p2.json")
STATUS=$(create_note "$S2_TOKEN" "second public note" COURSE_PUBLIC "$TMP_DIR/p3.json")
expect_status 200 "$STATUS" "create second public note" "$TMP_DIR/p3.json"
P3=$(field data.id < "$TMP_DIR/p3.json")
P3_REV=$(field data.revision < "$TMP_DIR/p3.json")
STATUS=$(create_note "$S2_TOKEN" "third public note" COURSE_PUBLIC "$TMP_DIR/p4.json")
expect_status 200 "$STATUS" "create third public note" "$TMP_DIR/p4.json"
P4=$(field data.id < "$TMP_DIR/p4.json")

STATUS=$(curl -s -o "$TMP_DIR/course-notes.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/notes")
expect_status 200 "$STATUS" "course public notes" "$TMP_DIR/course-notes.json"
expect_value "3" "$(field data.total < "$TMP_DIR/course-notes.json")" "three public originals"
STATUS=$(curl -s -o "$TMP_DIR/s1-my.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/me/notes")
expect_status 200 "$STATUS" "owner my notes" "$TMP_DIR/s1-my.json"
expect_value "2" "$(field data.total < "$TMP_DIR/s1-my.json")" "owner sees public and private"
STATUS=$(curl -s -o "$TMP_DIR/teacher-notes.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/notes")
expect_status 200 "$STATUS" "teacher public notes" "$TMP_DIR/teacher-notes.json"
expect_value "3" "$(field data.total < "$TMP_DIR/teacher-notes.json")" "teacher sees only public"
STATUS=$(curl -s -o "$TMP_DIR/s3-notes.json" -w '%{http_code}' -H "Authorization: Bearer $S3_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/notes")
expect_status 403 "$STATUS" "student without entitlement cannot list" "$TMP_DIR/s3-notes.json"
STATUS=$(curl -s -o "$TMP_DIR/teacher2-notes.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER2_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/notes")
expect_status 403 "$STATUS" "unassigned teacher cannot list" "$TMP_DIR/teacher2-notes.json"
STATUS=$(curl -s -o "$TMP_DIR/s2-private.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/notes/$P2")
expect_status 404 "$STATUS" "private note hidden from others" "$TMP_DIR/s2-private.json"
STATUS=$(curl -s -o "$TMP_DIR/s1-private.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/notes/$P2")
expect_status 200 "$STATUS" "owner reads private note" "$TMP_DIR/s1-private.json"
STATUS=$(curl -s -o "$TMP_DIR/admin-notes.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/notes")
expect_status 403 "$STATUS" "admin has no default note read" "$TMP_DIR/admin-notes.json"

echo "== Edit, revision and non-owner boundaries =="
STATUS=$(curl -s -o "$TMP_DIR/p1-edit.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" \
  -d "{\"body\":\"updated original body\",\"visibility\":\"COURSE_PUBLIC\",\"revision\":$P1_REV}" \
  "$BASE/api/v1/notes/$P1")
expect_status 200 "$STATUS" "owner edits public note" "$TMP_DIR/p1-edit.json"
P1_REV=$(field data.revision < "$TMP_DIR/p1-edit.json")
STATUS=$(curl -s -o "$TMP_DIR/p1-stale.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" -d '{"body":"stale","revision":0}' "$BASE/api/v1/notes/$P1")
expect_status 409 "$STATUS" "stale note revision rejected" "$TMP_DIR/p1-stale.json"
STATUS=$(curl -s -o "$TMP_DIR/p1-nonowner.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" \
  -d "{\"body\":\"hack\",\"revision\":$P1_REV}" "$BASE/api/v1/notes/$P1")
expect_status 403 "$STATUS" "non-owner cannot edit note" "$TMP_DIR/p1-nonowner.json"

echo "== Collection uniqueness, snapshot and copy isolation =="
STATUS=$(curl -s -o "$TMP_DIR/collect.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $S2_TOKEN" "$BASE/api/v1/notes/$P1/collect")
expect_status 200 "$STATUS" "collect public note" "$TMP_DIR/collect.json"
COPY=$(field data.id < "$TMP_DIR/collect.json")
expect_value "COLLECTED" "$(field data.type < "$TMP_DIR/collect.json")" "collected type"
expect_value "PRIVATE" "$(field data.visibility < "$TMP_DIR/collect.json")" "collected private"
expect_value "$P1" "$(field data.sourceNoteId < "$TMP_DIR/collect.json")" "source note id"
expect_value "S13BStudent1" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.sourceAuthorSnapshot.nickname)' "$TMP_DIR/collect.json")" "source author snapshot"
STATUS=$(curl -s -o "$TMP_DIR/collect2.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $S2_TOKEN" "$BASE/api/v1/notes/$P1/collect")
expect_status 200 "$STATUS" "duplicate collect returns same copy" "$TMP_DIR/collect2.json"
expect_value "$COPY" "$(field data.id < "$TMP_DIR/collect2.json")" "same copy id"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.note WHERE owner_id=$S2_ID AND source_note_id=$P1")" "unique collected copy"
STATUS=$(curl -s -o "$TMP_DIR/collect-own.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $S1_TOKEN" "$BASE/api/v1/notes/$P1/collect")
expect_status 400 "$STATUS" "cannot collect own note" "$TMP_DIR/collect-own.json"
STATUS=$(curl -s -o "$TMP_DIR/collect-private.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $S2_TOKEN" "$BASE/api/v1/notes/$P2/collect")
expect_status 404 "$STATUS" "cannot collect private note" "$TMP_DIR/collect-private.json"
STATUS=$(curl -s -o "$TMP_DIR/collect-s3.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $S3_TOKEN" "$BASE/api/v1/notes/$P1/collect")
expect_status 403 "$STATUS" "entitlement required to collect" "$TMP_DIR/collect-s3.json"
STATUS=$(curl -s -o "$TMP_DIR/copy-public.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" -d '{"visibility":"COURSE_PUBLIC","revision":0}' \
  "$BASE/api/v1/notes/$COPY")
expect_status 400 "$STATUS" "collected copy cannot be public" "$TMP_DIR/copy-public.json"
STATUS=$(curl -s -o "$TMP_DIR/copy-edit.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S2_TOKEN" -d '{"body":"my copy body","revision":0}' "$BASE/api/v1/notes/$COPY")
expect_status 200 "$STATUS" "owner edits collected copy" "$TMP_DIR/copy-edit.json"
STATUS=$(curl -s -o "$TMP_DIR/copy-read.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/notes/$COPY")
expect_status 200 "$STATUS" "owner reads collected copy" "$TMP_DIR/copy-read.json"
expect_value "my copy body" "$(field data.body < "$TMP_DIR/copy-read.json")" "copy body independent"
STATUS=$(curl -s -o "$TMP_DIR/p1-after.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/notes/$P1")
expect_status 200 "$STATUS" "original unchanged by copy edit" "$TMP_DIR/p1-after.json"
expect_value "updated original body" "$(field data.body < "$TMP_DIR/p1-after.json")" "original body"

STATUS=$(curl -s -o "$TMP_DIR/p1-update-after-copy.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" \
  -d "{\"body\":\"original changed after copy\",\"revision\":$P1_REV}" "$BASE/api/v1/notes/$P1")
expect_status 200 "$STATUS" "original update after copy" "$TMP_DIR/p1-update-after-copy.json"
P1_REV=$(field data.revision < "$TMP_DIR/p1-update-after-copy.json")
STATUS=$(curl -s -o "$TMP_DIR/copy-after-original-update.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/notes/$COPY")
expect_status 200 "$STATUS" "copy read after original update" "$TMP_DIR/copy-after-original-update.json"
expect_value "my copy body" "$(field data.body < "$TMP_DIR/copy-after-original-update.json")" "original update does not overwrite copy"

echo "== Original delete does not delete copy =="
STATUS=$(curl -s -o "$TMP_DIR/p1-delete.json" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $S1_TOKEN" "$BASE/api/v1/notes/$P1")
expect_status 200 "$STATUS" "delete original" "$TMP_DIR/p1-delete.json"
STATUS=$(curl -s -o "$TMP_DIR/copy-after-delete.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/notes/$COPY")
expect_status 200 "$STATUS" "copy survives original delete" "$TMP_DIR/copy-after-delete.json"
STATUS=$(curl -s -o "$TMP_DIR/public-after-delete.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/notes")
expect_status 200 "$STATUS" "public notes after delete" "$TMP_DIR/public-after-delete.json"
expect_value "2" "$(field data.total < "$TMP_DIR/public-after-delete.json")" "deleted original excluded from public"

echo "== Refund loses course access but owner can still manage notes =="
SOURCE_ID=$(db "SELECT es.id FROM bc_learning.course_grant cg JOIN bc_learning.entitlement_source es ON es.id=cg.source_id WHERE cg.user_id=$S1_ID AND cg.course_id=$COURSE_ID LIMIT 1")
db "UPDATE bc_learning.entitlement_source SET state='REVOKED' WHERE id=$SOURCE_ID"
STATUS=$(curl -s -o "$TMP_DIR/s1-my-refund.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/me/notes")
expect_status 200 "$STATUS" "owner lists notes after refund" "$TMP_DIR/s1-my-refund.json"
STATUS=$(curl -s -o "$TMP_DIR/s1-private-refund.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/notes/$P2")
expect_status 200 "$STATUS" "owner reads private note after refund" "$TMP_DIR/s1-private-refund.json"
STATUS=$(curl -s -o "$TMP_DIR/s1-private-edit.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $S1_TOKEN" -d "{\"body\":\"private note after refund\",\"revision\":$P2_REV}" \
  "$BASE/api/v1/notes/$P2")
expect_status 200 "$STATUS" "owner edits note after refund" "$TMP_DIR/s1-private-edit.json"
P2_REV=$(field data.revision < "$TMP_DIR/s1-private-edit.json")
STATUS=$(curl -s -o "$TMP_DIR/s1-other-refund.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/notes/$P4")
expect_status 403 "$STATUS" "refunded student cannot read others public note" "$TMP_DIR/s1-other-refund.json"
STATUS=$(curl -s -o "$TMP_DIR/s1-list-refund.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/notes")
expect_status 403 "$STATUS" "refunded student cannot list community notes" "$TMP_DIR/s1-list-refund.json"
STATUS=$(create_note "$S1_TOKEN" "new note after refund" PRIVATE "$TMP_DIR/s1-new.json")
expect_status 403 "$STATUS" "refunded student cannot create note" "$TMP_DIR/s1-new.json"
STATUS=$(curl -s -o "$TMP_DIR/s1-collect-refund.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $S1_TOKEN" "$BASE/api/v1/notes/$P4/collect")
expect_status 403 "$STATUS" "refunded student cannot collect" "$TMP_DIR/s1-collect-refund.json"
STATUS=$(curl -s -o "$TMP_DIR/s1-private-delete.json" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $S1_TOKEN" "$BASE/api/v1/notes/$P2")
expect_status 200 "$STATUS" "owner deletes note after refund" "$TMP_DIR/s1-private-delete.json"
STATUS=$(curl -s -o "$TMP_DIR/s1-my-deleted.json" -w '%{http_code}' -H "Authorization: Bearer $S1_TOKEN" \
  "$BASE/api/v1/me/notes")
expect_status 200 "$STATUS" "owner list includes deleted note" "$TMP_DIR/s1-my-deleted.json"
expect_value "DELETED" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));const n=d.data.items.find(x=>String(x.id)===process.argv[2]);console.log(n?n.state:"")' "$TMP_DIR/s1-my-deleted.json" "$P2")" "deleted note state in owner list"

echo "== Admin hide note and audit =="
STATUS=$(curl -s -o "$TMP_DIR/hide-note-noreason.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{}' \
  "$BASE/api/v1/admin/content/NOTE/$P3/hide")
expect_status 400 "$STATUS" "note hide requires reason" "$TMP_DIR/hide-note-noreason.json"
STATUS=$(curl -s -o "$TMP_DIR/hide-note.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"reason":"policy"}' \
  "$BASE/api/v1/admin/content/NOTE/$P3/hide")
expect_status 200 "$STATUS" "admin hides note" "$TMP_DIR/hide-note.json"
STATUS=$(curl -s -o "$TMP_DIR/teacher-hidden.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/notes/$P3")
expect_status 404 "$STATUS" "hidden note not public" "$TMP_DIR/teacher-hidden.json"
STATUS=$(curl -s -o "$TMP_DIR/owner-hidden.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/notes/$P3")
expect_status 200 "$STATUS" "owner sees hidden note" "$TMP_DIR/owner-hidden.json"
expect_value "HIDDEN" "$(field data.state < "$TMP_DIR/owner-hidden.json")" "hidden state"
STATUS=$(curl -s -o "$TMP_DIR/public-after-hide.json" -w '%{http_code}' -H "Authorization: Bearer $S2_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_ID/notes")
expect_status 200 "$STATUS" "public list after hide" "$TMP_DIR/public-after-hide.json"
expect_value "1" "$(field data.total < "$TMP_DIR/public-after-hide.json")" "hidden note excluded"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_interaction.moderation_action WHERE action='HIDE' AND target_type='NOTE' AND target_id=$P3")" "note hide audit"

echo "ALL S13-B ACCEPTANCE CHECKS PASSED"