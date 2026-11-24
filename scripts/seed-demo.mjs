#!/usr/bin/env node
/*
 * 演示数据种子：通过公开/管理 API 组装一条可操作的完整演示链。
 * 仅用于本地新演示库；固定名称和幂等键保证重复执行不重复建核心对象。
 * 用法：node scripts/seed-demo.mjs   （服务已启动，默认 http://127.0.0.1:18080）
 */
import { execFileSync } from 'node:child_process'

const BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'
const DB_PASSWORD = process.env.DB_PASSWORD || '1234'
const MYSQL = process.env.MYSQL_BIN || 'mysql'

const DEMO = {
  courseA: '演示课程：Java 基础 A',
  courseB: '演示课程：Java 进阶 B',
  courseC: '演示课程：Java 实战 C',
  route1: '演示路线 R1：基础+进阶',
  route2: '演示路线 R2：基础+实战',
  activity: '演示领券活动',
  exchange: '演示积分兑换'
}

function db(sql) {
  return execFileSync(MYSQL, [
    '--default-character-set=utf8mb4', '-h127.0.0.1', '-uroot', '-N', '-B', '-e', sql
  ], { env: { ...process.env, MYSQL_PWD: DB_PASSWORD }, encoding: 'utf8' }).trim()
}

async function request(path, { method = 'GET', token, body, headers = {} } = {}) {
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let json = null
  try {
    json = await response.json()
  } catch {
    // keep null for non-JSON errors
  }
  if (!response.ok) {
    const error = new Error(`${method} ${path} -> HTTP ${response.status}: ${json?.message || ''}`)
    error.status = response.status
    error.code = json?.code
    throw error
  }
  return json?.data
}

async function login(username, clientApp) {
  return request('/api/v1/auth/login', {
    method: 'POST',
    body: { username, password: PASSWORD, clientApp }
  })
}

async function findPaged(path, predicate, token, pageSize = 100, maxPages = 20) {
  for (let page = 1; page <= maxPages; page += 1) {
    const result = await request(`${path}${path.includes('?') ? '&' : '?'}page=${page}&pageSize=${pageSize}`,
      { token })
    const found = result.items.find(predicate)
    if (found) return found
    if (result.items.length < pageSize) return null
  }
  return null
}

async function ensureCategory(token) {
  const categories = await request('/api/v1/admin/categories', { token })
  const walk = nodes => {
    for (const node of nodes || []) {
      if (node.name === '基础语法') return node.id
      const nested = walk(node.children)
      if (nested) return nested
    }
    return null
  }
  return walk(categories) || 2
}

async function ensureMedia(token) {
  const result = await request('/api/v1/admin/media?status=AVAILABLE&provider=DEMO&page=1&pageSize=100',
    { token })
  if (result.items.length > 0) return result.items[0]
  return request('/api/v1/admin/media/demo-import', {
    method: 'POST', token,
    body: { relativePath: 'flower.mp4', title: 'DEMO flower' }
  })
}

async function ensureCourse(token, categoryId, mediaId, title, priceCent, lessons = 10) {
  const found = await request(`/api/v1/admin/courses?keyword=${encodeURIComponent(title)}&pageSize=100`,
    { token })
  let course = found.items.find(item => item.title === title)
  if (!course) {
    course = await request('/api/v1/admin/courses', {
      method: 'POST', token,
      body: { title, description: '演示数据；可重复执行的本地种子。', categoryId, priceCent, isFree: false }
    })
    const perChapter = Math.ceil(lessons / 2)
    for (let chapterIndex = 1; chapterIndex <= 2; chapterIndex += 1) {
      const chapter = await request(`/api/v1/admin/courses/${course.id}/chapters`, {
        method: 'POST', token, body: { title: `第 ${chapterIndex} 章`, sort: chapterIndex }
      })
      const start = (chapterIndex - 1) * perChapter + 1
      const end = Math.min(lessons, chapterIndex * perChapter)
      for (let sort = start; sort <= end; sort += 1) {
        await request(`/api/v1/admin/chapters/${chapter.id}/lessons`, {
          method: 'POST', token,
          body: { title: `第 ${sort} 节`, sort: sort - start + 1, mediaId }
        })
      }
    }
    const detail = await request(`/api/v1/admin/courses/${course.id}`, { token })
    await request(`/api/v1/admin/courses/${course.id}/publish`, {
      method: 'POST', token, body: { revision: detail.revision }
    })
  }
  const detail = await request(`/api/v1/admin/courses/${course.id}`, { token })
  if (detail.status !== 'ONLINE') {
    await request(`/api/v1/admin/courses/${course.id}/publish`, {
      method: 'POST', token, body: { revision: detail.revision }
    })
  }
  return { id: course.id, chapters: detail.chapters || [] }
}

