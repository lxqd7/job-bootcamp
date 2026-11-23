<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import {
  createAssignment,
  deleteAssignment,
  describeError,
  loadAssignment,
  loadAssignments,
  offlineAssignment,
  publishAssignment,
  updateAssignment
} from '../api/client'
import type { AssignmentSummary, AssignmentView } from '../api/types'

const kindLabels: Record<string, string> = {
  CHAPTER_CHOICE: '章节选择题',
  COURSE_CODE: '课程代码题'
}
const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ONLINE: '已发布',
  OFFLINE: '已下架'
}

const assignments = ref<AssignmentSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const courseFilter = ref('')
const statusFilter = ref('')
const loading = ref(true)
const errorMessage = ref('')
const notice = ref('')
const saving = ref(false)
const selected = ref<AssignmentView | null>(null)
const form = ref({
  kind: 'CHAPTER_CHOICE',
  courseId: '',
  chapterId: '',
  publicBodyText: '',
  answerBodyText: '',
  revision: 0
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const isPublished = computed(() => Boolean(selected.value?.currentVersionNo))

function formatTime(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadAssignments(courseFilter.value, statusFilter.value, page.value, pageSize)
    assignments.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  selected.value = null
  form.value = {
    kind: 'CHAPTER_CHOICE',
    courseId: '',
    chapterId: '',
    publicBodyText: '',
    answerBodyText: '',
    revision: 0
  }
  notice.value = ''
}

async function select(item: AssignmentSummary): Promise<void> {
  try {
    const view = await loadAssignment(item.id)
    selected.value = view
    form.value = {
      kind: view.kind,
      courseId: String(view.courseId),
      chapterId: view.chapterId ? String(view.chapterId) : '',
      publicBodyText: JSON.stringify(view.draftPublicBody, null, 2),
      answerBodyText: JSON.stringify(view.draftAnswerBody, null, 2),
      revision: view.revision
    }
    notice.value = ''
  } catch (error) {
    errorMessage.value = describeError(error)
  }
}

function fillSample(): void {
  if (form.value.kind === 'CHAPTER_CHOICE') {
    form.value.publicBodyText = JSON.stringify({
      questions: [
        {
          id: 'q1', type: 'SINGLE', body: 'Java 中用于声明常量的关键字是？', score: 10,
          options: [{ id: 'A', text: 'final' }, { id: 'B', text: 'static' }, { id: 'C', text: 'volatile' }]
        },
        {
          id: 'q2', type: 'MULTIPLE', body: '以下哪些属于 JVM 运行时数据区？', score: 20,
          options: [{ id: 'A', text: '堆' }, { id: 'B', text: '虚拟机栈' }, { id: 'C', text: '方法区' }]
        }
      ]
    }, null, 2)
    form.value.answerBodyText = JSON.stringify({
      answers: { q1: ['A'], q2: ['A', 'B', 'C'] },
      analysis: {
        q1: 'final 用于声明不可重新赋值的引用或不可继承类。',
        q2: '堆、虚拟机栈和方法区都是 JVM 运行时数据区。'
      }
    }, null, 2)
  } else {
    form.value.publicBodyText = JSON.stringify({
      scenario: '订单金额计算器需要根据单价和数量返回总金额。',
      requirements: '补全 calculate 方法；数量小于 0 时返回 0；使用 long 避免金额溢出。',
      segments: [
        { text: 'public class OrderCalculator {\n    public long calculate(long unitCent, int quantity) {\n' },
        { slotId: 's1', initialCode: '        // TODO\n' },
        { text: '    }\n}\n' }
      ]
    }, null, 2)
    form.value.answerBodyText = JSON.stringify({
      referenceBySlot: {
        s1: '        if (quantity < 0) return 0;\n        return unitCent * quantity;\n'
      },
      rubric: '正确处理负数、使用 long、无编译错误；不要求与参考实现逐字一致。'
    }, null, 2)
  }
}

function parseJson(text: string, label: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    throw new Error(`${label} 不是合法 JSON`)
  }
}

async function persistDraft(): Promise<AssignmentView> {
  const payload = {
    kind: form.value.kind,
    courseId: Number(form.value.courseId),
    chapterId: form.value.kind === 'CHAPTER_CHOICE'
      ? Number(form.value.chapterId || 0)
      : null,
    publicBody: parseJson(form.value.publicBodyText, '题目 JSON'),
    answerBody: parseJson(form.value.answerBodyText, '答案 JSON'),
    revision: selected.value ? form.value.revision : null
  }
  if (selected.value) {
    return updateAssignment(selected.value.id, payload)
  }
  return createAssignment(payload)
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
    form.value.courseId = String(view.courseId)
    form.value.chapterId = view.chapterId ? String(view.chapterId) : ''
    notice.value = `草稿已保存，revision ${view.revision}`
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    saving.value = false
  }
}

