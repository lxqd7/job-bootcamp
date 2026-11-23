import { setTimeout as sleep } from 'node:timers/promises'
import { execFileSync } from 'node:child_process'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const STUDENT_APP = process.env.STUDENT_APP || 'http://localhost:15173'
const ADMIN_APP = process.env.ADMIN_APP || 'http://localhost:15174'
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'
const MYSQL = process.env.MYSQL_BIN || 'mysql'
const STAMP = Date.now()

class Cdp {
  constructor(wsUrl) {
    this.ws = new WebSocket(wsUrl)
    this.nextId = 1
    this.pending = new Map()
    this.exceptions = []
    this.failedResponses = []
    this.ready = new Promise((resolve, reject) => {
      this.ws.addEventListener('open', resolve, { once: true })
      this.ws.addEventListener('error', reject, { once: true })
    })
    this.ws.addEventListener('message', event => {
      const message = JSON.parse(event.data)
      if (message.id) {
        const pending = this.pending.get(message.id)
        if (!pending) return
        this.pending.delete(message.id)
        message.error ? pending.reject(new Error(message.error.message)) : pending.resolve(message.result)
      }
      if (message.method === 'Runtime.exceptionThrown') {
        this.exceptions.push(message.params.exceptionDetails?.text || 'exception')
      }
      if (message.method === 'Network.responseReceived' && message.params.response.status >= 500) {
        this.failedResponses.push(`${message.params.response.url} ${message.params.response.status}`)
      }
    })
  }

  async send(method, params = {}) {
    await this.ready
    const id = this.nextId++
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
    })
  }

  close() { this.ws.close() }
}

async function newTab(url) {
  const response = await fetch(`${CDP_BASE}/json/new?${encodeURIComponent(url)}`, { method: 'PUT' })
  if (!response.ok) throw new Error(`create tab failed: ${response.status}`)
  return response.json()
}

async function evaluate(cdp, expression) {
  const result = await cdp.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (result.exceptionDetails) throw new Error(result.exceptionDetails.text || 'evaluate failed')
  return result.result.value
}

async function waitFor(cdp, expression, label, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const value = await evaluate(cdp, expression).catch(() => false)
    if (value) return value
    await sleep(200)
  }
  throw new Error(`timeout waiting for ${label}`)
}

async function navigate(cdp, url) {
  await cdp.send('Page.navigate', { url })
  await waitFor(cdp, 'document.readyState === "complete"', `load ${url}`)
}

async function fill(cdp, selector, value) {
  const encoded = JSON.stringify(value)
  const filled = await evaluate(cdp, `(() => {
    const el=document.querySelector(${JSON.stringify(selector)});
    if(!el) return false;
    el.value=${encoded};
    el.dispatchEvent(new Event('input',{bubbles:true}));
    el.dispatchEvent(new Event('change',{bubbles:true}));
    return true;
  })()`)
  if (!filled) throw new Error(`element not found: ${selector}`)
}

async function clickText(cdp, selector, text) {
  const clicked = await evaluate(cdp, `(() => {
    const el=[...document.querySelectorAll(${JSON.stringify(selector)})]
      .find(item => !item.disabled && item.innerText.includes(${JSON.stringify(text)}));
    if(!el) return false;
    el.click();
    return true;
  })()`)
  if (!clicked) throw new Error(`enabled element with text not found: ${text}`)
}

async function login(cdp, app, username) {
  await cdp.send('Network.clearBrowserCookies').catch(() => {})
  await navigate(cdp, `${app}/login`)
  await waitFor(cdp, 'Boolean(document.querySelector("input[autocomplete=username]"))', 'login form')
  await fill(cdp, 'input[autocomplete=username]', username)
  await fill(cdp, 'input[type=password]', PASSWORD)
  await evaluate(cdp, 'document.querySelector("button[type=submit]").click()')
  await waitFor(cdp, 'location.pathname !== "/login"', `login ${username}`)
}

async function api(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  })
  const body = await response.json()
  if (!response.ok) throw new Error(`${path} ${response.status} ${JSON.stringify(body)}`)
  return body.data
}

async function loginApi(username, clientApp) {
  return api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, clientApp })
  })
}

