<script setup lang="ts">
import AppShell from '../components/AppShell.vue'
import { currentMe } from '../auth/session'

const me = currentMe
</script>

<template>
  <AppShell>
    <section class="hero">
      <p class="stage">阶段 S02 · 教师工作区</p>
      <h1>教师工作区</h1>
      <p class="lead">
        当前账号是教师{{ me ? `（${me.nickname}）` : '' }}。课程、答疑、学生作答只读记录和福利题会随 S03/S12/S13 接入；服务端会按负责课程与路线校验范围。
      </p>
    </section>

    <section class="note">
      <h2>这一版没有教师业务页面</h2>
      <p>
        S02 只交付身份、角色菜单和账号管理。教师范围分配和只读作答属于后续阶段，不用静态页面冒充已完成。
      </p>
      <RouterLink class="btn" to="/me">查看个人资料</RouterLink>
    </section>
  </AppShell>
</template>

<style scoped>
.stage {
  margin: 0 0 14px;
  color: var(--muted);
  font-size: 13px;
  letter-spacing: 0.06em;
}

h1 {
  margin: 0;
  font-family: var(--serif);
  font-size: clamp(36px, 6vw, 52px);
  font-weight: 500;
}

.lead {
  max-width: 56ch;
  color: var(--muted);
  line-height: 1.75;
}

.note {
  margin-top: 44px;
  padding-left: 18px;
  border-left: 2px solid var(--pine);
}

.note h2 {
  margin: 0 0 6px;
  font-family: var(--serif);
  font-size: 20px;
  font-weight: 500;
}

.note p {
  max-width: 58ch;
  margin: 0 0 16px;
  color: var(--muted);
  line-height: 1.7;
}
</style>