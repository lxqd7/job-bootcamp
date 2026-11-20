import { setTimeout as sleep } from 'node:timers/promises'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const APP = process.env.STUDENT_APP || 'http://localhost:15173'
const USERNAME = process.env.DEMO_USER || 'student01'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'

class Cdp {
  constructor(wsUrl) {
    this.ws = new WebSocket(wsUrl)
    this.nextId = 1
    this.pending = new Map()
    this.listeners = new Map()
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

  close() {
    this.ws.close()
  }
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

async function waitFor(cdp, expression, label, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const value = await evaluate(cdp, expression).catch(() => false)
    if (value) return value
    await sleep(200)
  }
  throw new Error(`timeout waiting for ${label}`)
}

async function main() {
  const tabA = await newTab(`${APP}/login`)
  const tabB = await newTab(`${APP}/login`)
  const a = new Cdp(tabA.webSocketDebuggerUrl)
  const b = new Cdp(tabB.webSocketDebuggerUrl)
  const refreshRequests = { a: [], b: [] }
  const networkResponses = { a: [], b: [] }

  for (const [name, cdp] of [['a', a], ['b', b]]) {
    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('Network.enable')
    cdp.on('Network.requestWillBeSent', params => {
      if (params.request.url.includes('/api/v1/auth/refresh')) {
        refreshRequests[name].push(params.request.url)
      }
    })
    cdp.on('Network.responseReceived', params => {
      if (params.response.url.includes('/api/v1/auth/refresh')) {
        networkResponses[name].push(params.response.status)
      }
    })
  }

  await waitFor(a, 'document.readyState === "complete"', 'tab A login page')
  await waitFor(b, 'document.readyState === "complete"', 'tab B login page')

  const login = await evaluate(a, `fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({username: ${JSON.stringify(USERNAME)}, password: ${JSON.stringify(PASSWORD)}, clientApp: 'STUDENT'})
  }).then(async r => ({status: r.status, body: await r.json()}))`)
  if (login.status !== 200) throw new Error(`login failed: ${JSON.stringify(login)}`)

  await a.send('Page.navigate', { url: `${APP}/` })
  await waitFor(a, 'document.body.innerText.includes("欢迎回来")', 'tab A authenticated home')
  await b.send('Page.navigate', { url: `${APP}/` })
  await waitFor(b, 'document.body.innerText.includes("欢迎回来")', 'tab B authenticated home')

  const cookiesBefore = await a.send('Storage.getCookies', {})
  const oldRefresh = cookiesBefore.cookies.find(cookie => cookie.name === 'bc_student_refresh')
  const csrf = cookiesBefore.cookies.find(cookie => cookie.name === 'bc_student_csrf')

  refreshRequests.a = []
  refreshRequests.b = []
  networkResponses.a = []
  networkResponses.b = []
  await Promise.all([
    a.send('Page.reload', { ignoreCache: true }),
    b.send('Page.reload', { ignoreCache: true })
  ])
  await waitFor(a, 'document.body.innerText.includes("欢迎回来")', 'tab A after simultaneous reload', 15000)
  await waitFor(b, 'document.body.innerText.includes("欢迎回来")', 'tab B after simultaneous reload', 15000)
  await sleep(1000)

  const result = {
    refreshRequestsA: refreshRequests.a.length,
    refreshRequestsB: refreshRequests.b.length,
    refreshStatusesA: networkResponses.a,
    refreshStatusesB: networkResponses.b,
    pathA: await evaluate(a, 'location.pathname'),
    pathB: await evaluate(b, 'location.pathname'),
    hasUserA: await evaluate(a, 'document.body.innerText.includes("学员一")'),
    hasUserB: await evaluate(b, 'document.body.innerText.includes("学员一")')
  }

  if (oldRefresh && csrf) {
    const replay = await fetch('http://127.0.0.1:18080/api/v1/auth/refresh', {
      method: 'POST',
      headers: {
        'X-Client-App': 'STUDENT',
        'X-CSRF-Token': csrf.value,
        Cookie: `bc_student_refresh=${oldRefresh.value}`
      }
    })
    result.replayedOldRefreshStatus = replay.status
    result.replayedOldRefreshBody = await replay.text()

    const cookiesAfter = await a.send('Storage.getCookies', {})
    const newRefresh = cookiesAfter.cookies.find(cookie => cookie.name === 'bc_student_refresh')
    if (newRefresh) {
      const revoked = await fetch('http://127.0.0.1:18080/api/v1/auth/refresh', {
        method: 'POST',
        headers: {
          'X-Client-App': 'STUDENT',
          'X-CSRF-Token': csrf.value,
          Cookie: `bc_student_refresh=${newRefresh.value}`
        }
      })
      result.refreshAfterReplayStatus = revoked.status
    }
  }

  console.log(JSON.stringify(result, null, 2))
  a.close()
  b.close()
}

main().catch(error => {
  console.error(error.stack || error.message)
  process.exit(1)
})