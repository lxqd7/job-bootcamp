<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeTradeError, loadRefund } from '../api/client'
import type { RefundDetail } from '../api/types'
import { formatCent, formatDateTime } from '../order/format'

const route = useRoute()
const refundId = computed(() => String(route.params.id))
const refund = ref<RefundDetail | null>(null)
const loading = ref(true)
const errorMessage = ref('')

const states: Record<string, string> = {
  FREEZE_CONFIRMED: '退款资格已确认',
  CHANNEL_PENDING: '渠道退款处理中',
  CHANNEL_UNKNOWN: '渠道结果待确认',
  REVOKE_PENDING: '已退款，等待撤权',
  CLOSE_RESTORE: '渠道关闭，恢复权益中',
  COMPLETED: '退款成功',
  CLOSED: '退款关闭',
  FAILED: '退款失败'
}

async function load(): Promise<void> {
  try {
    refund.value = await loadRefund(refundId.value)
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink v-if="refund" :to="`/orders/${refund.orderId}`">← 返回订单详情</RouterLink></p>
    <p v-if="loading" class="empty">正在读取退款单…</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <template v-else-if="refund">
      <section class="head">
        <p class="stage">退款单</p>
        <h1>{{ states[refund.state] || refund.state }}</h1>
        <p class="lead">退款单号 {{ refund.refundNo }}；受理于 {{ formatDateTime(refund.acceptedAt) }}。</p>
      </section>
      <section class="card">
        <div class="row"><span>退款金额</span><strong>{{ formatCent(refund.amountCent) }}</strong></div>
        <div class="row"><span>退款状态</span><span>{{ refund.state }}</span></div>
        <div class="row"><span>渠道退款号</span><span>{{ refund.channelRefundNo || '—' }}</span></div>
        <div class="row"><span>关联订单</span><RouterLink :to="`/orders/${refund.orderId}`">{{ refund.orderId }}</RouterLink></div>
      </section>
      <p class="fine-print">已用优惠券不退还；退款成功后本订单来源撤销，其他有效来源和学习记录保留。</p>
    </template>
  </AppShell>
</template>

<style scoped>
.back, .stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(30px, 5vw, 46px); font-weight: 500; }
.lead { color: var(--muted); word-break: break-all; }
.card { margin-top: 24px; padding: 16px 0; border-top: 2px solid var(--ink); border-bottom: 1px solid var(--line); }
.row { display: flex; justify-content: space-between; gap: 16px; padding: 8px 0; border-bottom: 1px solid var(--line); font-size: 14px; }
.row:last-child { border-bottom: 0; }
.empty { color: var(--muted); }
.fine-print { margin-top: 18px; color: var(--muted); font-size: 12px; line-height: 1.7; }
</style>