<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { http } from '../api/http'
import type { ApiEnvelope, CategoryItem, CategoryTreeNode } from '../api/types'
import { describeAuthError } from '../auth/session'

interface FlatCategory {
  node: CategoryTreeNode
  depth: number
}

const tree = ref<CategoryTreeNode[]>([])
const rows = ref<FlatCategory[]>([])
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const createForm = reactive({
  parentId: '',
  name: '',
  sort: 0,
  enabled: true
})

const editForm = reactive({
  id: '',
  parentId: '',
  name: '',
  sort: 0,
  enabled: true,
  revision: 0
})
const editing = ref(false)

const parentOptions = computed(() => rows.value.filter(row => row.node.id !== editForm.id))

function flatten(nodes: CategoryTreeNode[], depth = 0, result: FlatCategory[] = []): FlatCategory[] {
  for (const node of nodes) {
    result.push({ node, depth })
    flatten(node.children, depth + 1, result)
  }
  return result
}

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await http.get<ApiEnvelope<CategoryTreeNode[]>>('/api/v1/admin/categories')
    tree.value = data.data
    rows.value = flatten(tree.value)
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function createCategory(): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await http.post('/api/v1/admin/categories', {
      parentId: createForm.parentId ? Number(createForm.parentId) : 0,
      name: createForm.name.trim(),
      sort: Number(createForm.sort) || 0,
      enabled: createForm.enabled
    })
    successMessage.value = `分类 ${createForm.name.trim()} 已创建。`
    createForm.name = ''
    createForm.sort = 0
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
}

function startEdit(row: FlatCategory): void {
  editing.value = true
  errorMessage.value = ''
  successMessage.value = ''
  editForm.id = row.node.id
  editForm.parentId = row.node.parentId
  editForm.name = row.node.name
  editForm.sort = row.node.sort
  editForm.enabled = row.node.enabled
  editForm.revision = row.node.revision
}

async function saveEdit(): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await http.patch<ApiEnvelope<CategoryItem>>(`/api/v1/admin/categories/${editForm.id}`, {
      name: editForm.name.trim(),
      parentId: editForm.parentId ? Number(editForm.parentId) : 0,
      sort: Number(editForm.sort) || 0,
      enabled: editForm.enabled,
      revision: editForm.revision
    })
    successMessage.value = '分类已保存。'
    editing.value = false
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
      <p class="stage">课程目录</p>
      <h1>分类管理</h1>
      <p class="lead">分类树由闭包表维护；移动分类会重建路径并拒绝移动到自身或子分类下。</p>
    </section>

    <section class="block">
      <h2>新建分类</h2>
      <form class="form-grid" @submit.prevent="createCategory">
        <label class="field">
          <span>父分类</span>
          <select v-model="createForm.parentId">
            <option value="">顶级分类</option>
            <option v-for="row in rows" :key="row.node.id" :value="row.node.id">
              {{ '· '.repeat(row.depth) }}{{ row.node.name }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>名称</span>
          <input v-model.trim="createForm.name" maxlength="200" required />
        </label>
        <label class="field">
          <span>排序</span>
          <input v-model.number="createForm.sort" type="number" />
        </label>
        <label class="check">
          <input v-model="createForm.enabled" type="checkbox" />
          <span>启用</span>
        </label>
        <button class="btn btn-primary" type="submit">创建分类</button>
      </form>
    </section>

    <section class="block">
      <h2>分类树</h2>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>父分类</th>
              <th>排序</th>
              <th>状态</th>
              <th>修订</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.node.id">
              <td :style="{ paddingLeft: `${row.depth * 20 + 12}px` }">{{ row.node.name }}</td>
              <td>{{ row.node.parentId === '0' ? '顶级' : row.node.parentId }}</td>
              <td>{{ row.node.sort }}</td>
              <td><span class="state" :class="{ off: !row.node.enabled }">{{ row.node.enabled ? '启用' : '停用' }}</span></td>
              <td>{{ row.node.revision }}</td>
              <td><button class="btn small-btn" type="button" @click="startEdit(row)">编辑</button></td>
            </tr>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="6" class="empty">还没有分类。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="editing" class="block">
      <h2>编辑分类</h2>
      <form class="form-grid" @submit.prevent="saveEdit">
        <label class="field">
          <span>父分类</span>
          <select v-model="editForm.parentId">
            <option value="0">顶级分类</option>
            <option v-for="row in parentOptions" :key="row.node.id" :value="row.node.id">
              {{ '· '.repeat(row.depth) }}{{ row.node.name }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>名称</span>
          <input v-model.trim="editForm.name" maxlength="200" required />
        </label>
        <label class="field">
          <span>排序</span>
          <input v-model.number="editForm.sort" type="number" />
        </label>
        <label class="check">
          <input v-model="editForm.enabled" type="checkbox" />
          <span>启用</span>
        </label>
        <button class="btn btn-primary" type="submit">保存</button>
        <button class="btn" type="button" @click="editing = false">取消</button>
      </form>
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
  max-width: 60ch;
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 16px;
  align-items: end;
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  font-size: 14px;
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
  white-space: nowrap;
}

th {
  color: var(--muted);
  font-weight: 500;
  font-size: 12px;
  letter-spacing: 0.06em;
}

.state {
  color: var(--pine);
}

.state.off {
  color: var(--bad);
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

.success {
  margin: 12px 0 0;
  color: var(--pine);
  font-size: 14px;
}
</style>