function db(sql) {
  return execFileSync(MYSQL, [
    '--default-character-set=utf8mb4', '-h127.0.0.1', '-uroot', '-N', '-B', '-e', sql
  ], { env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD || '1234' }, encoding: 'utf8' }).trim()
}

async function setup() {
  const admin = await loginApi('admin01', 'ADMIN')
  const adminHeaders = { Authorization: `Bearer ${admin.accessToken}` }
  const teacher = await loginApi('teacher01', 'ADMIN')
  const suffix = STAMP
  const courses = []
  for (const name of ['C', 'B', 'D']) {
    const course = await api('/api/v1/admin/courses', {
      method: 'POST',
      headers: adminHeaders,
      body: JSON.stringify({
        title: `S12C_${name}_${suffix}`, description: 'S12C', categoryId: 2, priceCent: 990, isFree: false
      })
    })
    const chapter = await api(`/api/v1/admin/courses/${course.id}/chapters`, {
      method: 'POST',
      headers: adminHeaders,
      body: JSON.stringify({ title: 'Chapter', sort: 1 })
    })
    await api(`/api/v1/admin/chapters/${chapter.id}/lessons`, {
      method: 'POST',
      headers: adminHeaders,
      body: JSON.stringify({ title: 'Video', sort: 1, mediaId: 1 })
    })
    await api(`/api/v1/admin/courses/${course.id}/publish`, {
      method: 'POST',
      headers: adminHeaders,
      body: JSON.stringify({ revision: 0 })
    })
    courses.push(course)
  }
  const route = await api('/api/v1/admin/routes', {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({
      title: `S12C_R_${suffix}`, description: 'S12C', priceCent: 300,
      members: courses.map((course, index) => ({ courseId: course.id, sort: index + 1 }))
    })
  })
  await api(`/api/v1/admin/routes/${route.id}/publish`, {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({ revision: route.revision })
  })
  const current = await api(`/api/v1/admin/teachers/${teacher.user.id}/assignments`, { headers: adminHeaders })
  await api(`/api/v1/admin/teachers/${teacher.user.id}/assignments`, {
    method: 'PUT',
    headers: adminHeaders,
    body: JSON.stringify({ courseIds: [courses[0].id], routeIds: [route.id], revision: current.revision })
  })

  // Pre-create a benefit code task so the student page can exercise code + AI feedback.
  const codePublic = {
    scenario: '福利代码题：返回订单应付金额。',
    requirements: '补全 calculate；数量为负返回 0；使用 long。',
    segments: [
      { text: 'public class Benefit {\n    public long calculate(long unit, int quantity) {\n' },
      { slotId: 's1', initialCode: '        // TODO\n' },
      { text: '    }\n}\n' }
    ]
  }
  const codeAnswer = {
    referenceBySlot: { s1: '        if (quantity < 0) return 0;\n        return unit * quantity;\n' },
    rubric: '负数、long、无编译错误；允许等价写法。'
  }
  const code = await api('/api/v1/teacher/benefit-assignments', {
    method: 'POST',
    headers: { Authorization: `Bearer ${teacher.accessToken}` },
    body: JSON.stringify({
      kind: 'BENEFIT_CODE', courseId: courses[0].id, routeIds: [route.id],
      publicBody: codePublic, answerBody: codeAnswer
    })
  })
  await api(`/api/v1/teacher/benefit-assignments/${code.id}/publish`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${teacher.accessToken}` },
    body: JSON.stringify({ revision: code.revision })
  })

  const username = `s12cui_${suffix}`
  await api('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, nickname: 'S12CUIStudent' })
  })
  const student = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, clientApp: 'STUDENT' })
  })
  const studentHeaders = { Authorization: `Bearer ${student.accessToken}` }
  const quote = await api('/api/v1/trade/quotes', {
    method: 'POST',
    headers: studentHeaders,
    body: JSON.stringify({ productType: 'ROUTE', productId: route.id, couponSelection: { mode: 'NONE', ids: [] } })
  })
  const order = await api('/api/v1/orders', {
    method: 'POST',
    headers: { ...studentHeaders, 'Idempotency-Key': `s12c-ui-${suffix}` },
    body: JSON.stringify({ quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: [] })
  })
  await api(`/api/v1/orders/${order.id}/payment`, {
    method: 'POST',
    headers: studentHeaders,
    body: JSON.stringify({ channel: 'MOCK' })
  })
  await api(`/api/v1/orders/${order.id}/payment/mock-pay`, { method: 'POST', headers: studentHeaders })
  let fulfilled = false
  for (let i = 0; i < 50; i += 1) {
    const state = await api(`/api/v1/orders/${order.id}`, { headers: studentHeaders })
    if (state.fulfillmentState === 'DONE') { fulfilled = true; break }
    await sleep(400)
  }
  if (!fulfilled) throw new Error('route entitlement not fulfilled')
  return {
    courseId: courses[0].id,
    routeId: route.id,
    username,
    codeBenefitId: code.id,
    adminHeaders
  }
}

async function submitChoice(cdp) {
  await waitFor(cdp, 'document.body.innerText.includes("福利选择题")', 'benefit choice page')
  await waitFor(cdp, '[...document.querySelectorAll("button")].some(button => !button.disabled && button.innerText.includes("开始作答"))', 'choice start button')
  await clickText(cdp, 'button', '开始作答')
  await waitFor(cdp, 'Boolean(document.querySelector(".question input"))', 'choice inputs')
  await evaluate(cdp, `(() => {
    const inputs=[...document.querySelectorAll('.question input')];
    inputs[0].click();
    inputs[1].click();
    return true;
  })()`)
  await clickText(cdp, 'button', '提交本次作答')
  await waitFor(cdp, 'location.pathname.startsWith("/submissions/")', 'benefit submission')
}

async function main() {
  const { courseId, routeId, username, codeBenefitId, adminHeaders } = await setup()
  const tab = await newTab(`${ADMIN_APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Page.bringToFront').catch(() => {})

  await login(cdp, ADMIN_APP, 'teacher01')
  await navigate(cdp, `${ADMIN_APP}/teacher/benefits`)
  await waitFor(cdp, 'document.body.innerText.includes("路线福利题")', 'teacher benefit page')
  await waitFor(cdp, 'document.querySelectorAll(".route-option").length > 0', 'teacher route options')
  await clickText(cdp, 'button', '新建福利题')
  await evaluate(cdp, 'document.querySelector(".route-option input").click()')
  await clickText(cdp, 'button', '填入当前类型示例')
  await waitFor(cdp, 'document.querySelectorAll(".json-grid textarea").length === 2 && document.querySelector(".json-grid textarea").value.length > 10', 'benefit sample')
  await clickText(cdp, 'button', '保存草稿')
  await waitFor(cdp, 'document.body.innerText.includes("草稿已保存")', 'benefit draft saved')
  await clickText(cdp, 'button', '保存并发布')
  await waitFor(cdp, 'document.body.innerText.includes("已发布版本 v1")', 'benefit published')
  await waitFor(cdp, 'document.body.innerText.includes("已绑定")', 'bound epoch shown')
  console.log('PASS  teacher creates and publishes a single-question benefit with bound epoch')
  const choiceBenefitId = db(`SELECT id FROM bc_assignment.assignment WHERE course_id=${courseId} AND kind='BENEFIT_CHOICE' ORDER BY id DESC LIMIT 1`)

  await login(cdp, STUDENT_APP, username)
  await navigate(cdp, `${STUDENT_APP}/routes/${routeId}`)
  await waitFor(cdp, 'document.body.innerText.includes("课程与顺序")', 'route detail')
  const clicked = await evaluate(cdp, `(() => {
    const rows=[...document.querySelectorAll('tbody tr')];
    const row=rows.find(item => item.innerText.includes('S12C_C_'));
    const link=row?.querySelector('a');
    if(!link) return false;
    link.click();
    return true;
  })()`)
  if (!clicked) throw new Error('route benefit link not found')
  await waitFor(cdp, 'document.body.innerText.includes("路线福利")', 'route benefits page')
  await waitFor(cdp, 'document.body.innerText.includes("福利选择题")', 'choice benefit listed')
  await waitFor(cdp, 'document.body.innerText.includes("福利代码题")', 'code benefit listed')
  console.log('PASS  route page lists choice and code benefit entries')

  const choiceOpened = await evaluate(cdp, `(() => {
    const cards=[...document.querySelectorAll('.benefit')];
    const card=cards.find(item => item.innerText.includes('福利选择题'));
    const button=card?.querySelector('a');
    if(!button) return false;
    button.click();
    return true;
  })()`)
  if (!choiceOpened) throw new Error('choice benefit open link missing')
  await submitChoice(cdp)
  await waitFor(cdp, 'document.body.innerText.includes("10 / 10")', 'benefit choice score')
  await waitFor(cdp, 'document.body.innerText.includes("堆和虚拟机栈")', 'benefit analysis')
  console.log('PASS  student answers choice benefit and sees score/analysis')
  const choiceSubmissionId = Number((await evaluate(cdp, 'location.pathname')).split('/').pop())

  await navigate(cdp, `${STUDENT_APP}/routes/${routeId}/benefits?courseId=${courseId}`)
  await waitFor(cdp, 'document.body.innerText.includes("当前版本已作答")', 'answered state in route benefits')
  const codeOpened = await evaluate(cdp, `(() => {
    const cards=[...document.querySelectorAll('.benefit')];
    const card=cards.find(item => item.innerText.includes('福利代码题'));
    const button=card?.querySelector('a');
    if(!button) return false;
    button.click();
    return true;
  })()`)
  if (!codeOpened) throw new Error('code benefit open link missing')
  await waitFor(cdp, 'document.body.innerText.includes("福利代码题")', 'code benefit page')
  await waitFor(cdp, '[...document.querySelectorAll("button")].some(button => !button.disabled && button.innerText.includes("开始作答"))', 'code start button')
  await clickText(cdp, 'button', '开始作答')
  await waitFor(cdp, 'Boolean(document.querySelector(".cm-content"))', 'benefit code editor')
  await evaluate(cdp, 'document.querySelector(".cm-content").focus()')
  await cdp.send('Input.insertText', { text: 'if (quantity < 0) return 0;\nreturn unit * quantity;' })
  await waitFor(cdp, 'document.body.innerText.includes("有未保存修改") || document.body.innerText.includes("草稿已保存")', 'benefit code draft')
  await clickText(cdp, 'button', '提交本次作答')
  await waitFor(cdp, 'location.pathname.startsWith("/submissions/")', 'benefit code submission')
  const codeSubmissionId = Number((await evaluate(cdp, 'location.pathname')).split('/').pop())
  await api('/api/v1/dev/assignments/mock-review', { method: 'POST', headers: adminHeaders })
  await waitFor(cdp, 'document.body.innerText.includes("已确认满足要求") || document.body.innerText.includes("部分满足要求") || document.body.innerText.includes("需要修改")', 'benefit code review', 20000)
  await waitFor(cdp, 'document.body.innerText.includes("MOCK")', 'benefit code mock label')
  await waitFor(cdp, 'document.body.innerText.includes("参考实现")', 'benefit code reference')
  console.log('PASS  student answers code benefit and sees AI feedback/reference')

  await login(cdp, ADMIN_APP, 'teacher01')
  await navigate(cdp, `${ADMIN_APP}/teacher/submissions`)
  await waitFor(cdp, 'document.body.innerText.includes("学生作答记录")', 'teacher submissions page')
  await waitFor(cdp, 'document.querySelectorAll("tbody tr").length >= 2', 'benefit submissions listed')
  await clickText(cdp, 'button', '查看')
  await waitFor(cdp, 'document.body.innerText.includes("只读作答详情")', 'teacher benefit detail')
  await waitFor(cdp, 'document.body.innerText.includes("福利代码题") || document.body.innerText.includes("福利选择题")', 'teacher benefit label')
  const retry = await evaluate(cdp, `[...document.querySelectorAll('button')].some(b => /重试|改分/.test(b.innerText))`)
  if (retry) throw new Error('teacher benefit detail exposes retry/score button')
  console.log('PASS  teacher reads benefit submissions without write controls')

  const states = db(`SELECT review_state FROM bc_assignment.submission WHERE id IN (${choiceSubmissionId},${codeSubmissionId}) ORDER BY id`)
    .split(/\r?\n/).map(value => value.trim()).join('|')
  if (states !== 'GRADED|GRADED') throw new Error(`unexpected benefit submission states: ${states}`)
  console.log('PASS  benefit choice and code submissions independently retained')

  if (cdp.exceptions.length) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log('ALL S12-C UI CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})