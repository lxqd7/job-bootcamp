import http from 'node:http'
import { setTimeout as sleep } from 'node:timers/promises'

const PORT = Number(process.env.AI_STUB_PORT || 18999)

const VALID_CONTENT = JSON.stringify({
  verdict: 'SATISFIED',
  summary: '补全内容符合题目要求，未执行代码。',
  issues: [],
  suggestions: ['检查数量为负的边界'],
  limitations: ['未执行代码，不能证明编译通过']
})

const VALID_ISSUE_CONTENT = JSON.stringify({
  verdict: 'PARTIAL',
  summary: '实现方向正确，但缺少负数判断。',
  issues: [
    {
      slotId: 's1',
      evidence: 'return quantity < 0 ? 0 : unit * quantity;',
      reason: '代码已包含负数判断',
      suggestion: '确认 unit 为 long 时不会溢出'
    }
  ],
  suggestions: ['补充边界测试'],
  limitations: ['未执行代码']
})

const BAD_SCHEMA_CONTENT = JSON.stringify({
  verdict: 'SATISFIED',
  summary: 'ok',
  issues: [],
  suggestions: [],
  limitations: [],
  score: 99,
  issuesSlot: 'unknown'
})

let state = reset('qwen_success')

function reset(scenario) {
  return { scenario, calls: [], requests: {}, qwenCalls: 0, deepseekCalls: 0 }
}

function behavior(scenario, provider, callNo) {
  switch (scenario) {
    case 'qwen_success':
      return { content: VALID_CONTENT }
    case 'qwen_503_deepseek_success':
      return provider === 'QWEN' ? { status: 503 } : { content: VALID_CONTENT }
    case 'qwen_format_repair_success':
      return provider === 'QWEN' && callNo === 1
        ? { content: 'not a json object' }
        : { content: VALID_ISSUE_CONTENT }
    case 'qwen_format_twice':
      return provider === 'QWEN'
        ? { content: 'not a json object' }
        : { content: VALID_CONTENT }
    case 'both_503':
      return { status: 503 }
    case 'qwen_400':
      return provider === 'QWEN' ? { status: 400 } : { content: VALID_CONTENT }
    case 'qwen_401_deepseek_success':
      return provider === 'QWEN' ? { status: 401 } : { content: VALID_CONTENT }
    case 'deepseek_format_twice':
      return provider === 'QWEN'
        ? { status: 503 }
        : { content: 'not a json object' }
    case 'qwen_timeout_deepseek_success':
      return provider === 'QWEN'
        ? { delay: 1500, content: VALID_CONTENT }
        : { content: VALID_CONTENT }
    case 'qwen_empty_repair':
      return provider === 'QWEN' && callNo === 1
        ? { content: '' }
        : { content: VALID_CONTENT }
    case 'qwen_truncated_repair':
      return provider === 'QWEN' && callNo === 1
        ? { content: VALID_CONTENT, finish: 'length' }
        : { content: VALID_CONTENT }
    case 'qwen_schema_bad_repair':
      return provider === 'QWEN' && callNo === 1
        ? { content: BAD_SCHEMA_CONTENT }
        : { content: VALID_CONTENT }
    case 'qwen_schema_bad_twice':
      return provider === 'QWEN'
        ? { content: BAD_SCHEMA_CONTENT }
        : { content: VALID_CONTENT }
    default:
      return { content: VALID_CONTENT }
  }
}

function sendJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
  res.end(JSON.stringify(body))
}

function readBody(req) {
  return new Promise(resolve => {
    let raw = ''
    req.on('data', chunk => { raw += chunk })
    req.on('end', () => {
      try {
        resolve(raw ? JSON.parse(raw) : {})
      } catch {
        resolve({})
      }
    })
  })
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'POST' && req.url === '/control') {
    const body = await readBody(req)
    state = reset(String(body.scenario || 'qwen_success'))
    sendJson(res, 200, { scenario: state.scenario })
    return
  }
  if (req.method === 'GET' && req.url === '/state') {
    sendJson(res, 200, {
      scenario: state.scenario,
      calls: state.calls,
      requests: state.requests
    })
    return
  }
  const provider = req.url.startsWith('/qwen') ? 'QWEN'
    : req.url.startsWith('/deepseek') ? 'DEEPSEEK' : null
  if (!provider) {
    sendJson(res, 404, { error: 'unknown path' })
    return
  }
  const body = await readBody(req)
  state.calls.push({ provider, path: req.url })
  state.requests[provider] = body
  const callNo = provider === 'QWEN' ? ++state.qwenCalls : ++state.deepseekCalls
  const plan = behavior(state.scenario, provider, callNo)
  if (plan.delay) {
    await sleep(plan.delay)
  }
  if (plan.status) {
    sendJson(res, plan.status, { error: { message: 'stub provider unavailable' } })
    return
  }
  sendJson(res, 200, {
    choices: [
      {
        message: { content: plan.content },
        finish_reason: plan.finish || 'stop'
      }
    ]
  })
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`S11-B AI stub listening on ${PORT}`)
})