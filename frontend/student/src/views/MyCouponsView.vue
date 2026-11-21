<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeTradeError, loadMyCoupons } from '../api/client'
import type { UserCouponView } from '../api/types'
import {
  couponStatusLabels,
  describeCouponRule,
  describeCouponScope,
  formatTimestamp
} from '../coupons/format'

const coupons = ref<UserCouponView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const displayStatus = ref('')
const loading = ref(true)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const filters = [
  { value: '', label: '全部' },
  { value: 'AVAILABLE', label: '可用' },
  { value: 'NOT_STARTED', label: '未生效' },
  { value: 'LOCKED', label: '已锁定' },
  { value: 'USED', label: '已使用' },
  { value: 'EXPIRED', label: '已过期' }
]
const sourceLabels: Record<string, string> = {
  ACTIVITY: '活动领取',
  EXCHANGE: '积分兑换'
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadMyCoupons(displayStatus.value, page.value, pageSize)
    coupons.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

function changeFilter(): void {
  page.value = 1
  void load()
}

function changePage(next: number): void {
  page.value = Math.min(totalPages.value, Math.max(1, next))
  void load()
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">优惠券</p>
      <h1>我的券</h1>
      <p class="lead">
        展示已领取的优惠券及其来源、有效期和当前状态。券的锁定、使用与结算明细将在 S08-b/c 接入。
      </p>
    </section>

    <div class="toolbar">
      <label class="filter">
        <span>状态</span>
        <select v-model="displayStatus" @change="changeFilter">
          <option v-for="item in filters" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </label>
      <button class="btn small-btn" type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="loading" class="empty">正在读取优惠券…</p>

    <section v-else class="coupons" aria-live="polite">
      <article v-for="coupon in coupons" :key="coupon.id" class="coupon">
        <div class="coupon-main">
          <div class="title-row">
            <h2>{{ coupon.rule ? describeCouponRule(coupon.rule) : '券规则不可用' }}</h2>
            <span class="status" :class="`status-${coupon.displayStatus.toLowerCase()}`">
              {{ couponStatusLabels[coupon.displayStatus] ?? coupon.displayStatus }}
            </span>
          </div>
          <p class="meta">
            {{ coupon.rule ? describeCouponScope(coupon.rule) : '' }} ·
            {{ sourceLabels[coupon.sourceType] ?? coupon.sourceType }}
          </p>
          <p class="meta">有效期 {{ formatTimestamp(coupon.validFrom) }} 至 {{ formatTimestamp(coupon.validUntil) }}</p>
        </div>
        <span class="coupon-id">#{{ coupon.id }}</span>
      </article>
      <p v-if="coupons.length === 0" class="empty">
        还没有符合条件的券。可以到 <RouterLink to="/activities">领券活动</RouterLink> 查看。
      </p>
    </section>

    <div v-if="totalPages > 1" class="pager">
      <button class="btn small-btn" type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
    </div>
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
  font-size: clamp(34px, 5vw, 48px);
  font-weight: 500;
}

.lead {
  max-width: 66ch;
  color: var(--muted);
  line-height: 1.75;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 14px;
  margin-top: 28px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--ink);
}

.filter {
  display: grid;
  gap: 4px;
  color: var(--muted);
  font-size: 12px;
}

.filter select {
  padding: 5px 9px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
}

.coupons {
  border-bottom: 1px solid var(--line);
}

.coupon {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 0;
  border-bottom: 1px solid var(--line);
}

.title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
}

.coupon h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: 21px;
  font-weight: 500;
}

.status {
  padding: 1px 7px;
  border: 1px solid var(--line);
  color: var(--muted);
  font-size: 11px;
}

.status-available {
  border-color: var(--pine);
  color: var(--pine);
}

.status-locked {
  border-color: var(--amber);
  color: var(--amber);
}

.status-expired,
.status-used {
  color: var(--muted);
}

.meta {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.coupon-id {
  color: var(--muted);
  font-family: var(--mono);
  font-size: 11px;
  white-space: nowrap;
}

.small-btn {
  padding: 5px 12px;
  font-size: 13px;
}

.pager {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 18px;
  color: var(--muted);
  font-size: 13px;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}
</style>