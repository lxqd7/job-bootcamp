<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { describeAuthError, login } from '../auth/session'

const username = ref('')
const password = ref('')
const errorMessage = ref('')
const busy = ref(false)

const route = useRoute()
const router = useRouter()

function safeRedirect(value: unknown): string {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/'
}

async function submit(): Promise<void> {
  busy.value = true
  errorMessage.value = ''
  try {
    await login(username.value.trim(), password.value)
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-intro">
      <p class="stage">阶段 S02 · 身份与会话</p>
      <h1>登录学生端</h1>
      <p class="lead">
        登录后进入学习工作区。Access token 只保存在页面内存；刷新页面时通过 HttpOnly refresh cookie 续期，不需要重新输入密码。
      </p>
    </section>

    <form class="auth-form" @submit.prevent="submit">
      <label class="field">
        <span>用户名</span>
        <input v-model.trim="username" autocomplete="username" required />
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="current-password" required />
      </label>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <button class="btn btn-primary" type="submit" :disabled="busy">
        {{ busy ? '登录中…' : '登录' }}
      </button>
      <p class="switch">
        还没有账号？<RouterLink to="/register">注册学生账号</RouterLink>
      </p>
      <p class="muted small">演示账号：student01 / Demo@123456</p>
    </form>
  </main>
</template>