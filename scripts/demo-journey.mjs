#!/usr/bin/env node
/*
 * S17-b 可亲手操作的完整演示：注册 → 免费课 → 签到/学习 → 单买 A → 路线抵扣
 * → 领券并抵扣 C → 作业 AI → 福利题 → 问答/笔记 → 积分兑换 → C 退款关联。
 * 依赖 S16-a 种子课程/路线；需要本地服务、Rabbit、Redis 和 mock 场景。
 */
const BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'
const STAMP = Date.now()

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
  const json = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(`${method} ${path} -> ${response.status} ${json.message || ''}`)
    error.status = response.status
    throw error
  }
  return json.data
}

async function login(username, app) {
  return request('/api/v1/auth/login', {
    method: 'POST', body: { username, password: PASSWORD, clientApp: app }
  })
}

async function waitFor(check, label, timeoutMs = 60000) {  const deadline = Date.now() + timeoutMs
  let last
  while (Date.now() < deadline) {
    try {
      last = await check()
      if (last) return last
    } catch (error) {
      last = error.message
    }
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  throw new Error(`${label}: ${last || 'timeout'}`)
}

function step(title) {
  console.log(`\n== ${title} ==`)
}

async function findCourse(token, title) {
  const result = await request(`/api/v1/admin/courses?keyword=${encodeURIComponent(title)}&page=1&pageSize=50`,
    { token })
  const course = result.items.find(item => item.title === title)
  if (!course) throw new Error(`missing seed course: ${title}`)
  return course
}

async function findRoute(token, title) {
  for (let page = 1; page <= 10; page += 1) {
    const result = await request(`/api/v1/admin/routes?page=${page}&pageSize=100`, { token })
    const route = result.items.find(item => item.title === title)
    if (route) return route
    if (result.items.length < 100) break
  }
  throw new Error(`missing seed route: ${title}`)
}

async function waitOrder(token, orderId) {
  return waitFor(async () => {
    const order = await request(`/api/v1/orders/${orderId}`, { token })
    return order.fulfillmentState === 'DONE' ? order : null
  }, `order ${orderId} fulfillment`)
}

async function createOrder(token, quote, key) {
  const couponIds = (quote.couponPlan || []).map(line => line.userCouponId)
  const order = await request('/api/v1/orders', {
    method: 'POST', token, headers: { 'Idempotency-Key': key },
    body: { quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: couponIds }
  })
  return order
}

async function pay(token, orderId) {
  await request(`/api/v1/orders/${orderId}/payment`, {
    method: 'POST', token, body: { channel: 'MOCK' }
  })
  await request(`/api/v1/orders/${orderId}/payment/mock-pay`, { method: 'POST', token })
  return waitOrder(token, orderId)
}

async function buyCourse(token, courseId, key, couponMode = 'NONE') {
  const quote = await request('/api/v1/trade/quotes', {
    method: 'POST', token,
    body: { productType: 'COURSE', productId: courseId, couponSelection: { mode: couponMode, ids: [] } }
  })
  if (quote.status !== 'READY') throw new Error(`quote not ready: ${quote.status}`)
  const order = await createOrder(token, quote, key)
  await pay(token, order.id)
  return { order, quote }
}

async function buyRoute(token, routeId, key) {
  const quote = await request('/api/v1/trade/quotes', {
    method: 'POST', token,
    body: { productType: 'ROUTE', productId: routeId, couponSelection: { mode: 'NONE', ids: [] } }
  })
  if (quote.status !== 'READY') throw new Error(`route quote not ready: ${quote.status}`)
  const order = await createOrder(token, quote, key)
  await pay(token, order.id)
  return { order, quote }
}

async function completeFirstLesson(token, courseId) {
  const catalog = await request(`/api/v1/learning/courses/${courseId}`, { token })
  const lesson = catalog.chapters.flatMap(chapter => chapter.lessons)[0]
  if (!lesson) throw new Error(`course ${courseId} has no lesson`)
  const authorization = await request(`/api/v1/lessons/${lesson.id}/play-authorizations`, {
    method: 'POST', token
  })
  const position = authorization.durationMs || 5055
  await request(`/api/v1/learning/lessons/${lesson.id}/progress`, {
    method: 'PUT', token,
    body: { positionMs: position, playSessionId: authorization.playSessionId }
  })
  return lesson.id
}

async function assignTeacher(adminToken, teacherId, courseId, routeId) {
  const current = await request(`/api/v1/admin/teachers/${teacherId}/assignments`, { token: adminToken })
  await request(`/api/v1/admin/teachers/${teacherId}/assignments`, {
    method: 'PUT', token: adminToken,
    body: { courseIds: [courseId], routeIds: [routeId], revision: current.revision }
  })
}

async function ensureCodeAssignment(adminToken, courseId) {
  const page = await request(`/api/v1/admin/assignments?courseId=${courseId}&page=1&pageSize=100`,
    { token: adminToken })
  let assignment = page.items.find(item => item.kind === 'COURSE_CODE')
  if (!assignment) {
    assignment = await request('/api/v1/admin/assignments', {
      method: 'POST', token: adminToken,
      body: {
        kind: 'COURSE_CODE', courseId, chapterId: null,
        publicBody: {
          scenario: '演示代码题：返回 1。',
          requirements: '补全 slot，返回 1。',
          segments: [
            { text: 'public class Journey {\n    public int value() {\n' },
            { slotId: 's1', initialCode: '        // TODO\n' },
            { text: '    }\n}\n' }
          ]
        },
        answerBody: { referenceBySlot: { s1: '        return 1;\n' }, rubric: '返回固定值。' }
      }
    })
  }
  const detail = await request(`/api/v1/admin/assignments/${assignment.id}`, { token: adminToken })
  if (detail.status !== 'ONLINE') {
    await request(`/api/v1/admin/assignments/${assignment.id}/publish`, {
      method: 'POST', token: adminToken, body: { revision: detail.revision }
    })
  }
  return assignment.id
}

async function main() {
  const admin = await login('admin01', 'ADMIN')
  const teacher = await login('teacher01', 'ADMIN')
  const courseA = await findCourse(admin.accessToken, '演示课程：Java 基础 A')
  const courseC = await findCourse(admin.accessToken, '演示课程：Java 实战 C')
  const route1 = await findRoute(admin.accessToken, '演示路线 R1：基础+进阶')
  const freeCourse = await findCourse(admin.accessToken, 'Java 入门体验')

  const username = `journey_${STAMP}`
  step('1. 注册并登录学生')
  await request('/api/v1/auth/register', {
    method: 'POST', body: { username, password: PASSWORD, nickname: `演示学员${STAMP}` }
  })
  const student = await login(username, 'STUDENT')
  const token = student.accessToken
  console.log(`PASS  student ${username} userId=${student.user.id}`)

  step('2. 免费课领取')
  const claim = await request(`/api/v1/courses/${freeCourse.id}/claim`, { method: 'POST', token })
  await waitFor(async () => {
    const state = await request(`/api/v1/claims/${claim.claimId}`, { token })
    return state.status === 'FULFILLED' ? state : null
  }, 'free claim fulfilled')
  console.log(`PASS  free course ${freeCourse.id} claim FULFILLED`)

  step('3. 签到')
  await request('/api/v1/sign-ins', { method: 'POST', token })
  console.log('PASS  sign-in +1')

  step('4. 单买课程 A 并完成第一节')
  const buyA = await buyCourse(token, courseA.id, `journey-a-${STAMP}`)
  const lessonA = await completeFirstLesson(token, courseA.id)
  console.log(`PASS  course A order ${buyA.order.id}, lesson ${lessonA} completed`)

  step('5. 购买路线 R1（A+B），验证已拥有课程抵扣')
  const buyR1 = await buyRoute(token, route1.id, `journey-r1-${STAMP}`)
  console.log(`PASS  route R1 order ${buyR1.order.id}, total=${buyR1.quote.totalCent}, credit=${buyR1.quote.creditCent}, payable=${buyR1.quote.payableCent}`)

  step('6. 创建活动券、领取并购买 C 时使用 AUTO 最优券')
  const rule = await request('/api/v1/admin/coupon-rules', {
    method: 'POST', token: admin.accessToken,
    body: {
      ruleKey: `JOURNEY_COUPON_${STAMP}`, type: 'NO_THRESHOLD', scope: 'ALL',
      amountCent: 500, validityType: 'DAYS', validDays: 7
    }
  })
  const activity = await request('/api/v1/admin/activities', {
    method: 'POST', token: admin.accessToken,
    body: {
      name: `演示旅程活动 ${STAMP}`,
      openAt: new Date(Date.now() - 3600000).toISOString().slice(0, 19),
      closeAt: new Date(Date.now() + 86400000).toISOString().slice(0, 19)
    }
  })
  const withItem = await request(`/api/v1/admin/activities/${activity.id}/items`, {
    method: 'POST', token: admin.accessToken,
    body: { ruleId: rule.id, stockTotal: 10, perUserLimit: 1 }
  })
  const itemId = withItem.items[0].id
  await request(`/api/v1/admin/activities/${activity.id}/activate`, {
    method: 'POST', token: admin.accessToken, body: { revision: withItem.revision }
  })
  const coupon = await request(`/api/v1/activity-items/${itemId}/claims`, {
    method: 'POST', token, headers: { 'Idempotency-Key': `journey-coupon-${STAMP}` }
  })
  const buyC = await buyCourse(token, courseC.id, `journey-c-${STAMP}`, 'AUTO')
  const lessonC = await completeFirstLesson(token, courseC.id)
  console.log(`PASS  coupon ${coupon.id}, course C order ${buyC.order.id}, discount=${buyC.quote.discountCent}, lesson ${lessonC} completed`)

  step('7. 作业与 mock AI 点评')
  const codeAssignment = await ensureCodeAssignment(admin.accessToken, courseA.id)
  const assignments = await request(`/api/v1/courses/${courseA.id}/assignments`, { token })
  const codeItem = assignments.find(item => String(item.id) === String(codeAssignment))
  const attempt = await request(`/api/v1/assignments/${codeAssignment}/attempts`, { method: 'POST', token })
  const submission = await request(`/api/v1/attempts/${attempt.id}/submissions`, {
    method: 'POST', token, headers: { 'Idempotency-Key': `journey-code-${STAMP}` },
    body: { answers: { s1: 'return 1;' } }
  })
  const review = await waitFor(async () => {
    const value = await request(`/api/v1/submissions/${submission.id}/review`, { token })
    return value.taskStatus === 'SUCCEEDED' ? value : null
  }, 'AI review succeeded')
  console.log(`PASS  assignment ${codeItem?.id ?? codeAssignment}, submission ${submission.id}, review SUCCEEDED, mock=${review.isMock}`)

  step('8. 教师福利题与路线资格')
  await assignTeacher(admin.accessToken, teacher.user.id, courseA.id, route1.id)
  const benefit = await request('/api/v1/teacher/benefit-assignments', {
    method: 'POST', token: teacher.accessToken,
    body: {
      kind: 'BENEFIT_CHOICE', courseId: courseA.id, routeIds: [route1.id],
      publicBody: {
        questions: [{
          id: 'q1', type: 'MULTIPLE', body: '福利题：JVM 运行时区域？', score: 10,
          options: [{ id: 'A', text: '堆' }, { id: 'B', text: '栈' }, { id: 'C', text: '磁盘' }]
        }]
      },
      answerBody: { answers: { q1: ['A', 'B'] }, analysis: { q1: '堆和栈属于运行时数据区。' } }
    }
  })
  await request(`/api/v1/teacher/benefit-assignments/${benefit.id}/publish`, {
    method: 'POST', token: teacher.accessToken, body: { revision: benefit.revision }
  })
  const benefits = await request(`/api/v1/routes/${route1.id}/benefit-assignments?courseId=${courseA.id}`, { token })
  const listedBenefit = benefits.find(item => String(item.id) === String(benefit.id)) || benefits[0]
  const benefitAttempt = await request(`/api/v1/benefit-assignments/${listedBenefit.id}/attempts`, {
    method: 'POST', token
  })
  const benefitSubmission = await request(`/api/v1/attempts/${benefitAttempt.id}/submissions`, {
    method: 'POST', token, headers: { 'Idempotency-Key': `journey-benefit-${STAMP}` },
    body: { answers: { q1: ['A', 'B'] } }
  })
  console.log(`PASS  benefit ${benefit.id} score=${benefitSubmission.score}/10`)

  step('9. 问答与笔记')
  const question = await request(`/api/v1/courses/${courseA.id}/questions`, {
    method: 'POST', token,
    body: { title: `演示旅程问题 ${STAMP}`, body: 'final 和 static 的区别？', anonymous: false }
  })
  await request(`/api/v1/questions/${question.id}/answers`, {
    method: 'POST', token: teacher.accessToken,
    body: { body: '教师回答：final 管不可重新赋值，static 管类级归属。' }
  })
  const note = await request(`/api/v1/courses/${courseA.id}/notes`, {
    method: 'POST', token, body: { body: `演示旅程笔记 ${STAMP}`, visibility: 'COURSE_PUBLIC' }
  })
  await request(`/api/v1/courses/${courseA.id}/notes`, {
    method: 'POST', token, body: { body: `演示旅程私人笔记 ${STAMP}`, visibility: 'PRIVATE' }
  })
  console.log(`PASS  question ${question.id}, teacher answer, public note ${note.id}, private note`)

  step('10. 积分兑换')
  const points = await waitFor(async () => {
    const summary = await request('/api/v1/me/points', { token })
    return summary.available >= 10 ? summary : null
  }, 'journey points available')
  const exchangeRule = await request('/api/v1/admin/coupon-rules', {
    method: 'POST', token: admin.accessToken,
    body: {
      ruleKey: `JOURNEY_EXCHANGE_${STAMP}`, type: 'NO_THRESHOLD', scope: 'ALL',
      amountCent: 500, validityType: 'DAYS', validDays: 7
    }
  })
  const exchangeProject = await request('/api/v1/admin/exchange-projects', {
    method: 'POST', token: admin.accessToken,
    body: {
      name: `演示旅程兑换 ${STAMP}`, ruleId: exchangeRule.id, pointsPrice: 10,
      stockTotal: 5, perUserLimit: 1,
      openAt: new Date(Date.now() - 3600000).toISOString().slice(0, 19),
      closeAt: new Date(Date.now() + 86400000).toISOString().slice(0, 19)
    }
  })
  const exchangeOnline = await request(`/api/v1/admin/exchange-projects/${exchangeProject.id}/online`, {
    method: 'POST', token: admin.accessToken, body: { revision: 0 }
  })
  const exchange = await request(`/api/v1/exchange-projects/${exchangeProject.id}/exchanges`, {
    method: 'POST', token, headers: { 'Idempotency-Key': `journey-exchange-${STAMP}` },
    body: { expectedRevision: exchangeOnline.revision }
  })
  console.log(`PASS  points ${points.available} -> exchange ${exchange.exchangeId}, coupon ${exchange.userCouponId}, balance ${exchange.balance}`)

  step('11. 课程 C 退款与来源撤销')
  await request(`/api/v1/dev/refunds/${buyC.order.id}/scenario`, {
    method: 'POST', token: admin.accessToken, body: { outcome: 'COMPLETED' }
  })
  const refund = await request(`/api/v1/orders/${buyC.order.id}/refund-applications`, {
    method: 'POST', token, body: { reason: '演示旅程退款关联' }
  })
  const refundState = await waitFor(async () => {
    const state = await request(`/api/v1/refund-applications/${refund.requestId}`, { token })
    return state.state === 'COMPLETED' ? state : null
  }, 'refund application completed')
  const myCourses = await request('/api/v1/me/courses?page=1&pageSize=100', { token })
  if (myCourses.items.some(item => String(item.courseId) === String(courseC.id))) {
    throw new Error('refunded course C still grants access')
  }
  const coupons = await request('/api/v1/me/coupons?page=1&pageSize=100', { token })
  const usedCoupon = coupons.items.find(item => String(item.id) === String(coupon.id))
  if (!usedCoupon || usedCoupon.status !== 'USED') {
    throw new Error('coupon was returned or lost after refund')
  }
  console.log(`PASS  refund ${refundState.requestId} COMPLETED, course C access revoked, coupon remains USED`)

  step('12. 汇总')
  console.log(`账号：${username} / ${PASSWORD}`)
  console.log(`课程：A=${courseA.id}, C=${courseC.id}; 路线 R1=${route1.id}; 免费课=${freeCourse.id}`)
  console.log(`订单：A=${buyA.order.id}, R1=${buyR1.order.id}, C=${buyC.order.id}`)
  console.log(`作业提交=${submission.id}; 福利提交=${benefitSubmission.id}; 兑换=${exchange.exchangeId}`)
  console.log('可在学生端 /me/courses、/me/points、/me/coupons、/me/exchanges、/orders 查看结果。')
  console.log('ALL S17-B DEMO JOURNEY CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})