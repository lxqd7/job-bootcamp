#!/usr/bin/env bash
# S12-b acceptance: course+route benefit access, historical epoch, refund freeze, offline,
# teacher removal and write-time recheck.
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
  local username="$1" out="$2" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"username\":\"$username\",\"password\":\"Passw0rd!\",\"nickname\":\"$username\"}" \
    "$BASE/api/v1/auth/register")
  expect_status 200 "$status" "register $username" "$out"
}

create_course() {
  local title="$1" out="$2" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S12B\",\"categoryId\":2,\"priceCent\":990,\"isFree\":false}" \
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
  local title="$1" price="$2" members="$3" out="$4" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S12B route\",\"priceCent\":$price,\"members\":$members}" \
    "$BASE/api/v1/admin/routes")
  expect_status 200 "$status" "create route $title" "$out"
  local id rev
  id=$(field data.id < "$out")
  rev=$(field data.revision < "$out")
  status=$(curl -s -o "$TMP_DIR/route-publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" -d "{\"revision\":$rev}" \
    "$BASE/api/v1/admin/routes/$id/publish")
  expect_status 200 "$status" "publish route $title" "$TMP_DIR/route-publish.json"
  CREATED_ROUTE=$id
  ROUTE_REV=$(field data.revision < "$TMP_DIR/route-publish.json")
}

