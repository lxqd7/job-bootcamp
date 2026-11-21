<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { createOrder, createQuote, describeTradeError, loadMyCoupons, loadQuote, loadRoute } from '../api/client'
import type { CouponPlanLine, RouteDetail, TradeQuote, UserCouponView } from '../api/types'
import { formatCent, formatCountdown, remainingSeconds } from '../order/format'

type CouponMode = 'NONE' | 'AUTO' | 'MANUAL'

const route = useRoute()
const router = useRouter()
const productType = computed<'COURSE' | 'ROUTE'>(() =>
  String(route.query.productType ?? 'COURSE').toUpperCase() === 'ROUTE' ? 'ROUTE' : 'COURSE'
)
const productId = computed(() => String(route.query.productId ?? route.query.courseId ?? ''))
const isRoute = computed(() => productType.value === 'ROUTE')
const quote = ref<TradeQuote | null>(null)
const routeDetail = ref<RouteDetail | null>(null)
const availableCoupons = ref<UserCouponView[]>([])
const couponMode = ref<CouponMode>('AUTO')
const selectedCouponIds = ref<string[]>([])
const loading = ref(true)
const calculating = ref(false)
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
const ready = computed(() => quote.value?.status === 'READY')
const canConfirm = computed(() => Boolean(quote.value && ready.value && !expired.value && !loading.value && !calculating.value && !submitting.value))

function freshKey(): string {
  // 同一次确认重试必须复用；重新报价后才换新键，避免把不同报价的请求误认成同一单。
  const cryptoApi = globalThis.crypto
  return cryptoApi && 'randomUUID' in cryptoApi
    ? cryptoApi.randomUUID()
    : `order-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function courseTitle(courseId: string): string {
  const member = routeDetail.value?.members.find(item => item.courseId === courseId)
  return member?.title ?? `课程 ${courseId}`
}

function couponTypeLabel(coupon: UserCouponView): string {
  const rule = coupon.rule
  if (!rule) return `券 #${coupon.id}`
  if (rule.type === 'DISCOUNT') {
    return `${((10000 - (rule.payRateBps ?? 10000)) / 1000).toFixed(1)} 折券`
  }
  if (rule.amountCent != null) return `满 ${formatCent(rule.thresholdCent ?? 0)} 减 ${formatCent(rule.amountCent)}`
  return `券 #${coupon.id}`
}

function couponScopeLabel(coupon: UserCouponView): string {
  const rule = coupon.rule
  if (!rule) return ''
  if (rule.scope === 'ALL') return '全场'
  if (rule.scope === 'COURSE') return `课程 ${rule.targetId}`
  if (rule.scope === 'CATEGORY') return `分类 ${rule.targetId}`
  return rule.scope === 'ROUTE' ? `路线 ${rule.targetId}` : rule.scope
}

function couponPlanText(plan: CouponPlanLine[]): string {
  if (plan.length === 0) return '未使用优惠券'
  return `已使用 ${plan.length} 张券，共优惠 ${formatCent(plan.reduce((sum, item) => sum + item.discountCent, 0))}`
}

function toggleCoupon(id: string): void {
  const index = selectedCouponIds.value.indexOf(id)
  if (index >= 0) {
    selectedCouponIds.value = selectedCouponIds.value.filter(item => item !== id)
    return
  }
  if (selectedCouponIds.value.length >= 3) {
    errorMessage.value = '每单最多选择 3 张优惠券。'
    return
  }
  selectedCouponIds.value = [...selectedCouponIds.value, id]
  errorMessage.value = ''
}

async function loadAvailableCoupons(): Promise<void> {
  try {
    const page = await loadMyCoupons('AVAILABLE', 1, 50)
    availableCoupons.value = page.items
  } catch {
    // 券列表失败不阻塞报价；用户仍可使用自动推荐或不用券。
    availableCoupons.value = []
  }
}

async function waitUntilReady(quoteId: string): Promise<TradeQuote> {
  calculating.value = true
  for (let attempt = 0; attempt < 20; attempt++) {
    await new Promise(resolve => setTimeout(resolve, 500))
    const current = await loadQuote(quoteId)
    if (current.status !== 'CALCULATING') {
      calculating.value = false
      return current
    }
  }
  calculating.value = false
  throw new Error('优惠计算超时，请重新报价。')
}

