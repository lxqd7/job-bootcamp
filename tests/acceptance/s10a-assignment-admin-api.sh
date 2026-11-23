#!/usr/bin/env bash
# S10-a acceptance: formal assignment drafts, immutable versions, publish/offline, validation and isolation.
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

json_get() {
  node -e '
    const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    let v=d; for(const k of process.argv[2].split(".")) v=v==null?undefined:v[k];
    console.log(v==null?"":v);
  ' "$1" "$2"
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
}

create_course() {
  local title="$1" status
  status=$(curl -s -o "$TMP_DIR/course.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S10A\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create course" "$TMP_DIR/course.json"
  COURSE_ID=$(field data.id < "$TMP_DIR/course.json")
  COURSE_REV=$(field data.revision < "$TMP_DIR/course.json")
  status=$(curl -s -o "$TMP_DIR/chapter.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Chapter","sort":1}' \
    "$BASE/api/v1/admin/courses/$COURSE_ID/chapters")
  expect_status 200 "$status" "create chapter" "$TMP_DIR/chapter.json"
  CHAPTER_ID=$(field data.id < "$TMP_DIR/chapter.json")
}

create_assignment() {
  local kind="$1" course="$2" chapter="$3" public_file="$4" answer_file="$5" out="$6"
  node -e '
    const fs=require("fs");
    const p=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
    const a=JSON.parse(fs.readFileSync(process.argv[2],"utf8"));
    fs.writeFileSync(process.argv[3], JSON.stringify({
      kind: process.argv[4], courseId: Number(process.argv[5]),
      chapterId: process.argv[6] === "" ? null : Number(process.argv[6]),
      publicBody: p, answerBody: a
    }));
  ' "$public_file" "$answer_file" "$out.body" "$kind" "$course" "$chapter"
  curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" --data-binary @"$out.body" \
    "$BASE/api/v1/admin/assignments"
}

echo "== Setup course/chapter =="
login admin01 ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
create_course "S10A course $STAMP"
COURSE_A=$COURSE_ID
CHAPTER_A=$CHAPTER_ID
create_course "S10A course B $STAMP"
COURSE_B=$COURSE_ID
CHAPTER_B=$CHAPTER_ID
create_course "S10A course C $STAMP"
COURSE_C=$COURSE_ID
CHAPTER_C=$CHAPTER_ID

cat > "$TMP_DIR/choice-public.json" <<'JSON'
{"questions":[{"id":"q1","type":"SINGLE","body":"Java 常量关键字？","score":10,"options":[{"id":"A","text":"final"},{"id":"B","text":"static"}]},{"id":"q2","type":"MULTIPLE","body":"JVM 运行时区域？","score":20,"options":[{"id":"A","text":"堆"},{"id":"B","text":"栈"},{"id":"C","text":"方法区"}]}]}
JSON
cat > "$TMP_DIR/choice-answer.json" <<'JSON'
{"answers":{"q1":["A"],"q2":["A","B","C"]},"analysis":{"q1":"final 表示不可重新赋值。","q2":"三者都是运行时区域。"}}
JSON

echo "== Draft and validation =="
STATUS=$(create_assignment CHAPTER_CHOICE "$COURSE_A" "$CHAPTER_A" \
  "$TMP_DIR/choice-public.json" "$TMP_DIR/choice-answer.json" "$TMP_DIR/assign.json")
expect_status 200 "$STATUS" "create chapter-choice draft" "$TMP_DIR/assign.json"
ASSIGNMENT_ID=$(field data.id < "$TMP_DIR/assign.json")
ASSIGNMENT_REV=$(field data.revision < "$TMP_DIR/assign.json")
expect_value "DRAFT" "$(field data.status < "$TMP_DIR/assign.json")" "draft status"
expect_value "0" "$ASSIGNMENT_REV" "initial revision"
expect_value "q1" "$(field data.draftPublicBody.questions.0.id < "$TMP_DIR/assign.json")" "draft public body"
STATUS=$(create_assignment CHAPTER_CHOICE "$COURSE_A" "$CHAPTER_A" \
  "$TMP_DIR/choice-public.json" "$TMP_DIR/choice-answer.json" "$TMP_DIR/duplicate.json")
expect_status 409 "$STATUS" "duplicate formal scope rejected" "$TMP_DIR/duplicate.json"

cat > "$TMP_DIR/bad-choice-public.json" <<'JSON'
{"questions":[{"id":"q1","type":"SINGLE","body":"单选只能一个答案","score":10,"options":[{"id":"A","text":"A"},{"id":"B","text":"B"}]}]}
JSON
cat > "$TMP_DIR/bad-choice-answer.json" <<'JSON'
{"answers":{"q1":["A","B"]}}
JSON
STATUS=$(create_assignment CHAPTER_CHOICE "$COURSE_B" "$CHAPTER_B" \
  "$TMP_DIR/bad-choice-public.json" "$TMP_DIR/bad-choice-answer.json" "$TMP_DIR/bad-choice.json")
expect_status 422 "$STATUS" "single choice with two answers rejected" "$TMP_DIR/bad-choice.json"
BAD_ROWS=$(db "SELECT COUNT(*) FROM bc_assignment.assignment WHERE course_id=$COURSE_B AND chapter_id=$CHAPTER_B")
expect_value "0" "$BAD_ROWS" "invalid draft not persisted"

echo "== Publish immutable version =="
STATUS=$(curl -s -o "$TMP_DIR/publish1.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$ASSIGNMENT_REV}" \
  "$BASE/api/v1/admin/assignments/$ASSIGNMENT_ID/publish")
expect_status 200 "$STATUS" "publish version 1" "$TMP_DIR/publish1.json"
expect_value "ONLINE" "$(field data.status < "$TMP_DIR/publish1.json")" "published status"
expect_value "1" "$(field data.currentVersionNo < "$TMP_DIR/publish1.json")" "version no 1"
REV1=$(field data.revision < "$TMP_DIR/publish1.json")
VERSION1_ID=$(db "SELECT id FROM bc_assignment.assignment_version WHERE assignment_id=$ASSIGNMENT_ID AND version_no=1")
BODY1=$(db "SELECT JSON_UNQUOTE(JSON_EXTRACT(public_body,'$.questions[0].body')) FROM bc_assignment.assignment_version WHERE id=$VERSION1_ID")
ANSWER1=$(db "SELECT JSON_UNQUOTE(JSON_EXTRACT(answer_body,'$.answers.q1[0]')) FROM bc_assignment.assignment_version WHERE id=$VERSION1_ID")
expect_value "Java 常量关键字？" "$BODY1" "version public body stored"
expect_value "A" "$ANSWER1" "version answer stored separately"

node -e '
  const fs=require("fs"); const p=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  p.questions[0].body="Java 常量关键字（修订）？";
  fs.writeFileSync(process.argv[1], JSON.stringify(p));
' "$TMP_DIR/choice-public.json"
REV_PUBLISH1=$(field data.revision < "$TMP_DIR/publish1.json")
node -e '
  const fs=require("fs");
  fs.writeFileSync(process.argv[1], JSON.stringify({
    kind:"CHAPTER_CHOICE", courseId:Number(process.argv[2]), chapterId:Number(process.argv[3]),
    publicBody:JSON.parse(fs.readFileSync(process.argv[4],"utf8")),
    answerBody:JSON.parse(fs.readFileSync(process.argv[5],"utf8")),
    revision:Number(process.argv[6])
  }));
' "$TMP_DIR/update.body" "$COURSE_A" "$CHAPTER_A" "$TMP_DIR/choice-public.json" "$TMP_DIR/choice-answer.json" "$REV_PUBLISH1"
STATUS=$(curl -s -o "$TMP_DIR/update.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" --data-binary @"$TMP_DIR/update.body" \
  "$BASE/api/v1/admin/assignments/$ASSIGNMENT_ID")
expect_status 200 "$STATUS" "update published assignment draft" "$TMP_DIR/update.json"
REV2=$(field data.revision < "$TMP_DIR/update.json")
expect_value "1" "$(field data.currentVersionNo < "$TMP_DIR/update.json")" "current version unchanged after draft edit"
BODY_AFTER_EDIT=$(db "SELECT JSON_UNQUOTE(JSON_EXTRACT(public_body,'$.questions[0].body')) FROM bc_assignment.assignment_version WHERE id=$VERSION1_ID")
expect_value "Java 常量关键字？" "$BODY_AFTER_EDIT" "version 1 remains immutable"

STATUS=$(curl -s -o "$TMP_DIR/publish2.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$REV2}" \
  "$BASE/api/v1/admin/assignments/$ASSIGNMENT_ID/publish")
expect_status 200 "$STATUS" "publish version 2" "$TMP_DIR/publish2.json"
expect_value "2" "$(field data.currentVersionNo < "$TMP_DIR/publish2.json")" "version no 2"
VERSIONS=$(db "SELECT COUNT(*) FROM bc_assignment.assignment_version WHERE assignment_id=$ASSIGNMENT_ID")
expect_value "2" "$VERSIONS" "two immutable versions"
V1_STILL=$(db "SELECT JSON_UNQUOTE(JSON_EXTRACT(public_body,'$.questions[0].body')) FROM bc_assignment.assignment_version WHERE id=$VERSION1_ID")
expect_value "Java 常量关键字？" "$V1_STILL" "version 1 body after version 2"

echo "== Offline, republish and delete rules =="
STATUS=$(curl -s -o "$TMP_DIR/offline.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$(field data.revision < "$TMP_DIR/publish2.json")}" \
  "$BASE/api/v1/admin/assignments/$ASSIGNMENT_ID/offline")
expect_status 200 "$STATUS" "offline assignment" "$TMP_DIR/offline.json"
expect_value "OFFLINE" "$(field data.status < "$TMP_DIR/offline.json")" "offline status"
expect_value "2" "$(field data.currentVersionNo < "$TMP_DIR/offline.json")" "offline keeps version"
STATUS=$(curl -s -o "$TMP_DIR/delete-published.json" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/assignments/$ASSIGNMENT_ID?revision=$(field data.revision < "$TMP_DIR/offline.json")")
expect_status 409 "$STATUS" "published assignment cannot be deleted" "$TMP_DIR/delete-published.json"

cat > "$TMP_DIR/code-public.json" <<'JSON'
{"scenario":"订单金额计算","requirements":"补全 calculate，数量为负返回 0。","segments":[{"text":"public class Calc {\n"},{"slotId":"s1","initialCode":"// TODO\n"},{"text":"}\n"}]}
JSON
cat > "$TMP_DIR/code-answer.json" <<'JSON'
{"referenceBySlot":{"s1":"return quantity < 0 ? 0 : unit * quantity;\n"},"rubric":"负数、long、无编译错误。"}
JSON
STATUS=$(create_assignment COURSE_CODE "$COURSE_C" "" "$TMP_DIR/code-public.json" "$TMP_DIR/code-answer.json" "$TMP_DIR/code.json")
expect_status 200 "$STATUS" "create course-code draft" "$TMP_DIR/code.json"
CODE_ID=$(field data.id < "$TMP_DIR/code.json")
CODE_REV=$(field data.revision < "$TMP_DIR/code.json")
cat > "$TMP_DIR/bad-code-answer.json" <<'JSON'
{"referenceBySlot":{"s2":"wrong slot"},"rubric":"x"}
JSON
node -e '
  const fs=require("fs"); const p=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  fs.writeFileSync(process.argv[2], JSON.stringify({kind:"COURSE_CODE",publicBody:p,answerBody:JSON.parse(fs.readFileSync(process.argv[3],"utf8")),revision:Number(process.argv[4])}));
' "$TMP_DIR/code-public.json" "$TMP_DIR/bad-code-update.body" "$TMP_DIR/bad-code-answer.json" "$CODE_REV"
STATUS=$(curl -s -o "$TMP_DIR/bad-code-update.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" --data-binary @"$TMP_DIR/bad-code-update.body" \
  "$BASE/api/v1/admin/assignments/$CODE_ID")
expect_status 422 "$STATUS" "code reference slot mismatch rejected" "$TMP_DIR/bad-code-update.json"
CODE_STATE=$(db "SELECT status, revision FROM bc_assignment.assignment WHERE id=$CODE_ID" | tr '\t' '|')
expect_value "DRAFT|0" "$CODE_STATE" "invalid code update leaves draft"
STATUS=$(curl -s -o "$TMP_DIR/code-publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$CODE_REV}" \
  "$BASE/api/v1/admin/assignments/$CODE_ID/publish")
expect_status 200 "$STATUS" "publish course-code" "$TMP_DIR/code-publish.json"
expect_value "ONLINE" "$(field data.status < "$TMP_DIR/code-publish.json")" "code online"

create_course "S10A draft delete $STAMP"
COURSE_D=$COURSE_ID
CHAPTER_D=$CHAPTER_ID
STATUS=$(create_assignment CHAPTER_CHOICE "$COURSE_D" "$CHAPTER_D" \
  "$TMP_DIR/choice-public.json" "$TMP_DIR/choice-answer.json" "$TMP_DIR/delete-draft.json")
expect_status 200 "$STATUS" "create scratch draft" "$TMP_DIR/delete-draft.json"
DELETE_ID=$(field data.id < "$TMP_DIR/delete-draft.json")
DELETE_REV=$(field data.revision < "$TMP_DIR/delete-draft.json")
STATUS=$(curl -s -o "$TMP_DIR/deleted.json" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/assignments/$DELETE_ID?revision=$DELETE_REV")
expect_status 200 "$STATUS" "delete unpublished draft" "$TMP_DIR/deleted.json"
DELETED_COUNT=$(db "SELECT COUNT(*) FROM bc_assignment.assignment WHERE id=$DELETE_ID")
expect_value "0" "$DELETED_COUNT" "draft physically deleted"

echo "== Admin list and role/isolation =="
STATUS=$(curl -s -o "$TMP_DIR/list.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/assignments?courseId=$COURSE_A&page=1&pageSize=20")
expect_status 200 "$STATUS" "admin lists assignments" "$TMP_DIR/list.json"
FOUND=$(node -e '
  const fs=require("fs"); const d=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  console.log((d.data.items||[]).some(x=>String(x.id)===process.argv[2]) ? "yes" : "no");
' "$TMP_DIR/list.json" "$ASSIGNMENT_ID")
expect_value "yes" "$FOUND" "list contains assignment"

STATUS=$(curl -s -o "$TMP_DIR/student-login.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"student01\",\"password\":\"$DEMO_PASSWORD\",\"clientApp\":\"STUDENT\"}" \
  "$BASE/api/v1/auth/login")
expect_status 200 "$STATUS" "login student" "$TMP_DIR/student-login.json"
STUDENT_TOKEN=$(field data.accessToken < "$TMP_DIR/student-login.json")
STATUS=$(curl -s -o "$TMP_DIR/student-admin.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT_TOKEN" \
  "$BASE/api/v1/admin/assignments")
expect_status 403 "$STATUS" "student cannot list assignments" "$TMP_DIR/student-admin.json"
STATUS=$(curl -s -o "$TMP_DIR/anonymous.json" -w '%{http_code}' "$BASE/api/v1/admin/assignments")
expect_status 401 "$STATUS" "anonymous cannot list assignments" "$TMP_DIR/anonymous.json"

COURSE_STATUS=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/v1/admin/courses/$COURSE_A" | field data.status)
expect_value "DRAFT" "$COURSE_STATUS" "assignment draft does not change course status"

echo "ALL S10-A ACCEPTANCE CHECKS PASSED"
echo "assignment=$ASSIGNMENT_ID code=$CODE_ID course=$COURSE_A"