<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeError, loadRoute } from '../api/client'
import type { RouteDetail } from '../api/types'

const routeParam = useRoute()
const detail = ref<RouteDetail | null>(null)
const loading = ref(true)
const errorMessage = ref('')

function money(value: number): string {
  return `¥${(value / 100).toFixed(2)}`
}

onMounted(async () => {
  try {
    detail.value = await loadRoute(String(routeParam.params.id))
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink to="/routes">← 返回路线列表</RouterLink></p>

    <p v-if="loading" class="muted">读取路线…</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="detail">
      <section class="head">
        <p class="stage">路线 ID {{ detail.id }} · 版本 {{ detail.currentVersionNo }}</p>
        <h1>{{ detail.title }}</h1>
        <p class="lead">{{ detail.description }}</p>
        <p class="facts">
          <span class="price">{{ money(detail.priceCent) }}</span>
          <span>{{ detail.members.length }} 门课程</span>
        </p>
      </section>

      <section class="members">
        <h2>课程与顺序</h2>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>顺序</th>
                <th>课程</th>
                <th>单买价</th>
                <th>套餐内分摊</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in detail.members" :key="member.courseId">
                <td>{{ member.sort }}</td>
                <td>{{ member.title }}</td>
                <td>{{ member.isFree ? '免费' : money(member.unitPriceCent) }}</td>
                <td>{{ member.isFree ? '0' : money(member.allocatedCent) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="muted small">
          分摊是套餐内部金额拆分，用于已有课程抵扣和退款计算；实际抵扣与购买在 S07 接入。
        </p>
      </section>
    </template>
  </AppShell>
</template>

<style scoped>
.back {
  margin: 0 0 18px;
  font-size: 14px;
}

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
  max-width: 60ch;
  color: var(--muted);
  line-height: 1.75;
}

.facts {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  color: var(--muted);
  font-size: 13px;
}

.price {
  color: var(--ink);
  font-weight: 600;
}

.members {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 2px solid var(--ink);
}

.members h2 {
  margin: 0 0 14px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  padding: 10px 12px 10px 0;
  border-bottom: 1px solid var(--line);
  text-align: left;
}

th {
  color: var(--muted);
  font-weight: 500;
  font-size: 12px;
  letter-spacing: 0.06em;
}
</style>