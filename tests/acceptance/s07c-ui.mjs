import { setTimeout as sleep } from 'node:timers/promises'

const CDP_BASE = process.env.CDP_BASE || 'http://127.0.0.1:9222'
const APP = process.env.STUDENT_APP || 'http://localhost:15173'
const ROUTE_ID = process.env.ROUTE_ID || '11'
const USERNAME = process.env.DEMO_USER || 'student03'
const PASSWORD = process.env.DEMO_PASSWORD || 'Demo@123456'

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

async function clickText(cdp, selector, text) {
  const clicked = await evaluate(cdp, `(() => {
    const el = [...document.querySelectorAll(${JSON.stringify(selector)})].find(item => item.innerText.includes(${JSON.stringify(text)}));
    if (!el) return false;
    el.click();
    return true;
  })()`)
  if (!clicked) throw new Error(`element with text not found: ${text}`)
}

async function login(cdp) {
  await cdp.send('Network.clearBrowserCookies').catch(() => {})
  await navigate(cdp, `${APP}/login`)
  await waitFor(cdp, 'Boolean(document.querySelector("input[autocomplete=username]"))', 'login form')
  await fill(cdp, 'input[autocomplete=username]', USERNAME)
  await fill(cdp, 'input[type=password]', PASSWORD)
  await click(cdp, 'button[type=submit]')
  await waitFor(cdp, 'location.pathname !== "/login"', 'login redirect')
  console.log(`PASS  ${USERNAME} login`)
}

async function main() {
  const tab = await newTab(`${APP}/login`)
  const cdp = new Cdp(tab.webSocketDebuggerUrl)
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  cdp.exceptions = []
  cdp.failedResponses = []
  cdp.ws.addEventListener('message', event => {
    const message = JSON.parse(event.data)
    if (message.method === 'Runtime.exceptionThrown') {
      cdp.exceptions.push(message.params.exceptionDetails?.text || 'exception')
    }
    if (message.method === 'Network.responseReceived' && message.params.response.status >= 500) {
      cdp.failedResponses.push(`${message.params.response.url} ${message.params.response.status}`)
    }
  })

  await login(cdp)
  await navigate(cdp, `${APP}/routes/${ROUTE_ID}`)
  await waitFor(cdp, 'document.body.innerText.includes("购买路线")', 'route purchase entry')
  await waitFor(cdp, 'document.body.innerText.includes("套餐内分摊")', 'route allocation table')
  console.log('PASS  route detail shows allocation and purchase entry')

  await click(cdp, 'a.btn-primary')
  await waitFor(cdp, 'location.pathname === "/checkout"', 'checkout route')
  await waitFor(cdp, 'document.body.innerText.includes("课程抵扣明细")', 'route deduction details')
  await waitFor(cdp, 'document.body.innerText.includes("已抵扣")', 'route credit line')
  await waitFor(cdp, 'document.body.innerText.includes("退款依赖提示")', 'refund dependency warning')
  console.log('PASS  checkout shows deduction and refund warning')

  await click(cdp, 'button.btn-primary')
  await waitFor(cdp, '/^\\/orders\\/\\d+$/.test(location.pathname)', 'route order detail', 20000)
  await waitFor(cdp, 'document.body.innerText.includes("WAIT_PAY") || document.body.innerText.includes("待支付")', 'route order WAIT_PAY')
  console.log('PASS  route order created from checkout')

  await evaluate(cdp, 'window.confirm = () => true')
  await clickText(cdp, 'button', '取消订单')
  await waitFor(cdp, 'document.body.innerText.includes("已关闭")', 'route order cancelled')
  console.log('PASS  route order cancel path works')

  await navigate(cdp, `${APP}/me/routes`)
  await waitFor(cdp, 'document.body.innerText.includes("我的路线")', 'my routes page')
  await waitFor(cdp, 'document.body.innerText.includes("包含后续新增课程权益")', 'future course entitlement')
  console.log('PASS  my routes shows future-course entitlement')

  await navigate(cdp, `${APP}/routes/8`)
  await waitFor(cdp, 'document.body.innerText.includes("已拥有路线资格")', 'owned route marker')
  console.log('PASS  route detail marks owned qualification')

  if (cdp.exceptions.length > 0) throw new Error(`runtime exceptions: ${cdp.exceptions.join('; ')}`)
  if (cdp.failedResponses.length > 0) throw new Error(`5xx responses: ${cdp.failedResponses.join('; ')}`)
  console.log('PASS  no runtime exception or 5xx response')
  cdp.close()
  console.log('ALL S07-C UI CHECKS PASSED')
}

main().catch(error => {
  console.error('FAIL ', error.message)
  process.exit(1)
})