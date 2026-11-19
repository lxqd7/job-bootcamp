<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { describeError, loadCategories, loadCourses } from '../api/client'
import type { CategoryNode, CourseSummary } from '../api/types'

interface CategoryOption {
  id: string
  label: string
  depth: number
}

const categories = ref<CategoryNode[]>([])
const courses = ref<CourseSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const keyword = ref('')
const categoryId = ref('')
const loading = ref(false)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function flatten(nodes: CategoryNode[], depth = 0, result: CategoryOption[] = []): CategoryOption[] {
  for (const node of nodes) {
    result.push({ id: node.id, label: `${'· '.repeat(depth)}${node.name}`, depth })
    flatten(node.children, depth + 1, result)
  }
  return result
}

const categoryOptions = computed(() => flatten(categories.value))

const categoryNames = computed(() => {
  const map = new Map<string, string>()
  for (const option of categoryOptions.value) {
    map.set(option.id, option.label.replace(/^·\s*/, ''))
  }
  return map
})

function formatPrice(course: CourseSummary): string {
  return course.isFree ? '免费' : `¥${(course.priceCent / 100).toFixed(2)}`
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadCourses({
      keyword: keyword.value.trim() || undefined,
      categoryId: categoryId.value || undefined,
      page: page.value,
      pageSize
    })
    courses.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
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
    const result = await loadCategories()
    categories.value = result.categories
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
      <h1>课程</h1>
      <p class="lead">已上架课程与目录来自课程服务。购买和学习入口会在 S05/S06 接入，这一页只展示真实目录。</p>
    </section>

    <form class="filters" @submit.prevent="search">
      <label class="field">
        <span>关键词</span>
        <input v-model.trim="keyword" placeholder="课程标题或描述" />
      </label>
      <label class="field">
        <span>分类</span>
        <select v-model="categoryId">
          <option value="">全部分类</option>
          <option v-for="option in categoryOptions" :key="option.id" :value="option.id">
            {{ option.label }}
          </option>
        </select>
      </label>
      <button class="btn btn-primary" type="submit" :disabled="loading">查询</button>
    </form>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <section class="course-list" aria-live="polite">
      <RouterLink
        v-for="course in courses"
        :key="course.id"
        class="course"
        :to="`/courses/${course.id}`"
      >
        <div class="course-top">
          <h2>{{ course.title }}</h2>
          <span class="price" :class="{ free: course.isFree }">{{ formatPrice(course) }}</span>
        </div>
        <p class="meta">
          {{ categoryNames.get(course.categoryId) || '未分类' }} · 课程 ID {{ course.id }}
        </p>
        <p class="hint">查看目录 →</p>
      </RouterLink>
      <p v-if="!loading && courses.length === 0" class="empty">没有匹配的已上架课程。</p>
    </section>

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
  max-width: 56ch;
  color: var(--muted);
  line-height: 1.75;
}

.filters {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1.5fr) auto;
  gap: 18px;
  align-items: end;
  margin-top: 28px;
  padding-top: 16px;
  border-top: 2px solid var(--ink);
}

.course-list {
  display: grid;
  gap: 0;
  margin-top: 28px;
  border-top: 1px solid var(--line);
}

.course {
  display: block;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  text-decoration: none;
}

.course-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.course h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.price {
  color: var(--muted);
  font-variant-numeric: tabular-nums;
}

.price.free {
  color: var(--pine);
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

@media (max-width: 720px) {
  .filters {
    grid-template-columns: 1fr;
  }
}
</style>