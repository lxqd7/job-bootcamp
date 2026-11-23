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

function db(sql) {
  return execFileSync(MYSQL, [
    '--default-character-set=utf8mb4', '-h127.0.0.1', '-uroot', '-N', '-B', '-e', sql
  ], { env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD || '1234' }, encoding: 'utf8' }).trim()
}

async function waitDb(sql, expected, label, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs
  let actual = ''
  while (Date.now() < deadline) {
    actual = db(sql)
    if (actual === expected) return
    await sleep(400)
  }
  throw new Error(`timeout waiting for ${label}: expected ${expected}, got ${actual}`)
}

async function setup() {
  const admin = await loginApi('admin01', 'ADMIN')
  const adminHeaders = { Authorization: `Bearer ${admin.accessToken}` }
  const teacher = await loginApi('teacher01', 'ADMIN')
  const teacherHeaders = { Authorization: `Bearer ${teacher.accessToken}` }
  const course = await api('/api/v1/admin/courses', {
    method: 'POST',
    headers: adminHeaders,
    body: JSON.stringify({
      title: `S14C_UI_${STAMP}`, description: 'S14C UI', categoryId: 2, priceCent: 990, isFree: false
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

  const username = `s14cui_${STAMP}`
  await api('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, nickname: 'S14CUIStudent' })
  })
  const student = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, clientApp: 'STUDENT' })
  })
  const studentHeaders = { Authorization: `Bearer ${student.accessToken}` }
  const quote = await api('/api/v1/trade/quotes', {
    method: 'POST', headers: studentHeaders,
    body: JSON.stringify({ productType: 'COURSE', productId: course.id, couponSelection: { mode: 'NONE', ids: [] } })
  })
  const order = await api('/api/v1/orders', {
    method: 'POST',
    headers: { ...studentHeaders, 'Idempotency-Key': `s14c-ui-${STAMP}` },
    body: JSON.stringify({ quoteId: quote.quoteId, quoteHash: quote.quoteHash, selectedCouponIds: [] })
  })
  await api(`/api/v1/orders/${order.id}/payment`, {
    method: 'POST', headers: studentHeaders, body: JSON.stringify({ channel: 'MOCK' })
  })
  await api(`/api/v1/orders/${order.id}/payment/mock-pay`, { method: 'POST', headers: studentHeaders })
  for (let i = 0; i < 50; i += 1) {
    const state = await api(`/api/v1/orders/${order.id}`, { headers: studentHeaders })
    if (state.fulfillmentState === 'DONE') break
    await sleep(400)
    if (i === 49) throw new Error('course entitlement not fulfilled')
  }
  const noteBody = `S14C_UI_NOTE_${STAMP} 公开笔记`
  const note = await api(`/api/v1/courses/${course.id}/notes`, {
    method: 'POST', headers: studentHeaders,
    body: JSON.stringify({ body: noteBody, visibility: 'COURSE_PUBLIC' })
  })
  return {
    courseId: course.id,
    noteId: note.id,
    noteBody,
    username,
    studentId: student.user.id,
    adminToken: admin.accessToken,
    teacherToken: teacher.accessToken,
    studentToken: student.accessToken
  }
}

async function requestStatus(path, token) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  return response.status
}

