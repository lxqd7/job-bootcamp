<script setup lang="ts">
import { currentMe } from '../auth/session'
</script>

<template>
  <main class="auth-page">
    <section class="auth-intro">
      <p class="stage">403</p>
      <h1>这个页面不属于你的角色</h1>
      <p class="lead">当前账号没有访问该页面的权限。可以返回工作区，或退出后换一个账号登录。</p>
    </section>
    <div class="auth-form">
      <RouterLink v-if="currentMe" class="btn btn-primary" to="/">返回工作区</RouterLink>
      <RouterLink v-else class="btn btn-primary" to="/login">去登录</RouterLink>
      <RouterLink class="btn" to="/login">切换账号</RouterLink>
    </div>
  </main>
</template>