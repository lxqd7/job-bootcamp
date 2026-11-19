<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { loadSignStatus, signIn } from '../api/client'
import type { SignStatus } from '../api/types'
import { describeAuthError } from '../auth/session'

const status = ref<SignStatus | null>(null)
const loading = ref(true)
const signing = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const monthTitle = computed(() => {
  if (!status.value) return ''
  const [year, month] = status.value.month.split('-')
  return `${year} 年 ${Number(month)} 月`
})

const weekdays = ['一', '二', '三', '四', '五', '六', '日']

const calendarCells = computed(() => {
  if (!status.value) return [] as Array<number | null>
  const [year, month] = status.value.month.split('-').map(Number)
  const first = new Date(year, month - 1, 1)
  const offset = (first.getDay() + 6) % 7
  const days = new Date(year, month, 0).getDate()
  const cells: Array<number | null> = Array.from({ length: offset }, () => null)
  for (let day = 1; day <= days; day++) {
    cells.push(day)
  }
  while (cells.length % 7 !== 0) {
    cells.push(null)
  }
  return cells
})

const signedDays = computed(() => new Set(status.value?.days ?? []))

const todayDay = computed(() => {
  if (!status.value) return null
  return Number(status.value.date.slice(-2))
})

const nextMilestone = computed(() => {
  if (!status.value) return null
  const current = status.value.continuousDays
  if (current < 7) return 7
  if (current < 14) return 14
  if (current < 28) return 28
  return null
})

const nextMilestoneText = computed(() => {
  if (!nextMilestone.value) return '本月连续签到奖励已全部拿到。'
  return `再连续签到 ${nextMilestone.value - (status.value?.continuousDays ?? 0)} 天，可拿到第 ${nextMilestone.value} 天奖励。`
})

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    status.value = await loadSignStatus()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function handleSign(): Promise<void> {
  signing.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const result = await signIn()
    if (result.alreadySigned) {
      successMessage.value = '今天已经签到过了，明天再来。'
    } else if (result.bonusPoints > 0) {
      successMessage.value = `签到成功，今日 +${result.rewardPoints} 分（基础 ${result.basePoints} + 连续 ${result.continuousDays} 天奖励 ${result.bonusPoints}）。`
    } else {
      successMessage.value = `签到成功，今日 +${result.rewardPoints} 分，已连续 ${result.continuousDays} 天。`
    }
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    signing.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">积分与签到</p>
      <h1>每日签到</h1>
      <p class="lead">
        每个自然日可签到一次，基础 1 分；当月连续第 7/14/28 天额外奖励 10/20/40 分。跨月重新计算连续天数，不补签。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>

    <section v-if="loading" class="loading">正在读取签到状态…</section>

    <template v-else-if="status">
      <section class="stats" aria-label="积分概况">
        <div class="stat">
          <span>可用积分</span>
          <strong>{{ status.available }}</strong>
        </div>
        <div class="stat" :class="{ warn: status.debt > 0 }">
          <span>待扣回</span>
          <strong>{{ status.debt }}</strong>
        </div>
        <div class="stat">
          <span>{{ status.month }} 月得分</span>
          <strong>{{ status.currentMonthScore }}</strong>
        </div>
        <div class="stat">
          <span>连续签到</span>
          <strong>{{ status.continuousDays }} 天</strong>
        </div>
      </section>

      <p v-if="status.debt > 0" class="debt-note">
        有 {{ status.debt }} 分待扣回：后续获得积分会先抵扣，余额不会变成负数。
      </p>

      <section class="sign-card">
        <div>
          <p class="month">{{ monthTitle }}</p>
          <h2>{{ status.signedToday ? '今天已签到' : '今天还没有签到' }}</h2>
          <p class="muted small">
            {{ status.signedToday
              ? `今天获得 ${status.todayPoints} 分，本月已签到 ${status.monthSignedDays} 天。`
              : '签到只认服务器北京时间今天，不能补签或指定日期。' }}
          </p>
        </div>
        <button class="btn btn-primary sign-btn" type="button" :disabled="status.signedToday || signing" @click="handleSign">
          {{ signing ? '签到中…' : status.signedToday ? '今日已签到' : '立即签到 +1' }}
        </button>
      </section>

      <section class="calendar" aria-label="本月签到日历">
        <div class="calendar-head">
          <h2>本月签到</h2>
          <span class="muted small">{{ status.monthSignedDays }} 天 · {{ nextMilestoneText }}</span>
        </div>
        <div class="weekdays">
          <span v-for="day in weekdays" :key="day">{{ day }}</span>
        </div>
        <div class="days">
          <span
            v-for="(day, index) in calendarCells"
            :key="index"
            class="day"
            :class="{
              empty: day === null,
              signed: day !== null && signedDays.has(day),
              today: day !== null && day === todayDay
            }"
          >
            {{ day ?? '' }}
            <i v-if="day !== null && signedDays.has(day)" aria-label="已签到"></i>
          </span>
        </div>
      </section>
    </template>
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

.loading {
  margin-top: 40px;
  color: var(--muted);
}

.success {
  margin: 12px 0;
  color: var(--pine);
  font-size: 14px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  margin-top: 34px;
  border-top: 2px solid var(--ink);
  border-bottom: 1px solid var(--line);
}

.stat {
  padding: 16px 14px 16px 0;
  border-right: 1px solid var(--line);
}

.stat:last-child {
  border-right: 0;
}

.stat span {
  display: block;
  color: var(--muted);
  font-size: 12px;
}

.stat strong {
  display: block;
  margin-top: 4px;
  font-family: var(--serif);
  font-size: 28px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.stat.warn strong {
  color: var(--amber);
}

.debt-note {
  margin: 14px 0 0;
  padding-left: 14px;
  border-left: 2px solid var(--amber);
  color: var(--amber);
  font-size: 14px;
}

.sign-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 34px;
  padding: 22px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.sign-card h2 {
  margin: 2px 0 6px;
  font-family: var(--serif);
  font-size: 24px;
  font-weight: 500;
}

.month {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.06em;
}

.sign-btn {
  min-width: 150px;
}

.calendar {
  margin-top: 38px;
}

.calendar-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.calendar-head h2 {
  margin: 0;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.weekdays,
.days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}

.weekdays {
  margin-top: 14px;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
}

.weekdays span {
  padding: 6px 8px;
}

.days {
  border-left: 1px solid var(--line);
}

.day {
  position: relative;
  min-height: 58px;
  padding: 8px;
  border-right: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.day.empty {
  background: transparent;
}

.day.signed {
  background: rgba(23, 89, 76, 0.08);
  color: var(--pine);
  font-weight: 600;
}

.day.today {
  box-shadow: inset 0 0 0 2px var(--ink);
  color: var(--ink);
}

.day i {
  position: absolute;
  right: 9px;
  bottom: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--pine);
}

@media (max-width: 640px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stat:nth-child(2) {
    border-right: 0;
  }

  .stat:nth-child(3),
  .stat:nth-child(4) {
    border-top: 1px solid var(--line);
  }

  .day {
    min-height: 46px;
    padding: 5px;
  }
}
</style>