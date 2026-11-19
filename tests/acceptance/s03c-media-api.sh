#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
COURSE_BASE="${COURSE_BASE:-http://127.0.0.1:18082}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
DEMO_ROOT="${DEMO_ROOT:-media/demo}"
TMP_DIR="$(mktemp -d)"
STAMP="$(date +%s)"

cleanup() {
  if [ -n "${TEMP_DEMO_FILE:-}" ]; then
    rm -f "$TEMP_DEMO_FILE"
  fi
  rm -rf "$TMP_DIR"
}
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

find_category() {
  node -e '
    const fs = require("fs");
    const data = JSON.parse(fs.readFileSync(0, "utf8"));
    function find(nodes, name) {
      for (const node of nodes || []) {
        if (node.name === name) return node;
        const found = find(node.children, name);
        if (found) return found;
      }
      return null;
    }
    const found = find(data.data, process.argv[1]);
    console.log(found ? found.id : "");
  ' "$1"
}

expect_status() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  local body="$4"
  if [ "$actual" = "$expected" ]; then
    echo "PASS  $label  (HTTP $actual)"
  else
    echo "FAIL  $label  expected HTTP $expected, got $actual"
    echo "--- response ---"
    cat "$body" 2>/dev/null || true
    echo
    exit 1
  fi
}

