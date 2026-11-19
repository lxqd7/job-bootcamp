<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { http } from '../api/http'
import type { AdminUserItem, ApiEnvelope, PageResponse } from '../api/types'
import { describeAuthError } from '../auth/session'

const users = ref<AdminUserItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const keyword = ref('')
const roleFilter = ref<'' | 'TEACHER' | 'ADMIN'>('')
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const createForm = reactive({
  username: '',
  password: '',
  nickname: '',
  role: 'TEACHER' as 'TEACHER' | 'ADMIN'
})
const creating = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await http.get<ApiEnvelope<PageResponse<AdminUserItem>>>('/api/v1/admin/users', {
      params: {
        keyword: keyword.value.trim() || undefined,
        role: roleFilter.value || undefined,
        page: page.value,
        pageSize
      }
    })
    users.value = data.data.items
    total.value = data.data.total
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function createUser(): Promise<void> {
  creating.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await http.post('/api/v1/admin/users', {
      username: createForm.username.trim(),
      password: createForm.password,
      nickname: createForm.nickname.trim(),
      role: createForm.role
    })
    successMessage.value = `账号 ${createForm.username.trim()} 已创建。`
    createForm.username = ''
    createForm.password = ''
    createForm.nickname = ''
    createForm.role = 'TEACHER'
    page.value = 1
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    creating.value = false
  }
}

async function toggleEnabled(user: AdminUserItem): Promise<void> {
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await http.patch(`/api/v1/admin/users/${user.id}`, {
      enabled: !user.enabled,
      revision: user.revision
    })
    successMessage.value = `账号 ${user.username} 已${user.enabled ? '禁用' : '启用'}。`
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
    await load()
  }
}

async function changeRole(user: AdminUserItem, event: Event): Promise<void> {
  const select = event.target as HTMLSelectElement
  const nextRole = select.value as 'TEACHER' | 'ADMIN'
  if (nextRole === user.role) {
    return
  }
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await http.patch(`/api/v1/admin/users/${user.id}`, {
      role: nextRole,
      revision: user.revision
    })
    successMessage.value = `账号 ${user.username} 的角色已改为 ${nextRole}。`
    await load()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
    await load()
  }
}

async function search(): Promise<void> {
  page.value = 1
  await load()
}

async function gotoPage(next: number): Promise<void> {
  if (next < 1 || next > totalPages.value) {
    return
  }
  page.value = next
  await load()
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

onMounted(load)
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">账号与角色</p>
      <h1>账号管理</h1>
      <p class="lead">
        管理员可以创建教师或管理员账号、启停账号和调整角色。禁用或改角色会让该账号的旧会话立即失效。
      </p>
    </section>

    <section class="block">
      <h2>创建账号</h2>
      <form class="create-form" @submit.prevent="createUser">
        <label class="field">
          <span>用户名</span>
          <input v-model.trim="createForm.username" required />
        </label>
        <label class="field">
          <span>昵称</span>
          <input v-model.trim="createForm.nickname" maxlength="30" required />
        </label>
        <label class="field">
          <span>初始密码</span>
          <input v-model="createForm.password" type="password" autocomplete="new-password" required />
        </label>
        <label class="field">
          <span>角色</span>
          <select v-model="createForm.role">
            <option value="TEACHER">教师</option>
            <option value="ADMIN">管理员</option>
          </select>
        </label>
        <button class="btn btn-primary" type="submit" :disabled="creating">
          {{ creating ? '创建中…' : '创建账号' }}
        </button>
      </form>
    </section>

    <section class="block">
      <div class="list-head">
        <h2>账号列表</h2>
        <form class="filters" @submit.prevent="search">
          <input v-model.trim="keyword" placeholder="用户名或昵称" />
          <select v-model="roleFilter">
            <option value="">全部角色</option>
            <option value="TEACHER">教师</option>
            <option value="ADMIN">管理员</option>
          </select>
          <button class="btn" type="submit" :disabled="loading">查询</button>
        </form>
      </div>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>用户名</th>
              <th>昵称</th>
              <th>角色</th>
              <th>状态</th>
              <th>修订</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td><code>{{ user.id }}</code></td>
              <td>{{ user.username }}</td>
              <td>{{ user.nickname }}</td>
              <td>
                <select
                  class="role-select"
                  :value="user.role"
                  :disabled="user.role === 'STUDENT'"
                  @change="changeRole(user, $event)"
                >
                  <option v-if="user.role === 'STUDENT'" value="STUDENT">学生</option>
                  <option value="TEACHER">教师</option>
                  <option value="ADMIN">管理员</option>
                </select>
              </td>
              <td>
                <span class="state" :class="{ off: !user.enabled }">
                  {{ user.enabled ? '启用' : '禁用' }}
                </span>
              </td>
              <td>{{ user.revision }}</td>
              <td class="time">{{ formatDate(user.createdAt) }}</td>
              <td>
                <button class="btn small-btn" type="button" @click="toggleEnabled(user)">
                  {{ user.enabled ? '禁用' : '启用' }}
                </button>
              </td>
            </tr>
            <tr v-if="!loading && users.length === 0">
              <td colspan="8" class="empty">没有匹配的账号。</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <span class="muted small">共 {{ total }} 条 · 第 {{ page }} / {{ totalPages }} 页</span>
        <div class="pager-actions">
          <button class="btn small-btn" type="button" :disabled="page <= 1 || loading" @click="gotoPage(page - 1)">
            上一页
          </button>
          <button class="btn small-btn" type="button" :disabled="page >= totalPages || loading" @click="gotoPage(page + 1)">
            下一页
          </button>
        </div>
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
  max-width: 58ch;
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

.create-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 18px;
  align-items: end;
}

.create-form .btn {
  align-self: end;
  justify-self: start;
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
  flex-wrap: wrap;
  gap: 10px;
}

.filters input,
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
  white-space: nowrap;
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

.time {
  color: var(--muted);
  font-size: 13px;
}

.role-select {
  padding: 4px 6px;
  border: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
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
  color: var(--muted);
  text-align: center;
  padding: 24px 0;
}

.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}

.pager-actions {
  display: flex;
  gap: 8px;
}

.form-error {
  margin-top: 12px;
}

.success {
  margin: 12px 0 0;
  color: var(--pine);
  font-size: 14px;
}
</style>