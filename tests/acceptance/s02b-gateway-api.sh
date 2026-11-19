#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18080}"
DIRECT_USER="${DIRECT_USER:-http://127.0.0.1:18081}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
INTERNAL_SECRET="${INTERNAL_SECRET:-dev-only-internal-secret}"
TMP_DIR="$(mktemp -d)"
TEACHER_USER="teacher_s02b_$(date +%s)"
TEACHER_PASSWORD="TeacherPass1!"

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
  local password="$2"
  local jar="$3"
  local body="$4"
  curl -s -o "$body" -c "$jar" -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"STUDENT\"}" \
    "$BASE/api/v1/auth/login"
}

login_admin() {
  local username="$1"
  local password="$2"
  local jar="$3"
  local body="$4"
  curl -s -o "$body" -c "$jar" -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"ADMIN\"}" \
    "$BASE/api/v1/auth/login"
}

echo "== Public routes =="
HEALTH_BODY="$TMP_DIR/health.json"
HEALTH_STATUS=$(curl -s -o "$HEALTH_BODY" -w '%{http_code}' "$BASE/actuator/health")
expect_status 200 "$HEALTH_STATUS" "gateway health" "$HEALTH_BODY"

CATEGORIES_BODY="$TMP_DIR/categories.json"
CATEGORIES_STATUS=$(curl -s -o "$CATEGORIES_BODY" -w '%{http_code}' "$BASE/api/v1/categories")
expect_status 200 "$CATEGORIES_STATUS" "categories are public via gateway" "$CATEGORIES_BODY"

ME_NO_TOKEN_BODY="$TMP_DIR/me-no-token.json"
ME_NO_TOKEN_STATUS=$(curl -s -o "$ME_NO_TOKEN_BODY" -w '%{http_code}' "$BASE/api/v1/me")
expect_status 401 "$ME_NO_TOKEN_STATUS" "protected route rejects missing token" "$ME_NO_TOKEN_BODY"

INTERNAL_VIA_GATEWAY_BODY="$TMP_DIR/internal-via-gateway.json"
INTERNAL_VIA_GATEWAY_STATUS=$(curl -s -o "$INTERNAL_VIA_GATEWAY_BODY" -w '%{http_code}' -X POST "$BASE/int/v1/sessions/check")
expect_status 404 "$INTERNAL_VIA_GATEWAY_STATUS" "internal API is not exposed by gateway" "$INTERNAL_VIA_GATEWAY_BODY"

DIRECT_INTERNAL_NO_TOKEN_BODY="$TMP_DIR/internal-no-service-token.json"
DIRECT_INTERNAL_NO_TOKEN_STATUS=$(curl -s -o "$DIRECT_INTERNAL_NO_TOKEN_BODY" -w '%{http_code}' -X POST "$DIRECT_USER/int/v1/sessions/check")
expect_status 401 "$DIRECT_INTERNAL_NO_TOKEN_STATUS" "internal API rejects missing service token" "$DIRECT_INTERNAL_NO_TOKEN_BODY"

echo
echo "== Login through gateway =="
STUDENT_JAR="$TMP_DIR/student.jar"
STUDENT_LOGIN="$TMP_DIR/student-login.json"
STUDENT_STATUS=$(login student01 "$DEMO_PASSWORD" "$STUDENT_JAR" "$STUDENT_LOGIN")
expect_status 200 "$STUDENT_STATUS" "student login via gateway" "$STUDENT_LOGIN"
STUDENT_ACCESS=$(field data.accessToken < "$STUDENT_LOGIN")
STUDENT_ID=$(field data.user.id < "$STUDENT_LOGIN")
STUDENT_CSRF=$(field data.csrfToken < "$STUDENT_LOGIN")

ME_BODY="$TMP_DIR/me.json"
ME_STATUS=$(curl -s -o "$ME_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $STUDENT_ACCESS" "$BASE/api/v1/me")
expect_status 200 "$ME_STATUS" "GET /me through gateway" "$ME_BODY"
ME_ID=$(field data.id < "$ME_BODY")
if [ "$ME_ID" = "$STUDENT_ID" ]; then
  echo "PASS  /me returns the authenticated user"
else
  echo "FAIL  /me id mismatch: expected $STUDENT_ID, got $ME_ID"
  exit 1
fi

FORGED_BODY="$TMP_DIR/me-forged.json"
FORGED_STATUS=$(curl -s -o "$FORGED_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $STUDENT_ACCESS" \
  -H 'X-User-Id: 999999' \
  -H 'X-User-Role: ADMIN' \
  "$BASE/api/v1/me")
expect_status 200 "$FORGED_STATUS" "forged identity headers do not break authenticated request" "$FORGED_BODY"
FORGED_ID=$(field data.id < "$FORGED_BODY")
if [ "$FORGED_ID" = "$STUDENT_ID" ]; then
  echo "PASS  forged X-User-Id/X-User-Role are ignored"
