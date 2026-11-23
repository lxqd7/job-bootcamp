<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import {
  createTeacherAnswer,
  describeError,
  loadAnswerComments,
  loadTeacherCourses,
  loadTeacherQuestion,
  loadTeacherQuestions
} from '../api/client'
import type {
  TeacherCommentDetail,
  TeacherCourseItem,
  TeacherQuestionSummary,
  TeacherQuestionThread
} from '../api/types'

const courses = ref<TeacherCourseItem[]>([])
const courseId = ref('')
const questions = ref<TeacherQuestionSummary[]>([])
const total = ref(0)
const thread = ref<TeacherQuestionThread | null>(null)
const comments = ref<Record<string, TeacherCommentDetail[]>>({})
const answerText = ref('')
const loading = ref(true)
const errorMessage = ref('')
const notice = ref('')

const courseTitle = computed(() => courses.value.find(item => item.id === courseId.value)?.title ?? '负责课程')

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
  try {
    const result = await loadTeacherQuestions(courseId.value)
    questions.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeError(error)
  }
}

async function openQuestion(item: TeacherQuestionSummary): Promise<void> {
  errorMessage.value = ''
  notice.value = ''
  comments.value = {}
  try {
    thread.value = await loadTeacherQuestion(item.id)
    answerText.value = ''
  } catch (error) {
    errorMessage.value = describeError(error)
  }
}

async function answer(): Promise<void> {
  if (!thread.value || !answerText.value.trim()) return
  try {
    await createTeacherAnswer(thread.value.question.id, answerText.value.trim())
    answerText.value = ''
    thread.value = await loadTeacherQuestion(thread.value.question.id)
    await loadList()
    notice.value = '回答已提交，学生端会显示教师身份。'
  } catch (error) {
    errorMessage.value = describeError(error)
  }
}

async function loadCommentsFor(answerId: string): Promise<void> {
  try {
    const result = await loadAnswerComments(answerId)
    comments.value[answerId] = result.items
  } catch (error) {
    errorMessage.value = describeError(error)
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
      <p class="stage">教师工作区 · 答疑</p>
      <h1>负责课程答疑</h1>
      <p class="lead">
        只展示当前负责课程的问答；匿名问题显示“匿名学员”，教师默认不能追溯真实身份。
        教师回答会保存并展示 TEACHER 身份，页面没有正式课程编辑、采纳、悬赏或人工改分入口。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>

    <p v-if="courses.length === 0 && !loading" class="empty">当前账号没有负责课程。</p>
    <template v-else>
      <div class="toolbar">
        <label class="filter">
          <span>负责课程</span>
          <select v-model="courseId" :disabled="loading" @change="thread = null; loadList()">
            <option v-for="course in courses" :key="course.id" :value="course.id">{{ course.title }}</option>
          </select>
        </label>
        <button class="btn small-btn" type="button" :disabled="loading" @click="loadList">刷新</button>
      </div>

      <p v-if="loading" class="empty">正在读取问答…</p>
      <template v-else>
        <section class="questions">
          <div class="list-head"><h2>问题列表</h2><span class="muted">{{ total }} 个问题</span></div>
          <p v-if="questions.length === 0" class="empty">该课程还没有问题。</p>
          <button
            v-for="item in questions"
            :key="item.id"
            class="question-item"
            type="button"
            @click="openQuestion(item)"
          >
            <strong>{{ item.title }}</strong>
            <span class="meta">{{ item.authorDisplay }} · {{ item.answerCount }} 个回答 · {{ formatTime(item.createdAt) }}</span>
          </button>
        </section>

        <section v-if="thread" class="thread panel">
          <header class="thread-head">
            <div>
              <p class="stage">问题详情 · {{ courseTitle }}</p>
              <h2>{{ thread.question.title }}</h2>
              <p class="meta">{{ thread.question.authorDisplay }} · {{ formatTime(thread.question.createdAt) }}</p>
            </div>
          </header>
          <p class="body">{{ thread.question.body }}</p>

          <h3>回答（{{ thread.answersTotal }}）</h3>
          <article v-for="item in thread.answers" :key="item.id" class="answer">
            <header>
              <span class="author">{{ item.authorDisplay }}</span>
              <span v-if="item.authorRole === 'TEACHER'" class="teacher-badge">教师</span>
              <span class="muted">{{ formatTime(item.createdAt) }}</span>
            </header>
            <p class="body">{{ item.body }}</p>
            <button class="link-button" type="button" @click="loadCommentsFor(item.id)">
              查看评论（{{ item.commentCount }}）
            </button>
            <ul v-if="comments[item.id]?.length" class="comments">
              <li v-for="comment in comments[item.id]" :key="comment.id">
                <strong>{{ comment.authorDisplay }}</strong>
                <span v-if="comment.authorRole === 'TEACHER'" class="teacher-badge">教师</span>
                {{ comment.body }}
              </li>
            </ul>
          </article>

          <div class="answer-form">
            <h3>教师回答</h3>
            <textarea v-model="answerText" rows="4" placeholder="以教师身份回答…"></textarea>
            <button class="btn btn-primary" type="button" @click="answer">提交回答</button>
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
h3 { margin: 22px 0 10px; font-family: var(--serif); font-size: 18px; font-weight: 500; }
.lead { max-width: 74ch; color: var(--muted); line-height: 1.8; }
.toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 24px 0 14px; }
.filter { display: grid; gap: 4px; min-width: 260px; color: var(--muted); font-size: 12px; }
.filter select { padding: 7px 9px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.list-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.question-item { display: grid; width: 100%; gap: 4px; padding: 12px 0; border: 0; border-bottom: 1px solid var(--line); background: transparent; color: var(--ink); text-align: left; cursor: pointer; }
.question-item:hover strong { color: var(--pine); }
.meta, .muted { color: var(--muted); font-size: 12px; }
.panel { margin-top: 26px; padding-top: 18px; border-top: 2px solid var(--ink); }
.thread-head { display: flex; gap: 16px; justify-content: space-between; }
.body { line-height: 1.8; white-space: pre-wrap; }
.answer { padding: 14px 0; border-bottom: 1px solid var(--line); }
.answer header { display: flex; gap: 10px; align-items: baseline; }
.author { font-weight: 600; }
.teacher-badge { padding: 1px 6px; border: 1px solid var(--pine); color: var(--pine); font-size: 11px; }
.link-button { padding: 0; border: 0; background: transparent; color: var(--pine); cursor: pointer; font-size: 12px; }
.comments { margin: 8px 0 0; padding: 8px 12px; background: var(--paper-soft, #f6f4ee); list-style: none; font-size: 13px; line-height: 1.8; }
.answer-form { display: grid; gap: 10px; margin-top: 24px; }
.answer-form textarea { padding: 10px 12px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.answer-form .btn { justify-self: start; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.empty { padding: 20px 0; color: var(--muted); }
</style>