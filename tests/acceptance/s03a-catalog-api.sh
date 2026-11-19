#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DIRECT_COURSE="${DIRECT_COURSE:-http://127.0.0.1:18082}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
TMP_DIR="$(mktemp -d)"
STAMP="$(date +%s)"
CATEGORY_NAME="S03A_Cat_${STAMP}"
FREE_TITLE="S03A Free ${STAMP}"
PAID_TITLE="S03A Paid ${STAMP}"

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
    console.log(found ? found.id + "|" + found.revision : "");
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

login() {
  local username="$1"
  local clientApp="$2"
  local jar="$3"
  local body="$4"
  curl -s -o "$body" -c "$jar" -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"$clientApp\"}" \
    "$BASE/api/v1/auth/login"
}

echo "== Access control =="
NO_TOKEN_BODY="$TMP_DIR/admin-courses-no-token.json"
NO_TOKEN_STATUS=$(curl -s -o "$NO_TOKEN_BODY" -w '%{http_code}' "$BASE/api/v1/admin/courses")
expect_status 401 "$NO_TOKEN_STATUS" "admin course API requires token" "$NO_TOKEN_BODY"

DIRECT_BODY="$TMP_DIR/direct-admin.json"
DIRECT_STATUS=$(curl -s -o "$DIRECT_BODY" -w '%{http_code}' "$DIRECT_COURSE/api/v1/admin/categories")
expect_status 401 "$DIRECT_STATUS" "direct course admin API rejects missing token" "$DIRECT_BODY"

ADMIN_JAR="$TMP_DIR/admin.jar"
ADMIN_LOGIN="$TMP_DIR/admin-login.json"
ADMIN_STATUS=$(login admin01 ADMIN "$ADMIN_JAR" "$ADMIN_LOGIN")
expect_status 200 "$ADMIN_STATUS" "admin login" "$ADMIN_LOGIN"
ADMIN_ACCESS=$(field data.accessToken < "$ADMIN_LOGIN")

TREE_BODY="$TMP_DIR/admin-tree.json"
TREE_STATUS=$(curl -s -o "$TREE_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/categories")
expect_status 200 "$TREE_STATUS" "admin reads category tree" "$TREE_BODY"

JAVA_INFO=$(find_category "Java" < "$TREE_BODY")
JAVA_ID="${JAVA_INFO%%|*}"
JAVA_REVISION="${JAVA_INFO##*|}"
if [ -z "$JAVA_ID" ]; then
  echo "FAIL  Java category not found"
  exit 1
fi
echo "PASS  Java category id=$JAVA_ID revision=$JAVA_REVISION"

TEACHER_JAR="$TMP_DIR/teacher.jar"
TEACHER_LOGIN="$TMP_DIR/teacher-login.json"
TEACHER_STATUS=$(login teacher01 ADMIN "$TEACHER_JAR" "$TEACHER_LOGIN")
expect_status 200 "$TEACHER_STATUS" "teacher login" "$TEACHER_LOGIN"
TEACHER_ACCESS=$(field data.accessToken < "$TEACHER_LOGIN")
TEACHER_ADMIN_BODY="$TMP_DIR/teacher-admin.json"
TEACHER_ADMIN_STATUS=$(curl -s -o "$TEACHER_ADMIN_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $TEACHER_ACCESS" "$BASE/api/v1/admin/courses")
expect_status 403 "$TEACHER_ADMIN_STATUS" "teacher cannot manage courses" "$TEACHER_ADMIN_BODY"

STUDENT_JAR="$TMP_DIR/student.jar"
STUDENT_LOGIN="$TMP_DIR/student-login.json"
STUDENT_STATUS=$(login student01 STUDENT "$STUDENT_JAR" "$STUDENT_LOGIN")
expect_status 200 "$STUDENT_STATUS" "student login" "$STUDENT_LOGIN"
STUDENT_ACCESS=$(field data.accessToken < "$STUDENT_LOGIN")
STUDENT_ADMIN_BODY="$TMP_DIR/student-admin.json"
STUDENT_ADMIN_STATUS=$(curl -s -o "$STUDENT_ADMIN_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $STUDENT_ACCESS" "$BASE/api/v1/admin/courses")
expect_status 403 "$STUDENT_ADMIN_STATUS" "student cannot manage courses" "$STUDENT_ADMIN_BODY"

