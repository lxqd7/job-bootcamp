<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { authorizeLesson, loadLearningCourse, reportProgress } from '../api/client'
import type { LearningCourse, LearningLesson, PlayAuthorization } from '../api/types'
import { describeAuthError } from '../auth/session'

const route = useRoute()
const course = ref<LearningCourse | null>(null)
const selectedLesson = ref<LearningLesson | null>(null)
const authorization = ref<PlayAuthorization | null>(null)
const videoSrc = ref('')
const videoElement = ref<HTMLVideoElement | null>(null)
const loading = ref(true)
const authorizing = ref(false)
const errorMessage = ref('')
const notice = ref('')
const completionNotice = ref('')
const pausedByExpiry = ref(false)
const pendingSeekMs = ref<number | null>(null)

let reportTimer: ReturnType<typeof setInterval> | null = null
let renewTimer: ReturnType<typeof setTimeout> | null = null
let lastReportedAt = 0

const courseId = computed(() => String(route.params.id))
const progressPercent = computed(() => {
  if (!course.value || course.value.totalCount === 0) return 0
  return Math.round((course.value.completedCount / course.value.totalCount) * 100)
})

const lessons = computed(() => course.value?.chapters.flatMap(chapter => chapter.lessons) ?? [])

const authExpiryText = computed(() => {
  if (!authorization.value) return ''
  return new Date(authorization.value.expiresAt).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
})

function clearTimers(): void {
  if (reportTimer) clearInterval(reportTimer)
  if (renewTimer) clearTimeout(renewTimer)
  reportTimer = null
  renewTimer = null
}

