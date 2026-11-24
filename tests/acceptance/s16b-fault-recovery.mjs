import { setTimeout as sleep } from 'node:timers/promises'
import { execFileSync, spawn, spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'

const BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const MARKETING_BASE = process.env.MARKETING_BASE || 'http://127.0.0.1:18087'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'
const MYSQL = process.env.MYSQL_BIN || 'mysql'
const DB_PASSWORD = process.env.DB_PASSWORD || '1234'
const ROOT = process.env.PROJECT_ROOT || process.cwd()
const STAMP = Date.now()

function loadLocalEnv() {
  const file = path.join(ROOT, 'config', 'local.env.ps1')
  if (!fs.existsSync(file)) return {}
  const values = {}
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\$env:([A-Za-z0-9_]+)\s*=\s*['"](.*)['"]\s*$/)
    if (match) values[match[1]] = match[2]
  }
  return values
}
const LOCAL_ENV = loadLocalEnv()

let passed = 0
const stopped = new Set()

function pass(label) {
  passed += 1
  console.log(`PASS  ${label}`)
}
function assert(condition, label) {
  if (!condition) throw new Error(label)
  pass(label)
}

function db(sql) {
  return execFileSync(MYSQL, [
    '--default-character-set=utf8mb4', '-h127.0.0.1', '-uroot', '-N', '-B', '-e', sql
  ], { env: { ...process.env, MYSQL_PWD: DB_PASSWORD }, encoding: 'utf8' }).trim()
}

async function waitDb(sql, expected, label, timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs
  let actual = ''
  while (Date.now() < deadline) {
    actual = db(sql)
    if (actual === expected) return
    await sleep(500)
  }
  throw new Error(`${label}: expected ${expected}, got ${actual}`)
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

async function findPaged(path, predicate, token) {
  for (let page = 1; page <= 10; page += 1) {
    const result = await request(BASE, `${path}?page=${page}&pageSize=100`, { token })
    const found = result.items.find(predicate)
    if (found) return found
    if (result.items.length < 100) return null
  }
  return null
}

function runPowerShellFile(file, args = []) {
  const result = spawnSync('powershell', ['-ExecutionPolicy', 'Bypass', '-File', file, ...args], {
    cwd: ROOT,
    env: {
      ...process.env,
      ...LOCAL_ENV,
      OUTBOX_TRANSPORT: 'rabbit',
      OUTBOX_RELAY_ENABLED: 'true',
      AI_WAKEUP_ENABLED: 'true',
      AI_MOCK_POLL_MS: '500',
      POINTS_TEST_API_ENABLED: 'true'
    },
    encoding: 'utf8',
    timeout: 180000
  })
  if (result.status !== 0) {
    throw new Error(`${file} failed: ${result.stdout}\n${result.stderr}`)
  }
  return result.stdout
}

function readPids(file) {
  if (!fs.existsSync(file)) return []
  return JSON.parse(fs.readFileSync(file, 'utf8').replace(/^﻿/, ''))
}

function stopService(name) {
  const pidsFile = path.join(ROOT, '.local-run', 'pids.json')
  const records = readPids(pidsFile)
  const target = records.find(item => item.kind === name)
  if (!target) throw new Error(`${name} is not recorded in pids.json`)
  execFileSync('taskkill.exe', ['/PID', String(target.pid), '/T', '/F'], { stdio: 'ignore' })
  fs.writeFileSync(pidsFile, JSON.stringify(records.filter(item => item.kind !== name), null, 2))
  stopped.add(name)
}

const SERVICES = {
  marketing: { jar: 'bootcamp-marketing/target/bootcamp-marketing-0.1.0-SNAPSHOT.jar', port: 18087 },
  learning: { jar: 'bootcamp-learning/target/bootcamp-learning-0.1.0-SNAPSHOT.jar', port: 18083 },
  assignment: { jar: 'bootcamp-assignment/target/bootcamp-assignment-0.1.0-SNAPSHOT.jar', port: 18085 },
  interaction: { jar: 'bootcamp-interaction/target/bootcamp-interaction-0.1.0-SNAPSHOT.jar', port: 18086 }
}

async function waitHealth(port, timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/actuator/health`)
      if (response.ok) return
    } catch {}
    await sleep(500)
  }
  throw new Error(`service on ${port} did not become healthy`)
}

async function startService(name, extraEnv = {}) {
  const service = SERVICES[name]
  if (!service) throw new Error(`unknown service ${name}`)
  const pidsFile = path.join(ROOT, '.local-run', 'pids.json')
  const records = readPids(pidsFile)
  const logDir = path.join(ROOT, '.local-run', 'logs')
  fs.mkdirSync(logDir, { recursive: true })
  const out = fs.openSync(path.join(logDir, `${name}.out.log`), 'a')
  const err = fs.openSync(path.join(logDir, `${name}.err.log`), 'a')
  const java = execFileSync('where.exe', ['java'], { encoding: 'utf8' }).split(/\r?\n/)[0].trim()
  const child = spawn(java, ['-jar', path.join(ROOT, 'backend', service.jar)], {
    cwd: ROOT,
    env: {
      ...process.env,
      ...LOCAL_ENV,
      OUTBOX_TRANSPORT: 'rabbit',
      OUTBOX_RELAY_ENABLED: 'true',
      AI_WAKEUP_ENABLED: 'true',
      AI_MOCK_POLL_MS: '500',
      POINTS_TEST_API_ENABLED: 'true',
      ...extraEnv
    },
    detached: true,
    stdio: ['ignore', out, err]
  })
  child.unref()
  const nextRecords = records.filter(item => item.kind !== name)
  nextRecords.push({
    kind: name,
    pid: child.pid,
    port: service.port,
    url: `http://127.0.0.1:${service.port}/actuator/health`
  })
  fs.writeFileSync(pidsFile, JSON.stringify(nextRecords, null, 2))
  await waitHealth(service.port)
  stopped.delete(name)
}

