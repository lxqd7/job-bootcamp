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
  const course = await api('/api/v1/admin/courses', {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({
      title: `S11C UI ${STAMP}`, description: 'S11C', categoryId: 2, priceCent: 990, isFree: false
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
    body: JSON.stringify({ revision: course.revision })
  })
  const username = `s11cui_${STAMP}`
  await api('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, nickname: 'S11CUIStudent' })
  })
  const student = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, clientApp: 'STUDENT' })
  })
  const studentHeaders = { Authorization: `Bearer ${student.accessToken}` }
  const quote = await api('/api/v1/trade/quotes', {
    method: 'POST',
    headers: studentHeaders,
    body: JSON.stringify({ productType: 'COURSE', productId: course.id, couponSelection: { mode: 'NONE', ids: [] } })
  })
  const order = await api('/api/v1/orders', {
    method: 'POST',
    headers: { ...studentHeaders, 'Idempotency-Key': `s11c-ui-${STAMP}` },
    body: JSON.stringify({ quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: [] })
  })
  await api(`/api/v1/orders/${order.id}/payment`, {
    method: 'POST',
    headers: studentHeaders,
    body: JSON.stringify({ channel: 'MOCK' })
  })
  await api(`/api/v1/orders/${order.id}/payment/mock-pay`, { method: 'POST', headers: studentHeaders })
  let fulfilled = false
  for (let i = 0; i < 40; i += 1) {
    const state = await api(`/api/v1/orders/${order.id}`, { headers: studentHeaders })
    if (state.fulfillmentState === 'DONE') { fulfilled = true; break }
    await sleep(500)
  }
  if (!fulfilled) throw new Error('course entitlement not fulfilled')

  const publicBody = {
    scenario: '订单金额计算',
    requirements: '补全 calculate，数量为负返回 0。',
    segments: [{ text: 'public class Calc {\n' }, { slotId: 's1', initialCode: '// TODO\n' }, { text: '}\n' }]
  }
  const answerBody = {
    referenceBySlot: { s1: 'return quantity < 0 ? 0 : unit * quantity;\n' },
    rubric: '负数、long、无编译错误。'
  }
  const code = await api('/api/v1/admin/assignments', {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({ kind: 'COURSE_CODE', courseId: course.id, chapterId: null, publicBody, answerBody })
  })
  await api(`/api/v1/admin/assignments/${code.id}/publish`, {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({ revision: code.revision })
  })
  const current = await api(`/api/v1/admin/teachers/${teacher.user.id}/assignments`, { headers: adminHeaders })
  await api(`/api/v1/admin/teachers/${teacher.user.id}/assignments`, {
    method: 'PUT',
    headers: adminHeaders,
    body: JSON.stringify({ courseIds: [course.id], routeIds: [], revision: current.revision })
  })
  return { codeId: code.id, username }
}

async function submitCode(cdp, codeId) {
  await navigate(cdp, `${STUDENT_APP}/assignments/${codeId}`)
  await waitFor(cdp, 'document.body.innerText.includes("课程代码补全")', 'code page')
  await waitFor(cdp, '[...document.querySelectorAll("button")].some(button => /开始作答|再练一次/.test(button.innerText))', 'start button')
  const started = await evaluate(cdp, `(() => {
    const el=[...document.querySelectorAll('button')].find(button => !button.disabled && /开始作答|再练一次/.test(button.innerText));
    if(!el) return false;
    el.click();
    return true;
  })()`)
  if (!started) throw new Error('start button not clickable')
  await waitFor(cdp, 'Boolean(document.querySelector(".cm-content"))', 'editor')
  await evaluate(cdp, 'document.querySelector(".cm-content").focus()')
  await cdp.send('Input.insertText', { text: 'return quantity < 0 ? 0 : unit * quantity;' })
  await waitFor(cdp, 'document.body.innerText.includes("有未保存修改") || document.body.innerText.includes("草稿已保存")', 'draft state')
  await clickText(cdp, 'button', '提交本次作答')
  await waitFor(cdp, 'location.pathname.startsWith("/submissions/")', 'submission page')
  return Number((await evaluate(cdp, 'location.pathname')).split('/').pop())
}