function chooseLesson(current: LearningLesson | null): LearningLesson | null {
  if (!course.value) return null
  if (current) {
    const found = lessons.value.find(lesson => lesson.id === current.id)
    if (found) return found
  }
  return lessons.value.find(lesson => lesson.mediaId) ?? lessons.value[0] ?? null
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  notice.value = ''
  completionNotice.value = ''
  clearTimers()
  authorization.value = null
  videoSrc.value = ''
  try {
    course.value = await loadLearningCourse(courseId.value)
    const next = chooseLesson(selectedLesson.value)
    selectedLesson.value = next
    if (next?.mediaId) {
      await authorize()
    }
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function authorize(): Promise<void> {
  const lesson = selectedLesson.value
  if (!lesson?.mediaId) return
  authorizing.value = true
  errorMessage.value = ''
  notice.value = ''
  pausedByExpiry.value = false
  const resumeMs = videoElement.value && !videoElement.value.paused
    ? Math.floor(videoElement.value.currentTime * 1000)
    : lesson.positionMs
  try {
    const result = await authorizeLesson(lesson.id)
    authorization.value = result
    if (!result.demoUrl) {
      videoSrc.value = ''
      errorMessage.value = '当前媒体不是 DEMO，VOD 播放器尚未接入。'
      return
    }
    pendingSeekMs.value = Math.max(0, resumeMs)
    videoSrc.value = result.demoUrl
    await nextTick()
    videoElement.value?.load()
    if (videoElement.value) {
      videoElement.value.currentTime = Math.min(resumeMs / 1000, (result.durationMs || 1) / 1000)
      videoElement.value.play().catch(() => undefined)
    }
    scheduleRenewal()
    scheduleReporting()
  } catch (error) {
    authorization.value = null
    videoSrc.value = ''
    errorMessage.value = describeAuthError(error)
  } finally {
    authorizing.value = false
  }
}

function scheduleRenewal(): void {
  if (renewTimer) clearTimeout(renewTimer)
  // 120 秒授权提前续期，避免播放中突然失效。
  renewTimer = setTimeout(async () => {
    if (!selectedLesson.value?.mediaId || !authorization.value) return
    if (videoElement.value?.ended) return
    const before = videoElement.value?.currentTime ?? 0
    await authorize()
    if (videoElement.value && !videoElement.value.paused) {
      videoElement.value.currentTime = before
    }
  }, 90_000)
}

function scheduleReporting(): void {
  if (!reportTimer) {
    reportTimer = setInterval(() => {
      if (videoElement.value && !videoElement.value.paused && !videoElement.value.ended) {
        void reportPosition()
      }
    }, 5_000)
  }
}

async function reportPosition(force = false): Promise<void> {
  const lesson = selectedLesson.value
  const video = videoElement.value
  const auth = authorization.value
  if (!lesson || !video || !auth) return
  const position = Math.min(Math.max(0, Math.floor(video.currentTime * 1000)), auth.durationMs)
  if (!force && Math.abs(position - lastReportedAt) < 1_000) return
  lastReportedAt = position
  try {
    const result = await reportProgress(lesson.id, position, auth.playSessionId)
    lesson.positionMs = position
    lesson.maxPositionMs = Math.max(lesson.maxPositionMs, position)
    lesson.completed = result.completed
    lesson.completedAt = result.completedAt
    if (course.value) {
      course.value.completedCount = result.courseCompletedCount
    }
    if (result.firstCompletion) {
      completionNotice.value = '首次完成，10 分学习奖励处理中；可在“积分”页查看到账结果。'
    }
  } catch (error) {
    authorization.value = null
    pausedByExpiry.value = true
    video.pause()
    errorMessage.value = describeAuthError(error)
  }
}

function onLoadedMetadata(): void {
  if (pendingSeekMs.value !== null && videoElement.value) {
    videoElement.value.currentTime = pendingSeekMs.value / 1000
    pendingSeekMs.value = null
  }
}

function onPlay(): void {
  if (!authorization.value && selectedLesson.value?.mediaId) {
    void authorize()
  }
}

function onPause(): void {
  void reportPosition(true)
}

function onEnded(): void {
  void reportPosition(true)
  notice.value = '本节播放结束。'
}

function onVideoError(): void {
  authorization.value = null
  videoSrc.value = ''
  pausedByExpiry.value = true
  errorMessage.value = '播放授权已失效或视频暂时不可用，请重新授权后继续。'
}

async function selectLesson(lesson: LearningLesson): Promise<void> {
  if (lesson.id === selectedLesson.value?.id) return
  if (videoElement.value) {
    videoElement.value.pause()
    videoElement.value.removeAttribute('src')
    videoElement.value.load()
  }
  clearTimers()
  authorization.value = null
  videoSrc.value = ''
  selectedLesson.value = lesson
  errorMessage.value = ''
  notice.value = ''
  completionNotice.value = ''
  if (lesson.mediaId) {
    await authorize()
  }
}

function reauthorize(): void {
  void authorize()
}

watch(() => route.params.id, load)
onMounted(load)
onBeforeUnmount(clearTimers)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">学习空间</p>
      <h1>{{ course?.title ?? '学习课程' }}</h1>
      <p v-if="course" class="lead">{{ course.description }}</p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="completionNotice" class="completion" role="status">{{ completionNotice }}</p>

    <p v-if="loading" class="empty">正在读取课程目录与进度…</p>

    <template v-else-if="course">
      <section class="summary">
        <div>
          <span>课程进度</span>
          <strong>{{ course.completedCount }} / {{ course.totalCount }}</strong>
        </div>
        <div class="track" aria-hidden="true">
          <span :style="{ width: `${progressPercent}%` }"></span>
        </div>
        <span class="percent">{{ progressPercent }}%</span>
      </section>

      <div class="layout">
        <aside class="catalog" aria-label="课程目录">
          <section v-for="chapter in course.chapters" :key="chapter.id" class="chapter">
            <h2>{{ chapter.title }}</h2>
            <button
              v-for="lesson in chapter.lessons"
              :key="lesson.id"
              class="lesson"
              :class="{ active: selectedLesson?.id === lesson.id, done: lesson.completed }"
              type="button"
              @click="selectLesson(lesson)"
            >
              <span class="lesson-title">{{ lesson.title }}</span>
              <span class="lesson-state">{{ lesson.completed ? '已完成' : lesson.mediaId ? '可学习' : '无视频' }}</span>
            </button>
          </section>
          <p v-if="course.chapters.length === 0" class="empty">课程暂时没有目录。</p>
        </aside>

        <main class="player-area">
          <template v-if="selectedLesson">
            <div class="player-head">
              <div>
                <p class="stage">正在学习</p>
                <h2>{{ selectedLesson.title }}</h2>
              </div>
              <span v-if="authorization" class="auth">
                授权有效至 {{ authExpiryText }}
              </span>
            </div>

            <div v-if="!selectedLesson.mediaId" class="no-video">
              该小节没有视频内容。
            </div>
            <div v-else class="video-wrap">
              <video
                v-if="videoSrc"
                ref="videoElement"
                class="video"
                controls
                playsinline
                preload="metadata"
                :src="videoSrc"
                @loadedmetadata="onLoadedMetadata"
                @play="onPlay"
                @pause="onPause"
                @ended="onEnded"
                @error="onVideoError"
              ></video>
              <div v-else class="no-video">
                <p>{{ authorizing ? '正在获取播放授权…' : '播放授权尚未建立。' }}</p>
                <button v-if="!authorizing" class="btn small-btn" type="button" @click="reauthorize">
                  重新授权
                </button>
              </div>
            </div>

            <p v-if="pausedByExpiry" class="expired">
              播放授权已失效，视频已暂停。点击“重新授权”继续学习。
            </p>
            <p class="help">
              上报只使用当前播放位置，服务端以视频真实时长判断是否完成；拖动进度条也可计入完成。
            </p>
          </template>
          <p v-else class="empty">请选择左侧小节开始学习。</p>
        </main>
      </div>
    </template>

    <p v-else class="empty">
      还没有该课程的有效学习权益。可以先到
      <RouterLink to="/courses">课程目录</RouterLink>
      查看课程。
    </p>
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
  font-size: clamp(34px, 5vw, 46px);
  font-weight: 500;
}

.lead {
  max-width: 64ch;
  color: var(--muted);
  line-height: 1.75;
}

.summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  margin-top: 28px;
  padding: 14px 0;
  border-top: 2px solid var(--ink);
  border-bottom: 1px solid var(--line);
}

