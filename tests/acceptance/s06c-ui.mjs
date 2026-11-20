import { setTimeout as sleep } from 'node:timers/promises'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const STUDENT_APP = process.env.STUDENT_APP || 'http://localhost:15173'
const ADMIN_APP = process.env.ADMIN_APP || 'http://localhost:15174'
const USERNAME = process.env.DEMO_USER || 'student01'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'
const COURSE_ID = process.env.TRADE_COURSE_ID || '11'
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:18080'

async function apiJson(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, options)
  const body = await response.json()
  if (!response.ok || body.code !== 'OK') {
    throw new Error(`${path} failed: ${response.status} ${body.message || ''}`)
  }
  return body.data
}

async function createPendingOrder() {
  const stamp = Date.now()
  const username = `ui_trade_${stamp}`
  await apiJson('/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: 'Passw0rd!', nickname: 'UITrade' })
  })
  const login = await apiJson('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: 'Passw0rd!', clientApp: 'STUDENT' })
  })
  const auth = { Authorization: `Bearer ${login.accessToken}`, 'Content-Type': 'application/json' }
  const quote = await apiJson('/api/v1/trade/quotes', {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ productType: 'COURSE', productId: Number(COURSE_ID), couponSelection: { mode: 'NONE', ids: [] } })
  })
  const order = await apiJson('/api/v1/orders', {
    method: 'POST',
    headers: { ...auth, 'Idempotency-Key': `ui-${stamp}` },
    body: JSON.stringify({ quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: [] })
  })
  await apiJson(`/api/v1/orders/${order.id}/payment`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ channel: 'MOCK' })
  })
  return { token: login.accessToken, orderId: order.id, orderNo: order.orderNo }
}

async function createUiStudent() {
  const username = `ui_student_${Date.now()}`
  await apiJson('/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: 'Passw0rd!', nickname: 'UIStudent' })
  })
  return { username, password: 'Passw0rd!' }
}

class Cdp {
  constructor(wsUrl) {
    this.ws = new WebSocket(wsUrl)
    this.nextId = 1
    this.pending = new Map()
    this.listeners = new Map()
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
        if (message.error) pending.reject(new Error(message.error.message))
        else pending.resolve(message.result)
      } else if (message.method) {
        for (const listener of this.listeners.get(message.method) || []) listener(message.params)
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

  on(method, listener) {
    if (!this.listeners.has(method)) this.listeners.set(method, [])
    this.listeners.get(method).push(listener)
  }

  close() { this.ws.close() }
}

async function newTab(url) {
  const response = await fetch(`${CDP_BASE}/json/new?${encodeURIComponent(url)}`, { method: 'PUT' })
  if (!response.ok) throw new Error(`create tab failed: ${response.status}`)
  return response.json()
}

async function evaluate(cdp, expression) {
  const result = await cdp.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text || 'evaluate failed')
  }
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
  await evaluate(cdp, `(() => {
    const el = document.querySelector(${JSON.stringify(selector)});
    if (!el) return false;
    el.value = ${encoded};
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    return true;
  })()`)
}

async function click(cdp, selector) {
  const clicked = await evaluate(cdp, `(() => {
    const el = document.querySelector(${JSON.stringify(selector)});
    if (!el) return false;
    el.click();
    return true;
  })()`)
  if (!clicked) throw new Error(`element not found: ${selector}`)
}

async function login(cdp, app, username, password, label) {
  await cdp.send('Network.clearBrowserCookies').catch(() => {})
  await navigate(cdp, `${app}/login`)
  await waitFor(cdp, 'Boolean(document.querySelector("input[autocomplete=username]"))', `${label} login form`)
  await fill(cdp, 'input[autocomplete=username]', username)
  await fill(cdp, 'input[type=password]', password)
  await click(cdp, 'button[type=submit]')
  await waitFor(cdp, 'location.pathname !== "/login"', `${label} login redirect`)
  console.log(`PASS  ${label} login`)
}

