#!/usr/bin/env bash
# S12-a acceptance: teacher course+route scope, single benefit question, first-publish epoch binding,
# immutable route bindings across versions, and new-question-for-new-epoch boundary.
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
    if (Array.isArray(v)) console.log(JSON.stringify(v)); else console.log(v==null?"":v);
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
  local username="$1" password="$2" app="$3" out="$4" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"$password\",\"clientApp\":\"$app\"}" \
    "$BASE/api/v1/auth/login")
  expect_status 200 "$status" "login $username" "$out"
  TOKEN=$(field data.accessToken < "$out")
  USER_ID=$(field data.user.id < "$out")
}

create_course() {
  local title="$1" out="$2" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S12A\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
    "$BASE/api/v1/admin/courses")
  expect_status 200 "$status" "create course $title" "$out"
  local id
  id=$(field data.id < "$out")
  status=$(curl -s -o "$TMP_DIR/chapter.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Chapter","sort":1}' \
    "$BASE/api/v1/admin/courses/$id/chapters")
  expect_status 200 "$status" "create chapter $title" "$TMP_DIR/chapter.json"
  local chapter
  chapter=$(field data.id < "$TMP_DIR/chapter.json")
  status=$(curl -s -o "$TMP_DIR/lesson.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"title":"Video","sort":1,"mediaId":1}' \
    "$BASE/api/v1/admin/chapters/$chapter/lessons")
  expect_status 200 "$status" "create lesson $title" "$TMP_DIR/lesson.json"
  status=$(curl -s -o "$TMP_DIR/course-publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d '{"revision":0}' \
    "$BASE/api/v1/admin/courses/$id/publish")
  expect_status 200 "$status" "publish course $title" "$TMP_DIR/course-publish.json"
  CREATED_COURSE=$id
}

json_members() {
  local json="[" first=true sort=1
  for id in "$@"; do
    if [ "$first" = false ]; then json+=","; fi
    json+="{\"courseId\":$id,\"sort\":$sort}"; first=false; sort=$((sort+1))
  done
  echo "$json]"
}

create_route() {
  local title="$1" price="$2" members="$3" publish="$4" out="$5" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S12A route\",\"priceCent\":$price,\"members\":$members}" \
    "$BASE/api/v1/admin/routes")
  expect_status 200 "$status" "create route $title" "$out"
  local id rev
  id=$(field data.id < "$out")
  rev=$(field data.revision < "$out")
  if [ "$publish" = "true" ]; then
    status=$(curl -s -o "$TMP_DIR/route-publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
      -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$rev}" \
      "$BASE/api/v1/admin/routes/$id/publish")
    expect_status 200 "$status" "publish route $title" "$TMP_DIR/route-publish.json"
    ROUTE_REV=$(field data.revision < "$TMP_DIR/route-publish.json")
  else
    ROUTE_REV=$rev
  fi
  CREATED_ROUTE=$id
}

update_route() {
  local id="$1" title="$2" price="$3" members="$4" revision="$5" out="$6" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S12A route\",\"priceCent\":$price,\"members\":$members,\"revision\":$revision}" \
    "$BASE/api/v1/admin/routes/$id")
  expect_status 200 "$status" "update route $title" "$out"
  ROUTE_REV=$(field data.revision < "$out")
}

publish_route() {
  local id="$1" revision="$2" out="$3" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$revision}" \
    "$BASE/api/v1/admin/routes/$id/publish")
  expect_status 200 "$status" "publish route $id" "$out"
  ROUTE_REV=$(field data.revision < "$out")
}

benefit_create() {
  local kind="$1" course="$2" routes="$3" public_file="$4" answer_file="$5" out="$6" status
  node -e '
    const fs=require("fs");
    fs.writeFileSync(process.argv[1], JSON.stringify({
      kind:process.argv[2], courseId:Number(process.argv[3]),
      routeIds:JSON.parse(process.argv[4]),
      publicBody:JSON.parse(fs.readFileSync(process.argv[5],"utf8")),
      answerBody:JSON.parse(fs.readFileSync(process.argv[6],"utf8"))
    }));
  ' "$out.body" "$kind" "$course" "$routes" "$public_file" "$answer_file"
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TEACHER_TOKEN" --data-binary @"$out.body" \
    "$BASE/api/v1/teacher/benefit-assignments")
  echo "$status"
}

