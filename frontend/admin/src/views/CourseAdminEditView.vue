<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { http } from '../api/http'
import type {
  AdminChapterView,
  AdminCourseDetail,
  AdminLessonView,
  ApiEnvelope,
  CategoryTreeNode
} from '../api/types'
import { describeAuthError } from '../auth/session'

interface CategoryOption {
  id: string
  label: string
}

interface LessonDraft {
  title: string
  sort: number
  mediaId: string
}

const route = useRoute()
const courseId = String(route.params.id)

const detail = ref<AdminCourseDetail | null>(null)
const categories = ref<CategoryOption[]>([])
const loading = ref(true)
const errorMessage = ref('')
const successMessage = ref('')

const courseForm = reactive({
  title: '',
  description: '',
  categoryId: '',
  priceYuan: 0,
  isFree: false,
  coverUrl: '',
  revision: 0
})

const chapterForm = reactive({
  title: '',
  sort: 0
})

const lessonDrafts = reactive<Record<string, LessonDraft>>({})

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

function note(message: string): void {
  successMessage.value = message
  errorMessage.value = ''
}

function fail(error: unknown): void {
  errorMessage.value = describeAuthError(error)
  successMessage.value = ''
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await http.get<ApiEnvelope<AdminCourseDetail>>(`/api/v1/admin/courses/${courseId}`)
    detail.value = data.data
    courseForm.title = data.data.title
    courseForm.description = data.data.description
    courseForm.categoryId = data.data.categoryId
    courseForm.priceYuan = data.data.priceCent / 100
    courseForm.isFree = data.data.isFree
    courseForm.coverUrl = data.data.coverUrl ?? ''
    courseForm.revision = data.data.revision
    for (const chapter of data.data.chapters) {
      if (!lessonDrafts[chapter.id]) {
        lessonDrafts[chapter.id] = { title: '', sort: 0, mediaId: '' }
      }
    }
  } catch (error) {
    fail(error)
  } finally {
    loading.value = false
  }
}

async function saveCourse(): Promise<void> {
  try {
    await http.patch(`/api/v1/admin/courses/${courseId}`, {
      title: courseForm.title.trim(),
      description: courseForm.description,
      categoryId: Number(courseForm.categoryId),
      priceCent: courseForm.isFree ? 0 : Math.round(Number(courseForm.priceYuan) * 100),
      isFree: courseForm.isFree,
      coverUrl: courseForm.coverUrl.trim() || null,
      revision: courseForm.revision
    })
    note('课程已保存。')
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
    await http.post(`/api/v1/admin/courses/${courseId}/${action}`, { revision: detail.value.revision })
    note(action === 'publish' ? '课程已上架。' : '课程已下架。')
    await load()
  } catch (error) {
    fail(error)
  }
}

async function addChapter(): Promise<void> {
  try {
    await http.post(`/api/v1/admin/courses/${courseId}/chapters`, {
      title: chapterForm.title.trim(),
      sort: Number(chapterForm.sort) || 0
    })
    note('章节已添加。')
    chapterForm.title = ''
    chapterForm.sort = 0
    await load()
  } catch (error) {
    fail(error)
  }
}

async function saveChapter(chapter: AdminChapterView): Promise<void> {
  try {
    await http.patch(`/api/v1/admin/courses/${courseId}/chapters/${chapter.id}`, {
      title: chapter.title.trim(),
      sort: Number(chapter.sort) || 0,
      revision: chapter.revision
    })
    note('章节已保存。')
    await load()
  } catch (error) {
    fail(error)
  }
}

async function deleteChapter(chapter: AdminChapterView): Promise<void> {
  try {
    await http.delete(`/api/v1/admin/chapters/${chapter.id}`, { params: { revision: chapter.revision } })
    note('章节已删除。')
    await load()
  } catch (error) {
    fail(error)
  }
}

function draftFor(chapterId: string): LessonDraft {
  if (!lessonDrafts[chapterId]) {
    lessonDrafts[chapterId] = { title: '', sort: 0, mediaId: '' }
  }
  return lessonDrafts[chapterId]
}

async function addLesson(chapter: AdminChapterView): Promise<void> {
  const draft = draftFor(chapter.id)
  try {
    await http.post(`/api/v1/admin/chapters/${chapter.id}/lessons`, {
      title: draft.title.trim(),
      sort: Number(draft.sort) || 0,
      mediaId: draft.mediaId ? Number(draft.mediaId) : null
    })
    note('小节已添加。')
    draft.title = ''
    draft.sort = 0
    draft.mediaId = ''
    await load()
  } catch (error) {
    fail(error)
  }
}

async function saveLesson(chapter: AdminChapterView, lesson: AdminLessonView): Promise<void> {
  try {
    await http.patch(`/api/v1/admin/chapters/${chapter.id}/lessons/${lesson.id}`, {
      title: lesson.title.trim(),
      sort: Number(lesson.sort) || 0,
      mediaId: lesson.mediaId ? Number(lesson.mediaId) : null,
      revision: lesson.revision
    })
    note('小节已保存。')
    await load()
  } catch (error) {
    fail(error)
  }
}

