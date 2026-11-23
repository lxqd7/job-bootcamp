import { setTimeout as sleep } from 'node:timers/promises'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const ADMIN_APP = process.env.ADMIN_APP || 'http://localhost:15174'
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:18080'
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

async function api(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  })
  const body = await response.json()
  if (!response.ok) throw new Error(`${path} ${response.status} ${JSON.stringify(body)}`)
  return body.data
}

async function setup() {
  const admin = await api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'admin01', password: process.env.DEMO_PASSWORD || 'Demo@123456', clientApp: 'ADMIN' })
  })
  const course = await api('/api/v1/admin/courses', {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({
      title: `S10A UI ${STAMP}`, description: 'S10A', categoryId: 2,
      priceCent: 990, isFree: false
    })
  })
  const chapter = await api(`/api/v1/admin/courses/${course.id}/chapters`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.accessToken}` },
    body: JSON.stringify({ title: 'Chapter', sort: 1 })
  })
  return { courseId: course.id, chapterId: chapter.id }
}

async function main() {
  const { courseId, chapterId } = await setup()
  const tab = await newTab(`${ADMIN_APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')

  await cdp.send('Network.clearBrowserCookies').catch(() => {})
  await navigate(cdp, `${ADMIN_APP}/login`)
  await waitFor(cdp, 'Boolean(document.querySelector("input[autocomplete=username]"))', 'login form')
  await fill(cdp, 'input[autocomplete=username]', 'admin01')
  await fill(cdp, 'input[type=password]', process.env.DEMO_PASSWORD || 'Demo@123456')
  await evaluate(cdp, 'document.querySelector("button[type=submit]").click()')
  await waitFor(cdp, 'location.pathname !== "/login"', 'login redirect')

  await navigate(cdp, `${ADMIN_APP}/admin/assignments`)
  await waitFor(cdp, 'document.body.innerText.includes("章节选择题与课程代码题")', 'assignment admin page')
  console.log('PASS  admin assignment page loads')

  await clickText(cdp, 'button', '新建草稿')
  const filled = await evaluate(cdp, `(() => {
    const inputs=[...document.querySelectorAll('.form-grid input')];
    if(inputs.length < 2) return false;
    const set=(el,value)=>{el.value=value; el.dispatchEvent(new Event('input',{bubbles:true}));};
    set(inputs[0], ${JSON.stringify(String(courseId))});
    set(inputs[1], ${JSON.stringify(String(chapterId))});
    return true;
  })()`)
  if (!filled) throw new Error('assignment scope inputs not found')
  await clickText(cdp, 'button', '填入当前类型示例')
  await waitFor(cdp, 'document.querySelectorAll("textarea").length === 2 && document.querySelector("textarea").value.length > 10', 'sample JSON')
  console.log('PASS  assignment draft form fills sample public/answer JSON')

  await clickText(cdp, 'button', '保存草稿')
  await waitFor(cdp, 'document.body.innerText.includes("草稿已保存")', 'draft saved')
  console.log('PASS  assignment draft saved')
  await clickText(cdp, 'button', '保存并发布新版本')
  await waitFor(cdp, 'document.body.innerText.includes("已发布版本 v1")', 'version 1 published')
  await waitFor(cdp, 'document.body.innerText.includes("已发布")', 'published list state')
  console.log('PASS  assignment published as immutable version v1')

  await clickText(cdp, 'button', '下架')
  await waitFor(cdp, 'document.body.innerText.includes("已下架")', 'offline state')
  console.log('PASS  assignment offline keeps version')

  if (cdp.exceptions.length) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log('ALL S10-A UI CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})