benefit_publish() {
  local id="$1" revision="$2" out="$3" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TEACHER_TOKEN" -d "{\"revision\":$revision}" \
    "$BASE/api/v1/teacher/benefit-assignments/$id/publish")
  echo "$status"
}

route_epoch() {
  db "SELECT epoch_id FROM bc_course.route_member_current WHERE route_id=$1 AND course_id=$2"
}

bound_epochs() {
  db "SELECT GROUP_CONCAT(CONCAT(route_id,':',epoch_id) ORDER BY route_id) FROM bc_assignment.assignment_route WHERE assignment_id=$1"
}

echo "== Setup courses, routes and teacher scopes =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
create_course "S12A_C_$STAMP" "$TMP_DIR/course-c.json"
COURSE_C=$CREATED_COURSE
create_course "S12A_B_$STAMP" "$TMP_DIR/course-b.json"
COURSE_B=$CREATED_COURSE
create_course "S12A_D_$STAMP" "$TMP_DIR/course-d.json"
COURSE_D=$CREATED_COURSE

create_route "S12A_R1_$STAMP" 600 "$(json_members "$COURSE_C" "$COURSE_B" "$COURSE_D")" true "$TMP_DIR/r1.json"
R1=$CREATED_ROUTE
R1_REV=$ROUTE_REV
create_route "S12A_R2_$STAMP" 300 "$(json_members "$COURSE_C" "$COURSE_B")" true "$TMP_DIR/r2.json"
R2=$CREATED_ROUTE
R2_REV=$ROUTE_REV
create_route "S12A_R3_$STAMP" 300 "$(json_members "$COURSE_B" "$COURSE_D")" true "$TMP_DIR/r3.json"
R3=$CREATED_ROUTE
create_route "S12A_R4_$STAMP" 300 "$(json_members "$COURSE_C" "$COURSE_B")" false "$TMP_DIR/r4.json"
R4=$CREATED_ROUTE
create_route "S12A_R5_$STAMP" 300 "$(json_members "$COURSE_C" "$COURSE_B")" true "$TMP_DIR/r5.json"
R5=$CREATED_ROUTE

login teacher01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/teacher1.json"
TEACHER_TOKEN=$TOKEN
TEACHER_ID=$USER_ID
login teacher02 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/teacher2.json"
TEACHER2_TOKEN=$TOKEN
TEACHER2_ID=$USER_ID