update_route() {
  local id="$1" title="$2" price="$3" members="$4" revision="$5" out="$6" status
  status=$(curl -s -o "$out" -w '%{http_code}' -X PATCH -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -d "{\"title\":\"$title\",\"description\":\"S12B route\",\"priceCent\":$price,\"members\":$members,\"revision\":$revision}" \
    "$BASE/api/v1/admin/routes/$id")
  expect_status 200 "$status" "update route $id" "$out"
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

fulfill_order() {
  local token="$1" product_type="$2" product_id="$3" out_prefix="$4" idempotency="$5"
  local status quote_id quote_hash order_id
  status=$(curl -s -o "$TMP_DIR/quote.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" \
    -d "{\"productType\":\"$product_type\",\"productId\":$product_id,\"couponSelection\":{\"mode\":\"NONE\",\"ids\":[]}}" \
    "$BASE/api/v1/trade/quotes")
  expect_status 200 "$status" "quote $product_type" "$TMP_DIR/quote.json"
  quote_id=$(field data.quoteId < "$TMP_DIR/quote.json")
  quote_hash=$(field data.quoteHash < "$TMP_DIR/quote.json")
  status=$(curl -s -o "$TMP_DIR/order.json" -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "Idempotency-Key: $idempotency" -H "Authorization: Bearer $token" \
    -d "{\"quoteId\":\"$quote_id\",\"quoteHash\":\"$quote_hash\",\"selectedCouponIds\":[]}" \
    "$BASE/api/v1/orders")
  expect_status 200 "$status" "create $product_type order" "$TMP_DIR/order.json"
  order_id=$(field data.id < "$TMP_DIR/order.json")
  status=$(curl -s -o "$TMP_DIR/payment.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $token" -d '{"channel":"MOCK"}' \
    "$BASE/api/v1/orders/$order_id/payment")
  expect_status 200 "$status" "payment request $product_type" "$TMP_DIR/payment.json"
  status=$(curl -s -o "$TMP_DIR/pay.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $token" \
    "$BASE/api/v1/orders/$order_id/payment/mock-pay")
  expect_status 200 "$status" "mock pay $product_type" "$TMP_DIR/pay.json"
  for attempt in $(seq 1 50); do
    curl -s -o "$TMP_DIR/order-state.json" -H "Authorization: Bearer $token" "$BASE/api/v1/orders/$order_id"
    if [ "$(field data.fulfillmentState < "$TMP_DIR/order-state.json")" = "DONE" ]; then
      echo "PASS  $product_type fulfilled" >&2
      FULFILLED_ORDER=$order_id
      return 0
    fi
    sleep 0.4
  done
  echo "FAIL  order not fulfilled"; cat "$TMP_DIR/order-state.json"; exit 1
}

benefit_create_publish() {
  local public_file="$1" answer_file="$2" out="$3"
  node -e '
    const fs=require("fs");
    fs.writeFileSync(process.argv[1], JSON.stringify({
      kind:"BENEFIT_CHOICE", courseId:Number(process.argv[2]), routeIds:[Number(process.argv[3])],
      publicBody:JSON.parse(fs.readFileSync(process.argv[4],"utf8")),
      answerBody:JSON.parse(fs.readFileSync(process.argv[5],"utf8"))
    }));
  ' "$out.body" "$COURSE_C" "$ROUTE_ID" "$public_file" "$answer_file"
  STATUS=$(curl -s -o "$out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TEACHER_TOKEN" --data-binary @"$out.body" \
    "$BASE/api/v1/teacher/benefit-assignments")
  expect_status 200 "$STATUS" "create benefit" "$out"
  local id rev
  id=$(field data.id < "$out")
  rev=$(field data.revision < "$out")
  STATUS=$(curl -s -o "$TMP_DIR/benefit-publish.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $TEACHER_TOKEN" -d "{\"revision\":$rev}" \
    "$BASE/api/v1/teacher/benefit-assignments/$id/publish")
  expect_status 200 "$STATUS" "publish benefit" "$TMP_DIR/benefit-publish.json"
  CREATED_BENEFIT=$id
  BENEFIT_REV=$(field data.revision < "$TMP_DIR/benefit-publish.json")
}

benefit_list() {
  local token="$1" route="$2" out="$3" status
  status=$(curl -s -o "$out" -w '%{http_code}' -H "Authorization: Bearer $token" \
    "$BASE/api/v1/routes/$route/benefit-assignments?courseId=$COURSE_C")
  echo "$status"
}

wait_grant() {
  local user_id="$1" route_id="$2" course_id="$3" epoch_id="$4"
  for attempt in $(seq 1 40); do
    local count
    count=$(db "SELECT COUNT(*) FROM bc_learning.grant_route_member gr JOIN bc_learning.route_entitlement re ON re.source_id=gr.source_id JOIN bc_learning.entitlement_source es ON es.id=re.source_id WHERE re.user_id=$user_id AND re.route_id=$route_id AND gr.course_id=$course_id AND gr.epoch_id=$epoch_id AND es.state='ACTIVE'")
    if [ "$count" -gt 0 ]; then echo "PASS  route grant propagated"; return 0; fi
    sleep 0.3
  done
  echo "FAIL  grant did not propagate"; exit 1
}

echo "== Setup courses, route, teacher and benefit =="
login admin01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/admin.json"
ADMIN_TOKEN=$TOKEN
create_course "S12B_C_$STAMP" "$TMP_DIR/course-c.json"; COURSE_C=$CREATED_COURSE
create_course "S12B_B_$STAMP" "$TMP_DIR/course-b.json"; COURSE_B=$CREATED_COURSE
create_course "S12B_D_$STAMP" "$TMP_DIR/course-d.json"; COURSE_D=$CREATED_COURSE
create_route "S12B_R_$STAMP" 300 "$(json_members "$COURSE_C" "$COURSE_B" "$COURSE_D")" "$TMP_DIR/route.json"
ROUTE_ID=$CREATED_ROUTE
ROUTE_TITLE="S12B_R_$STAMP"

login teacher01 "$DEMO_PASSWORD" ADMIN "$TMP_DIR/teacher.json"
TEACHER_TOKEN=$TOKEN
TEACHER_ID=$USER_ID
STATUS=$(curl -s -o "$TMP_DIR/t-current.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "read teacher scope" "$TMP_DIR/t-current.json"
STATUS=$(curl -s -o "$TMP_DIR/t-save.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[$COURSE_C],\"routeIds\":[$ROUTE_ID],\"revision\":$(field data.revision < "$TMP_DIR/t-current.json")}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "assign teacher course+route" "$TMP_DIR/t-save.json"

cat > "$TMP_DIR/one-public.json" <<'JSON'
{"questions":[{"id":"q1","type":"MULTIPLE","body":"福利题：JVM 区域？","score":10,"options":[{"id":"A","text":"堆"},{"id":"B","text":"栈"},{"id":"C","text":"磁盘"}]}]}
JSON
cat > "$TMP_DIR/one-answer.json" <<'JSON'
{"answers":{"q1":["A","B"]},"analysis":{"q1":"堆和栈属于运行时数据区。"}}
JSON
benefit_create_publish "$TMP_DIR/one-public.json" "$TMP_DIR/one-answer.json" "$TMP_DIR/benefit.json"
BENEFIT_ID=$CREATED_BENEFIT
B1_REV=$BENEFIT_REV
EPOCH_OLD=$(db "SELECT GROUP_CONCAT(epoch_id) FROM bc_assignment.assignment_route WHERE assignment_id=$BENEFIT_ID AND route_id=$ROUTE_ID")
echo "INFO  benefit=$BENEFIT_ID epoch=$EPOCH_OLD"

echo "== Student1 buys route before removal: course + route benefit access =="
register "s12b_old_$STAMP" "$TMP_DIR/reg-old.json"
login "s12b_old_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/old.json"
STUDENT1_TOKEN=$TOKEN
STUDENT1_ID=$USER_ID
fulfill_order "$STUDENT1_TOKEN" ROUTE "$ROUTE_ID" old "s12b-old-$STAMP"
STATUS=$(benefit_list "$STUDENT1_TOKEN" "$ROUTE_ID" "$TMP_DIR/old-list.json")
expect_status 200 "$STATUS" "old student lists route benefits" "$TMP_DIR/old-list.json"
expect_value "1" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.length)' "$TMP_DIR/old-list.json")" "old student sees bound benefit"
STATUS=$(curl -s -o "$TMP_DIR/old-detail.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$BENEFIT_ID")
expect_status 200 "$STATUS" "old student reads benefit" "$TMP_DIR/old-detail.json"
if grep -q "analysis\|answers" "$TMP_DIR/old-detail.json"; then echo "FAIL  benefit answer leaked"; exit 1; fi
echo "PASS  student benefit detail hides answers"
STATUS=$(curl -s -o "$TMP_DIR/old-attempt.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $STUDENT1_TOKEN" "$BASE/api/v1/benefit-assignments/$BENEFIT_ID/attempts")
expect_status 200 "$STATUS" "old student starts benefit attempt" "$TMP_DIR/old-attempt.json"
ATTEMPT1=$(field data.id < "$TMP_DIR/old-attempt.json")
STATUS=$(curl -s -o "$TMP_DIR/old-submit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: s12b-old-submit" -H "Authorization: Bearer $STUDENT1_TOKEN" \
  -d '{"answers":{"q1":["A","B"]}}' \
  "$BASE/api/v1/attempts/$ATTEMPT1/submissions")
expect_status 200 "$STATUS" "submit benefit choice" "$TMP_DIR/old-submit.json"
SUBMISSION1=$(field data.id < "$TMP_DIR/old-submit.json")
expect_value "10" "$(field data.score < "$TMP_DIR/old-submit.json")" "benefit score"
expect_value "堆和栈属于运行时数据区。" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.analysis.q1)' "$TMP_DIR/old-submit.json")" "benefit analysis after submit"

echo "== Course-only student has no route benefit =="
register "s12b_course_$STAMP" "$TMP_DIR/reg-course.json"
login "s12b_course_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/course.json"
STUDENT2_TOKEN=$TOKEN
STUDENT2_ID=$USER_ID
fulfill_order "$STUDENT2_TOKEN" COURSE "$COURSE_C" course "s12b-course-$STAMP"
STATUS=$(benefit_list "$STUDENT2_TOKEN" "$ROUTE_ID" "$TMP_DIR/course-list.json")
expect_status 200 "$STATUS" "course-only lists benefits" "$TMP_DIR/course-list.json"
expect_value "0" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.length)' "$TMP_DIR/course-list.json")" "course-only sees no benefit"
STATUS=$(curl -s -o "$TMP_DIR/course-detail.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT2_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$BENEFIT_ID")
expect_status 403 "$STATUS" "course-only cannot read benefit" "$TMP_DIR/course-detail.json"
STATUS=$(curl -s -o "$TMP_DIR/course-attempt.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $STUDENT2_TOKEN" "$BASE/api/v1/benefit-assignments/$BENEFIT_ID/attempts")
expect_status 403 "$STATUS" "course-only cannot start benefit" "$TMP_DIR/course-attempt.json"

