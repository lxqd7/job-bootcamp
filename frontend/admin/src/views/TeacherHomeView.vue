<script setup lang="ts">
import AppShell from '../components/AppShell.vue'
import { currentMe } from '../auth/session'

const me = currentMe
</script>

<template>
  <AppShell>
    <section class="hero">
      <p class="stage">教师工作区</p>
      <h1>教师工作区</h1>
      <p class="lead">
        当前账号是教师{{ me ? `（${me.nickname}）` : '' }}。学生作答记录按当前负责课程只读展示；
        答疑、福利题和优秀笔记评选会随后续阶段接入，服务端会按负责课程与路线校验范围。
      </p>
    </section>

    <section class="note">
      <h2>学生作答只读记录</h2>
      <p>
        查看当前负责课程学生的章节自测成绩、代码提交和 AI 反馈。页面不提供修改答案、人工改分、
        重试点评或跨课程查看的入口；教师调离课程后旧的读取请求会被服务端拒绝。
      </p>
      <RouterLink class="btn" to="/teacher/submissions">查看学生作答</RouterLink>
      <RouterLink class="btn" to="/teacher/benefits">维护福利题</RouterLink>
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