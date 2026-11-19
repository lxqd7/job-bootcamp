<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { http } from '../api/http'
import type { ApiEnvelope, MeResponse } from '../api/types'
import {
  applyMe,
  changePassword,
  currentMe,
  describeAuthError,
  reloadMe
} from '../auth/session'

const me = currentMe
const router = useRouter()

const nickname = ref('')
const avatarUrl = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const busy = ref(false)

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordError = ref('')
const passwordBusy = ref(false)

const permissionsText = computed(() =>
  me.value && me.value.permissions.length > 0 ? me.value.permissions.join('、') : '无额外权限'
)

watch(
  me,
  value => {
    if (value) {
      nickname.value = value.nickname
      avatarUrl.value = value.avatarUrl ?? ''
    }
  },
  { immediate: true }
)

onMounted(async () => {
  await reloadMe()
})

async function saveProfile(): Promise<void> {
  if (!me.value) {
    return
  }
  busy.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const { data } = await http.patch<ApiEnvelope<MeResponse>>('/api/v1/me', {
      nickname: nickname.value.trim(),
      avatarUrl: avatarUrl.value.trim() || null,
      revision: me.value.revision
    })
    applyMe(data.data)
    successMessage.value = '资料已保存。'
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    busy.value = false
  }
}

async function submitPassword(): Promise<void> {
  passwordError.value = ''
  if (newPassword.value !== confirmPassword.value) {
    passwordError.value = '两次输入的新密码不一致。'
    return
  }
  passwordBusy.value = true
  try {
    await changePassword(oldPassword.value, newPassword.value)
    await router.replace({ name: 'login', query: { changed: '1' } })
  } catch (error) {
    passwordError.value = describeAuthError(error)
  } finally {
    passwordBusy.value = false
  }
}
</script>

<template>
  <AppShell>
    <section class="profile-head">
      <p class="stage">账号</p>
      <h1>个人资料</h1>
      <p class="lead">昵称和头像地址由本人维护；修改密码会让所有已登录会话失效，需要重新登录。</p>
    </section>

    <section v-if="me" class="block">
      <h2>基本信息</h2>
      <p class="meta">
        账号 ID <code>{{ me.id }}</code> · 角色 {{ me.role }} · 权限 {{ permissionsText }}
      </p>
      <form class="form" @submit.prevent="saveProfile">
        <label class="field">
          <span>昵称</span>
          <input v-model.trim="nickname" maxlength="30" required />
        </label>
        <label class="field">
          <span>头像地址（可选，暂不做上传）</span>
          <input v-model.trim="avatarUrl" maxlength="1000" placeholder="留空表示不设置" />
        </label>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>
        <button class="btn btn-primary" type="submit" :disabled="busy">
          {{ busy ? '保存中…' : '保存资料' }}
        </button>
      </form>
    </section>

    <section class="block">
      <h2>修改密码</h2>
      <form class="form" @submit.prevent="submitPassword">
        <label class="field">
          <span>当前密码</span>
          <input v-model="oldPassword" type="password" autocomplete="current-password" required />
        </label>
        <label class="field">
          <span>新密码（8～72 字节）</span>
          <input v-model="newPassword" type="password" autocomplete="new-password" required />
        </label>
        <label class="field">
          <span>确认新密码</span>
          <input v-model="confirmPassword" type="password" autocomplete="new-password" required />
        </label>
        <p v-if="passwordError" class="form-error" role="alert">{{ passwordError }}</p>
        <button class="btn" type="submit" :disabled="passwordBusy">
          {{ passwordBusy ? '提交中…' : '修改密码并退出' }}
        </button>
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
  max-width: 54ch;
  color: var(--muted);
  line-height: 1.75;
}

.block {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.block h2 {
  margin: 0 0 8px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.meta {
  color: var(--muted);
  font-size: 13px;
}

.meta code {
  font-family: var(--mono);
}

.form {
  display: grid;
  gap: 18px;
  max-width: 440px;
  margin-top: 18px;
}

.success {
  margin: 0;
  color: var(--pine);
  font-size: 14px;
}
</style>