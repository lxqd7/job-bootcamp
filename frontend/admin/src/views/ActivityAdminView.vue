<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import {
  addActivityItem,
  changeActivityState,
  createActivity,
  createCouponRule,
  describeError,
  loadAdminActivities,
  loadCouponRules
} from '../api/client'
import type { ActivityView, CouponRuleView } from '../api/types'

const rules = ref<CouponRuleView[]>([])
const activities = ref<ActivityView[]>([])
const loading = ref(true)
const busy = ref(false)
const errorMessage = ref('')
const notice = ref('')
const selectedActivityId = ref('')

const ruleForm = ref({
  ruleKey: `ACTIVITY_${Date.now()}`,
  type: 'FULL_REDUCTION',
  scope: 'ALL',
  targetId: '',
  thresholdCent: 1000,
  amountCent: 200,
  payRateBps: 9000,
  maxDiscountCent: 1000,
  validityType: 'DAYS',
  validDays: 30,
  validFrom: localInput(new Date()),
  validUntil: localInput(new Date(Date.now() + 30 * 86400000))
})
const activityForm = ref({
  name: `领券活动 ${new Date().toLocaleDateString('zh-CN')}`,
  openAt: localInput(new Date()),
  closeAt: localInput(new Date(Date.now() + 7 * 86400000))
})
const itemForm = ref({ ruleId: '', stockTotal: 100, perUserLimit: 1 })

const selectedActivity = computed(() =>
  activities.value.find(item => String(item.id) === selectedActivityId.value) ?? null
)

function localInput(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function money(value: number | null): string {
  return `¥${((value ?? 0) / 100).toFixed(2)}`
}

function ruleLabel(rule: CouponRuleView | null): string {
  if (!rule) return '规则不可用'
  if (rule.type === 'DISCOUNT') {
    return `折扣券 ${rule.payRateBps} bps，上限 ${money(rule.maxDiscountCent)}`
  }
  if (rule.type === 'NO_THRESHOLD') return `无门槛减 ${money(rule.amountCent)}`
  return `满 ${money(rule.thresholdCent)} 减 ${money(rule.amountCent)}`
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [rulePage, activityPage] = await Promise.all([loadCouponRules(), loadAdminActivities()])
    rules.value = rulePage.items
    activities.value = activityPage.items
    if (!selectedActivityId.value && activities.value[0]) {
      selectedActivityId.value = activities.value[0].id
    }
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

async function submitRule(): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const form = ruleForm.value
    const payload: Record<string, unknown> = {
      ruleKey: form.ruleKey,
      type: form.type,
      scope: form.scope,
      thresholdCent: Number(form.thresholdCent),
      validityType: form.validityType
    }
    if (form.scope !== 'ALL') payload.targetId = Number(form.targetId)
    if (form.type === 'DISCOUNT') {
      payload.payRateBps = Number(form.payRateBps)
      payload.maxDiscountCent = Number(form.maxDiscountCent)
    } else {
      payload.amountCent = Number(form.amountCent)
    }
    if (form.validityType === 'DAYS') {
      payload.validDays = Number(form.validDays)
    } else {
      payload.validFrom = form.validFrom
      payload.validUntil = form.validUntil
    }
    await createCouponRule(payload)
    notice.value = '券规则已创建。'
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    busy.value = false
  }
}

async function submitActivity(): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const created = await createActivity(activityForm.value)
    selectedActivityId.value = created.id
    notice.value = '活动草稿已创建，请添加券项后激活。'
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    busy.value = false
  }
}

async function submitItem(): Promise<void> {
  if (!selectedActivity.value || busy.value) return
  busy.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const activity = await addActivityItem(selectedActivity.value.id, {
      ruleId: Number(itemForm.value.ruleId),
      stockTotal: Number(itemForm.value.stockTotal),
      perUserLimit: Number(itemForm.value.perUserLimit)
    })
    activities.value = activities.value.map(item => item.id === activity.id ? activity : item)
    notice.value = '活动券项已添加。'
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    busy.value = false
  }
}