async function loadQuoteFor(mode: CouponMode, ids: string[], resetKey = true): Promise<void> {
  if (!productId.value) {
    errorMessage.value = '缺少商品 ID，无法创建确认单。'
    loading.value = false
    return
  }
  loading.value = true
  errorMessage.value = ''
  notice.value = ''
  routeDetail.value = null
  try {
    let next = await createQuote(productType.value, productId.value, mode, ids)
    if (next.status === 'CALCULATING') {
      next = await waitUntilReady(next.quoteId)
    }
    if (next.status === 'FAILED') {
      throw new Error('优惠方案不可行，请调整所选优惠券。')
    }
    quote.value = next
    couponMode.value = mode
    selectedCouponIds.value = next.couponPlan.map(item => item.userCouponId)
    if (resetKey) idempotencyKey.value = freshKey()
    if (isRoute.value) {
      try {
        routeDetail.value = await loadRoute(productId.value)
      } catch {
        // 报价已成功时，路线标题仍可从报价展示，不阻塞确认。
      }
    }
  } catch (error) {
    // 手选方案不可行时保留上一份可用报价，学生可以调整券组合后再计算。
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

function chooseMode(mode: CouponMode): void {
  if (mode === 'MANUAL') {
    couponMode.value = 'MANUAL'
    errorMessage.value = ''
    return
  }
  selectedCouponIds.value = []
  void loadQuoteFor(mode, [])
}

function calculateManual(): void {
  if (selectedCouponIds.value.length === 0) {
    void loadQuoteFor('NONE', [])
    return
  }
  void loadQuoteFor('MANUAL', [...selectedCouponIds.value])
}

function refreshClock(): void {
  nowMs.value = Date.now()
}

async function confirmOrder(): Promise<void> {
  if (!quote.value || !canConfirm.value) return
  submitting.value = true
  errorMessage.value = ''
  notice.value = ''
  const couponIds = quote.value.couponPlan.map(item => item.userCouponId)
  try {
    const order = await createOrder(quote.value.quoteId, quote.value.quoteHash, idempotencyKey.value, couponIds)
    await router.push({ name: 'order-detail', params: { id: order.id } })
  } catch (error) {
    const code = (error as { response?: { data?: { code?: string } } }).response?.data?.code
    const message = describeTradeError(error)
    if (code === 'QUOTE_CHANGED' || code === 'COUPON_UNAVAILABLE' || code === 'COUPON_COMBINATION_INVALID') {
      await loadAvailableCoupons()
      await loadQuoteFor(couponMode.value, selectedCouponIds.value)
      notice.value = '优惠券或价格已变化，已重新获取报价，请重新确认。'
    } else {
      errorMessage.value = message
    }
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadAvailableCoupons()
  await loadQuoteFor('AUTO', [])
  timer = setInterval(refreshClock, 1000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <AppShell>
    <p class="back">
      <RouterLink :to="isRoute ? `/routes/${productId}` : `/courses/${productId}`">
        ← 返回{{ isRoute ? '路线详情' : '课程详情' }}
      </RouterLink>
    </p>

    <section class="head">
      <p class="stage">确认订单</p>
      <h1>确认{{ isRoute ? '路线' : '课程' }}购买</h1>
      <p class="lead">
        金额来自服务端报价；已有课程按套餐分摊价抵扣。默认展示全局最优券组合，可切换不用券或手选最多 3 张；
        下单时优惠券一次性锁定，未支付取消会释放并保留原有效期。
      </p>
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
            <p class="muted">
              {{ isRoute ? '路线' : '课程' }} ID {{ quote.product.id }} · 版本 {{ quote.product.version }}
            </p>
          </div>
          <span class="price">{{ formatCent(quote.payableCent) }}</span>
        </div>

        <section v-if="isRoute" class="route-lines">
          <h3>课程抵扣明细</h3>
          <div v-for="line in quote.courses" :key="line.courseId" class="line">
            <span class="line-title">{{ courseTitle(String(line.courseId)) }}</span>
            <span>套餐分摊 {{ formatCent(line.allocatedCent) }}</span>
            <span v-if="line.creditCent > 0" class="credit">已抵扣 {{ formatCent(line.creditCent) }}</span>
            <span v-else class="new">本次应付 {{ formatCent(line.remainingCent) }}</span>
          </div>
          <p class="fine-print">
            已抵扣课程不新增路线来源；抵扣来源由服务端按最早有效课程权益选择。
          </p>
        </section>

        <section class="coupon-panel">
          <div class="coupon-head">
            <h3>优惠券</h3>
            <span class="muted">{{ couponPlanText(quote.couponPlan) }}</span>
          </div>
          <div class="mode-row">
            <button class="mode" :class="{ active: couponMode === 'AUTO' }" type="button" :disabled="loading" @click="chooseMode('AUTO')">
              推荐最优
            </button>
            <button class="mode" :class="{ active: couponMode === 'MANUAL' }" type="button" :disabled="loading" @click="chooseMode('MANUAL')">
              手选优惠券
            </button>
            <button class="mode" :class="{ active: couponMode === 'NONE' }" type="button" :disabled="loading" @click="chooseMode('NONE')">
              不使用优惠券
            </button>
          </div>

          <div v-if="couponMode === 'MANUAL'" class="manual-list">
            <label v-for="coupon in availableCoupons" :key="coupon.id" class="coupon-option">
              <input
                type="checkbox"
                :checked="selectedCouponIds.includes(coupon.id)"
                :disabled="loading"
                @change="toggleCoupon(coupon.id)"
              />
              <span>{{ couponTypeLabel(coupon) }} · {{ couponScopeLabel(coupon) }}</span>
              <span class="muted">有效期至 {{ coupon.validUntil.slice(0, 10) }}</span>
            </label>
            <p v-if="availableCoupons.length === 0" class="muted">暂无可用券。</p>
            <button class="btn" type="button" :disabled="loading" @click="calculateManual">
              用所选券重新计算
            </button>
          </div>

          <p v-if="calculating" class="muted">正在计算最优优惠…</p>
          <div v-if="quote.couponPlan.length > 0" class="coupon-lines">
            <div v-for="line in quote.couponPlan" :key="line.userCouponId" class="coupon-line">
              <span>第 {{ line.applyOrder }} 张 · 券 #{{ line.userCouponId }}</span>
              <span class="muted">券前基数 {{ formatCent(line.baseCent) }}</span>
              <span class="discount">-{{ formatCent(line.discountCent) }}</span>
            </div>
          </div>
          <p v-else class="muted">当前方案未使用优惠券。</p>
          <p class="fine-print">
            下单时服务端会重新校验券归属、有效期和报价指纹，并按券 ID 顺序一次性锁定；优惠为 0 的券不会进入方案。
          </p>
        </section>

        <dl class="amounts">
          <div><dt>商品金额</dt><dd>{{ formatCent(quote.totalCent) }}</dd></div>
          <div><dt>抵扣</dt><dd>{{ formatCent(quote.creditCent) }}</dd></div>
          <div><dt>优惠</dt><dd>{{ formatCent(quote.discountCent) }}</dd></div>
          <div class="total"><dt>应付</dt><dd>{{ formatCent(quote.payableCent) }}</dd></div>
        </dl>

        <section v-if="quote.refundDependencyWarnings.length > 0" class="warnings">
          <h3>退款依赖提示</h3>
          <ul>
            <li v-for="warning in quote.refundDependencyWarnings" :key="warning">{{ warning }}</li>
          </ul>
          <p>以上为确认前提示；正式退款判定以受理时点的来源和依赖快照为准。</p>
        </section>

        <p class="expiry" :class="{ expired }">
          <template v-if="expired">报价已过期，请重新获取。</template>
          <template v-else>报价剩余 {{ formatCountdown(secondsLeft) }}，过期后需要重新确认。</template>
        </p>

        <div class="actions">
          <button class="btn btn-primary" type="button" :disabled="!canConfirm" @click="confirmOrder">
            {{ submitting ? '创建中…' : calculating ? '计算中…' : '确认并创建订单' }}
          </button>
          <button class="btn" type="button" :disabled="submitting" @click="loadQuoteFor(couponMode, selectedCouponIds)">
            重新报价
          </button>
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

.route-lines {
  margin-top: 24px;
  padding: 14px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.route-lines h3,
.warnings h3 {
  margin: 0 0 10px;
  font-family: var(--serif);
  font-size: 19px;
  font-weight: 500;
}

.line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 14px;
  align-items: baseline;
  padding: 7px 0;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}

.line:last-of-type {
  border-bottom: 0;
}

.line-title {
  overflow: hidden;
  color: var(--ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.credit {
  color: var(--pine);
}

.new {
  color: var(--amber);
}

.coupon-panel {
  margin-top: 24px;
  padding: 16px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.coupon-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.coupon-head h3 {
  margin: 0;
  font-family: var(--serif);
  font-size: 19px;
  font-weight: 500;
}

.mode-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.mode {
  padding: 6px 12px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}

.mode.active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--paper);
}

.manual-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.coupon-option {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  font-size: 13px;
  cursor: pointer;
}

.coupon-option .muted {
  margin-left: auto;
  font-size: 12px;
}

.coupon-lines {
  display: grid;
  gap: 6px;
  margin-top: 12px;
}

.coupon-line {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 10px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--line);
  font-size: 13px;
}

.coupon-line .discount {
  color: var(--pine);
  font-variant-numeric: tabular-nums;
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

.warnings {
  margin-top: 18px;
  padding: 12px 14px;
  border-left: 2px solid var(--amber);
  background: rgba(164, 98, 42, 0.05);
  color: var(--amber);
}

.warnings ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
}

.warnings p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 12px;
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
  line-height: 1.7;
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

  .line {
    grid-template-columns: 1fr auto;
  }

  .line-title {
    grid-column: 1 / -1;
  }
}
</style>