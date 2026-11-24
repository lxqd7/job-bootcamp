const BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'

let passed = 0
function pass(label) {
  passed += 1
  console.log(`PASS  ${label}`)
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
  const json = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(`${method} ${path} -> ${response.status}`)
    error.status = response.status
    throw error
  }
  return raw ? json : json.data
}

async function login(username, app) {
  return request('/api/v1/auth/login', {
    method: 'POST', body: { username, password: PASSWORD, clientApp: app }
  })
}

async function expectStatus(expected, method, path, token, options = {}, label = '') {
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers || {})
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  })
  if (!expected.includes(response.status)) {
    throw new Error(`${label || path}: expected ${expected.join('/')}, got ${response.status}`)
  }
  pass(`${label || `${method} ${path}`} -> ${response.status}`)
}

async function findPaged(path, predicate, token, pageSize = 100) {
  for (let page = 1; page <= 10; page += 1) {
    const result = await request(`${path}${path.includes('?') ? '&' : '?'}page=${page}&pageSize=${pageSize}`,
      { token })
    const found = result.items.find(predicate)
    if (found) return found
    if (result.items.length < pageSize) return null
  }
  return null
}

const FORBIDDEN_KEYS = new Set([
  'password', 'passwordHash', 'password_hash', 'tokenVersion', 'refreshToken',
  'privateKey', 'private_key', 'apiKey', 'api_key', 'secret', 'rawResponse', 'prompt'
])

function scanSensitive(value, path = '$') {
  if (Array.isArray(value)) {
    value.forEach((item, index) => scanSensitive(item, `${path}[${index}]`))
    return
  }
  if (value && typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      if (FORBIDDEN_KEYS.has(key)) throw new Error(`sensitive field ${path}.${key}`)
      scanSensitive(child, `${path}.${key}`)
    }
  }
}

