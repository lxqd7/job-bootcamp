<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { claimCourse, describeTradeError, loadClaim, loadCourse, loadCategories } from '../api/client'
import type { CategoryNode, CourseDetail } from '../api/types'

const route = useRoute()
const course = ref<CourseDetail | null>(null)
const categoryName = ref('')
const loading = ref(true)
const errorMessage = ref('')
const claiming = ref(false)
const claimed = ref(false)
const claimNotice = ref('')

const lessonCount = computed(() =>
  course.value ? course.value.chapters.reduce((total, chapter) => total + chapter.lessons.length, 0) : 0
)
const owned = computed(() => Boolean(course.value?.isOwned || claimed.value))

function formatPrice(value: CourseDetail): string {
  return value.isFree ? '免费' : `¥${(value.priceCent / 100).toFixed(2)}`
}

function findName(nodes: CategoryNode[], id: string): string {
  for (const node of nodes) {
    if (node.id === id) {
      return node.name
    }
    const found = findName(node.children, id)
    if (found) {
      return found
    }
  }
  return ''
}

onMounted(async () => {
  try {
    const id = String(route.params.id)
    course.value = await loadCourse(id)
    const categories = await loadCategories()
    categoryName.value = findName(categories.categories, course.value.categoryId)
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
})

async function claimFree(): Promise<void> {
  if (!course.value || claiming.value) return
  claiming.value = true
  errorMessage.value = ''
  claimNotice.value = ''
  try {
    let result = await claimCourse(course.value.id)
    for (let attempt = 0; result.status === 'PROCESSING' && attempt < 20; attempt++) {
      await new Promise(resolve => setTimeout(resolve, 500))
      result = await loadClaim(result.claimId)
    }
    if (result.status === 'FULFILLED') {
      claimed.value = true
      claimNotice.value = '领取成功，可以进入学习。'
    } else {
      claimNotice.value = '领取已受理，学习服务仍在处理中，可稍后到“我的课程”查看。'
    }
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    claiming.value = false
  }
}
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink to="/courses">← 返回课程列表</RouterLink></p>

    <p v-if="loading" class="muted">读取课程详情…</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="course">
      <section class="head">
        <p class="stage">{{ categoryName || '未分类' }} · 课程 ID {{ course.id }}</p>
        <h1>{{ course.title }}</h1>
        <p class="lead">{{ course.description }}</p>
        <p class="facts">
          <span class="price" :class="{ free: course.isFree }">{{ formatPrice(course) }}</span>
          <span>{{ course.chapters.length }} 章 · {{ lessonCount }} 个小节</span>
          <span>目录版本 {{ course.catalogVersion }}</span>
        </p>
      </section>

      <section class="catalog">
        <h2>目录</h2>
        <div v-for="chapter in course.chapters" :key="chapter.id" class="chapter">
          <h3>{{ chapter.title }}</h3>
          <ul>
            <li v-for="lesson in chapter.lessons" :key="lesson.id">
              <span>{{ lesson.title }}</span>
              <span class="badge" :class="{ video: lesson.hasVideo }">
                {{ lesson.hasVideo ? '视频' : '待接入视频' }}
              </span>
            </li>
          </ul>
        </div>
        <p v-if="course.chapters.length === 0" class="muted">这门课程还没有目录。</p>
      </section>

      <section class="note">
        <h2>购买与学习</h2>
        <p v-if="claimNotice" class="claim-notice" role="status">{{ claimNotice }}</p>
        <div v-if="owned" class="purchase-actions">
          <RouterLink class="btn btn-primary" :to="`/learning/courses/${course.id}`">进入学习</RouterLink>
          <span class="muted">已拥有有效学习权益。</span>
        </div>
        <div v-else-if="course.isFree" class="purchase-actions">
          <button class="btn btn-primary" type="button" :disabled="claiming" @click="claimFree">
            {{ claiming ? '领取中…' : '免费领取' }}
          </button>
          <span class="muted">免费课程领取后由学习服务建立 FREE 来源。</span>
        </div>
        <div v-else-if="course.status === 'ONLINE'" class="purchase-actions">
          <RouterLink class="btn btn-primary" :to="{ name: 'checkout', query: { courseId: course.id } }">
            立即购买
          </RouterLink>
          <span class="muted">先确认报价，再创建 30 分钟待支付订单；当前阶段不接优惠券。</span>
        </div>
        <p v-else class="muted">课程当前未上架，暂不能购买。</p>
      </section>
    </template>
  </AppShell>
</template>

<style scoped>
.back {
  margin: 0 0 18px;
  font-size: 14px;
}

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
  line-height: 1.1;
}

.lead {
  max-width: 60ch;
  color: var(--muted);
  line-height: 1.75;
}

.facts {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin: 18px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.price {
  color: var(--ink);
  font-weight: 600;
}

.price.free {
  color: var(--pine);
}

.catalog {
  margin-top: 40px;
  padding-top: 20px;
  border-top: 2px solid var(--ink);
}

.catalog h2 {
  margin: 0 0 14px;
  font-family: var(--serif);
  font-size: 24px;
  font-weight: 500;
}

.chapter {
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}

.chapter h3 {
  margin: 0 0 8px;
  font-family: var(--serif);
  font-size: 19px;
  font-weight: 500;
}

.chapter ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.chapter li {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 5px 0;
  color: var(--muted);
  font-size: 14px;
}

.badge {
  padding: 1px 8px;
  border: 1px solid var(--line);
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}

.badge.video {
  border-color: var(--pine);
  color: var(--pine);
}

.note {
  margin-top: 40px;
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
  max-width: 60ch;
  margin: 0;
  color: var(--muted);
  line-height: 1.7;
}

.purchase-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
}

.claim-notice {
  margin-bottom: 12px !important;
  color: var(--pine) !important;
}
</style>