import { setTimeout as sleep } from 'node:timers/promises'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
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
  await navigate(cdp, `${ADMIN_APP}/login`)
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

async function loginApi(username) {
  return api('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password: PASSWORD, clientApp: 'ADMIN' })
  })
}

async function setup() {
  const admin = await loginApi('admin01')
  const headers = { Authorization: `Bearer ${admin.accessToken}` }
  const rule = await api('/api/v1/admin/coupon-rules', {
    method: 'POST', headers,
    body: JSON.stringify({
      ruleKey: `S15A_UI_${STAMP}`, type: 'NO_THRESHOLD', scope: 'ALL',
      amountCent: 500, validityType: 'DAYS', validDays: 7
    })
  })
  return { adminToken: admin.accessToken, ruleId: rule.id }
}

async function main() {
  const { ruleId } = await setup()
  const name = `S15A UI Project ${STAMP}`
  const tab = await newTab(`${ADMIN_APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Page.bringToFront').catch(() => {})

  await login(cdp, 'admin01')
  await navigate(cdp, `${ADMIN_APP}/admin/exchange-projects`)
  await waitFor(cdp, 'document.body.innerText.includes("积分兑换项目")', 'exchange project page')
  await waitFor(cdp, 'Boolean(document.querySelector(".form-grid input"))', 'project form')
  await waitFor(cdp, 'document.querySelectorAll(".form-grid option").length > 1 && Boolean(document.querySelector(".grid"))', 'project data loaded')

  await fill(cdp, '.form-grid input', name)
  await fill(cdp, '.form-grid select', ruleId)
  await clickText(cdp, '.form-panel button', '创建项目')
  await waitFor(cdp, 'document.body.innerText.includes("兑换项目已创建")', 'project created')
  await waitFor(cdp, `document.body.innerText.includes(${JSON.stringify(name)})`, 'project listed')
  await waitFor(cdp, `[...document.querySelectorAll('tbody tr')].some(r => r.innerText.includes(${JSON.stringify(name)}) && r.innerText.includes("已下架"))`, 'offline state')
  await waitFor(cdp, `[...document.querySelectorAll('tbody tr')].some(r => r.innerText.includes(${JSON.stringify(name)}) && r.innerText.includes("0 / 100"))`, 'stock shown')
  console.log('PASS  admin creates an offline exchange project with rule/stock/limit')

  await clickCardButton(cdp, 'tbody tr', name, '编辑')
  await waitFor(cdp, 'document.body.innerText.includes("编辑兑换项目")', 'edit mode')
  const numberInputs = await evaluate(cdp, '[...document.querySelectorAll(".form-grid input[type=number]")].length')
  if (numberInputs < 3) throw new Error('project numeric fields missing')
  await fill(cdp, '.form-grid input[type=number]', '120')
  await evaluate(cdp, `(() => {
    const inputs=[...document.querySelectorAll('.form-grid input[type=number]')];
    const set=(el,v)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,String(v));el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));};
    set(inputs[1], 50);
    set(inputs[2], 2);
    return true;
  })()`)
  await clickText(cdp, '.form-panel button', '保存修改')
  await waitFor(cdp, 'document.body.innerText.includes("兑换项目已更新")', 'project updated')
  await waitFor(cdp, `[...document.querySelectorAll('tbody tr')].some(r => r.innerText.includes(${JSON.stringify(name)}) && r.innerText.includes("120 分") && r.innerText.includes("0 / 50"))`, 'updated price/stock')
  console.log('PASS  admin edits price, stock and per-user limit with revision')

  await clickCardButton(cdp, 'tbody tr', name, '上架')
  await waitFor(cdp, 'document.body.innerText.includes("兑换项目已上架")', 'project online')
  await waitFor(cdp, `[...document.querySelectorAll('tbody tr')].some(r => r.innerText.includes(${JSON.stringify(name)}) && r.innerText.includes("已上架"))`, 'online state')
  await clickCardButton(cdp, 'tbody tr', name, '下架')
  await waitFor(cdp, 'document.body.innerText.includes("兑换项目已下架")', 'project offline')
  await waitFor(cdp, `[...document.querySelectorAll('tbody tr')].some(r => r.innerText.includes(${JSON.stringify(name)}) && r.innerText.includes("已下架"))`, 'offline again')
  await clickCardButton(cdp, 'tbody tr', name, '上架')
  await waitFor(cdp, 'document.body.innerText.includes("兑换项目已上架")', 'project re-online')
  console.log('PASS  admin can online, offline and re-online a project')

  await fill(cdp, '.filter select', 'ONLINE')
  await waitFor(cdp, `[...document.querySelectorAll('tbody tr')].some(r => r.innerText.includes(${JSON.stringify(name)}) && r.innerText.includes("已上架"))`, 'online filter')
  await fill(cdp, '.filter select', 'OFFLINE')
  await waitFor(cdp, `![...document.querySelectorAll('tbody tr')].some(r => r.innerText.includes(${JSON.stringify(name)}))`, 'offline filter excludes project')
  console.log('PASS  state filter reflects the current project state')

  if (cdp.exceptions.length) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log(`ALL S15-A UI CHECKS PASSED (project ${name})`)
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})