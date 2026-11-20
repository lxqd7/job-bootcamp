<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { loadOrders, describeTradeError } from '../api/client'
import type { OrderResponse } from '../api/types'
import { formatCent, formatDateTime, fulfillmentLabel, orderStateLabel } from '../order/format'

const orders = ref<OrderResponse[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const state = ref('')
const loading = ref(true)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const stateOptions = [
  { value: '', label: '全部订单' },
  { value: 'WAIT_PAY', label: '待支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'CLOSED', label: '已关闭' }
]

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadOrders(page.value, pageSize, state.value)
    orders.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

function changeState(): void {
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
      <p class="stage">交易</p>
      <h1>我的订单</h1>
      <p class="lead">订单状态和金额来自服务端；待支付倒计时仅作提示，最终由服务端判断有效期。</p>
    </section>

    <div class="toolbar">
      <label class="filter">
        <span>状态</span>
        <select v-model="state" @change="changeState">
          <option v-for="option in stateOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <button class="btn small-btn" type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="loading" class="empty">正在读取订单…</p>

    <section v-else class="orders">
      <RouterLink
        v-for="order in orders"
        :key="order.id"
        class="order-row"
        :to="{ name: 'order-detail', params: { id: order.id } }"
      >
        <span class="order-no">{{ order.orderNo }}</span>
        <span class="order-state" :class="`state-${order.state.toLowerCase()}`">{{ orderStateLabel(order.state) }}</span>
        <span class="order-meta">
          {{ formatDateTime(order.createdAt) }} ·
          {{ order.items.length }} 门课程 ·
          {{ fulfillmentLabel(order.fulfillmentState) }}
        </span>
        <span class="order-amount">{{ formatCent(order.pricing.payableCent) }}</span>
      </RouterLink>
      <p v-if="orders.length === 0" class="empty">
        还没有订单。可以从 <RouterLink to="/courses">课程目录</RouterLink> 购买付费课程。
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
  max-width: 62ch;
  color: var(--muted);
  line-height: 1.75;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 16px;
  margin-top: 30px;
  padding-bottom: 14px;
  border-bottom: 2px solid var(--ink);
}

.filter {
  display: grid;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
}

.filter select {
  padding: 5px 10px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
}

.orders {
  border-bottom: 1px solid var(--line);
}

.order-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) auto minmax(0, 1.4fr) auto;
  gap: 16px;
  align-items: baseline;
  padding: 15px 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  text-decoration: none;
}

.order-row:hover {
  background: rgba(23, 89, 76, 0.04);
}

.order-no {
  overflow: hidden;
  font-family: var(--mono);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-state {
  padding: 1px 8px;
  border: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
}

.state-wait_pay {
  border-color: var(--amber);
  color: var(--amber);
}

.state-paid {
  border-color: var(--pine);
  color: var(--pine);
}

.order-meta {
  color: var(--muted);
  font-size: 12px;
}

.order-amount {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.pager {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 20px;
  color: var(--muted);
  font-size: 13px;
}

.small-btn {
  padding: 5px 12px;
  font-size: 13px;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}

@media (max-width: 760px) {
  .order-row {
    grid-template-columns: 1fr auto;
  }

  .order-meta {
    grid-column: 1 / -1;
  }
}
</style>