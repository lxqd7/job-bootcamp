<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import {
  loadAssignment,
  loadReview,
  loadSubmission
} from '../api/client'
import { describeAuthError } from '../auth/session'
import type {
  ChoiceBody,
  CodeBody,
  ReviewView,
  StudentAssignment,
  SubmissionDetail
} from '../api/types'

const route = useRoute()

const submission = ref<SubmissionDetail | null>(null)
const assignment = ref<StudentAssignment | null>(null)
const review = ref<ReviewView | null>(null)
const loading = ref(true)
const errorMessage = ref('')
const reviewError = ref('')

let pollTimer: ReturnType<typeof setTimeout> | null = null
let pollStartedAt = 0

const submissionId = computed(() => String(route.params.id))
const choiceBody = computed<ChoiceBody | null>(() =>
  submission.value?.kind === 'CHAPTER_CHOICE' ? (submission.value.publicBody as ChoiceBody) : null
)
const codeBody = computed<CodeBody | null>(() =>
  submission.value?.kind === 'COURSE_CODE' ? (submission.value.publicBody as CodeBody) : null
)
const activeReview = computed(() => {
  const status = review.value?.taskStatus
  return status === 'QUEUED' || status === 'RUNNING' || status === 'RETRY_WAIT'
})
const reviewFailed = computed(() => review.value?.taskStatus === 'FAILED')
const reviewStep = computed(() => {
  const status = review.value?.taskStatus
  if (status === 'SUCCEEDED') return 3
  if (status === 'FAILED') return 4
  if (status === 'RUNNING' || status === 'RETRY_WAIT') return 2
  if (status === 'QUEUED') return 1
  return 0
})
const versionHint = computed(() => {
  if (!submission.value || !assignment.value) return ''
  if (assignment.value.currentVersionNo == null) return ''
  if (submission.value.versionNo === assignment.value.currentVersionNo) return ''
  return `本次提交绑定历史版本 v${submission.value.versionNo}，当前作业版本为 v${assignment.value.currentVersionNo}。`
})

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function chosen(questionId: string): string[] {
  const value = submission.value?.answers[questionId]
  return Array.isArray(value) ? (value as string[]) : []
}

function isChosen(questionId: string, optionId: string): boolean {
  return chosen(questionId).includes(optionId)
}

function isCorrectOption(questionId: string, optionId: string): boolean {
  return (submission.value?.correctAnswers?.[questionId] ?? []).includes(optionId)
}

function questionCorrect(questionId: string): boolean {
  const expected = [...(submission.value?.correctAnswers?.[questionId] ?? [])].sort()
  const actual = [...chosen(questionId)].sort()
  return expected.length > 0 && expected.join(',') === actual.join(',')
}

function slotCode(slotId: string): string {
  const value = submission.value?.answers[slotId]
  return typeof value === 'string' ? value : ''
}

function verdictLabel(verdict: string | undefined): string {
  switch (verdict) {
    case 'SATISFIED':
      return '已确认满足要求'
    case 'PARTIAL':
      return '部分满足要求'
    case 'NEEDS_CHANGE':
      return '需要修改'
    default:
      return verdict ?? '—'
  }
}

function reviewStateLabel(state: string | undefined): string {
  switch (state) {
    case 'QUEUED':
      return '排队等待点评'
    case 'RUNNING':
      return '正在生成点评'
    case 'RETRY_WAIT':
      return '等待重试点评'
    case 'SUCCEEDED':
      return '点评完成'
    case 'FAILED':
      return '点评失败'
    case 'NOT_APPLICABLE':
      return '无需点评'
    default:
      return state ?? '读取中'
  }
}

function reviewStepClass(step: number): string {
  if (reviewStep.value === 4 && step >= 2) return 'step failed'
  if (reviewStep.value >= step) return 'step done'
  if (reviewStep.value + 1 === step) return 'step active'
  return 'step'
}

function safeErrorText(code: string | null): string {
  switch (code) {
    case 'AI_UNAVAILABLE':
      return '点评服务暂时不可用，本次点评未能完成。'
    case 'AI_INVALID_RESPONSE':
      return '点评结果格式异常，本次点评未能完成。'
    case 'AI_TIMEOUT':
      return '点评超时，本次点评未能完成。'
    default:
      return '本次点评未能完成，提交记录和题目答案已保留。'
  }
}

