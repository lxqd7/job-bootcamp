<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import {
  changeExchangeProjectState,
  createExchangeProject,
  describeError,
  loadCouponRules,
  loadExchangeProjects,
  updateExchangeProject
} from '../api/client'
import type { CouponRuleView, ExchangeProjectView } from '../api/types'

const rules = ref<CouponRuleView[]>([])
const projects = ref<ExchangeProjectView[]>([])
const total = ref(0)
const state = ref('')
const loading = ref(true)
const busy = ref(false)
const errorMessage = ref('')
const notice = ref('')
const editingId = ref('')
const editingRevision = ref(0)
const form = ref({
  name: `积分兑换 ${new Date().toLocaleDateString('zh-CN')}`,
  ruleId: '',
  pointsPrice: 100,
  stockTotal: 100,
  perUserLimit: 1,
  openAt: localInput(new Date()),
  closeAt: localInput(new Date(Date.now() + 7 * 86400000))
})

function localInput(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function ruleLabel(rule: CouponRuleView | ExchangeProjectView['rule']): string {
  if (!rule) return '规则不可用'
  if (rule.type === 'DISCOUNT') {
    return `折扣券 ${rule.ruleKey} v${rule.versionNo}`
  }
  if (rule.type === 'NO_THRESHOLD') return `无门槛券 ${rule.ruleKey} v${rule.versionNo}`
  return `满减券 ${rule.ruleKey} v${rule.versionNo}`
}

function formatTime(value: string): string {
  return value.replace('T', ' ').slice(0, 16)
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const [rulePage, projectPage] = await Promise.all([
      loadCouponRules(),
      loadExchangeProjects(state.value)
    ])
    rules.value = rulePage.items
    projects.value = projectPage.items
    total.value = projectPage.total
    if (!form.value.ruleId && rules.value[0]) {
      form.value.ruleId = rules.value[0].id
    }
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  editingId.value = ''
  editingRevision.value = 0
  form.value = {
    name: `积分兑换 ${new Date().toLocaleDateString('zh-CN')}`,
    ruleId: rules.value[0]?.id ?? '',
    pointsPrice: 100,
    stockTotal: 100,
    perUserLimit: 1,
    openAt: localInput(new Date()),
    closeAt: localInput(new Date(Date.now() + 7 * 86400000))
  }
  errorMessage.value = ''
  notice.value = ''
}

function edit(project: ExchangeProjectView): void {
  editingId.value = project.id
  editingRevision.value = project.revision
  form.value = {
    name: project.name,
    ruleId: project.rule?.id ?? '',
    pointsPrice: project.pointsPrice,
    stockTotal: project.stockTotal,
    perUserLimit: project.perUserLimit,
    openAt: project.openAt.slice(0, 16),
    closeAt: project.closeAt.slice(0, 16)
  }
  errorMessage.value = ''
  notice.value = ''
}

async function submit(): Promise<void> {
  if (busy.value) return
  if (!form.value.name.trim() || !form.value.ruleId) {
    errorMessage.value = '项目名称和券规则不能为空。'
    return
  }
  busy.value = true
  errorMessage.value = ''
  notice.value = ''
  const payload = {
    name: form.value.name.trim(),
    ruleId: form.value.ruleId,
    pointsPrice: form.value.pointsPrice,
    stockTotal: form.value.stockTotal,
    perUserLimit: form.value.perUserLimit,
    openAt: form.value.openAt,
    closeAt: form.value.closeAt,
    revision: editingRevision.value
  }
  try {
    if (editingId.value) {
      const updated = await updateExchangeProject(editingId.value, payload)
      editingRevision.value = updated.revision
      notice.value = '兑换项目已更新；新配置只影响之后的兑换。'
    } else {
      await createExchangeProject(payload)
      resetForm()
      notice.value = '兑换项目已创建为下架状态，确认后可上架。'
    }
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    busy.value = false
  }
}

async function changeState(project: ExchangeProjectView, action: 'online' | 'offline'): Promise<void> {
  if (busy.value) return
  busy.value = true
  errorMessage.value = ''
  notice.value = ''
  try {
    const updated = await changeExchangeProjectState(project.id, action, project.revision)
    notice.value = action === 'online' ? '兑换项目已上架。' : '兑换项目已下架；已兑换的券不受影响。'
    if (editingId.value === project.id) {
      editingRevision.value = updated.revision
    }
    await load()
  } catch (error) {
    errorMessage.value = describeError(error)
  } finally {
    busy.value = false
  }
}

function changeFilter(): void {
  void load()
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">管理员 · 营销配置</p>
      <h1>积分兑换项目</h1>
      <p class="lead">
        配置券规则、积分价格、库存、每人累计限兑和开放窗口。调价/改库存带 revision 确认；
        下架或重新上架不会清空已兑换数量，已发出的券按原规则继续使用。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>

    <section class="panel form-panel">
      <div class="list-head">
        <h2>{{ editingId ? '编辑兑换项目' : '新建兑换项目' }}</h2>
        <button v-if="editingId" class="btn small-btn" type="button" @click="resetForm">退出编辑</button>
      </div>
      <div class="form-grid">
        <label class="field"><span>项目名</span><input v-model="form.name" maxlength="100" /></label>
        <label class="field">
          <span>券规则</span>
          <select v-model="form.ruleId">
            <option v-for="rule in rules" :key="rule.id" :value="rule.id">{{ ruleLabel(rule) }}</option>
          </select>
        </label>
        <label class="field"><span>积分价格</span><input v-model.number="form.pointsPrice" type="number" min="1" /></label>
        <label class="field"><span>总库存</span><input v-model.number="form.stockTotal" type="number" min="0" /></label>
        <label class="field"><span>每人累计限兑</span><input v-model.number="form.perUserLimit" type="number" min="1" /></label>
        <label class="field"><span>开放时间</span><input v-model="form.openAt" type="datetime-local" /></label>
        <label class="field"><span>结束时间</span><input v-model="form.closeAt" type="datetime-local" /></label>
      </div>
      <button class="btn btn-primary" type="button" :disabled="busy" @click="submit">
        {{ busy ? '保存中…' : editingId ? '保存修改' : '创建项目' }}
      </button>
    </section>

    <section class="panel">
      <div class="toolbar">
        <label class="filter">
          <span>状态</span>
          <select v-model="state" @change="changeFilter">
            <option value="">全部</option>
            <option value="ONLINE">已上架</option>
            <option value="OFFLINE">已下架</option>
          </select>
        </label>
        <span class="muted">{{ total }} 个项目</span>
      </div>
      <p v-if="loading" class="empty">正在读取兑换项目…</p>
      <p v-else-if="projects.length === 0" class="empty">还没有兑换项目。</p>
      <table v-else class="grid">
        <thead>
          <tr>
            <th>项目</th>
            <th>券规则</th>
            <th>积分价</th>
            <th>库存</th>
            <th>每人限兑</th>
            <th>开放窗口</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="project in projects" :key="project.id">
            <td><strong>{{ project.name }}</strong><br /><span class="muted">v{{ project.revision }}</span></td>
            <td>{{ ruleLabel(project.rule) }}</td>
            <td>{{ project.pointsPrice }} 分</td>
            <td>{{ project.stockUsed }} / {{ project.stockTotal }}</td>
            <td>{{ project.perUserLimit }}</td>
            <td>{{ formatTime(project.openAt) }}<br />至 {{ formatTime(project.closeAt) }}</td>
            <td>
              <span :class="['tag', project.state === 'ONLINE' ? 'online' : 'offline']">
                {{ project.state === 'ONLINE' ? '已上架' : '已下架' }}
              </span>
            </td>
            <td class="actions-cell">
              <button class="btn small-btn" type="button" @click="edit(project)">编辑</button>
              <button
                v-if="project.state === 'ONLINE'"
                class="btn small-btn danger"
                type="button"
                :disabled="busy"
                @click="changeState(project, 'offline')"
              >
                下架
              </button>
              <button
                v-else
                class="btn small-btn"
                type="button"
                :disabled="busy"
                @click="changeState(project, 'online')"
              >
                上架
              </button>
            </td>
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
.lead { max-width: 78ch; color: var(--muted); line-height: 1.8; }
.panel { margin-top: 26px; padding-top: 18px; border-top: 2px solid var(--ink); }
.list-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 14px; }
.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; margin-bottom: 14px; }
.field { display: grid; gap: 5px; color: var(--muted); font-size: 12px; }
.field input, .field select { padding: 8px 10px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.toolbar { display: flex; flex-wrap: wrap; align-items: end; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.filter { display: grid; gap: 4px; color: var(--muted); font-size: 12px; }
.filter select { padding: 7px 9px; border: 1px solid var(--line); background: transparent; color: var(--ink); font: inherit; }
.grid { width: 100%; border-collapse: collapse; font-size: 13px; }
.grid th, .grid td { padding: 9px 8px; border-bottom: 1px solid var(--line); text-align: left; vertical-align: top; }
.grid th { color: var(--muted); font-size: 11px; font-weight: 500; letter-spacing: .04em; }
.muted { color: var(--muted); font-size: 12px; }
.tag { padding: 1px 6px; font-size: 11px; border: 1px solid var(--line); white-space: nowrap; }
.tag.online { border-color: var(--pine); color: var(--pine); }
.tag.offline { color: var(--muted); }
.actions-cell { display: flex; flex-wrap: wrap; gap: 6px; }
.small-btn { padding: 6px 10px; font-size: 12px; }
.danger { color: var(--amber); border-color: var(--amber); }
.empty { padding: 20px 0; color: var(--muted); }
</style>