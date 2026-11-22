<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { describeError, loadAdminRefunds, reconcileRefund } from '../api/client'
import type { AdminRefundItem } from '../api/types'

const stateLabels: Record<string, string> = {
  FREEZE_CONFIRMED: '资格已确认',
  CHANNEL_PENDING: '渠道处理中',
  CHANNEL_UNKNOWN: '渠道待确认',
  REVOKE_PENDING: '待撤权',
  CLOSE_RESTORE: '待恢复权益',
  COMPLETED: '已完成',
  CLOSED: '已关闭',
  REJECTED: '资格拒绝',
  FAILED: '失败'
}
const abnormalStates = ['CHANNEL_UNKNOWN', 'CHANNEL_PENDING', 'REVOKE_PENDING', 'CLOSE_RESTORE', 'FAILED']

const refunds = ref<AdminRefundItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const state = ref('')
const loading = ref(true)
const errorMessage = ref('')
const notice = ref('')
const reconciling = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const isAbnormal = (item: AdminRefundItem): boolean => abnormalStates.includes(item.state)

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
    const result = await loadAdminRefunds(state.value, page.value, pageSize)
    refunds.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

function changeState(): void {
  page.value = 1
  void load()
}

function changePage(next: number): void {
  page.value = next
  void load()
}

async function reconcile(item: AdminRefundItem): Promise<void> {
  if (reconciling.value) return
  reconciling.value = item.id
  notice.value = ''
  errorMessage.value = ''
  try {
    const result = await reconcileRefund(item.id)
    notice.value = `退款 ${item.refundNo} 已触发对账：${String(result.state ?? '')}`
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    reconciling.value = ''
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">运营 · 退款对账</p>
      <h1>退款异常与重试</h1>
      <p class="lead">
        页面只展示退款事实并提供查询/恢复动作，不提供“审核通过退款”或修改金额的入口。
        资格由学习和交易自动判定；明确关闭的退款会恢复原订单权益。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>

    <div class="toolbar">
      <label class="filter">
        <span>退款状态</span>
        <select v-model="state" @change="changeState">
          <option value="">全部</option>
          <option v-for="value in abnormalStates" :key="value" :value="value">
            {{ stateLabels[value] }}（{{ value }}）
          </option>
          <option value="COMPLETED">已完成</option>
          <option value="CLOSED">已关闭</option>
        </select>
      </label>
      <button class="btn small-btn" type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <p v-if="loading" class="empty">正在读取退款单…</p>

    <template v-else>
      <div class="table-wrap">
        <table class="refunds">
          <thead>
            <tr>
              <th>退款单</th>
              <th>订单 / 用户</th>
              <th>状态</th>
              <th>金额</th>
              <th>进度</th>
              <th>渠道退款号</th>
              <th>更新时间</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in refunds" :key="item.id" :class="{ abnormal: isAbnormal(item) }">
              <td class="mono">{{ item.refundNo }}</td>
              <td class="mono">{{ item.orderId }}<br><span class="muted">{{ item.userId ?? '—' }}</span></td>
              <td>{{ stateLabels[item.state] ?? item.state }}<br><span class="muted mono">{{ item.state }}</span></td>
              <td>{{ formatCent(item.amountCent) }}</td>
              <td>{{ item.progressN == null || item.progressD == null ? '—' : `${item.progressN}/${item.progressD}` }}</td>
              <td class="mono">{{ item.channelRefundNo ?? '—' }}</td>
              <td>{{ formatTime(item.updatedAt) }}</td>
              <td>
                <button
                  v-if="isAbnormal(item)"
                  class="btn small-btn"
                  type="button"
                  :disabled="reconciling === item.id"
                  @click="reconcile(item)"
                >
                  {{ reconciling === item.id ? '查询中…' : '查询/恢复' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-if="refunds.length === 0" class="empty">没有匹配退款单。</p>
      </div>

      <div v-if="totalPages > 1" class="pager">
        <button class="btn small-btn" type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>

      <p class="fine-print">
        黄底行为渠道或撤权需要关注的退款。查询/恢复只重试已存在的事实，不重新发起资格审批。
      </p>
    </template>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 48px); font-weight: 500; }
.lead { max-width: 72ch; color: var(--muted); line-height: 1.8; }
.toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 24px 0 14px; }
.filter { display: grid; gap: 4px; color: var(--muted); font-size: 12px; }
.filter select { padding: 7px 9px; border: 1px solid var(--line); background: transparent; font: inherit; color: var(--ink); }
.table-wrap { overflow-x: auto; border-top: 2px solid var(--ink); }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 8px; border-bottom: 1px solid var(--line); text-align: left; vertical-align: top; }
th { color: var(--muted); font-weight: 500; }
tr.abnormal { background: rgba(164, 98, 42, .06); }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; word-break: break-all; }
.muted { color: var(--muted); font-size: 11px; }
.pager { display: flex; align-items: center; gap: 14px; margin-top: 16px; color: var(--muted); font-size: 13px; }
.small-btn { padding: 5px 9px; font-size: 12px; }
.empty { color: var(--muted); padding: 20px 0; }
.fine-print { margin-top: 18px; color: var(--muted); font-size: 12px; line-height: 1.7; }
</style>