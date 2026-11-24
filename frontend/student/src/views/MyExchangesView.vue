<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeTradeError, loadMyExchanges } from '../api/client'
import type { ExchangeRecordView } from '../api/types'
import { couponStatusLabels, describeCouponRule, formatTimestamp } from '../coupons/format'

const records = ref<ExchangeRecordView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(true)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadMyExchanges(page.value, pageSize)
    records.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
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
      <p class="stage">积分兑换记录</p>
      <h1>我的兑换</h1>
      <p class="lead">
        每次兑换保存当时的项目版本、消耗积分和券快照。券过期或订单退款不返还积分，也不影响已记录的历史。
      </p>
      <p class="links">
        <RouterLink to="/exchange">去兑换</RouterLink>
        <RouterLink to="/me/coupons">我的券</RouterLink>
        <RouterLink to="/me/points">积分明细</RouterLink>
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="loading" class="empty">正在读取兑换记录…</p>
    <template v-else>
      <p v-if="records.length === 0" class="empty">
        还没有兑换记录。可以到 <RouterLink to="/exchange">积分兑换</RouterLink> 选择项目。
      </p>
      <article v-for="record in records" :key="record.exchangeId" class="record">
        <header>
          <div>
            <h2>{{ record.projectName }}</h2>
            <p class="meta">
              {{ formatTimestamp(record.createdAt) }} · 项目版本 v{{ record.projectRevision }} ·
              记录 #{{ record.exchangeId }}
            </p>
          </div>
          <strong class="points">-{{ record.pointsSpent }} 分</strong>
        </header>
        <p class="meta">
          {{ record.rule ? describeCouponRule(record.rule) : '券规则不可用' }} ·
          券 #{{ record.userCouponId }} ·
          {{ couponStatusLabels[record.couponStatus ?? ''] ?? record.couponStatus ?? '状态未知' }}
        </p>
        <p class="meta">
          有效期 {{ formatTimestamp(record.validFrom) }} 至 {{ formatTimestamp(record.validUntil) }} ·
          兑换后余额 {{ record.balanceAfter }} 分
        </p>
        <p class="actions"><RouterLink to="/me/coupons">在“我的券”查看</RouterLink></p>
      </article>
    </template>

    <div v-if="totalPages > 1" class="pager">
      <button class="btn small-btn" type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
    </div>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 10px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(34px, 5vw, 48px); font-weight: 500; }
h2 { margin: 0; font-family: var(--serif); font-size: 21px; font-weight: 500; }
.lead { max-width: 72ch; color: var(--muted); line-height: 1.8; }
.links { display: flex; flex-wrap: wrap; gap: 16px; font-size: 13px; }
.record { padding: 16px 0; border-bottom: 1px solid var(--line); }
.record header { display: flex; gap: 16px; justify-content: space-between; align-items: start; }
.meta { color: var(--muted); font-size: 12px; line-height: 1.8; }
.points { color: var(--amber); white-space: nowrap; }
.actions { margin: 8px 0 0; font-size: 13px; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.pager { display: flex; gap: 12px; align-items: center; margin-top: 16px; color: var(--muted); font-size: 13px; }
.empty { padding: 20px 0; color: var(--muted); }
</style>