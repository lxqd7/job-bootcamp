#!/usr/bin/env bash
# Requires RabbitMQ and services started with OUTBOX_TRANSPORT=rabbit OUTBOX_RELAY_ENABLED=true.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
TMP_DIR="$(mktemp -d)"
STAMP="$(date +%s)"

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

field() {
  node -e '
    const fs = require("fs");
    const data = JSON.parse(fs.readFileSync(0, "utf8"));
    let value = data;
    for (const key of process.argv[1].split(".")) {
      value = value == null ? undefined : value[key];
    }
    if (Array.isArray(value)) { console.log(JSON.stringify(value)); }
    else { console.log(value == null ? "" : value); }
  ' "$1"
}

expect_status() {
  local expected="$1" actual="$2" label="$3" body="$4"
  if [ "$actual" = "$expected" ]; then
    echo "PASS  $label  (HTTP $actual)"
  else
    echo "FAIL  $label  expected HTTP $expected, got $actual"
    echo "--- response ---"; cat "$body" 2>/dev/null || true; echo
    exit 1
  fi
}

expect_value() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then
    echo "PASS  $label  ($actual)"
  else
    echo "FAIL  $label  expected '$expected', got '$actual'"
    exit 1
  fi
}

login() {
  local username="$1" app="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
}

create_free_course() {
  local title="$1" out="$2"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S05A free course\",\"categoryId\":3,\"priceCent\":0,\"isFree\":true,\"coverUrl\":null}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create free course $title" "$out"
  CREATED_COURSE=$(field data.id < "$out")
}

publish_course() {
  local course_id="$1" out="$2"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d '{"revision":0}' "$BASE/api/v1/admin/courses/$course_id/publish")
  expect_status 200 "$status" "publish free course $course_id" "$out"
  PUBLISH_REVISION=$(field data.revision < "$out")
}

update_paid() {
  local course_id="$1" revision="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -X PATCH \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"S05A Paid $course_id\",\"description\":\"S05A paid\",\"categoryId\":3,\"priceCent\":990,\"isFree\":false,\"coverUrl\":null,\"revision\":$revision}" \
    "$BASE/api/v1/admin/courses/$course_id")
  expect_status 200 "$status" "convert course $course_id to paid" "$out"
}

me_courses() {
  local token="$1" out="$2"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H "Authorization: Bearer $token" "$BASE/api/v1/me/courses?page=1&pageSize=100")
  expect_status 200 "$status" "my courses" "$out"
}

course_present() {
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    console.log(d.data.items.some(i=>i.courseId===process.argv[2]) ? "yes" : "no");
  ' "$1" "$2"
}

wait_claim() {
  local token="$1" claim_id="$2" out="$3"
  local status
  for i in $(seq 1 20); do
    status=$(curl -s -o "$out" -w '%{http_code}' -H "Authorization: Bearer $token" "$BASE/api/v1/claims/$claim_id")
    if [ "$status" = "200" ] && [ "$(field data.status < "$out")" = "FULFILLED" ]; then
      echo "PASS  claim $claim_id becomes FULFILLED after event delivery"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  claim $claim_id did not become FULFILLED"
  cat "$out" 2>/dev/null || true
  exit 1
}

echo "== Login and basic access =="
login student01 STUDENT "$TMP_DIR/student01.json"
login student02 STUDENT "$TMP_DIR/student02.json"
login student03 STUDENT "$TMP_DIR/student03.json"
login teacher01 ADMIN "$TMP_DIR/teacher01.json"
login admin01 ADMIN "$TMP_DIR/admin01.json"
STUDENT1=$(field data.accessToken < "$TMP_DIR/student01.json")
STUDENT2=$(field data.accessToken < "$TMP_DIR/student02.json")
STUDENT3=$(field data.accessToken < "$TMP_DIR/student03.json")
TEACHER=$(field data.accessToken < "$TMP_DIR/teacher01.json")
ADMIN_TOKEN=$(field data.accessToken < "$TMP_DIR/admin01.json")

TEACHER_BODY="$TMP_DIR/teacher-courses.json"
TEACHER_STATUS=$(curl -s -o "$TEACHER_BODY" -w '%{http_code}' -H "Authorization: Bearer $TEACHER" "$BASE/api/v1/me/courses")
expect_status 403 "$TEACHER_STATUS" "teacher cannot read student entitlements" "$TEACHER_BODY"
NO_TOKEN_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/me/courses")
expect_status 401 "$NO_TOKEN_STATUS" "entitlements require login" /dev/null