echo "== Old student keeps historical epoch after route removes course =="
update_route "$ROUTE_ID" "$ROUTE_TITLE" 300 "$(json_members "$COURSE_B" "$COURSE_D")" "$ROUTE_REV" "$TMP_DIR/route-remove.json"
publish_route "$ROUTE_ID" "$ROUTE_REV" "$TMP_DIR/route-remove-publish.json"
STATUS=$(curl -s -o "$TMP_DIR/old-after-remove.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$BENEFIT_ID")
expect_status 200 "$STATUS" "old student keeps old epoch access" "$TMP_DIR/old-after-remove.json"
STATUS=$(curl -s -o "$TMP_DIR/old-submission.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1_TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION1")
expect_status 200 "$STATUS" "old submission remains readable" "$TMP_DIR/old-submission.json"

echo "== New route buyer after removal gets no old-epoch benefit =="
register "s12b_new_$STAMP" "$TMP_DIR/reg-new.json"
login "s12b_new_$STAMP" "Passw0rd!" STUDENT "$TMP_DIR/new.json"
STUDENT3_TOKEN=$TOKEN
STUDENT3_ID=$USER_ID
fulfill_order "$STUDENT3_TOKEN" COURSE "$COURSE_C" new-course "s12b-new-course-$STAMP"
fulfill_order "$STUDENT3_TOKEN" ROUTE "$ROUTE_ID" new-route "s12b-new-route-$STAMP"
STATUS=$(benefit_list "$STUDENT3_TOKEN" "$ROUTE_ID" "$TMP_DIR/new-list.json")
expect_status 200 "$STATUS" "new buyer lists benefits" "$TMP_DIR/new-list.json"
expect_value "0" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.length)' "$TMP_DIR/new-list.json")" "new buyer sees no old-epoch benefit"
STATUS=$(curl -s -o "$TMP_DIR/new-detail-old.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT3_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$BENEFIT_ID")
expect_status 403 "$STATUS" "new buyer cannot read old-epoch benefit" "$TMP_DIR/new-detail-old.json"

