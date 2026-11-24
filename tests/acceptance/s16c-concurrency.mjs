import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'

const BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const MARKETING_BASE = process.env.MARKETING_BASE || 'http://127.0.0.1:18087'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'
const MYSQL = process.env.MYSQL_BIN || 'mysql'
const DB_PASSWORD = process.env.DB_PASSWORD || '1234'
const STAMP = Date.now()

function db(sql) {
  return execFileSync(MYSQL, [
    '--default-character-set=utf8mb4', '-h127.0.0.1', '-uroot', '-N', '-B', '-e', sql
  ], { env: { ...process.env, MYSQL_PWD: DB_PASSWORD }, encoding: 'utf8' }).trim()
}

async function request(base, path, { method = 'GET', token, body, headers = {} } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const json = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(`${method} ${path} -> ${response.status} ${json.message || ''}`)
    error.status = response.status
    throw error
  }
  return json.data
}

async function login(username, app) {
  return request(BASE, '/api/v1/auth/login', {
    method: 'POST', body: { username, password: PASSWORD, clientApp: app }
  })
}

async function registerStudent(username) {
  await request(BASE, '/api/v1/auth/register', {
    method: 'POST', body: { username, password: PASSWORD, nickname: username }
  })
  return login(username, 'STUDENT')
}

function percentile(values, fraction) {
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * fraction))]
}

function stats(label, values) {
  const p50 = percentile(values, 0.5)
  const p95 = percentile(values, 0.95)
  const max = Math.max(...values)
  console.log(`RESULT  ${label}: n=${values.length} p50=${p50.toFixed(1)}ms p95=${p95.toFixed(1)}ms max=${max.toFixed(1)}ms`)
  return { p50, p95, max }
}

function assert(condition, label) {
  if (!condition) throw new Error(label)
  console.log(`PASS  ${label}`)
}

async function ensureCodeAssignment(adminToken, courseId) {
  const page = await request(BASE, `/api/v1/admin/assignments?courseId=${courseId}&page=1&pageSize=100`,
    { token: adminToken })
  let assignment = page.items.find(item => item.kind === 'COURSE_CODE')
  if (!assignment) {
    assignment = await request(BASE, '/api/v1/admin/assignments', {
      method: 'POST', token: adminToken,
      body: {
        kind: 'COURSE_CODE', courseId, chapterId: null,
        publicBody: {
          scenario: 'S16-c 100 concurrent task submissions',
          requirements: '补全 slot。',
          segments: [
            { text: 'public class Load {\n    public int value() {\n' },
            { slotId: 's1', initialCode: '        // TODO\n' },
            { text: '    }\n}\n' }
          ]
        },
        answerBody: { referenceBySlot: { s1: '        return 1;\n' }, rubric: '返回固定值。' }
      }
    })
  }
  const detail = await request(BASE, `/api/v1/admin/assignments/${assignment.id}`, { token: adminToken })
  if (detail.status !== 'ONLINE') {
    await request(BASE, `/api/v1/admin/assignments/${assignment.id}/publish`, {
      method: 'POST', token: adminToken, body: { revision: detail.revision }
    })
  }
  return assignment.id
}