echo
echo "== Category closure =="
CAT_CREATE_BODY="$TMP_DIR/category-create.json"
CAT_CREATE_STATUS=$(curl -s -o "$CAT_CREATE_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"parentId\":$JAVA_ID,\"name\":\"$CATEGORY_NAME\",\"sort\":3,\"enabled\":true}" \
  "$BASE/api/v1/admin/categories")
expect_status 200 "$CAT_CREATE_STATUS" "create nested category" "$CAT_CREATE_BODY"
CATEGORY_ID=$(field data.id < "$CAT_CREATE_BODY")
CATEGORY_REVISION=$(field data.revision < "$CAT_CREATE_BODY")

CYCLE_BODY="$TMP_DIR/category-cycle.json"
CYCLE_STATUS=$(curl -s -o "$CYCLE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"name\":\"Java\",\"parentId\":$CATEGORY_ID,\"sort\":1,\"enabled\":true,\"revision\":$JAVA_REVISION}" \
  "$BASE/api/v1/admin/categories/$JAVA_ID")
expect_status 400 "$CYCLE_STATUS" "category move into descendant rejected" "$CYCLE_BODY"

CAT_UPDATE_BODY="$TMP_DIR/category-update.json"
CAT_UPDATE_STATUS=$(curl -s -o "$CAT_UPDATE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"name\":\"$CATEGORY_NAME\",\"parentId\":$JAVA_ID,\"sort\":4,\"enabled\":true,\"revision\":$CATEGORY_REVISION}" \
  "$BASE/api/v1/admin/categories/$CATEGORY_ID")
expect_status 200 "$CAT_UPDATE_STATUS" "update category" "$CAT_UPDATE_BODY"

echo
echo "== Course and publish rules =="
FREE_BODY="$TMP_DIR/free-create.json"
FREE_STATUS=$(curl -s -o "$FREE_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"title\":\"$FREE_TITLE\",\"description\":\"Free demo course\",\"categoryId\":$CATEGORY_ID,\"priceCent\":0,\"isFree\":true,\"coverUrl\":null}" \
  "$BASE/api/v1/admin/courses")
expect_status 200 "$FREE_STATUS" "create free course" "$FREE_BODY"
FREE_ID=$(field data.id < "$FREE_BODY")

PAID_BODY="$TMP_DIR/paid-create.json"
PAID_STATUS=$(curl -s -o "$PAID_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"title\":\"$PAID_TITLE\",\"description\":\"Paid demo course\",\"categoryId\":$CATEGORY_ID,\"priceCent\":990,\"isFree\":false,\"coverUrl\":null}" \
  "$BASE/api/v1/admin/courses")
expect_status 200 "$PAID_STATUS" "create paid course" "$PAID_BODY"
PAID_ID=$(field data.id < "$PAID_BODY")

