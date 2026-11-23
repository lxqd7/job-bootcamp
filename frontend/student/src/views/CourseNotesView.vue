<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeAuthError } from '../auth/session'
import { collectNote, createNote, loadCourse, loadCourseNotes } from '../api/client'
import type { CourseDetail, NoteSummary } from '../api/types'

const route = useRoute()
const courseId = computed(() => String(route.params.id))
const course = ref<CourseDetail | null>(null)
const notes = ref<NoteSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(true)
const errorMessage = ref('')
const notice = ref('')
const form = ref({ body: '', visibility: 'PRIVATE' as 'PRIVATE' | 'COURSE_PUBLIC' })

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
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
    const result = await loadCourseNotes(courseId.value, page.value, pageSize)
    notes.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function create(): Promise<void> {
  if (!form.value.body.trim()) {
    errorMessage.value = '笔记正文不能为空。'
    return
  }
  try {
    await createNote(courseId.value, {
      body: form.value.body.trim(),
      visibility: form.value.visibility
    })
    form.value.body = ''
    notice.value = '笔记已保存。'
    await loadList()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function collect(item: NoteSummary): Promise<void> {
  try {
    await collectNote(item.id)
    notice.value = '已采集为私人副本，可在“我的笔记”中管理。'
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
      <p class="stage">公开笔记</p>
      <h1>{{ course?.title ?? '课程笔记' }}</h1>
      <p class="lead">
        公开笔记只对拥有课程权益的学生和负责课程教师可见。采集会保存当时的正文和作者快照为私人副本，原文更新或删除不会覆盖副本。
      </p>
      <p><RouterLink to="/me/notes">前往我的笔记</RouterLink></p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="loading" class="empty">正在读取笔记…</p>

    <template v-else>
      <section class="panel create">
        <h2>写笔记</h2>
        <textarea v-model="form.body" rows="4" maxlength="20000" placeholder="记录你的理解…"></textarea>
        <label class="inline">
          <span>可见性</span>
          <select v-model="form.visibility">
            <option value="PRIVATE">仅自己可见</option>
            <option value="COURSE_PUBLIC">课程公开</option>
          </select>
        </label>
        <button class="btn btn-primary" type="button" @click="create">保存笔记</button>
      </section>

      <section class="notes">
        <div class="list-head"><h2>公开笔记</h2><span class="muted">{{ total }} 篇</span></div>
        <p v-if="notes.length === 0" class="empty">还没有公开笔记。</p>
        <article v-for="item in notes" :key="item.id" class="note">
          <header>
            <strong>{{ item.authorDisplay }}</strong>
            <span v-if="item.excellent" class="tag excellent">优秀</span>
            <span class="muted">{{ formatTime(item.updatedAt) }}</span>
            <span v-if="item.mine" class="tag">我的</span>
          </header>
          <p class="excerpt">{{ item.excerpt }}</p>
          <div class="actions">
            <RouterLink :to="`/notes/${item.id}`">查看全文</RouterLink>
            <button v-if="!item.mine" class="link-button" type="button" @click="collect(item)">采集为私人副本</button>
          </div>
        </article>
        <div v-if="totalPages > 1" class="pager">
          <button class="btn small-btn" type="button" :disabled="page <= 1" @click="page -= 1; loadList()">上一页</button>
          <span>{{ page }} / {{ totalPages }}</span>
          <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="page += 1; loadList()">下一页</button>
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
.lead { max-width: 72ch; color: var(--muted); line-height: 1.8; }
.panel { margin-top: 24px; padding-top: 18px; border-top: 2px solid var(--ink); }
.create { display: grid; gap: 12px; }
.create textarea { padding: 10px 12px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.inline { display: flex; gap: 10px; align-items: baseline; color: var(--muted); font-size: 12px; }
.inline select { padding: 6px 9px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.list-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.note { padding: 14px 0; border-bottom: 1px solid var(--line); }
.note header { display: flex; gap: 10px; align-items: baseline; }
.muted { color: var(--muted); font-size: 12px; }
.tag { padding: 1px 6px; border: 1px solid var(--pine); color: var(--pine); font-size: 11px; }
.tag.excellent { background: var(--pine); color: var(--paper); }
.excerpt { color: var(--ink); line-height: 1.8; white-space: pre-wrap; }
.actions { display: flex; gap: 16px; align-items: baseline; font-size: 13px; }
.link-button { padding: 0; border: 0; background: transparent; color: var(--pine); cursor: pointer; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.pager { display: flex; gap: 12px; align-items: center; margin-top: 12px; color: var(--muted); font-size: 13px; }
.empty { padding: 20px 0; color: var(--muted); }
</style>