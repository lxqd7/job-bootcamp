<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { http } from '../api/http'
import type {
  ApiEnvelope,
  MediaAuthorization,
  MediaImportPreviewResponse,
  MediaImportResponse,
  MediaItemView,
  PageResponse
} from '../api/types'
import { describeAuthError } from '../auth/session'

const mediaItems = ref<MediaItemView[]>([])
const statusFilter = ref('')
const providerFilter = ref('')
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const demoForm = reactive({
  relativePath: 'flower.mp4',
  title: 'DEMO flower'
})

const importForm = reactive({
  batchId: crypto.randomUUID(),
  itemsText: '[{"oldId":"old-1","appId":"1250000000","fileId":"5285890800000000000"}]'
})
const previewResult = ref<MediaImportPreviewResponse | null>(null)
const importResult = ref<MediaImportResponse | null>(null)

function statusLabel(value: string): string {
  switch (value) {
    case 'AVAILABLE':
      return '可用'
    case 'PENDING':
      return '待校验'
    case 'MISSING':
      return '缺失'
    case 'ERROR':
      return '异常'
    default:
      return value
  }
}

function durationText(value: number | null): string {
  return value == null ? '—' : `${(value / 1000).toFixed(1)} s`
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await http.get<ApiEnvelope<PageResponse<MediaItemView>>>('/api/v1/admin/media', {
      params: {
        status: statusFilter.value || undefined,
        provider: providerFilter.value || undefined,
        page: 1,
        pageSize: 100
      }
    })
    mediaItems.value = data.data.items
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function demoImport(): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const { data } = await http.post<ApiEnvelope<MediaItemView>>('/api/v1/admin/media/demo-import', {
      relativePath: demoForm.relativePath.trim(),
      title: demoForm.title.trim()
    })
    successMessage.value = `演示媒体已导入，mediaId ${data.data.id}，状态 ${statusLabel(data.data.status)}。`
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function verifyMedia(item: MediaItemView): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await http.post(`/api/v1/admin/media/${item.id}/verify`)
    successMessage.value = `mediaId ${item.id} 已重新校验。`
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function previewMedia(item: MediaItemView): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const { data } = await http.post<ApiEnvelope<MediaAuthorization>>(
      `/api/v1/admin/media/${item.id}/preview-authorizations`
    )
    if (data.data.demoUrl) {
      window.open(data.data.demoUrl, '_blank')
      successMessage.value = `已生成 DEMO 预览地址（${data.data.expiresAt} 过期）。`
    } else {
      successMessage.value = `已生成 ${data.data.provider} 预览凭据，fileId ${data.data.fileId}；真实 VOD 播放未在本地验证。`
    }
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function detachMedia(item: MediaItemView): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await http.delete(`/api/v1/admin/media/${item.id}`, { params: { revision: item.revision } })
    successMessage.value = `mediaId ${item.id} 已解除引用（未删除云端/本地文件）。`
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function previewImport(): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  previewResult.value = null
  try {
    const items = JSON.parse(importForm.itemsText) as Array<Record<string, unknown>>
    const { data } = await http.post<ApiEnvelope<MediaImportPreviewResponse>>(
      '/api/v1/admin/media/import-preview',
      { batchId: importForm.batchId.trim(), items }
    )
    previewResult.value = data.data
    successMessage.value = '预览完成，尚未写入业务记录。'
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

async function executeImport(): Promise<void> {
  if (!previewResult.value) {
    errorMessage.value = '请先执行 import-preview。'
    return
  }
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const selected = previewResult.value.items
      .filter(item => item.action !== 'DUPLICATE')
      .map(item => item.oldId)
    const { data } = await http.post<ApiEnvelope<MediaImportResponse>>('/api/v1/admin/media/import', {
      batchId: importForm.batchId.trim(),
      inputHash: previewResult.value.inputHash,
      selectedOldIds: selected
    })
    importResult.value = data.data
    successMessage.value = '导入完成；VOD 未配置时资源会标记为异常，不会假装可用。'
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">媒体与演示视频</p>
      <h1>媒体管理</h1>
      <p class="lead">
        管理员可以导入并校验媒体。DEMO 媒体来自授权演示目录，VOD 使用腾讯云官方 SDK；没有凭据时 VOD 会标记为异常，不会伪造可用状态。
      </p>
    </section>

    <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>

    <section class="block">
      <h2>导入 DEMO 媒体</h2>
      <form class="inline-form" @submit.prevent="demoImport">
        <label class="field">
          <span>DEMO_MEDIA_ROOT 下相对路径</span>
          <input v-model.trim="demoForm.relativePath" required />
        </label>
        <label class="field">
          <span>标题（记录用）</span>
          <input v-model.trim="demoForm.title" required />
        </label>
        <button class="btn btn-primary" type="submit">导入演示媒体</button>
      </form>
      <p class="muted small">当前示例文件：media/demo/flower.mp4（MDN CC0 示例视频，5.055 秒）。</p>
    </section>

    <section class="block">
      <h2>VOD 媒体清单</h2>
      <p class="muted small">先预览，再选择导入。相同 batchId/inputHash 重试不会重复创建；缺少 VOD 凭据时校验结果为异常。</p>
      <div class="inline-form">
        <label class="field">
          <span>batchId</span>
          <input v-model.trim="importForm.batchId" />
        </label>
        <label class="field wide">
          <span>items JSON（oldId/appId/fileId）</span>
          <textarea v-model="importForm.itemsText" rows="4"></textarea>
        </label>
        <button class="btn" type="button" @click="previewImport">预览</button>
        <button class="btn btn-primary" type="button" :disabled="!previewResult" @click="executeImport">导入</button>
      </div>
      <div v-if="previewResult" class="result">
        <p class="muted small">预览 operation {{ previewResult.operationKey }}，inputHash {{ previewResult.inputHash }}</p>
        <ul>
          <li v-for="item in previewResult.items" :key="item.oldId">
            {{ item.oldId }} → {{ item.action }}{{ item.mediaId ? `（media ${item.mediaId}）` : '' }}
          </li>
        </ul>
      </div>
      <div v-if="importResult" class="result">
        <p class="muted small">导入 operation {{ importResult.operationKey }}</p>
        <ul>
          <li v-for="item in importResult.items" :key="item.oldId">
            {{ item.oldId }} → media {{ item.mediaId || '—' }} · {{ item.status }}
          </li>
        </ul>
      </div>
    </section>

    <section class="block">
      <div class="list-head">
        <h2>媒体列表</h2>
        <form class="filters" @submit.prevent="load">
          <select v-model="statusFilter">
            <option value="">全部状态</option>
            <option value="AVAILABLE">可用</option>
            <option value="PENDING">待校验</option>
            <option value="MISSING">缺失</option>
            <option value="ERROR">异常</option>
          </select>
          <select v-model="providerFilter">
            <option value="">全部来源</option>
            <option value="DEMO">DEMO</option>
            <option value="VOD">VOD</option>
          </select>
          <button class="btn" type="submit" :disabled="loading">查询</button>
        </form>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>来源</th>
              <th>标识</th>
              <th>时长</th>
              <th>状态</th>
              <th>已解除</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in mediaItems" :key="item.id">
              <td><code>{{ item.id }}</code></td>
              <td>{{ item.provider }}</td>
              <td class="identifier">
                {{ item.demoPath || item.fileId || '—' }}
                <span v-if="item.appId" class="muted"> (app {{ item.appId }})</span>
              </td>
              <td>{{ durationText(item.durationMs) }}</td>
              <td><span class="state" :class="item.status.toLowerCase()">{{ statusLabel(item.status) }}</span></td>
              <td>{{ item.detached ? '是' : '否' }}</td>
              <td class="actions">
                <button class="btn small-btn" type="button" @click="verifyMedia(item)">校验</button>
                <button class="btn small-btn" type="button" :disabled="item.status !== 'AVAILABLE' || item.detached" @click="previewMedia(item)">预览</button>
                <button class="btn small-btn" type="button" :disabled="item.detached" @click="detachMedia(item)">解除</button>
              </td>
            </tr>
            <tr v-if="!loading && mediaItems.length === 0">
              <td colspan="7" class="empty">还没有媒体。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
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

.block {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.block h2 {
  margin: 0 0 14px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.inline-form {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: end;
}

.inline-form .wide {
  flex: 1 1 360px;
}

textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font-family: var(--mono);
  font-size: 13px;
}

.list-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.filters {
  display: flex;
  gap: 10px;
}

.filters select {
  padding: 6px 8px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
}

.table-wrap {
  overflow-x: auto;
  margin-top: 16px;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

th,
td {
  padding: 10px 12px 10px 0;
  border-bottom: 1px solid var(--line);
  text-align: left;
  vertical-align: baseline;
}

th {
  color: var(--muted);
  font-weight: 500;
  font-size: 12px;
  letter-spacing: 0.06em;
}

td code {
  font-family: var(--mono);
  font-size: 12px;
}

.identifier {
  max-width: 320px;
  overflow-wrap: anywhere;
}

.state {
  color: var(--pine);
}

.state.pending {
  color: var(--amber);
}

.state.missing,
.state.error {
  color: var(--bad);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.small-btn {
  padding: 4px 10px;
  font-size: 13px;
}

.empty {
  padding: 24px 0;
  color: var(--muted);
  text-align: center;
}

.result {
  margin-top: 14px;
  padding-left: 16px;
  border-left: 2px solid var(--line);
}

.result ul {
  margin: 6px 0 0;
  padding-left: 18px;
  color: var(--muted);
  font-size: 13px;
}

.success {
  margin: 12px 0;
  color: var(--pine);
  font-size: 14px;
}
</style>