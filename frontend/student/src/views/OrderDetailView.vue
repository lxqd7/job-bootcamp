<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { cancelOrder, describeTradeError, loadOrder } from '../api/client'
import type { OrderResponse } from '../api/types'
import {
  formatCent,
  formatCountdown,
  formatDateTime,
  fulfillmentLabel,
  orderStateLabel,
  remainingSeconds
} from '../order/format'

const route = useRoute()
const order = ref<OrderResponse | null>(null)
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const notice = ref('')
const nowMs = ref(Date.now())

let clock: ReturnType<typeof setInterval> | null = null
let poller: ReturnType<typeof setInterval> | null = null

const orderId = computed(() => String(route.params.id))
const secondsLeft = computed(() => (order.value ? remainingSeconds(order.value.expiresAt, nowMs.value) : 0))
const expired = computed(() => Boolean(order.value && order.value.state === 'WAIT_PAY' && secondsLeft.value <= 0))

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    order.value = await loadOrder(orderId.value)
    if (order.value.state === 'PAID' && order.value.fulfillmentState !== 'DONE') {
      startPolling()
    } else {
      stopPolling()
    }
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

function startPolling(): void {
  if (poller) return
  // 已付款但学习服务尚未回执时轮询；到达 DONE 立即停止，避免无意义请求。
  poller = setInterval(async () => {
    try {
      const latest = await loadOrder(orderId.value)
      order.value = latest
      if (latest.fulfillmentState === 'DONE' || latest.state !== 'PAID') {
        stopPolling()
      }
    } catch {
      // 轮询失败保留当前页面状态，用户可手动刷新
    }
  }, 3000)
}

function stopPolling(): void {
  if (poller) clearInterval(poller)
  poller = null
}

async function cancel(): Promise<void> {
  if (!order.value || submitting.value) return
  if (!window.confirm('确认取消这笔待支付订单？取消后需要重新下单。')) return
  submitting.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    order.value = await cancelOrder(order.value.id)
    notice.value = '取消请求已受理，以服务端最终状态为准。'
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void load()
  clock = setInterval(() => { nowMs.value = Date.now() }, 1000)
})