async function createPublicNote(token, courseId, body) {
  return request(BASE, `/api/v1/courses/${courseId}/notes`, {
    method: 'POST', token, body: { body, visibility: 'COURSE_PUBLIC' }
  })
}

async function evaluateNote(token, noteId) {
  return request(BASE, `/api/v1/teacher/notes/${noteId}/excellent`, {
    method: 'PUT', token, body: { contentRevision: 0 }
  })
}

async function buyCourseWhileOrderUp(token, courseId, key) {
  const quote = await request(BASE, '/api/v1/trade/quotes', {
    method: 'POST', token,
    body: { productType: 'COURSE', productId: courseId, couponSelection: { mode: 'NONE', ids: [] } }
  })
  return request(BASE, '/api/v1/orders', {
    method: 'POST', token, headers: { 'Idempotency-Key': key },
    body: { quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: [] }
  })
}

async function waitOrderState(token, orderId, predicate, label, timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs
  let state = null
  while (Date.now() < deadline) {
    state = await request(BASE, `/api/v1/orders/${orderId}`, { token })
    if (predicate(state)) return state
    await sleep(500)
  }
  throw new Error(`${label}: ${JSON.stringify(state)}`)
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
          scenario: 'S16-b worker recovery',
          requirements: '补全 slot，返回传入值。',
          segments: [
            { text: 'public class Demo {\n    public int value() {\n' },
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

async function ensureService(name) {
  const service = SERVICES[name]
  try {
    const response = await fetch(`http://127.0.0.1:${service.port}/actuator/health`)
    if (response.ok) return
  } catch {}
  await startService(name)
}

async function main() {
  await ensureService('marketing')
  await ensureService('learning')
  await ensureService('assignment')
  const admin = await login('admin01', 'ADMIN')
  const teacher = await login('teacher01', 'ADMIN')
  const student1 = await login('student01', 'STUDENT')
  const student2 = await login('student02', 'STUDENT')
  const courses = await request(BASE, '/api/v1/admin/courses?page=1&pageSize=100', { token: admin.accessToken })
  const courseA = courses.items.find(item => item.title === '演示课程：Java 基础 A')
  const courseB = courses.items.find(item => item.title === '演示课程：Java 进阶 B')
  if (!courseA || !courseB) throw new Error('demo courses missing; run scripts/seed-demo.mjs')

  try {
    console.log('== Rabbit outage: interaction outbox replay ==')
    const note1 = await createPublicNote(student1.accessToken, courseA.id, `S16B rabbit note ${STAMP}`)
    runPowerShellFile('scripts/stop-rabbit.ps1')
    stopped.add('rabbit')
    await evaluateNote(teacher.accessToken, note1.id)
    await sleep(1500)
    const outboxState = db(`SELECT CONCAT_WS('|',status,attempt) FROM bc_interaction.outbox_event WHERE aggregate_id='${note1.id}' AND type='interaction.note-excellent.v1'`)
    assert(/^(NEW|NEEDS_ATTENTION)\|/.test(outboxState), 'Rabbit-down event remains pending in interaction outbox')
    assert(db(`SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='${note1.id}'`) === '0',
      'Rabbit-down event not consumed before recovery')
    runPowerShellFile('scripts/start-rabbit.ps1')
    stopped.delete('rabbit')
    await waitDb(`SELECT status FROM bc_interaction.outbox_event WHERE aggregate_id='${note1.id}' AND type='interaction.note-excellent.v1'`, 'SENT', 'interaction outbox replay after Rabbit restart')
    await waitDb(`SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='${note1.id}'`, '1', 'points awarded after Rabbit restart')
    pass('Rabbit restart replays pending excellent event')

    console.log('== Marketing outage: queue holds, recovery consumes ==')
    const note2 = await createPublicNote(student2.accessToken, courseA.id, `S16B marketing note ${STAMP}`)
    stopService('marketing')
    await evaluateNote(teacher.accessToken, note2.id)
    await waitDb(`SELECT status FROM bc_interaction.outbox_event WHERE aggregate_id='${note2.id}' AND type='interaction.note-excellent.v1'`, 'SENT', 'event sent while marketing down', 30000)
    assert(db(`SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='${note2.id}'`) === '0',
      'marketing-down event not credited yet')
    startService('marketing')
    await waitDb(`SELECT COUNT(*) FROM bc_marketing.points_ledger WHERE type='EXCELLENT' AND business_key='${note2.id}'`, '1', 'marketing recovery consumes queued event')
    await waitDb(`SELECT COUNT(*) FROM bc_marketing.inbox_event WHERE consumer='interaction-note-excellent-v1' AND business_key='EXCELLENT:${note2.id}' AND state='PROCESSED'`, '1', 'marketing inbox processed after recovery')
    pass('marketing restart consumes queued event exactly once')

    console.log('== Learning outage: payment succeeds, fulfillment recovers ==')
    const username = `s16b_buyer_${STAMP}`
    await request(BASE, '/api/v1/auth/register', {
      method: 'POST', body: { username, password: PASSWORD, nickname: 'S16BBuyer' }
    })
    const buyer = await request(BASE, '/api/v1/auth/login', {
      method: 'POST', body: { username, password: PASSWORD, clientApp: 'STUDENT' }
    })
    const order = await buyCourseWhileOrderUp(buyer.accessToken, courseB.id, `s16b-buy-${STAMP}`)
    stopService('learning')
    await request(BASE, `/api/v1/orders/${order.id}/payment`, {
      method: 'POST', token: buyer.accessToken, body: { channel: 'MOCK' }
    })
    await request(BASE, `/api/v1/orders/${order.id}/payment/mock-pay`, {
      method: 'POST', token: buyer.accessToken
    })
    const pending = await waitOrderState(buyer.accessToken, order.id,
      state => state.state === 'PAID' && state.fulfillmentState !== 'DONE',
      'paid order remains pending while learning is down', 15000)
    assert(pending.state === 'PAID' && pending.fulfillmentState !== 'DONE',
      'order paid but fulfillment pending during learning outage')
    startService('learning')
    await waitOrderState(buyer.accessToken, order.id, state => state.fulfillmentState === 'DONE',
      'order fulfillment after learning restart')
    const mine = await request(BASE, '/api/v1/me/courses?page=1&pageSize=100', { token: buyer.accessToken })
    assert(mine.items.some(item => String(item.courseId) === String(courseB.id)),
      'learning restart grants course access')
    pass('learning restart completes pending payment fulfillment')

    console.log('== Worker outage: lease recovery after assignment restart ==')
    stopService('assignment')
    await startService('assignment', {
      AI_MOCK_DELAY_MS: '20000',
      AI_LEASE_SECONDS: '5',
      AI_LEASE_RENEW_MS: '1000'
    })
    const codeAssignment = await ensureCodeAssignment(admin.accessToken, courseA.id)
    const studentAssignments = await request(BASE, `/api/v1/courses/${courseA.id}/assignments`, {
      token: student1.accessToken
    })
    const codeItem = studentAssignments.find(item => String(item.id) === String(codeAssignment))
    if (!codeItem) throw new Error('code assignment not visible to student')
    const attempt = await request(BASE, `/api/v1/assignments/${codeAssignment}/attempts`, {
      method: 'POST', token: student1.accessToken
    })
    const submission = await request(BASE, `/api/v1/attempts/${attempt.id}/submissions`, {
      method: 'POST', token: student1.accessToken,
      headers: { 'Idempotency-Key': `s16b-worker-${STAMP}` },
      body: { answers: { s1: 'return 1;' } }
    })
    await waitDb(`SELECT CASE WHEN state IN ('QUEUED','RUNNING') THEN state ELSE state END FROM bc_assignment.ai_task WHERE submission_id=${submission.id}`, 'RUNNING', 'worker task running with delay', 15000)
    stopService('assignment')
    await sleep(7000)
    await startService('assignment', {
      AI_MOCK_DELAY_MS: '0',
      AI_LEASE_SECONDS: '5',
      AI_LEASE_RENEW_MS: '1000'
    })
    await waitDb(`SELECT review_state FROM bc_assignment.submission WHERE id=${submission.id}`, 'GRADED', 'worker lease recovery completes submission', 60000)
    pass('worker restart recovers expired lease and completes review')

    console.log('== Redis outage and board rebuild ==')
    const boardMonth = '2031-01'
    await request(MARKETING_BASE, '/dev/points/fixtures/month-score', {
      method: 'POST', body: { userId: Number(student2.user.id), month: boardMonth, score: 77 }
    })
    await request(MARKETING_BASE, `/dev/boards/${boardMonth}/rebuild`, { method: 'POST' })
    const boardBefore = await request(BASE, `/api/v1/boards/monthly?month=${boardMonth}&page=1&pageSize=100`,
      { token: student2.accessToken })
    assert(boardBefore.items.some(item => item.score === 77), 'board shows historical month score with Redis')

    runPowerShellFile('scripts/stop-redis.ps1')
    stopped.add('redis')
    const boardDown = await request(BASE, `/api/v1/boards/monthly?month=${boardMonth}&page=1&pageSize=100`,
      { token: student2.accessToken })
    assert(boardDown.items.some(item => item.score === 77), 'board falls back to MySQL while Redis is down')
    runPowerShellFile('scripts/start-redis.ps1')
    stopped.delete('redis')
    await request(MARKETING_BASE, `/dev/boards/${boardMonth}/rebuild`, { method: 'POST' })
    const boardAfter = await request(BASE, `/api/v1/boards/monthly?month=${boardMonth}&page=1&pageSize=100`,
      { token: student2.accessToken })
    assert(boardAfter.items.some(item => item.score === 77), 'board rebuild after Redis recovery')
    pass('historical board survives Redis outage and rebuild')

    console.log(`ALL S16-B CORE FAULT RECOVERY CHECKS PASSED (${passed})`)
  } finally {
    for (const name of [...stopped]) {
      if (name === 'rabbit') {
        try { runPowerShellFile('scripts/start-rabbit.ps1') } catch {}
      } else if (name === 'redis') {
        try { runPowerShellFile('scripts/start-redis.ps1') } catch {}
      } else {
        try { startService(name) } catch {}
      }
    }
  }
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})