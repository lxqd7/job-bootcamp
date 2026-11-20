#!/usr/bin/env bash
# Requires RabbitMQ and services started with OUTBOX_TRANSPORT=rabbit OUTBOX_RELAY_ENABLED=true.
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
COURSE_BASE="${COURSE_BASE:-http://127.0.0.1:18082}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
TMP_DIR="$(mktemp -d)"
STAMP="$(date +%s)"
USERNAME="s05b${STAMP}"

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

field() {
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(0,"utf8"));
    let v=d; for (const k of process.argv[1].split(".")) v=v==null?undefined:v[k];
    if (Array.isArray(v)) console.log(JSON.stringify(v)); else console.log(v==null?"":v);
  ' "$1"
}

expect_status() {
  local expected="$1" actual="$2" label="$3" body="$4"
  if [ "$actual" = "$expected" ]; then echo "PASS  $label  (HTTP $actual)";
  else echo "FAIL  $label expected $expected got $actual"; cat "$body" 2>/dev/null || true; exit 1; fi
}

expect_value() {
  local expected="$1" actual="$2" label="$3"
  if [ "$expected" = "$actual" ]; then echo "PASS  $label  ($actual)";
  else echo "FAIL  $label expected '$expected' got '$actual'"; exit 1; fi
}

login() {
  local username="$1" app="$2" out="$3"
  local status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"$app\"}" "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
}

wait_claim() {
  local token="$1" claim_id="$2" out="$3"
  local status body
  for i in $(seq 1 20); do
    status=$(curl -s -o "$out" -w '%{http_code}' -H "Authorization: Bearer $token" "$BASE/api/v1/claims/$claim_id")
    body=$(field data.status < "$out")
    if [ "$status" = "200" ] && [ "$body" = "FULFILLED" ]; then
      echo "PASS  claim $claim_id fulfilled by event chain"
      return 0
    fi
    sleep 0.5
  done
  echo "FAIL  claim $claim_id not fulfilled"; cat "$out"; exit 1
}

echo "== Login and create video course =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN=$(field data.accessToken < "$TMP_DIR/admin.json")