else
  echo "FAIL  forged headers changed identity: expected $STUDENT_ID, got $FORGED_ID"
  exit 1
fi

FORGED_NO_AUTH_BODY="$TMP_DIR/me-forged-no-auth.json"
FORGED_NO_AUTH_STATUS=$(curl -s -o "$FORGED_NO_AUTH_BODY" -w '%{http_code}' \
  -H 'X-User-Id: 1' "$BASE/api/v1/me")
expect_status 401 "$FORGED_NO_AUTH_STATUS" "forged identity header without token rejected" "$FORGED_NO_AUTH_BODY"

echo
echo "== Refresh through gateway =="
REFRESH_BODY="$TMP_DIR/refresh.json"
REFRESH_STATUS=$(curl -s -o "$REFRESH_BODY" -w '%{http_code}' -X POST \
  -b "$STUDENT_JAR" -c "$STUDENT_JAR" \
  -H 'X-Client-App: STUDENT' \
  -H "X-CSRF-Token: $STUDENT_CSRF" \
  "$BASE/api/v1/auth/refresh")
expect_status 200 "$REFRESH_STATUS" "refresh via gateway rotates token" "$REFRESH_BODY"
NEW_STUDENT_ACCESS=$(field data.accessToken < "$REFRESH_BODY")

ME_AFTER_REFRESH_BODY="$TMP_DIR/me-after-refresh.json"
ME_AFTER_REFRESH_STATUS=$(curl -s -o "$ME_AFTER_REFRESH_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $NEW_STUDENT_ACCESS" "$BASE/api/v1/me")
expect_status 200 "$ME_AFTER_REFRESH_STATUS" "new access token works via gateway" "$ME_AFTER_REFRESH_BODY"

