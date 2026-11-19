<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { describeAuthError, register } from '../auth/session'

const username = ref('')
const password = ref('')
const nickname = ref('')
const errorMessage = ref('')
const busy = ref(false)

const router = useRouter()

async function submit(): Promise<void> {
  busy.value = true
  errorMessage.value = ''
  try {
    await register(username.value.trim(), password.value, nickname.value.trim())
    await router.replace({ name: 'home' })
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
      <h1>注册学生账号</h1>
      <p class="lead">
        注册只会创建学生账号，角色不能由客户端指定。用户名 4～32 位字母、数字或下划线，密码 8～72 字节。
      </p>
    </section>

    <form class="auth-form" @submit.prevent="submit">
      <label class="field">
        <span>用户名</span>
        <input v-model.trim="username" autocomplete="username" required />
      </label>
      <label class="field">
        <span>昵称</span>
        <input v-model.trim="nickname" maxlength="30" required />
      </label>
      <label class="field">
        <span>密码</span>
        <input v-model="password" type="password" autocomplete="new-password" required />
      </label>
      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <button class="btn btn-primary" type="submit" :disabled="busy">
        {{ busy ? '注册中…' : '注册并登录' }}
      </button>
      <p class="switch">
        已有账号？<RouterLink to="/login">返回登录</RouterLink>
      </p>
    </form>
  </main>
</template>