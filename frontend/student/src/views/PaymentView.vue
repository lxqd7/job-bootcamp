<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeTradeError, initiatePayment, loadOrder, mockPay } from '../api/client'
import type { OrderResponse, PaymentInitiateResponse } from '../api/types'
import { formatCent, formatDateTime, fulfillmentLabel, orderStateLabel } from '../order/format'

const route = useRoute()
const order = ref<OrderResponse | null>(null)
const payment = ref<PaymentInitiateResponse | null>(null)
const loading = ref(true)
const paying = ref(false)
const errorMessage = ref('')
const notice = ref('')
const orderId = computed(() => String(route.params.id))

let poller: ReturnType<typeof setInterval> | null = null
let pollCount = 0

const isMock = computed(() => payment.value?.mode === 'MOCK')
const canPay = computed(() => order.value?.state === 'WAIT_PAY')

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    order.value = await loadOrder(orderId.value)
    if (order.value.state === 'WAIT_PAY') {
      // 支付页只创建一次渠道支付单；重复刷新复用同一 payment_request。
      payment.value = await initiatePayment(orderId.value)
    } else if (order.value.state === 'PAID' && order.value.fulfillmentState !== 'DONE') {
      startPolling()
    }
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

function startPolling(): void {
  stopPolling()
  pollCount = 0
  // 支付成功只代表已收款；继续轮询到 learning 回执 DONE 才显示权益已开通。
  poller = setInterval(async () => {
    pollCount++
    try {
      const latest = await loadOrder(orderId.value)
      order.value = latest
      if (latest.state === 'PAID' && latest.fulfillmentState !== 'DONE' && pollCount < 10) {
        return
      }
      if (latest.state !== 'WAIT_PAY') {
        notice.value = latest.state === 'PAID'
          ? latest.fulfillmentState === 'DONE'
            ? '支付已确认，学习权益已开通。'
            : '支付已确认，学习权益仍在校验中，请到订单详情继续查看。'
          : '订单已关闭，未形成有效购买。'
        stopPolling()
      } else if (pollCount >= 10) {
        notice.value = '支付回调尚未到达，订单仍待支付。请稍后刷新或查看服务端回调。'
        stopPolling()
      }
    } catch {
      // 轮询失败不覆盖当前页面
    }
  }, 1500)
}

function stopPolling(): void {
  if (poller) clearInterval(poller)
  poller = null
}

async function simulatePay(): Promise<void> {
  if (!order.value || paying.value || !canPay.value) return
  paying.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    order.value = await mockPay(order.value.id)
    if (order.value.state === 'PAID') {
      notice.value = 'MOCK 支付已确认，正在等待学习服务开通。'
      startPolling()
    } else {
      notice.value = '支付结果已提交，请等待服务端确认。'
    }
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    paying.value = false
  }
}

onMounted(load)
onBeforeUnmount(stopPolling)
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink :to="{ name: 'order-detail', params: { id: orderId } }">← 返回订单详情</RouterLink></p>

    <p v-if="loading" class="empty">正在准备支付…</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="order">
      <section class="head">
        <p class="stage">支付订单</p>
        <h1>{{ formatCent(order.pricing.payableCent) }}</h1>
        <p class="lead">订单 {{ order.orderNo }} · 当前状态 {{ orderStateLabel(order.state) }}</p>
      </section>

      <p v-if="notice" class="notice" role="status">{{ notice }}</p>

      <section v-if="canPay" class="pay-card">
        <div class="mock-label">MOCK 模拟支付 · 仅本地演示，不会产生真实扣款</div>
        <p class="muted">
          首版只接入模拟渠道。点击下方按钮模拟一次成功付款；付款时间由服务端模拟，学生不能指定。
        </p>
        <div v-if="payment" class="payment-meta">
          <span>支付单 #{{ payment.paymentId }}</span>
          <span>渠道 {{ payment.mode }}</span>
          <span>状态 {{ payment.state }}</span>
        </div>
        <button
          v-if="isMock"
          class="btn btn-primary pay-button"
          type="button"
          :disabled="paying"
          @click="simulatePay"
        >
          {{ paying ? '提交中…' : '模拟支付成功' }}
        </button>
        <p v-else class="muted">当前支付渠道尚未开放。</p>
        <button class="btn" type="button" :disabled="paying" @click="load">刷新支付单</button>
      </section>

      <section v-else class="result">
        <p class="result-title">{{ orderStateLabel(order.state) }}</p>
        <p class="muted">
          <template v-if="order.state === 'PAID'">
            支付时间 {{ formatDateTime(order.paidAt) }}，发权状态 {{ fulfillmentLabel(order.fulfillmentState) }}。
          </template>
          <template v-else>订单已关闭，不会继续收款；如已产生异常收款，服务端会创建系统退回。</template>
        </p>
        <RouterLink class="btn" :to="{ name: 'order-detail', params: { id: order.id } }">查看订单详情</RouterLink>
      </section>

      <p class="fine-print">
        前端跳转或按钮点击不代表支付成功；只有服务端验签回调与订单查询确认后，订单才会变为已支付。
      </p>
    </template>
  </AppShell>
</template>

<style scoped>
.back {
  margin: 0 0 18px;
  font-size: 14px;
}

.stage {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 13px;
  letter-spacing: 0.06em;
}

h1 {
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(44px, 8vw, 68px);
  font-weight: 500;
  line-height: 1;
}

.lead {
  color: var(--muted);
  line-height: 1.7;
}

.pay-card,
.result {
  margin-top: 30px;
  padding: 22px 0;
  border-top: 2px solid var(--ink);
  border-bottom: 1px solid var(--line);
}

.mock-label {
  display: inline-block;
  margin-bottom: 12px;
  padding: 3px 9px;
  border: 1px solid var(--amber);
  color: var(--amber);
  font-size: 12px;
  letter-spacing: 0.04em;
}

.pay-card .muted,
.result .muted {
  max-width: 62ch;
  font-size: 14px;
  line-height: 1.7;
}

.payment-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin: 14px 0;
  color: var(--muted);
  font-family: var(--mono);
  font-size: 12px;
}

.pay-button {
  margin-right: 10px;
}

.result-title {
  margin: 0 0 6px;
  font-family: var(--serif);
  font-size: 26px;
}

.notice {
  margin: 12px 0;
  color: var(--pine);
  font-size: 14px;
}

.fine-print {
  margin-top: 18px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}
</style>