CREATE=$(curl -s -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN" \
  -d '{"title":"S05B Progress '"$STAMP"'","description":"progress and completion","categoryId":3,"priceCent":0,"isFree":true,"coverUrl":null}' \
  "$BASE/api/v1/admin/courses")
COURSE=$(field data.id <<< "$CREATE")
CHAPTER=$(curl -s -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN" \
  -d '{"title":"Chapter","sort":1}' "$BASE/api/v1/admin/courses/$COURSE/chapters")
CHAPTER_ID=$(field data.id <<< "$CHAPTER")
LESSON_IDS=()
for i in 1 2 3 4 5 6; do
  BODY=$(curl -s -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN" \
    -d "{\"title\":\"Video $i\",\"sort\":$i,\"mediaId\":1}" \
    "$BASE/api/v1/admin/chapters/$CHAPTER_ID/lessons")
  LESSON_IDS+=("$(field data.id <<< "$BODY")")
done
PUBLISH=$(curl -s -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN" \
  -d '{"revision":0}' "$BASE/api/v1/admin/courses/$COURSE/publish")
expect_value "ONLINE" "$(field data.status <<< "$PUBLISH")" "video course published"
LESSON_ID="${LESSON_IDS[0]}"
echo "course=$COURSE lesson=$LESSON_ID"

REGISTER=$(curl -s -o "$TMP_DIR/register.json" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$DEMO_PASSWORD\",\"nickname\":\"S05B User\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$REGISTER" "register test student" "$TMP_DIR/register.json"
login "$USERNAME" STUDENT "$TMP_DIR/student.json"
STUDENT=$(field data.accessToken < "$TMP_DIR/student.json")

echo
echo "== Entitlement and playback authorization =="
NO_ACCESS=$(curl -s -o "$TMP_DIR/no-access.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $STUDENT" "$BASE/api/v1/lessons/$LESSON_ID/play-authorizations")
expect_status 403 "$NO_ACCESS" "play authorization requires course entitlement" "$TMP_DIR/no-access.json"

CLAIM=$(curl -s -X POST -H "Authorization: Bearer $STUDENT" "$BASE/api/v1/courses/$COURSE/claim")
CLAIM_ID=$(field data.claimId <<< "$CLAIM")
CLAIM_STATUS_FILE="$TMP_DIR/claim-status.json"
wait_claim "$STUDENT" "$CLAIM_ID" "$CLAIM_STATUS_FILE"

AUTH=$(curl -s -X POST -H "Authorization: Bearer $STUDENT" "$BASE/api/v1/lessons/$LESSON_ID/play-authorizations")
PLAY_SESSION=$(field data.playSessionId <<< "$AUTH")
DURATION=$(field data.durationMs <<< "$AUTH")
DEMO_URL=$(field data.demoUrl <<< "$AUTH")
expect_value "5055" "$DURATION" "server-side duration is used"
if [ -z "$PLAY_SESSION" ] || [ -z "$DEMO_URL" ]; then echo "FAIL  play authorization missing fields"; exit 1; fi
VIDEO_STATUS=$(curl -s -o "$TMP_DIR/video.bin" -w '%{http_code}' "$COURSE_BASE$DEMO_URL")
expect_status 200 "$VIDEO_STATUS" "signed DEMO URL is playable" /dev/null
expect_value "1128375" "$(wc -c < "$TMP_DIR/video.bin")" "signed DEMO video size"

echo
echo "== 50 percent boundary, completion and rewind =="
HALF=$(curl -s -X PUT -H 'Content-Type: application/json' -H "Authorization: Bearer $STUDENT" \
  -d "{\"positionMs\":2527,\"playSessionId\":\"$PLAY_SESSION\"}" \
  "$BASE/api/v1/learning/lessons/$LESSON_ID/progress")
expect_value "false" "$(field data.completed <<< "$HALF")" "exactly 50 percent is not complete"
OVER=$(curl -s -X PUT -H 'Content-Type: application/json' -H "Authorization: Bearer $STUDENT" \
  -d "{\"positionMs\":2528,\"playSessionId\":\"$PLAY_SESSION\"}" \
  "$BASE/api/v1/learning/lessons/$LESSON_ID/progress")
expect_value "true" "$(field data.completed <<< "$OVER")" "above 50 percent completes"
REWIND=$(curl -s -X PUT -H 'Content-Type: application/json' -H "Authorization: Bearer $STUDENT" \
  -d "{\"positionMs\":1000,\"playSessionId\":\"$PLAY_SESSION\"}" \
  "$BASE/api/v1/learning/lessons/$LESSON_ID/progress")
expect_value "true" "$(field data.completed <<< "$REWIND")" "rewind does not clear completion"

for i in 2 3 4 5 6; do
  LID="${LESSON_IDS[$((i-1))]}"
  A=$(curl -s -X POST -H "Authorization: Bearer $STUDENT" "$BASE/api/v1/lessons/$LID/play-authorizations")
  PS=$(field data.playSessionId <<< "$A")
  curl -s -X PUT -H 'Content-Type: application/json' -H "Authorization: Bearer $STUDENT" \
    -d "{\"positionMs\":5055,\"playSessionId\":\"$PS\"}" \
    "$BASE/api/v1/learning/lessons/$LID/progress" > "$TMP_DIR/progress-$i.json"
done

sleep 2
SUMMARY=""
for i in $(seq 1 20); do
  SUMMARY=$(curl -s -H "Authorization: Bearer $STUDENT" "$BASE/api/v1/me/points")
  [ "$(field data.available <<< "$SUMMARY")" = "50" ] && break
  sleep 0.5
done
expect_value "50" "$(field data.available <<< "$SUMMARY")" "six first completions cap at 50 points"
LEDGER=$(curl -s -H "Authorization: Bearer $STUDENT" "$BASE/api/v1/me/points/ledger?page=1&pageSize=20")
VIDEO_ROWS=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(0,"utf8"));
  console.log(d.data.items.filter(i=>i.type==="VIDEO").length);
' <<< "$LEDGER")
expect_value "6" "$VIDEO_ROWS" "six completion facts produce six video ledger rows"
SKIPPED=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(0,"utf8"));
  console.log(d.data.items.filter(i=>i.resultCode==="SKIPPED_DAILY_CAP").length);
' <<< "$LEDGER")
expect_value "1" "$SKIPPED" "sixth completion is recorded as daily-cap skipped"

echo
echo "== Play session ownership =="
login student01 STUDENT "$TMP_DIR/student01.json"
STUDENT1=$(field data.accessToken < "$TMP_DIR/student01.json")
OTHER=$(curl -s -o "$TMP_DIR/other-session.json" -w '%{http_code}' -X PUT \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $STUDENT1" \
  -d "{\"positionMs\":2528,\"playSessionId\":\"$PLAY_SESSION\"}" \
  "$BASE/api/v1/learning/lessons/$LESSON_ID/progress")
expect_status 403 "$OTHER" "another student cannot use the play session" "$TMP_DIR/other-session.json"

echo
echo "ALL S05-b PLAYBACK AND PROGRESS CHECKS PASSED"