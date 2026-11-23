<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import {
  createTeacherBenefit,
  deleteTeacherBenefit,
  describeError,
  loadTeacherBenefit,
  loadTeacherBenefits,
  loadTeacherCourses,
  loadTeacherRoutes,
  offlineTeacherBenefit,
  publishTeacherBenefit,
  updateTeacherBenefit
} from '../api/client'
import type {
  TeacherBenefitSummary,
  TeacherBenefitView,
  TeacherCourseItem,
  TeacherRouteItem
} from '../api/types'

const kindLabels: Record<string, string> = {
  BENEFIT_CHOICE: '福利选择题',
  BENEFIT_CODE: '福利代码题'
}
const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ONLINE: '已发布',
  OFFLINE: '已下架'
}

const courses = ref<TeacherCourseItem[]>([])
const routes = ref<TeacherRouteItem[]>([])
const benefits = ref<TeacherBenefitSummary[]>([])
const courseId = ref('')
const selected = ref<TeacherBenefitView | null>(null)
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const notice = ref('')
const form = ref({
  kind: 'BENEFIT_CHOICE',
  routeIds: [] as string[],
  publicBodyText: '',
  answerBodyText: '',
  revision: 0
})

const selectedRoutes = computed(() =>
  selected.value?.routes.map(route => `路线 ${route.routeId} · epoch ${route.epochId}`) ?? []
)
const isPublished = computed(() => Boolean(selected.value?.currentVersionNo))

function toggleRoute(id: string): void {
  if (selected.value) return
  form.value.routeIds = form.value.routeIds.includes(id)
    ? form.value.routeIds.filter(item => item !== id)
    : [...form.value.routeIds, id]
}

function fillSample(): void {
  if (form.value.kind === 'BENEFIT_CHOICE') {
    form.value.publicBodyText = JSON.stringify({
      questions: [
        {
          id: 'q1', type: 'MULTIPLE', body: 'JVM 运行时数据区有哪些？', score: 10,
          options: [{ id: 'A', text: '堆' }, { id: 'B', text: '虚拟机栈' }, { id: 'C', text: '磁盘' }]
        }
      ]
    }, null, 2)
    form.value.answerBodyText = JSON.stringify({
      answers: { q1: ['A', 'B'] },
      analysis: { q1: '堆和虚拟机栈属于 JVM 运行时数据区。' }
    }, null, 2)
  } else {
    form.value.publicBodyText = JSON.stringify({
      scenario: '福利代码题：返回订单应付金额。',
      requirements: '补全 calculate；数量为负返回 0；使用 long。',
      segments: [
        { text: 'public class Benefit {\n    public long calculate(long unit, int quantity) {\n' },
        { slotId: 's1', initialCode: '        // TODO\n' },
        { text: '    }\n}\n' }
      ]
    }, null, 2)
    form.value.answerBodyText = JSON.stringify({
      referenceBySlot: { s1: '        if (quantity < 0) return 0;\n        return unit * quantity;\n' },
      rubric: '负数、long、无编译错误；允许等价写法。'
    }, null, 2)
  }
}

function resetForm(): void {
  selected.value = null
  form.value = {
    kind: 'BENEFIT_CHOICE',
    routeIds: [],
    publicBodyText: '',
    answerBodyText: '',
    revision: 0
  }
  notice.value = ''
}

async function loadCourses(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [teacherCourses, teacherRoutes] = await Promise.all([loadTeacherCourses(), loadTeacherRoutes()])
    courses.value = teacherCourses
    routes.value = teacherRoutes
    if (!courseId.value && courses.value.length > 0) {
      courseId.value = courses.value[0].id
    }
    await loadList()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

async function loadList(): Promise<void> {
  if (!courseId.value) {
    benefits.value = []
    return
  }
  errorMessage.value = ''
  try {
    const result = await loadTeacherBenefits(courseId.value)
    benefits.value = result.items
  } catch (error) {
    errorMessage.value = describeError(error)
  }
}

async function select(item: TeacherBenefitSummary): Promise<void> {
  errorMessage.value = ''
  notice.value = ''
  try {
    const view = await loadTeacherBenefit(item.id)
    selected.value = view
    form.value = {
      kind: view.kind,
      routeIds: view.routes.length > 0 ? view.routes.map(route => route.routeId) : item.routeIds,
      publicBodyText: JSON.stringify(view.draftPublicBody, null, 2),
      answerBodyText: JSON.stringify(view.draftAnswerBody, null, 2),
      revision: view.revision
    }
  } catch (error) {
    errorMessage.value = describeError(error)
  }
}

function parseJson(text: string, label: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`${label} 不是合法 JSON`)
  }
}

