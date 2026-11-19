<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { http } from '../api/http'
import type { AdminCourseItem, ApiEnvelope, PageResponse, RouteDetail } from '../api/types'
import { describeAuthError } from '../auth/session'

const routeParam = useRoute()
const routeId = String(routeParam.params.id)

const detail = ref<RouteDetail | null>(null)
const courses = ref<AdminCourseItem[]>([])
const loading = ref(true)
const errorMessage = ref('')
const successMessage = ref('')

const form = reactive({
  title: '',
  description: '',
  priceYuan: 0
})
const selectedCourseIds = ref<string[]>([])

function statusLabel(value: string): string {
  return value === 'ONLINE' ? '已上架' : value === 'OFFLINE' ? '已下架' : '草稿'
}

function money(value: number): string {
  return `¥${(value / 100).toFixed(2)}`
}

function note(message: string): void {
  successMessage.value = message
  errorMessage.value = ''
}

function fail(error: unknown): void {
  errorMessage.value = describeAuthError(error)
  successMessage.value = ''
}

function toggleCourse(courseId: string): void {
  if (selectedCourseIds.value.includes(courseId)) {
    selectedCourseIds.value = selectedCourseIds.value.filter(id => id !== courseId)
  } else {
    selectedCourseIds.value = [...selectedCourseIds.value, courseId]
  }
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<ApiEnvelope<RouteDetail>>(`/api/v1/admin/routes/${routeId}`)
    detail.value = data.data
    form.title = data.data.title
    form.description = data.data.description
    form.priceYuan = data.data.priceCent / 100
    selectedCourseIds.value = [...data.data.members]
      .sort((a, b) => a.sort - b.sort)
      .map(member => member.courseId)
  } catch (error) {
    fail(error)
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (!detail.value) {
    return
  }
  try {
    await http.patch(`/api/v1/admin/routes/${routeId}`, {
      title: form.title.trim(),
      description: form.description,
      priceCent: Math.round(Number(form.priceYuan) * 100),
      members: selectedCourseIds.value.map((courseId, index) => ({
        courseId: Number(courseId),
        sort: index + 1
      })),
      revision: detail.value.revision
    })
    note('路线已保存。')
    await load()
  } catch (error) {
    fail(error)
  }
}

async function changeStatus(action: 'publish' | 'offline'): Promise<void> {
  if (!detail.value) {
    return
  }
  try {
    await http.post(`/api/v1/admin/routes/${routeId}/${action}`, { revision: detail.value.revision })
    note(action === 'publish' ? '路线已上架。' : '路线已下架。')
    await load()
  } catch (error) {
    fail(error)
  }
}

onMounted(async () => {
  try {
    const { data } = await http.get<ApiEnvelope<PageResponse<AdminCourseItem>>>('/api/v1/admin/courses', {
      params: { page: 1, pageSize: 100 }
    })
    courses.value = data.data.items
  } catch {
    courses.value = []
  }
  await load()
})
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink to="/admin/routes">← 返回路线列表</RouterLink></p>
    <p v-if="loading" class="muted">读取路线…</p>
    <p v-else-if="!detail" class="form-error" role="alert">{{ errorMessage || '路线不存在' }}</p>

    <template v-else>
      <section class="head">
        <p class="stage">路线 ID {{ detail.id }} · 当前版本 {{ detail.currentVersionNo }}</p>
        <h1>{{ detail.title }}</h1>
        <p class="facts">
          状态 {{ statusLabel(detail.status) }} · 修订 {{ detail.revision }} · {{ detail.draft ? '含草稿成员' : '当前成员' }}
        </p>
      </section>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>

      <section class="block">
        <h2>路线信息与成员</h2>
        <form class="form-grid" @submit.prevent="save">
          <label class="field">
            <span>标题</span>
            <input v-model.trim="form.title" maxlength="200" required />
          </label>
          <label class="field">
            <span>价格（元）</span>
            <input v-model.number="form.priceYuan" type="number" min="0.01" step="0.01" required />
          </label>
          <label class="field wide">
            <span>描述</span>
            <input v-model.trim="form.description" maxlength="20000" />
          </label>
          <fieldset class="courses">
            <legend>成员课程（勾选顺序即推荐顺序）</legend>
            <label v-for="course in courses" :key="course.id" class="check">
              <input
                type="checkbox"
                :checked="selectedCourseIds.includes(course.id)"
                @change="toggleCourse(course.id)"
              />
              <span>{{ course.title }}（{{ course.isFree ? '免费' : money(course.priceCent) }}，{{ course.status }}）</span>
            </label>
          </fieldset>
          <button class="btn btn-primary" type="submit">保存路线</button>
        </form>
        <div class="status-actions">
          <button class="btn" type="button" @click="changeStatus('publish')">上架</button>
          <button class="btn" type="button" @click="changeStatus('offline')">下架</button>
          <span class="muted small">上架/在线更新会生成新的不可变路线版本，并写入本地 outbox。</span>
        </div>
      </section>

      <section class="block">
        <h2>当前分摊</h2>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>顺序</th>
                <th>课程</th>
                <th>单买价</th>
                <th>套餐分摊</th>
                <th>epoch</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in detail.members" :key="member.courseId">
                <td>{{ member.sort }}</td>
                <td>{{ member.title }}</td>
                <td>{{ member.isFree ? '免费' : money(member.unitPriceCent) }}</td>
                <td>{{ member.isFree ? '0' : money(member.allocatedCent) }}</td>
                <td><code>{{ member.epochId || '—' }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
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
  font-size: clamp(32px, 5vw, 46px);
  font-weight: 500;
}

.facts {
  color: var(--muted);
  font-size: 13px;
}

.block {
  margin-top: 36px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.block h2 {
  margin: 0 0 14px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  align-items: end;
}

.form-grid .wide {
  grid-column: span 2;
}

.courses {
  grid-column: 1 / -1;
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 12px 0 0;
  border: 0;
  border-top: 1px solid var(--line);
}

.courses legend {
  color: var(--muted);
  font-size: 13px;
}

.check {
  display: flex;
  gap: 8px;
  align-items: baseline;
  color: var(--muted);
  font-size: 14px;
}

.status-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
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
  white-space: nowrap;
}

th {
  color: var(--muted);
  font-weight: 500;
  font-size: 12px;
  letter-spacing: 0.06em;
}

td code {
  font-family: var(--mono);
  font-size: 12px;
}

.success {
  margin: 12px 0 0;
  color: var(--pine);
  font-size: 14px;
}

@media (max-width: 720px) {
  .form-grid .wide {
    grid-column: span 1;
  }
}
</style>