STATUS=$(curl -s -o "$TMP_DIR/t1-current.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "read teacher01 scope" "$TMP_DIR/t1-current.json"
STATUS=$(curl -s -o "$TMP_DIR/t1-save.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[$COURSE_C],\"routeIds\":[$R1,$R2,$R3,$R4],\"revision\":$(field data.revision < "$TMP_DIR/t1-current.json")}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "assign teacher01 course C + R1-R4" "$TMP_DIR/t1-save.json"
STATUS=$(curl -s -o "$TMP_DIR/t2-current.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/teachers/$TEACHER2_ID/assignments")
expect_status 200 "$STATUS" "read teacher02 scope" "$TMP_DIR/t2-current.json"
STATUS=$(curl -s -o "$TMP_DIR/t2-save.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[],\"routeIds\":[$R1],\"revision\":$(field data.revision < "$TMP_DIR/t2-current.json")}" \
  "$BASE/api/v1/admin/teachers/$TEACHER2_ID/assignments")
expect_status 200 "$STATUS" "assign teacher02 route only" "$TMP_DIR/t2-save.json"
TEACHER_TOKEN=$TOKEN

echo "== Benefit choice must be exactly one question =="
cat > "$TMP_DIR/two-public.json" <<'JSON'
{"questions":[{"id":"q1","type":"SINGLE","body":"题1","score":10,"options":[{"id":"A","text":"A"},{"id":"B","text":"B"}]},{"id":"q2","type":"SINGLE","body":"题2","score":10,"options":[{"id":"A","text":"A"},{"id":"B","text":"B"}]}]}
JSON
cat > "$TMP_DIR/two-answer.json" <<'JSON'
{"answers":{"q1":["A"],"q2":["B"]},"analysis":{"q1":"解析1","q2":"解析2"}}
JSON
TEACHER_TOKEN=$(field data.accessToken < "$TMP_DIR/teacher1.json")
STATUS=$(benefit_create BENEFIT_CHOICE "$COURSE_C" "[$R1]" "$TMP_DIR/two-public.json" "$TMP_DIR/two-answer.json" "$TMP_DIR/two.json")
expect_status 422 "$STATUS" "two-question benefit rejected" "$TMP_DIR/two.json"

cat > "$TMP_DIR/one-public.json" <<'JSON'
{"questions":[{"id":"q1","type":"MULTIPLE","body":"福利题：JVM 区域？","score":10,"options":[{"id":"A","text":"堆"},{"id":"B","text":"栈"},{"id":"C","text":"磁盘"}]}]}
JSON
cat > "$TMP_DIR/one-answer.json" <<'JSON'
{"answers":{"q1":["A","B"]},"analysis":{"q1":"堆和栈属于运行时数据区。"}}
JSON
STATUS=$(benefit_create BENEFIT_CHOICE "$COURSE_C" "[$R1,$R2]" "$TMP_DIR/one-public.json" "$TMP_DIR/one-answer.json" "$TMP_DIR/b1.json")
expect_status 200 "$STATUS" "create benefit draft with two routes" "$TMP_DIR/b1.json"
B1=$(field data.id < "$TMP_DIR/b1.json")
B1_REV=$(field data.revision < "$TMP_DIR/b1.json")
expect_value "DRAFT" "$(field data.status < "$TMP_DIR/b1.json")" "draft status"
expect_value "[]" "$(field data.routes < "$TMP_DIR/b1.json")" "no route binding before publish"

STATUS=$(curl -s -o "$TMP_DIR/list.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/benefit-assignments?courseId=$COURSE_C&page=1&pageSize=20")
expect_status 200 "$STATUS" "teacher lists benefits" "$TMP_DIR/list.json"
expect_value "1" "$(field data.total < "$TMP_DIR/list.json")" "one benefit draft"
expect_value "$R1,$R2" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.items[0].routeIds.join(","))' "$TMP_DIR/list.json")" "draft route ids listed"
STATUS=$(curl -s -o "$TMP_DIR/admin-formal.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/assignments?courseId=$COURSE_C")
expect_status 200 "$STATUS" "admin formal list" "$TMP_DIR/admin-formal.json"
expect_value "0" "$(field data.total < "$TMP_DIR/admin-formal.json")" "benefit not in formal admin list"

node -e '
  const fs=require("fs"); const p=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  p.routeIds=[Number(process.argv[2])];
  const body=JSON.parse(fs.readFileSync(process.argv[3],"utf8"));
  body.routeIds=p.routeIds;
  fs.writeFileSync(process.argv[3],JSON.stringify(body));
' "$TMP_DIR/b1.json.body" "$R1" "$TMP_DIR/b1.json.body"
STATUS=$(curl -s -o "$TMP_DIR/change-route.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEACHER_TOKEN" --data-binary @"$TMP_DIR/b1.json.body" \
  "$BASE/api/v1/teacher/benefit-assignments/$B1")
expect_status 409 "$STATUS" "route change before first publish rejected" "$TMP_DIR/change-route.json"

echo "== First publish binds each route current epoch =="
EPOCH_R1_OLD=$(route_epoch "$R1" "$COURSE_C")
EPOCH_R2=$(route_epoch "$R2" "$COURSE_C")
STATUS=$(benefit_publish "$B1" "$B1_REV" "$TMP_DIR/b1-publish.json")
expect_status 200 "$STATUS" "publish benefit first version" "$TMP_DIR/b1-publish.json"
B1_REV=$(field data.revision < "$TMP_DIR/b1-publish.json")
expect_value "ONLINE" "$(field data.status < "$TMP_DIR/b1-publish.json")" "published benefit online"
expect_value "1" "$(field data.currentVersionNo < "$TMP_DIR/b1-publish.json")" "version 1"
expect_value "2" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.routes.length)' "$TMP_DIR/b1-publish.json")" "two route bindings"
EXPECTED_BOUND=$(printf "%s:%s,%s:%s" "$R1" "$EPOCH_R1_OLD" "$R2" "$EPOCH_R2" | tr ',' '\n' | sort -n -t: -k1 | paste -sd,)
EXPECTED_BOUND=$(node -e 'const [r1,e1,r2,e2]=process.argv.slice(1);console.log([[r1,e1],[r2,e2]].sort((a,b)=>a[0]-b[0]).map(x=>x.join(":")).join(","))' "$R1" "$EPOCH_R1_OLD" "$R2" "$EPOCH_R2")
expect_value "$EXPECTED_BOUND" "$(bound_epochs "$B1")" "assignment_route stores route epochs"

