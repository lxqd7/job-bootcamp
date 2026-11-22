import { setTimeout as sleep } from 'node:timers/promises'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const APP = process.env.STUDENT_APP || 'http://localhost:15173'
const ADMIN_APP = process.env.ADMIN_APP || 'http://localhost:15174'
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const USERNAME = `s09cui_${Date.now()}`
const PASSWORD = 'Passw0rd!'

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

async function api(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  })
  const body = await response.json()
  if (!response.ok) throw new Error(`${path} ${response.status} ${JSON.stringify(body)}`)
  return body.data
}

async function login(cdp, app, username, password) {
  await cdp.send('Network.clearBrowserCookies').catch(() => {})
  await navigate(cdp, `${app}/login`)
  await waitFor(cdp, 'Boolean(document.querySelector("input[autocomplete=username]"))', 'login form')
  await fill(cdp, 'input[autocomplete=username]', username)
  await fill(cdp, 'input[type=password]', password)
  await evaluate(cdp, 'document.querySelector("button[type=submit]").click()')
  await waitFor(cdp, 'location.pathname !== "/login"', 'login redirect')
}

async function createCourse(admin) {
  const course = await api('/api/v1/admin/courses', {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({
      title: `S09C UI ${USERNAME}`, description: 'S09C', categoryId: 2,
      priceCent: 990, isFree: false
    })
  })
  const chapter = await api(`/api/v1/admin/courses/${course.id}/chapters`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({ title: 'Chapter', sort: 1 })
  })
  for (let index = 1; index <= 10; index++) {
    await api(`/api/v1/admin/chapters/${chapter.id}/lessons`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${admin.accessToken}` },
      body: JSON.stringify({ title: `Video ${index}`, sort: index, mediaId: 1 })
    })
  }
  await api(`/api/v1/admin/courses/${course.id}/publish`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({ revision: course.revision })
  })
  return course.id
}

async function buyCourse(student, courseId) {
  const quote = await api('/api/v1/trade/quotes', {
    method: 'POST',
    headers: { Authorization: `Bearer ${student.accessToken}` },
    body: JSON.stringify({ productType: 'COURSE', productId: Number(courseId), couponSelection: { mode: 'NONE', ids: [] } })
  })
  const order = await api('/api/v1/orders', {
    method: 'POST',
    headers: { Authorization: `Bearer ${student.accessToken}`, 'Idempotency-Key': `s09c-ui-${USERNAME}` },
    body: JSON.stringify({ quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: [] })
  })
  await api(`/api/v1/orders/${order.id}/payment`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${student.accessToken}` },
    body: JSON.stringify({ channel: 'MOCK' })
  })
  await api(`/api/v1/orders/${order.id}/payment/mock-pay`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${student.accessToken}` }
  })
  for (let attempt = 0; attempt < 40; attempt++) {
    const latest = await api(`/api/v1/orders/${order.id}`, {
      headers: { Authorization: `Bearer ${student.accessToken}` }
    })
    if (latest.fulfillmentState === 'DONE') return order.id
    await sleep(500)
  }
  throw new Error('order fulfillment timeout')
}

async function main() {
  const admin = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'admin01', password: process.env.DEMO_PASSWORD || 'Demo@123456', clientApp: 'ADMIN' })
  })
  const courseId = await createCourse(admin)
  await api('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username: USERNAME, password: PASSWORD, nickname: 'S09CUI' })
  })
  const student = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: USERNAME, password: PASSWORD, clientApp: 'STUDENT' })
  })
  const orderId = await buyCourse(student, courseId)
  await api(`/api/v1/dev/refunds/${orderId}/scenario`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({ outcome: 'UNKNOWN' })
  })

  const studentTab = await newTab(`${APP}/login`)
  const adminTab = await newTab(`${ADMIN_APP}/login`)
  const studentCdp = new Cdp(studentTab.webSocketDebuggerUrl)
  const adminCdp = new Cdp(adminTab.webSocketDebuggerUrl)
  for (const cdp of [studentCdp, adminCdp]) {
    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Network.enable')
  }

  await login(studentCdp, APP, USERNAME, PASSWORD)
  await navigate(studentCdp, `${APP}/orders/${orderId}`)
  await waitFor(studentCdp, 'document.body.innerText.includes("申请退款")', 'order refund entry')
  await clickText(studentCdp, 'a', '申请退款')
  await waitFor(studentCdp, 'document.body.innerText.includes("受理规则")', 'refund apply page')
  console.log('PASS  order detail opens refund application page')
  await fill(studentCdp, 'textarea', 'S09C UI refund')
  await clickText(studentCdp, 'button', '提交退款申请')
  await waitFor(studentCdp, 'location.pathname.startsWith("/refund-applications/")', 'refund status route')
  await waitFor(studentCdp, 'document.body.innerText.includes("退款处理中")', 'refund status page')
  console.log('PASS  student submits refund and sees processing status')

  await login(adminCdp, ADMIN_APP, 'admin01', process.env.DEMO_PASSWORD || 'Demo@123456')
  await navigate(adminCdp, `${ADMIN_APP}/admin/refunds`)
  await waitFor(adminCdp, 'document.body.innerText.includes("退款异常与重试")', 'admin refund page')
  await waitFor(adminCdp, `document.body.innerText.includes(${JSON.stringify(String(orderId))})`, 'admin refund row')
  console.log('PASS  admin refund list shows abnormal refund')
  await api(`/api/v1/dev/refunds/${orderId}/scenario`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({ outcome: 'COMPLETED' })
  })
  const clicked = await evaluate(adminCdp, `(() => {
    const row=[...document.querySelectorAll('tbody tr')].find(tr => tr.innerText.includes(${JSON.stringify(String(orderId))}));
    const button=row?.querySelector('button');
    if(!button) return false;
    button.click();
    return true;
  })()`)
  if (!clicked) throw new Error('admin reconcile button not found')
  await waitFor(adminCdp, 'document.body.innerText.includes("已触发对账")', 'admin reconcile notice')
  console.log('PASS  admin reconciles abnormal refund')

  await waitFor(studentCdp, 'document.body.innerText.includes("退款成功")', 'student completed refund')
  console.log('PASS  student sees refund completed after polling')
  await waitFor(studentCdp, 'document.body.innerText.includes("退款单")', 'student refund detail card')
  console.log('PASS  student refund page shows refund amount and state')

  if (studentCdp.exceptions.length || adminCdp.exceptions.length) {
    throw new Error(`runtime exceptions: ${[...studentCdp.exceptions, ...adminCdp.exceptions].join('; ')}`)
  }
  if (studentCdp.failedResponses.length || adminCdp.failedResponses.length) {
    throw new Error(`5xx responses: ${[...studentCdp.failedResponses, ...adminCdp.failedResponses].join('; ')}`)
  }
  console.log('PASS  no runtime exception or 5xx response')
  studentCdp.close()
  adminCdp.close()
  console.log('ALL S09-C UI CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})