<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import {
  describeError,
  loadExcellentRewards,
  loadModerationAudits,
  revokeExcellentReward
} from '../api/client'
import type { AdminExcellentRewardView, AdminModerationAuditView } from '../api/types'

const rewards = ref<AdminExcellentRewardView[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const state = ref('')
const audits = ref<AdminModerationAuditView[]>([])
const auditTotal = ref(0)
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')
const notice = ref('')
const selected = ref<AdminExcellentRewardView | null>(null)
const reason = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function loadRewards(): Promise<void> {
  const result = await loadExcellentRewards(state.value, page.value, pageSize)
  rewards.value = result.items
  total.value = result.total
}

async function loadAudits(): Promise<void> {
  const result = await loadModerationAudits('REVOKE_EXCELLENT', 1, 20)
  audits.value = result.items
  auditTotal.value = result.total
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    await Promise.all([loadRewards(), loadAudits()])
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

function changeState(): void {
  page.value = 1
  void load()
}

function openRevoke(item: AdminExcellentRewardView): void {
  selected.value = item
  reason.value = ''
  notice.value = ''
  errorMessage.value = ''
}

async function submitRevoke(): Promise<void> {
  if (!selected.value || submitting.value) return
  if (!reason.value.trim()) {
    errorMessage.value = '撤回原因不能为空。'
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    await revokeExcellentReward(selected.value.noteId, reason.value.trim())
    notice.value = '已撤回优秀奖励；扣分、债务和原获奖月榜修正将在事件消费后完成。'
    selected.value = null
    await Promise.all([loadRewards(), loadAudits()])
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    submitting.value = false
  }
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 19)
}

function actionLabel(value: string): string {
  return value === 'REVOKE_EXCELLENT' ? '违规撤奖' : value === 'HIDE' ? '隐藏内容' : value === 'TRACE_AUTHOR' ? '匿名追溯' : value
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">管理员治理</p>
      <h1>优秀奖励与审计</h1>
      <p class="lead">
        这里只展示公开原创笔记的评优事实，不开放私人笔记正文浏览。违规撤回必须填写原因；
        撤回会扣回 5 分（不足记待扣回），并修正原获奖月份月榜。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>

    <div class="toolbar">
      <label class="filter">
        <span>奖励状态</span>
        <select v-model="state" @change="changeState">
          <option value="">全部</option>
          <option value="ACTIVE">有效展示</option>
          <option value="REVOKED">已撤回</option>
        </select>
      </label>
      <button class="btn small-btn" type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <section class="panel">
      <div class="list-head">
        <h2>评优记录</h2>
        <span class="muted">{{ total }} 条</span>
      </div>
      <p v-if="loading" class="empty">正在读取评优记录…</p>
      <p v-else-if="rewards.length === 0" class="empty">还没有评优记录。</p>
      <table v-else class="grid">
        <thead>
          <tr>
            <th>笔记</th>
            <th>作者</th>
            <th>教师</th>
            <th>版本</th>
            <th>评优时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in rewards" :key="String(item.noteId)">
            <td class="excerpt">{{ item.noteExcerpt }}</td>
            <td>{{ item.authorDisplay }}</td>
            <td>{{ item.teacherDisplay }}</td>
            <td>v{{ item.contentRevision }}</td>
            <td>{{ formatTime(item.awardedAt) }}</td>
            <td>
              <span v-if="item.revoked" class="tag warn">已撤回</span>
              <span v-else-if="item.excellent" class="tag">优秀</span>
              <span v-else class="tag muted-tag">展示已取消</span>
            </td>
            <td>
              <button
                v-if="!item.revoked"
                class="btn small-btn danger"
                type="button"
                @click="openRevoke(item)"
              >
                违规撤回
              </button>
              <span v-else class="muted">{{ item.revokeReason }}</span>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="totalPages > 1" class="pager">
        <button class="btn small-btn" type="button" :disabled="page <= 1" @click="page -= 1; loadRewards()">
          上一页
        </button>
        <span>{{ page }} / {{ totalPages }}</span>
        <button class="btn small-btn" type="button" :disabled="page >= totalPages" @click="page += 1; loadRewards()">
          下一页
        </button>
      </div>
    </section>

    <section v-if="selected" class="panel revoke-panel">
      <h2>撤回优秀奖励 · 笔记 #{{ selected.noteId }}</h2>
      <p class="muted">{{ selected.noteExcerpt }}</p>
      <label class="reason-field">
        <span>撤回原因（必填，审计可见）</span>
        <textarea v-model="reason" rows="3" maxlength="1000" placeholder="例如：内容抄袭，经核实违规"></textarea>
      </label>
      <div class="actions">
        <button class="btn btn-primary" type="button" :disabled="submitting" @click="submitRevoke">
          {{ submitting ? '撤回中…' : '确认撤回' }}
        </button>
        <button class="btn" type="button" :disabled="submitting" @click="selected = null">取消</button>
      </div>
    </section>

    <section class="panel">
      <div class="list-head">
        <h2>治理审计</h2>
        <span class="muted">最近 {{ auditTotal }} 条</span>
      </div>
      <p v-if="audits.length === 0" class="empty">还没有撤回审计记录。</p>
      <table v-else class="grid">
        <thead>
          <tr>
            <th>时间</th>
            <th>动作</th>
            <th>目标</th>
            <th>管理员</th>
            <th>原因</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in audits" :key="String(item.id)">
            <td>{{ formatTime(item.createdAt) }}</td>
            <td>{{ actionLabel(item.action) }}</td>
            <td>{{ item.targetType }} #{{ item.targetId }}</td>
            <td>{{ item.adminDisplay }}</td>
            <td>{{ item.reason }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </AppShell>
</template>

<style scoped>
.stage { margin: 0 0 10px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 46px); font-weight: 500; }
h2 { margin: 0; font-family: var(--serif); font-size: 21px; font-weight: 500; }
.lead { max-width: 74ch; color: var(--muted); line-height: 1.8; }
.toolbar { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 24px 0 14px; }
.filter { display: grid; gap: 4px; color: var(--muted); font-size: 12px; }
.filter select { padding: 7px 9px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.panel { margin-top: 26px; padding-top: 18px; border-top: 2px solid var(--ink); }
.list-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
.grid { width: 100%; border-collapse: collapse; font-size: 13px; }
.grid th, .grid td { padding: 9px 8px; border-bottom: 1px solid var(--line); text-align: left; vertical-align: top; }
.grid th { color: var(--muted); font-size: 11px; font-weight: 500; letter-spacing: .04em; }
.excerpt { max-width: 280px; line-height: 1.6; }
.muted { color: var(--muted); font-size: 12px; }
.tag { padding: 1px 6px; border: 1px solid var(--pine); color: var(--pine); font-size: 11px; white-space: nowrap; }
.tag.warn { border-color: var(--amber); color: var(--amber); }
.muted-tag { border-color: var(--line); color: var(--muted); }
.small-btn { padding: 6px 12px; font-size: 13px; }
.danger { color: var(--amber); border-color: var(--amber); }
.revoke-panel { padding: 18px; border: 1px solid var(--amber); background: var(--paper-soft, #f6f4ee); }
.reason-field { display: grid; gap: 6px; margin-top: 14px; color: var(--muted); font-size: 12px; }
.reason-field textarea { padding: 10px 12px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.actions { display: flex; gap: 10px; margin-top: 12px; }
.pager { display: flex; gap: 12px; align-items: center; margin-top: 12px; color: var(--muted); font-size: 13px; }
.empty { padding: 20px 0; color: var(--muted); }
</style>