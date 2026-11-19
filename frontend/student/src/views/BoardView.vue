<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { loadBoard } from '../api/client'
import type { BoardResponse } from '../api/types'
import { describeAuthError } from '../auth/session'

function currentMonth(): string {
  return new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    timeZone: 'Asia/Shanghai'
  }).format(new Date())
}

const month = ref(currentMonth())
const board = ref<BoardResponse | null>(null)
const page = ref(1)
const pageSize = 20
const loading = ref(true)
const errorMessage = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil((board.value?.total ?? 0) / pageSize)))

const monthTitle = computed(() => {
  const [year, value] = month.value.split('-')
  return `${year} 年 ${Number(value)} 月`
})

async function load(nextPage = page.value): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    board.value = await loadBoard(month.value, nextPage, pageSize)
    page.value = nextPage
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function gotoPage(next: number): Promise<void> {
  if (next < 1 || next > totalPages.value) return
  await load(next)
}

onMounted(() => load(1))
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">积分月榜</p>
      <h1>学习月榜</h1>
      <p class="lead">
        只统计当月获得积分的学生，按积分降序排列；同分按用户 ID 升序，不设并列名次。兑换不影响月榜。
      </p>
    </section>

    <div class="toolbar">
      <label class="field">
        <span>月份</span>
        <input v-model="month" type="month" @change="load(1)" />
      </label>
      <button class="btn" type="button" :disabled="loading" @click="load(page)">刷新</button>
      <span v-if="board" class="my-rank">
        我的排名：
        <strong>{{ board.myRank ? `第 ${board.myRank} 名` : '本月未上榜' }}</strong>
      </span>
    </div>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <section class="board" aria-live="polite">
      <div class="board-head" aria-hidden="true">
        <span>排名</span>
        <span>学生</span>
        <span>积分</span>
      </div>
      <div
        v-for="item in board?.items ?? []"
        :key="item.rank"
        class="board-row"
        :class="{ mine: board?.myRank === item.rank, top: item.rank <= 3 }"
      >
        <span class="rank">{{ item.rank }}</span>
        <span class="nickname">
          {{ item.nickname }}
          <em v-if="board?.myRank === item.rank">我</em>
        </span>
        <span class="score">{{ item.score }}</span>
      </div>
      <p v-if="loading" class="empty">正在读取月榜…</p>
      <p v-else-if="!board || board.items.length === 0" class="empty">
        {{ monthTitle }}还没有学生获得积分。
      </p>
    </section>

    <div class="pager">
      <span class="muted small">
        {{ monthTitle }} · 共 {{ board?.total ?? 0 }} 人 · 第 {{ page }} / {{ totalPages }} 页
      </span>
      <div class="pager-actions">
        <button class="btn small-btn" type="button" :disabled="page <= 1 || loading" @click="gotoPage(page - 1)">
          上一页
        </button>
        <button class="btn small-btn" type="button" :disabled="page >= totalPages || loading" @click="gotoPage(page + 1)">
          下一页
        </button>
      </div>
    </div>

    <p class="note">
      月榜只展示昵称和积分，不公开手机号；退款、兑换不会减少当月已获得积分。
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
  font-size: clamp(34px, 5vw, 48px);
  font-weight: 500;
}

.lead {
  max-width: 62ch;
  color: var(--muted);
  line-height: 1.75;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 14px 20px;
  margin-top: 30px;
  padding-top: 16px;
  border-top: 2px solid var(--ink);
}

.toolbar .field {
  display: grid;
  gap: 6px;
}

.toolbar .field span {
  color: var(--muted);
  font-size: 12px;
}

.toolbar input {
  padding: 7px 9px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
}

.my-rank {
  margin-left: auto;
  color: var(--muted);
  font-size: 14px;
}

.my-rank strong {
  color: var(--ink);
  font-weight: 600;
}

.board {
  margin-top: 26px;
  border-top: 1px solid var(--line);
}

.board-head,
.board-row {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr) 100px;
  gap: 12px;
  align-items: baseline;
}

.board-head {
  padding: 10px 0;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
}

.board-row {
  padding: 15px 0;
  border-bottom: 1px solid var(--line);
}

.board-row.top .rank {
  color: var(--pine);
}

.board-row.mine {
  background: rgba(23, 89, 76, 0.06);
  box-shadow: inset 3px 0 0 var(--pine);
  padding-left: 12px;
}

.rank {
  font-family: var(--serif);
  font-size: 20px;
  font-variant-numeric: tabular-nums;
}

.nickname {
  font-weight: 500;
}

.nickname em {
  margin-left: 8px;
  padding: 1px 6px;
  border: 1px solid var(--pine);
  color: var(--pine);
  font-size: 11px;
  font-style: normal;
}

.score {
  font-variant-numeric: tabular-nums;
  text-align: right;
  font-size: 18px;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
}

.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 20px;
}

.pager-actions {
  display: flex;
  gap: 8px;
}

.small-btn {
  padding: 5px 12px;
  font-size: 13px;
}

.note {
  margin-top: 28px;
  color: var(--muted);
  font-size: 13px;
}

@media (max-width: 640px) {
  .board-head,
  .board-row {
    grid-template-columns: 50px minmax(0, 1fr) 70px;
  }

  .my-rank {
    flex-basis: 100%;
    margin-left: 0;
  }
}
</style>