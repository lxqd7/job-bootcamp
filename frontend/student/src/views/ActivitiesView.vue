<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { claimCoupon, describeTradeError, loadActivities } from '../api/client'
import type { ActivityView } from '../api/types'
import { describeCouponRule, describeCouponScope, describeCouponValidity, formatTimestamp } from '../coupons/format'

const activities = ref<ActivityView[]>([])
const loading = ref(true)
const claimingItem = ref('')
const errorMessage = ref('')
const notice = ref('')

function newKey(): string {
  const cryptoApi = globalThis.crypto
  return cryptoApi && 'randomUUID' in cryptoApi
    ? cryptoApi.randomUUID()
    : `coupon-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadActivities(1, 20)
    activities.value = result.items
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

async function claim(itemId: string): Promise<void> {
  if (claimingItem.value) return
  claimingItem.value = itemId
  errorMessage.value = ''
  notice.value = ''
  try {
    const coupon = await claimCoupon(itemId, newKey())
    notice.value = `领券成功，可到“我的券”查看。券有效期至 ${formatTimestamp(coupon.validUntil)}。`
    await load()
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    claimingItem.value = ''
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">优惠活动</p>
      <h1>领券活动</h1>
      <p class="lead">
        活动券需在开放期内领取；同一活动同一券种有个人限领和库存限制。领取成功后会进入“我的券”，可用于后续结算。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="loading" class="empty">正在读取活动…</p>

    <section v-else class="activities" aria-live="polite">
      <article v-for="activity in activities" :key="activity.id" class="activity">
        <div class="activity-head">
          <div>
            <p class="stage">活动 {{ activity.id }}</p>
            <h2>{{ activity.name }}</h2>
            <p class="meta">
              开放至 {{ formatTimestamp(activity.closeAt) }} · 状态 {{ activity.state }}
            </p>
          </div>
          <span class="badge">活动进行中</span>
        </div>

        <div class="items">
          <div v-for="item in activity.items" :key="item.id" class="item">
            <div class="item-main">
              <strong>{{ item.rule ? describeCouponRule(item.rule) : '券规则不可用' }}</strong>
              <span class="scope">{{ describeCouponScope(item.rule) }}</span>
              <span class="validity">{{ describeCouponValidity(item.rule) }}</span>
            </div>
            <div class="item-side">
              <span class="stock">剩余 {{ Math.max(0, item.stockTotal - item.issuedCount) }} 张</span>
              <span class="limit">每人限领 {{ item.perUserLimit }} 张</span>
              <button
                class="btn small-btn"
                type="button"
                :disabled="Boolean(claimingItem) || item.stockTotal <= item.issuedCount || !item.enabled"
                @click="claim(item.id)"
              >
                {{ claimingItem === item.id ? '领取中…' : '领取' }}
              </button>
            </div>
          </div>
        </div>
      </article>
      <p v-if="activities.length === 0" class="empty">
        当前没有开放中的领券活动。可以到
        <RouterLink to="/me/coupons">我的券</RouterLink>
        查看已有券。
      </p>
    </section>

    <p class="fine-print">
      活动免费领券只发放优惠券，不授予课程或路线权益；券使用和结算选择将在 S08-b/c 接入。
    </p>
  </AppShell>
</template>

<style scoped>
.stage {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 12px;
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

.activities {
  margin-top: 30px;
  border-top: 2px solid var(--ink);
}

.activity {
  padding: 20px 0;
  border-bottom: 1px solid var(--line);
}

.activity-head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}

.activity h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: 25px;
  font-weight: 500;
}

.meta {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.badge {
  padding: 2px 8px;
  border: 1px solid var(--pine);
  color: var(--pine);
  font-size: 12px;
  white-space: nowrap;
}

.items {
  margin-top: 14px;
}

.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-top: 1px solid var(--line);
}

.item-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.item-main strong {
  font-size: 14px;
}

.scope,
.validity,
.stock,
.limit {
  color: var(--muted);
  font-size: 12px;
}

.item-side {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  white-space: nowrap;
}

.small-btn {
  padding: 5px 12px;
  font-size: 13px;
}

.notice {
  color: var(--pine);
  font-size: 14px;
}

.fine-print {
  margin-top: 20px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}

@media (max-width: 700px) {
  .item {
    display: block;
  }

  .item-side {
    justify-content: flex-start;
    margin-top: 8px;
  }
}
</style>