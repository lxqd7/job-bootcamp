<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { http } from '../api/http'
import type { AdminCourseItem, ApiEnvelope, CategoryTreeNode, PageResponse } from '../api/types'
import { describeAuthError } from '../auth/session'

interface CategoryOption {
  id: string
  label: string
}

const categories = ref<CategoryOption[]>([])
const courses = ref<AdminCourseItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const status = ref('')
const keyword = ref('')
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const createForm = reactive({
  title: '',
  description: '',
  categoryId: '',
  priceYuan: 0,
  isFree: false,
  coverUrl: ''
})
const creating = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function flatten(nodes: CategoryTreeNode[], depth = 0, result: CategoryOption[] = []): CategoryOption[] {
  for (const node of nodes) {
    result.push({ id: node.id, label: `${'· '.repeat(depth)}${node.name}` })
    flatten(node.children, depth + 1, result)
  }
  return result
}

function categoryLabel(id: string): string {
  return categories.value.find(option => option.id === id)?.label ?? id
}

function statusLabel(value: string): string {
  return value === 'ONLINE' ? '已上架' : value === 'OFFLINE' ? '已下架' : '草稿'
}

function priceText(course: AdminCourseItem): string {
  return course.isFree ? '免费' : `¥${(course.priceCent / 100).toFixed(2)}`
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await http.get<ApiEnvelope<PageResponse<AdminCourseItem>>>('/api/v1/admin/courses', {
      params: {
        status: status.value || undefined,
        keyword: keyword.value.trim() || undefined,
        page: page.value,
        pageSize
      }
    })
    courses.value = data.data.items
    total.value = data.data.total
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function createCourse(): Promise<void> {
  creating.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const priceCent = createForm.isFree ? 0 : Math.round(Number(createForm.priceYuan) * 100)
    const { data } = await http.post<ApiEnvelope<AdminCourseItem>>('/api/v1/admin/courses', {
      title: createForm.title.trim(),
      description: createForm.description,
      categoryId: Number(createForm.categoryId),
      priceCent,
      isFree: createForm.isFree,
      coverUrl: createForm.coverUrl.trim() || null
    })
    successMessage.value = `课程 ${createForm.title.trim()} 已创建，ID ${data.data.id}。`
    createForm.title = ''
    createForm.description = ''
    createForm.priceYuan = 0
    createForm.isFree = false
    createForm.coverUrl = ''
    page.value = 1
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    creating.value = false
  }
}

async function search(): Promise<void> {
  page.value = 1
  await load()
}

async function gotoPage(next: number): Promise<void> {
  if (next < 1 || next > totalPages.value) {
    return
  }
  page.value = next
  await load()
}

onMounted(async () => {
  try {
    const { data } = await http.get<ApiEnvelope<CategoryTreeNode[]>>('/api/v1/admin/categories')
    categories.value = flatten(data.data)
  } catch {
    categories.value = []
  }
  await load()
})
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">课程目录</p>
      <h1>课程管理</h1>
      <p class="lead">创建课程、维护分类与目录，并在编辑页发布或下架。已上架课程才会出现在学生列表。</p>
    </section>

    <section class="block">
      <h2>新建课程</h2>
      <form class="course-form" @submit.prevent="createCourse">
        <label class="field">
          <span>标题</span>
          <input v-model.trim="createForm.title" maxlength="200" required />
        </label>
        <label class="field">
          <span>分类</span>
          <select v-model="createForm.categoryId" required>
            <option value="" disabled>选择分类</option>
            <option v-for="option in categories" :key="option.id" :value="option.id">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>价格（元）</span>
          <input v-model.number="createForm.priceYuan" type="number" min="0" step="0.01" :disabled="createForm.isFree" />
        </label>
        <label class="check">
          <input v-model="createForm.isFree" type="checkbox" />
          <span>免费课程</span>
        </label>
        <label class="field wide">
          <span>描述</span>
          <input v-model.trim="createForm.description" maxlength="20000" />
        </label>
        <label class="field wide">
          <span>封面地址（可选）</span>
          <input v-model.trim="createForm.coverUrl" maxlength="1000" />
        </label>
        <button class="btn btn-primary" type="submit" :disabled="creating">
          {{ creating ? '创建中…' : '创建课程' }}
        </button>
      </form>
      <p class="muted small">付费课程最低 9.9 元；免费课程价格按 0 保存。</p>
    </section>

    <section class="block">
      <div class="list-head">
        <h2>课程列表</h2>
        <form class="filters" @submit.prevent="search">
          <input v-model.trim="keyword" placeholder="标题或描述" />
          <select v-model="status">
            <option value="">全部状态</option>
            <option value="DRAFT">草稿</option>
            <option value="ONLINE">已上架</option>
            <option value="OFFLINE">已下架</option>
          </select>
          <button class="btn" type="submit" :disabled="loading">查询</button>
        </form>
      </div>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>分类</th>
              <th>价格</th>
              <th>状态</th>
              <th>目录版本</th>
              <th>修订</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="course in courses" :key="course.id">
              <td><code>{{ course.id }}</code></td>
              <td>{{ course.title }}</td>
              <td>{{ categoryLabel(course.categoryId) }}</td>
              <td>{{ priceText(course) }}</td>
              <td><span class="state" :class="course.status.toLowerCase()">{{ statusLabel(course.status) }}</span></td>
              <td>{{ course.catalogVersion }}</td>
              <td>{{ course.revision }}</td>
              <td><RouterLink class="btn small-btn" :to="`/admin/courses/${course.id}`">编辑</RouterLink></td>
            </tr>
            <tr v-if="!loading && courses.length === 0">
              <td colspan="8" class="empty">没有匹配的课程。</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <span class="muted small">共 {{ total }} 门 · 第 {{ page }} / {{ totalPages }} 页</span>
        <div class="pager-actions">
          <button class="btn small-btn" type="button" :disabled="page <= 1 || loading" @click="gotoPage(page - 1)">
            上一页
          </button>
          <button class="btn small-btn" type="button" :disabled="page >= totalPages || loading" @click="gotoPage(page + 1)">
            下一页
          </button>
        </div>
      </div>
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
  max-width: 60ch;
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

.course-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
  align-items: end;
}

.course-form .wide {
  grid-column: span 2;
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 14px;
}

.list-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.filters input,
.filters select {
  padding: 6px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
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

.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}

.pager-actions {
  display: flex;
  gap: 8px;
}

.success {
  margin: 12px 0 0;
  color: var(--pine);
  font-size: 14px;
}

@media (max-width: 720px) {
  .course-form .wide {
    grid-column: span 1;
  }
}
</style>