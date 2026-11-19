#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
TMP_DIR="$(mktemp -d)"
STAMP="$(date +%s)"

cleanup() {
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

json_members() {
  local ids=("$@")
  local json="["
  local first=true
  local sort=1
  for id in "${ids[@]}"; do
    if [ "$first" = false ]; then json+=","; fi
    json+="{\"courseId\":$id,\"sort\":$sort}"
    first=false
    sort=$((sort + 1))
  done
  json+="]"
  echo "$json"
}

echo "== Login and category =="
ADMIN_JAR="$TMP_DIR/admin.jar"
ADMIN_LOGIN="$TMP_DIR/admin-login.json"
ADMIN_STATUS=$(curl -s -o "$ADMIN_LOGIN" -c "$ADMIN_JAR" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"admin01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"ADMIN\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$ADMIN_STATUS" "admin login" "$ADMIN_LOGIN"
ADMIN_ACCESS=$(field data.accessToken < "$ADMIN_LOGIN")

TREE_BODY="$TMP_DIR/tree.json"
TREE_STATUS=$(curl -s -o "$TREE_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/categories")
expect_status 200 "$TREE_STATUS" "admin category tree" "$TREE_BODY"
JAVA_ID=$(find_category "Java" < "$TREE_BODY")
if [ -z "$JAVA_ID" ]; then echo "FAIL Java category missing"; exit 1; fi

create_paid_course() {
  local title="$1"
  local body="$TMP_DIR/${title}.json"
  local status
  status=$(curl -s -o "$body" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
    -d "{\"title\":\"$title\",\"description\":\"S03B course\",\"categoryId\":$JAVA_ID,\"priceCent\":990,\"isFree\":false,\"coverUrl\":null}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create paid course $title" "$body"
  local course_id
  course_id=$(field data.id < "$body")
  local chapter_body="$TMP_DIR/${title}-chapter.json"
  status=$(curl -s -o "$chapter_body" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
    -d '{"title":"Chapter","sort":1}' "$BASE/api/v1/admin/courses/$course_id/chapters")
  expect_status 200 "$status" "create chapter for $title" "$chapter_body"
  local chapter_id
  chapter_id=$(field data.id < "$chapter_body")
  local lesson_body="$TMP_DIR/${title}-lesson.json"
  status=$(curl -s -o "$lesson_body" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
    -d '{"title":"Lesson","sort":1,"mediaId":null}' "$BASE/api/v1/admin/chapters/$chapter_id/lessons")
  expect_status 200 "$status" "create lesson for $title" "$lesson_body"
  local lesson_id
  lesson_id=$(field data.id < "$lesson_body")
  status=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
    -d '{"title":"Lesson","sort":1,"mediaId":999,"revision":0}' \
    "$BASE/api/v1/admin/chapters/$chapter_id/lessons/$lesson_id")
  expect_status 200 "$status" "attach placeholder media for $title" /dev/null
  local publish_body="$TMP_DIR/${title}-publish.json"
  status=$(curl -s -o "$publish_body" -w '%{http_code}' -X POST \
    -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
    -d '{"revision":0}' "$BASE/api/v1/admin/courses/$course_id/publish")
  expect_status 200 "$status" "publish course $title" "$publish_body"
  CREATED_COURSE_ID="$course_id"
}

echo
echo "== Create three paid courses =="
create_paid_course "S03B_A_$STAMP"
COURSE_A="$CREATED_COURSE_ID"
create_paid_course "S03B_B_$STAMP"
COURSE_B="$CREATED_COURSE_ID"
create_paid_course "S03B_C_$STAMP"
COURSE_C="$CREATED_COURSE_ID"
echo "      courses: A=$COURSE_A B=$COURSE_B C=$COURSE_C"

echo
echo "== Route price validation =="
ROUTE100_BODY="$TMP_DIR/route100.json"
ROUTE100_STATUS=$(curl -s -o "$ROUTE100_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"title\":\"S03B Route 100 $STAMP\",\"description\":\"\",\"priceCent\":100,\"members\":$(json_members "$COURSE_A" "$COURSE_B")}" \
  "$BASE/api/v1/admin/routes")
expect_status 400 "$ROUTE100_STATUS" "route with 50-cent allocation rejected" "$ROUTE100_BODY"

ROUTE_BODY="$TMP_DIR/route.json"
ROUTE_STATUS=$(curl -s -o "$ROUTE_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"title\":\"S03B Route $STAMP\",\"description\":\"S03B\",\"priceCent\":200,\"members\":$(json_members "$COURSE_A" "$COURSE_B")}" \
  "$BASE/api/v1/admin/routes")
expect_status 200 "$ROUTE_STATUS" "route with 100-cent allocations accepted" "$ROUTE_BODY"
ROUTE_ID=$(field data.id < "$ROUTE_BODY")
ROUTE_REVISION=$(field data.revision < "$ROUTE_BODY")

PUBLISH_BODY="$TMP_DIR/route-publish.json"
PUBLISH_STATUS=$(curl -s -o "$PUBLISH_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"revision\":$ROUTE_REVISION}" "$BASE/api/v1/admin/routes/$ROUTE_ID/publish")
expect_status 200 "$PUBLISH_STATUS" "route published with version 1" "$PUBLISH_BODY"
ROUTE_REVISION=$(field data.revision < "$PUBLISH_BODY")

PUBLIC_ROUTE_BODY="$TMP_DIR/route-public.json"
PUBLIC_ROUTE_STATUS=$(curl -s -o "$PUBLIC_ROUTE_BODY" -w '%{http_code}' "$BASE/api/v1/routes/$ROUTE_ID")
expect_status 200 "$PUBLIC_ROUTE_STATUS" "public route detail" "$PUBLIC_ROUTE_BODY"
PUBLIC_ALLOCATIONS=$(field data.members < "$PUBLIC_ROUTE_BODY")
echo "      route members: $PUBLIC_ALLOCATIONS"

echo
echo "== Route member epochs =="
UPDATE1_BODY="$TMP_DIR/route-update1.json"
UPDATE1_STATUS=$(curl -s -o "$UPDATE1_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"title\":\"S03B Route $STAMP\",\"description\":\"S03B\",\"priceCent\":600,\"members\":$(json_members "$COURSE_A" "$COURSE_B" "$COURSE_C"),\"revision\":$ROUTE_REVISION}" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID")
expect_status 200 "$UPDATE1_STATUS" "add course C creates route version 2" "$UPDATE1_BODY"
ROUTE_REVISION=$(field data.revision < "$UPDATE1_BODY")

UPDATE2_BODY="$TMP_DIR/route-update2.json"
UPDATE2_STATUS=$(curl -s -o "$UPDATE2_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"title\":\"S03B Route $STAMP\",\"description\":\"S03B\",\"priceCent\":600,\"members\":$(json_members "$COURSE_B" "$COURSE_C"),\"revision\":$ROUTE_REVISION}" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID")
expect_status 200 "$UPDATE2_STATUS" "remove course A creates route version 3" "$UPDATE2_BODY"
ROUTE_REVISION=$(field data.revision < "$UPDATE2_BODY")

UPDATE3_BODY="$TMP_DIR/route-update3.json"
UPDATE3_STATUS=$(curl -s -o "$UPDATE3_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"title\":\"S03B Route $STAMP\",\"description\":\"S03B\",\"priceCent\":600,\"members\":$(json_members "$COURSE_A" "$COURSE_B" "$COURSE_C"),\"revision\":$ROUTE_REVISION}" \
  "$BASE/api/v1/admin/routes/$ROUTE_ID")
expect_status 200 "$UPDATE3_STATUS" "re-add course A creates route version 4" "$UPDATE3_BODY"
ROUTE_REVISION=$(field data.revision < "$UPDATE3_BODY")

echo
echo "== Course price change validation =="
COURSE_C_DETAIL_BODY="$TMP_DIR/course-c.json"
COURSE_C_DETAIL_STATUS=$(curl -s -o "$COURSE_C_DETAIL_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/courses/$COURSE_C")
expect_status 200 "$COURSE_C_DETAIL_STATUS" "course C detail" "$COURSE_C_DETAIL_BODY"
COURSE_C_REVISION=$(field data.revision < "$COURSE_C_DETAIL_BODY")

INVALID_PRICE_BODY="$TMP_DIR/course-invalid-price.json"
INVALID_PRICE_STATUS=$(curl -s -o "$INVALID_PRICE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"title\":\"S03B_C_$STAMP\",\"description\":\"S03B course\",\"categoryId\":$JAVA_ID,\"priceCent\":50000,\"isFree\":false,\"coverUrl\":null,\"revision\":$COURSE_C_REVISION}" \
  "$BASE/api/v1/admin/courses/$COURSE_C")
expect_status 400 "$INVALID_PRICE_STATUS" "course price change that breaks route allocation rejected" "$INVALID_PRICE_BODY"

VALID_PRICE_BODY="$TMP_DIR/course-valid-price.json"
VALID_PRICE_STATUS=$(curl -s -o "$VALID_PRICE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"title\":\"S03B_C_$STAMP\",\"description\":\"S03B course\",\"categoryId\":$JAVA_ID,\"priceCent\":1000,\"isFree\":false,\"coverUrl\":null,\"revision\":$COURSE_C_REVISION}" \
  "$BASE/api/v1/admin/courses/$COURSE_C")
expect_status 200 "$VALID_PRICE_STATUS" "valid course price change refreshes route snapshot" "$VALID_PRICE_BODY"

ROUTE_AFTER_PRICE_BODY="$TMP_DIR/route-after-price.json"
ROUTE_AFTER_PRICE_STATUS=$(curl -s -o "$ROUTE_AFTER_PRICE_BODY" -w '%{http_code}' "$BASE/api/v1/routes/$ROUTE_ID")
expect_status 200 "$ROUTE_AFTER_PRICE_STATUS" "route remains valid after course price change" "$ROUTE_AFTER_PRICE_BODY"
ROUTE_VERSION=$(field data.currentVersionNo < "$ROUTE_AFTER_PRICE_BODY")
if [ "$ROUTE_VERSION" -ge 5 ]; then
  echo "PASS  route was re-versioned after course price change (v$ROUTE_VERSION)"
else
  echo "FAIL  expected route version >= 5, got $ROUTE_VERSION"
  exit 1
fi

echo
echo "== Teacher assignment =="
TEACHER_LIST_BODY="$TMP_DIR/teachers.json"
TEACHER_LIST_STATUS=$(curl -s -o "$TEACHER_LIST_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/users?keyword=teacher01")
expect_status 200 "$TEACHER_LIST_STATUS" "query teacher account" "$TEACHER_LIST_BODY"
TEACHER_ID=$(field data.items.0.id < "$TEACHER_LIST_BODY")

ASSIGNMENT_BODY="$TMP_DIR/assignments.json"
ASSIGNMENT_STATUS=$(curl -s -o "$ASSIGNMENT_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$ASSIGNMENT_STATUS" "read teacher assignments" "$ASSIGNMENT_BODY"
ASSIGNMENT_REVISION=$(field data.revision < "$ASSIGNMENT_BODY")

ASSIGN_BODY="$TMP_DIR/assign.json"
ASSIGN_STATUS=$(curl -s -o "$ASSIGN_BODY" -w '%{http_code}' -X PUT \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"courseIds\":[$COURSE_A],\"routeIds\":[$ROUTE_ID],\"revision\":$ASSIGNMENT_REVISION}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$ASSIGN_STATUS" "assign course and route to teacher" "$ASSIGN_BODY"

STALE_ASSIGN_BODY="$TMP_DIR/assign-stale.json"
STALE_ASSIGN_STATUS=$(curl -s -o "$STALE_ASSIGN_BODY" -w '%{http_code}' -X PUT \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"courseIds\":[$COURSE_B],\"routeIds\":[],\"revision\":$ASSIGNMENT_REVISION}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 409 "$STALE_ASSIGN_STATUS" "stale teacher assignment revision rejected" "$STALE_ASSIGN_BODY"

TEACHER_JAR="$TMP_DIR/teacher.jar"
TEACHER_LOGIN="$TMP_DIR/teacher-login.json"
TEACHER_LOGIN_STATUS=$(curl -s -o "$TEACHER_LOGIN" -c "$TEACHER_JAR" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"teacher01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"ADMIN\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$TEACHER_LOGIN_STATUS" "teacher login" "$TEACHER_LOGIN"
TEACHER_ACCESS=$(field data.accessToken < "$TEACHER_LOGIN")

TEACHER_COURSES_BODY="$TMP_DIR/teacher-courses.json"
TEACHER_COURSES_STATUS=$(curl -s -o "$TEACHER_COURSES_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $TEACHER_ACCESS" "$BASE/api/v1/teacher/courses")
expect_status 200 "$TEACHER_COURSES_STATUS" "teacher sees assigned course" "$TEACHER_COURSES_BODY"

TEACHER_ROUTES_BODY="$TMP_DIR/teacher-routes.json"
TEACHER_ROUTES_STATUS=$(curl -s -o "$TEACHER_ROUTES_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $TEACHER_ACCESS" "$BASE/api/v1/teacher/routes")
expect_status 200 "$TEACHER_ROUTES_STATUS" "teacher sees assigned route" "$TEACHER_ROUTES_BODY"

TEACHER_ADMIN_BODY="$TMP_DIR/teacher-admin.json"
TEACHER_ADMIN_STATUS=$(curl -s -o "$TEACHER_ADMIN_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $TEACHER_ACCESS" "$BASE/api/v1/admin/routes")
expect_status 403 "$TEACHER_ADMIN_STATUS" "teacher cannot manage routes" "$TEACHER_ADMIN_BODY"

echo
echo "ALL S03-b ROUTE CHECKS PASSED"
echo "route id=$ROUTE_ID courseA=$COURSE_A courseB=$COURSE_B courseC=$COURSE_C teacher=$TEACHER_ID"