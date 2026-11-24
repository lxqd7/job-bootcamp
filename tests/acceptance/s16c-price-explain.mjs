import { execFileSync } from 'node:child_process'

const BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'
const MYSQL = process.env.MYSQL_BIN || 'mysql'
const DB_PASSWORD = process.env.DB_PASSWORD || '1234'
const STAMP = Date.now()

function db(sql) {
  return execFileSync(MYSQL, [
    '--default-character-set=utf8mb4', '-h127.0.0.1', '-uroot', '-N', '-B', '-e', sql
  ], { env: { ...process.env, MYSQL_PWD: DB_PASSWORD }, encoding: 'utf8' }).trim()
}

async function request(path, { method = 'GET', token, body, headers = {}, raw = false } = {}) {
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const json = await response.json()
  if (!response.ok) throw new Error(`${method} ${path} -> ${response.status} ${json.message || ''}`)
  return raw ? { json, response } : json.data
}

async function login(username, app) {
  return request('/api/v1/auth/login', {
    method: 'POST', body: { username, password: PASSWORD, clientApp: app }
  })
}

function percentile(values, fraction) {
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * fraction))]
}

function assert(condition, label) {
  if (!condition) throw new Error(label)
  console.log(`PASS  ${label}`)
}

async function waitQuote(token, quote) {
  let current = quote
  for (let i = 0; i < 200 && current.status === 'CALCULATING'; i += 1) {
    await new Promise(resolve => setTimeout(resolve, 50))
    current = await request(`/api/v1/trade/quotes/${quote.quoteId}`, { token })
  }
  if (current.status !== 'READY') throw new Error(`quote not ready: ${current.status}`)
  return current
}

