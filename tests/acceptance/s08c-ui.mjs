import { setTimeout as sleep } from 'node:timers/promises'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const APP = process.env.STUDENT_APP || 'http://localhost:15173'
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const USERNAME = `s08cui_${Date.now()}`
const PASSWORD = 'Passw0rd!'
const COURSE_ID = process.env.COURSE_ID || '11'

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

async function waitFor(cdp, expression, label, timeoutMs = 15000) {
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

async function click(cdp, selector) {
  const clicked = await evaluate(cdp, `(() => {
    const el=document.querySelector(${JSON.stringify(selector)});
    if(!el) return false;
    el.click();
    return true;
  })()`)
  if (!clicked) throw new Error(`element not found: ${selector}`)
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

async function login(cdp) {
  await cdp.send('Network.clearBrowserCookies').catch(() => {})
  await navigate(cdp, `${APP}/login`)
  await waitFor(cdp, 'Boolean(document.querySelector("input[autocomplete=username]"))', 'login form')
  await fill(cdp, 'input[autocomplete=username]', USERNAME)
  await fill(cdp, 'input[type=password]', PASSWORD)
  await click(cdp, 'button[type=submit]')
  await waitFor(cdp, 'location.pathname !== "/login"', 'login redirect')
}

async function main() {
  const admin = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'admin01', password: process.env.DEMO_PASSWORD || 'Demo@123456', clientApp: 'ADMIN' })
  })
  const rule = await api('/api/v1/admin/coupon-rules', {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({
      ruleKey: `S08CUI_${USERNAME}`, type: 'NO_THRESHOLD', scope: 'ALL', thresholdCent: 0,
      amountCent: 200, validityType: 'DAYS', validDays: 7
    })
  })
  const now = new Date(Date.now() - 3600000).toISOString().slice(0, 19)
  const end = new Date(Date.now() + 86400000).toISOString().slice(0, 19)
  const activity = await api('/api/v1/admin/activities', {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({ name: `S08C UI ${USERNAME}`, openAt: now, closeAt: end })
  })
  const itemView = await api(`/api/v1/admin/activities/${activity.id}/items`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({ ruleId: Number(rule.id), stockTotal: 10, perUserLimit: 1 })
  })
  await api(`/api/v1/admin/activities/${activity.id}/activate`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({ revision: itemView.revision })
  })

  await api('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username: USERNAME, password: PASSWORD, nickname: 'S08CUI' })
  })
  const student = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: USERNAME, password: PASSWORD, clientApp: 'STUDENT' })
  })
  await api(`/api/v1/activity-items/${itemView.items[0].id}/claims`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${student.accessToken}`, 'Idempotency-Key': `s08c-ui-${USERNAME}` }
  })

  const tab = await newTab(`${APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await login(cdp)

  await navigate(cdp, `${APP}/checkout?courseId=${COURSE_ID}`)
  await waitFor(cdp, 'document.body.innerText.includes("优惠券")', 'coupon panel')
  await waitFor(cdp, 'document.body.innerText.includes("已使用 1 张券")', 'recommended coupon plan')
  console.log('PASS  checkout shows recommended coupon plan')
  await clickText(cdp, 'button', '手选优惠券')
  await waitFor(cdp, 'document.querySelectorAll(".coupon-option").length > 0', 'manual coupon options')
  console.log('PASS  manual coupon selection lists available coupons')

  await clickText(cdp, 'button', '确认并创建订单')
  await waitFor(cdp, 'location.pathname.startsWith("/orders/")', 'order detail route')
  await waitFor(cdp, 'document.body.innerText.includes("优惠券明细")', 'order coupon detail')
  console.log('PASS  order detail shows coupon line')
  await waitFor(cdp, 'document.body.innerText.includes("待支付")', 'wait pay state')

  await evaluate(cdp, 'window.confirm=()=>true')
  await clickText(cdp, 'button', '取消订单')
  await waitFor(cdp, 'document.body.innerText.includes("已关闭")', 'closed state')
  console.log('PASS  cancel closes order and returns coupon')

  if (cdp.exceptions.length) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log('ALL S08-C UI CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})