function schedulePoll(): void {
  if (pollTimer) clearTimeout(pollTimer)
  const elapsed = Date.now() - pollStartedAt
  // 前 30 秒每 2 秒轮询，之后降为 5 秒；离开页面立即停止。
  const delay = elapsed < 30_000 ? 2_000 : 5_000
  pollTimer = setTimeout(() => {
    void refreshReview()
  }, delay)
}

async function refreshReview(): Promise<void> {
  if (!submission.value || submission.value.kind !== 'COURSE_CODE') return
  try {
    const result = await loadReview(submission.value.id)
    review.value = result
    reviewError.value = ''
    if (result.taskStatus === 'QUEUED' || result.taskStatus === 'RUNNING' || result.taskStatus === 'RETRY_WAIT') {
      schedulePoll()
    }
  } catch (error) {
    reviewError.value = describeAuthError(error)
    if (pollStartedAt && Date.now() - pollStartedAt < 10 * 60_000) {
      schedulePoll()
    }
  }
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  reviewError.value = ''
  if (pollTimer) clearTimeout(pollTimer)
  try {
    submission.value = await loadSubmission(submissionId.value)
    try {
      assignment.value = await loadAssignment(submission.value.assignmentId)
    } catch {
      assignment.value = null
    }
    if (submission.value.kind === 'COURSE_CODE') {
      pollStartedAt = Date.now()
      await refreshReview()
    }
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => {
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">提交详情</p>
      <h1>{{ submission ? (submission.kind === 'COURSE_CODE' ? '代码点评记录' : '章节自测记录') : '提交记录' }}</h1>
      <p v-if="submission" class="lead">
        提交于 {{ formatTime(submission.submittedAt) }} · 绑定版本 v{{ submission.versionNo }}
        <template v-if="assignment"> · <RouterLink :to="`/assignments/${submission.assignmentId}`">返回作业</RouterLink></template>
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="loading" class="empty">正在读取提交记录…</p>

    <template v-else-if="submission">
      <p v-if="versionHint" class="version-hint">{{ versionHint }}</p>

      <section v-if="submission.kind === 'CHAPTER_CHOICE'" class="score-line">
        <span>本次成绩</span>
        <strong>{{ submission.score }} / {{ submission.maxScore }}</strong>
      </section>

      <section v-if="choiceBody" class="paper">
        <article v-for="(question, index) in choiceBody.questions" :key="question.id" class="question">
          <header>
            <span class="index">第 {{ index + 1 }} 题</span>
            <span class="tag">{{ question.type === 'SINGLE' ? '单选' : '多选' }}</span>
            <span class="points">{{ question.score }} 分</span>
            <span class="result" :class="{ ok: questionCorrect(question.id) }">
              {{ chosen(question.id).length === 0 ? '未作答' : questionCorrect(question.id) ? '正确' : '错误' }}
            </span>
          </header>
          <p class="body">{{ question.body }}</p>
          <ul class="options">
            <li
              v-for="option in question.options"
              :key="option.id"
              :class="{ chosen: isChosen(question.id, option.id), correct: isCorrectOption(question.id, option.id) }"
            >
              <span class="option-text">{{ option.text }}</span>
              <span v-if="isChosen(question.id, option.id)" class="mark">你的选择</span>
              <span v-if="isCorrectOption(question.id, option.id)" class="mark correct-mark">正确答案</span>
            </li>
          </ul>
          <p class="analysis">
            <strong>解析：</strong>{{ submission.analysis?.[question.id] ?? '暂无解析。' }}
          </p>
        </article>
      </section>

      <template v-if="codeBody">
        <section class="paper code-paper">
          <h2>场景</h2>
          <p class="pre">{{ codeBody.scenario }}</p>
          <h2>要求</h2>
          <p class="pre">{{ codeBody.requirements }}</p>

          <h2>本次提交</h2>
          <template v-for="(segment, index) in codeBody.segments" :key="index">
            <pre v-if="!segment.slotId" class="fixed-code">{{ segment.text }}</pre>
            <div v-else class="slot-block">
              <p class="slot-label">补全位置 {{ segment.slotId }}</p>
              <pre class="student-code">{{ slotCode(segment.slotId as string) || '（空）' }}</pre>
            </div>
          </template>
        </section>

        <section class="paper review">
          <header class="review-head">
            <div>
              <p class="stage">AI 点评 · 静态检查</p>
              <h2>{{ review?.taskStatus ? reviewStateLabel(review.taskStatus) : '读取点评状态' }}</h2>
            </div>
            <span v-if="review?.feedback?.isMock" class="mock-tag">固定样例 · MOCK（未调用真实 AI）</span>
            <span v-else-if="review?.taskStatus === 'SUCCEEDED'" class="real-tag">外部 AI 辅助点评</span>
          </header>

          <ol class="status-track" aria-label="点评进度">
            <li class="step done">已提交</li>
            <li :class="reviewStepClass(2)">点评处理中</li>
            <li :class="reviewStepClass(3)">点评完成</li>
          </ol>

          <p v-if="reviewError" class="form-error" role="alert">
            {{ reviewError }}
            <button class="link-button" type="button" @click="refreshReview">刷新状态</button>
          </p>
          <div v-if="activeReview" class="waiting-panel" role="status">
            <p><strong>{{ reviewStateLabel(review?.taskStatus) }}</strong> · 页面每 2 秒自动刷新，30 秒后改为 5 秒；离开页面后停止。</p>
            <p class="muted">提交记录和答案已保存；点评过程不影响学习进度。</p>
          </div>
          <div v-else-if="reviewFailed" class="failure-panel">
            <strong>点评失败</strong>
            <p>{{ safeErrorText(review?.safeErrorCode ?? null) }}</p>
            <p class="muted">提交记录、答案和参考实现已保留；本次任务不会自动重试，也没有单独的重试按钮。</p>
            <RouterLink v-if="submission" class="btn small-btn" :to="`/assignments/${submission.assignmentId}`">
              返回作业重新作答
            </RouterLink>
          </div>

          <template v-if="review?.feedback">
            <p class="verdict">{{ verdictLabel(review.feedback.verdict) }}</p>
            <p class="summary">{{ review.feedback.summary }}</p>
            <ul v-if="review.feedback.issues?.length" class="issues">
              <li v-for="(issue, index) in review.feedback.issues" :key="index">
                <p class="issue-slot">{{ issue.slotId }}</p>
                <p><strong>证据：</strong>{{ issue.evidence }}</p>
                <p><strong>原因：</strong>{{ issue.reason }}</p>
                <p><strong>建议：</strong>{{ issue.suggestion }}</p>
              </li>
            </ul>
            <div v-if="review.feedback.suggestions?.length" class="suggestions">
              <h3>总体建议</h3>
              <ul><li v-for="(item, index) in review.feedback.suggestions" :key="index">{{ item }}</li></ul>
            </div>
            <p class="fine-print">
              点评为静态检查，未运行代码；模型局限：
              {{ review.feedback.limitations?.length ? review.feedback.limitations.join('；') : '无' }}
            </p>
          </template>
          <p class="fine-print">
            AI 点评为静态检查，不执行代码、不提供数值评分；学生不能选择模型，也不能单独重试点评。
            代码提交后发送给外部 AI 服务辅助点评，提交记录和答案保存在本系统。
          </p>
        </section>

        <section class="paper reference">
          <h2>参考实现</h2>
          <template v-for="(segment, index) in codeBody.segments" :key="index">
            <pre v-if="!segment.slotId" class="fixed-code">{{ segment.text }}</pre>
            <div v-else class="slot-block">
              <p class="slot-label">参考 · {{ segment.slotId }}</p>
              <pre class="student-code">{{ submission.referenceBySlot?.[segment.slotId as string] ?? '—' }}</pre>
            </div>
          </template>
        </section>
      </template>
    </template>

    <p v-else class="empty">无法读取该提交记录。</p>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 44px); font-weight: 500; }
h2 { font-family: var(--serif); font-size: 20px; font-weight: 500; }
.lead { color: var(--muted); line-height: 1.7; }
.version-hint { padding: 12px; border-left: 3px solid var(--amber); color: var(--amber); font-size: 13px; }
.score-line { display: flex; gap: 14px; align-items: baseline; margin: 22px 0; padding: 14px 0; border-top: 2px solid var(--ink); border-bottom: 1px solid var(--line); }
.score-line span { color: var(--muted); font-size: 12px; }
.score-line strong { font-family: var(--serif); font-size: 24px; font-weight: 500; }
.paper { margin-top: 24px; }
.question { padding: 18px 0; border-bottom: 1px solid var(--line); }
.question header { display: flex; gap: 10px; align-items: baseline; }
.index { font-weight: 600; }
.tag { padding: 1px 6px; border: 1px solid var(--line); color: var(--muted); font-size: 11px; }
.points { color: var(--muted); font-size: 12px; }
.result { margin-left: auto; font-size: 12px; color: var(--amber); }
.result.ok { color: var(--pine); }
.body { margin: 12px 0; line-height: 1.7; white-space: pre-wrap; }
.options { display: grid; gap: 6px; margin: 0; padding: 0; list-style: none; }
.options li { display: flex; gap: 10px; align-items: baseline; padding: 8px 10px; border: 1px solid var(--line); }
.options li.chosen { border-color: var(--ink); }
.options li.correct { background: rgba(46, 94, 78, .05); }
.option-text { flex: 1; }
.mark { color: var(--muted); font-size: 11px; }
.correct-mark { color: var(--pine); }
.analysis { margin-top: 12px; padding: 10px 12px; background: var(--paper-soft, #f6f4ee); font-size: 13px; line-height: 1.8; }
.pre { white-space: pre-wrap; color: var(--muted); line-height: 1.8; }
.fixed-code { margin: 0; padding: 10px 12px; background: var(--paper-soft, #f6f4ee); font-size: 13px; white-space: pre-wrap; }
.slot-block { margin: 12px 0; }
.slot-label { margin: 0 0 6px; color: var(--muted); font-size: 12px; }
.student-code { margin: 0; padding: 12px; border: 1px solid var(--line); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 13px; white-space: pre-wrap; overflow-x: auto; }
.review-head { display: flex; flex-wrap: wrap; gap: 12px; align-items: baseline; justify-content: space-between; }
.muted { color: var(--muted); font-size: 12px; }
.mock-tag { padding: 2px 8px; border: 1px solid var(--amber); color: var(--amber); font-size: 11px; }
.real-tag { padding: 2px 8px; border: 1px solid var(--pine); color: var(--pine); font-size: 11px; }
.status-track { display: flex; gap: 0; margin: 18px 0; padding: 0; list-style: none; counter-reset: review-step; }
.status-track .step { position: relative; flex: 1; padding: 9px 10px 9px 26px; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); color: var(--muted); font-size: 12px; counter-increment: review-step; }
.status-track .step::before { content: counter(review-step); position: absolute; left: 6px; top: 7px; display: grid; width: 15px; height: 15px; place-items: center; border: 1px solid var(--line); border-radius: 50%; font-size: 10px; }
.status-track .step.done { color: var(--pine); border-color: var(--pine); }
.status-track .step.active { color: var(--ink); border-color: var(--ink); }
.status-track .step.failed { color: var(--amber); border-color: var(--amber); }
.waiting-panel { margin: 14px 0; padding: 12px 14px; border-left: 3px solid var(--pine); background: rgba(46, 94, 78, .04); font-size: 13px; line-height: 1.7; }
.waiting-panel p { margin: 0 0 4px; }
.failure-panel { display: grid; gap: 8px; margin: 14px 0; padding: 14px; border: 1px solid var(--amber); background: rgba(164, 98, 42, .05); font-size: 13px; line-height: 1.7; }
.failure-panel strong { color: var(--amber); }
.failure-panel p { margin: 0; }
.failure-panel .small-btn { justify-self: start; padding: 5px 12px; font-size: 13px; }
.verdict { font-family: var(--serif); font-size: 19px; }
.summary { line-height: 1.8; }
.issues { padding-left: 20px; }
.issues li { margin-bottom: 14px; line-height: 1.7; }
.issue-slot { margin: 0; color: var(--pine); font-size: 12px; }
.suggestions ul { line-height: 1.8; }
.fine-print { color: var(--muted); font-size: 12px; line-height: 1.7; }
.empty { padding: 22px 0; color: var(--muted); }
</style>