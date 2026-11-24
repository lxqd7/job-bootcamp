const BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'
const TITLES = {
  a: '演示课程：Java 基础 A',
  b: '演示课程：Java 进阶 B',
  c: '演示课程：Java 实战 C',
  r1: '演示路线 R1：基础+进阶',
  r2: '演示路线 R2：基础+实战',
  activity: '演示领券活动',
  exchange: '演示积分兑换'
}

let passed = 0
function pass(label) {
  passed += 1
  console.log(`PASS  ${label}`)
}
function assert(condition, label) {
  if (!condition) throw new Error(label)
  pass(label)
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
  const json = await response.json()
  if (!response.ok) throw new Error(`${method} ${path} -> ${response.status} ${json.message || ''}`)
  return json.data
}

async function login(username, app) {
  return request('/api/v1/auth/login', {
    method: 'POST',
    body: { username, password: PASSWORD, clientApp: app }
  })
}

async function findPaged(path, predicate, token, pageSize = 100, maxPages = 10) {
  for (let page = 1; page <= maxPages; page += 1) {
    const result = await request(`${path}${path.includes('?') ? '&' : '?'}page=${page}&pageSize=${pageSize}`,
      { token })
    const found = result.items.find(predicate)
    if (found) return found
    if (result.items.length < pageSize) return null
  }
  return null
}

