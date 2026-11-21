<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { currentMe, logout } from '../auth/session'

const route = useRoute()
const router = useRouter()

const me = currentMe

const links = computed(() => [
  { to: '/', label: '首页' },
  { to: '/courses', label: '课程' },
  { to: '/routes', label: '路线' },
  { to: '/me/courses', label: '我的课程' },
  { to: '/me/routes', label: '我的路线' },
  { to: '/orders', label: '订单' },
  { to: '/sign-in', label: '签到' },
  { to: '/me/points', label: '积分' },
  { to: '/boards', label: '月榜' },
  { to: '/me', label: '个人资料' }
])

const roleLabel = computed(() => {
  switch (me.value?.role) {
    case 'STUDENT':
      return '学生'
    case 'TEACHER':
      return '教师'
    case 'ADMIN':
      return '管理员'
    default:
      return ''
  }
})

async function handleLogout(): Promise<void> {
  await logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header class="shell-header">
      <RouterLink class="brand" to="/">
        Java 求职训练营
        <span>学生端</span>
      </RouterLink>
      <nav class="shell-nav" aria-label="学生端导航">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          :class="{ active: route.path === link.to }"
        >
          {{ link.label }}
        </RouterLink>
      </nav>
      <div v-if="me" class="shell-user">
        <span class="name">{{ me.nickname }}</span>
        <span class="role">{{ roleLabel }}</span>
        <button type="button" class="link-button" @click="handleLogout">退出</button>
      </div>
    </header>
    <main class="shell-main">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
}

.shell-header {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 18px 32px;
  padding: 20px clamp(20px, 5vw, 56px);
  border-bottom: 1px solid var(--line);
}

.brand {
  color: var(--ink);
  font-family: var(--serif);
  font-size: 20px;
  text-decoration: none;
}

.brand span {
  margin-left: 8px;
  color: var(--muted);
  font-family: var(--sans);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.shell-nav {
  display: flex;
  gap: 22px;
}

.shell-nav a {
  color: var(--muted);
  font-size: 14px;
  text-decoration: none;
}

.shell-nav a.active,
.shell-nav a:hover {
  color: var(--ink);
}

.shell-user {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-left: auto;
  font-size: 13px;
}

.shell-user .name {
  font-weight: 600;
}

.shell-user .role {
  color: var(--muted);
}

.link-button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--pine);
  cursor: pointer;
  text-decoration: underline;
}

.shell-main {
  max-width: 860px;
  margin: 0 auto;
  padding: clamp(32px, 7vw, 64px) 24px 64px;
}

@media (max-width: 640px) {
  .shell-user {
    margin-left: 0;
  }
}
</style>