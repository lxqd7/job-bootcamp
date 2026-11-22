<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeTradeError, loadRefundApplication, loadRefundByOrder } from '../api/client'
import type { RefundIntentView, RefundStatus } from '../api/types'
import { formatCent, formatDateTime } from '../order/format'

const route = useRoute()
const requestId = computed(() => String(route.params.requestId))
const intent = ref<RefundIntentView | null>(null)
const refund = ref<RefundStatus | null>(null)
const loading = ref(true)
const errorMessage = ref('')
let poller: ReturnType<typeof setInterval> | null = null

const active = computed(() => Boolean(intent.value && ['ACCEPTED', 'PROCESSING'].includes(intent.value.state)))

function intentLabel(state: string): string {
  return {
    ACCEPTED: '已受理，等待资格检查',
    PROCESSING: '退款处理中',
    COMPLETED: '退款成功',
    REJECTED: '未通过退款资格',
    CLOSED: '退款已关闭'
  }[state] ?? state
}

function refundLabel(state: string): string {
  return {
    FREEZE_CONFIRMED: '退款资格已确认',
    CHANNEL_PENDING: '渠道退款处理中',
    CHANNEL_UNKNOWN: '渠道结果待确认',
    REVOKE_PENDING: '已退款，等待撤权',
    CLOSE_RESTORE: '渠道关闭，恢复权益中',
    COMPLETED: '退款成功',
    CLOSED: '退款关闭',
    FAILED: '退款失败'
  }[state] ?? state
}

function reasonLabel(reason: string | null): string {
  if (!reason) return ''
  return {
    REFUND_PROGRESS: '固定小节清单完成数超过 20%',
    REFUND_EXPIRED: '已超过支付后 7×24 小时申请窗口',
    REFUND_DEPENDENCY: '订单被未退款路线抵扣，需先处理关联路线',
    QUOTE_CHANGED: '订单快照已变化',
    PAYMENT_UNKNOWN: '关联订单渠道结果未知，等待确认'
  }[reason] ?? reason
}

async function load(): Promise<void> {
  errorMessage.value = ''
  try {
    intent.value = await loadRefundApplication(requestId.value)
    if (intent.value.orderId) {
      try {
        refund.value = await loadRefundByOrder(intent.value.orderId)
      } catch {
        // 资格拒绝时可能还没有退款单，申请状态本身仍然有效。
        refund.value = null
      }
    }
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

function startPolling(): void {
  if (poller) return
  poller = setInterval(async () => {
    await load()
    if (!active.value) stopPolling()
  }, 3000)
}

function stopPolling(): void {
  if (poller) clearInterval(poller)
  poller = null
}

onMounted(async () => {
  await load()
  if (active.value) startPolling()
})

onBeforeUnmount(stopPolling)
</script>

<template>
  <AppShell>
    <p class="back">
      <RouterLink v-if="intent" :to="`/orders/${intent.orderId}`">← 返回订单详情</RouterLink>
      <RouterLink v-else to="/orders">← 返回订单列表</RouterLink>
    </p>
    <p v-if="loading" class="empty">正在读取退款申请…</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="intent">
      <section class="head">
        <p class="stage">退款申请</p>
        <h1>{{ intentLabel(intent.state) }}</h1>
        <p class="lead">
          申请编号 {{ intent.requestId }}；受理时间 {{ formatDateTime(intent.acceptedAt) }}，
          申请序列 #{{ intent.sequence }}。
        </p>
      </section>

      <p v-if="intent.reason" class="reason" :class="{ rejected: intent.state === 'REJECTED' }">
        {{ reasonLabel(intent.reason) }}
      </p>

      <section class="summary">
        <div><span>固定清单完成</span><strong>{{ intent.completedCount }} / {{ intent.totalCount }}</strong></div>
        <div><span>进度阈值</span><strong>≤ 20%</strong></div>
        <div><span>申请状态</span><strong>{{ intent.state }}</strong></div>
      </section>

      <section v-if="refund" class="card">
        <h2>退款单</h2>
        <div class="row"><span>退款单号</span><strong>{{ refund.refundId }}</strong></div>
        <div class="row"><span>退款金额</span><strong>{{ formatCent(refund.amountCent) }}</strong></div>
        <div class="row"><span>退款状态</span><strong>{{ refundLabel(refund.state) }}</strong></div>
        <div class="row"><span>渠道退款号</span><span>{{ refund.channelRefundNo || '—' }}</span></div>
        <RouterLink class="detail-link" :to="`/refunds/${refund.refundId}`">查看退款单详情</RouterLink>
      </section>
      <p v-else-if="intent.state === 'REJECTED'" class="empty">申请未进入退款渠道，订单权益已恢复。</p>
      <p v-else class="empty">退款单尚未建立，页面会自动刷新。</p>

      <p v-if="active" class="polling">页面每 3 秒刷新一次申请和退款状态。</p>
      <p class="fine-print">
        已用优惠券不退还；退款成功只撤销本订单来源，其他有效来源保留。学习及作答记录不删除。
      </p>
    </template>
  </AppShell>
</template>

<style scoped>
.back, .stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(30px, 5vw, 46px); font-weight: 500; }
.lead { color: var(--muted); line-height: 1.75; word-break: break-all; }
.reason { margin: 18px 0; padding: 12px 14px; border-left: 2px solid var(--amber); background: rgba(164, 98, 42, .05); color: var(--amber); }
.reason.rejected { border-color: var(--bad); color: var(--bad); }
.summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin-top: 24px; padding: 16px 0; border-top: 2px solid var(--ink); border-bottom: 1px solid var(--line); }
.summary div { display: grid; gap: 4px; }
.summary span { color: var(--muted); font-size: 12px; }
.card { margin-top: 24px; padding: 16px 0; border-bottom: 1px solid var(--line); }
.card h2 { margin: 0 0 8px; font-family: var(--serif); font-size: 21px; font-weight: 500; }
.row { display: flex; justify-content: space-between; gap: 16px; padding: 7px 0; border-bottom: 1px solid var(--line); font-size: 14px; }
.row:last-of-type { border-bottom: 0; }
.detail-link { display: inline-block; margin-top: 12px; }
.polling { color: var(--pine); font-size: 13px; }
.empty { color: var(--muted); }
.fine-print { margin-top: 18px; color: var(--muted); font-size: 12px; line-height: 1.7; }
</style>