.summary div:first-child {
  display: grid;
  gap: 2px;
}

.summary span {
  color: var(--muted);
  font-size: 12px;
}

.summary strong {
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.track {
  height: 6px;
  background: var(--line);
}

.track span {
  display: block;
  height: 100%;
  background: var(--pine);
}

.percent {
  font-variant-numeric: tabular-nums;
}

.layout {
  display: grid;
  grid-template-columns: minmax(190px, 0.72fr) minmax(0, 1.6fr);
  gap: 28px;
  margin-top: 28px;
}

.catalog {
  border-right: 1px solid var(--line);
  padding-right: 18px;
}

.chapter {
  margin-bottom: 22px;
}

.chapter h2 {
  margin: 0 0 8px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.06em;
}

.lesson {
  display: flex;
  width: 100%;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 0;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.lesson.active {
  color: var(--pine);
  font-weight: 600;
}

.lesson.done .lesson-state {
  color: var(--pine);
}

.lesson-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lesson-state {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: 11px;
}

.player-area {
  min-width: 0;
}

.player-head {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.player-head h2 {
  margin: 2px 0 0;
  font-family: var(--serif);
  font-size: 24px;
  font-weight: 500;
}

.auth {
  color: var(--muted);
  font-size: 12px;
}

.video-wrap {
  overflow: hidden;
  background: #111;
  aspect-ratio: 16 / 9;
}

.video {
  display: block;
  width: 100%;
  height: 100%;
}

.no-video {
  display: grid;
  min-height: 240px;
  place-items: center;
  padding: 24px;
  border: 1px dashed var(--line);
  color: var(--muted);
  text-align: center;
}

.small-btn {
  padding: 5px 12px;
  font-size: 13px;
}

.expired {
  margin: 12px 0 0;
  padding-left: 12px;
  border-left: 2px solid var(--amber);
  color: var(--amber);
  font-size: 13px;
}

.notice,
.completion {
  margin: 12px 0;
  color: var(--pine);
  font-size: 14px;
}

.help {
  margin-top: 14px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}

@media (max-width: 760px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .catalog {
    border-right: 0;
    padding-right: 0;
    border-bottom: 1px solid var(--line);
    padding-bottom: 16px;
  }
}
</style>