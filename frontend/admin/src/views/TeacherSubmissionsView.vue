<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import {
  describeError,
  loadTeacherAssignments,
  loadTeacherCourses,
  loadTeacherSubmission,
  loadTeacherSubmissions
} from '../api/client'
import type {
  ChoiceBody,
  CodeBody,
  TeacherAssignmentItem,
  TeacherCourseItem,
  TeacherSubmissionSummary,
  TeacherSubmissionView
} from '../api/types'

const courses = ref<TeacherCourseItem[]>([])
const assignments = ref<TeacherAssignmentItem[]>([])
const submissions = ref<TeacherSubmissionSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const courseId = ref('')
const assignmentId = ref('')
const detail = ref<TeacherSubmissionView | null>(null)
const loading = ref(true)
const loadingDetail = ref(false)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const choiceBody = computed<ChoiceBody | null>(() =>
  detail.value?.kind === 'CHAPTER_CHOICE' ? (detail.value.submission.publicBody as ChoiceBody) : null
)
const codeBody = computed<CodeBody | null>(() =>
  detail.value?.kind === 'COURSE_CODE' ? (detail.value.submission.publicBody as CodeBody) : null
)

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function assignmentLabel(item: { kind: string; chapterId?: string | null }): string {
  if (item.kind === 'COURSE_CODE') return '课程代码补全'
  return `章节自测${item.chapterId ? `（章节 ${item.chapterId}）` : ''}`
}

function reviewStateLabel(state: string): string {
  switch (state) {
    case 'GRADED':
      return '已判分'
    case 'PENDING_REVIEW':
    case 'QUEUED':
      return '等待点评'
    case 'RUNNING':
      return '点评中'
    case 'RETRY_WAIT':
      return '等待重试'
    case 'FAILED':
      return '点评失败'
    case 'SUCCEEDED':
      return '点评完成'
    default:
      return state
  }
}

function verdictLabel(verdict: string): string {
  switch (verdict) {
    case 'SATISFIED':
      return '已确认满足要求'
    case 'PARTIAL':
      return '部分满足要求'
    case 'NEEDS_CHANGE':
      return '需要修改'
    default:
      return verdict
  }
}

function chosen(questionId: string): string[] {
  const value = detail.value?.submission.answers[questionId]
  return Array.isArray(value) ? (value as string[]) : []
}

function isChosen(questionId: string, optionId: string): boolean {
  return chosen(questionId).includes(optionId)
}

function isCorrect(questionId: string, optionId: string): boolean {
  return (detail.value?.submission.correctAnswers?.[questionId] ?? []).includes(optionId)
}

function slotCode(slotId: string): string {
  const value = detail.value?.submission.answers[slotId]
  return typeof value === 'string' ? value : ''
}

