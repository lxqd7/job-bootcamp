<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import {
  cancelTeacherNote,
  describeError,
  evaluateTeacherNote,
  loadTeacherCourseNotes,
  loadTeacherCourses,
  loadTeacherNote
} from '../api/client'
import type { TeacherCourseItem, TeacherNoteDetail, TeacherNoteSummary } from '../api/types'

const courses = ref<TeacherCourseItem[]>([])
const courseId = ref('')
const notes = ref<TeacherNoteSummary[]>([])
const total = ref(0)
const detail = ref<TeacherNoteDetail | null>(null)
const loading = ref(true)
const loadingDetail = ref(false)
const evaluating = ref(false)
const errorMessage = ref('')
const notice = ref('')

async function loadCourses(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    courses.value = await loadTeacherCourses()
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
  if (!courseId.value) return
  detail.value = null
  try {
    const result = await loadTeacherCourseNotes(courseId.value)
    notes.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeError(error)
  }
}

async function openNote(item: TeacherNoteSummary): Promise<void> {
  loadingDetail.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    detail.value = await loadTeacherNote(item.id)
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loadingDetail.value = false
  }
}

async function evaluate(): Promise<void> {
  if (!detail.value || evaluating.value) return
  evaluating.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const updated = await evaluateTeacherNote(detail.value.id, detail.value.revision)
    notice.value = '已评选为优秀笔记；首次评优奖励作者 5 分，重复评优不再发奖。'
    await loadList()
    detail.value = updated
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    evaluating.value = false
  }
}

async function cancelExcellent(): Promise<void> {
  if (!detail.value || evaluating.value) return
  evaluating.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const updated = await cancelTeacherNote(detail.value.id)
    notice.value = '已取消优秀展示；普通取消不扣分。'
    await loadList()
    detail.value = updated
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    evaluating.value = false
  }
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(loadCourses)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">教师工作区 · 公开笔记</p>
      <h1>负责课程公开笔记</h1>
      <p class="lead">
        只展示当前负责课程中公开且未隐藏的原创笔记；私人笔记和采集副本不会出现在这里。
        教师可将公开原创笔记评选为优秀，首次评选奖励作者 5 分；取消只撤展示、不扣分，违规撤回由管理员处理。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>

    <p v-if="courses.length === 0 && !loading" class="empty">当前账号没有负责课程。</p>
    <template v-else>
      <div class="toolbar">
        <label class="filter">
          <span>负责课程</span>
          <select v-model="courseId" :disabled="loading" @change="loadList">
            <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.title }}</option>
          </select>
        </label>
        <button class="btn small-btn" type="button" :disabled="loading" @click="loadList">刷新</button>
      </div>

      <p v-if="loading" class="empty">正在读取公开笔记…</p>
      <template v-else>
        <p v-if="notes.length === 0" class="empty">该课程还没有公开笔记。</p>
        <article v-for="item in notes" :key="item.id" class="note">
          <header>
            <strong>{{ item.authorDisplay }}</strong>
            <span v-if="item.excellent" class="tag">优秀</span>
            <span class="muted">{{ formatTime(item.updatedAt) }}</span>
          </header>
          <p class="excerpt">{{ item.excerpt }}</p>
          <button class="link-button" type="button" :disabled="loadingDetail" @click="openNote(item)">
            查看全文
          </button>
        </article>

        <section v-if="detail" class="detail">
          <header class="detail-head">
            <div>
              <p class="stage">只读笔记</p>
              <h2>{{ detail.authorDisplay }} 的公开笔记</h2>
              <p class="muted">
                {{ formatTime(detail.updatedAt) }} · {{ detail.state }}
                <span v-if="detail.excellent" class="tag">优秀</span>
                <span v-if="detail.excellentRevision !== null" class="muted">
                  · 获评版本 v{{ detail.excellentRevision }}
                </span>
              </p>
            </div>
            <button class="btn small-btn" type="button" @click="detail = null">关闭</button>
          </header>
          <p class="body">{{ detail.body }}</p>
          <div class="evaluate-actions">
            <button
              v-if="!detail.excellent"
              class="btn btn-primary small-btn"
              type="button"
              :disabled="evaluating"
              @click="evaluate"
            >
              {{ evaluating ? '处理中…' : '评选为优秀' }}
            </button>
            <button
              v-else
              class="btn small-btn"
              type="button"
              :disabled="evaluating"
              @click="cancelExcellent"
            >
              {{ evaluating ? '处理中…' : '取消优秀' }}
            </button>
            <span class="muted">评优锁定当前内容版本，首次奖励 5 分。</span>
          </div>
        </section>
      </template>
    </template>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 10px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 46px); font-weight: 500; }
h2 { margin: 0; font-family: var(--serif); font-size: 22px; font-weight: 500; }
.lead { max-width: 74ch; color: var(--muted); line-height: 1.8; }
.toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 24px 0 14px; }
.filter { display: grid; gap: 4px; min-width: 260px; color: var(--muted); font-size: 12px; }
.filter select { padding: 7px 9px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.note { padding: 14px 0; border-bottom: 1px solid var(--line); }
.note header { display: flex; gap: 10px; align-items: baseline; }
.muted { color: var(--muted); font-size: 12px; }
.tag { padding: 1px 6px; border: 1px solid var(--pine); color: var(--pine); font-size: 11px; }
.evaluate-actions { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--line); }
.excerpt { line-height: 1.8; white-space: pre-wrap; }
.link-button { padding: 0; border: 0; background: transparent; color: var(--pine); cursor: pointer; font-size: 13px; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.detail { margin-top: 28px; padding-top: 18px; border-top: 2px solid var(--ink); }
.detail-head { display: flex; gap: 16px; justify-content: space-between; align-items: start; }
.body { line-height: 1.9; white-space: pre-wrap; }
.empty { padding: 20px 0; color: var(--muted); }
</style>