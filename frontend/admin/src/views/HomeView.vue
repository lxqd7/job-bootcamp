<script setup lang="ts">
import { computed } from 'vue'
import AppShell from '../components/AppShell.vue'
import { currentMe } from '../auth/session'

const me = currentMe
const isAdmin = computed(() => me.value?.role === 'ADMIN')
</script>

<template>
  <AppShell>
    <section class="hero">
      <p class="stage">阶段 S03 · 课程目录</p>
      <h1>欢迎回来{{ me ? `，${me.nickname}` : '' }}</h1>
      <p class="lead">
        管理端与教师工作区共用登录入口，菜单按角色生成，服务端仍会独立校验权限和数据范围。
      </p>
    </section>

    <section v-if="isAdmin" class="entries">
      <RouterLink class="entry" to="/admin/categories">
        <h2>分类管理</h2>
        <p>维护分类树与闭包路径，移动分类会校验环路。</p>
      </RouterLink>
      <RouterLink class="entry" to="/admin/courses">
        <h2>课程管理</h2>
        <p>创建课程、维护章节与小节，并控制上架与下架。</p>
      </RouterLink>
      <RouterLink class="entry" to="/admin/users">
        <h2>账号管理</h2>
        <p>查看、创建教师和管理员账号，调整启停与角色。禁用或改角色会让旧会话立即失效。</p>
      </RouterLink>
      <RouterLink class="entry" to="/admin/moderation">
        <h2>内容治理</h2>
        <p>查看公开原创笔记的评优事实，填写原因违规撤回奖励，并核对治理审计。</p>
      </RouterLink>
      <RouterLink class="entry" to="/me">
        <h2>个人资料</h2>
        <p>维护昵称与头像地址，修改密码后需要重新登录。</p>
      </RouterLink>
    </section>

    <section v-else class="note">
      <h2>教师工作区从 S03 开始接入</h2>
      <p>
        当前已按教师角色生成菜单并验证权限边界；课程范围、答疑、学生作答查看会随课程与互动阶段实现，不用静态页面冒充完成。
      </p>
    </section>
  </AppShell>
</template>

<style scoped>
.hero .stage {
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
  line-height: 1.08;
}

.lead {
  max-width: 54ch;
  margin: 20px 0 0;
  color: var(--muted);
  line-height: 1.75;
}

.entries {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
  margin-top: 44px;
}

.entry {
  display: block;
  padding: 20px 0 0;
  border-top: 2px solid var(--ink);
  color: var(--ink);
  text-decoration: none;
}

.entry h2 {
  margin: 0 0 8px;
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 500;
}

.entry p {
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.entry:hover h2 {
  color: var(--pine);
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
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}
</style>