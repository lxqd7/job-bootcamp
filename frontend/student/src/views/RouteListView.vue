<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { describeError, loadRoutes } from '../api/client'
import type { RouteSummary } from '../api/types'

const routes = ref<RouteSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const loading = ref(false)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function priceText(route: RouteSummary): string {
  return `¥${(route.priceCent / 100).toFixed(2)}`
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadRoutes(page.value, pageSize)
    routes.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

async function gotoPage(next: number): Promise<void> {
  if (next < 1 || next > totalPages.value) {
    return
  }
  page.value = next
  await load()
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">路线套餐</p>
      <h1>学习路线</h1>
      <p class="lead">路线由多门课程组成，价格低于单课之和。已有课程会在确认页按套餐分摊价自动抵扣；购买后获得路线资格和后续新增课程权益。</p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <section class="route-list" aria-live="polite">
      <RouterLink v-for="route in routes" :key="route.id" class="route" :to="`/routes/${route.id}`">
        <div class="route-top">
          <h2>{{ route.title }}</h2>
          <span class="price">{{ priceText(route) }}</span>
        </div>
        <p class="meta">{{ route.memberCount }} 门课程 · 路线 ID {{ route.id }}</p>
        <p class="hint">查看路线 →</p>
      </RouterLink>
      <p v-if="!loading && routes.length === 0" class="empty">还没有已上架路线。</p>
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
  max-width: 58ch;
  color: var(--muted);
  line-height: 1.75;
}

.route-list {
  margin-top: 34px;
  border-top: 2px solid var(--ink);
}

.route {
  display: block;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  text-decoration: none;
}

.route-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.route h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.price {
  font-variant-numeric: tabular-nums;
}

.meta {
  margin: 6px 0 0;
  color: var(--muted);
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
</style>