async function expectNoProviderText(cdp, label) {
  const leaked = await evaluate(cdp, `(() => {
    const text=document.body.innerText;
    return /Qwen|DeepSeek|qwen-plus|deepseek-chat/.test(text);
  })()`)
  if (leaked) throw new Error(`${label} leaked provider/model text`)
}

async function main() {
  const { codeId, username } = await setup()
  const tab = await newTab(`${STUDENT_APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Page.bringToFront').catch(() => {})

  await login(cdp, STUDENT_APP, username)
  const successId = await submitCode(cdp, codeId)
  await waitFor(cdp, 'document.querySelectorAll(".status-track .step").length === 3', 'status track')
  await waitFor(cdp, 'document.body.innerText.includes("自动刷新")', 'polling indicator', 5000)
  console.log('PASS  submission page shows status track and automatic polling')
  await waitFor(cdp, 'document.body.innerText.includes("已确认满足要求")', 'mock review completed', 20000)
  await waitFor(cdp, 'document.body.innerText.includes("MOCK")', 'mock label')
  await waitFor(cdp, 'document.body.innerText.includes("点评完成")', 'completed status')
  await expectNoProviderText(cdp, 'student success page')
  console.log('PASS  polling updates to completed mock review without provider/model text')

  const failureId = await submitCode(cdp, codeId)
  await sleep(1200)
  const taskId = db(`SELECT id FROM bc_assignment.ai_task WHERE submission_id=${failureId}`)
  if (!taskId) throw new Error('failure task not found')
  db(`UPDATE bc_assignment.ai_task SET state='FAILED', safe_error_code='AI_UNAVAILABLE', lease_token=NULL, lease_until=NULL WHERE id=${taskId}`)
  db(`UPDATE bc_assignment.submission SET review_state='FAILED' WHERE id=${failureId}`)
  db(`UPDATE bc_assignment.ai_user_slot SET active_task_id=NULL WHERE active_task_id=${taskId}`)
  await waitFor(cdp, 'document.body.innerText.includes("点评失败")', 'failed state via polling', 15000)
  await waitFor(cdp, 'document.body.innerText.includes("点评服务暂时不可用")', 'safe failure text')
  await waitFor(cdp, 'document.body.innerText.includes("没有单独的重试按钮")', 'no retry explanation')
  const retryButton = await evaluate(cdp, `[...document.querySelectorAll('button')].some(b => /重试|选择模型/.test(b.innerText))`)
  if (retryButton) throw new Error('student page exposes retry/model button')
  await expectNoProviderText(cdp, 'student failure page')
  console.log('PASS  failed review shows safe category, no retry or model selection')

  await login(cdp, ADMIN_APP, 'teacher01')
  await navigate(cdp, `${ADMIN_APP}/teacher/submissions`)
  await waitFor(cdp, 'document.body.innerText.includes("学生作答记录")', 'teacher page')
  await waitFor(cdp, 'document.querySelectorAll("tbody tr").length > 0', 'teacher rows')
  await clickText(cdp, 'button', '查看')
  await waitFor(cdp, 'document.body.innerText.includes("只读作答详情")', 'teacher detail')
  await waitFor(cdp, 'document.body.innerText.includes("点评失败")', 'teacher failed feedback')
  await waitFor(cdp, 'document.body.innerText.includes("教师只读")', 'read-only label')
  const teacherRetry = await evaluate(cdp, `[...document.querySelectorAll('button')].some(b => /重试|改分/.test(b.innerText))`)
  if (teacherRetry) throw new Error('teacher page exposes retry/score button')
  await expectNoProviderText(cdp, 'teacher detail')
  console.log('PASS  teacher sees read-only failed feedback without retry/model controls')

  const status = db(`SELECT review_state FROM bc_assignment.submission WHERE id IN (${successId},${failureId}) ORDER BY id`)
    .split(/\r?\n/).map(value => value.trim()).join('|')
  if (status !== 'GRADED|FAILED') throw new Error(`unexpected submission states: ${status}`)
  console.log('PASS  successful and failed submissions remain independent')

  if (cdp.exceptions.length) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log('ALL S11-C UI CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})