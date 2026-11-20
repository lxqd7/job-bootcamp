<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { describeError, loadAdminOrders, runPaymentScenario } from '../api/client'
import type { AdminOrderItem } from '../api/types'

const stateLabels: Record<string, string> = {
  CREATING: '创建中',
  WAIT_PAY: '待支付',
  PAID: '已支付',
  CLOSING: '关闭中',
  CLOSED: '已关闭',
  CREATE_FAILED: '创建失败',
  RECONCILING: '对账中'
}

const fulfillmentLabels: Record<string, string> = {
  PENDING: '待开通',
  RUNNING: '开通中',
  DONE: '已开通',
  ATTENTION: '需关注'
}

const orders = ref<AdminOrderItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const state = ref('')
const loading = ref(true)
const errorMessage = ref('')
const selectedId = ref('')
const scenario = ref('SUCCESS')
const paidAt = ref('')
const running = ref(false)
const scenarioResult = ref('')
const notice = ref('')

const selected = computed(() => orders.value.find(order => order.id === selectedId.value) ?? null)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const scenarioOptions = [
  { value: 'SUCCESS', label: '支付成功' },
  { value: 'DUPLICATE', label: '重复回调' },
  { value: 'EXTRA_PAYMENT', label: '额外收款' },
  { value: 'LATE_NOTIFICATION', label: '延迟通知' },
  { value: 'UNKNOWN', label: '渠道未知' },
  { value: 'CLOSE_SUCCESS', label: '关闭成功' },
  { value: 'CLOSE_UNKNOWN', label: '关闭未知' }
]

