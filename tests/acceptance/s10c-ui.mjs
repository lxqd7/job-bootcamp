import { setTimeout as sleep } from 'node:timers/promises'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const STUDENT_APP = process.env.STUDENT_APP || 'http://localhost:15173'
const ADMIN_APP = process.env.ADMIN_APP || 'http://localhost:15174'
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'
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

async function setup() {
  const admin = await loginApi('admin01', 'ADMIN')
  const adminHeaders = { Authorization: `Bearer ${admin.accessToken}` }
  const teacher = await loginApi('teacher01', 'ADMIN')
  const course = await api('/api/v1/admin/courses', {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({
      title: `S10C UI ${STAMP}`, description: 'S10C', categoryId: 2, priceCent: 990, isFree: false
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
  const username = `s10cui_${STAMP}`
  await api('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, nickname: 'S10CUIStudent' })
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
    headers: { ...studentHeaders, 'Idempotency-Key': `s10c-ui-${STAMP}` },
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
    questions: [
      { id: 'q1', type: 'SINGLE', body: 'Java 常量关键字？', score: 10, options: [{ id: 'A', text: 'final' }, { id: 'B', text: 'static' }] },
      { id: 'q2', type: 'MULTIPLE', body: 'JVM 运行时区域？', score: 20, options: [{ id: 'A', text: '堆' }, { id: 'B', text: '栈' }, { id: 'C', text: '方法区' }] }
    ]
  }
  const answerBody = {
    answers: { q1: ['A'], q2: ['A', 'B', 'C'] },
    analysis: { q1: 'final 表示常量。', q2: '三者都是运行时数据区。' }
  }
  const choice = await api('/api/v1/admin/assignments', {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({ kind: 'CHAPTER_CHOICE', courseId: course.id, chapterId: chapter.id, publicBody, answerBody })
  })
  await api(`/api/v1/admin/assignments/${choice.id}/publish`, {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({ revision: choice.revision })
  })
  const codePublic = {
    scenario: '订单金额计算',
    requirements: '补全 calculate，数量为负返回 0。',
    segments: [{ text: 'public class Calc {\n' }, { slotId: 's1', initialCode: '// TODO\n' }, { text: '}\n' }]
  }
  const codeAnswer = { referenceBySlot: { s1: 'return quantity < 0 ? 0 : unit * quantity;\n' }, rubric: '负数、long、无编译错误。' }
  const code = await api('/api/v1/admin/assignments', {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({ kind: 'COURSE_CODE', courseId: course.id, chapterId: null, publicBody: codePublic, answerBody: codeAnswer })
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
  return {
    courseId: course.id,
    choiceId: choice.id,
    codeId: code.id,
    adminHeaders,
    username
  }
}

async function main() {
  const { courseId, choiceId, codeId, adminHeaders, username } = await setup()
  const tab = await newTab(`${STUDENT_APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Page.bringToFront').catch(() => {})

  await login(cdp, STUDENT_APP, username)
  await navigate(cdp, `${STUDENT_APP}/learning/courses/${courseId}`)
  await waitFor(cdp, 'document.body.innerText.includes("自测作业")', 'learning assignment entry')
  console.log('PASS  learning page lists chapter/code assignment entries')

  await clickText(cdp, '.assignment-link', '章自测')
  await waitFor(cdp, `location.pathname === "/assignments/${choiceId}"`, 'open choice assignment')
  await waitFor(cdp, 'document.body.innerText.includes("章节自测")', 'choice page loaded')
  await waitFor(cdp, '[...document.querySelectorAll("button")].some(button => button.innerText.includes("开始作答"))', 'choice start button')
  console.log('PASS  chapter choice assignment page loads')

  await clickText(cdp, 'button', '开始作答')
  await waitFor(cdp, 'Boolean(document.querySelector(".question input"))', 'choice inputs enabled')
  await evaluate(cdp, `(() => {
    const questions=[...document.querySelectorAll('.question')];
    questions[0].querySelector('input').click();
    questions[1].querySelectorAll('input').forEach(input => input.click());
    return true;
  })()`)
  await waitFor(cdp, '[...document.querySelectorAll("button")].some(button => button.innerText.includes("提交本次作答") && !button.disabled)', 'choice submit button')
  await clickText(cdp, 'button', '提交本次作答')
  await waitFor(cdp, 'location.pathname.startsWith("/submissions/")', 'choice submitted')
  await waitFor(cdp, 'document.body.innerText.includes("30 / 30")', 'choice score')
  await waitFor(cdp, 'document.body.innerText.includes("解析") && document.body.innerText.includes("正确答案")', 'analysis and correct answers')
  console.log('PASS  choice submission shows score, correct answers and analysis')

  await navigate(cdp, `${STUDENT_APP}/assignments/${codeId}`)
  await waitFor(cdp, 'document.body.innerText.includes("课程代码补全")', 'code page loaded')
  await waitFor(cdp, '[...document.querySelectorAll("button")].some(button => button.innerText.includes("开始作答"))', 'code start button')
  await clickText(cdp, 'button', '开始作答')
  await waitFor(cdp, 'Boolean(document.querySelector(".cm-content"))', 'code editor mounted')
  await evaluate(cdp, 'document.querySelector(".cm-content").focus()')
  await cdp.send('Input.insertText', { text: 'return quantity < 0 ? 0 : unit * quantity;' })
  await waitFor(cdp, 'document.body.innerText.includes("草稿已保存") || document.body.innerText.includes("有未保存修改")', 'code draft state', 8000)
  console.log('PASS  code slot editor accepts input')

  await clickText(cdp, 'button', '提交本次作答')
  await waitFor(cdp, 'location.pathname.startsWith("/submissions/")', 'code submitted')
  await waitFor(cdp, 'document.body.innerText.includes("AI 点评")', 'review section')
  await api('/api/v1/dev/assignments/mock-review', { method: 'POST', headers: adminHeaders })
  await waitFor(cdp, 'document.body.innerText.includes("已确认满足要求") || document.body.innerText.includes("需要修改") || document.body.innerText.includes("部分满足要求")', 'mock review completed', 20000)
  await waitFor(cdp, 'document.body.innerText.includes("MOCK")', 'mock label')
  await waitFor(cdp, 'document.body.innerText.includes("参考实现")', 'reference implementation')
  console.log('PASS  code submission shows mock AI review, mock label and reference')

  const studentTab = cdp
  await studentTab.send('Network.clearBrowserCookies').catch(() => {})
  await login(cdp, ADMIN_APP, 'teacher01')
  await navigate(cdp, `${ADMIN_APP}/teacher/submissions`)
  await waitFor(cdp, 'document.body.innerText.includes("学生作答记录")', 'teacher submissions page')
  await waitFor(cdp, 'document.querySelectorAll("tbody tr").length > 0', 'teacher submission rows')
  console.log('PASS  teacher page lists assigned course submissions')
  await clickText(cdp, 'button', '查看')
  await waitFor(cdp, 'document.body.innerText.includes("只读作答详情")', 'teacher detail')
  await waitFor(cdp, 'document.body.innerText.includes("学生 #")', 'teacher sees student reference')
  const forbiddenActions = await evaluate(cdp, `(() => {
    const labels=[...document.querySelectorAll('button')].map(el => el.innerText.trim());
    return labels.some(text => /重试|改分|修改答案|评分/.test(text));
  })()`)
  if (forbiddenActions) throw new Error('teacher page exposes a write/retry action')
  console.log('PASS  teacher detail is read-only with no retry or score edit action')

  if (cdp.exceptions.length) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log('ALL S10-C UI CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})