async function main() {
  const admin = await login('admin01', 'ADMIN')
  const courses = await request('/api/v1/admin/courses?page=1&pageSize=100', { token: admin.accessToken })
  const courseA = courses.items.find(item => item.title === '演示课程：Java 基础 A')
  if (!courseA) throw new Error('demo course missing; run scripts/seed-demo.mjs')

  const username = `s16c_price_${STAMP}`
  await request('/api/v1/auth/register', {
    method: 'POST', body: { username, password: PASSWORD, nickname: 'S16CPrice' }
  })
  const student = await login(username, 'STUDENT')
  const userId = Number(student.user.id)

  console.log('== 100 coupon candidates ==')
  const values = []
  for (let i = 0; i < 100; i += 1) {
    values.push(`('S16C_PERF_${STAMP}_${i}',1,'NO_THRESHOLD','ALL',NULL,0,10,NULL,NULL,'DAYS',NULL,NULL,30,TRUE,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0)`)
  }
  db(`INSERT INTO bc_marketing.coupon_rule(rule_key,version_no,type,scope,target_id,threshold_cent,amount_cent,pay_rate_bps,max_discount_cent,validity_type,valid_from,valid_until,valid_days,issued,created_at,updated_at,revision) VALUES ${values.join(',')}`)
  const ruleRows = db(`SELECT id FROM bc_marketing.coupon_rule WHERE rule_key LIKE 'S16C_PERF_${STAMP}_%' ORDER BY id`).split(/\r?\n/)
  assert(ruleRows.length === 100, '100 performance coupon rules created')
  const couponValues = ruleRows.map((ruleId, index) =>
    `(${userId},${ruleId},'ACTIVITY',${index + 1},${index + 1},UTC_TIMESTAMP(3) - INTERVAL 1 HOUR,UTC_TIMESTAMP(3) + INTERVAL 30 DAY,'AVAILABLE',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),0)`)
  db(`INSERT INTO bc_marketing.user_coupon(user_id,rule_id,source_type,source_group_id,source_item_id,valid_from,valid_until,status,created_at,updated_at,revision) VALUES ${couponValues.join(',')}`)
  assert(db(`SELECT COUNT(*) FROM bc_marketing.user_coupon WHERE user_id=${userId}`) === '100',
    '100 user coupons available for pricing')

  const durations = []
  let first = null
  for (let i = 0; i < 10; i += 1) {
    const started = performance.now()
    let quote
    try {
      quote = await request('/api/v1/trade/quotes', {
        method: 'POST', token: student.accessToken,
        body: {
          productType: 'COURSE', productId: courseA.id,
          couponSelection: { mode: 'AUTO', ids: [] }
        }
      })
    } catch (error) {
      if (/NO_COURSE_ACCESS|无权|403/.test(error.message)) {
        throw new Error(`quote requires course access: ${error.message}`)
      }
      throw error
    }
    const ready = await waitQuote(student.accessToken, quote)
    durations.push(performance.now() - started)
    if (!first) first = ready
    assert(ready.discountCent === 30 && ready.couponPlan.length === 3,
      `AUTO uses best 3 of 100 candidates (run ${i + 1})`)
  }
  durations.sort((a, b) => a - b)
  const p50 = percentile(durations, 0.5)
  const p95 = percentile(durations, 0.95)
  console.log(`RESULT  100-candidate AUTO quote: n=${durations.length} cold=${durations[0].toFixed(1)}ms p50=${p50.toFixed(1)}ms p95=${p95.toFixed(1)}ms max=${durations[durations.length - 1].toFixed(1)}ms`)

  console.log('== API response contract ==')
  const loginRaw = await request('/api/v1/auth/login', {
    method: 'POST', raw: true,
    body: { username: 'student01', password: PASSWORD, clientApp: 'STUDENT' }
  })
  for (const key of ['code', 'message', 'data', 'traceId']) {
    assert(Object.prototype.hasOwnProperty.call(loginRaw.json, key), `success envelope has ${key}`)
  }
  assert(loginRaw.response.headers.get('content-type').includes('application/json'),
    'success response is JSON')
  const errorResponse = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'student01', password: 'wrong-password', clientApp: 'STUDENT' })
  })
  const errorBody = await errorResponse.json()
  assert(errorResponse.status === 401 && errorBody.code === 'UNAUTHORIZED' && Boolean(errorBody.traceId),
    'error envelope has stable code and traceId')
  for (const key of ['quoteId', 'status', 'quoteHash', 'totalCent', 'discountCent', 'payableCent']) {
    assert(Object.prototype.hasOwnProperty.call(first, key), `quote contract has ${key}`)
  }

  console.log('== List query EXPLAIN ==')
  const plans = [
    ['course list', `EXPLAIN SELECT id,title FROM bc_course.course WHERE status='ONLINE' AND category_id=${courseA.categoryId} ORDER BY id DESC LIMIT 20`],
    ['order list', `EXPLAIN SELECT id,state FROM bc_trade.trade_order WHERE user_id=${userId} ORDER BY created_at DESC,id DESC LIMIT 20`],
    ['monthly board', `EXPLAIN SELECT user_id,score FROM bc_marketing.points_month WHERE month='2031-01' ORDER BY score DESC,user_id ASC LIMIT 20`],
    ['note list', `EXPLAIN SELECT id,body FROM bc_interaction.note WHERE course_id=${courseA.id} AND visibility='COURSE_PUBLIC' AND state='VISIBLE' AND type='ORIGINAL' ORDER BY updated_at DESC,id DESC LIMIT 20`],
    ['question list', `EXPLAIN SELECT id,title FROM bc_interaction.question WHERE course_id=${courseA.id} AND state='VISIBLE' ORDER BY created_at DESC,id DESC LIMIT 20`],
    ['exchange list', `EXPLAIN SELECT id,name FROM bc_marketing.exchange_project WHERE state='ONLINE' ORDER BY id DESC LIMIT 20`]
  ]
  const expectedKeys = {
    'monthly board': 'idx_points_month_rank',
    'note list': 'idx_note_course_visibility',
    'question list': 'idx_question_course_state',
    'exchange list': 'idx_exchange_project_state'
  }
  for (const [label, sql] of plans) {
    const row = db(sql).split('\t')
    const type = row[4]
    const key = row[6] || 'NULL'
    const rows = row[9]
    console.log(`EXPLAIN  ${label}: type=${type} key=${key} rows=${rows}`)
    if (expectedKeys[label]) {
      assert(key === expectedKeys[label], `${label} uses ${expectedKeys[label]}`)
    } else {
      assert(type !== '', `${label} has an execution plan`)
    }
  }

  console.log(`ALL S16-C PRICE/EXPLAIN/CONTRACT CHECKS PASSED (100 candidates p95=${p95.toFixed(1)}ms)`)
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})