async function main() {
  const admin = await login('admin01', 'ADMIN')
  const student1 = await login('student01', 'STUDENT')
  const courses = await request(BASE, '/api/v1/admin/courses?page=1&pageSize=100', { token: admin.accessToken })
  const courseA = courses.items.find(item => item.title === '演示课程：Java 基础 A')
  if (!courseA) throw new Error('demo course missing; run scripts/seed-demo.mjs')

  console.log('== 100 concurrent identical points events ==')
  const pointsUser = await registerStudent(`s16c_points_${STAMP}`)
  const eventId = randomUUID()
  const lessonId = 9_000_000 + (STAMP % 100_000)
  const pointsRequests = Array.from({ length: 100 }, () => {
    const started = performance.now()
    return fetch(`${MARKETING_BASE}/dev/points/rewards`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        eventId, userId: Number(pointsUser.user.id), lessonId, points: 10,
        occurredAt: new Date().toISOString()
      })
    }).then(async response => ({
      status: response.status,
      body: await response.json(),
      ms: performance.now() - started
    }))
  })
  const pointResults = await Promise.all(pointsRequests)
  const pointStats = stats('100 points requests', pointResults.map(item => item.ms))
  assert(pointResults.every(item => item.status === 200),
    'all 100 identical points requests return 200')
  assert(new Set(pointResults.map(item => item.body.data.ledgerId)).size === 1,
    '100 identical points requests return one ledger id')
  assert(db(`SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='VIDEO' AND business_key='${pointsUser.user.id}:${lessonId}'`) === '1',
    'only one VIDEO ledger row exists')
  assert(db(`SELECT available FROM bc_marketing.points_account WHERE user_id=${pointsUser.user.id}`) === '10',
    'points account credited exactly once')
  assert(pointStats.p95 < 10000, 'points p95 under local 10s guard')

  console.log('== 100 concurrent exchanges against stock 10 ==')
  const rule = await request(BASE, '/api/v1/admin/coupon-rules', {
    method: 'POST', token: admin.accessToken,
    body: {
      ruleKey: `S16C_LOAD_${STAMP}`, type: 'NO_THRESHOLD', scope: 'ALL',
      amountCent: 100, validityType: 'DAYS', validDays: 7
    }
  })
  const project = await request(BASE, '/api/v1/admin/exchange-projects', {
    method: 'POST', token: admin.accessToken,
    body: {
      name: `S16C 加载项目 ${STAMP}`, ruleId: rule.id, pointsPrice: 10,
      stockTotal: 10, perUserLimit: 100,
      openAt: new Date(Date.now() - 3600000).toISOString().slice(0, 19),
      closeAt: new Date(Date.now() + 86400000).toISOString().slice(0, 19)
    }
  })
  const online = await request(BASE, `/api/v1/admin/exchange-projects/${project.id}/online`, {
    method: 'POST', token: admin.accessToken, body: { revision: 0 }
  })
  db(`INSERT INTO bc_marketing.points_account(user_id,available,debt,revision,created_at,updated_at) VALUES(${pointsUser.user.id},1000,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE available=1000,debt=0`)
  const exchangeRequests = Array.from({ length: 100 }, (_, index) => {
    const started = performance.now()
    return fetch(`${BASE}/api/v1/exchange-projects/${project.id}/exchanges`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${pointsUser.accessToken}`,
        'Idempotency-Key': `s16c-load-${STAMP}-${index}`
      },
      body: JSON.stringify({ expectedRevision: online.revision })
    }).then(async response => ({
      status: response.status,
      body: await response.json(),
      ms: performance.now() - started
    }))
  })
  const exchangeResults = await Promise.all(exchangeRequests)
  const exchangeStats = stats('100 exchange requests', exchangeResults.map(item => item.ms))
  const successes = exchangeResults.filter(item => item.status === 200)
  const stockErrors = exchangeResults.filter(item => item.status === 409 && item.body.code === 'STOCK_EXHAUSTED')
  assert(successes.length === 10, `exactly 10 exchanges succeed (got ${successes.length})`)
  assert(stockErrors.length === 90, `90 exchanges fail with STOCK_EXHAUSTED (got ${stockErrors.length})`)
  assert(db(`SELECT stock_used FROM bc_marketing.exchange_project WHERE id=${project.id}`) === '10',
    'stock_used is exactly 10')
  assert(db(`SELECT COUNT(*) FROM bc_marketing.exchange_record WHERE project_id=${project.id}`) === '10',
    'exactly 10 exchange records exist')
  assert(db(`SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCHANGE' AND business_key LIKE '${pointsUser.user.id}:s16c-load-%'`) === '10',
    'exactly 10 points deductions exist')
  assert(db(`SELECT available FROM bc_marketing.points_account WHERE user_id=${pointsUser.user.id}`) === '900',
    'balance reflects exactly 10 deductions')
  assert(exchangeStats.p95 < 30000, 'exchange p95 under local 30s guard')

  console.log('== 100 concurrent submissions for one code attempt ==')
  const codeAssignment = await ensureCodeAssignment(admin.accessToken, courseA.id)
  for (let i = 0; i < 30; i += 1) {
    const active = db(`SELECT COALESCE(active_task_id,0) FROM bc_assignment.ai_user_slot WHERE user_id=${student1.user.id}`)
    if (active === '0' || active === '') break
    if (i === 29) throw new Error('student AI slot still busy before task load')
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  const attempt = await request(BASE, `/api/v1/assignments/${codeAssignment}/attempts`, {
    method: 'POST', token: student1.accessToken
  })
  const submissions = await Promise.all(Array.from({ length: 100 }, (_, index) => {
    const started = performance.now()
    return fetch(`${BASE}/api/v1/attempts/${attempt.id}/submissions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${student1.accessToken}`,
        'Idempotency-Key': `s16c-task-${STAMP}-${index}`
      },
      body: JSON.stringify({ answers: { s1: 'return 1;' } })
    }).then(async response => ({
      status: response.status,
      body: await response.json(),
      ms: performance.now() - started
    }))
  }))
  const taskStats = stats('100 task submission requests', submissions.map(item => item.ms))
  const taskSuccesses = submissions.filter(item => item.status === 200)
  const taskConflicts = submissions.filter(item => item.status === 409)
  assert(taskSuccesses.length === 1, `exactly one task submission succeeds (got ${taskSuccesses.length})`)
  assert(taskConflicts.length === 99, `99 concurrent submissions conflict (got ${taskConflicts.length})`)
  const submissionIds = new Set(taskSuccesses.map(item => String(item.body.data.id)))
  assert(submissionIds.size === 1, 'one successful submission id')
  assert(db(`SELECT COUNT(*) FROM bc_assignment.submission WHERE attempt_id=${attempt.id}`) === '1',
    'only one submission row exists for the attempt')
  assert(db(`SELECT COUNT(*) FROM bc_assignment.ai_task WHERE submission_id=${[...submissionIds][0]}`) === '1',
    'only one AI task exists for the submission')
  assert(taskStats.p95 < 30000, 'task p95 under local 30s guard')

  console.log(`ALL S16-C CONCURRENCY CHECKS PASSED (points p95=${pointStats.p95.toFixed(1)}ms, exchange p95=${exchangeStats.p95.toFixed(1)}ms, task p95=${taskStats.p95.toFixed(1)}ms)`)
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})