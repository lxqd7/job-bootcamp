<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { describeError, loadCategories, loadHealth } from '../api/client'
import type { CategoryNode } from '../api/types'
import { currentMe } from '../auth/session'

type LoadState = 'loading' | 'ok' | 'error'

const me = currentMe

const healthState = ref<LoadState>('loading')
const healthError = ref('')
const latencyMs = ref<number | null>(null)

const categoryState = ref<LoadState>('loading')
const categories = ref<CategoryNode[]>([])
const categoryError = ref('')
const traceId = ref('')

const busy = computed(
  () => healthState.value === 'loading' || categoryState.value === 'loading'
)

const healthText = computed(() => {
  if (healthState.value === 'loading') return '连接中'
  if (healthState.value === 'ok') return '运行中'
  return '连接失败'
})

const healthTone = computed(() =>
  healthState.value === 'ok' ? 'ok' : healthState.value === 'error' ? 'bad' : 'idle'
)

const categoryCount = computed(() => countNodes(categories.value))

const categoryText = computed(() => {
  if (categoryState.value === 'loading') return '读取中'
  if (categoryState.value === 'error') return '读取失败'
  return categoryCount.value === 0 ? '空列表' : `${categoryCount.value} 个分类`
})

const categoryTone = computed(() => {
  if (categoryState.value === 'error') return 'bad'
  if (categoryState.value === 'loading') return 'idle'
  return 'ok'
})

const errorMessages = computed(() =>
  Array.from(new Set([healthError.value, categoryError.value].filter(Boolean)))
)

const roleLabel = computed(() => {
  switch (me.value?.role) {
    case 'STUDENT':
      return '学生'
    case 'TEACHER':
      return '教师'
    case 'ADMIN':
      return '管理员'
    default:
      return ''
  }
})

function countNodes(nodes: CategoryNode[]): number {
  return nodes.reduce((total, node) => total + 1 + countNodes(node.children), 0)
}

async function refreshHealth(): Promise<void> {
  healthState.value = 'loading'
  healthError.value = ''
  const startedAt = performance.now()
  try {
    const health = await loadHealth()
    latencyMs.value = Math.round(performance.now() - startedAt)
    if (health.status === 'UP') {
      healthState.value = 'ok'
    } else {
      healthState.value = 'error'
      healthError.value = `课程服务返回状态 ${health.status}。`
    }
  } catch (error) {
    healthState.value = 'error'
    healthError.value = describeError(error)
  }
}

async function refreshCategories(): Promise<void> {
  categoryState.value = 'loading'
  categoryError.value = ''
  try {
    const result = await loadCategories()
    categories.value = result.categories
    traceId.value = result.traceId
    categoryState.value = 'ok'
  } catch (error) {
    categoryState.value = 'error'
    categoryError.value = describeError(error)
  }
}

async function refresh(): Promise<void> {
  await Promise.all([refreshHealth(), refreshCategories()])
}

onMounted(refresh)
</script>

<template>
  <AppShell>
    <section class="hero">
      <p class="stage">阶段 S04 · 积分与签到</p>
      <h1>欢迎回来{{ me ? `，${me.nickname}` : '' }}</h1>
      <p class="lead">
        这一页通过网关读取真实数据。你的登录状态、刷新 cookie 和课程分类都来自本地服务，不是写死的演示内容。
      </p>
    </section>

    <section class="ledger" aria-label="当前状态" aria-live="polite">
      <div class="ledger-head" aria-hidden="true">
        <span>连接项</span>
        <span>状态</span>
        <span>接口</span>
      </div>
      <div class="ledger-row">
        <div class="cell label">登录身份</div>
        <div class="cell status-cell">
          <span class="status ok"><i class="dot"></i>{{ me ? `${me.nickname} · ${roleLabel}` : '未登录' }}</span>
        </div>
        <div class="cell endpoint"><code>/api/v1/me</code></div>
      </div>
      <div class="ledger-row">
        <div class="cell label">课程服务</div>
        <div class="cell status-cell">
          <span class="status" :class="healthTone"><i class="dot"></i>{{ healthText }}</span>
          <span v-if="latencyMs !== null" class="meta">{{ latencyMs }} ms</span>
        </div>
        <div class="cell endpoint"><code>/actuator/health</code></div>
      </div>
      <div class="ledger-row">
        <div class="cell label">课程分类</div>
        <div class="cell status-cell">
          <span class="status" :class="categoryTone"><i class="dot"></i>{{ categoryText }}</span>
        </div>
        <div class="cell endpoint"><code>/api/v1/categories</code></div>
      </div>
    </section>

    <div v-if="errorMessages.length" class="errors">
      <p v-for="message in errorMessages" :key="message" class="error">{{ message }}</p>
    </div>

    <section v-if="categoryState === 'ok' && categoryCount === 0" class="note">
      <h2>目录还是空的</h2>
      <p>
        Flyway 已经在 bc_course 建好 category 表，但还没有写入分类数据。S03 会接入真实的课程目录。
      </p>
    </section>

    <footer class="foot">
      <div class="trace">
        <span>traceId</span>
        <code>{{ traceId || '—' }}</code>
      </div>
      <button class="btn" type="button" :disabled="busy" @click="refresh">
        {{ busy ? '读取中' : '重新读取' }}
      </button>
    </footer>
  </AppShell>
</template>

<style scoped>
.stage {
  margin: 0 0 14px;
  color: var(--muted);
  font-size: 13px;
  letter-spacing: 0.06em;
}

h1 {
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(36px, 6vw, 52px);
  font-weight: 500;
  line-height: 1.08;
  letter-spacing: -0.015em;
}

.lead {
  max-width: 54ch;
  margin: 20px 0 0;
  color: var(--muted);
  line-height: 1.75;
}

.ledger {
  margin-top: 44px;
  border-top: 2px solid var(--ink);
}

.ledger-head,
.ledger-row {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) 220px;
  gap: 16px;
  align-items: baseline;
}

.ledger-head {
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.ledger-row {
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
}

.cell.label {
  font-family: var(--serif);
  font-size: 19px;
}

.status-cell {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--muted);
}

.status.ok .dot {
  background: var(--pine);
}

.status.bad .dot {
  background: var(--bad);
}

.status.idle .dot {
  background: var(--amber);
}

.meta {
  color: var(--muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.endpoint code {
  color: var(--muted);
  font-family: var(--mono);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.error {
  margin: 0;
  padding-left: 16px;
  border-left: 2px solid var(--bad);
  color: var(--bad);
}

.errors {
  display: grid;
  gap: 8px;
  margin-top: 18px;
}

.note {
  margin-top: 40px;
  padding-left: 18px;
  border-left: 2px solid var(--pine);
}

.note h2 {
  margin: 0 0 6px;
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 500;
}

.note p {
  max-width: 58ch;
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 56px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}

.trace {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.trace span {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.trace code {
  color: var(--ink);
  font-family: var(--mono);
  font-size: 12px;
  overflow-wrap: anywhere;
}

@media (max-width: 640px) {
  .ledger-head {
    display: none;
  }

  .ledger-row {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .endpoint {
    display: none;
  }
}
</style>