async function persistDraft(): Promise<TeacherBenefitView> {
  const payload = {
    kind: form.value.kind,
    courseId: Number(courseId.value),
    routeIds: form.value.routeIds.map(Number),
    publicBody: parseJson(form.value.publicBodyText, '题目 JSON'),
    answerBody: parseJson(form.value.answerBodyText, '答案 JSON'),
    revision: selected.value ? form.value.revision : null
  }
  if (selected.value) {
    return updateTeacherBenefit(selected.value.id, payload)
  }
  return createTeacherBenefit(payload)
}

async function saveDraft(): Promise<void> {
  if (saving.value) return
  saving.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const view = await persistDraft()
    selected.value = view
    form.value.revision = view.revision
    if (view.routes.length > 0) {
      form.value.routeIds = view.routes.map(route => route.routeId)
    }
    notice.value = `草稿已保存，revision ${view.revision}`
    await loadList()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    saving.value = false
  }
}

async function publish(): Promise<void> {
  if (saving.value || !selected.value) return
  saving.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const saved = await persistDraft()
    const view = await publishTeacherBenefit(saved.id, saved.revision)
    selected.value = view
    form.value.revision = view.revision
    notice.value = `已发布版本 v${view.currentVersionNo}；首次发布已固定路线 epoch`
    await loadList()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    saving.value = false
  }
}

async function offline(): Promise<void> {
  if (saving.value || !selected.value) return
  saving.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const view = await offlineTeacherBenefit(selected.value.id, form.value.revision)
    selected.value = view
    form.value.revision = view.revision
    notice.value = '已下架；路线绑定和历史作答保留'
    await loadList()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    saving.value = false
  }
}

async function removeDraft(): Promise<void> {
  if (saving.value || !selected.value || isPublished.value) return
  saving.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    await deleteTeacherBenefit(selected.value.id, form.value.revision)
    resetForm()
    notice.value = '草稿已删除'
    await loadList()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    saving.value = false
  }
}

onMounted(loadCourses)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">教师工作区 · 福利题</p>
      <h1>路线福利题</h1>
      <p class="lead">
        福利题按课程创建，并绑定一条或多条当前负责且已上线、包含该课程的路线。
        首次发布后路线与 epoch 固定；内容修改生成新版本，不会改绑。学生仍需课程权益和路线福利资格才能作答。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>

    <p v-if="courses.length === 0 && !loading" class="empty">
      当前账号没有负责课程，无法维护福利题。
    </p>

    <template v-else>
      <div class="toolbar">
        <label class="filter">
          <span>负责课程</span>
          <select v-model="courseId" :disabled="loading" @change="resetForm(); loadList()">
            <option v-for="course in courses" :key="course.id" :value="course.id">
              {{ course.title }}（{{ course.status }}）
            </option>
          </select>
        </label>
        <button class="btn small-btn" type="button" :disabled="saving" @click="resetForm">新建福利题</button>
        <button class="btn small-btn" type="button" :disabled="loading" @click="loadList">刷新</button>
      </div>

      <p v-if="loading" class="empty">正在读取福利题…</p>
      <template v-else>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>类型</th><th>状态</th><th>版本</th><th>路线</th><th>更新时间</th><th></th></tr>
            </thead>
            <tbody>
              <tr v-for="item in benefits" :key="item.id" :class="{ selected: selected?.id === item.id }">
                <td>{{ kindLabels[item.kind] ?? item.kind }}</td>
                <td>{{ statusLabels[item.status] ?? item.status }}</td>
                <td>v{{ item.currentVersionNo ?? '—' }}</td>
                <td class="mono">{{ item.routeIds.join(', ') || '—' }}</td>
                <td>{{ item.updatedAt.replace('T', ' ').slice(0, 19) }}</td>
                <td><button class="link-button" type="button" @click="select(item)">编辑/查看</button></td>
              </tr>
            </tbody>
          </table>
          <p v-if="benefits.length === 0" class="empty">该课程还没有福利题。</p>
        </div>

        <section class="editor">
          <header class="editor-head">
            <div>
              <p class="stage">{{ selected ? `编辑福利题 #${selected.id}` : '新建福利题草稿' }}</p>
              <h2>{{ kindLabels[form.kind] ?? form.kind }}</h2>
            </div>
            <span v-if="selected" class="status">{{ statusLabels[selected.status] ?? selected.status }}</span>
          </header>

          <div class="form-grid">
            <label>
              <span>类型</span>
              <select v-model="form.kind" :disabled="Boolean(selected)">
                <option value="BENEFIT_CHOICE">福利选择题（严格 1 题）</option>
                <option value="BENEFIT_CODE">福利代码题（1 道）</option>
              </select>
            </label>
            <label>
              <span>课程</span>
              <select v-model="courseId" :disabled="Boolean(selected)">
                <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.title }}</option>
              </select>
            </label>
          </div>

          <fieldset class="route-picker">
            <legend>绑定路线（首次发布后固定）</legend>
            <label v-for="route in routes" :key="route.id" class="route-option">
              <input
                type="checkbox"
                :checked="form.routeIds.includes(route.id)"
                :disabled="Boolean(selected)"
                @change="toggleRoute(route.id)"
              />
              <span>{{ route.title }}（{{ route.status }}）</span>
            </label>
            <p v-if="routes.length === 0" class="muted small">当前账号没有负责路线。</p>
            <p class="muted small">路线必须已上线且包含所选课程，否则发布会被服务端拒绝。</p>
          </fieldset>

          <div class="json-grid">
            <label>
              <span>题目 JSON</span>
              <textarea v-model="form.publicBodyText" rows="12" spellcheck="false"></textarea>
            </label>
            <label>
              <span>答案 / 解析 JSON</span>
              <textarea v-model="form.answerBodyText" rows="12" spellcheck="false"></textarea>
            </label>
          </div>
          <p v-if="selectedRoutes.length > 0" class="bound">
            已绑定：{{ selectedRoutes.join('；') }}
          </p>
          <p v-if="selected?.currentVersionNo" class="muted small">
            当前发布版本 v{{ selected.currentVersionNo }}。修改内容后发布会生成新版本，路线/epoch 不变。
          </p>

          <div class="actions">
            <button class="btn" type="button" :disabled="saving" @click="fillSample">填入当前类型示例</button>
            <button class="btn btn-primary" type="button" :disabled="saving" @click="saveDraft">
              {{ saving ? '保存中…' : '保存草稿' }}
            </button>
            <button class="btn" type="button" :disabled="saving" @click="publish">
              {{ selected?.currentVersionNo ? '保存并发布新版本' : '保存并发布' }}
            </button>
            <button
              v-if="selected?.status === 'ONLINE'"
              class="btn"
              type="button"
              :disabled="saving"
              @click="offline"
            >
              下架
            </button>
            <button
              v-if="selected && !isPublished"
              class="btn danger"
              type="button"
              :disabled="saving"
              @click="removeDraft"
            >
              删除草稿
            </button>
          </div>
        </section>
      </template>
    </template>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 46px); font-weight: 500; }