onBeforeUnmount(() => {
  if (clock) clearInterval(clock)
  stopPolling()
})
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink to="/orders">← 返回订单列表</RouterLink></p>

    <p v-if="loading" class="empty">正在读取订单…</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="order">
      <section class="head">
        <p class="stage">订单详情</p>
        <div class="title-row">
          <h1>订单 {{ order.orderNo }}</h1>
          <span class="state" :class="`state-${order.state.toLowerCase()}`">{{ orderStateLabel(order.state) }}</span>
        </div>
        <p class="lead">
          创建于 {{ formatDateTime(order.createdAt) }}；发权状态
          <strong>{{ fulfillmentLabel(order.fulfillmentState) }}</strong>。
        </p>
      </section>

      <p v-if="notice" class="notice" role="status">{{ notice }}</p>

      <section class="summary">
        <div><span>应付</span><strong>{{ formatCent(order.pricing.payableCent) }}</strong></div>
        <div><span>支付时间</span><strong>{{ formatDateTime(order.paidAt) }}</strong></div>
        <div>
          <span>{{ order.state === 'WAIT_PAY' ? '支付剩余' : '订单有效期' }}</span>
          <strong>{{ order.state === 'WAIT_PAY' ? (expired ? '已过期' : formatCountdown(secondsLeft)) : formatDateTime(order.expiresAt) }}</strong>
        </div>
      </section>

      <section class="items">
        <h2>订单内容</h2>
        <article v-for="item in order.items" :key="item.courseId" class="item">
          <div>
            <p class="item-title">课程 {{ item.courseId }}</p>
            <p class="meta">课程版本 {{ item.courseVersion }} · {{ item.newPaid ? '本次新购付费课' : '已有权益' }}</p>
          </div>
          <span>{{ formatCent(item.payableCent) }}</span>
        </article>
        <p v-if="order.items.length === 0" class="empty">订单没有课程明细。</p>
      </section>

      <section v-if="order.coupons.length > 0" class="coupons">
        <h2>优惠券明细</h2>
        <article v-for="coupon in order.coupons" :key="coupon.userCouponId" class="coupon">
          <div>
            <p class="item-title">第 {{ coupon.applyOrder }} 张 · 券 #{{ coupon.userCouponId }}</p>
            <p class="meta">
              券前基数 {{ formatCent(coupon.baseCent) }}
              <template v-if="coupon.allocation.length > 0">
                · 分摊：<span v-for="(allocation, index) in coupon.allocation" :key="allocation.courseId">
                  {{ index > 0 ? '、' : '' }}课程 {{ allocation.courseId }} {{ formatCent(allocation.discountCent) }}
                </span>
              </template>
            </p>
          </div>
          <span class="coupon-discount">-{{ formatCent(coupon.discountCent) }}</span>
        </article>
        <p class="fine-print">优惠券在订单创建时一次性锁定；支付成功后核销，未支付关闭时释放并保留原有效期。</p>
      </section>

      <section class="amounts">
        <div><span>订单金额</span><span>{{ formatCent(order.pricing.totalCent) }}</span></div>
        <div><span>抵扣</span><span>{{ formatCent(order.pricing.creditCent) }}</span></div>
        <div><span>优惠</span><span>{{ formatCent(order.pricing.discountCent) }}</span></div>
        <div class="total"><span>实付</span><span>{{ formatCent(order.pricing.payableCent) }}</span></div>
      </section>

      <div class="actions">
        <template v-if="order.state === 'WAIT_PAY'">
          <RouterLink v-if="!expired" class="btn btn-primary" :to="{ name: 'order-pay', params: { id: order.id } }">
            去支付
          </RouterLink>
          <button class="btn" type="button" :disabled="submitting" @click="cancel">
            {{ submitting ? '处理中…' : '取消订单' }}
          </button>
        </template>
        <template v-else-if="order.state === 'PAID'">
          <RouterLink
            v-if="order.fulfillmentState === 'DONE' && order.items[0]"
            class="btn btn-primary"
            :to="`/learning/courses/${order.items[0].courseId}`"
          >
            去学习
          </RouterLink>
          <RouterLink class="btn" :to="`/orders/${order.id}/refund`">申请退款</RouterLink>
          <span v-if="order.fulfillmentState !== 'DONE'" class="pending">学习服务开通处理中，本页会自动刷新。</span>
        </template>
        <button class="btn" type="button" :disabled="loading" @click="load">刷新状态</button>
      </div>

      <p class="fine-print">
        支付成功不等于学习权益已开通；发权状态以学习服务回执为准。退款入口在 S09 接入。
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

.title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
}

h1 {
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(28px, 5vw, 42px);
  font-weight: 500;
  word-break: break-all;
}

.state {
  padding: 2px 9px;
  border: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}

.state-wait_pay {
  border-color: var(--amber);
  color: var(--amber);
}

.state-paid {
  border-color: var(--pine);
  color: var(--pine);
}

.lead {
  color: var(--muted);
  line-height: 1.7;
}

.summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 28px;
  padding: 16px 0;
  border-top: 2px solid var(--ink);
  border-bottom: 1px solid var(--line);
}

.summary div {
  display: grid;
  gap: 3px;
}

.summary span {
  color: var(--muted);
  font-size: 12px;
}

.summary strong {
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.items {
  margin-top: 30px;
}

.items h2 {
  margin: 0 0 10px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}

.item-title {
  margin: 0;
  font-weight: 600;
}

.meta {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.coupons {
  margin-top: 30px;
}

.coupons h2 {
  margin: 0 0 10px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.coupon {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}

.coupon-discount {
  color: var(--pine);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.amounts {
  margin-top: 22px;
  border-top: 1px solid var(--line);
}

.amounts div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 7px 0;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 14px;
}

.amounts .total {
  color: var(--ink);
  font-size: 17px;
  font-weight: 600;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 22px;
}

.pending {
  color: var(--amber);
  font-size: 14px;
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
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}

@media (max-width: 640px) {
  .summary {
    grid-template-columns: 1fr;
  }
}
</style>