function formatCent(value: number): string {
  return `¥${(value / 100).toFixed(2)}`
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadAdminOrders(state.value, page.value, pageSize)
    orders.value = result.items
    total.value = result.total
    if (!selected.value) {
      selectedId.value = result.items[0]?.id ?? ''
    }
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

function select(order: AdminOrderItem): void {
  selectedId.value = order.id
  notice.value = ''
  scenarioResult.value = ''
}

function changeState(): void {
  page.value = 1
  selectedId.value = ''
  void load()
}

function changePage(next: number): void {
  page.value = Math.min(totalPages.value, Math.max(1, next))
  void load()
}

async function runScenario(): Promise<void> {
  const order = selected.value
  if (!order?.paymentRequestId || running.value) return
  if (scenario.value === 'LATE_NOTIFICATION' && !paidAt.value) {
    errorMessage.value = '延迟通知场景需要填写渠道 paidAt。'
    return
  }
  if (!window.confirm(`确认对支付单 #${order.paymentRequestId} 执行 MOCK 场景“${scenario.value}”？`)) {
    return
  }
  running.value = true
  errorMessage.value = ''
  notice.value = ''
  scenarioResult.value = ''
  try {
    const result = await runPaymentScenario(order.paymentRequestId, scenario.value, paidAt.value || undefined)
    scenarioResult.value = JSON.stringify(result, null, 2)
    notice.value = '模拟场景已执行，订单列表已刷新。'
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    running.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">开发专用 · 交易场景</p>
      <h1>订单与模拟支付</h1>
      <p class="lead">
        当前页面只在本地/测试打开，操作真实 MOCK 回调处理链路。生产应关闭模拟场景，不提供“人工改金额/人工标付款”。
      </p>
    </section>

    <p class="warning">MOCK 支付场景仅用于演示；真实扣款、真实渠道回调未接入。</p>
    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>

    <div class="toolbar">
      <label class="filter">
        <span>订单状态</span>
        <select v-model="state" @change="changeState">
          <option value="">全部</option>
          <option value="WAIT_PAY">待支付</option>
          <option value="PAID">已支付</option>
          <option value="CLOSED">已关闭</option>
          <option value="CLOSING">关闭中</option>
        </select>
      </label>
      <button class="btn small-btn" type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <p v-if="loading" class="empty">正在读取订单…</p>

    <template v-else>
      <div class="table-wrap">
        <table class="orders">
          <thead>
            <tr>
              <th>订单号</th>
              <th>用户</th>
              <th>状态</th>
              <th>发权</th>
              <th>应付</th>
              <th>支付单</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="order in orders"
              :key="order.id"
              :class="{ selected: order.id === selectedId }"
              @click="select(order)"
            >
              <td class="mono">{{ order.orderNo }}</td>
              <td>{{ order.userId }}</td>
              <td>{{ stateLabels[order.state] ?? order.state }}</td>
              <td>{{ fulfillmentLabels[order.fulfillmentState] ?? order.fulfillmentState }}</td>
              <td>{{ formatCent(order.payableCent) }}</td>
              <td class="mono">{{ order.paymentRequestId ?? '—' }} / {{ order.paymentRequestState ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="orders.length === 0" class="empty">没有匹配订单。</p>
      </div>

      <div v-if="totalPages > 1" class="pager">
        <button class="btn small-btn" type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>

      <section v-if="selected" class="scenario-panel">
        <h2>场景控制 · {{ selected.orderNo }}</h2>
        <p class="muted">
          创建 {{ formatTime(selected.createdAt) }} · 到期 {{ formatTime(selected.expiresAt) }} ·
          支付时间 {{ formatTime(selected.paidAt) }}
        </p>
        <div v-if="selected.paymentRequestId" class="form-grid">
          <label class="field">
            <span>场景</span>
            <select v-model="scenario">
              <option v-for="option in scenarioOptions" :key="option.value" :value="option.value">
                {{ option.label }}（{{ option.value }}）
              </option>
            </select>
          </label>
          <label class="field">
            <span>渠道 paidAt（可选，带时区）</span>
            <input v-model="paidAt" placeholder="2026-11-20T13:00:00Z" />
          </label>
        </div>
        <p v-else class="empty">该订单还没有支付单。先由学生发起支付，或选择其他订单。</p>
        <div class="actions">
          <button
            class="btn btn-primary"
            type="button"
            :disabled="running || !selected.paymentRequestId"
            @click="runScenario"
          >
            {{ running ? '执行中…' : '执行模拟场景' }}
          </button>
        </div>
        <pre v-if="scenarioResult" class="result">{{ scenarioResult }}</pre>
      </section>
    </template>
  </AppShell>
</template>

<style scoped>
.stage {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 13px;
  letter-spacing: 0.06em;
}

h1 {
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(32px, 5vw, 44px);
  font-weight: 500;
}

.lead {
  max-width: 68ch;
  color: var(--muted);
  line-height: 1.75;
}

.warning {
  margin: 22px 0;
  padding: 10px 14px;
  border-left: 2px solid var(--amber);
  background: rgba(164, 98, 42, 0.06);
  color: var(--amber);
  font-size: 13px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 16px;
  margin-top: 24px;
  padding-bottom: 14px;
  border-bottom: 2px solid var(--ink);
}

.filter,
.field {
  display: grid;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
}

.filter select,
.field select,
.field input {
  padding: 6px 9px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-size: 14px;
}

.table-wrap {
  overflow-x: auto;
}

.orders {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.orders th,
.orders td {
  padding: 10px 8px;
  border-bottom: 1px solid var(--line);
  text-align: left;
  white-space: nowrap;
}

.orders th {
  color: var(--muted);
  font-weight: 500;
}

.orders tbody tr {
  cursor: pointer;
}

.orders tbody tr:hover,
.orders tbody tr.selected {
  background: rgba(23, 89, 76, 0.07);
}

.mono {
  font-family: var(--mono);
  font-size: 12px;
}

.scenario-panel {
  margin-top: 30px;
  padding-top: 18px;
  border-top: 2px solid var(--ink);
}

.scenario-panel h2 {
  margin: 0 0 4px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.scenario-panel .muted {
  margin: 0 0 16px;
  color: var(--muted);
  font-size: 13px;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  max-width: 720px;
}

.actions {
  margin-top: 16px;
}

.result {
  max-height: 280px;
  margin-top: 16px;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--line);
  background: rgba(26, 28, 25, 0.04);
  font-family: var(--mono);
  font-size: 12px;
  white-space: pre-wrap;
}

.pager {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 16px;
  color: var(--muted);
  font-size: 13px;
}

.small-btn {
  padding: 5px 12px;
  font-size: 13px;
}

.notice {
  color: var(--pine);
  font-size: 14px;
}

.empty {
  padding: 20px 0;
  color: var(--muted);
}

@media (max-width: 700px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>