h2 { margin: 2px 0 0; font-family: var(--serif); font-size: 22px; font-weight: 500; }
.lead { max-width: 74ch; color: var(--muted); line-height: 1.8; }
.toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 24px 0 14px; }
.filter { display: grid; gap: 4px; min-width: 260px; color: var(--muted); font-size: 12px; }
select, input, textarea { font: inherit; }
.filter select, .form-grid select { padding: 7px 9px; border: 1px solid var(--line); background: transparent; color: var(--ink); }
.table-wrap { overflow-x: auto; border-top: 2px solid var(--ink); }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 9px 6px; border-bottom: 1px solid var(--line); text-align: left; vertical-align: top; }
th { color: var(--muted); font-weight: 500; }
tr.selected { background: rgba(46, 94, 78, .05); }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; word-break: break-all; }
.link-button { padding: 0; border: 0; background: transparent; color: var(--pine); cursor: pointer; text-decoration: underline; }
.small-btn { padding: 5px 10px; font-size: 12px; }
.editor { margin-top: 34px; padding-top: 20px; border-top: 2px solid var(--ink); }
.editor-head { display: flex; flex-wrap: wrap; gap: 12px; align-items: start; justify-content: space-between; }
.status { padding: 2px 8px; border: 1px solid var(--line); color: var(--muted); font-size: 11px; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin: 18px 0; }
.form-grid label, .json-grid label { display: grid; gap: 5px; color: var(--muted); font-size: 12px; }
.form-grid select { color: var(--ink); }
.route-picker { margin: 18px 0; padding: 0; border: 0; }
.route-picker legend { margin-bottom: 8px; color: var(--muted); font-size: 12px; }
.route-option { display: flex; gap: 8px; align-items: baseline; padding: 4px 0; font-size: 13px; }
.json-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; }
.json-grid textarea { padding: 10px; border: 1px solid var(--line); background: transparent; color: var(--ink); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; line-height: 1.6; }
.bound { margin-top: 14px; padding: 10px 12px; border-left: 3px solid var(--pine); color: var(--muted); font-size: 12px; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 18px; }
.muted { color: var(--muted); font-size: 12px; }
.small { font-size: 11px; }
.danger { color: var(--amber); border-color: var(--amber); }
.empty { padding: 22px 0; color: var(--muted); }
</style>