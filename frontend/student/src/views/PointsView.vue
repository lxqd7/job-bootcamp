<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { loadPointsLedger, loadPointsSummary } from '../api/client'
import type { PointsLedgerItem, PointsSummary } from '../api/types'
import { describeAuthError } from '../auth/session'

const summary = ref<PointsSummary | null>(null)
const items = ref<PointsLedgerItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(true)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const typeLabels: Record<string, string> = {
  VIDEO: '视频学习',
  SIGN: '每日签到',
  EXCELLENT: '优秀笔记',
  REVOKE: '奖励撤回',
  EXCHANGE: '积分兑换'
}

function typeLabel(type: string): string {
  return typeLabels[type] ?? type
}

function reasonText(item: PointsLedgerItem): string {
  if (item.resultCode === 'SKIPPED_DAILY_CAP') {
    return '达到每日 50 分上限，未发分'
  }
  if (item.type === 'REVOKE') {
    const debt = item.debtDelta > 0 ? `，记待扣回 ${item.debtDelta} 分` : ''
    return `违规撤回 ${Math.abs(item.rewardPoints)} 分：扣余额 ${Math.abs(item.balanceDelta)} 分${debt}；原月榜 ${item.rankDelta}`
  }
  if (item.debtDelta < 0) {
    return `奖励 ${item.rewardPoints} 分，先抵扣待扣回 ${-item.debtDelta} 分`
  }
  if (item.rewardPoints === 0) {
    return '本次未增加积分'
  }
  if (item.rankDelta > 0) {
    return `月榜 +${item.rankDelta}`
  }
  return '—'
}

function deltaText(item: PointsLedgerItem): string {
  if (item.balanceDelta > 0) return `+${item.balanceDelta}`
  return String(item.balanceDelta)
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [summaryResult, ledgerResult] = await Promise.all([
      loadPointsSummary(),
      loadPointsLedger(page.value, pageSize)
    ])
    summary.value = summaryResult
    items.value = ledgerResult.items
    total.value = ledgerResult.total
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function gotoPage(next: number): Promise<void> {
  if (next < 1 || next > totalPages.value) return
  page.value = next
  await load()
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">积分账本</p>
      <h1>我的积分</h1>
      <p class="lead">
        可用余额、待扣回和月榜得分分开记录。兑换不影响月榜；新积分会优先抵扣待扣回部分。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <section v-if="summary" class="stats" aria-label="积分概况">
      <div class="stat">
        <span>可用积分</span>
        <strong>{{ summary.available }}</strong>
      </div>
      <div class="stat" :class="{ warn: summary.debt > 0 }">
        <span>待扣回</span>
        <strong>{{ summary.debt }}</strong>
      </div>
      <div class="stat">
        <span>本月月榜得分</span>
        <strong>{{ summary.currentMonthScore }}</strong>
      </div>
    </section>

    <p v-if="summary && summary.debt > 0" class="debt-note">
      待扣回积分来自违规奖励撤回；后续获得积分会优先抵扣，余额不会为负。
    </p>

    <div class="actions">
      <RouterLink class="btn btn-primary" to="/sign-in">去签到</RouterLink>
      <RouterLink class="btn" to="/boards">看月榜</RouterLink>
    </div>

    <section class="ledger" aria-live="polite">
      <div class="ledger-head" aria-hidden="true">
        <span>日期</span>
        <span>类型</span>
        <span>积分变动</span>
        <span>余额</span>
        <span>说明</span>
      </div>
      <div v-for="item in items" :key="item.id" class="ledger-row">
        <span class="date">{{ item.businessDate }}</span>
        <span class="type">{{ typeLabel(item.type) }}</span>
        <span class="delta" :class="{ plus: item.balanceDelta > 0, zero: item.balanceDelta === 0 }">
          {{ deltaText(item) }}
        </span>
        <span class="balance">{{ item.balanceAfter }}</span>
        <span class="reason">{{ reasonText(item) }}</span>
      </div>
      <p v-if="!loading && items.length === 0" class="empty">还没有积分记录，签到或完成视频学习后会出现在这里。</p>
      <p v-if="loading" class="empty">正在读取积分流水…</p>
    </section>

    <div class="pager">
      <span class="muted small">共 {{ total }} 条 · 第 {{ page }} / {{ totalPages }} 页</span>
      <div class="pager-actions">
        <button class="btn small-btn" type="button" :disabled="page <= 1 || loading" @click="gotoPage(page - 1)">
          上一页
        </button>
        <button class="btn small-btn" type="button" :disabled="page >= totalPages || loading" @click="gotoPage(page + 1)">
          下一页
        </button>
      </div>
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
  max-width: 62ch;
  color: var(--muted);
  line-height: 1.75;
}

.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 32px;
  border-top: 2px solid var(--ink);
  border-bottom: 1px solid var(--line);
}

.stat {
  padding: 16px 16px 16px 0;
  border-right: 1px solid var(--line);
}

.stat:last-child {
  border-right: 0;
}

.stat span {
  display: block;
  color: var(--muted);
  font-size: 12px;
}

.stat strong {
  display: block;
  margin-top: 4px;
  font-family: var(--serif);
  font-size: 30px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.stat.warn strong {
  color: var(--amber);
}

.debt-note {
  margin: 14px 0 0;
  padding-left: 14px;
  border-left: 2px solid var(--amber);
  color: var(--amber);
  font-size: 14px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.ledger {
  margin-top: 34px;
  border-top: 1px solid var(--line);
}

.ledger-head,
.ledger-row {
  display: grid;
  grid-template-columns: 110px 100px 90px 70px minmax(0, 1fr);
  gap: 12px;
  align-items: baseline;
}

.ledger-head {
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
}

.ledger-row {
  padding: 13px 0;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}

.date,
.balance {
  font-variant-numeric: tabular-nums;
}

.type {
  color: var(--muted);
}

.delta {
  font-variant-numeric: tabular-nums;
  color: var(--bad);
}

.delta.plus {
  color: var(--pine);
  font-weight: 600;
}

.delta.zero {
  color: var(--muted);
}

.reason {
  color: var(--muted);
  font-size: 13px;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}

.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 20px;
}

.pager-actions {
  display: flex;
  gap: 8px;
}

.small-btn {
  padding: 5px 12px;
  font-size: 13px;
}

@media (max-width: 640px) {
  .stats {
    grid-template-columns: 1fr;
  }

  .stat {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .ledger-head {
    display: none;
  }

  .ledger-row {
    grid-template-columns: 1fr auto;
    gap: 4px 12px;
  }

  .ledger-row .date,
  .ledger-row .type {
    grid-column: 1;
  }

  .ledger-row .delta,
  .ledger-row .balance {
    grid-column: 2;
    text-align: right;
  }

  .ledger-row .reason {
    grid-column: 1 / -1;
  }
}
</style>