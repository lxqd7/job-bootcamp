<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeAuthError } from '../auth/session'
import {
  createAnswer,
  createComment,
  createQuestion,
  deleteAnswer,
  deleteComment,
  deleteQuestion,
  likeTarget,
  loadComments,
  loadCourse,
  loadQuestion,
  loadQuestions,
  unlikeTarget
} from '../api/client'
import type {
  AnswerDetail,
  CommentDetail,
  CourseDetail,
  QuestionSummary,
  QuestionThread
} from '../api/types'

const route = useRoute()
const courseId = computed(() => String(route.params.id))
const course = ref<CourseDetail | null>(null)
const questions = ref<QuestionSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const thread = ref<QuestionThread | null>(null)
const comments = ref<Record<string, CommentDetail[]>>({})
const expanded = ref<Record<string, boolean>>({})
const loading = ref(true)
const errorMessage = ref('')
const notice = ref('')

const askForm = ref({ title: '', body: '', anonymous: false })
const answerText = ref('')
const commentText = ref<Record<string, string>>({})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  notice.value = ''
  thread.value = null
  comments.value = {}
  try {
    course.value = await loadCourse(courseId.value)
    await loadList()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function loadList(): Promise<void> {
  try {
    const result = await loadQuestions(courseId.value, page.value, pageSize)
    questions.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function ask(): Promise<void> {
  if (!askForm.value.title.trim() || !askForm.value.body.trim()) {
    errorMessage.value = '标题和正文不能为空。'
    return
  }
  try {
    await createQuestion(courseId.value, {
      title: askForm.value.title.trim(),
      body: askForm.value.body.trim(),
      anonymous: askForm.value.anonymous
    })
    askForm.value = { title: '', body: '', anonymous: false }
    notice.value = '问题已发布。'
    await loadList()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function selectQuestion(item: QuestionSummary): Promise<void> {
  errorMessage.value = ''
  notice.value = ''
  try {
    thread.value = await loadQuestion(item.id)
    answerText.value = ''
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function submitAnswer(): Promise<void> {
  if (!thread.value || !answerText.value.trim()) return
  try {
    await createAnswer(thread.value.question.id, answerText.value.trim())
    answerText.value = ''
    thread.value = await loadQuestion(thread.value.question.id)
    await loadList()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function toggleComments(answer: AnswerDetail): Promise<void> {
  expanded.value[answer.id] = !expanded.value[answer.id]
  if (expanded.value[answer.id]) {
    try {
      const result = await loadComments(answer.id)
      comments.value[answer.id] = result.items
    } catch (error) {
      errorMessage.value = describeAuthError(error)
    }
  }
}

async function submitComment(answer: AnswerDetail): Promise<void> {
  const text = (commentText.value[answer.id] ?? '').trim()
  if (!text) return
  try {
    await createComment(answer.id, text)
    commentText.value[answer.id] = ''
    const result = await loadComments(answer.id)
    comments.value[answer.id] = result.items
    if (thread.value) {
      thread.value = await loadQuestion(thread.value.question.id)
    }
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function toggleLike(type: 'ANSWER' | 'COMMENT', item: AnswerDetail | CommentDetail): Promise<void> {
  try {
    const result = item.liked
      ? await unlikeTarget(type, item.id)
      : await likeTarget(type, item.id)
    item.liked = result.liked
    item.likeCount = result.likeCount
    if (type === 'COMMENT' && 'answerId' in item) {
      const list = comments.value[item.answerId]
      if (list) {
        comments.value[item.answerId] = [...list]
      }
    }
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function removeQuestion(): Promise<void> {
  if (!thread.value || !thread.value.question.mine) return
  try {
    await deleteQuestion(thread.value.question.id)
    thread.value = null
    notice.value = '问题已删除。'
    await loadList()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function removeAnswer(answer: AnswerDetail): Promise<void> {
  if (!thread.value || !answer.mine) return
  try {
    await deleteAnswer(answer.id)
    thread.value = await loadQuestion(thread.value.question.id)
    await loadList()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function removeComment(answer: AnswerDetail, comment: CommentDetail): Promise<void> {
  if (!comment.mine) return
  try {
    await deleteComment(comment.id)
    const result = await loadComments(answer.id)
    comments.value[answer.id] = result.items
    if (thread.value) {
      thread.value = await loadQuestion(thread.value.question.id)
    }
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 19)
}

watch(() => route.params.id, load)
onMounted(load)
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink :to="`/courses/${courseId}`">← 返回课程</RouterLink></p>
    <section class="head">
      <p class="stage">课程问答</p>
      <h1>{{ course?.title ?? '课程问答' }}</h1>
      <p class="lead">
        有课程权益的学生可以提问、互相回答；教师只回答负责课程并显示教师身份。匿名问题显示“匿名学员”，不会返回作者 ID。
        问题删除或下架后整条线程不可公开访问。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="loading" class="empty">正在读取问答…</p>

    <template v-else>
      <section class="panel ask">
        <h2>提问</h2>
        <label><span>标题</span><input v-model="askForm.title" maxlength="100" placeholder="一句话描述问题" /></label>
        <label><span>正文</span><textarea v-model="askForm.body" rows="4" maxlength="10000"></textarea></label>
        <label class="inline"><input v-model="askForm.anonymous" type="checkbox" /> 匿名提问</label>
        <button class="btn btn-primary" type="button" @click="ask">发布问题</button>
      </section>

      <section class="questions">
        <div class="list-head"><h2>问题列表</h2><span class="muted">{{ total }} 个问题</span></div>
        <p v-if="questions.length === 0" class="empty">还没有问题，来提第一个问题吧。</p>
        <button
          v-for="item in questions"
          :key="item.id"
          class="question-item"
          type="button"
          @click="selectQuestion(item)"
        >
          <strong>{{ item.title }}</strong>
          <span class="meta">
            {{ item.authorDisplay }} · {{ formatTime(item.createdAt) }} · {{ item.answerCount }} 个回答
          </span>
        </button>
        <div v-if="totalPages > 1" class="pager">
          <button class="btn small-btn" type="button" :disabled="page <= 1" @click="page -= 1; loadList()">上一页</button>
          <span>{{ page }} / {{ totalPages }}</span>
          <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="page += 1; loadList()">下一页</button>
        </div>
      </section>

      <section v-if="thread" class="thread panel">
        <header class="thread-head">
          <div>
            <p class="stage">问题详情</p>
            <h2>{{ thread.question.title }}</h2>
            <p class="meta">{{ thread.question.authorDisplay }} · {{ formatTime(thread.question.createdAt) }}</p>
          </div>
          <button v-if="thread.question.mine" class="btn small-btn danger" type="button" @click="removeQuestion">
            删除问题
          </button>
        </header>
        <p class="body">{{ thread.question.body }}</p>

        <h3>回答（{{ thread.answersTotal }}）</h3>
        <article v-for="answer in thread.answers" :key="answer.id" class="answer">
          <header>
            <span class="author">{{ answer.authorDisplay }}</span>
            <span v-if="answer.authorRole === 'TEACHER'" class="teacher-badge">教师</span>
            <span class="muted">{{ formatTime(answer.createdAt) }}</span>
          </header>
          <p class="body">{{ answer.body }}</p>
          <div class="answer-actions">
            <button class="link-button" type="button" @click="toggleComments(answer)">
              {{ expanded[answer.id] ? '收起评论' : `评论（${answer.commentCount}）` }}
            </button>
            <button class="link-button" type="button" :disabled="answer.mine" @click="toggleLike('ANSWER', answer)">
              {{ answer.liked ? '取消赞' : '赞' }} {{ answer.likeCount }}
            </button>
            <button v-if="answer.mine" class="link-button danger" type="button" @click="removeAnswer(answer)">
              删除回答
            </button>
          </div>
          <div v-if="expanded[answer.id]" class="comments">
            <div v-for="comment in comments[answer.id] ?? []" :key="comment.id" class="comment">
              <span class="author">{{ comment.authorDisplay }}</span>
              <span v-if="comment.authorRole === 'TEACHER'" class="teacher-badge">教师</span>
              <span>{{ comment.body }}</span>
              <button class="link-button" type="button" :disabled="comment.mine" @click="toggleLike('COMMENT', comment)">
                {{ comment.liked ? '取消赞' : '赞' }} {{ comment.likeCount }}
              </button>
              <button v-if="comment.mine" class="link-button danger" type="button" @click="removeComment(answer, comment)">
                删除
              </button>
            </div>
            <div class="comment-form">
              <input v-model="commentText[answer.id]" placeholder="写评论…" @keyup.enter="submitComment(answer)" />
              <button class="btn small-btn" type="button" @click="submitComment(answer)">评论</button>
            </div>
          </div>
        </article>

        <div class="answer-form">
          <h3>写回答</h3>
          <textarea v-model="answerText" rows="3" placeholder="分享你的理解…"></textarea>
          <button class="btn btn-primary" type="button" @click="submitAnswer">提交回答</button>
        </div>
      </section>
    </template>
  </AppShell>
</template>

<style scoped>
.back { margin: 0 0 18px; font-size: 14px; }
.stage { margin: 0 0 10px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 44px); font-weight: 500; }
h2 { margin: 0; font-family: var(--serif); font-size: 22px; font-weight: 500; }
h3 { margin: 22px 0 10px; font-family: var(--serif); font-size: 18px; font-weight: 500; }
.lead { max-width: 72ch; color: var(--muted); line-height: 1.8; }
.panel { margin-top: 26px; padding-top: 18px; border-top: 2px solid var(--ink); }
.ask { display: grid; gap: 12px; }
.ask label { display: grid; gap: 5px; color: var(--muted); font-size: 12px; }
.ask input:not([type=checkbox]), .ask textarea, .answer-form textarea, .comment-form input { padding: 9px 11px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.inline { display: flex !important; gap: 8px; align-items: baseline; }
.list-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.question-item { display: grid; width: 100%; gap: 4px; padding: 12px 0; border: 0; border-bottom: 1px solid var(--line); background: transparent; color: var(--ink); text-align: left; cursor: pointer; }
.question-item:hover strong { color: var(--pine); }
.meta, .muted { color: var(--muted); font-size: 12px; }
.thread-head { display: flex; flex-wrap: wrap; gap: 16px; align-items: start; justify-content: space-between; }
.body { line-height: 1.8; white-space: pre-wrap; }
.answer { padding: 14px 0; border-bottom: 1px solid var(--line); }
.answer header { display: flex; gap: 10px; align-items: baseline; }
.author { font-weight: 600; }
.teacher-badge { padding: 1px 6px; border: 1px solid var(--pine); color: var(--pine); font-size: 11px; }
.answer-actions { display: flex; gap: 14px; align-items: baseline; }
.link-button { padding: 0; border: 0; background: transparent; color: var(--pine); cursor: pointer; font-size: 12px; }
.link-button.danger { color: var(--amber); }
.comments { margin-top: 10px; padding: 10px 12px; background: var(--paper-soft, #f6f4ee); }
.comment { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; padding: 4px 0; font-size: 13px; }
.comment-form { display: flex; gap: 8px; margin-top: 8px; }
.comment-form input { flex: 1; }
.answer-form { display: grid; gap: 10px; margin-top: 24px; }
.answer-form .btn { justify-self: start; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.danger { color: var(--amber); border-color: var(--amber); }
.pager { display: flex; gap: 12px; align-items: center; margin-top: 12px; color: var(--muted); font-size: 13px; }
.empty { padding: 20px 0; color: var(--muted); }
</style>