<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, RouterLink, useRoute, useRouter } from 'vue-router'
import { basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { EditorView } from '@codemirror/view'
import { java } from '@codemirror/lang-java'
import AppShell from '../components/AppShell.vue'
import {
  httpStatus,
  loadAssignment,
  loadAssignmentProgress,
  loadAttempt,
  loadBenefitAssignment,
  loadBenefitProgress,
  loadBenefitSubmissions,
  loadSubmissions,
  saveDraft,
  startAttempt as startAttemptApi,
  startBenefitAttempt,
  submitAttempt
} from '../api/client'
import { describeAuthError } from '../auth/session'
import type {
  AssignmentProgress,
  AttemptView,
  ChoiceBody,
  CodeBody,
  StudentAssignment,
  StudentBenefit,
  SubmissionSummary
} from '../api/types'

const route = useRoute()
const router = useRouter()

const benefitMode = computed(() => route.name === 'benefit-assignment')
const assignment = ref<StudentAssignment | StudentBenefit | null>(null)
const progress = ref<AssignmentProgress | null>(null)
const history = ref<SubmissionSummary[]>([])
const attempt = ref<AttemptView | null>(null)
const answers = ref<Record<string, unknown>>({})
const loading = ref(true)
const starting = ref(false)
const submitting = ref(false)
const saving = ref(false)
const dirty = ref(false)
const conflict = ref(false)
const errorMessage = ref('')
const statusMessage = ref('')
const confirmUnanswered = ref(0)

const slotHosts = new Map<string, HTMLElement>()
const editors: EditorView[] = []
const codeValues = ref<Record<string, string>>({})
let saveTimer: ReturnType<typeof setTimeout> | null = null
let submitKey = ''
let submitSignature = ''

const assignmentId = computed(() => String(route.params.id))
const kind = computed(() => assignment.value?.kind ?? null)
const isCodeKind = computed(() => Boolean(kind.value?.endsWith('_CODE')))
const isChoiceKind = computed(() => Boolean(kind.value?.endsWith('_CHOICE')))
const choiceBody = computed<ChoiceBody | null>(() =>
  isChoiceKind.value ? (assignment.value?.publicBody as ChoiceBody | null) : null
)
const codeBody = computed<CodeBody | null>(() =>
  isCodeKind.value ? (assignment.value?.publicBody as CodeBody | null) : null
)
const sourceLink = computed(() => {
  if (!assignment.value) return '/courses'
  if (benefitMode.value) {
    const benefit = assignment.value as StudentBenefit
    return `/routes/${benefit.routeId}/benefits?courseId=${benefit.courseId}`
  }
  return `/learning/courses/${assignment.value.courseId}`
})
const codeSlots = computed(() =>
  codeBody.value?.segments.filter(segment => Boolean(segment.slotId)) ?? []
)
const canAnswer = computed(() =>
  Boolean(attempt.value && attempt.value.status === 'OPEN' && assignment.value?.status === 'ONLINE')
)
const currentScore = computed(() => progress.value?.recent ?? null)
const highestScore = computed(() => progress.value?.highest ?? null)
const unansweredCount = computed(() => {
  if (!choiceBody.value) return 0
  return choiceBody.value.questions.filter(question => {
    const value = answers.value[question.id]
    return !Array.isArray(value) || value.length === 0
  }).length
})
const hasCode = computed(() =>
  Object.values(codeValues.value).some(value => value.trim().length > 0)
)

function isSelected(questionId: string, optionId: string): boolean {
  const value = answers.value[questionId]
  return Array.isArray(value) && value.includes(optionId)
}

function toggleOption(questionId: string, optionId: string, multiple: boolean): void {
  if (!canAnswer.value) return
  const current = Array.isArray(answers.value[questionId])
    ? (answers.value[questionId] as string[])
    : []
  if (!multiple) {
    answers.value[questionId] = [optionId]
  } else if (current.includes(optionId)) {
    answers.value[questionId] = current.filter(item => item !== optionId)
  } else {
    answers.value[questionId] = [...current, optionId]
  }
  scheduleSave()
}

function setSlotHost(slotId: string, element: Element | null): void {
  if (element instanceof HTMLElement) {
    slotHosts.set(slotId, element)
  } else {
    slotHosts.delete(slotId)
  }
}

function hostFor(slotId: string): HTMLElement | null {
  const tracked = slotHosts.get(slotId)
  if (tracked?.isConnected) return tracked
  const candidates = document.querySelectorAll<HTMLElement>('[data-slot]')
  return [...candidates].find(element => element.dataset.slot === slotId) ?? null
}

function destroyEditors(): void {
  for (const editor of editors) {
    editor.destroy()
  }
  editors.length = 0
  slotHosts.clear()
}

function mountEditors(): void {
  destroyEditors()
  if (!canAnswer.value || !isCodeKind.value) return
  for (const segment of codeSlots.value) {
    const slotId = segment.slotId as string
    const host = hostFor(slotId)
    if (!host) continue
    const initial = codeValues.value[slotId] ?? segment.initialCode ?? ''
    codeValues.value[slotId] = initial
    const editor = new EditorView({
      state: EditorState.create({
        doc: initial,
        extensions: [
          basicSetup,
          java(),
          EditorView.updateListener.of(update => {
            if (update.docChanged) {
              codeValues.value[slotId] = update.state.doc.toString()
              scheduleSave()
            }
          })
        ]
      }),
      parent: host
    })
    editors.push(editor)
  }
}

function payload(): Record<string, unknown> {
  if (isCodeKind.value) {
    return { ...codeValues.value }
  }
  return JSON.parse(JSON.stringify(answers.value)) as Record<string, unknown>
}

function applyAttempt(view: AttemptView): void {
  attempt.value = view
  const draft = view.draft ?? {}
  if (isCodeKind.value) {
    const values: Record<string, string> = {}
    for (const segment of codeSlots.value) {
      const slotId = segment.slotId as string
      const value = draft[slotId]
      values[slotId] = typeof value === 'string' ? value : (segment.initialCode ?? '')
    }
    codeValues.value = values
    answers.value = values
  } else {
    answers.value = JSON.parse(JSON.stringify(draft)) as Record<string, unknown>
  }
  dirty.value = false
  conflict.value = false
  void nextTick(mountEditors)
}

async function start(): Promise<void> {
  if (!assignment.value || starting.value) return
  starting.value = true
  errorMessage.value = ''
  statusMessage.value = ''
  try {
    const view = benefitMode.value
      ? await startBenefitAttempt(assignment.value.id)
      : await startAttemptApi(assignment.value.id)
    applyAttempt(view)
    statusMessage.value = '已开始本次作答。'
    await refreshProgress()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    starting.value = false
  }
}

function scheduleSave(): void {
  dirty.value = true
  statusMessage.value = ''
  if (!attempt.value || !canAnswer.value || conflict.value) return
  if (saveTimer) clearTimeout(saveTimer)
  // 代码草稿与选择题统一 2 秒防抖，页面离开前再强制保存一次。
  saveTimer = setTimeout(() => {
    void saveNow()
  }, 2000)
}

async function saveNow(): Promise<void> {
  if (!attempt.value || !dirty.value || !canAnswer.value || conflict.value) return
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
  }
  saving.value = true
  errorMessage.value = ''
  try {
    const updated = await saveDraft(attempt.value.id, payload(), attempt.value.draftRevision)
    attempt.value.draftRevision = updated.draftRevision
    dirty.value = false
    statusMessage.value = '草稿已保存。'
  } catch (error) {
    if (httpStatus(error) === 409) {
      conflict.value = true
      errorMessage.value = '草稿已在其他设备更新。本地内容已保留，可载入最新草稿或稍后重试。'
    } else {
      errorMessage.value = describeAuthError(error)
    }
  } finally {
    saving.value = false
  }
}