echo "== Route re-add creates new epoch; new benefit is visible to new buyer =="
update_route "$ROUTE_ID" "$ROUTE_TITLE" 300 "$(json_members "$COURSE_C" "$COURSE_B" "$COURSE_D")" "$ROUTE_REV" "$TMP_DIR/route-readd.json"
publish_route "$ROUTE_ID" "$ROUTE_REV" "$TMP_DIR/route-readd-publish.json"
EPOCH_NEW=$(db "SELECT epoch_id FROM bc_course.route_member_current WHERE route_id=$ROUTE_ID AND course_id=$COURSE_C")
wait_grant "$STUDENT3_ID" "$ROUTE_ID" "$COURSE_C" "$EPOCH_NEW"
benefit_create_publish "$TMP_DIR/one-public.json" "$TMP_DIR/one-answer.json" "$TMP_DIR/benefit-new.json"
NEW_BENEFIT=$CREATED_BENEFIT
NEW_REV=$BENEFIT_REV
STATUS=$(curl -s -o "$TMP_DIR/new-detail-new.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT3_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$NEW_BENEFIT")
expect_status 200 "$STATUS" "new buyer reads new-epoch benefit" "$TMP_DIR/new-detail-new.json"
STATUS=$(curl -s -o "$TMP_DIR/new-detail-old2.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT3_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$BENEFIT_ID")
expect_status 403 "$STATUS" "new buyer still cannot read old-epoch benefit" "$TMP_DIR/new-detail-old2.json"

echo "== Refund freeze removes benefit access; restore returns it =="
SOURCE_ID=$(db "SELECT re.source_id FROM bc_learning.route_entitlement re WHERE re.user_id=$STUDENT1_ID AND re.route_id=$ROUTE_ID LIMIT 1")
db "UPDATE bc_learning.entitlement_source SET state='FROZEN' WHERE id=$SOURCE_ID"
STATUS=$(curl -s -o "$TMP_DIR/frozen-detail.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$BENEFIT_ID")
expect_status 403 "$STATUS" "frozen source blocks benefit read" "$TMP_DIR/frozen-detail.json"
STATUS=$(benefit_list "$STUDENT1_TOKEN" "$ROUTE_ID" "$TMP_DIR/frozen-list.json")
expect_status 200 "$STATUS" "frozen source list" "$TMP_DIR/frozen-list.json"
expect_value "0" "$(node -e 'const d=JSON.parse(require("fs").readFileSync(process.argv[1]));console.log(d.data.length)' "$TMP_DIR/frozen-list.json")" "frozen source hides benefit"
db "UPDATE bc_learning.entitlement_source SET state='ACTIVE' WHERE id=$SOURCE_ID"
STATUS=$(curl -s -o "$TMP_DIR/restored-detail.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$BENEFIT_ID")
expect_status 200 "$STATUS" "restored source returns benefit" "$TMP_DIR/restored-detail.json"