async function loadBase(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    courses.value = await loadTeacherCourses()
    if (courses.value.length > 0) {
      courseId.value = courses.value[0].id
      await loadCourseData()
    }
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

async function loadCourseData(): Promise<void> {
  detail.value = null
  assignmentId.value = ''
  page.value = 1
  if (!courseId.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    assignments.value = await loadTeacherAssignments(courseId.value)
    await loadList()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

async function loadList(): Promise<void> {
  if (!courseId.value) return
  try {
    const result = await loadTeacherSubmissions(courseId.value, assignmentId.value, page.value, pageSize)
    submissions.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeError(error)
  }
}

async function filterAssignment(): Promise<void> {
  page.value = 1
  detail.value = null
  loading.value = true
  await loadList()
  loading.value = false
}

function changePage(next: number): void {
  page.value = next
  void loadList()
}

async function openDetail(item: TeacherSubmissionSummary): Promise<void> {
  loadingDetail.value = true
  errorMessage.value = ''
  try {
    detail.value = await loadTeacherSubmission(item.id)
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loadingDetail.value = false
  }
}

onMounted(loadBase)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">教师工作区 · 只读</p>
      <h1>学生作答记录</h1>
      <p class="lead">
        只展示当前分配课程的学生历次选择题作答、成绩、代码提交与 AI 反馈。
        页面没有修改答案、人工改分或重试点评的入口；教师调离课程后服务端会拒绝读取。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <div class="toolbar">
      <label class="filter">
        <span>负责课程</span>
        <select v-model="courseId" :disabled="loading" @change="loadCourseData">
          <option v-for="course in courses" :key="course.id" :value="course.id">
            {{ course.title }}（{{ course.status }}）
          </option>
        </select>
      </label>
      <label class="filter">
        <span>作业</span>
        <select v-model="assignmentId" :disabled="loading" @change="filterAssignment">
          <option value="">全部作业</option>
          <option v-for="item in assignments" :key="item.id" :value="item.id">
            {{ assignmentLabel(item) }} · v{{ item.currentVersionNo ?? '—' }}（{{ item.status }}）
          </option>
        </select>
      </label>
      <button class="btn small-btn" type="button" :disabled="loading" @click="loadList">刷新</button>
    </div>

    <p v-if="courses.length === 0 && !loading" class="empty">
      当前账号没有负责课程。请联系管理员分配后再查看作答记录。
    </p>
    <p v-else-if="loading" class="empty">正在读取学生作答…</p>

    <template v-else>
      <div class="table-wrap">
        <table>
          <thead>
            <tr><th>学生</th><th>作业</th><th>版本</th><th>成绩 / 状态</th><th>提交时间</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="item in submissions" :key="item.id" :class="{ selected: detail?.submissionId === item.id }">
              <td>{{ item.studentNickname ?? `学生 ${item.studentId}` }}<br><span class="muted mono">#{{ item.studentId }}</span></td>
              <td>{{ assignmentLabel(item) }}<br><span class="muted mono">作业 #{{ item.assignmentId }}</span></td>
              <td>v{{ item.versionNo ?? '—' }}</td>
              <td>{{ item.score == null ? reviewStateLabel(item.reviewState) : `${item.score} / ${item.maxScore}` }}</td>
              <td>{{ formatTime(item.submittedAt) }}</td>
              <td><button class="link-button" type="button" :disabled="loadingDetail" @click="openDetail(item)">查看</button></td>
            </tr>
          </tbody>
        </table>
        <p v-if="submissions.length === 0" class="empty">该范围内还没有学生提交。</p>
      </div>

      <div v-if="totalPages > 1" class="pager">
        <button class="btn small-btn" type="button" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>

      <section v-if="detail" class="detail">
        <header class="detail-head">
          <div>
            <p class="stage">只读作答详情</p>
            <h2>{{ detail.studentNickname ?? `学生 ${detail.studentId}` }} · {{ assignmentLabel(detail.submission) }}</h2>
            <p class="muted">
              学生 #{{ detail.studentId }} · 版本 v{{ detail.submission.versionNo }} ·
              提交于 {{ formatTime(detail.submission.submittedAt) }}
            </p>
          </div>
          <button class="btn small-btn" type="button" @click="detail = null">关闭</button>
        </header>

        <template v-if="choiceBody">
          <p class="score-line">成绩：{{ detail.submission.score }} / {{ detail.submission.maxScore }}</p>
          <article v-for="(question, index) in choiceBody.questions" :key="question.id" class="question">
            <p class="question-body"><strong>第 {{ index + 1 }} 题</strong>（{{ question.score }} 分）{{ question.body }}</p>
            <ul class="options">
              <li
                v-for="option in question.options"
                :key="option.id"
                :class="{ chosen: isChosen(question.id, option.id), correct: isCorrect(question.id, option.id) }"
              >
                <span>{{ option.text }}</span>
                <span v-if="isChosen(question.id, option.id)" class="mark">学生选择</span>
                <span v-if="isCorrect(question.id, option.id)" class="mark correct-mark">正确答案</span>
              </li>
            </ul>
            <p class="analysis"><strong>解析：</strong>{{ detail.submission.analysis?.[question.id] ?? '暂无解析。' }}</p>
          </article>
        </template>

        <template v-if="codeBody">
          <h3>题目要求</h3>
          <p class="pre">{{ codeBody.scenario }}</p>
          <p class="pre">{{ codeBody.requirements }}</p>
          <h3>学生代码</h3>
          <template v-for="(segment, index) in codeBody.segments" :key="index">
            <pre v-if="!segment.slotId" class="fixed-code">{{ segment.text }}</pre>
            <div v-else class="slot-block">
              <p class="slot-label">{{ segment.slotId }}</p>
              <pre class="code">{{ slotCode(segment.slotId) || '（空）' }}</pre>
            </div>
          </template>

          <h3>AI 反馈</h3>
          <p class="muted">
            {{ reviewStateLabel(detail.review.taskStatus) }}
            <span v-if="detail.review.feedback?.isMock" class="mock-tag">固定样例 · MOCK</span>
          </p>
          <p v-if="detail.review.taskStatus === 'FAILED'" class="failed">
            点评失败（{{ detail.review.safeErrorCode ?? '未知类别' }}）；教师无重试入口。
          </p>
          <template v-if="detail.review.feedback">
            <p class="verdict">{{ verdictLabel(detail.review.feedback.verdict) }}</p>
            <p class="summary">{{ detail.review.feedback.summary }}</p>
            <ul v-if="detail.review.feedback.issues?.length" class="issues">
              <li v-for="(issue, index) in detail.review.feedback.issues" :key="index">
                <p class="issue-slot">{{ issue.slotId }}</p>
                <p><strong>证据：</strong>{{ issue.evidence }}</p>
                <p><strong>原因：</strong>{{ issue.reason }}</p>
                <p><strong>建议：</strong>{{ issue.suggestion }}</p>
              </li>
            </ul>
            <p v-if="detail.review.feedback.suggestions?.length" class="suggestions">
              <strong>总体建议：</strong>{{ detail.review.feedback.suggestions.join('；') }}
            </p>
            <p class="fine-print">
              模型局限：{{ detail.review.feedback.limitations?.length ? detail.review.feedback.limitations.join('；') : '无' }}
            </p>
          </template>

          <h3>参考实现</h3>
          <template v-for="(segment, index) in codeBody.segments" :key="index">
            <div v-if="segment.slotId" class="slot-block">
              <p class="slot-label">{{ segment.slotId }}</p>
              <pre class="code">{{ detail.submission.referenceBySlot?.[segment.slotId] ?? '—' }}</pre>
            </div>
          </template>
        </template>
      </section>
    </template>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 48px); font-weight: 500; }
h2 { margin: 2px 0 6px; font-family: var(--serif); font-size: 22px; font-weight: 500; }
h3 { margin: 22px 0 8px; font-family: var(--serif); font-size: 18px; font-weight: 500; }
.lead { max-width: 72ch; color: var(--muted); line-height: 1.8; }
.toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: 14px; margin: 24px 0 16px; }
.filter { display: grid; gap: 4px; min-width: 220px; color: var(--muted); font-size: 12px; }
.filter select { padding: 7px 9px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.table-wrap { overflow-x: auto; border-top: 2px solid var(--ink); }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 8px; border-bottom: 1px solid var(--line); text-align: left; vertical-align: top; }
th { color: var(--muted); font-weight: 500; }
tr.selected { background: rgba(46, 94, 78, .05); }
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.muted { color: var(--muted); font-size: 12px; }
.link-button { padding: 0; border: 0; background: transparent; color: var(--pine); cursor: pointer; text-decoration: underline; }
.pager { display: flex; align-items: center; gap: 14px; margin-top: 16px; color: var(--muted); font-size: 13px; }
.small-btn { padding: 5px 10px; font-size: 12px; }
.detail { margin-top: 34px; padding-top: 22px; border-top: 2px solid var(--ink); }
.detail-head { display: flex; flex-wrap: wrap; gap: 14px; justify-content: space-between; align-items: start; }
.score-line { font-size: 14px; }
.question { padding: 14px 0; border-bottom: 1px solid var(--line); }
.question-body { line-height: 1.7; white-space: pre-wrap; }
.options { display: grid; gap: 5px; margin: 10px 0; padding: 0; list-style: none; }
.options li { display: flex; gap: 10px; padding: 7px 9px; border: 1px solid var(--line); }
.options li.chosen { border-color: var(--ink); }
.options li.correct { background: rgba(46, 94, 78, .05); }
.mark { margin-left: auto; color: var(--muted); font-size: 11px; }
.correct-mark { color: var(--pine); }
.analysis { padding: 9px 11px; background: var(--paper-soft, #f6f4ee); font-size: 13px; line-height: 1.8; }
.pre { white-space: pre-wrap; color: var(--muted); line-height: 1.8; }
.fixed-code, .code { margin: 0; padding: 10px 12px; background: var(--paper-soft, #f6f4ee); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 13px; white-space: pre-wrap; }
.slot-block { margin: 10px 0; }
.slot-label { margin: 0 0 5px; color: var(--muted); font-size: 12px; }
.mock-tag { margin-left: 8px; padding: 1px 7px; border: 1px solid var(--amber); color: var(--amber); font-size: 11px; }
.failed { color: var(--amber); }
.verdict { font-family: var(--serif); font-size: 19px; }
.summary { line-height: 1.8; }
.issues { padding-left: 20px; }
.issues li { margin-bottom: 12px; line-height: 1.7; }
.issue-slot { margin: 0; color: var(--pine); font-size: 12px; }
.suggestions, .fine-print { line-height: 1.8; font-size: 13px; }
.fine-print { color: var(--muted); font-size: 12px; }
.empty { padding: 22px 0; color: var(--muted); }
</style>