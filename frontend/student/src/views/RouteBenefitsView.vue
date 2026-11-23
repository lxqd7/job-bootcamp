<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeAuthError } from '../auth/session'
import { loadRoute, loadRouteBenefits } from '../api/client'
import type { RouteDetail, StudentBenefit } from '../api/types'

const route = useRoute()
const routeId = computed(() => String(route.params.id))
const detail = ref<RouteDetail | null>(null)
const benefits = ref<StudentBenefit[]>([])
const courseId = ref('')
const loading = ref(true)
const errorMessage = ref('')

const selectedMember = computed(() =>
  detail.value?.members.find(member => String(member.courseId) === courseId.value) ?? null
)

function kindLabel(item: StudentBenefit): string {
  return item.kind === 'BENEFIT_CODE' ? '福利代码题' : '福利选择题'
}

function statusLabel(item: StudentBenefit): string {
  if (item.status === 'OFFLINE') return '已下架（历史作答可查看）'
  if (item.currentVersionSubmitted) return '当前版本已作答'
  if (item.openAttemptId) return '作答中'
  return '未作答'
}

async function loadBenefits(): Promise<void> {
  if (!courseId.value) {
    benefits.value = []
    return
  }
  errorMessage.value = ''
  try {
    benefits.value = await loadRouteBenefits(routeId.value, courseId.value)
  } catch (error) {
    benefits.value = []
    errorMessage.value = describeAuthError(error)
  }
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  benefits.value = []
  try {
    detail.value = await loadRoute(routeId.value)
    const queryCourse = String(route.query.courseId ?? '')
    const hasQuery = detail.value.members.some(member => String(member.courseId) === queryCourse)
    courseId.value = hasQuery ? queryCourse : String(detail.value.members[0]?.courseId ?? '')
    await loadBenefits()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

function changeCourse(): void {
  void loadBenefits()
}

watch(() => route.query.courseId, load)
onMounted(load)
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink :to="`/routes/${routeId}`">← 返回路线详情</RouterLink></p>
    <section class="head">
      <p class="stage">路线福利</p>
      <h1>{{ detail?.title ?? '课程福利题' }}</h1>
      <p class="lead">
        福利题按课程展示。访问需要同时拥有该课程的有效学习权益，以及这条路线在题目绑定 epoch 上的有效福利资格。
        题目下架后不能再开始新作答，已有记录仍可查看。
      </p>
    </section>

    <p v-if="loading" class="empty">正在读取路线福利题…</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="detail">
      <label class="field course-select">
        <span>课程</span>
        <select v-model="courseId" @change="changeCourse">
          <option v-for="member in detail.members" :key="member.courseId" :value="String(member.courseId)">
            {{ member.title }}
          </option>
        </select>
      </label>

      <section v-if="benefits.length > 0" class="benefit-list">
        <article v-for="item in benefits" :key="item.id" class="benefit">
          <div>
            <p class="kind">{{ kindLabel(item) }} · v{{ item.currentVersionNo ?? '—' }}</p>
            <h2>{{ selectedMember?.title ?? '课程福利题' }}</h2>
            <p class="state">{{ statusLabel(item) }}</p>
          </div>
          <RouterLink
            class="btn small-btn"
            :to="`/benefit-assignments/${item.id}?routeId=${routeId}`"
          >
            {{ item.status === 'OFFLINE' ? '查看历史' : item.currentVersionSubmitted ? '再练一次' : '开始作答' }}
          </RouterLink>
        </article>
      </section>
      <p v-else class="empty">
        当前课程没有可访问的福利题，或你还不具备这条路线在该题绑定 epoch 上的有效福利资格。
      </p>

      <p class="fine-print">
        福利题自愿作答，不计入视频学习进度或退款进度；如果路线已移除该课程，历史绑定 epoch 上的已有题目仍按原资格保留。
      </p>
    </template>
  </AppShell>
</template>

<style scoped>
.back { margin: 0 0 18px; font-size: 14px; }
.stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 46px); font-weight: 500; }
.lead { max-width: 70ch; color: var(--muted); line-height: 1.8; }
.course-select { display: grid; gap: 6px; max-width: 360px; margin: 26px 0 18px; color: var(--muted); font-size: 12px; }
.course-select select { padding: 8px 10px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.benefit-list { border-top: 2px solid var(--ink); }
.benefit { display: flex; flex-wrap: wrap; gap: 18px; align-items: center; justify-content: space-between; padding: 18px 0; border-bottom: 1px solid var(--line); }
.kind { margin: 0 0 4px; color: var(--muted); font-size: 12px; letter-spacing: .04em; }
.benefit h2 { margin: 0 0 4px; font-family: var(--serif); font-size: 21px; font-weight: 500; }
.state { margin: 0; color: var(--muted); font-size: 13px; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.empty { padding: 24px 0; color: var(--muted); line-height: 1.8; }
.fine-print { margin-top: 22px; color: var(--muted); font-size: 12px; line-height: 1.8; }
</style>