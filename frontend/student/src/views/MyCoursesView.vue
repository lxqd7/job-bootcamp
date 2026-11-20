<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { loadCourse, loadLearningCourse, loadMyCourses } from '../api/client'
import type { LearningCourse, MyCourseItem } from '../api/types'
import { describeAuthError } from '../auth/session'

interface CourseCard {
  item: MyCourseItem
  title: string
  coverUrl: string | null
  learning: LearningCourse | null
}

const cards = ref<CourseCard[]>([])
const loading = ref(true)
const errorMessage = ref('')

const sourceLabels: Record<string, string> = {
  FREE: '免费领取',
  COURSE_ORDER: '单课购买',
  ROUTE_ORDER: '路线获得'
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadMyCourses()
    cards.value = await Promise.all(result.items.map(async item => {
      let title = `课程 ${item.courseId}`
      let coverUrl: string | null = null
      let learning: LearningCourse | null = null
      try {
        const detail = await loadCourse(item.courseId)
        title = detail.title
        coverUrl = detail.coverUrl
      } catch {
        // 课程下架或详情不可用时仍展示权益摘要
      }
      try {
        learning = await loadLearningCourse(item.courseId)
      } catch {
        // 进度接口失败不阻塞我的课程列表
      }
      return { item, title, coverUrl, learning }
    }))
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

function progressPercent(card: CourseCard): number {
  if (!card.learning || card.learning.totalCount === 0) return 0
  return Math.round((card.learning.completedCount / card.learning.totalCount) * 100)
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">学习权益</p>
      <h1>我的课程</h1>
      <p class="lead">
        这里只展示已经获得有效来源的课程。免费领取、单课购买和路线获得分别标记来源；点开课程继续学习。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="loading" class="empty">正在读取我的课程…</p>

    <section v-else class="courses" aria-live="polite">
      <RouterLink
        v-for="card in cards"
        :key="card.item.courseId"
        class="course"
        :to="`/learning/courses/${card.item.courseId}`"
      >
        <div class="cover">
          <img v-if="card.coverUrl" :src="card.coverUrl" :alt="card.title" />
          <span v-else>{{ card.title.slice(0, 1) }}</span>
        </div>
        <div class="body">
          <div class="title-row">
            <h2>{{ card.title }}</h2>
            <span class="source">{{ sourceLabels[card.item.sourceType] ?? card.item.sourceType }}</span>
          </div>
          <p class="meta">
            获得时间 {{ card.item.grantedAt.replace('T', ' ').slice(0, 19) }}
          </p>
          <p v-if="card.learning" class="progress-text">
            已完成 {{ card.learning.completedCount }} / {{ card.learning.totalCount }} 节
          </p>
          <div v-if="card.learning" class="progress-track" aria-hidden="true">
            <span :style="{ width: `${progressPercent(card)}%` }"></span>
          </div>
          <p class="hint">继续学习 →</p>
        </div>
      </RouterLink>
      <p v-if="cards.length === 0" class="empty">
        还没有课程权益。可以先去
        <RouterLink to="/courses">课程目录</RouterLink>
        领取免费课程。
      </p>
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

.courses {
  margin-top: 34px;
  border-top: 2px solid var(--ink);
}

.course {
  display: flex;
  gap: 18px;
  padding: 18px 0;
  border-bottom: 1px solid var(--line);
  color: var(--ink);
  text-decoration: none;
}

.cover {
  display: grid;
  flex: 0 0 104px;
  width: 104px;
  height: 72px;
  place-items: center;
  overflow: hidden;
  background: var(--line);
  color: var(--muted);
  font-family: var(--serif);
  font-size: 28px;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.body {
  min-width: 0;
  flex: 1;
}

.title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.source {
  flex: 0 0 auto;
  padding: 1px 7px;
  border: 1px solid var(--pine);
  color: var(--pine);
  font-size: 11px;
}

.meta {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 12px;
}

.progress-text {
  margin: 7px 0 4px;
  color: var(--muted);
  font-size: 13px;
}

.progress-track {
  height: 4px;
  background: var(--line);
}

.progress-track span {
  display: block;
  height: 100%;
  background: var(--pine);
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

@media (max-width: 640px) {
  .course {
    display: block;
  }

  .cover {
    margin-bottom: 10px;
  }
}
</style>