async function main() {
  const setupData = await setup()
  const { courseId, noteBody, username, studentId } = setupData
  const tab = await newTab(`${STUDENT_APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Page.bringToFront').catch(() => {})

  // Student before evaluation: no excellent badge.
  await login(cdp, STUDENT_APP, username)
  await navigate(cdp, `${STUDENT_APP}/courses/${courseId}/notes`)
  await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(noteBody)})`, 'student note list')
  const beforeBadge = await evaluate(cdp, `[...document.querySelectorAll('.note')].some(n => n.innerText.includes(${JSON.stringify(noteBody)}) && n.querySelector('.tag.excellent'))`)
  if (beforeBadge) throw new Error('excellent badge shown before teacher evaluation')
  console.log('PASS  student sees note without excellent badge before evaluation')

  // Teacher evaluates from the workbench.
  await login(cdp, ADMIN_APP, 'teacher01')
  await navigate(cdp, `${ADMIN_APP}/teacher/notes`)
  await waitFor(cdp, 'document.body.innerText.includes("负责课程公开笔记")', 'teacher notes page')
  await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(noteBody)})`, 'teacher note listed')
  await clickCardButton(cdp, '.note', noteBody, 'button', '查看全文')
  await waitFor(cdp, 'Boolean(document.querySelector(".evaluate-actions button"))', 'evaluate action')
  await clickText(cdp, '.evaluate-actions button', '评选为优秀')
  await waitFor(cdp, 'document.body.innerText.includes("已评选为优秀笔记")', 'teacher evaluation saved')
  await waitFor(cdp, 'document.querySelector(".detail")?.innerText.includes("获评版本 v0")', 'locked excellent revision')
  await clickText(cdp, '.evaluate-actions button', '取消优秀')
  await waitFor(cdp, 'document.body.innerText.includes("已取消优秀展示")', 'teacher cancels excellent')
  await waitFor(cdp, '[...document.querySelectorAll(".detail button")].some(b => b.innerText.includes("评选为优秀"))', 'evaluate button restored')
  await clickText(cdp, '.evaluate-actions button', '评选为优秀')
  await waitFor(cdp, 'document.body.innerText.includes("已评选为优秀笔记")', 'teacher re-evaluates after cancel')
  await waitFor(cdp, '[...document.querySelectorAll(".note")].some(n => n.innerText.includes(' + JSON.stringify(noteBody) + ') && n.innerText.includes("优秀"))', 'teacher list excellent tag')
  console.log('PASS  teacher evaluates note, sees locked revision and list tag')

  // Student sees excellent badge in list, detail and my notes.
  await login(cdp, STUDENT_APP, username)
  await navigate(cdp, `${STUDENT_APP}/courses/${courseId}/notes`)
  await waitFor(cdp, `[...document.querySelectorAll('.note')].some(n => n.innerText.includes(${JSON.stringify(noteBody)}) && n.querySelector('.tag.excellent'))`, 'student excellent tag')
  await clickCardButton(cdp, '.note', noteBody, 'a', '查看全文')
  await waitFor(cdp, 'document.querySelector(".excellent")?.innerText.includes("优秀笔记")', 'student excellent detail badge')
  await navigate(cdp, `${STUDENT_APP}/me/notes`)
  await waitFor(cdp, `[...document.querySelectorAll('.note')].some(n => n.innerText.includes(${JSON.stringify(noteBody)}) && n.querySelector('.tag.excellent'))`, 'my notes excellent tag')
  console.log('PASS  student sees excellent marker in course list, detail and my notes')

  // Admin governance page: revoke with reason and audit.
  await login(cdp, ADMIN_APP, 'admin01')
  await navigate(cdp, `${ADMIN_APP}/admin/moderation`)
  await waitFor(cdp, 'document.body.innerText.includes("优秀奖励与审计")', 'moderation page')
  await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(noteBody)})`, 'reward listed')
  await clickCardButton(cdp, 'tbody tr', noteBody, 'button', '违规撤回')
  await waitFor(cdp, 'Boolean(document.querySelector(".reason-field textarea"))', 'revoke reason form')
  const reason = `S14C violation ${STAMP}`
  await fill(cdp, '.reason-field textarea', reason)
  await clickText(cdp, '.revoke-panel button', '确认撤回')
  await waitFor(cdp, 'document.body.innerText.includes("已撤回优秀奖励")', 'revoke saved')
  await waitFor(cdp, `[...document.querySelectorAll('tbody tr')].some(r => r.innerText.includes(${JSON.stringify(noteBody)}) && r.innerText.includes("已撤回"))`, 'revoked row')
  await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(reason)})`, 'audit reason visible')
  await fill(cdp, '.filter select', 'REVOKED')
  await waitFor(cdp, `[...document.querySelectorAll('tbody tr')].some(r => r.innerText.includes(${JSON.stringify(noteBody)}) && r.innerText.includes("已撤回"))`, 'revoked filter')
  console.log('PASS  admin revokes with reason and sees reward/audit state')

  // Governance endpoints enforce admin-only access and state validation.
  const studentStatus = await requestStatus('/api/v1/admin/excellent-rewards', setupData.studentToken)
  const teacherStatus = await requestStatus('/api/v1/admin/excellent-rewards', setupData.teacherToken)
  const adminStatus = await requestStatus('/api/v1/admin/excellent-rewards', setupData.adminToken)
  if (studentStatus !== 403 || teacherStatus !== 403 || adminStatus !== 200) {
    throw new Error(`unexpected governance statuses: ${studentStatus}/${teacherStatus}/${adminStatus}`)
  }
  const invalid = await fetch(`${API_BASE}/api/v1/admin/excellent-rewards?state=BAD`, {
    headers: { Authorization: `Bearer ${setupData.adminToken}` }
  })
  if (invalid.status !== 400) throw new Error(`invalid state should be 400, got ${invalid.status}`)
  const audits = await api('/api/v1/admin/moderation-actions?action=REVOKE_EXCELLENT', {
    headers: { Authorization: `Bearer ${setupData.adminToken}` }
  })
  if (!audits.items.some(item => item.reason === reason)) throw new Error('governance audit list missing revoke reason')
  console.log('PASS  governance APIs enforce permissions, state validation and audit listing')

  // Wait for points event, then student sees revocation and debt hint.
  await waitDb(`SELECT COUNT(*) FROM bc_marketing.points_ledger r JOIN bc_marketing.points_ledger e ON r.original_ledger_id=e.id WHERE r.type='REVOKE' AND e.business_key=${setupData.noteId}`, '1', 'revoke ledger')
  db(`UPDATE bc_marketing.points_account SET debt=3 WHERE user_id=${studentId}`)
  await login(cdp, STUDENT_APP, username)
  await navigate(cdp, `${STUDENT_APP}/me/points`)
  await waitFor(cdp, 'document.body.innerText.includes("奖励撤回")', 'revoke ledger row')
  await waitFor(cdp, 'document.body.innerText.includes("违规撤回 5 分")', 'revoke explanation')
  await waitFor(cdp, 'document.querySelector(".debt-note")?.innerText.includes("待扣回")', 'debt hint')
  await waitFor(cdp, '[...document.querySelectorAll(".stat")].some(s => s.innerText.includes("待扣回") && s.innerText.includes("3"))', 'debt amount')
  console.log('PASS  points page shows revoke detail and debt hint')

  // Student no longer sees excellent badge after revocation.
  await navigate(cdp, `${STUDENT_APP}/courses/${courseId}/notes`)
  await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(noteBody)})`, 'student notes after revoke')
  const afterBadge = await evaluate(cdp, `[...document.querySelectorAll('.note')].some(n => n.innerText.includes(${JSON.stringify(noteBody)}) && n.querySelector('.tag.excellent'))`)
  if (afterBadge) throw new Error('excellent badge remains after revoke')
  console.log('PASS  student excellent badge disappears after revocation')

  if (cdp.exceptions.length) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log(`ALL S14-C UI CHECKS PASSED (student ${username})`)
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})