FREE_CHAPTER_BODY="$TMP_DIR/free-chapter.json"
FREE_CHAPTER_STATUS=$(curl -s -o "$FREE_CHAPTER_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d '{"title":"Chapter One","sort":1}' \
  "$BASE/api/v1/admin/courses/$FREE_ID/chapters")
expect_status 200 "$FREE_CHAPTER_STATUS" "create chapter" "$FREE_CHAPTER_BODY"
FREE_CHAPTER_ID=$(field data.id < "$FREE_CHAPTER_BODY")

FREE_LESSON_BODY="$TMP_DIR/free-lesson.json"
FREE_LESSON_STATUS=$(curl -s -o "$FREE_LESSON_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d '{"title":"Lesson One","sort":1,"mediaId":null}' \
  "$BASE/api/v1/admin/chapters/$FREE_CHAPTER_ID/lessons")
expect_status 200 "$FREE_LESSON_STATUS" "create lesson" "$FREE_LESSON_BODY"
FREE_LESSON_ID=$(field data.id < "$FREE_LESSON_BODY")

FREE_DETAIL_BODY="$TMP_DIR/free-detail-admin.json"
FREE_DETAIL_STATUS=$(curl -s -o "$FREE_DETAIL_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/courses/$FREE_ID")
expect_status 200 "$FREE_DETAIL_STATUS" "admin course detail" "$FREE_DETAIL_BODY"
FREE_REVISION=$(field data.revision < "$FREE_DETAIL_BODY")

LESSON_UPDATE_BODY="$TMP_DIR/lesson-update.json"
LESSON_UPDATE_STATUS=$(curl -s -o "$LESSON_UPDATE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d '{"title":"Lesson One Updated","sort":1,"mediaId":null,"revision":0}' \
  "$BASE/api/v1/admin/chapters/$FREE_CHAPTER_ID/lessons/$FREE_LESSON_ID")
expect_status 200 "$LESSON_UPDATE_STATUS" "update lesson with revision" "$LESSON_UPDATE_BODY"
LESSON_REVISION=$(field data.revision < "$LESSON_UPDATE_BODY")

LESSON_DELETE_BODY="$TMP_DIR/lesson-delete.json"
LESSON_DELETE_STATUS=$(curl -s -o "$LESSON_DELETE_BODY" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  "$BASE/api/v1/admin/lessons/$FREE_LESSON_ID?revision=$LESSON_REVISION")
expect_status 200 "$LESSON_DELETE_STATUS" "delete lesson with revision" "$LESSON_DELETE_BODY"

FREE_PUBLISH_BODY="$TMP_DIR/free-publish.json"
FREE_PUBLISH_STATUS=$(curl -s -o "$FREE_PUBLISH_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"revision\":$FREE_REVISION}" \
  "$BASE/api/v1/admin/courses/$FREE_ID/publish")
expect_status 200 "$FREE_PUBLISH_STATUS" "free course can be published" "$FREE_PUBLISH_BODY"

PAID_PUBLISH_BODY="$TMP_DIR/paid-publish.json"
PAID_PUBLISH_STATUS=$(curl -s -o "$PAID_PUBLISH_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d '{"revision":0}' \
  "$BASE/api/v1/admin/courses/$PAID_ID/publish")
expect_status 400 "$PAID_PUBLISH_STATUS" "paid course without video cannot publish" "$PAID_PUBLISH_BODY"

PAID_DETAIL_PUBLIC_BODY="$TMP_DIR/paid-public.json"
PAID_DETAIL_PUBLIC_STATUS=$(curl -s -o "$PAID_DETAIL_PUBLIC_BODY" -w '%{http_code}' \
  "$BASE/api/v1/courses/$PAID_ID")
expect_status 404 "$PAID_DETAIL_PUBLIC_STATUS" "draft course is not public" "$PAID_DETAIL_PUBLIC_BODY"

echo
echo "== Public catalog and search =="
SEARCH_BODY="$TMP_DIR/search.json"
SEARCH_STATUS=$(curl -s -o "$SEARCH_BODY" -w '%{http_code}' \
  "$BASE/api/v1/courses?categoryId=$JAVA_ID&keyword=S03A&page=1&pageSize=20")
expect_status 200 "$SEARCH_STATUS" "search by ancestor category and keyword" "$SEARCH_BODY"
SEARCH_TOTAL=$(field data.total < "$SEARCH_BODY")
if [ "$SEARCH_TOTAL" -ge 1 ]; then
  echo "PASS  category subtree search returned $SEARCH_TOTAL course(s)"
else
  echo "FAIL  category subtree search returned no courses"
  exit 1
fi

FREE_PUBLIC_BODY="$TMP_DIR/free-public.json"
FREE_PUBLIC_STATUS=$(curl -s -o "$FREE_PUBLIC_BODY" -w '%{http_code}' "$BASE/api/v1/courses/$FREE_ID")
expect_status 200 "$FREE_PUBLIC_STATUS" "public course detail" "$FREE_PUBLIC_BODY"
FREE_CHAPTERS=$(field data.chapters < "$FREE_PUBLIC_BODY")
if [ "$FREE_CHAPTERS" != "[]" ]; then
  echo "PASS  public detail returns the catalog"
else
  echo "FAIL  public detail has no catalog"
  exit 1
fi

echo
echo "ALL S03-a CATALOG CHECKS PASSED"
echo "category: $CATEGORY_NAME ($CATEGORY_ID), free course: $FREE_ID, paid course: $PAID_ID"