async function publish(): Promise<void> {
  if (!selected.value || saving.value) return
  saving.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const saved = await persistDraft()
    const view = await publishAssignment(saved.id, saved.revision)
    selected.value = view
    form.value.revision = view.revision
    notice.value = `已发布版本 v${view.currentVersionNo}；历史作答继续绑定旧版本`
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    saving.value = false
  }
}

async function offline(): Promise<void> {
  if (!selected.value || saving.value) return
  saving.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const view = await offlineAssignment(selected.value.id, form.value.revision)
    selected.value = view
    form.value.revision = view.revision
    notice.value = '已下架；历史版本和作答记录保留'
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    saving.value = false
  }
}

async function removeDraft(): Promise<void> {
  if (!selected.value || saving.value || isPublished.value) return
  if (!window.confirm('删除这个从未发布的草稿？')) return
  saving.value = true
  errorMessage.value = ''
  try {
    await deleteAssignment(selected.value.id, form.value.revision)
    resetForm()
    notice.value = '草稿已删除'
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    saving.value = false
  }
}

function changePage(next: number): void {
  page.value = next
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">正式作业 · 题目版本</p>
      <h1>章节选择题与课程代码题</h1>
      <p class="lead">
        每章一份选择题作业、每门课程一份代码题。草稿可反复修改；发布时生成不可变版本，
        修改后再发布生成新版本，历史作答继续绑定当时版本。未发布不阻止课程购买或播放。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>

    <div class="toolbar">
      <label class="filter"><span>课程 ID</span><input v-model="courseFilter" placeholder="全部" @keyup.enter="load" /></label>
      <label class="filter">
        <span>状态</span>
        <select v-model="statusFilter" @change="page = 1; load()">
          <option value="">全部</option>
          <option value="DRAFT">草稿</option>
          <option value="ONLINE">已发布</option>
          <option value="OFFLINE">已下架</option>
        </select>
      </label>
      <button class="btn small-btn" type="button" :disabled="loading" @click="load">刷新</button>
      <button class="btn small-btn" type="button" @click="resetForm">新建草稿</button>
    </div>

    <p v-if="loading" class="empty">正在读取作业…</p>
    <template v-else>
      <div class="table-wrap">
        <table class="assignments">
          <thead>
            <tr><th>ID</th><th>类型</th><th>课程 / 章节</th><th>状态</th><th>版本</th><th>revision</th><th>更新时间</th></tr>
          </thead>
          <tbody>
            <tr v-for="item in assignments" :key="item.id" :class="{ selected: selected?.id === item.id }" @click="select(item)">
              <td class="mono">{{ item.id }}</td>
              <td>{{ kindLabels[item.kind] ?? item.kind }}</td>
              <td class="mono">{{ item.courseId }} / {{ item.chapterId ?? '—' }}</td>
              <td>{{ statusLabels[item.status] ?? item.status }}</td>
              <td>{{ item.currentVersionNo == null ? '—' : `v${item.currentVersionNo}` }}</td>
              <td>{{ item.revision }}</td>
              <td>{{ formatTime(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="assignments.length === 0" class="empty">没有匹配作业。</p>
      </div>

      <div v-if="totalPages > 1" class="pager">
        <button class="btn small-btn" type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>

      <section class="editor">
        <div class="editor-head">
          <h2>{{ selected ? `作业 #${selected.id}` : '新建草稿' }}</h2>
          <button class="btn small-btn" type="button" @click="fillSample">填入当前类型示例</button>
        </div>
        <div class="form-grid">
          <label class="field"><span>类型</span>
            <select v-model="form.kind" :disabled="Boolean(selected)">
              <option value="CHAPTER_CHOICE">章节选择题</option>
              <option value="COURSE_CODE">课程代码题</option>
            </select>
          </label>
          <label class="field"><span>课程 ID</span><input v-model="form.courseId" inputmode="numeric" :disabled="Boolean(selected)" /></label>
          <label class="field" v-if="form.kind === 'CHAPTER_CHOICE'"><span>章节 ID</span><input v-model="form.chapterId" inputmode="numeric" :disabled="Boolean(selected)" /></label>
        </div>
        <div class="json-grid">
          <label class="json-field"><span>题目公开 JSON</span>
            <textarea v-model="form.publicBodyText" rows="18" spellcheck="false" placeholder="点击“填入示例”查看结构" />
          </label>
          <label class="json-field"><span>标准答案 / 参考实现 JSON（仅管理员可见）</span>
            <textarea v-model="form.answerBodyText" rows="18" spellcheck="false" placeholder="答案与解析不能出现在题目 JSON 中" />
          </label>
        </div>
        <div class="actions">
          <button class="btn btn-primary" type="button" :disabled="saving" @click="saveDraft">
            {{ saving ? '保存中…' : '保存草稿' }}
          </button>
          <button class="btn" type="button" :disabled="saving || !form.publicBodyText || !form.answerBodyText" @click="publish">
            保存并发布新版本
          </button>
          <button v-if="isPublished" class="btn" type="button" :disabled="saving" @click="offline">下架</button>
          <button v-if="!isPublished" class="btn danger" type="button" :disabled="saving || !selected" @click="removeDraft">删除草稿</button>
        </div>
        <div v-if="selected?.currentVersionNo" class="current-version">
          <h3>当前发布版本 v{{ selected.currentVersionNo }}</h3>
          <pre>{{ JSON.stringify(selected.currentPublicBody, null, 2) }}</pre>
          <p class="muted">答案和评分标准保存在服务端版本快照中，不在学生接口返回。</p>
        </div>
      </section>
    </template>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 48px); font-weight: 500; }
.lead { max-width: 78ch; color: var(--muted); line-height: 1.8; }
.toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 24px 0 14px; }
.filter, .field { display: grid; gap: 4px; color: var(--muted); font-size: 12px; }
.filter input, .filter select, .field input, .field select { padding: 7px 9px; border: 1px solid var(--line); background: transparent; font: inherit; color: var(--ink); }
.table-wrap { overflow-x: auto; border-top: 2px solid var(--ink); }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 8px; border-bottom: 1px solid var(--line); text-align: left; }
th { color: var(--muted); font-weight: 500; }
tr { cursor: pointer; }
tr.selected { background: rgba(31, 77, 58, .07); }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.pager { display: flex; align-items: center; gap: 14px; margin-top: 16px; color: var(--muted); font-size: 13px; }
.small-btn { padding: 5px 9px; font-size: 12px; }
.editor { margin-top: 32px; padding-top: 18px; border-top: 2px solid var(--ink); }
.editor-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.editor-head h2 { margin: 0; font-family: var(--serif); font-size: 25px; font-weight: 500; }
.form-grid { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 14px; }
.form-grid .field { min-width: 150px; }
.json-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px; margin-top: 16px; }
.json-field { display: grid; gap: 6px; color: var(--muted); font-size: 12px; }
.json-field textarea { width: 100%; padding: 10px; border: 1px solid var(--line); background: #fbfaf6; color: var(--ink); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; resize: vertical; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
.danger { border-color: var(--bad); color: var(--bad); }
.current-version { margin-top: 22px; padding-top: 14px; border-top: 1px solid var(--line); }
.current-version h3 { margin: 0 0 8px; font-family: var(--serif); font-size: 20px; font-weight: 500; }
pre { max-height: 260px; overflow: auto; padding: 12px; border: 1px solid var(--line); background: #fbfaf6; font-size: 12px; }
.muted { color: var(--muted); font-size: 12px; }
.empty { color: var(--muted); padding: 20px 0; }
@media (max-width: 800px) { .json-grid { grid-template-columns: 1fr; } }
</style>