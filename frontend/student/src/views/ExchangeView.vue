<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeTradeError, exchangePoints, loadExchangeCatalog, loadPointsSummary } from '../api/client'
import type { ExchangeProjectStudentView, PointsSummary } from '../api/types'
import { describeCouponRule, describeCouponScope, formatTimestamp } from '../coupons/format'

const projects = ref<ExchangeProjectStudentView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const summary = ref<PointsSummary | null>(null)
const loading = ref(true)
const busy = ref(false)
const errorMessage = ref('')
const notice = ref('')
const selected = ref<ExchangeProjectStudentView | null>(null)
const result = ref<{ exchangeId: string; userCouponId: string; balance: number; pointsSpent: number } | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const balanceAfter = computed(() => {
  if (!summary.value || !selected.value) return 0
  return Math.max(0, summary.value.available - selected.value.pointsPrice)
})

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [catalog, points] = await Promise.all([
      loadExchangeCatalog(page.value, pageSize),
      loadPointsSummary()
    ])
    projects.value = catalog.items
    total.value = catalog.total
    summary.value = points
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

function select(project: ExchangeProjectStudentView): void {
  selected.value = project
  result.value = null
  errorMessage.value = ''
  notice.value = ''
}

async function confirmExchange(): Promise<void> {
  if (!selected.value || busy.value) return
  busy.value = true
  errorMessage.value = ''
  try {
    const key = `exchange-${selected.value.id}-${crypto.randomUUID()}`
    const done = await exchangePoints(selected.value.id, selected.value.revision, key)
    result.value = {
      exchangeId: done.exchangeId,
      userCouponId: done.userCouponId,
      balance: done.balance,
      pointsSpent: done.pointsSpent
    }
    notice.value = '兑换成功，券已发放到“我的券”。'
    selected.value = null
    await load()
  } catch (error) {
    const message = describeTradeError(error)
    // 配置变化、库存或限兑变化后刷新列表，让用户基于最新状态重新确认。
    await load()
    errorMessage.value = message
  } finally {
    busy.value = false
  }
}

function reason(project: ExchangeProjectStudentView): string {
  if (project.userExchangedCount >= project.perUserLimit) return '已达个人兑换上限'
  if (project.stockRemaining <= 0) return '库存不足'
  if (!project.canExchange) return '当前不在开放时间'
  return ''
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
      <p class="stage">常驻积分兑换</p>
      <h1>用积分换券</h1>
      <p class="lead">
        兑换会在同一事务内扣除可用积分并发放优惠券；兑换不影响月榜。配置、库存或限兑发生变化时需要重新确认。
      </p>
      <p class="links">
        <RouterLink to="/me/exchanges">兑换记录</RouterLink>
        <RouterLink to="/me/coupons">我的券</RouterLink>
        <RouterLink to="/me/points">积分明细</RouterLink>
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>

    <section v-if="summary" class="stats">
      <div class="stat"><span>可用积分</span><strong>{{ summary.available }}</strong></div>
      <div class="stat" :class="{ warn: summary.debt > 0 }"><span>待扣回</span><strong>{{ summary.debt }}</strong></div>
      <div class="stat"><span>本月榜分</span><strong>{{ summary.currentMonthScore }}</strong></div>
    </section>

    <section v-if="result" class="result" role="status">
      <h2>兑换完成</h2>
      <p>消耗 {{ result.pointsSpent }} 分，剩余 {{ result.balance }} 分；兑换记录 #{{ result.exchangeId }}，券 #{{ result.userCouponId }}。</p>
      <RouterLink class="btn small-btn" to="/me/coupons">查看我的券</RouterLink>
      <RouterLink class="btn small-btn" to="/me/exchanges">查看兑换记录</RouterLink>
    </section>

    <section v-if="selected" class="confirm panel">
      <h2>确认兑换</h2>
      <p>{{ selected.name }} · {{ describeCouponRule(selected.rule) }}</p>
      <dl>
        <div><dt>消耗积分</dt><dd>{{ selected.pointsPrice }} 分</dd></div>
        <div><dt>当前可用</dt><dd>{{ summary?.available ?? 0 }} 分</dd></div>
        <div><dt>兑换后余额</dt><dd>{{ balanceAfter }} 分</dd></div>
        <div><dt>券有效期</dt><dd>{{ selected.rule?.validityType === 'DAYS' ? `取得后 ${selected.rule.validDays} 天` : `${formatTimestamp(selected.rule?.validFrom ?? null)} 至 ${formatTimestamp(selected.rule?.validUntil ?? null)}` }}</dd></div>
      </dl>
      <p class="warning">兑换成功后积分立即扣除，券不支持主动撤销；请确认后再提交。</p>
      <div class="actions">
        <button class="btn btn-primary" type="button" :disabled="busy" @click="confirmExchange">
          {{ busy ? '兑换中…' : '确认扣分并兑换' }}
        </button>
        <button class="btn" type="button" :disabled="busy" @click="selected = null">取消</button>
      </div>
    </section>

    <p v-if="loading" class="empty">正在读取兑换项目…</p>
    <section v-else class="projects">
      <p v-if="projects.length === 0" class="empty">当前没有上架的兑换项目。</p>
      <article v-for="project in projects" :key="project.id" class="project">
        <div class="project-head">
          <div>
            <h2>{{ project.name }}</h2>
            <p class="meta">{{ describeCouponRule(project.rule) }} · {{ describeCouponScope(project.rule) }}</p>
          </div>
          <span class="price">{{ project.pointsPrice }} 分</span>
        </div>
        <div class="project-meta">
          <span>剩余库存 {{ project.stockRemaining }}</span>
          <span>已兑 {{ project.userExchangedCount }} / {{ project.perUserLimit }}</span>
          <span>{{ formatTimestamp(project.openAt) }} 至 {{ formatTimestamp(project.closeAt) }}</span>
        </div>
        <div class="project-actions">
          <button
            class="btn btn-primary small-btn"
            type="button"
            :disabled="!project.canExchange"
            @click="select(project)"
          >
            {{ project.canExchange ? '选择兑换' : reason(project) }}
          </button>
          <span v-if="summary && summary.available < project.pointsPrice" class="muted">可用积分不足</span>
        </div>
      </article>
    </section>

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
.stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin: 24px 0; }
.stat { padding: 14px 16px; border: 1px solid var(--line); }
.stat span { display: block; color: var(--muted); font-size: 12px; }
.stat strong { font-family: var(--serif); font-size: 28px; font-weight: 500; }
.stat.warn strong { color: var(--amber); }
.result, .confirm { margin: 20px 0; padding: 18px; border: 1px solid var(--pine); background: var(--paper-soft, #f6f4ee); }
.panel { border-top: 2px solid var(--ink); }
.confirm dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin: 14px 0; }
.confirm dt { color: var(--muted); font-size: 12px; }
.confirm dd { margin: 4px 0 0; font-size: 16px; }
.warning { color: var(--amber); font-size: 13px; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 12px; }
.projects { margin-top: 20px; }
.project { padding: 16px 0; border-bottom: 1px solid var(--line); }
.project-head { display: flex; gap: 16px; justify-content: space-between; align-items: start; }
.meta, .muted { color: var(--muted); font-size: 12px; }
.price { font-family: var(--serif); font-size: 24px; white-space: nowrap; }
.project-meta { display: flex; flex-wrap: wrap; gap: 16px; margin: 10px 0; color: var(--muted); font-size: 12px; }
.project-actions { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.pager { display: flex; gap: 12px; align-items: center; margin-top: 16px; color: var(--muted); font-size: 13px; }
.empty { padding: 20px 0; color: var(--muted); }
</style>