async function deleteLesson(lesson: AdminLessonView): Promise<void> {
  try {
    await http.delete(`/api/v1/admin/lessons/${lesson.id}`, { params: { revision: lesson.revision } })
    note('小节已删除。')
    await load()
  } catch (error) {
    fail(error)
  }
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
    <p class="back"><RouterLink to="/admin/courses">← 返回课程列表</RouterLink></p>
    <p v-if="loading" class="muted">读取课程…</p>
    <p v-else-if="!detail" class="form-error" role="alert">{{ errorMessage || '课程不存在' }}</p>

    <template v-else>
      <section class="head">
        <p class="stage">课程 ID {{ detail.id }} · 目录版本 {{ detail.catalogVersion }}</p>
        <h1>{{ detail.title }}</h1>
        <p class="facts">
          状态 {{ statusLabel(detail.status) }} · 分类 {{ categoryLabel(detail.categoryId) }} · 修订 {{ detail.revision }}
        </p>
      </section>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>

      <section class="block">
        <h2>课程信息</h2>
        <form class="form-grid" @submit.prevent="saveCourse">
          <label class="field">
            <span>标题</span>
            <input v-model.trim="courseForm.title" maxlength="200" required />
          </label>
          <label class="field">
            <span>分类</span>
            <select v-model="courseForm.categoryId">
              <option v-for="option in categories" :key="option.id" :value="option.id">{{ option.label }}</option>
            </select>
          </label>
          <label class="field">
            <span>价格（元）</span>
            <input v-model.number="courseForm.priceYuan" type="number" min="0" step="0.01" :disabled="courseForm.isFree" />
          </label>
          <label class="check">
            <input v-model="courseForm.isFree" type="checkbox" />
            <span>免费课程</span>
          </label>
          <label class="field wide">
            <span>描述</span>
            <input v-model.trim="courseForm.description" maxlength="20000" />
          </label>
          <label class="field wide">
            <span>封面地址（可选）</span>
            <input v-model.trim="courseForm.coverUrl" maxlength="1000" />
          </label>
          <button class="btn btn-primary" type="submit">保存课程</button>
        </form>
        <div class="status-actions">
          <button class="btn" type="button" @click="changeStatus('publish')">上架</button>
          <button class="btn" type="button" @click="changeStatus('offline')">下架</button>
          <span class="muted small">上架付费课程前需要至少一个有效视频小节；媒体验证在 S03-c 接入。</span>
        </div>
      </section>

      <section class="block">
        <h2>章节与小节</h2>
        <form class="inline-form" @submit.prevent="addChapter">
          <input v-model.trim="chapterForm.title" placeholder="新章节标题" required />
          <input v-model.number="chapterForm.sort" type="number" placeholder="排序" />
          <button class="btn" type="submit">添加章节</button>
        </form>

        <div v-for="chapter in detail.chapters" :key="chapter.id" class="chapter">
          <div class="chapter-head">
            <input v-model.trim="chapter.title" class="title-input" maxlength="200" />
            <input v-model.number="chapter.sort" class="sort-input" type="number" />
            <button class="btn small-btn" type="button" @click="saveChapter(chapter)">保存章节</button>
            <button class="btn small-btn" type="button" @click="deleteChapter(chapter)">删除章节</button>
            <span class="muted small">修订 {{ chapter.revision }}</span>
          </div>

          <div class="lessons">
            <div v-for="lesson in chapter.lessons" :key="lesson.id" class="lesson">
              <input v-model.trim="lesson.title" class="title-input" maxlength="200" />
              <input v-model.number="lesson.sort" class="sort-input" type="number" />
              <input v-model.trim="lesson.mediaId" class="media-input" placeholder="mediaId" />
              <button class="btn small-btn" type="button" @click="saveLesson(chapter, lesson)">保存</button>
              <button class="btn small-btn" type="button" @click="deleteLesson(lesson)">删除</button>
              <span class="muted small">修订 {{ lesson.revision }}</span>
            </div>
            <div class="lesson add-lesson">
              <input v-model.trim="draftFor(chapter.id).title" placeholder="新小节标题" />
              <input v-model.number="draftFor(chapter.id).sort" type="number" placeholder="排序" />
              <input v-model.trim="draftFor(chapter.id).mediaId" placeholder="mediaId（可空）" />
              <button class="btn small-btn" type="button" @click="addLesson(chapter)">添加小节</button>
            </div>
          </div>
        </div>

        <p v-if="detail.chapters.length === 0" class="muted">还没有章节。</p>
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

.check {
  display: flex;
  align-items: center;
  gap: 8px;
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

.inline-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.inline-form input,
.chapter-head input,
.lesson input {
  padding: 6px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
}

.chapter {
  padding: 14px 0;
  border-top: 1px solid var(--line);
}

.chapter-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.title-input {
  min-width: 220px;
  flex: 1;
}

.sort-input {
  width: 80px;
}

.media-input {
  width: 110px;
}

.lessons {
  display: grid;
  gap: 8px;
  margin-top: 10px;
  padding-left: 18px;
  border-left: 2px solid var(--line);
}

.lesson {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.add-lesson {
  margin-top: 4px;
}

.small-btn {
  padding: 4px 10px;
  font-size: 13px;
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