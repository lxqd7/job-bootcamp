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
    const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype
      :el.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto,'value').set.call(el, ${encoded});
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

async function clickCardButton(cdp, cardSelector, cardText, buttonSelector, buttonText) {
  const clicked = await evaluate(cdp, `(() => {
    const card=[...document.querySelectorAll(${JSON.stringify(cardSelector)})]
      .find(item => item.innerText.includes(${JSON.stringify(cardText)}));
    if(!card) return false;
    const button=[...card.querySelectorAll(${JSON.stringify(buttonSelector)})]
      .find(item => !item.disabled && item.innerText.includes(${JSON.stringify(buttonText)}));
    if(!button) return false;
    button.click();
    return true;
  })()`)
  if (!clicked) throw new Error(`card action not found: ${cardText} / ${buttonText}`)
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

async function buyCourse(headers, key) {
  const quote = await api('/api/v1/trade/quotes', {
    method: 'POST',
    headers,
    body: JSON.stringify({ productType: 'COURSE', productId: COURSE_ID, couponSelection: { mode: 'NONE', ids: [] } })
  })
  const order = await api('/api/v1/orders', {
    method: 'POST',
    headers: { ...headers, 'Idempotency-Key': key },
    body: JSON.stringify({ quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: [] })
  })
  await api(`/api/v1/orders/${order.id}/payment`, {
    method: 'POST', headers, body: JSON.stringify({ channel: 'MOCK' })
  })
  await api(`/api/v1/orders/${order.id}/payment/mock-pay`, { method: 'POST', headers })
  for (let i = 0; i < 50; i += 1) {
    const state = await api(`/api/v1/orders/${order.id}`, { headers })
    if (state.fulfillmentState === 'DONE') return
    await sleep(400)
  }
  throw new Error('course entitlement not fulfilled')
}

async function setup() {
  const admin = await loginApi('admin01', 'ADMIN')
  const adminHeaders = { Authorization: `Bearer ${admin.accessToken}` }
  const teacher = await loginApi('teacher01', 'ADMIN')
  const course = await api('/api/v1/admin/courses', {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({
      title: `S13C_UI_${STAMP}`, description: 'S13C UI', categoryId: 2, priceCent: 990, isFree: false
    })
  })
  const chapter = await api(`/api/v1/admin/courses/${course.id}/chapters`, {
    method: 'POST', headers: adminHeaders, body: JSON.stringify({ title: 'Chapter', sort: 1 })
  })
  await api(`/api/v1/admin/chapters/${chapter.id}/lessons`, {
    method: 'POST', headers: adminHeaders, body: JSON.stringify({ title: 'Video', sort: 1, mediaId: 1 })
  })
  await api(`/api/v1/admin/courses/${course.id}/publish`, {
    method: 'POST', headers: adminHeaders, body: JSON.stringify({ revision: 0 })
  })
  const current = await api(`/api/v1/admin/teachers/${teacher.user.id}/assignments`, { headers: adminHeaders })
  await api(`/api/v1/admin/teachers/${teacher.user.id}/assignments`, {
    method: 'PUT',
    headers: adminHeaders,
    body: JSON.stringify({ courseIds: [course.id], routeIds: [], revision: current.revision })
  })
  COURSE_ID = course.id

  const s1 = await registerAndBuy(`s13cui1_${STAMP}`)
  const s2 = await registerAndBuy(`s13cui2_${STAMP}`)
  const s2Headers = { Authorization: `Bearer ${s2.accessToken}` }
  const question = await api(`/api/v1/courses/${course.id}/questions`, {
    method: 'POST',
    headers: s2Headers,
    body: JSON.stringify({ title: `UI_QA_${STAMP}`, body: '问题正文：接口幂等怎么设计？', anonymous: false })
  })
  const answer = await api(`/api/v1/questions/${question.id}/answers`, {
    method: 'POST', headers: s2Headers, body: JSON.stringify({ body: '回答正文：用唯一键加事务。' })
  })
  await api(`/api/v1/answers/${answer.id}/comments`, {
    method: 'POST', headers: s2Headers, body: JSON.stringify({ body: '评论正文：再补一句。' })
  })
  await api(`/api/v1/courses/${course.id}/notes`, {
    method: 'POST',
    headers: s2Headers,
    body: JSON.stringify({ body: `COLLECT_${STAMP} 公开笔记原文`, visibility: 'COURSE_PUBLIC' })
  })
  return {
    courseId: course.id,
    studentUsername: `s13cui1_${STAMP}`,
    student2Username: `s13cui2_${STAMP}`,
    student2Headers: s2Headers
  }
}

let COURSE_ID = 0

async function registerAndBuy(username) {
  await api('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, nickname: username })
  })
  const session = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, clientApp: 'STUDENT' })
  })
  await buyCourse({ Authorization: `Bearer ${session.accessToken}` }, `s13c-ui-${username}`)
  return session
}

