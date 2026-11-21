import { setTimeout as sleep } from 'node:timers/promises'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const APP = process.env.STUDENT_APP || 'http://localhost:15173'
const ADMIN_APP = process.env.ADMIN_APP || 'http://localhost:15174'
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
const STUDENT_USER = `s08ui_${Date.now()}`
const STUDENT_PASSWORD = 'Passw0rd!'

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
        if (message.error) pending.reject(new Error(message.error.message))
        else pending.resolve(message.result)
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
  await evaluate(cdp, `(() => {
    const el=document.querySelector(${JSON.stringify(selector)});
    if(!el) return false;
    el.value=${encoded};
    el.dispatchEvent(new Event('input',{bubbles:true}));
    el.dispatchEvent(new Event('change',{bubbles:true}));
    return true;
  })()`)
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

async function login(cdp, app, username, password) {
  await cdp.send('Network.clearBrowserCookies').catch(() => {})
  await navigate(cdp, `${app}/login`)
  await waitFor(cdp, 'Boolean(document.querySelector("input[autocomplete=username]"))', 'login form')
  await fill(cdp, 'input[autocomplete=username]', username)
  await fill(cdp, 'input[type=password]', password)
  await click(cdp, 'button[type=submit]')
  await waitFor(cdp, 'location.pathname !== "/login"', 'login redirect')
}

async function main() {
  const register = await fetch(`${API_BASE}/api/v1/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: STUDENT_USER, password: STUDENT_PASSWORD, nickname: 'S08UI' })
  })
  if (!register.ok) throw new Error(`register failed ${register.status}`)

  const studentTab = await newTab(`${APP}/login`)
  const adminTab = await newTab(`${ADMIN_APP}/login`)
  const student = new Cdp(studentTab.webSocketDebuggerUrl)
  const admin = new Cdp(adminTab.webSocketDebuggerUrl)
  for (const cdp of [student, admin]) {
    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Network.enable')
  }

  await login(student, APP, STUDENT_USER, STUDENT_PASSWORD)
  await navigate(student, `${APP}/activities`)
  await waitFor(student, 'document.body.innerText.includes("领券活动")', 'activities page')
  await waitFor(student, 'document.body.innerText.includes("剩余")', 'activity stock')
  console.log('PASS  student activities page lists claimable coupons')
  await clickText(student, 'button', '领取')
  await waitFor(student, 'document.body.innerText.includes("领券成功")', 'claim success')
  console.log('PASS  student claims activity coupon')
  await navigate(student, `${APP}/me/coupons`)
  await waitFor(student, 'document.body.innerText.includes("我的券")', 'my coupons page')
  await waitFor(student, 'document.body.innerText.includes("可用") || document.body.innerText.includes("未生效")', 'coupon status')
  console.log('PASS  my coupons lists issued coupon')

  await login(admin, ADMIN_APP, 'admin01', process.env.DEMO_PASSWORD || 'Demo@123456')
  await navigate(admin, `${ADMIN_APP}/admin/activities`)
  await waitFor(admin, 'document.body.innerText.includes("优惠活动与券规则")', 'admin activity page')
  await waitFor(admin, 'document.body.innerText.includes("活动与券项")', 'admin item section')
  console.log('PASS  admin activity configuration page loads')

  if (student.exceptions.length || admin.exceptions.length) {
    throw new Error(`runtime exceptions: ${[...student.exceptions, ...admin.exceptions].join('; ')}`)
  }
  if (student.failedResponses.length || admin.failedResponses.length) {
    throw new Error(`5xx responses: ${[...student.failedResponses, ...admin.failedResponses].join('; ')}`)
  }
  console.log('PASS  no runtime exception or 5xx response')
  student.close()
  admin.close()
  console.log('ALL S08-A UI CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})