echo "== Login =="
ADMIN_JAR="$TMP_DIR/admin.jar"
ADMIN_LOGIN="$TMP_DIR/admin-login.json"
ADMIN_STATUS=$(curl -s -o "$ADMIN_LOGIN" -c "$ADMIN_JAR" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"ADMIN\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$ADMIN_STATUS" "admin login" "$ADMIN_LOGIN"
ADMIN_ACCESS=$(field data.accessToken < "$ADMIN_LOGIN")

echo
echo "== DEMO import and verify =="
DEMO_BODY="$TMP_DIR/demo-import.json"
DEMO_STATUS=$(curl -s -o "$DEMO_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d '{"relativePath":"flower.mp4","title":"DEMO flower"}' \
  "$BASE/api/v1/admin/media/demo-import")
expect_status 200 "$DEMO_STATUS" "import authorized DEMO media" "$DEMO_BODY"
DEMO_ID=$(field data.id < "$DEMO_BODY")
DEMO_DURATION=$(field data.durationMs < "$DEMO_BODY")
DEMO_STATUS_VALUE=$(field data.status < "$DEMO_BODY")
if [ "$DEMO_STATUS_VALUE" = "AVAILABLE" ] && [ "$DEMO_DURATION" -gt 5000 ]; then
  echo "PASS  DEMO media is AVAILABLE with parsed duration ${DEMO_DURATION}ms"
else
  echo "FAIL  DEMO media status/duration invalid: $DEMO_STATUS_VALUE / $DEMO_DURATION"
  exit 1
fi

DUP_BODY="$TMP_DIR/demo-dup.json"
DUP_STATUS=$(curl -s -o "$DUP_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d '{"relativePath":"flower.mp4","title":"DEMO flower"}' \
  "$BASE/api/v1/admin/media/demo-import")
expect_status 200 "$DUP_STATUS" "duplicate DEMO import is idempotent" "$DUP_BODY"
DUP_ID=$(field data.id < "$DUP_BODY")
if [ "$DUP_ID" = "$DEMO_ID" ]; then
  echo "PASS  duplicate DEMO import returned the same mediaId"
else
  echo "FAIL  duplicate DEMO import created a new mediaId"
  exit 1
fi

TRAVERSAL_BODY="$TMP_DIR/demo-traversal.json"
TRAVERSAL_STATUS=$(curl -s -o "$TRAVERSAL_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d '{"relativePath":"../../pom.xml","title":"escape"}' \
  "$BASE/api/v1/admin/media/demo-import")
expect_status 400 "$TRAVERSAL_STATUS" "DEMO import rejects path traversal" "$TRAVERSAL_BODY"

VERIFY_BODY="$TMP_DIR/verify.json"
VERIFY_STATUS=$(curl -s -o "$VERIFY_BODY" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media/$DEMO_ID/verify")
expect_status 200 "$VERIFY_STATUS" "verify DEMO media" "$VERIFY_BODY"
VERIFY_OPERATION=$(field data.operationKey < "$VERIFY_BODY")
OPERATION_BODY="$TMP_DIR/operation.json"
OPERATION_STATUS=$(curl -s -o "$OPERATION_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media/operations/$VERIFY_OPERATION")
expect_status 200 "$OPERATION_STATUS" "read verify operation" "$OPERATION_BODY"
OPERATION_STATE=$(field data.state < "$OPERATION_BODY")
if [ "$OPERATION_STATE" = "SUCCESS" ]; then
  echo "PASS  verify operation succeeded"
else
  echo "FAIL  verify operation state $OPERATION_STATE"
  exit 1
fi

echo
echo "== DEMO preview and Range =="
PREVIEW_BODY="$TMP_DIR/preview.json"
PREVIEW_STATUS=$(curl -s -o "$PREVIEW_BODY" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media/$DEMO_ID/preview-authorizations")
expect_status 200 "$PREVIEW_STATUS" "admin preview authorization" "$PREVIEW_BODY"
DEMO_URL=$(field data.demoUrl < "$PREVIEW_BODY")
PLAY_SESSION=$(field data.playSessionId < "$PREVIEW_BODY")
if [ -z "$DEMO_URL" ]; then echo "FAIL  no demoUrl"; exit 1; fi

FULL_BODY="$TMP_DIR/video-full.bin"
FULL_HEADERS="$TMP_DIR/video-full.headers"
FULL_STATUS=$(curl -s -D "$FULL_HEADERS" -o "$FULL_BODY" -w '%{http_code}' "$COURSE_BASE$DEMO_URL")
expect_status 200 "$FULL_STATUS" "full DEMO media download" /dev/null
if [ -s "$FULL_BODY" ]; then echo "PASS  DEMO media body is not empty ($(wc -c < "$FULL_BODY") bytes)"; else echo "FAIL empty body"; exit 1; fi
if grep -qi '^Content-Type: video/mp4' "$FULL_HEADERS"; then echo "PASS  DEMO media Content-Type video/mp4"; else echo "FAIL missing video content type"; cat "$FULL_HEADERS"; exit 1; fi

RANGE_BODY="$TMP_DIR/video-range.bin"
RANGE_HEADERS="$TMP_DIR/video-range.headers"
RANGE_STATUS=$(curl -s -D "$RANGE_HEADERS" -o "$RANGE_BODY" -w '%{http_code}' \
  -H 'Range: bytes=0-1023' "$COURSE_BASE$DEMO_URL")
expect_status 206 "$RANGE_STATUS" "DEMO media Range request" /dev/null
if [ "$(wc -c < "$RANGE_BODY")" -eq 1024 ]; then echo "PASS  Range returned 1024 bytes"; else echo "FAIL Range body size"; exit 1; fi
if grep -qi '^Content-Range: bytes 0-1023/' "$RANGE_HEADERS"; then echo "PASS  Content-Range header present"; else echo "FAIL missing Content-Range"; cat "$RANGE_HEADERS"; exit 1; fi

BAD_URL="${DEMO_URL%&sig=*}&sig=bad"
BAD_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$COURSE_BASE$BAD_URL")
expect_status 403 "$BAD_STATUS" "invalid DEMO signature rejected" /dev/null

EMPTY_SESSION_URL="${DEMO_URL/psid=$PLAY_SESSION/psid=00000000-0000-0000-0000-000000000000}"
EMPTY_SESSION_STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$COURSE_BASE$EMPTY_SESSION_URL")
expect_status 403 "$EMPTY_SESSION_STATUS" "missing play session rejected" /dev/null

echo
echo "== VOD preview/import (real VOD NOT_RUN) =="
BATCH_ID="s03c-$STAMP"
VOD_PREVIEW_BODY="$TMP_DIR/vod-preview.json"
VOD_PREVIEW_STATUS=$(curl -s -o "$VOD_PREVIEW_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"batchId\":\"$BATCH_ID\",\"items\":[{\"oldId\":\"old-$STAMP\",\"appId\":\"1250000000\",\"fileId\":\"file-$STAMP\",\"courseMapping\":null}]}" \
  "$BASE/api/v1/admin/media/import-preview")
expect_status 200 "$VOD_PREVIEW_STATUS" "VOD import preview" "$VOD_PREVIEW_BODY"
VOD_HASH=$(field data.inputHash < "$VOD_PREVIEW_BODY")

VOD_IMPORT_BODY="$TMP_DIR/vod-import.json"
VOD_IMPORT_STATUS=$(curl -s -o "$VOD_IMPORT_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"batchId\":\"$BATCH_ID\",\"inputHash\":\"$VOD_HASH\",\"selectedOldIds\":[\"old-$STAMP\"]}" \
  "$BASE/api/v1/admin/media/import")
expect_status 200 "$VOD_IMPORT_STATUS" "VOD import without credentials marks ERROR" "$VOD_IMPORT_BODY"
VOD_MEDIA_ID=$(field data.items.0.mediaId < "$VOD_IMPORT_BODY")
VOD_MEDIA_STATUS=$(field data.items.0.status < "$VOD_IMPORT_BODY")
if [ "$VOD_MEDIA_STATUS" = "ERROR" ]; then
  echo "PASS  VOD asset is ERROR without credentials (mediaId $VOD_MEDIA_ID)"
else
  echo "FAIL  expected VOD ERROR, got $VOD_MEDIA_STATUS"
  exit 1
fi

VOD_IMPORT_AGAIN_BODY="$TMP_DIR/vod-import-again.json"
VOD_IMPORT_AGAIN_STATUS=$(curl -s -o "$VOD_IMPORT_AGAIN_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"batchId\":\"$BATCH_ID\",\"inputHash\":\"$VOD_HASH\",\"selectedOldIds\":[\"old-$STAMP\"]}" \
  "$BASE/api/v1/admin/media/import")
expect_status 200 "$VOD_IMPORT_AGAIN_STATUS" "repeat VOD import is idempotent" "$VOD_IMPORT_AGAIN_BODY"
AGAIN_MEDIA_ID=$(field data.items.0.mediaId < "$VOD_IMPORT_AGAIN_BODY")
if [ "$AGAIN_MEDIA_ID" = "$VOD_MEDIA_ID" ]; then
  echo "PASS  repeat VOD import reused the same mediaId"
else
  echo "FAIL  repeat VOD import created a new mediaId"
  exit 1
fi

VOD_PREVIEW_AUTH_BODY="$TMP_DIR/vod-preview-auth.json"
VOD_PREVIEW_AUTH_STATUS=$(curl -s -o "$VOD_PREVIEW_AUTH_BODY" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media/$VOD_MEDIA_ID/preview-authorizations")
expect_status 400 "$VOD_PREVIEW_AUTH_STATUS" "unconfigured VOD asset is not previewable" "$VOD_PREVIEW_AUTH_BODY"

echo
echo "== Paid course publish requires verified media =="
TREE_BODY="$TMP_DIR/tree.json"
TREE_STATUS=$(curl -s -o "$TREE_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/categories")
expect_status 200 "$TREE_STATUS" "admin category tree" "$TREE_BODY"
JAVA_ID=$(find_category "Java" < "$TREE_BODY")

create_paid_course() {
  local title="$1"
  local media_id="$2"
  local body="$TMP_DIR/$title-create.json"
  local status
  status=$(curl -s -o "$body" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
    -d "{\"title\":\"$title\",\"description\":\"S03C\",\"categoryId\":$JAVA_ID,\"priceCent\":990,\"isFree\":false,\"coverUrl\":null}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create paid course $title" "$body"
  local course_id
  course_id=$(field data.id < "$body")
  local chapter_body="$TMP_DIR/$title-chapter.json"
  status=$(curl -s -o "$chapter_body" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
    -d '{"title":"Chapter","sort":1}' "$BASE/api/v1/admin/courses/$course_id/chapters")
  expect_status 200 "$status" "create chapter $title" "$chapter_body"
  local chapter_id
  chapter_id=$(field data.id < "$chapter_body")
  status=$(curl -s -o /dev/null -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
    -d "{\"title\":\"Lesson\",\"sort\":1,\"mediaId\":$media_id}" \
    "$BASE/api/v1/admin/chapters/$chapter_id/lessons")
  expect_status 200 "$status" "create lesson with media $media_id" /dev/null
  CREATED_COURSE_ID="$course_id"
}

create_paid_course "S03C Verified $STAMP" "$DEMO_ID"
VERIFIED_COURSE="$CREATED_COURSE_ID"
PUBLISH_OK_BODY="$TMP_DIR/publish-verified.json"
PUBLISH_OK_STATUS=$(curl -s -o "$PUBLISH_OK_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d '{"revision":0}' "$BASE/api/v1/admin/courses/$VERIFIED_COURSE/publish")
expect_status 200 "$PUBLISH_OK_STATUS" "paid course publishes with verified DEMO media" "$PUBLISH_OK_BODY"

create_paid_course "S03C Placeholder $STAMP" "999"
PLACEHOLDER_COURSE="$CREATED_COURSE_ID"
PUBLISH_BAD_BODY="$TMP_DIR/publish-placeholder.json"
PUBLISH_BAD_STATUS=$(curl -s -o "$PUBLISH_BAD_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d '{"revision":0}' "$BASE/api/v1/admin/courses/$PLACEHOLDER_COURSE/publish")
expect_status 400 "$PUBLISH_BAD_STATUS" "placeholder media cannot publish paid course" "$PUBLISH_BAD_BODY"

echo
echo "== Access control, detach and missing media =="
STUDENT_JAR="$TMP_DIR/student.jar"
STUDENT_LOGIN="$TMP_DIR/student-login.json"
STUDENT_STATUS=$(curl -s -o "$STUDENT_LOGIN" -c "$STUDENT_JAR" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"student01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$STUDENT_STATUS" "student login" "$STUDENT_LOGIN"
STUDENT_ACCESS=$(field data.accessToken < "$STUDENT_LOGIN")
STUDENT_MEDIA_BODY="$TMP_DIR/student-media.json"
STUDENT_MEDIA_STATUS=$(curl -s -o "$STUDENT_MEDIA_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $STUDENT_ACCESS" "$BASE/api/v1/admin/media")
expect_status 403 "$STUDENT_MEDIA_STATUS" "student cannot access media admin" "$STUDENT_MEDIA_BODY"

TEMP_NAME="s03c-temp-$STAMP.mp4"
TEMP_DEMO_FILE="$DEMO_ROOT/$TEMP_NAME"
cp "$DEMO_ROOT/flower.mp4" "$TEMP_DEMO_FILE"
TEMP_IMPORT_BODY="$TMP_DIR/temp-import.json"
TEMP_IMPORT_STATUS=$(curl -s -o "$TEMP_IMPORT_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"relativePath\":\"$TEMP_NAME\",\"title\":\"S03C temp $STAMP\"}" \
  "$BASE/api/v1/admin/media/demo-import")
expect_status 200 "$TEMP_IMPORT_STATUS" "import temporary DEMO media" "$TEMP_IMPORT_BODY"
TEMP_ID=$(field data.id < "$TEMP_IMPORT_BODY")

TEMP_VERIFY_BODY="$TMP_DIR/temp-verify.json"
TEMP_VERIFY_STATUS=$(curl -s -o "$TEMP_VERIFY_BODY" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media/$TEMP_ID/verify")
expect_status 200 "$TEMP_VERIFY_STATUS" "verify temporary DEMO media" "$TEMP_VERIFY_BODY"

LIST_BODY="$TMP_DIR/media-list.json"
LIST_STATUS=$(curl -s -o "$LIST_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media?page=1&pageSize=100")
expect_status 200 "$LIST_STATUS" "admin media list" "$LIST_BODY"
TEMP_REVISION=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  const item=d.data.items.find(i=>i.id===process.argv[2]); console.log(item?item.revision:"");
' "$LIST_BODY" "$TEMP_ID")

DETACH_BODY="$TMP_DIR/detach.json"
DETACH_STATUS=$(curl -s -o "$DETACH_BODY" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media/$TEMP_ID?revision=$TEMP_REVISION")
expect_status 200 "$DETACH_STATUS" "detach media reference" "$DETACH_BODY"
DETACHED_VALUE=$(field data.detached < "$DETACH_BODY")
if [ "$DETACHED_VALUE" = "true" ] && [ -f "$TEMP_DEMO_FILE" ]; then
  echo "PASS  detach only removes the reference and keeps the file"
else
  echo "FAIL  detach flag or file state is wrong"
  exit 1
fi

DETACHED_PREVIEW_BODY="$TMP_DIR/detached-preview.json"
DETACHED_PREVIEW_STATUS=$(curl -s -o "$DETACHED_PREVIEW_BODY" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media/$TEMP_ID/preview-authorizations")
expect_status 400 "$DETACHED_PREVIEW_STATUS" "detached media cannot be previewed" "$DETACHED_PREVIEW_BODY"

rm -f "$TEMP_DEMO_FILE"
MISSING_VERIFY_BODY="$TMP_DIR/missing-verify.json"
MISSING_VERIFY_STATUS=$(curl -s -o "$MISSING_VERIFY_BODY" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media/$TEMP_ID/verify")
expect_status 200 "$MISSING_VERIFY_STATUS" "verify media whose file was removed" "$MISSING_VERIFY_BODY"

LIST_AFTER_BODY="$TMP_DIR/media-list-after.json"
LIST_AFTER_STATUS=$(curl -s -o "$LIST_AFTER_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/media?page=1&pageSize=100")
expect_status 200 "$LIST_AFTER_STATUS" "admin media list after file removal" "$LIST_AFTER_BODY"
MISSING_STATUS=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  const item=d.data.items.find(i=>i.id===process.argv[2]); console.log(item?item.status:"");
' "$LIST_AFTER_BODY" "$TEMP_ID")
if [ "$MISSING_STATUS" = "MISSING" ]; then
  echo "PASS  removed demo file is marked MISSING"
else
  echo "FAIL  expected MISSING, got $MISSING_STATUS"
  exit 1
fi

echo
echo "ALL S03-c MEDIA CHECKS PASSED"
echo "demo media=$DEMO_ID temp media=$TEMP_ID vod media=$VOD_MEDIA_ID verified course=$VERIFIED_COURSE placeholder course=$PLACEHOLDER_COURSE"