async function main() {
  const setupData = await setup()
  const { courseId, studentUsername, student2Username, student2Headers } = setupData
  const tab = await newTab(`${STUDENT_APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Page.bringToFront').catch(() => {})

  // Student Q&A: list, thread, answer/comment likes.
  await login(cdp, STUDENT_APP, studentUsername)
  await navigate(cdp, `${STUDENT_APP}/courses/${courseId}/questions`)
  await waitFor(cdp, 'document.body.innerText.includes("课程问答")', 'question page')
  await waitFor(cdp, `document.body.innerText.includes("UI_QA_${STAMP}")`, 'question listed')
  await clickText(cdp, '.question-item', `UI_QA_${STAMP}`)
  await waitFor(cdp, 'document.body.innerText.includes("回答（1）")', 'thread answers')
  await clickText(cdp, '.answer .link-button', '赞 0')
  await waitFor(cdp, 'document.querySelector(".answer")?.innerText.includes("取消赞 1")', 'answer liked')
  console.log('PASS  student likes an answer and sees the updated count')

  await clickText(cdp, '.answer .link-button', '评论（1）')
  await waitFor(cdp, 'document.querySelector(".comments")?.innerText.includes("评论正文")', 'comments expanded')
  await clickText(cdp, '.comment .link-button', '赞 0')
  await waitFor(cdp, 'document.querySelector(".comment")?.innerText.includes("取消赞 1")', 'comment liked')
  console.log('PASS  student likes a comment and sees the updated count')

  await fill(cdp, '.ask input', `UI_ASK_${STAMP}`)
  await fill(cdp, '.ask textarea', '自己提出的问题：幂等键和唯一键有什么区别？')
  await clickText(cdp, '.ask button', '发布问题')
  await waitFor(cdp, 'document.body.innerText.includes("问题已发布")', 'question created')
  await waitFor(cdp, `document.body.innerText.includes("UI_ASK_${STAMP}")`, 'own question listed')
  console.log('PASS  student creates a question from the Q&A page')

  // Student notes: create public note, collect another public note, manage collected copy.
  await navigate(cdp, `${STUDENT_APP}/courses/${courseId}/notes`)
  await waitFor(cdp, 'document.body.innerText.includes("公开笔记")', 'notes page')
  await fill(cdp, '.create textarea', `UI_NOTE_${STAMP} 我的公开笔记`)
  await fill(cdp, '.create select', 'COURSE_PUBLIC')
  await clickText(cdp, '.create button', '保存笔记')
  await waitFor(cdp, 'document.body.innerText.includes("笔记已保存")', 'note created')
  await waitFor(cdp, `document.body.innerText.includes("UI_NOTE_${STAMP}")`, 'own note listed')
  console.log('PASS  student creates a public note from the course notes page')

  await waitFor(cdp, `document.body.innerText.includes("COLLECT_${STAMP}")`, 'collectable note listed')
  await clickCardButton(cdp, '.note', `COLLECT_${STAMP}`, 'button', '采集为私人副本')
  await waitFor(cdp, 'document.body.innerText.includes("已采集为私人副本")', 'note collected')
  console.log('PASS  student collects another public note as a private copy')

  await navigate(cdp, `${STUDENT_APP}/me/notes`)
  await waitFor(cdp, 'document.body.innerText.includes("原创与采集")', 'my notes page')
  await waitFor(cdp, `document.body.innerText.includes("UI_NOTE_${STAMP}")`, 'original note in my notes')
  await waitFor(cdp, `document.body.innerText.includes("COLLECT_${STAMP}")`, 'collected note in my notes')
  await clickCardButton(cdp, '.note', `COLLECT_${STAMP}`, 'a', '查看/编辑')
  await waitFor(cdp, 'document.body.innerText.includes("采集副本")', 'collected detail')
  await waitFor(cdp, 'document.body.innerText.includes("仅自己可见")', 'collected visibility')
  await waitFor(cdp, 'document.body.innerText.includes("采集自")', 'source snapshot')
  const collectedSelectDisabled = await evaluate(cdp, 'document.querySelector(".editor select")?.disabled === true')
  if (!collectedSelectDisabled) throw new Error('collected note visibility should be locked')
  await fill(cdp, '.editor textarea', `COLLECT_${STAMP} 私人副本已编辑`)
  await clickText(cdp, '.editor button', '保存修改')
  await waitFor(cdp, 'document.body.innerText.includes("笔记已保存")', 'collected note updated')
  console.log('PASS  collected copy stays private, shows source snapshot and can be edited')

  // Teacher workspace: answer assigned-course questions and read public notes.
  await login(cdp, ADMIN_APP, 'teacher01')
  await navigate(cdp, `${ADMIN_APP}/teacher/questions`)
  await waitFor(cdp, 'document.body.innerText.includes("负责课程答疑")', 'teacher question page')
  await waitFor(cdp, `document.body.innerText.includes("UI_ASK_${STAMP}")`, 'teacher sees question')
  await clickText(cdp, '.question-item', `UI_ASK_${STAMP}`)
  await waitFor(cdp, 'Boolean(document.querySelector(".answer-form textarea"))', 'teacher answer form')
  await fill(cdp, '.answer-form textarea', '教师回答：唯一键保证一人一条，幂等键保证请求可重放。')
  await clickText(cdp, '.answer-form button', '提交回答')
  await waitFor(cdp, 'document.body.innerText.includes("回答已提交")', 'teacher answer saved')
  await waitFor(cdp, 'document.body.innerText.includes("教师回答：唯一键保证")', 'teacher answer visible')
  const teacherBadge = await evaluate(cdp, '[...document.querySelectorAll(".teacher-badge")].some(el => el.innerText.includes("教师"))')
  if (!teacherBadge) throw new Error('teacher badge missing')
  console.log('PASS  teacher answers an assigned-course question with TEACHER badge')

  await navigate(cdp, `${ADMIN_APP}/teacher/notes`)
  await waitFor(cdp, 'document.body.innerText.includes("负责课程公开笔记")', 'teacher notes page')
  await waitFor(cdp, `document.body.innerText.includes("UI_NOTE_${STAMP}")`, 'teacher sees public note')
  await clickCardButton(cdp, '.note', `UI_NOTE_${STAMP}`, 'button', '查看全文')
  await waitFor(cdp, 'document.body.innerText.includes("只读笔记")', 'teacher note detail')
  await waitFor(cdp, `document.querySelector(".detail")?.innerText.includes("UI_NOTE_${STAMP}")`, 'teacher note body')
  const writeButton = await evaluate(cdp, `[...document.querySelectorAll(".detail button")].some(b => /保存|删除|采集/.test(b.innerText))`)
  if (writeButton) throw new Error('teacher note detail exposes write controls')
  console.log('PASS  teacher reads assigned-course public notes without write controls')

  // Student sees the teacher answer and cannot self-like it.
  await login(cdp, STUDENT_APP, studentUsername)
  await navigate(cdp, `${STUDENT_APP}/courses/${courseId}/questions`)
  await waitFor(cdp, `document.body.innerText.includes("UI_ASK_${STAMP}")`, 'student question list after teacher answer')
  await clickText(cdp, '.question-item', `UI_ASK_${STAMP}`)
  await waitFor(cdp, 'document.body.innerText.includes("教师回答：唯一键保证")', 'student sees teacher answer')
  await waitFor(cdp, 'document.querySelector(".answer")?.innerText.includes("教师")', 'student sees teacher badge')
  console.log('PASS  student sees teacher identity on the answer')

  // Cross-account visibility: second student's token must not see the first student's private collected copy.
  const myNotesForS2 = await api('/api/v1/me/notes', { headers: student2Headers })
  if (myNotesForS2.items.some(item => item.type === 'COLLECTED' && item.excerpt.includes(`COLLECT_${STAMP}`))) {
    throw new Error('collected copy leaked into another student list')
  }
  console.log('PASS  collected copy is absent from another student list')

  if (cdp.exceptions.length) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log(`ALL S13-C UI CHECKS PASSED (students ${studentUsername}/${student2Username})`)
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})