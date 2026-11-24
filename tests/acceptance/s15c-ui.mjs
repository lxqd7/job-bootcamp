import { setTimeout as sleep } from 'node:timers/promises'
import { execFileSync } from 'node:child_process'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const STUDENT_APP = process.env.STUDENT_APP || 'http://localhost:15173'
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

async function clickCardButton(cdp, cardSelector, cardText, buttonText) {
  const clicked = await evaluate(cdp, `(() => {
    const card=[...document.querySelectorAll(${JSON.stringify(cardSelector)})]
      .find(item => item.innerText.includes(${JSON.stringify(cardText)}));
    if(!card) return false;
    const button=[...card.querySelectorAll('button')]
      .find(item => !item.disabled && item.innerText.includes(${JSON.stringify(buttonText)}));
    if(!button) return false;
    button.click();
    return true;
  })()`)
  if (!clicked) throw new Error(`card button not found: ${cardText} / ${buttonText}`)
}

async function login(cdp, username) {
  await cdp.send('Network.clearBrowserCookies').catch(() => {})
  await navigate(cdp, `${STUDENT_APP}/login`)
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

async function loginApi(username, app) {
  return api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, clientApp: app })
  })
}

function db(sql) {
  return execFileSync(MYSQL, [
    '--default-character-set=utf8mb4', '-h127.0.0.1', '-uroot', '-N', '-B', '-e', sql
  ], { env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD || '1234' }, encoding: 'utf8' }).trim()
}

function localIso(offsetMs) {
  return new Date(Date.now() + offsetMs).toISOString().slice(0, 19)
}

async function setup() {
  const admin = await loginApi('admin01', 'ADMIN')
  const adminHeaders = { Authorization: `Bearer ${admin.accessToken}` }
  const rule = await api('/api/v1/admin/coupon-rules', {
    method: 'POST', headers: adminHeaders,
    body: JSON.stringify({
      ruleKey: `S15C_UI_${STAMP}`, type: 'NO_THRESHOLD', scope: 'ALL',
      amountCent: 500, validityType: 'DAYS', validDays: 7
    })
  })
  const openAt = localIso(-3600000)
  const closeAt = localIso(604800000)
  const project = await api('/api/v1/admin/exchange-projects', {
    method: 'POST', headers: adminHeaders,
    body: JSON.stringify({
      name: `S15C UI 兑换 ${STAMP}`, ruleId: rule.id, pointsPrice: 40,
      stockTotal: 5, perUserLimit: 2, openAt, closeAt
    })
  })
  const online = await api(`/api/v1/admin/exchange-projects/${project.id}/online`, {
    method: 'POST', headers: adminHeaders, body: JSON.stringify({ revision: 0 })
  })
  const username = `s15cui_${STAMP}`
  await api('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, nickname: 'S15CUIStudent' })
  })
  const student = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, clientApp: 'STUDENT' })
  })
  db(`INSERT INTO bc_marketing.points_account(user_id,available,debt,revision,created_at,updated_at) VALUES(${student.user.id},300,0,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE available=300,debt=0`)
  return {
    projectId: project.id,
    projectName: project.name,
    projectRevision: online.revision,
    ruleId: rule.id,
    openAt,
    closeAt,
    username,
    adminToken: admin.accessToken
  }
}

async function main() {
  const setupData = await setup()
  const { projectId, projectName, projectRevision, ruleId, openAt, closeAt, username, adminToken } = setupData
  const tab = await newTab(`${STUDENT_APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Page.bringToFront').catch(() => {})

  await login(cdp, username)
  await navigate(cdp, `${STUDENT_APP}/exchange`)
  await waitFor(cdp, 'document.body.innerText.includes("用积分换券")', 'exchange page')
  await waitFor(cdp, `document.querySelector('.project')?.innerText.includes(${JSON.stringify(projectName)})`, 'project card')
  await waitFor(cdp, 'document.body.innerText.includes("300")', 'points balance')
  console.log('PASS  student sees exchange project, rule and points balance')

  await clickCardButton(cdp, '.project', projectName, '选择兑换')
  await waitFor(cdp, 'document.body.innerText.includes("确认扣分并兑换")', 'confirm panel')
  await waitFor(cdp, 'document.body.innerText.includes("消耗积分") && document.body.innerText.includes("40 分")', 'deduction summary')
  await clickText(cdp, '.confirm button', '确认扣分并兑换')
  await waitFor(cdp, 'document.body.innerText.includes("兑换完成")', 'exchange success')
  await waitFor(cdp, 'document.body.innerText.includes("消耗 40 分，剩余 260 分")', 'result balance')
  console.log('PASS  confirmation shows deduction and executes exchange')

  await navigate(cdp, `${STUDENT_APP}/me/exchanges`)
  await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(projectName)})`, 'exchange record project')
  await waitFor(cdp, 'document.body.innerText.includes("-40 分")', 'exchange record points')
  await waitFor(cdp, 'document.body.innerText.includes("项目版本 v1")', 'project revision snapshot')
  console.log('PASS  exchange record shows project, points and revision')

  await navigate(cdp, `${STUDENT_APP}/me/coupons`)
  await waitFor(cdp, 'document.body.innerText.includes("积分兑换")', 'coupon source type')
  await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(projectName)})`, 'coupon source name')
  console.log('PASS  my coupons shows exchange source and project name')

  // Configuration changes between selection and confirmation must force reconfirmation.
  await navigate(cdp, `${STUDENT_APP}/exchange`)
  await waitFor(cdp, `document.querySelector('.project')?.innerText.includes(${JSON.stringify(projectName)})`, 'project after first exchange')
  await clickCardButton(cdp, '.project', projectName, '选择兑换')
  await waitFor(cdp, 'document.body.innerText.includes("确认扣分并兑换")', 'second confirm panel')
  await api(`/api/v1/admin/exchange-projects/${projectId}`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${adminToken}` },
    body: JSON.stringify({
      name: projectName, ruleId, pointsPrice: 50, stockTotal: 5, perUserLimit: 2,
      openAt, closeAt, revision: projectRevision
    })
  })
  await clickText(cdp, '.confirm button', '确认扣分并兑换')
  await waitFor(cdp, 'document.querySelector(".form-error")?.innerText.includes("兑换配置已变化")', 'stale config rejected')
  await waitFor(cdp, `[...document.querySelectorAll('.project')].some(p => p.innerText.includes(${JSON.stringify(projectName)}) && p.innerText.includes("50 分"))`, 'catalog refreshed with new price')
  console.log('PASS  config change forces reconfirmation and refreshes price')

  await navigate(cdp, `${STUDENT_APP}/exchange`)
  await waitFor(cdp, `[...document.querySelectorAll('.project')].some(p => p.innerText.includes(${JSON.stringify(projectName)}) && p.innerText.includes("50 分"))`, 'new price after reload')
  await clickCardButton(cdp, '.project', projectName, '选择兑换')
  await waitFor(cdp, 'document.body.innerText.includes("确认扣分并兑换")', 'third confirm panel')
  await clickText(cdp, '.confirm button', '确认扣分并兑换')
  await waitFor(cdp, 'document.body.innerText.includes("消耗 50 分，剩余 210 分")', 'exchange at new price')
  console.log('PASS  student reconfirms at updated price and deducts 50')

  if (cdp.exceptions.length) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log(`ALL S15-C UI CHECKS PASSED (project ${projectName})`)
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})