async function ensureRoute(token, title, priceCent, courseA, courseB) {
  let route = await findPaged('/api/v1/admin/routes', item => item.title === title, token)
  if (!route) {
    route = await request('/api/v1/admin/routes', {
      method: 'POST', token,
      body: {
        title, description: '演示数据；可重复执行的本地种子。', priceCent,
        members: [{ courseId: courseA, sort: 1 }, { courseId: courseB, sort: 2 }]
      }
    })
  }
  const detail = await request(`/api/v1/admin/routes/${route.id}`, { token })
  if (detail.status !== 'ONLINE') {
    await request(`/api/v1/admin/routes/${route.id}/publish`, {
      method: 'POST', token, body: { revision: detail.revision }
    })
  }
  return route.id
}

async function assignTeacher(adminToken, teacherId, courseIds, routeIds) {
  const current = await request(`/api/v1/admin/teachers/${teacherId}/assignments`, { token: adminToken })
  await request(`/api/v1/admin/teachers/${teacherId}/assignments`, {
    method: 'PUT', token: adminToken,
    body: { courseIds, routeIds, revision: current.revision }
  })
}

async function findCourseDetail(token, courseId) {
  return request(`/api/v1/courses/${courseId}`, { token })
}

async function buyCourse(token, courseId, key) {
  const detail = await findCourseDetail(token, courseId)
  if (detail.isOwned) return
  let quote
  try {
    quote = await request('/api/v1/trade/quotes', {
      method: 'POST', token,
      body: { productType: 'COURSE', productId: courseId, couponSelection: { mode: 'NONE', ids: [] } }
    })
  } catch (error) {
    if (error.status === 409 && /已拥有/.test(error.message)) return
    throw error
  }
  const order = await request('/api/v1/orders', {
    method: 'POST', token, headers: { 'Idempotency-Key': key },
    body: { quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: [] }
  })
  await request(`/api/v1/orders/${order.id}/payment`, {
    method: 'POST', token, body: { channel: 'MOCK' }
  })
  await request(`/api/v1/orders/${order.id}/payment/mock-pay`, { method: 'POST', token })
  await waitFulfilled(token, order.id)
}