echo "== Freeze after attempt start blocks submit (write-time recheck) =="
STATUS=$(curl -s -o "$TMP_DIR/attempt2.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $STUDENT1_TOKEN" "$BASE/api/v1/benefit-assignments/$BENEFIT_ID/attempts")
expect_status 200 "$STATUS" "start another attempt" "$TMP_DIR/attempt2.json"
ATTEMPT2=$(field data.id < "$TMP_DIR/attempt2.json")
db "UPDATE bc_learning.entitlement_source SET state='FROZEN' WHERE id=$SOURCE_ID"
STATUS=$(curl -s -o "$TMP_DIR/frozen-submit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: s12b-frozen-submit" -H "Authorization: Bearer $STUDENT1_TOKEN" \
  -d '{"answers":{"q1":["A","B"]}}' "$BASE/api/v1/attempts/$ATTEMPT2/submissions")
expect_status 403 "$STATUS" "frozen source blocks submit" "$TMP_DIR/frozen-submit.json"
db "UPDATE bc_learning.entitlement_source SET state='ACTIVE' WHERE id=$SOURCE_ID"
STATUS=$(curl -s -o "$TMP_DIR/restored-submit.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Idempotency-Key: s12b-restored-submit" -H "Authorization: Bearer $STUDENT1_TOKEN" \
  -d '{"answers":{"q1":["A","B"]}}' "$BASE/api/v1/attempts/$ATTEMPT2/submissions")
expect_status 200 "$STATUS" "restored source allows submit" "$TMP_DIR/restored-submit.json"

echo "== Offline blocks new attempts but keeps historical access =="
STATUS=$(curl -s -o "$TMP_DIR/offline.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEACHER_TOKEN" -d "{\"revision\":$B1_REV}" \
  "$BASE/api/v1/teacher/benefit-assignments/$BENEFIT_ID/offline")
expect_status 200 "$STATUS" "offline benefit" "$TMP_DIR/offline.json"
B1_REV=$(field data.revision < "$TMP_DIR/offline.json")
STATUS=$(curl -s -o "$TMP_DIR/offline-detail.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$BENEFIT_ID")
expect_status 200 "$STATUS" "offline benefit detail remains" "$TMP_DIR/offline-detail.json"
expect_value "OFFLINE" "$(field data.status < "$TMP_DIR/offline-detail.json")" "offline status"
expect_value "" "$(field data.publicBody < "$TMP_DIR/offline-detail.json")" "offline public body hidden"
STATUS=$(curl -s -o "$TMP_DIR/offline-attempt.json" -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $STUDENT1_TOKEN" "$BASE/api/v1/benefit-assignments/$BENEFIT_ID/attempts")
expect_status 409 "$STATUS" "offline rejects new attempt" "$TMP_DIR/offline-attempt.json"
STATUS=$(curl -s -o "$TMP_DIR/offline-submission.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1_TOKEN" \
  "$BASE/api/v1/submissions/$SUBMISSION1")
expect_status 200 "$STATUS" "offline keeps historical submission" "$TMP_DIR/offline-submission.json"

echo "== Teacher removal blocks reads and mutations =="
STATUS=$(curl -s -o "$TMP_DIR/t-remove.json" -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"courseIds\":[],\"routeIds\":[],\"revision\":$(field data.revision < "$TMP_DIR/t-save.json")}" \
  "$BASE/api/v1/admin/teachers/$TEACHER_ID/assignments")
expect_status 200 "$STATUS" "remove teacher scope" "$TMP_DIR/t-remove.json"
STATUS=$(curl -s -o "$TMP_DIR/t-get-after.json" -w '%{http_code}' -H "Authorization: Bearer $TEACHER_TOKEN" \
  "$BASE/api/v1/teacher/benefit-assignments/$BENEFIT_ID")
expect_status 403 "$STATUS" "removed teacher cannot read" "$TMP_DIR/t-get-after.json"
STATUS=$(curl -s -o "$TMP_DIR/t-offline-after.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEACHER_TOKEN" -d "{\"revision\":$B1_REV}" \
  "$BASE/api/v1/teacher/benefit-assignments/$BENEFIT_ID/offline")
expect_status 403 "$STATUS" "removed teacher cannot mutate" "$TMP_DIR/t-offline-after.json"
STATUS=$(curl -s -o "$TMP_DIR/old-still.json" -w '%{http_code}' -H "Authorization: Bearer $STUDENT1_TOKEN" \
  "$BASE/api/v1/benefit-assignments/$BENEFIT_ID")
expect_status 200 "$STATUS" "teacher removal does not affect student access" "$TMP_DIR/old-still.json"

echo "ALL S12-B ACCEPTANCE CHECKS PASSED"