echo
echo "== Free claim idempotency =="
CLAIM_BODY="$TMP_DIR/claim-free.json"
CLAIM_STATUS=$(curl -s -o "$CLAIM_BODY" -w '%{http_code}' -X POST -H "Authorization: Bearer $STUDENT1" "$BASE/api/v1/courses/1/claim")
expect_status 200 "$CLAIM_STATUS" "claim online free course" "$CLAIM_BODY"
CLAIM_ID=$(field data.claimId < "$CLAIM_BODY")
CLAIM_STATUS_BODY="$TMP_DIR/claim-free-status.json"
curl -s -o "$CLAIM_STATUS_BODY" -H "Authorization: Bearer $STUDENT1" "$BASE/api/v1/claims/$CLAIM_ID"
if [ "$(field data.status < "$CLAIM_STATUS_BODY")" = "PROCESSING" ]; then
  wait_claim "$STUDENT1" "$CLAIM_ID" "$CLAIM_STATUS_BODY"
fi
SOURCE_ID=$(field data.sourceId < "$CLAIM_STATUS_BODY")
expect_value "FULFILLED" "$(field data.status < "$CLAIM_STATUS_BODY")" "free claim is fulfilled by event chain"
if [ -z "$SOURCE_ID" ]; then echo "FAIL  sourceId missing"; exit 1; fi

CLAIM_AGAIN="$TMP_DIR/claim-again.json"
CLAIM_AGAIN_STATUS=$(curl -s -o "$CLAIM_AGAIN" -w '%{http_code}' -X POST -H "Authorization: Bearer $STUDENT1" "$BASE/api/v1/courses/1/claim")
expect_status 200 "$CLAIM_AGAIN_STATUS" "repeat claim is idempotent" "$CLAIM_AGAIN"
expect_value "$CLAIM_ID" "$(field data.claimId < "$CLAIM_AGAIN")" "repeat claim returns same claimId"
expect_value "$SOURCE_ID" "$(field data.sourceId < "$CLAIM_AGAIN")" "repeat claim returns same sourceId"

