<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { createOrder, createQuote, describeTradeError } from '../api/client'
import type { TradeQuote } from '../api/types'
import { formatCent, formatCountdown, remainingSeconds } from '../order/format'

const route = useRoute()
const router = useRouter()
const courseId = computed(() => String(route.query.courseId ?? ''))
const quote = ref<TradeQuote | null>(null)
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const notice = ref('')
const nowMs = ref(Date.now())
const idempotencyKey = ref('')

let timer: ReturnType<typeof setInterval> | null = null

const secondsLeft = computed(() =>
  quote.value ? remainingSeconds(quote.value.expiresAt, nowMs.value) : 0
)
const expired = computed(() => Boolean(quote.value && secondsLeft.value <= 0))

function freshKey(): string {
  const cryptoApi = globalThis.crypto
  return cryptoApi && 'randomUUID' in cryptoApi
    ? cryptoApi.randomUUID()
    : `order-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function loadQuote(): Promise<void> {
  if (!courseId.value) {
    errorMessage.value = '缺少课程 ID，无法创建确认单。'
    loading.value = false
    return
  }
  loading.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    quote.value = await createQuote(courseId.value)
    idempotencyKey.value = freshKey()
  } catch (error) {
    quote.value = null
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

function refreshClock(): void {
  nowMs.value = Date.now()
}

async function confirmOrder(): Promise<void> {
  if (!quote.value || submitting.value || expired.value) return
  submitting.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const order = await createOrder(quote.value.quoteId, quote.value.quoteHash, idempotencyKey.value)
    await router.push({ name: 'order-detail', params: { id: order.id } })
  } catch (error) {
    const code = (error as { response?: { data?: { code?: string } } }).response?.data?.code
    const message = describeTradeError(error)
    if (code === 'QUOTE_CHANGED') {
      await loadQuote()
      notice.value = '价格或权益已变化，已重新获取报价，请重新确认。'
    } else {
      errorMessage.value = message
    }
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadQuote()
  timer = setInterval(refreshClock, 1000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink :to="`/courses/${courseId}`">← 返回课程详情</RouterLink></p>

    <section class="head">
      <p class="stage">确认订单</p>
      <h1>确认购买</h1>
      <p class="lead">金额来自服务端报价；确认后创建 30 分钟待支付订单。优惠券将在 S08 开放。</p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="loading" class="empty">正在获取可信报价…</p>

    <template v-else-if="quote">
      <section class="quote-card">
        <div class="quote-head">
          <div>
            <p class="stage">商品</p>
            <h2>{{ quote.product.title }}</h2>
            <p class="muted">课程 ID {{ quote.product.id }} · 目录版本 {{ quote.product.version }}</p>
          </div>
          <span class="price">{{ formatCent(quote.payableCent) }}</span>
        </div>

        <dl class="amounts">
          <div><dt>商品金额</dt><dd>{{ formatCent(quote.totalCent) }}</dd></div>
          <div><dt>抵扣</dt><dd>{{ formatCent(quote.creditCent) }}</dd></div>
          <div><dt>优惠</dt><dd>{{ formatCent(quote.discountCent) }}</dd></div>
          <div class="total"><dt>应付</dt><dd>{{ formatCent(quote.payableCent) }}</dd></div>
        </dl>

        <p class="expiry" :class="{ expired }">
          <template v-if="expired">报价已过期，请重新获取。</template>
          <template v-else>报价剩余 {{ formatCountdown(secondsLeft) }}，过期后需要重新确认。</template>
        </p>

        <div class="actions">
          <button class="btn btn-primary" type="button" :disabled="submitting || expired" @click="confirmOrder">
            {{ submitting ? '创建中…' : '确认并创建订单' }}
          </button>
          <button class="btn" type="button" :disabled="submitting" @click="loadQuote">重新报价</button>
        </div>
        <p class="fine-print">提交中按钮会禁用并复用同一个 Idempotency-Key，避免重复订单。</p>
      </section>
    </template>
  </AppShell>
</template>

<style scoped>
.back,
.stage {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 13px;
  letter-spacing: 0.06em;
}

.back a {
  letter-spacing: 0;
}

h1 {
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(34px, 5vw, 48px);
  font-weight: 500;
}

.lead {
  max-width: 62ch;
  color: var(--muted);
  line-height: 1.75;
}

.quote-card {
  margin-top: 32px;
  padding-top: 20px;
  border-top: 2px solid var(--ink);
}

.quote-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 24px;
}

.quote-head h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: 28px;
  font-weight: 500;
}

.quote-head .muted {
  margin: 4px 0 0;
  font-size: 13px;
}

.price {
  font-family: var(--serif);
  font-size: 34px;
  white-space: nowrap;
}

.amounts {
  margin: 26px 0 0;
  border-top: 1px solid var(--line);
}

.amounts div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}

.amounts dt {
  color: var(--muted);
}

.amounts dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
}

.amounts .total {
  color: var(--ink);
  font-size: 17px;
  font-weight: 600;
}

.expiry {
  margin: 18px 0 0;
  color: var(--pine);
  font-size: 13px;
}

.expiry.expired {
  color: var(--bad);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 22px;
}

.notice {
  margin: 12px 0;
  color: var(--pine);
  font-size: 14px;
}

.fine-print {
  margin: 14px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}

@media (max-width: 560px) {
  .quote-head {
    display: block;
  }

  .price {
    display: block;
    margin-top: 12px;
  }
}
</style>