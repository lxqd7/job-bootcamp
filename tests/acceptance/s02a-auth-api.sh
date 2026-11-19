#!/usr/bin/env bash
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:18081}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
TMP_DIR="$(mktemp -d)"
TEST_USER="stu_s02a_$(date +%s)"
TEST_PASSWORD="Passw0rd!"
NEW_PASSWORD="NewPassw0rd!"

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
    console.log(value == null ? "" : value);
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

echo "== Registration =="
REG_BODY="$TMP_DIR/register.json"
REG_STATUS=$(curl -s -o "$REG_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASSWORD\",\"nickname\":\"TestStudent\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$REG_STATUS" "register student" "$REG_BODY"
TEST_USER_ID=$(field data.userId < "$REG_BODY")
echo "      userId=$TEST_USER_ID"

ROLE_USER="admin_attempt_$(date +%s)"
ROLE_BODY="$TMP_DIR/register-role.json"
ROLE_STATUS=$(curl -s -o "$ROLE_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ROLE_USER\",\"password\":\"$TEST_PASSWORD\",\"nickname\":\"PrivilegeAttempt\",\"role\":\"ADMIN\"}" \
  "$BASE/api/v1/auth/register")
expect_status 200 "$ROLE_STATUS" "register payload containing role is accepted" "$ROLE_BODY"

ROLE_LOGIN_BODY="$TMP_DIR/role-login.json"
ROLE_LOGIN_STATUS=$(curl -s -o "$ROLE_LOGIN_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ROLE_USER\",\"password\":\"$TEST_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$ROLE_LOGIN_STATUS" "role-injection account can log in" "$ROLE_LOGIN_BODY"
ROLE_VALUE=$(field data.user.role < "$ROLE_LOGIN_BODY")
if [ "$ROLE_VALUE" = "STUDENT" ]; then
  echo "PASS  role field injection ignored (role=$ROLE_VALUE)"
else
  echo "FAIL  expected STUDENT role, got $ROLE_VALUE"
  exit 1
fi

DUP_BODY="$TMP_DIR/register-dup.json"
DUP_STATUS=$(curl -s -o "$DUP_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASSWORD\",\"nickname\":\"Duplicate\"}" \
  "$BASE/api/v1/auth/register")
expect_status 409 "$DUP_STATUS" "duplicate username rejected" "$DUP_BODY"

SHORT_BODY="$TMP_DIR/register-short.json"
SHORT_STATUS=$(curl -s -o "$SHORT_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"username":"shortpw01","password":"1234567","nickname":"Short"}' \
  "$BASE/api/v1/auth/register")
expect_status 400 "$SHORT_STATUS" "short password rejected" "$SHORT_BODY"

LONG_PASSWORD=$(printf 'a%.0s' $(seq 1 73))
LONG_BODY="$TMP_DIR/register-long.json"
LONG_STATUS=$(curl -s -o "$LONG_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"longpw_$(date +%s)\",\"password\":\"$LONG_PASSWORD\",\"nickname\":\"Long\"}" \
  "$BASE/api/v1/auth/register")
expect_status 400 "$LONG_STATUS" "password longer than 72 bytes rejected" "$LONG_BODY"

echo
echo "== Login and profile =="
TEST_JAR="$TMP_DIR/test-user.jar"
TEST_LOGIN="$TMP_DIR/test-login.json"
TEST_LOGIN_STATUS=$(curl -s -o "$TEST_LOGIN" -c "$TEST_JAR" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$TEST_LOGIN_STATUS" "registered user can log in" "$TEST_LOGIN"
TEST_ACCESS=$(field data.accessToken < "$TEST_LOGIN")
TEST_ROLE=$(field data.user.role < "$TEST_LOGIN")
if [ "$TEST_ROLE" = "STUDENT" ]; then
  echo "PASS  role field injection ignored (role=$TEST_ROLE)"
else
  echo "FAIL  expected STUDENT role, got $TEST_ROLE"
  exit 1
fi

ME_BODY="$TMP_DIR/me.json"
ME_STATUS=$(curl -s -o "$ME_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $TEST_ACCESS" \
  "$BASE/api/v1/me")
expect_status 200 "$ME_STATUS" "GET /me with access token" "$ME_BODY"
TEST_REVISION=$(field data.revision < "$ME_BODY")
echo "      revision=$TEST_REVISION"

NO_TOKEN_BODY="$TMP_DIR/me-no-token.json"
NO_TOKEN_STATUS=$(curl -s -o "$NO_TOKEN_BODY" -w '%{http_code}' "$BASE/api/v1/me")
expect_status 401 "$NO_TOKEN_STATUS" "GET /me without token rejected" "$NO_TOKEN_BODY"

BAD_TOKEN_BODY="$TMP_DIR/me-bad-token.json"
BAD_TOKEN_STATUS=$(curl -s -o "$BAD_TOKEN_BODY" -w '%{http_code}' \
  -H 'Authorization: Bearer not-a-token' "$BASE/api/v1/me")
expect_status 401 "$BAD_TOKEN_STATUS" "GET /me with invalid token rejected" "$BAD_TOKEN_BODY"

PATCH_BODY="$TMP_DIR/patch.json"
PATCH_STATUS=$(curl -s -o "$PATCH_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEST_ACCESS" \
  -d "{\"nickname\":\"TestNick\",\"revision\":$TEST_REVISION}" \
  "$BASE/api/v1/me")
expect_status 200 "$PATCH_STATUS" "PATCH /me updates nickname" "$PATCH_BODY"
NEW_REVISION=$(field data.revision < "$PATCH_BODY")

STALE_BODY="$TMP_DIR/patch-stale.json"
STALE_STATUS=$(curl -s -o "$STALE_BODY" -w '%{http_code}' -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEST_ACCESS" \
  -d "{\"nickname\":\"StaleNick\",\"revision\":$TEST_REVISION}" \
  "$BASE/api/v1/me")
expect_status 409 "$STALE_STATUS" "PATCH /me with stale revision rejected" "$STALE_BODY"

curl -s -o /dev/null -X PATCH \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEST_ACCESS" \
  -d "{\"nickname\":\"TestStudent\",\"revision\":$NEW_REVISION}" \
  "$BASE/api/v1/me"

echo
echo "== Token claims =="
node -e '
  const payload = JSON.parse(Buffer.from(process.argv[1].split(".")[1], "base64url"));
  console.log(JSON.stringify({
    sub: payload.sub,
    role: payload.role,
    sessionId: payload.sessionId,
    tokenVersion: payload.tokenVersion,
    iss: payload.iss,
    aud: payload.aud,
    hasJti: !!payload.jti,
    ttlSeconds: payload.exp - payload.iat
  }));
' "$TEST_ACCESS"

echo
echo "== Login restrictions =="
STUDENT_JAR="$TMP_DIR/student.jar"
STUDENT_LOGIN="$TMP_DIR/student-login.json"
STUDENT_STATUS=$(curl -s -o "$STUDENT_LOGIN" -c "$STUDENT_JAR" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"student01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$STUDENT_STATUS" "student01 can log in to student app" "$STUDENT_LOGIN"
STUDENT_ACCESS=$(field data.accessToken < "$STUDENT_LOGIN")
STUDENT_CSRF=$(field data.csrfToken < "$STUDENT_LOGIN")

ADMIN_CLIENT_BODY="$TMP_DIR/student-admin-client.json"
ADMIN_CLIENT_STATUS=$(curl -s -o "$ADMIN_CLIENT_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"student01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"ADMIN\"}" \
  "$BASE/api/v1/auth/login")
expect_status 403 "$ADMIN_CLIENT_STATUS" "student cannot log in to admin app" "$ADMIN_CLIENT_BODY"

WRONG_PW_BODY="$TMP_DIR/login-wrong-password.json"
WRONG_PW_STATUS=$(curl -s -o "$WRONG_PW_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"username":"student01","password":"wrong-password","clientApp":"STUDENT"}' \
  "$BASE/api/v1/auth/login")
expect_status 401 "$WRONG_PW_STATUS" "wrong password rejected" "$WRONG_PW_BODY"

UNKNOWN_USER_BODY="$TMP_DIR/login-unknown-user.json"
UNKNOWN_USER_STATUS=$(curl -s -o "$UNKNOWN_USER_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"username":"no_such_user","password":"Demo@123456","clientApp":"STUDENT"}' \
  "$BASE/api/v1/auth/login")
expect_status 401 "$UNKNOWN_USER_STATUS" "unknown user rejected" "$UNKNOWN_USER_BODY"

echo
echo "== Refresh rotation and replay =="
NO_CSRF_BODY="$TMP_DIR/refresh-no-csrf.json"
NO_CSRF_STATUS=$(curl -s -o "$NO_CSRF_BODY" -w '%{http_code}' -X POST \
  -b "$STUDENT_JAR" \
  -H 'X-Client-App: STUDENT' \
  "$BASE/api/v1/auth/refresh")
expect_status 403 "$NO_CSRF_STATUS" "refresh without CSRF rejected" "$NO_CSRF_BODY"

MISMATCH_BODY="$TMP_DIR/refresh-wrong-client.json"
MISMATCH_STATUS=$(curl -s -o "$MISMATCH_BODY" -w '%{http_code}' -X POST \
  -b "$STUDENT_JAR" \
  -H 'X-Client-App: ADMIN' \
  -H "X-CSRF-Token: $STUDENT_CSRF" \
  "$BASE/api/v1/auth/refresh")
expect_status 401 "$MISMATCH_STATUS" "refresh with wrong client app rejected" "$MISMATCH_BODY"

cp "$STUDENT_JAR" "$TMP_DIR/student-old.jar"
REFRESH_BODY="$TMP_DIR/refresh.json"
REFRESH_STATUS=$(curl -s -o "$REFRESH_BODY" -w '%{http_code}' -X POST \
  -b "$STUDENT_JAR" -c "$STUDENT_JAR" \
  -H 'X-Client-App: STUDENT' \
  -H "X-CSRF-Token: $STUDENT_CSRF" \
  "$BASE/api/v1/auth/refresh")
expect_status 200 "$REFRESH_STATUS" "refresh rotates the refresh token" "$REFRESH_BODY"
NEW_ACCESS=$(field data.accessToken < "$REFRESH_BODY")

REPLAY_BODY="$TMP_DIR/refresh-replay.json"
REPLAY_STATUS=$(curl -s -o "$REPLAY_BODY" -w '%{http_code}' -X POST \
  -b "$TMP_DIR/student-old.jar" \
  -H 'X-Client-App: STUDENT' \
  -H "X-CSRF-Token: $STUDENT_CSRF" \
  "$BASE/api/v1/auth/refresh")
expect_status 401 "$REPLAY_STATUS" "reused refresh token is rejected" "$REPLAY_BODY"

REVOKED_BODY="$TMP_DIR/me-revoked.json"
REVOKED_STATUS=$(curl -s -o "$REVOKED_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $NEW_ACCESS" \
  "$BASE/api/v1/me")
expect_status 401 "$REVOKED_STATUS" "replay revokes the session and access token" "$REVOKED_BODY"

echo
echo "== Logout =="
LOGOUT_LOGIN="$TMP_DIR/logout-login.json"
LOGOUT_JAR="$TMP_DIR/logout.jar"
LOGOUT_LOGIN_STATUS=$(curl -s -o "$LOGOUT_LOGIN" -c "$LOGOUT_JAR" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"student01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$LOGOUT_LOGIN_STATUS" "login before logout" "$LOGOUT_LOGIN"
LOGOUT_ACCESS=$(field data.accessToken < "$LOGOUT_LOGIN")
LOGOUT_CSRF=$(field data.csrfToken < "$LOGOUT_LOGIN")

LOGOUT_BODY="$TMP_DIR/logout.json"
LOGOUT_STATUS=$(curl -s -o "$LOGOUT_BODY" -w '%{http_code}' -X POST \
  -b "$LOGOUT_JAR" -c "$LOGOUT_JAR" \
  -H 'X-Client-App: STUDENT' \
  -H "X-CSRF-Token: $LOGOUT_CSRF" \
  "$BASE/api/v1/auth/logout")
expect_status 200 "$LOGOUT_STATUS" "logout with refresh cookie and CSRF" "$LOGOUT_BODY"

LOGOUT_ME_BODY="$TMP_DIR/me-after-logout.json"
LOGOUT_ME_STATUS=$(curl -s -o "$LOGOUT_ME_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $LOGOUT_ACCESS" \
  "$BASE/api/v1/me")
expect_status 401 "$LOGOUT_ME_STATUS" "access token is invalid after logout" "$LOGOUT_ME_BODY"

echo
echo "== Password change =="
PW_LOGIN="$TMP_DIR/pw-login.json"
PW_JAR="$TMP_DIR/pw.jar"
PW_LOGIN_STATUS=$(curl -s -o "$PW_LOGIN" -c "$PW_JAR" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$PW_LOGIN_STATUS" "login before password change" "$PW_LOGIN"
PW_ACCESS=$(field data.accessToken < "$PW_LOGIN")

PW_CHANGE_BODY="$TMP_DIR/pw-change.json"
PW_CHANGE_STATUS=$(curl -s -o "$PW_CHANGE_BODY" -w '%{http_code}' -X PUT \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $PW_ACCESS" \
  -d "{\"oldPassword\":\"$TEST_PASSWORD\",\"newPassword\":\"$NEW_PASSWORD\"}" \
  "$BASE/api/v1/me/password")
expect_status 200 "$PW_CHANGE_STATUS" "password change succeeds" "$PW_CHANGE_BODY"

PW_OLD_ME_BODY="$TMP_DIR/pw-old-me.json"
PW_OLD_ME_STATUS=$(curl -s -o "$PW_OLD_ME_BODY" -w '%{http_code}' \
  -H "Authorization: Bearer $PW_ACCESS" \
  "$BASE/api/v1/me")
expect_status 401 "$PW_OLD_ME_STATUS" "old access token invalid after password change" "$PW_OLD_ME_BODY"

PW_OLD_LOGIN_BODY="$TMP_DIR/pw-old-login.json"
PW_OLD_LOGIN_STATUS=$(curl -s -o "$PW_OLD_LOGIN_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 401 "$PW_OLD_LOGIN_STATUS" "old password rejected" "$PW_OLD_LOGIN_BODY"

PW_NEW_LOGIN_BODY="$TMP_DIR/pw-new-login.json"
PW_NEW_LOGIN_STATUS=$(curl -s -o "$PW_NEW_LOGIN_BODY" -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$NEW_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$PW_NEW_LOGIN_STATUS" "new password accepted" "$PW_NEW_LOGIN_BODY"

echo
echo "ALL S02-a API CHECKS PASSED"
echo "test user: $TEST_USER (password changed to $NEW_PASSWORD)"