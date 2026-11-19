<script setup lang="ts">
import { currentMe } from '../auth/session'
</script>

<template>
  <main class="auth-page">
    <section class="auth-intro">
      <p class="stage">403</p>
      <h1>当前角色不能访问这个页面</h1>
      <p class="lead">菜单会隐藏无权入口，但真正的边界在服务端。可以返回工作区，或换一个账号登录。</p>
    </section>
    <div class="auth-form">
      <RouterLink v-if="currentMe" class="btn btn-primary" to="/">返回工作区</RouterLink>
      <RouterLink v-else class="btn btn-primary" to="/login">去登录</RouterLink>
      <RouterLink class="btn" to="/login">切换账号</RouterLink>
    </div>
  </main>
</template>