<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeAuthError } from '../auth/session'
import { collectNote, deleteNote, loadNote, updateNote } from '../api/client'
import type { NoteDetail } from '../api/types'

const route = useRoute()
const router = useRouter()
const note = ref<NoteDetail | null>(null)
const body = ref('')
const visibility = ref<'PRIVATE' | 'COURSE_PUBLIC'>('PRIVATE')
const loading = ref(true)
const saving = ref(false)
const errorMessage = ref('')
const notice = ref('')

const noteId = computed(() => String(route.params.id))
const sourceNickname = computed(() => {
  const snapshot = note.value?.sourceAuthorSnapshot
  const nickname = snapshot?.nickname
  return typeof nickname === 'string' ? nickname : ''
})

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    note.value = await loadNote(noteId.value)
    body.value = note.value.body
    visibility.value = note.value.visibility === 'COURSE_PUBLIC' ? 'COURSE_PUBLIC' : 'PRIVATE'
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (!note.value || saving.value) return
  if (!body.value.trim()) {
    errorMessage.value = '笔记正文不能为空。'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    note.value = await updateNote(note.value.id, {
      body: body.value,
      visibility: visibility.value,
      revision: note.value.revision
    })
    notice.value = '笔记已保存。'
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    saving.value = false
  }
}

async function remove(): Promise<void> {
  if (!note.value) return
  try {
    await deleteNote(note.value.id)
    await router.replace('/me/notes')
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function collect(): Promise<void> {
  if (!note.value) return
  try {
    await collectNote(note.value.id)
    notice.value = '已采集为私人副本，可在“我的笔记”中查看。'
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(load)
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink to="/me/notes">← 我的笔记</RouterLink></p>
    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="loading" class="empty">正在读取笔记…</p>

    <template v-else-if="note">
      <section class="head">
        <p class="stage">
          {{ note.type === 'COLLECTED' ? '采集副本' : '原创笔记' }} ·
          {{ note.visibility === 'COURSE_PUBLIC' ? '课程公开' : '仅自己可见' }} ·
          {{ note.state }}
        </p>
        <h1>{{ note.mine ? '我的笔记' : '公开笔记' }}</h1>
        <p class="lead">
          作者：{{ note.authorDisplay }} · 更新于 {{ formatTime(note.updatedAt) }}
          <template v-if="note.courseId"> · <RouterLink :to="`/courses/${note.courseId}/notes`">课程笔记</RouterLink></template>
        </p>
        <p v-if="note.type === 'COLLECTED'" class="snapshot">
          采集自 {{ sourceNickname || '原文作者' }} 的公开笔记；原文后续修改不会覆盖这份副本。
        </p>
      </section>

      <section v-if="note.mine" class="editor">
        <label><span>正文</span><textarea v-model="body" rows="10" maxlength="20000"></textarea></label>
        <label class="inline">
          <span>可见性</span>
          <select v-model="visibility" :disabled="note.type === 'COLLECTED'">
            <option value="PRIVATE">仅自己可见</option>
            <option v-if="note.type !== 'COLLECTED'" value="COURSE_PUBLIC">课程公开</option>
          </select>
          <small v-if="note.type === 'COLLECTED'">采集副本不能公开</small>
        </label>
        <div class="actions">
          <button class="btn btn-primary" type="button" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存修改' }}
          </button>
          <button class="btn danger" type="button" @click="remove">删除笔记</button>
        </div>
      </section>
      <article v-else class="body">{{ note.body }}</article>

      <p v-if="!note.mine && note.state === 'VISIBLE' && note.visibility === 'COURSE_PUBLIC'" class="collect">
        <button class="btn" type="button" @click="collect">采集为私人副本</button>
      </p>
    </template>
  </AppShell>
</template>

<style scoped>
.back { margin: 0 0 18px; font-size: 14px; }
.stage { margin: 0 0 10px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 44px); font-weight: 500; }
.lead { color: var(--muted); line-height: 1.7; }
.snapshot { padding: 10px 12px; border-left: 3px solid var(--pine); color: var(--muted); font-size: 13px; line-height: 1.7; }
.editor { display: grid; gap: 12px; margin-top: 24px; }
.editor label { display: grid; gap: 6px; color: var(--muted); font-size: 12px; }
.editor textarea { padding: 12px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; line-height: 1.8; }
.inline { display: flex; gap: 10px; align-items: baseline; }
.inline select { padding: 6px 9px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.actions { display: flex; gap: 10px; }
.body { margin-top: 20px; line-height: 1.9; white-space: pre-wrap; }
.collect { margin-top: 20px; }
.danger { color: var(--amber); border-color: var(--amber); }
.empty { padding: 20px 0; color: var(--muted); }
</style>