async function main() {
  const admin = await login('admin01', 'ADMIN')
  const teacher1 = await login('teacher01', 'ADMIN')
  const teacher2 = await login('teacher02', 'ADMIN')
  const student1 = await login('student01', 'STUDENT')
  const student3 = await login('student03', 'STUDENT')

  const courses = await request('/api/v1/admin/courses?page=1&pageSize=100', { token: admin.accessToken })
  const courseA = courses.items.find(item => item.title === '演示课程：Java 基础 A')
  const courseB = courses.items.find(item => item.title === '演示课程：Java 进阶 B')
  if (!courseA || !courseB) throw new Error('seed courses missing; run scripts/seed-demo.mjs first')
  const projects = await request('/api/v1/admin/exchange-projects?page=1&pageSize=100', { token: admin.accessToken })
  const exchange = projects.items.find(item => item.name === '演示积分兑换')
  if (!exchange) throw new Error('seed exchange project missing')
  const questionPage = await request(`/api/v1/courses/${courseA.id}/questions?page=1&pageSize=100`,
    { token: student1.accessToken })
  const question = questionPage.items.find(item => item.title === '演示问题：final 和 static 的区别？')
  if (!question) throw new Error('seed question missing')

  console.log('== Unauthenticated matrix ==')
  for (const path of ['/api/v1/admin/users', '/api/v1/me/points', '/api/v1/teacher/courses/1/assignments',
    '/api/v1/exchange-projects']) {
    await expectStatus([401], 'GET', path, null, {}, `anonymous ${path}`)
  }

  console.log('== Student matrix ==')
  for (const path of ['/api/v1/admin/users', '/api/v1/admin/courses', '/api/v1/admin/coupon-rules',
    '/api/v1/admin/exchange-projects', '/api/v1/admin/excellent-rewards',
    '/api/v1/admin/moderation-actions', '/api/v1/admin/orders', '/api/v1/admin/refunds',
    '/api/v1/admin/assignments', `/api/v1/teacher/courses/${courseA.id}/assignments`,
    `/api/v1/teacher/benefit-assignments?courseId=${courseA.id}`,
    `/api/v1/teacher/courses/${courseA.id}/submissions`]) {
    await expectStatus([403], 'GET', path, student1.accessToken, {}, `student denied ${path}`)
  }
  await expectStatus([403], 'POST', '/api/v1/admin/exchange-projects',
    student1.accessToken, {
      headers: { 'Idempotency-Key': `denied-${Date.now()}` },
      body: { name: 'denied' }
    }, 'student cannot manage exchange projects')
  await expectStatus([403], 'GET', `/api/v1/admin/questions/${question.id}/author?reason=matrix`,
    student1.accessToken, {}, 'student cannot trace anonymous author')
  await expectStatus([200], 'GET', `/api/v1/courses/${courseA.id}/notes`, student1.accessToken, {},
    'student with entitlement can read course notes')
  await expectStatus([403], 'GET', `/api/v1/courses/${courseA.id}/notes`, student3.accessToken, {},
    'student without entitlement cannot read course notes')

  console.log('== Teacher matrix ==')
  for (const path of ['/api/v1/admin/users', '/api/v1/admin/courses', '/api/v1/admin/coupon-rules',
    '/api/v1/admin/exchange-projects', '/api/v1/admin/excellent-rewards',
    '/api/v1/admin/moderation-actions', '/api/v1/me/points', '/api/v1/me/coupons',
    '/api/v1/me/notes', '/api/v1/exchange-projects',
    `/api/v1/admin/questions/${question.id}/author?reason=matrix`]) {
    await expectStatus([403], 'GET', path, teacher1.accessToken, {}, `teacher denied ${path}`)
  }
  await expectStatus([403], 'POST', `/api/v1/exchange-projects/${exchange.id}/exchanges`,
    teacher1.accessToken, {
      headers: { 'Idempotency-Key': `teacher-exchange-${Date.now()}` },
      body: { expectedRevision: exchange.revision }
    }, 'teacher cannot exchange')
  await expectStatus([200], 'GET', `/api/v1/courses/${courseA.id}/notes`, teacher1.accessToken, {},
    'teacher can read assigned course notes')
  await expectStatus([403], 'GET', `/api/v1/courses/${courseB.id}/notes`, teacher1.accessToken, {},
    'teacher cannot read unassigned course notes')
  await expectStatus([200], 'GET', `/api/v1/courses/${courseB.id}/notes`, teacher2.accessToken, {},
    'teacher02 can read assigned course B notes')

  console.log('== Admin matrix ==')
  for (const path of ['/api/v1/me/points', '/api/v1/me/courses', '/api/v1/me/notes',
    '/api/v1/me/coupons', '/api/v1/exchange-projects', `/api/v1/courses/${courseA.id}/notes`,
    `/api/v1/courses/${courseA.id}/questions`, `/api/v1/teacher/courses/${courseA.id}/assignments`,
    `/api/v1/teacher/benefit-assignments?courseId=${courseA.id}`]) {
    await expectStatus([403], 'GET', path, admin.accessToken, {}, `admin denied student endpoint ${path}`)
  }
  await expectStatus([403], 'POST', `/api/v1/exchange-projects/${exchange.id}/exchanges`,
    admin.accessToken, {
      headers: { 'Idempotency-Key': `admin-exchange-${Date.now()}` },
      body: { expectedRevision: exchange.revision }
    }, 'admin cannot exchange as student')
  for (const path of ['/api/v1/admin/users', '/api/v1/admin/courses', '/api/v1/admin/coupon-rules',
    '/api/v1/admin/exchange-projects', '/api/v1/admin/excellent-rewards',
    '/api/v1/admin/moderation-actions']) {
    await expectStatus([200], 'GET', path, admin.accessToken, {}, `admin can read ${path}`)
  }

  console.log('== Gateway and header spoofing ==')
  await expectStatus([404, 401, 403], 'GET', '/int/v1/access/check', null, {},
    'internal endpoint is not exposed by gateway')
  await expectStatus([403], 'GET', '/api/v1/admin/users', student1.accessToken,
    { headers: { 'X-User-Id': String(admin.user.id), 'X-Role': 'ADMIN' } },
    'spoofed identity headers ignored')

  console.log('== DTO sensitive field scan ==')
  const loginBody = await request('/api/v1/auth/login', {
    method: 'POST', raw: true,
    body: { username: 'student01', password: PASSWORD, clientApp: 'STUDENT' }
  })
  scanSensitive(loginBody)
  pass('login DTO has no password/hash/refresh token fields')
  const meBody = await request('/api/v1/me', { token: student1.accessToken, raw: true })
  scanSensitive(meBody)
  pass('profile DTO has no credential or internal fields')
  const adminUsers = await request('/api/v1/admin/users?page=1&pageSize=100',
    { token: admin.accessToken, raw: true })
  scanSensitive(adminUsers)
  pass('admin user list DTO has no password hash/token version')
  const coupons = await request('/api/v1/me/coupons?page=1&pageSize=100',
    { token: student1.accessToken, raw: true })
  scanSensitive(coupons)
  pass('coupon DTO has no secret or internal fields')
  const rewards = await request('/api/v1/admin/excellent-rewards?page=1&pageSize=100',
    { token: admin.accessToken, raw: true })
  scanSensitive(rewards)
  pass('governance DTO has no private note body/credentials')
  const anonymous = questionPage.items.find(item => item.title === '演示匿名问题：为什么用接口？')
  if (!anonymous) throw new Error('seed anonymous question missing')
  if (Object.prototype.hasOwnProperty.call(anonymous, 'authorId') && anonymous.authorId != null) {
    throw new Error('anonymous question leaked authorId')
  }
  pass('anonymous question DTO has no non-null authorId')

  console.log(`ALL S16-A SECURITY MATRIX CHECKS PASSED (${passed})`)
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})