async function waitFulfilled(token, orderId) {
  for (let i = 0; i < 60; i += 1) {
    const order = await request(`/api/v1/orders/${orderId}`, { token })
    if (order.fulfillmentState === 'DONE') return
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  throw new Error(`order ${orderId} not fulfilled`)
}

async function buyRoute(token, routeId, key) {
  const owned = await request('/api/v1/me/routes?page=1&pageSize=100', { token })
  if (owned.items.some(item => String(item.id) === String(routeId))) return
  let quote
  try {
    quote = await request('/api/v1/trade/quotes', {
      method: 'POST', token,
      body: { productType: 'ROUTE', productId: routeId, couponSelection: { mode: 'NONE', ids: [] } }
    })
  } catch (error) {
    if (error.status === 409) return
    throw error
  }
  const order = await request('/api/v1/orders', {
    method: 'POST', token, headers: { 'Idempotency-Key': key },
    body: { quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: [] }
  })
  await request(`/api/v1/orders/${order.id}/payment`, {
    method: 'POST', token, body: { channel: 'MOCK' }
  })
  await request(`/api/v1/orders/${order.id}/payment/mock-pay`, { method: 'POST', token })
  await waitFulfilled(token, order.id)
}

async function ensureChoiceAssignment(adminToken, courseId, chapterId) {
  const page = await request(`/api/v1/admin/assignments?courseId=${courseId}&page=1&pageSize=100`,
    { token: adminToken })
  let assignment = page.items.find(item => item.kind === 'CHAPTER_CHOICE')
  if (!assignment) {
    assignment = await request('/api/v1/admin/assignments', {
      method: 'POST', token: adminToken,
      body: {
        kind: 'CHAPTER_CHOICE', courseId, chapterId,
        publicBody: {
          questions: [
            { id: 'q1', type: 'SINGLE', body: 'final 关键字表示什么？', score: 10,
              options: [{ id: 'A', text: '不可重新赋值' }, { id: 'B', text: '必须初始化' }] }
          ]
        },
        answerBody: { answers: { q1: ['A'] }, analysis: { q1: 'final 表示不可重新赋值。' } }
      }
    })
  }
  const detail = await request(`/api/v1/admin/assignments/${assignment.id}`, { token: adminToken })
  if (detail.status !== 'ONLINE') {
    await request(`/api/v1/admin/assignments/${assignment.id}/publish`, {
      method: 'POST', token: adminToken, body: { revision: detail.revision }
    })
  }
}

async function ensureExchangeRule(adminToken) {
  const rules = await request('/api/v1/admin/coupon-rules?page=1&pageSize=100', { token: adminToken })
  let rule = rules.items.find(item => item.ruleKey === 'DEMO_EXCHANGE_2026')
  if (!rule) {
    rule = await request('/api/v1/admin/coupon-rules', {
      method: 'POST', token: adminToken,
      body: {
        ruleKey: 'DEMO_EXCHANGE_2026', type: 'NO_THRESHOLD', scope: 'ALL',
        amountCent: 500, validityType: 'DAYS', validDays: 30
      }
    })
  }
  return rule.id
}

async function ensureActivityRule(adminToken) {
  const rules = await request('/api/v1/admin/coupon-rules?page=1&pageSize=100', { token: adminToken })
  let rule = rules.items.find(item => item.ruleKey === 'DEMO_ACTIVITY_2026')
  if (!rule) {
    rule = await request('/api/v1/admin/coupon-rules', {
      method: 'POST', token: adminToken,
      body: {
        ruleKey: 'DEMO_ACTIVITY_2026', type: 'NO_THRESHOLD', scope: 'ALL',
        amountCent: 300, validityType: 'DAYS', validDays: 15
      }
    })
  }
  return rule.id
}

async function ensureActivity(adminToken, ruleId) {
  let activity = await findPaged('/api/v1/admin/activities', item => item.name === DEMO.activity, adminToken)
  if (!activity) {
    const now = new Date()
    activity = await request('/api/v1/admin/activities', {
      method: 'POST', token: adminToken,
      body: {
        name: DEMO.activity,
        openAt: new Date(now.getTime() - 86400000).toISOString().slice(0, 19),
        closeAt: new Date(now.getTime() + 30 * 86400000).toISOString().slice(0, 19)
      }
    })
  }
  let detail = await request(`/api/v1/admin/activities/${activity.id}`, { token: adminToken })
  if (detail.items.length === 0) {
    detail = await request(`/api/v1/admin/activities/${activity.id}/items`, {
      method: 'POST', token: adminToken,
      body: { ruleId, stockTotal: 100, perUserLimit: 1 }
    })
  }
  if (detail.state !== 'ACTIVE') {
    await request(`/api/v1/admin/activities/${activity.id}/activate`, {
      method: 'POST', token: adminToken, body: { revision: detail.revision }
    })
    detail = await request(`/api/v1/admin/activities/${activity.id}`, { token: adminToken })
  }
  return detail
}

async function ensureExchangeProject(adminToken, ruleId) {
  let project = await findPaged('/api/v1/admin/exchange-projects',
    item => item.name === DEMO.exchange, adminToken)
  if (!project) {
    const now = new Date()
    project = await request('/api/v1/admin/exchange-projects', {
      method: 'POST', token: adminToken,
      body: {
        name: DEMO.exchange, ruleId, pointsPrice: 100, stockTotal: 20, perUserLimit: 2,
        openAt: new Date(now.getTime() - 3600000).toISOString().slice(0, 19),
        closeAt: new Date(now.getTime() + 30 * 86400000).toISOString().slice(0, 19)
      }
    })
  }
  const detail = await request(`/api/v1/admin/exchange-projects/${project.id}`, { token: adminToken })
  if (detail.state !== 'ONLINE') {
    await request(`/api/v1/admin/exchange-projects/${project.id}/online`, {
      method: 'POST', token: adminToken, body: { revision: detail.revision }
    })
  }
  return detail
}

async function ensureStudentAssets(studentToken, student2Token, teacherToken, courseA, route1) {
  await buyCourse(studentToken, courseA, 'demo-buy-A-student01')
  await buyCourse(student2Token, courseA, 'demo-buy-A-student02')
  await buyRoute(studentToken, route1, 'demo-route-R1-student01')

  const notes = await request(`/api/v1/courses/${courseA}/notes?page=1&pageSize=100`, { token: studentToken })
  let publicNote = notes.items.find(item => item.excerpt.includes('演示公开笔记'))
  if (!publicNote) {
    publicNote = await request(`/api/v1/courses/${courseA}/notes`, {
      method: 'POST', token: studentToken,
      body: { body: '演示公开笔记：final 表示不可重新赋值。', visibility: 'COURSE_PUBLIC' }
    })
  }
  const privateNotes = await request(`/api/v1/courses/${courseA}/notes?page=1&pageSize=100`, { token: studentToken })
  if (!privateNotes.items.some(item => item.excerpt.includes('演示私人笔记'))) {
    await request(`/api/v1/courses/${courseA}/notes`, {
      method: 'POST', token: studentToken,
      body: { body: '演示私人笔记：只对自己可见。', visibility: 'PRIVATE' }
    })
  }
  await request(`/api/v1/notes/${publicNote.id}/collect`, { method: 'POST', token: student2Token })

  const questions = await request(`/api/v1/courses/${courseA}/questions?page=1&pageSize=100`, { token: studentToken })
  let question = questions.items.find(item => item.title === '演示问题：final 和 static 的区别？')
  if (!question) {
    question = await request(`/api/v1/courses/${courseA}/questions`, {
      method: 'POST', token: studentToken,
      body: { title: '演示问题：final 和 static 的区别？', body: '请说明 final 与 static 的区别。', anonymous: false }
    })
  }
  const anonymous = questions.items.find(item => item.title === '演示匿名问题：为什么用接口？')
  if (!anonymous) {
    await request(`/api/v1/courses/${courseA}/questions`, {
      method: 'POST', token: student2Token,
      body: { title: '演示匿名问题：为什么用接口？', body: '匿名提问，说明接口的作用。', anonymous: true }
    })
  }
  const thread = await request(`/api/v1/questions/${question.id}`, { token: teacherToken })
  if (!thread.answers.some(item => item.authorRole === 'TEACHER')) {
    await request(`/api/v1/questions/${question.id}/answers`, {
      method: 'POST', token: teacherToken,
      body: { body: '演示教师回答：final 管重新赋值，static 管类级归属。' }
    })
  }
}

function seedPoints() {
  db("INSERT INTO bc_marketing.points_account(user_id,available,debt,revision,created_at,updated_at) SELECT id,300,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3) FROM bc_user.user_account WHERE username='student01' ON DUPLICATE KEY UPDATE available=GREATEST(available,300)")
  db("INSERT INTO bc_marketing.points_account(user_id,available,debt,revision,created_at,updated_at) SELECT id,10,3,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3) FROM bc_user.user_account WHERE username='student02' ON DUPLICATE KEY UPDATE debt=GREATEST(debt,3)")
}

async function main() {
  const admin = await login('admin01', 'ADMIN')
  const teacher1 = await login('teacher01', 'ADMIN')
  const teacher2 = await login('teacher02', 'ADMIN')
  const student1 = await login('student01', 'STUDENT')
  const student2 = await login('student02', 'STUDENT')
  const student3 = await login('student03', 'STUDENT')
  const adminToken = admin.accessToken

  const categoryId = await ensureCategory(adminToken)
  const media = await ensureMedia(adminToken)
  const mediaId = media.id
  const courseA = await ensureCourse(adminToken, categoryId, mediaId, DEMO.courseA, 10000)
  const courseB = await ensureCourse(adminToken, categoryId, mediaId, DEMO.courseB, 20000)
  const courseC = await ensureCourse(adminToken, categoryId, mediaId, DEMO.courseC, 30000)
  const route1 = await ensureRoute(adminToken, DEMO.route1, 24000, courseA.id, courseB.id)
  const route2 = await ensureRoute(adminToken, DEMO.route2, 32000, courseA.id, courseC.id)

  await assignTeacher(adminToken, teacher1.user.id, [courseA.id], [route1, route2])
  await assignTeacher(adminToken, teacher2.user.id, [courseB.id, courseC.id], [route1, route2])
  const chapterA = courseA.chapters?.[0]?.id
  if (chapterA) await ensureChoiceAssignment(adminToken, courseA.id, chapterA)

  const exchangeRule = await ensureExchangeRule(adminToken)
  const activityRule = await ensureActivityRule(adminToken)
  const activity = await ensureActivity(adminToken, activityRule)
  const item = activity.items[0]
  await request(`/api/v1/activity-items/${item.id}/claims`, {
    method: 'POST', token: student1.accessToken,
    headers: { 'Idempotency-Key': 'demo-activity-claim' }
  })
  const exchangeProject = await ensureExchangeProject(adminToken, exchangeRule)

  await ensureStudentAssets(student1.accessToken, student2.accessToken, teacher1.accessToken,
    courseA.id, route1)

  seedPoints()
  try {
    await request(`/api/v1/exchange-projects/${exchangeProject.id}/exchanges`, {
      method: 'POST', token: student1.accessToken,
      headers: { 'Idempotency-Key': 'demo-exchange-1' },
      body: { expectedRevision: exchangeProject.revision }
    })
  } catch (error) {
    if (error.status !== 409 || error.code !== 'EXCHANGE_CHANGED') throw error
  }

  // 免费课领取作为演示链的最低门槛入口。
  const freeCourse = await request(`/api/v1/admin/courses?keyword=${encodeURIComponent('Java 入门体验')}&pageSize=20`,
    { token: adminToken })
  const free = freeCourse.items.find(item => item.title === 'Java 入门体验')
  if (free) {
    try {
      await request(`/api/v1/courses/${free.id}/claim`, { method: 'POST', token: student3.accessToken })
    } catch (error) {
      if (error.status !== 409) throw error
    }
    for (let i = 0; i < 40; i += 1) {
      const mine = await request('/api/v1/me/courses?page=1&pageSize=100', { token: student3.accessToken })
      if (mine.items.some(item => String(item.courseId) === String(free.id))) break
      if (i === 39) throw new Error('student03 free-course claim not fulfilled')
      await new Promise(resolve => setTimeout(resolve, 500))
    }
  }

  console.log('PASS  demo seed ready')
  console.log(`  courses: ${courseA.id}, ${courseB.id}, ${courseC.id}`)
  console.log(`  routes: ${route1}, ${route2}`)
  console.log(`  activity: ${activity.id}; exchange project: ${exchangeProject.id}`)
  console.log(`  accounts: student01-03, teacher01-02, admin01; ${DEMO.courseA} 10 lessons`)
}

main().catch(error => {
  console.error('FAIL  demo seed:', error.message)
  process.exit(1)
})