echo "== Content version keeps route/epoch binding =="
node -e '
  const fs=require("fs"); const p=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  p.questions[0].body="福利题：JVM 区域（v2）？";
  fs.writeFileSync(process.argv[1],JSON.stringify(p));
' "$TMP_DIR/one-public.json"
node -e '
  const fs=require("fs");
  const body=JSON.parse(fs.readFileSync(process.argv[1],"utf8"));
  body.publicBody=JSON.parse(fs.readFileSync(process.argv[2],"utf8"));
  body.revision=Number(process.argv[3]);
  body.routeIds=[Number(process.argv[4]),Number(process.argv[5])];
  fs.writeFileSync(process.argv[1],JSON.stringify(body));
' "$TMP_DIR/b1.json.body" "$TMP_DIR/one-public.json" "$B1_REV" "$R1" "$R2"
STATUS=$(curl -s -o "$TMP_DIR/b1-update.json" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEACHER_TOKEN" --data-binary @"$TMP_DIR/b1.json.body" \
  "$BASE/api/v1/teacher/benefit-assignments/$B1")
expect_status 200 "$STATUS" "update published benefit content" "$TMP_DIR/b1-update.json"
B1_REV=$(field data.revision < "$TMP_DIR/b1-update.json")
STATUS=$(benefit_publish "$B1" "$B1_REV" "$TMP_DIR/b1-publish2.json")
expect_status 200 "$STATUS" "publish second content version" "$TMP_DIR/b1-publish2.json"
B1_REV=$(field data.revision < "$TMP_DIR/b1-publish2.json")
expect_value "2" "$(field data.currentVersionNo < "$TMP_DIR/b1-publish2.json")" "version 2"
expect_value "$EXPECTED_BOUND" "$(bound_epochs "$B1")" "route epochs unchanged by content version"

echo "== New epoch requires a new benefit question =="
# R1: [C,B] -> [B] closes C epoch, then [C,B] creates a new C epoch.
update_route "$R1" "S12A_R1_$STAMP" 600 "$(json_members "$COURSE_B" "$COURSE_D")" "$R1_REV" "$TMP_DIR/r1-remove.json"
publish_route "$R1" "$ROUTE_REV" "$TMP_DIR/r1-remove-publish.json"
update_route "$R1" "S12A_R1_$STAMP" 600 "$(json_members "$COURSE_C" "$COURSE_B" "$COURSE_D")" "$ROUTE_REV" "$TMP_DIR/r1-add.json"
publish_route "$R1" "$ROUTE_REV" "$TMP_DIR/r1-add-publish.json"
EPOCH_R1_NEW=$(route_epoch "$R1" "$COURSE_C")
if [ "$EPOCH_R1_NEW" = "$EPOCH_R1_OLD" ]; then echo "FAIL  route epoch did not change"; exit 1; fi
echo "PASS  route re-add created a new epoch  ($EPOCH_R1_OLD -> $EPOCH_R1_NEW)"
STATUS=$(benefit_publish "$B1" "$B1_REV" "$TMP_DIR/b1-publish3.json")
expect_status 200 "$STATUS" "existing benefit keeps old epoch" "$TMP_DIR/b1-publish3.json"
B1_REV=$(field data.revision < "$TMP_DIR/b1-publish3.json")
expect_value "$EXPECTED_BOUND" "$(bound_epochs "$B1")" "existing benefit did not rebind"

STATUS=$(benefit_create BENEFIT_CHOICE "$COURSE_C" "[$R1]" "$TMP_DIR/one-public.json" "$TMP_DIR/one-answer.json" "$TMP_DIR/b2.json")
expect_status 200 "$STATUS" "create new question for new epoch" "$TMP_DIR/b2.json"
B2=$(field data.id < "$TMP_DIR/b2.json")
B2_REV=$(field data.revision < "$TMP_DIR/b2.json")
STATUS=$(benefit_publish "$B2" "$B2_REV" "$TMP_DIR/b2-publish.json")
expect_status 200 "$STATUS" "publish new epoch question" "$TMP_DIR/b2-publish.json"
expect_value "$(printf "%s:%s" "$R1" "$EPOCH_R1_NEW" | tr -d '\n')" "$(bound_epochs "$B2")" "new question binds new epoch"

echo "== Scope, route state and role boundaries =="
TEACHER_TOKEN=$(field data.accessToken < "$TMP_DIR/teacher1.json")
STATUS=$(benefit_create BENEFIT_CHOICE "$COURSE_C" "[$R3]" "$TMP_DIR/one-public.json" "$TMP_DIR/one-answer.json" "$TMP_DIR/r3.json")
expect_status 409 "$STATUS" "route without course rejected" "$TMP_DIR/r3.json"
STATUS=$(benefit_create BENEFIT_CHOICE "$COURSE_C" "[$R4]" "$TMP_DIR/one-public.json" "$TMP_DIR/one-answer.json" "$TMP_DIR/r4.json")
expect_status 409 "$STATUS" "unpublished route rejected" "$TMP_DIR/r4.json"
STATUS=$(benefit_create BENEFIT_CHOICE "$COURSE_C" "[$R5]" "$TMP_DIR/one-public.json" "$TMP_DIR/one-answer.json" "$TMP_DIR/r5.json")
expect_status 403 "$STATUS" "unassigned route rejected" "$TMP_DIR/r5.json"
TEACHER_TOKEN=$(field data.accessToken < "$TMP_DIR/teacher2.json")
STATUS=$(benefit_create BENEFIT_CHOICE "$COURSE_C" "[$R1]" "$TMP_DIR/one-public.json" "$TMP_DIR/one-answer.json" "$TMP_DIR/t2-create.json")
expect_status 403 "$STATUS" "teacher without course scope rejected" "$TMP_DIR/t2-create.json"
TEACHER_TOKEN=$(field data.accessToken < "$TMP_DIR/teacher1.json")
STATUS=$(curl -s -o "$TMP_DIR/admin-teacher.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/teacher/benefit-assignments?courseId=$COURSE_C")
expect_status 403 "$STATUS" "admin cannot use teacher benefit API" "$TMP_DIR/admin-teacher.json"

echo "== Benefit code single-task version and offline/delete rules =="
cat > "$TMP_DIR/code-public.json" <<'JSON'
{"scenario":"福利代码题","requirements":"补全返回值","segments":[{"text":"public class B {\n"},{"slotId":"s1","initialCode":"// TODO\n"},{"text":"}\n"}]}
JSON
cat > "$TMP_DIR/code-answer.json" <<'JSON'
{"referenceBySlot":{"s1":"return 1;\n"},"rubric":"返回值正确"}
JSON
STATUS=$(benefit_create BENEFIT_CODE "$COURSE_C" "[$R2]" "$TMP_DIR/code-public.json" "$TMP_DIR/code-answer.json" "$TMP_DIR/code.json")
expect_status 200 "$STATUS" "create benefit code draft" "$TMP_DIR/code.json"
BCODE=$(field data.id < "$TMP_DIR/code.json")
BCODE_REV=$(field data.revision < "$TMP_DIR/code.json")
STATUS=$(benefit_publish "$BCODE" "$BCODE_REV" "$TMP_DIR/code-publish.json")
expect_status 200 "$STATUS" "publish benefit code" "$TMP_DIR/code-publish.json"
expect_value "1" "$(field data.currentVersionNo < "$TMP_DIR/code-publish.json")" "code version 1"
BCODE_REV=$(field data.revision < "$TMP_DIR/code-publish.json")

STATUS=$(curl -s -o "$TMP_DIR/offline.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEACHER_TOKEN" -d "{\"revision\":$BCODE_REV}" \
  "$BASE/api/v1/teacher/benefit-assignments/$BCODE/offline")
expect_status 200 "$STATUS" "offline benefit code" "$TMP_DIR/offline.json"
BCODE_REV=$(field data.revision < "$TMP_DIR/offline.json")
expect_value "OFFLINE" "$(field data.status < "$TMP_DIR/offline.json")" "offline status"
expect_value "1" "$(db "SELECT COUNT(*) FROM bc_assignment.assignment_route WHERE assignment_id=$BCODE")" "offline keeps route binding"
STATUS=$(curl -s -o "$TMP_DIR/delete-published.json" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/benefit-assignments/$BCODE?revision=$BCODE_REV")
expect_status 409 "$STATUS" "published benefit cannot delete" "$TMP_DIR/delete-published.json"

STATUS=$(benefit_create BENEFIT_CHOICE "$COURSE_C" "[$R1]" "$TMP_DIR/one-public.json" "$TMP_DIR/one-answer.json" "$TMP_DIR/b3.json")
expect_status 200 "$STATUS" "create disposable draft" "$TMP_DIR/b3.json"
B3=$(field data.id < "$TMP_DIR/b3.json")
B3_REV=$(field data.revision < "$TMP_DIR/b3.json")
STATUS=$(curl -s -o "$TMP_DIR/delete-draft.json" -w '%{http_code}' -X DELETE \
  -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/benefit-assignments/$B3?revision=$B3_REV")
expect_status 200 "$STATUS" "delete unpublished draft" "$TMP_DIR/delete-draft.json"
STATUS=$(curl -s -o "$TMP_DIR/get-deleted.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/benefit-assignments/$B3")
expect_status 404 "$STATUS" "deleted draft is gone" "$TMP_DIR/get-deleted.json"

echo "== Student course assignment list does not include benefit =="
USERNAME="s12a_$STAMP"
curl -s -o "$TMP_DIR/register.json" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"Passw0rd!\",\"nickname\":\"S12ATester\"}" \
  "$BASE/api/v1/auth/register"
login "$USERNAME" "Passw0rd!" STUDENT "$TMP_DIR/student.json"
STUDENT_TOKEN=$TOKEN
STATUS=$(curl -s -o "$TMP_DIR/quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d "{\"productType\":\"COURSE\",\"productId\":$COURSE_C,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
  "$BASE/api/v1/trade/quotes")
expect_status 200 "$STATUS" "student quote course C" "$TMP_DIR/quote.json"
QUOTE_ID=$(field data.quoteId < "$TMP_DIR/quote.json")
QUOTE_HASH=$(field data.quoteHash < "$TMP_DIR/quote.json")
STATUS=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
  -H "Idempotency-Key: s12a-$STAMP" -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d "{\"quoteId\":\"$QUOTE_ID\",\"quoteHash\":\"$QUOTE_HASH\",\"selectedCouponIds\":[]}" \
  "$BASE/api/v1/orders")
expect_status 200 "$STATUS" "student orders course C" "$TMP_DIR/order.json"
ORDER_ID=$(field data.id < "$TMP_DIR/order.json")
curl -s -o /dev/null -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $STUDENT_TOKEN" \
  -d '{"channel":"MOCK"}' "$BASE/api/v1/orders/$ORDER_ID/payment"
curl -s -o /dev/null -X POST -H "Authorization: Bearer $STUDENT_TOKEN" \
  "$BASE/api/v1/orders/$ORDER_ID/payment/mock-pay"
for attempt in $(seq 1 40); do
  curl -s -o "$TMP_DIR/order-state.json" -H "Authorization: Bearer $STUDENT_TOKEN" "$BASE/api/v1/orders/$ORDER_ID"
  if [ "$(field data.fulfillmentState < "$TMP_DIR/order-state.json")" = "DONE" ]; then break; fi
  sleep 0.5
done
STATUS=$(curl -s -o "$TMP_DIR/course-assignments.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT_TOKEN" \
  "$BASE/api/v1/courses/$COURSE_C/assignments")
expect_status 200 "$STATUS" "student course assignments" "$TMP_DIR/course-assignments.json"
expect_value "0" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.length)' "$TMP_DIR/course-assignments.json")" "benefit not exposed as formal assignment"

echo "ALL S12-A ACCEPTANCE CHECKS PASSED"