async function main() {
  const admin = await login('admin01', 'ADMIN')
  const teacher = await login('teacher01', 'ADMIN')
  const s1 = await login('student01', 'STUDENT')
  const s2 = await login('student02', 'STUDENT')
  const s3 = await login('student03', 'STUDENT')

  const courseA = await findPaged('/api/v1/admin/courses?keyword=' + encodeURIComponent(TITLES.a),
    item => item.title === TITLES.a, admin.accessToken)
  assert(courseA, 'seed course A exists')
  const courseB = await findPaged('/api/v1/admin/courses?keyword=' + encodeURIComponent(TITLES.b),
    item => item.title === TITLES.b, admin.accessToken)
  const courseC = await findPaged('/api/v1/admin/courses?keyword=' + encodeURIComponent(TITLES.c),
    item => item.title === TITLES.c, admin.accessToken)
  assert(courseB && courseC, 'seed courses B/C exist')
  const detailA = await request(`/api/v1/admin/courses/${courseA.id}`, { token: admin.accessToken })
  const lessons = (detailA.chapters || []).flatMap(chapter => chapter.lessons || [])
  assert(detailA.status === 'ONLINE', 'seed course A published')
  assert((detailA.chapters || []).length === 2 && lessons.length === 10,
    'seed course A has 2 chapters and 10 lessons')

  const routes = await request('/api/v1/admin/routes?page=1&pageSize=200', { token: admin.accessToken })
  const r1 = routes.items.find(item => item.title === TITLES.r1)
  const r2 = routes.items.find(item => item.title === TITLES.r2)
  assert(r1 && r2, 'seed routes R1/R2 exist')
  const r1Detail = await request(`/api/v1/admin/routes/${r1.id}`, { token: admin.accessToken })
  assert(r1Detail.status === 'ONLINE' && r1Detail.members.length === 2, 'route R1 online with 2 members')

  const current = await request(`/api/v1/admin/teachers/${teacher.user.id}/assignments`, {
    token: admin.accessToken
  })
  assert(current.courseIds.includes(Number(courseA.id)) || current.courseIds.includes(courseA.id),
    'teacher01 assigned to seed course A')

  const assignments = await request(`/api/v1/admin/assignments?courseId=${courseA.id}&page=1&pageSize=50`,
    { token: admin.accessToken })
  assert(assignments.items.some(item => item.kind === 'CHAPTER_CHOICE' && item.status === 'ONLINE'),
    'seed chapter choice assignment online')

  const activity = await findPaged('/api/v1/admin/activities', item => item.name === TITLES.activity,
    admin.accessToken)
  assert(activity, 'seed activity exists')
  const activityDetail = await request(`/api/v1/admin/activities/${activity.id}`, { token: admin.accessToken })
  assert(activityDetail.state === 'ACTIVE' && activityDetail.items.length > 0, 'seed activity active with coupon item')
  const exchange = await findPaged('/api/v1/admin/exchange-projects', item => item.name === TITLES.exchange,
    admin.accessToken)
  assert(exchange, 'seed exchange project exists')
  const exchangeDetail = await request(`/api/v1/admin/exchange-projects/${exchange.id}`, {
    token: admin.accessToken
  })
  assert(exchangeDetail.state === 'ONLINE', 'seed exchange project online')

  const myCourses1 = await request('/api/v1/me/courses?page=1&pageSize=100', { token: s1.accessToken })
  const myCourses2 = await request('/api/v1/me/courses?page=1&pageSize=100', { token: s2.accessToken })
  assert(myCourses1.items.some(item => String(item.courseId) === String(courseA.id)), 'student01 owns seed course A')
  assert(myCourses2.items.some(item => String(item.courseId) === String(courseA.id)), 'student02 owns seed course A')
  const myRoutes = await request('/api/v1/me/routes?page=1&pageSize=100', { token: s1.accessToken })
  assert(myRoutes.items.some(item => String(item.routeId) === String(r1.id)), 'student01 owns route R1')
  const freeCourses = await request('/api/v1/me/courses?page=1&pageSize=100', { token: s3.accessToken })
  assert(freeCourses.total >= 1, 'student03 has free course access')

  const notes1 = await request(`/api/v1/me/notes?page=1&pageSize=100`, { token: s1.accessToken })
  assert(notes1.items.some(item => item.type === 'ORIGINAL' && item.visibility === 'COURSE_PUBLIC'),
    'seed public note exists')
  assert(notes1.items.some(item => item.type === 'ORIGINAL' && item.visibility === 'PRIVATE'),
    'seed private note exists')
  const notes2 = await request(`/api/v1/me/notes?page=1&pageSize=100`, { token: s2.accessToken })
  assert(notes2.items.some(item => item.type === 'COLLECTED'), 'seed collected note exists')

  const questions = await request(`/api/v1/courses/${courseA.id}/questions?page=1&pageSize=100`,
    { token: s1.accessToken })
  const publicQuestion = questions.items.find(item => item.title === '演示问题：final 和 static 的区别？')
  const anonymousQuestion = questions.items.find(item => item.title === '演示匿名问题：为什么用接口？')
  assert(publicQuestion, 'seed public question exists')
  assert(anonymousQuestion && anonymousQuestion.authorDisplay === '匿名学员', 'seed anonymous question projects anonymous')
  const thread = await request(`/api/v1/questions/${publicQuestion.id}`, { token: teacher.accessToken })
  assert(thread.answers.some(item => item.authorRole === 'TEACHER'), 'seed teacher answer exists')

  const coupons = await request('/api/v1/me/coupons?page=1&pageSize=100', { token: s1.accessToken })
  assert(coupons.items.some(item => item.sourceType === 'ACTIVITY'), 'student01 has activity coupon')
  const exchangeCoupons = coupons.items.filter(item => item.sourceType === 'EXCHANGE')
  assert(exchangeCoupons.length >= 1 && exchangeCoupons[0].sourceName === TITLES.exchange,
    'student01 has exchange coupon with project source')
  const exchanges = await request('/api/v1/me/exchanges?page=1&pageSize=20', { token: s1.accessToken })
  assert(exchanges.items.some(item => item.projectName === TITLES.exchange), 'seed exchange record exists')
  const points2 = await request('/api/v1/me/points', { token: s2.accessToken })
  assert(points2.debt >= 3, 'seed debt scenario exists for student02')

  console.log(`ALL S16-A DEMO SEED CHECKS PASSED (${passed})`)
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})