echo
echo "== Internal session check with valid service token =="
SERVICE_TOKEN=$(node -e '
  const crypto = require("crypto");
  const payload = ["v1", "test-client", "bootcamp-user", "sessions:check",
    Math.floor(Date.now() / 1000) + 60, crypto.randomUUID()].join("|");
  const signature = crypto.createHmac("sha256", process.argv[1]).update(payload).digest("base64url");
  process.stdout.write(Buffer.from(payload, "utf8").toString("base64url") + "." + signature);
' "$INTERNAL_SECRET")

INTERNAL_BODY="$TMP_DIR/internal-check.json"
INTERNAL_STATUS=$(curl -s -o "$INTERNAL_BODY" -w '%{http_code}' -X POST \
  -H "X-Service-Token: $SERVICE_TOKEN" \
  -H "Authorization: Bearer $NEW_STUDENT_ACCESS" \
  "$DIRECT_USER/int/v1/sessions/check")
expect_status 200 "$INTERNAL_STATUS" "internal session check with valid service token" "$INTERNAL_BODY"
INTERNAL_ACTIVE=$(field active < "$INTERNAL_BODY")
if [ "$INTERNAL_ACTIVE" = "true" ]; then
  echo "PASS  internal session check reports active session"
else
  echo "FAIL  internal session check did not report active"
  exit 1
fi

INTERNAL_BAD_BODY="$TMP_DIR/internal-bad.json"
INTERNAL_BAD_STATUS=$(curl -s -o "$INTERNAL_BAD_BODY" -w '%{http_code}' -X POST \
  -H "X-Service-Token: invalid-token" \
  -H "Authorization: Bearer $NEW_STUDENT_ACCESS" \
  "$DIRECT_USER/int/v1/sessions/check")
expect_status 401 "$INTERNAL_BAD_STATUS" "internal API rejects invalid service token" "$INTERNAL_BAD_BODY"

echo
echo "== Admin permission isolation =="
STUDENT_ADMIN_BODY="$TMP_DIR/student-admin.json"
STUDENT_ADMIN_STATUS=$(curl -s -o "$STUDENT_ADMIN_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $NEW_STUDENT_ACCESS" "$BASE/api/v1/admin/users")
expect_status 403 "$STUDENT_ADMIN_STATUS" "student cannot list admin users" "$STUDENT_ADMIN_BODY"

TEACHER_JAR="$TMP_DIR/teacher.jar"
TEACHER_LOGIN="$TMP_DIR/teacher-login.json"
TEACHER_STATUS=$(login_admin teacher01 "$DEMO_PASSWORD" "$TEACHER_JAR" "$TEACHER_LOGIN")
expect_status 200 "$TEACHER_STATUS" "seed teacher logs in" "$TEACHER_LOGIN"
TEACHER_ACCESS=$(field data.accessToken < "$TEACHER_LOGIN")

TEACHER_ADMIN_BODY="$TMP_DIR/teacher-admin.json"
TEACHER_ADMIN_STATUS=$(curl -s -o "$TEACHER_ADMIN_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $TEACHER_ACCESS" "$BASE/api/v1/admin/users")
expect_status 403 "$TEACHER_ADMIN_STATUS" "teacher cannot list admin users" "$TEACHER_ADMIN_BODY"

ADMIN_JAR="$TMP_DIR/admin.jar"
ADMIN_LOGIN="$TMP_DIR/admin-login.json"
ADMIN_STATUS=$(login_admin admin01 "$DEMO_PASSWORD" "$ADMIN_JAR" "$ADMIN_LOGIN")
expect_status 200 "$ADMIN_STATUS" "seed admin logs in to admin app" "$ADMIN_LOGIN"
ADMIN_ACCESS=$(field data.accessToken < "$ADMIN_LOGIN")

ADMIN_LIST_BODY="$TMP_DIR/admin-list.json"
ADMIN_LIST_STATUS=$(curl -s -o "$ADMIN_LIST_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/users?page=1&pageSize=20")
expect_status 200 "$ADMIN_LIST_STATUS" "admin can list users" "$ADMIN_LIST_BODY"

echo
echo "== Admin user lifecycle =="
CREATE_BODY="$TMP_DIR/create-teacher.json"
CREATE_STATUS=$(curl -s -o "$CREATE_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"username\":\"$TEACHER_USER\",\"password\":\"$TEACHER_PASSWORD\",\"nickname\":\"TeacherS02B\",\"role\":\"TEACHER\"}" \
  "$BASE/api/v1/admin/users")
expect_status 200 "$CREATE_STATUS" "admin creates a teacher account" "$CREATE_BODY"
NEW_TEACHER_ID=$(field data.id < "$CREATE_BODY")
NEW_TEACHER_REVISION=$(field data.revision < "$CREATE_BODY")

NEW_TEACHER_JAR="$TMP_DIR/new-teacher.jar"
NEW_TEACHER_LOGIN="$TMP_DIR/new-teacher-login.json"
NEW_TEACHER_STATUS=$(login_admin "$TEACHER_USER" "$TEACHER_PASSWORD" "$NEW_TEACHER_JAR" "$NEW_TEACHER_LOGIN")
expect_status 200 "$NEW_TEACHER_STATUS" "new teacher can log in" "$NEW_TEACHER_LOGIN"
NEW_TEACHER_ACCESS=$(field data.accessToken < "$NEW_TEACHER_LOGIN")

NEW_TEACHER_ADMIN_BODY="$TMP_DIR/new-teacher-admin.json"
NEW_TEACHER_ADMIN_STATUS=$(curl -s -o "$NEW_TEACHER_ADMIN_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $NEW_TEACHER_ACCESS" "$BASE/api/v1/admin/users")
expect_status 403 "$NEW_TEACHER_ADMIN_STATUS" "new teacher cannot list admin users" "$NEW_TEACHER_ADMIN_BODY"

DISABLE_BODY="$TMP_DIR/disable-teacher.json"
DISABLE_STATUS=$(curl -s -o "$DISABLE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"enabled\":false,\"revision\":$NEW_TEACHER_REVISION}" \
  "$BASE/api/v1/admin/users/$NEW_TEACHER_ID")
expect_status 200 "$DISABLE_STATUS" "admin disables teacher" "$DISABLE_BODY"
DISABLED_REVISION=$(field data.revision < "$DISABLE_BODY")

DISABLED_ME_BODY="$TMP_DIR/disabled-me.json"
DISABLED_ME_STATUS=$(curl -s -o "$DISABLED_ME_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $NEW_TEACHER_ACCESS" "$BASE/api/v1/me")
expect_status 401 "$DISABLED_ME_STATUS" "disabled teacher session is rejected" "$DISABLED_ME_BODY"

DISABLED_LOGIN_BODY="$TMP_DIR/disabled-login.json"
DISABLED_LOGIN_STATUS=$(login_admin "$TEACHER_USER" "$TEACHER_PASSWORD" "$TMP_DIR/disabled.jar" "$DISABLED_LOGIN_BODY")
expect_status 401 "$DISABLED_LOGIN_STATUS" "disabled teacher cannot log in" "$DISABLED_LOGIN_BODY"

ENABLE_BODY="$TMP_DIR/enable-teacher.json"
ENABLE_STATUS=$(curl -s -o "$ENABLE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"enabled\":true,\"revision\":$DISABLED_REVISION}" \
  "$BASE/api/v1/admin/users/$NEW_TEACHER_ID")
expect_status 200 "$ENABLE_STATUS" "admin re-enables teacher" "$ENABLE_BODY"
ENABLED_REVISION=$(field data.revision < "$ENABLE_BODY")

REENABLED_LOGIN_BODY="$TMP_DIR/reenabled-login.json"
REENABLED_LOGIN_STATUS=$(login_admin "$TEACHER_USER" "$TEACHER_PASSWORD" "$TMP_DIR/reenabled.jar" "$REENABLED_LOGIN_BODY")
expect_status 200 "$REENABLED_LOGIN_STATUS" "re-enabled teacher can log in" "$REENABLED_LOGIN_BODY"
REENABLED_ACCESS=$(field data.accessToken < "$REENABLED_LOGIN_BODY")

ROLE_CHANGE_BODY="$TMP_DIR/role-change.json"
ROLE_CHANGE_STATUS=$(curl -s -o "$ROLE_CHANGE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"role\":\"ADMIN\",\"revision\":$ENABLED_REVISION}" \
  "$BASE/api/v1/admin/users/$NEW_TEACHER_ID")
expect_status 200 "$ROLE_CHANGE_STATUS" "admin changes teacher role to ADMIN" "$ROLE_CHANGE_BODY"

ROLE_CHANGED_ME_BODY="$TMP_DIR/role-changed-me.json"
ROLE_CHANGED_ME_STATUS=$(curl -s -o "$ROLE_CHANGED_ME_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $REENABLED_ACCESS" "$BASE/api/v1/me")
expect_status 401 "$ROLE_CHANGED_ME_STATUS" "role change revokes the old session" "$ROLE_CHANGED_ME_BODY"

PROMOTED_LOGIN_BODY="$TMP_DIR/promoted-login.json"
PROMOTED_LOGIN_STATUS=$(login_admin "$TEACHER_USER" "$TEACHER_PASSWORD" "$TMP_DIR/promoted.jar" "$PROMOTED_LOGIN_BODY")
expect_status 200 "$PROMOTED_LOGIN_STATUS" "promoted admin can log in" "$PROMOTED_LOGIN_BODY"
PROMOTED_ACCESS=$(field data.accessToken < "$PROMOTED_LOGIN_BODY")

PROMOTED_ADMIN_BODY="$TMP_DIR/promoted-admin.json"
PROMOTED_ADMIN_STATUS=$(curl -s -o "$PROMOTED_ADMIN_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $PROMOTED_ACCESS" "$BASE/api/v1/admin/users")
expect_status 200 "$PROMOTED_ADMIN_STATUS" "promoted admin can list users" "$PROMOTED_ADMIN_BODY"

INVALID_ROLE_BODY="$TMP_DIR/create-student.json"
INVALID_ROLE_STATUS=$(curl -s -o "$INVALID_ROLE_BODY" -w '%{http_code}' -X POST \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"username\":\"student_attempt_$(date +%s)\",\"password\":\"StudentPass1!\",\"nickname\":\"StudentAttempt\",\"role\":\"STUDENT\"}" \
  "$BASE/api/v1/admin/users")
expect_status 400 "$INVALID_ROLE_STATUS" "admin cannot create student through admin API" "$INVALID_ROLE_BODY"

ADMIN_SELF_BODY="$TMP_DIR/admin-self.json"
ADMIN_SELF_STATUS=$(curl -s -o "$ADMIN_SELF_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_ACCESS" "$BASE/api/v1/admin/users?keyword=admin01")
expect_status 200 "$ADMIN_SELF_STATUS" "admin can query self" "$ADMIN_SELF_BODY"
ADMIN_SELF_ID=$(field data.items.0.id < "$ADMIN_SELF_BODY")
ADMIN_SELF_REVISION=$(field data.items.0.revision < "$ADMIN_SELF_BODY")

SELF_DISABLE_BODY="$TMP_DIR/self-disable.json"
SELF_DISABLE_STATUS=$(curl -s -o "$SELF_DISABLE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_ACCESS" \
  -d "{\"enabled\":false,\"revision\":$ADMIN_SELF_REVISION}" \
  "$BASE/api/v1/admin/users/$ADMIN_SELF_ID")
expect_status 400 "$SELF_DISABLE_STATUS" "admin cannot disable self" "$SELF_DISABLE_BODY"

echo
echo "== CORS =="
CORS_HEADERS="$TMP_DIR/cors-headers.txt"
curl -s -D "$CORS_HEADERS" -o /dev/null -X OPTIONS \
  -H 'Origin: http://localhost:15173' \
  -H 'Access-Control-Request-Method: GET' \
  "$BASE/api/v1/categories"
if grep -qi '^Access-Control-Allow-Origin: http://localhost:15173' "$CORS_HEADERS"; then
  echo "PASS  CORS allows the student dev origin"
else
  echo "FAIL  CORS header missing"
  cat "$CORS_HEADERS"
  exit 1
fi

echo
echo "ALL S02-b GATEWAY CHECKS PASSED"
echo "created teacher: $TEACHER_USER"