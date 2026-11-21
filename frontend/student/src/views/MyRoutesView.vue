<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { describeError, loadMyRoutes, loadRoute } from '../api/client'
import type { MyRouteItem, RouteDetail } from '../api/types'

interface RouteCard {
  item: MyRouteItem
  detail: RouteDetail | null
}

const cards = ref<RouteCard[]>([])
const loading = ref(true)
const errorMessage = ref('')

function money(value: number): string {
  return `¥${(value / 100).toFixed(2)}`
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadMyRoutes(1, 100)
    cards.value = await Promise.all(result.items.map(async item => {
      let detail: RouteDetail | null = null
      try {
        detail = await loadRoute(item.routeId)
      } catch {
        // 路线下架时仍展示历史资格
      }
      return { item, detail }
    }))
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">路线权益</p>
      <h1>我的路线</h1>
      <p class="lead">
        这里展示通过路线购买获得的路线资格。路线资格还包含未来新增课程权益；已有课程仍保留原学习来源。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="loading" class="empty">正在读取路线资格…</p>

    <section v-else class="cards" aria-live="polite">
      <RouterLink
        v-for="card in cards"
        :key="card.item.routeId"
        class="card"
        :to="`/routes/${card.item.routeId}`"
      >
        <div class="card-main">
          <div>
            <p class="stage">路线 ID {{ card.item.routeId }}</p>
            <h2>{{ card.detail?.title ?? `路线 ${card.item.routeId}` }}</h2>
            <p class="meta">
              获得时间 {{ card.item.acquiredAt.replace('T', ' ').slice(0, 19) }}
            </p>
          </div>
          <span class="price">{{ card.detail ? money(card.detail.priceCent) : '—' }}</span>
        </div>
        <p class="future">
          {{ card.item.futureCourses ? '包含后续新增课程权益' : '不包含后续新增课程' }}
        </p>
        <p class="hint">查看路线 →</p>
      </RouterLink>
      <p v-if="cards.length === 0" class="empty">
        还没有路线资格。可以到 <RouterLink to="/routes">路线列表</RouterLink> 查看可购买路线。
      </p>
    </section>
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
  max-width: 64ch;
  color: var(--muted);
  line-height: 1.75;
}

.cards {
  margin-top: 32px;
  border-top: 2px solid var(--ink);
}

.card {
  display: block;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  text-decoration: none;
}

.card-main {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 18px;
}

.card h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: 24px;
  font-weight: 500;
}

.meta {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.price {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  white-space: nowrap;
}

.future {
  margin: 12px 0 0;
  color: var(--pine);
  font-size: 13px;
}

.hint {
  margin: 8px 0 0;
  color: var(--pine);
  font-size: 13px;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}
</style>