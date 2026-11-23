<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { describeAuthError } from '../auth/session'
import { loadMyNotes } from '../api/client'
import type { NoteSummary } from '../api/types'

const notes = ref<NoteSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const type = ref('')
const loading = ref(true)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

function typeLabel(value: string): string {
  return value === 'COLLECTED' ? '采集副本' : '原创'
}

function visibilityLabel(value: string): string {
  return value === 'COURSE_PUBLIC' ? '公开' : '私密'
}

function stateLabel(value: string): string {
  return value === 'DELETED' ? '已删除' : value === 'HIDDEN' ? '已隐藏' : '正常'
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await loadMyNotes(type.value, page.value, pageSize)
    notes.value = result.items
    total.value = result.total
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

function changeType(): void {
  page.value = 1
  void load()
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 19)
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">我的笔记</p>
      <h1>原创与采集</h1>
      <p class="lead">
        这里可以管理本人原创和采集副本；退款失去课程权益后仍可查看、修改、删除自己的笔记。
        采集副本始终私人，原文更新不会覆盖副本。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <div class="toolbar">
      <label class="filter">
        <span>类型</span>
        <select v-model="type" @change="changeType">
          <option value="">全部</option>
          <option value="ORIGINAL">原创</option>
          <option value="COLLECTED">采集副本</option>
        </select>
      </label>
      <button class="btn small-btn" type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <p v-if="loading" class="empty">正在读取我的笔记…</p>
    <template v-else>
      <p v-if="notes.length === 0" class="empty">还没有笔记。</p>
      <article v-for="item in notes" :key="item.id" class="note">
        <header>
          <span class="tag">{{ typeLabel(item.type) }}</span>
          <span class="tag">{{ visibilityLabel(item.visibility) }}</span>
          <span v-if="item.excellent" class="tag excellent">优秀</span>
          <span v-if="item.state !== 'VISIBLE'" class="tag warn">{{ stateLabel(item.state) }}</span>
          <span class="muted">{{ formatTime(item.updatedAt) }}</span>
        </header>
        <p class="excerpt">{{ item.excerpt }}</p>
        <div class="actions">
          <RouterLink :to="`/notes/${item.id}`">查看/编辑</RouterLink>
          <RouterLink v-if="item.state === 'VISIBLE'" :to="`/courses/${item.courseId}/notes`">课程笔记</RouterLink>
        </div>
      </article>
      <div v-if="totalPages > 1" class="pager">
        <button class="btn small-btn" type="button" :disabled="page <= 1" @click="page -= 1; load()">上一页</button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="page += 1; load()">下一页</button>
      </div>
    </template>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 10px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 44px); font-weight: 500; }
.lead { max-width: 72ch; color: var(--muted); line-height: 1.8; }
.toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 24px 0 12px; }
.filter { display: grid; gap: 4px; color: var(--muted); font-size: 12px; }
.filter select { padding: 7px 9px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.note { padding: 14px 0; border-bottom: 1px solid var(--line); }
.note header { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; }
.tag { padding: 1px 6px; border: 1px solid var(--line); color: var(--muted); font-size: 11px; }
.tag.excellent { border-color: var(--pine); background: var(--pine); color: var(--paper); }
.tag.warn { color: var(--amber); border-color: var(--amber); }
.muted { color: var(--muted); font-size: 12px; }
.excerpt { line-height: 1.8; white-space: pre-wrap; }
.actions { display: flex; gap: 16px; font-size: 13px; }
.small-btn { padding: 6px 12px; font-size: 13px; }
.pager { display: flex; gap: 12px; align-items: center; margin-top: 12px; color: var(--muted); font-size: 13px; }
.empty { padding: 20px 0; color: var(--muted); }
</style>