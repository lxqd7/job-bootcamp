<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { onAuthCleared } from './auth/session'

const router = useRouter()

onMounted(() => {
  onAuthCleared(() => {
    const current = router.currentRoute.value
    if (!current.meta.public) {
      router.replace({ name: 'login', query: { redirect: current.fullPath } })
    }
  })
})
</script>

<template>
  <RouterView />
</template>