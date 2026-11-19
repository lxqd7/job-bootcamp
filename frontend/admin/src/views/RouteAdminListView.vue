<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { http } from '../api/http'
import type { AdminCourseItem, ApiEnvelope, PageResponse, RouteSummary } from '../api/types'
import { describeAuthError } from '../auth/session'

const routes = ref<RouteSummary[]>([])
const courses = ref<AdminCourseItem[]>([])
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const createForm = reactive({
  title: '',
  description: '',
  priceYuan: 0
})
const selectedCourseIds = ref<string[]>([])
const creating = ref(false)

const total = computed(() => routes.value.length)

function statusLabel(value: string): string {
  return value === 'ONLINE' ? '已上架' : value === 'OFFLINE' ? '已下架' : '草稿'
}

function priceText(value: number): string {
  return `¥${(value / 100).toFixed(2)}`
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
  errorMessage.value = ''
  try {
    const [routeResponse, courseResponse] = await Promise.all([
      http.get<ApiEnvelope<PageResponse<RouteSummary>>>('/api/v1/admin/routes', {
        params: { page: 1, pageSize: 100 }
      }),
      http.get<ApiEnvelope<PageResponse<AdminCourseItem>>>('/api/v1/admin/courses', {
        params: { page: 1, pageSize: 100 }
      })
    ])
    routes.value = routeResponse.data.data.items
    courses.value = courseResponse.data.data.items
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function createRoute(): Promise<void> {
  creating.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const members = selectedCourseIds.value.map((courseId, index) => ({
      courseId: Number(courseId),
      sort: index + 1
    }))
    const { data } = await http.post<ApiEnvelope<RouteSummary>>('/api/v1/admin/routes', {
      title: createForm.title.trim(),
      description: createForm.description,
      priceCent: Math.round(Number(createForm.priceYuan) * 100),
      members
    })
    successMessage.value = `路线 ${createForm.title.trim()} 已创建，ID ${data.data.id}。`
    createForm.title = ''
    createForm.description = ''
    createForm.priceYuan = 0
    selectedCourseIds.value = []
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    creating.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">路线套餐</p>
      <h1>路线管理</h1>
      <p class="lead">
        路线价格按单课价格比例分摊到付费课程，每门付费课至少 1 元；上架前所有成员课程必须已上架。
      </p>
    </section>

    <section class="block">
      <h2>新建路线</h2>
      <form class="route-form" @submit.prevent="createRoute">
        <label class="field">
          <span>标题</span>
          <input v-model.trim="createForm.title" maxlength="200" required />
        </label>
        <label class="field">
          <span>价格（元）</span>
          <input v-model.number="createForm.priceYuan" type="number" min="0.01" step="0.01" required />
        </label>
        <label class="field wide">
          <span>描述</span>
          <input v-model.trim="createForm.description" maxlength="20000" />
        </label>
        <fieldset class="courses">
          <legend>成员课程（按勾选顺序排序）</legend>
          <label v-for="course in courses" :key="course.id" class="check">
            <input
              type="checkbox"
              :checked="selectedCourseIds.includes(course.id)"
              @change="toggleCourse(course.id)"
            />
            <span>{{ course.title }}（{{ course.isFree ? '免费' : `¥${(course.priceCent / 100).toFixed(2)}` }}，{{ course.status }}）</span>
          </label>
          <p v-if="courses.length === 0" class="muted small">还没有课程，请先在课程管理中创建。</p>
        </fieldset>
        <button class="btn btn-primary" type="submit" :disabled="creating">
          {{ creating ? '创建中…' : '创建路线' }}
        </button>
      </form>
    </section>

    <section class="block">
      <h2>路线列表</h2>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>价格</th>
              <th>状态</th>
              <th>成员数</th>
              <th>修订</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="route in routes" :key="route.id">
              <td><code>{{ route.id }}</code></td>
              <td>{{ route.title }}</td>
              <td>{{ priceText(route.priceCent) }}</td>
              <td><span class="state" :class="route.status.toLowerCase()">{{ statusLabel(route.status) }}</span></td>
              <td>{{ route.memberCount }}</td>
              <td>{{ route.revision }}</td>
              <td><RouterLink class="btn small-btn" :to="`/admin/routes/${route.id}`">编辑</RouterLink></td>
            </tr>
            <tr v-if="!loading && routes.length === 0">
              <td colspan="7" class="empty">还没有路线。</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="muted small">{{ total }} 条路线</p>
    </section>
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

.block {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.block h2 {
  margin: 0 0 14px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.route-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  align-items: end;
}

.route-form .wide {
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

.table-wrap {
  overflow-x: auto;
  margin-top: 16px;
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

.state {
  color: var(--pine);
}

.state.draft {
  color: var(--amber);
}

.state.offline {
  color: var(--bad);
}

.small-btn {
  padding: 4px 10px;
  font-size: 13px;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
  text-align: center;
}

.success {
  margin: 12px 0 0;
  color: var(--pine);
  font-size: 14px;
}

@media (max-width: 720px) {
  .route-form .wide {
    grid-column: span 1;
  }
}
</style>