async function main() {
  const studentTab = await newTab(`${STUDENT_APP}/login`)
  const adminTab = await newTab(`${ADMIN_APP}/login`)
  const student = new Cdp(studentTab.webSocketDebuggerUrl)
  const admin = new Cdp(adminTab.webSocketDebuggerUrl)

  for (const cdp of [student, admin]) {
    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Network.enable')
    cdp.on('Runtime.exceptionThrown', params => {
      cdp.exceptions.push(params.exceptionDetails?.text || 'unknown exception')
    })
    cdp.on('Network.responseReceived', params => {
      if (params.response.status >= 500) {
        cdp.failedResponses.push(`${params.response.url} (${params.response.status})`)
      }
    })
  }

  const studentCredentials = process.env.DEMO_USER
    ? { username: USERNAME, password: PASSWORD }
    : await createUiStudent()
  await login(student, STUDENT_APP, studentCredentials.username, studentCredentials.password, 'student01')
  await navigate(student, `${STUDENT_APP}/courses/${COURSE_ID}`)
  await waitFor(student, 'document.body.innerText.includes("立即购买")', 'course purchase entry')
  console.log('PASS  paid course shows purchase entry')

  await click(student, 'a[href*="/checkout"]')
  await waitFor(student, 'location.pathname === "/checkout"', 'checkout route')
  await waitFor(student, 'document.body.innerText.includes("应付")', 'checkout quote')
  console.log('PASS  checkout quote loads')

  await click(student, 'button.btn-primary')
  await waitFor(student, '/^\\/orders\\/\\d+$/.test(location.pathname)', 'order detail route', 20000)
  const orderPath = await evaluate(student, 'location.pathname')
  console.log(`PASS  order created ${orderPath}`)

  await waitFor(student, 'document.body.innerText.includes("去支付")', 'pay action')
  await click(student, 'a.btn-primary')
  await waitFor(student, '/^\\/orders\\/\\d+\\/pay$/.test(location.pathname)', 'payment route')
  await waitFor(student, 'document.body.innerText.includes("模拟支付成功")', 'mock pay button')
  console.log('PASS  payment page shows explicit MOCK action')

  await click(student, 'button.btn-primary')
  await waitFor(student, 'document.body.innerText.includes("已开通") || document.body.innerText.includes("去学习") || document.body.innerText.includes("开通中")', 'payment result', 30000)
  const paymentText = await evaluate(student, 'document.body.innerText')
  if (!/已支付|已开通|开通中/.test(paymentText)) {
    throw new Error('payment page did not show paid/open state')
  }
  console.log('PASS  mock payment reaches paid/open state')

  const detailLink = await evaluate(student, `(() => {
    const links = [...document.querySelectorAll('a')];
    const link = links.find(item => item.textContent.includes('查看订单详情')) || links.find(item => item.textContent.includes('去学习'));
    return link ? link.getAttribute('href') : '';
  })()`)
  if (detailLink) {
    await navigate(student, `${STUDENT_APP}${detailLink}`)
    await waitFor(student, 'document.body.innerText.includes("订单详情")', 'order detail after pay')
    await waitFor(student, 'document.body.innerText.includes("已开通") || document.body.innerText.includes("去学习")', 'fulfillment display', 30000)
    console.log('PASS  order detail shows fulfillment')
  }

  const orderHeading = await evaluate(student, 'document.querySelector("h1")?.innerText || ""')
  const orderNo = orderHeading.replace(/^订单\s*/, '').trim()
  await navigate(student, `${STUDENT_APP}/orders`)
  await waitFor(student, 'document.body.innerText.includes("我的订单")', 'order list page')
  if (orderNo) {
    await waitFor(student, `document.body.innerText.includes(${JSON.stringify(orderNo)})`, 'order list contains latest order')
    console.log('PASS  order list shows latest order')
  }

  const pending = await createPendingOrder()
  await login(admin, ADMIN_APP, 'admin01', PASSWORD, 'admin01')
  await navigate(admin, `${ADMIN_APP}/admin/trades`)
  await waitFor(admin, 'document.body.innerText.includes("订单与模拟支付")', 'admin trades page')
  await waitFor(admin, 'document.querySelectorAll("tbody tr").length > 0', 'admin order rows', 15000)
  const rowCount = await evaluate(admin, 'document.querySelectorAll("tbody tr").length')
  console.log(`PASS  admin trades page lists ${rowCount} orders`)
  const hasMockLabel = await evaluate(admin, 'document.body.innerText.includes("MOCK 支付场景仅用于演示")')
  if (!hasMockLabel) throw new Error('admin page missing MOCK warning')
  console.log('PASS  admin page labels MOCK scenario')

  await evaluate(admin, `(() => {
    const row = [...document.querySelectorAll('tbody tr')].find(item => item.innerText.includes(${JSON.stringify(pending.orderNo)}));
    if (!row) return false;
    row.click();
    return true;
  })()`)
  await evaluate(admin, 'window.confirm = () => true')
  await click(admin, 'button.btn-primary')
  await waitFor(admin, 'document.querySelector("pre.result")?.innerText.includes("\\"orderState\\": \\"PAID\\"")', 'admin scenario result', 15000)
  const paidOrder = await apiJson(`/api/v1/orders/${pending.orderId}`, {
    headers: { Authorization: `Bearer ${pending.token}` }
  })
  if (paidOrder.state !== 'PAID') {
    throw new Error(`admin scenario did not pay order: ${paidOrder.state}`)
  }
  console.log('PASS  admin MOCK scenario pays pending order')

  for (const [name, cdp] of [['student', student], ['admin', admin]]) {
    if (cdp.exceptions.length > 0) {
      throw new Error(`${name} page exceptions: ${cdp.exceptions.join('; ')}`)
    }
    if (cdp.failedResponses.length > 0) {
      throw new Error(`${name} page 5xx responses: ${cdp.failedResponses.join('; ')}`)
    }
    console.log(`PASS  ${name} page has no runtime exception or 5xx response`)
  }

  student.close()
  admin.close()
  console.log('ALL S06-C UI CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})