async function reloadServerDraft(): Promise<void> {
  if (!attempt.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    const view = await loadAttempt(attempt.value.id)
    applyAttempt(view)
    statusMessage.value = '已载入服务器上的最新草稿。'
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    saving.value = false
  }
}

async function overwriteServerDraft(): Promise<void> {
  if (!attempt.value) return
  saving.value = true
  errorMessage.value = ''
  try {
    // 先读取最新 revision，再用本地内容覆盖；这是用户明确选择，而不是静默丢失任一侧输入。
    const latest = await loadAttempt(attempt.value.id)
    attempt.value.draftRevision = latest.draftRevision
    conflict.value = false
    dirty.value = true
    await saveNow()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    saving.value = false
  }
}

function newKey(): string {
  const cryptoObject = globalThis.crypto as Crypto | undefined
  if (cryptoObject && typeof cryptoObject.randomUUID === 'function') {
    return cryptoObject.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function submit(): Promise<void> {
  if (!attempt.value || submitting.value || !canAnswer.value) return
  if (conflict.value) {
    errorMessage.value = '草稿存在冲突，请先载入服务器最新草稿或用本地内容覆盖。'
    return
  }
  if (isCodeKind.value && !hasCode.value) {
    errorMessage.value = '代码不能全部为空，请至少补全一个 slot。'
    return
  }
  if (isChoiceKind.value && unansweredCount.value > 0 && confirmUnanswered.value === 0) {
    confirmUnanswered.value = unansweredCount.value
    return
  }
  submitting.value = true
  errorMessage.value = ''
  confirmUnanswered.value = 0
  const content = payload()
  const signature = JSON.stringify(content)
  if (signature !== submitSignature) {
    submitKey = newKey()
    submitSignature = signature
  }
  try {
    if (dirty.value && !conflict.value) {
      await saveNow()
    }
    const result = await submitAttempt(attempt.value.id, content, submitKey)
    await router.push(`/submissions/${result.id}`)
  } catch (error) {
    if (httpStatus(error) === 409) {
      errorMessage.value = describeAuthError(error)
      await refreshProgress()
    } else {
      errorMessage.value = describeAuthError(error)
    }
  } finally {
    submitting.value = false
  }
}

async function refreshProgress(): Promise<void> {
  if (!assignment.value) return
  try {
    progress.value = benefitMode.value
      ? await loadBenefitProgress(assignment.value.id)
      : await loadAssignmentProgress(assignment.value.id)
    const page = benefitMode.value
      ? await loadBenefitSubmissions(assignment.value.id)
      : await loadSubmissions(assignment.value.id)
    history.value = page.items
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  statusMessage.value = ''
  confirmUnanswered.value = 0
  destroyEditors()
  attempt.value = null
  answers.value = {}
  codeValues.value = {}
  try {
    assignment.value = benefitMode.value
      ? await loadBenefitAssignment(assignmentId.value)
      : await loadAssignment(assignmentId.value)
    await refreshProgress()
    if (progress.value?.openAttemptId) {
      applyAttempt(await loadAttempt(progress.value.openAttemptId))
    }
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
    if (canAnswer.value) {
      await nextTick()
      mountEditors()
    }
  }
}

function formatTime(value: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function reviewLabel(state: string): string {
  switch (state) {
    case 'PENDING_REVIEW':
    case 'QUEUED':
      return '等待点评'
    case 'RUNNING':
      return '点评中'
    case 'RETRY_WAIT':
      return '等待重试'
    case 'FAILED':
      return '点评失败'
    default:
      return state
  }
}

function scoreText(score: number | null, max: number | null): string {
  return score == null || max == null ? '待点评' : `${score} / ${max}`
}

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (dirty.value && canAnswer.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}

onBeforeRouteLeave(async () => {
  if (!dirty.value || !canAnswer.value) return true
  await saveNow()
  return true
})

watch(() => route.fullPath, load)
onMounted(() => {
  void load()
  window.addEventListener('beforeunload', handleBeforeUnload)
})
onBeforeUnmount(() => {
  if (saveTimer) clearTimeout(saveTimer)
  destroyEditors()
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">{{ benefitMode ? '路线福利题' : '正式作业' }}</p>
      <h1>{{ isCodeKind ? (benefitMode ? '福利代码题' : '课程代码补全') : (benefitMode ? '福利选择题' : '章节自测') }}</h1>
      <p v-if="assignment" class="lead">
        当前版本 v{{ assignment.currentVersionNo ?? '—' }}。
        <RouterLink :to="sourceLink">{{ benefitMode ? '返回路线福利' : '返回课程学习' }}</RouterLink>
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="statusMessage" class="notice" role="status">{{ statusMessage }}</p>
    <p v-if="loading" class="empty">正在读取作业…</p>

    <template v-else-if="assignment">
      <p v-if="assignment.status === 'DRAFT'" class="empty">该作业暂未发布，不影响课程学习。</p>
      <p v-else-if="assignment.status === 'OFFLINE'" class="offline">
        该作业已下架，不能开始新的作答或提交；已有历史记录仍可查看。
      </p>

      <section v-if="progress" class="score-board">
        <div>
          <span>当前版本</span>
          <strong>v{{ progress.currentVersionNo ?? '—' }}</strong>
        </div>
        <div>
          <span>最近成绩</span>
          <strong>{{ currentScore ? scoreText(currentScore.score, currentScore.maxScore) : '当前版本未作答' }}</strong>
        </div>
        <div>
          <span>最高成绩</span>
          <strong>{{ highestScore ? scoreText(highestScore.score, highestScore.maxScore) : '当前版本未作答' }}</strong>
        </div>
        <div class="score-action">
          <button
            v-if="assignment.status === 'ONLINE' && !canAnswer && assignment.publicBody"
            class="btn small-btn"
            type="button"
            :disabled="starting"
            @click="start"
          >
            {{ starting ? '准备中…' : progress.currentVersionSubmitted ? '再练一次' : '开始作答' }}
          </button>
        </div>
      </section>
      <p v-if="progress && !progress.currentVersionSubmitted && history.length > 0" class="hint">
        当前版本未作答；旧版本成绩仍保留在下方历史记录中。
      </p>

      <section v-if="canAnswer && choiceBody" class="paper">
        <article v-for="(question, index) in choiceBody.questions" :key="question.id" class="question">
          <header>
            <span class="index">第 {{ index + 1 }} 题</span>
            <span class="tag">{{ question.type === 'SINGLE' ? '单选' : '多选' }}</span>
            <span class="points">{{ question.score }} 分</span>
          </header>
          <p class="body">{{ question.body }}</p>
          <label v-for="option in question.options" :key="option.id" class="option">
            <input
              :type="question.type === 'SINGLE' ? 'radio' : 'checkbox'"
              :name="question.id"
              :checked="isSelected(question.id, option.id)"
              @change="toggleOption(question.id, option.id, question.type === 'MULTIPLE')"
            />
            <span>{{ option.text }}</span>
          </label>
        </article>
      </section>

      <section v-if="canAnswer && codeBody" class="paper code-paper">
        <div class="scenario">
          <h2>场景</h2>
          <p>{{ codeBody.scenario }}</p>
          <h2>要求</h2>
          <p class="pre">{{ codeBody.requirements }}</p>
        </div>
        <div class="skeleton">
          <template v-for="(segment, index) in codeBody.segments" :key="index">
            <pre v-if="!segment.slotId" class="fixed-code">{{ segment.text }}</pre>
            <div v-else class="slot">
              <p class="slot-label">补全位置 {{ segment.slotId }}</p>
              <div
                :ref="element => setSlotHost(segment.slotId as string, element as Element | null)"
                :data-slot="segment.slotId"
                class="editor"
              ></div>
            </div>
          </template>
        </div>
        <p class="editor-state">
          <span v-if="conflict" class="conflict">存在草稿冲突</span>
          <span v-else-if="saving">正在保存…</span>
          <span v-else-if="dirty">有未保存修改（2 秒后自动保存）</span>
          <span v-else>草稿已同步</span>
          <template v-if="conflict">
            <button class="link-button" type="button" @click="reloadServerDraft">
              载入服务器最新草稿
            </button>
            <button class="link-button" type="button" @click="overwriteServerDraft">
              用本地内容覆盖
            </button>
          </template>
        </p>
      </section>

      <div v-if="canAnswer" class="actions">
        <template v-if="confirmUnanswered > 0">
          <span class="warn">还有 {{ confirmUnanswered }} 题未作答，未答按 0 分计。</span>
          <button class="btn btn-primary" type="button" :disabled="submitting" @click="submit">
            {{ submitting ? '提交中…' : '仍要提交' }}
          </button>
          <button class="btn small-btn" type="button" @click="confirmUnanswered = 0">继续作答</button>
        </template>
        <template v-else>
          <button class="btn btn-primary" type="button" :disabled="submitting" @click="submit">
            {{ submitting ? '提交中…' : '提交本次作答' }}
          </button>
          <span class="fine-print">
            {{ isCodeKind ? '提交后生成 AI 点评，允许部分 slot 为空，但不能全空。' : '未答题可提交，按 0 分计。' }}
          </span>
        </template>
      </div>

      <section v-if="history.length > 0" class="history">
        <h2>提交历史</h2>
        <table>
          <thead>
            <tr><th>时间</th><th>版本</th><th>成绩 / 状态</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="item in history" :key="item.id">
              <td>{{ formatTime(item.submittedAt) }}</td>
              <td>
                v{{ item.versionNo }}
                <span v-if="item.versionNo === assignment.currentVersionNo" class="tag current">当前版本</span>
              </td>
              <td>
                {{ item.score == null ? reviewLabel(item.reviewState) : `${item.score} / ${item.maxScore}` }}
              </td>
              <td><RouterLink :to="`/submissions/${item.id}`">查看详情</RouterLink></td>
            </tr>
          </tbody>
        </table>
      </section>

      <p v-if="!canAnswer && !attempt && assignment.status === 'ONLINE' && progress?.currentVersionSubmitted" class="empty">
        当前版本已作答，可点击“再练一次”重新开始。
      </p>
    </template>

    <p v-else class="empty">无法读取该作业，请确认课程权益或链接是否正确。</p>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 46px); font-weight: 500; }
.lead { color: var(--muted); line-height: 1.7; }
.score-board { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 16px; margin: 24px 0 8px; padding: 16px 0; border-top: 2px solid var(--ink); border-bottom: 1px solid var(--line); }
.score-board div { display: grid; gap: 4px; }
.score-board span { color: var(--muted); font-size: 12px; }
.score-board strong { font-family: var(--serif); font-size: 20px; font-weight: 500; }
.score-action { align-content: center; }
.hint { color: var(--muted); font-size: 13px; }
.offline { padding: 14px; border-left: 3px solid var(--amber); color: var(--amber); }
.paper { margin-top: 20px; }
.question { padding: 18px 0; border-bottom: 1px solid var(--line); }
.question header { display: flex; gap: 10px; align-items: baseline; }
.index { font-weight: 600; }
.tag { padding: 1px 6px; border: 1px solid var(--line); color: var(--muted); font-size: 11px; }
.points { margin-left: auto; color: var(--muted); font-size: 12px; }
.body { margin: 12px 0; line-height: 1.7; white-space: pre-wrap; }
.option { display: flex; gap: 10px; align-items: baseline; padding: 7px 0; color: var(--ink); cursor: pointer; }
.code-paper .scenario p { color: var(--muted); line-height: 1.8; white-space: pre-wrap; }
.pre { white-space: pre-wrap; }
.skeleton { margin-top: 16px; }
.fixed-code { margin: 0; padding: 10px 12px; background: var(--paper-soft, #f6f4ee); font-size: 13px; white-space: pre-wrap; }
.slot { margin: 12px 0; }
.slot-label { margin: 0 0 6px; color: var(--muted); font-size: 12px; }
.editor { border: 1px solid var(--line); }
.editor :deep(.cm-editor) { font-size: 13px; }
.editor :deep(.cm-scroller) { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.editor-state { display: flex; gap: 12px; align-items: baseline; color: var(--muted); font-size: 12px; }
.conflict { color: var(--amber); }
.actions { display: flex; flex-wrap: wrap; gap: 14px; align-items: center; margin-top: 22px; }
.warn { color: var(--amber); font-size: 13px; }
.fine-print { color: var(--muted); font-size: 12px; }
.history { margin-top: 36px; }
.history h2 { font-family: var(--serif); font-size: 20px; font-weight: 500; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 9px 6px; border-bottom: 1px solid var(--line); text-align: left; }
th { color: var(--muted); font-weight: 500; }
.tag.current { margin-left: 6px; color: var(--pine); border-color: var(--pine); }
.link-button { padding: 0; border: 0; background: transparent; color: var(--pine); cursor: pointer; text-decoration: underline; }
.small-btn { padding: 5px 12px; font-size: 13px; }
.empty { padding: 22px 0; color: var(--muted); }
</style>