STATUS_BODY="$TMP_DIR/claim-status.json"
STATUS_CODE=$(curl -s -o "$STATUS_BODY" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1" "$BASE/api/v1/claims/$CLAIM_ID")
expect_status 200 "$STATUS_CODE" "claim owner can read status" "$STATUS_BODY"
expect_value "FULFILLED" "$(field data.status < "$STATUS_BODY")" "claim status is FULFILLED"

OTHER_BODY="$TMP_DIR/claim-other.json"
OTHER_STATUS=$(curl -s -o "$OTHER_BODY" -w '%{http_code}' -H "Authorization: Bearer $STUDENT2" "$BASE/api/v1/claims/$CLAIM_ID")
expect_status 404 "$OTHER_STATUS" "another student cannot read claim" "$OTHER_BODY"

PAID_CLAIM_BODY="$TMP_DIR/claim-paid.json"
PAID_CLAIM_STATUS=$(curl -s -o "$PAID_CLAIM_BODY" -w '%{http_code}' -X POST -H "Authorization: Bearer $STUDENT1" "$BASE/api/v1/courses/5/claim")
expect_status 400 "$PAID_CLAIM_STATUS" "paid course cannot be claimed free" "$PAID_CLAIM_BODY"

ME_BODY="$TMP_DIR/me-courses.json"
me_courses "$STUDENT1" "$ME_BODY"
expect_value "yes" "$(course_present "$ME_BODY" "1")" "free claim appears in my courses"
expect_value "FREE" "$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  const item=d.data.items.find(i=>i.courseId==="1"); console.log(item?item.sourceType:"");
' "$ME_BODY")" "my course source type is FREE"

ROUTES_BODY="$TMP_DIR/me-routes.json"
ROUTES_STATUS=$(curl -s -o "$ROUTES_BODY" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1" "$BASE/api/v1/me/routes")
expect_status 200 "$ROUTES_STATUS" "my routes query" "$ROUTES_BODY"
expect_value "0" "$(field data.total < "$ROUTES_BODY")" "no route entitlement before route purchase"

echo
echo "== Free-to-paid keeps existing entitlement =="
create_free_course "S05A Keep $STAMP" "$TMP_DIR/keep-create.json"
KEEP_COURSE="$CREATED_COURSE"
publish_course "$KEEP_COURSE" "$TMP_DIR/keep-publish.json"
KEEP_REVISION="$PUBLISH_REVISION"

KEEP_CLAIM="$TMP_DIR/keep-claim.json"
KEEP_CLAIM_STATUS=$(curl -s -o "$KEEP_CLAIM" -w '%{http_code}' -X POST -H "Authorization: Bearer $STUDENT3" "$BASE/api/v1/courses/$KEEP_COURSE/claim")
expect_status 200 "$KEEP_CLAIM_STATUS" "claim newly published free course" "$KEEP_CLAIM"
KEEP_CLAIM_ID=$(field data.claimId < "$KEEP_CLAIM")
KEEP_STATUS_FILE="$TMP_DIR/keep-claim-status.json"
curl -s -o "$KEEP_STATUS_FILE" -H "Authorization: Bearer $STUDENT3" "$BASE/api/v1/claims/$KEEP_CLAIM_ID"
if [ "$(field data.status < "$KEEP_STATUS_FILE")" = "PROCESSING" ]; then
  wait_claim "$STUDENT3" "$KEEP_CLAIM_ID" "$KEEP_STATUS_FILE"
fi
KEEP_SOURCE=$(field data.sourceId < "$KEEP_STATUS_FILE")
expect_value "yes" "$([ -n "$KEEP_SOURCE" ] && echo yes || echo no)" "new free course is granted by event chain"

update_paid "$KEEP_COURSE" "$KEEP_REVISION" "$TMP_DIR/keep-paid.json"
KEEP_ME="$TMP_DIR/keep-me.json"
me_courses "$STUDENT3" "$KEEP_ME"
expect_value "yes" "$(course_present "$KEEP_ME" "$KEEP_COURSE")" "claimed course remains after price change"

KEEP_NEW_CLAIM="$TMP_DIR/keep-new-claim.json"
KEEP_NEW_STATUS=$(curl -s -o "$KEEP_NEW_CLAIM" -w '%{http_code}' -X POST -H "Authorization: Bearer $STUDENT2" "$BASE/api/v1/courses/$KEEP_COURSE/claim")
expect_status 400 "$KEEP_NEW_STATUS" "student who did not claim before price change is rejected" "$KEEP_NEW_CLAIM"

echo
echo "== Concurrent claim and price change =="
create_free_course "S05A Race $STAMP" "$TMP_DIR/race-create.json"
RACE_COURSE="$CREATED_COURSE"
publish_course "$RACE_COURSE" "$TMP_DIR/race-publish.json"
RACE_REVISION="$PUBLISH_REVISION"

curl -s -o "$TMP_DIR/race-claim.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $STUDENT1" "$BASE/api/v1/courses/$RACE_COURSE/claim" > "$TMP_DIR/race-claim.status" &
CLAIM_PID=$!
curl -s -o "$TMP_DIR/race-update.json" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"title\":\"S05A Race Paid $RACE_COURSE\",\"description\":\"S05A race paid\",\"categoryId\":3,\"priceCent\":990,\"isFree\":false,\"coverUrl\":null,\"revision\":$RACE_REVISION}" \
  "$BASE/api/v1/admin/courses/$RACE_COURSE" > "$TMP_DIR/race-update.status" &
UPDATE_PID=$!
wait $CLAIM_PID
wait $UPDATE_PID
RACE_CLAIM_STATUS=$(cat "$TMP_DIR/race-claim.status")
RACE_UPDATE_STATUS=$(cat "$TMP_DIR/race-update.status")
echo "claim status=$RACE_CLAIM_STATUS update status=$RACE_UPDATE_STATUS"
if [ "$RACE_CLAIM_STATUS" = "200" ]; then
  RACE_CLAIM_ID=$(field data.claimId < "$TMP_DIR/race-claim.json")
  wait_claim "$STUDENT1" "$RACE_CLAIM_ID" "$TMP_DIR/race-claim-status.json"
fi
RACE_ME="$TMP_DIR/race-me.json"
me_courses "$STUDENT1" "$RACE_ME"
RACE_PRESENT=$(course_present "$RACE_ME" "$RACE_COURSE")
if [ "$RACE_CLAIM_STATUS" = "200" ]; then
  expect_value "yes" "$RACE_PRESENT" "claim accepted before/despite price change has a source"
else
  expect_value "no" "$RACE_PRESENT" "rejected race claim leaves no entitlement"
fi
if [ "$RACE_UPDATE_STATUS" = "200" ]; then
  echo "PASS  concurrent price update completed"
elif [ "$RACE_UPDATE_STATUS" = "409" ]; then
  echo "PASS  concurrent price update retried as revision conflict"
else
  echo "FAIL  unexpected concurrent price update status $RACE_UPDATE_STATUS"
  cat "$TMP_DIR/race-update.json"; exit 1
fi

echo
echo "ALL S05-a ENTITLEMENT CHECKS PASSED"