async function changeState(action: 'activate' | 'pause' | 'end'): Promise<void> {
  if (!selectedActivity.value || busy.value) return
  busy.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const activity = await changeActivityState(selectedActivity.value.id, action, selectedActivity.value.revision)
    activities.value = activities.value.map(item => item.id === activity.id ? activity : item)
    notice.value = `活动已${action === 'activate' ? '激活' : action === 'pause' ? '暂停' : '结束'}。`
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">营销配置</p>
      <h1>优惠活动与券规则</h1>
      <p class="lead">
        券规则是版本化配置，首次发券后核心字段不可改；活动库存和个人限领在领取事务内扣减。这里只配置发放，不涉及结算算法。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="loading" class="empty">正在读取配置…</p>

    <template v-else>
      <section class="panel">
        <h2>1. 新建券规则</h2>
        <div class="form-grid">
          <label class="field"><span>规则键</span><input v-model="ruleForm.ruleKey" /></label>
          <label class="field">
            <span>类型</span>
            <select v-model="ruleForm.type">
              <option value="FULL_REDUCTION">满减</option>
              <option value="NO_THRESHOLD">无门槛</option>
              <option value="DISCOUNT">折扣</option>
            </select>
          </label>
          <label class="field">
            <span>范围</span>
            <select v-model="ruleForm.scope">
              <option value="ALL">全场</option>
              <option value="COURSE">课程</option>
              <option value="CATEGORY">分类</option>
              <option value="ROUTE">路线</option>
            </select>
          </label>
          <label v-if="ruleForm.scope !== 'ALL'" class="field"><span>目标 ID</span><input v-model="ruleForm.targetId" /></label>
          <label class="field"><span>门槛（分）</span><input v-model.number="ruleForm.thresholdCent" type="number" /></label>
          <label v-if="ruleForm.type !== 'DISCOUNT'" class="field"><span>优惠（分）</span><input v-model.number="ruleForm.amountCent" type="number" /></label>
          <label v-if="ruleForm.type === 'DISCOUNT'" class="field"><span>支付比例 bps</span><input v-model.number="ruleForm.payRateBps" type="number" /></label>
          <label v-if="ruleForm.type === 'DISCOUNT'" class="field"><span>最高优惠（分）</span><input v-model.number="ruleForm.maxDiscountCent" type="number" /></label>
          <label class="field">
            <span>有效期</span>
            <select v-model="ruleForm.validityType">
              <option value="DAYS">取得后 N 天</option>
              <option value="FIXED">固定起止</option>
            </select>
          </label>
          <label v-if="ruleForm.validityType === 'DAYS'" class="field"><span>有效天数</span><input v-model.number="ruleForm.validDays" type="number" /></label>
          <label v-if="ruleForm.validityType === 'FIXED'" class="field"><span>生效时间</span><input v-model="ruleForm.validFrom" type="datetime-local" /></label>
          <label v-if="ruleForm.validityType === 'FIXED'" class="field"><span>失效时间</span><input v-model="ruleForm.validUntil" type="datetime-local" /></label>
        </div>
        <button class="btn btn-primary" type="button" :disabled="busy" @click="submitRule">创建规则</button>
      </section>

      <section class="panel">
        <h2>2. 新建活动草稿</h2>
        <div class="form-grid">
          <label class="field"><span>活动名</span><input v-model="activityForm.name" /></label>
          <label class="field"><span>开放时间</span><input v-model="activityForm.openAt" type="datetime-local" /></label>
          <label class="field"><span>结束时间</span><input v-model="activityForm.closeAt" type="datetime-local" /></label>
        </div>
        <button class="btn" type="button" :disabled="busy" @click="submitActivity">创建活动</button>
      </section>

      <section class="panel">
        <h2>3. 活动与券项</h2>
        <div v-if="activities.length === 0" class="empty">还没有活动。</div>
        <article v-for="activity in activities" :key="activity.id" class="activity-row" :class="{ selected: String(activity.id) === selectedActivityId }" @click="selectedActivityId = activity.id">
          <div>
            <strong>{{ activity.name }}</strong>
            <span class="meta">状态 {{ activity.state }} · {{ activity.openAt.replace('T', ' ').slice(0, 16) }} 至 {{ activity.closeAt.replace('T', ' ').slice(0, 16) }}</span>
          </div>
          <span class="meta">{{ activity.items.length }} 个券项</span>
        </article>

        <div v-if="selectedActivity" class="selected-panel">
          <h3>{{ selectedActivity.name }} · 券项</h3>
          <div v-for="item in selectedActivity.items" :key="item.id" class="item-row">
            <span>{{ ruleLabel(item.rule) }}</span>
            <span>库存 {{ item.issuedCount }} / {{ item.stockTotal }} · 每人 {{ item.perUserLimit }}</span>
          </div>
          <div class="form-grid add-item">
            <label class="field">
              <span>券规则</span>
              <select v-model="itemForm.ruleId">
                <option value="">请选择</option>
                <option v-for="rule in rules" :key="rule.id" :value="rule.id">
                  {{ rule.ruleKey }} v{{ rule.versionNo }} · {{ ruleLabel(rule) }}
                </option>
              </select>
            </label>
            <label class="field"><span>总库存</span><input v-model.number="itemForm.stockTotal" type="number" /></label>
            <label class="field"><span>个人限领</span><input v-model.number="itemForm.perUserLimit" type="number" /></label>
          </div>
          <button class="btn small-btn" type="button" :disabled="busy || !itemForm.ruleId" @click="submitItem">添加券项</button>
          <div class="state-actions">
            <button class="btn small-btn" type="button" :disabled="busy" @click="changeState('activate')">激活</button>
            <button class="btn small-btn" type="button" :disabled="busy" @click="changeState('pause')">暂停</button>
            <button class="btn small-btn" type="button" :disabled="busy" @click="changeState('end')">结束</button>
          </div>
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
  font-size: clamp(32px, 5vw, 44px);
  font-weight: 500;
}

.lead {
  max-width: 70ch;
  color: var(--muted);
  line-height: 1.75;
}

.panel {
  margin-top: 28px;
  padding-top: 18px;
  border-top: 2px solid var(--ink);
}

.panel h2 {
  margin: 0 0 14px;
  font-family: var(--serif);
  font-size: 23px;
  font-weight: 500;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 16px;
  max-width: 880px;
  margin-bottom: 14px;
}

.field {
  display: grid;
  gap: 5px;
  color: var(--muted);
  font-size: 12px;
}

.field input,
.field select {
  padding: 6px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-size: 14px;
}

.activity-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 11px 8px;
  border-bottom: 1px solid var(--line);
  cursor: pointer;
}

.activity-row.selected,
.activity-row:hover {
  background: rgba(23, 89, 76, 0.06);
}

.meta {
  margin-left: 10px;
  color: var(--muted);
  font-size: 12px;
}

.selected-panel {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid var(--line);
}

.selected-panel h3 {
  margin: 0 0 10px;
  font-family: var(--serif);
  font-size: 19px;
  font-weight: 500;
}

.item-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}

.add-item {
  margin-top: 14px;
}

.state-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.small-btn {
  padding: 5px 12px;
  font-size: 13px;
}

.notice {
  color: var(--pine);
  font-size: 14px;
}

.empty {
  padding: 20px 0;
  color: var(--muted);
}

@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .activity-row {
    display: block;
  }

  .meta {
    display: block;
    margin: 4px 0 0;
  }
}
</style>