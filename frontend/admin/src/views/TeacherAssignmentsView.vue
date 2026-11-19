<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppShell from '../components/AppShell.vue'
import { http } from '../api/http'
import type {
  AdminCourseItem,
  AdminUserItem,
  ApiEnvelope,
  PageResponse,
  RouteSummary,
  TeacherAssignmentsResponse
} from '../api/types'
import { describeAuthError } from '../auth/session'

const teachers = ref<AdminUserItem[]>([])
const courses = ref<AdminCourseItem[]>([])
const routes = ref<RouteSummary[]>([])
const selectedTeacherId = ref('')
const selectedCourseIds = ref<string[]>([])
const selectedRouteIds = ref<string[]>([])
const revision = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const selectedTeacher = computed(() =>
  teachers.value.find(teacher => teacher.id === selectedTeacherId.value) ?? null
)

function toggle(list: string[], id: string): string[] {
  return list.includes(id) ? list.filter(item => item !== id) : [...list, id]
}

async function loadBase(): Promise<void> {
  const [teacherResponse, courseResponse, routeResponse] = await Promise.all([
    http.get<ApiEnvelope<PageResponse<AdminUserItem>>>('/api/v1/admin/users', {
      params: { role: 'TEACHER', page: 1, pageSize: 100 }
    }),
    http.get<ApiEnvelope<PageResponse<AdminCourseItem>>>('/api/v1/admin/courses', {
      params: { page: 1, pageSize: 100 }
    }),
    http.get<ApiEnvelope<PageResponse<RouteSummary>>>('/api/v1/admin/routes', {
      params: { page: 1, pageSize: 100 }
    })
  ])
  teachers.value = teacherResponse.data.data.items
  courses.value = courseResponse.data.data.items
  routes.value = routeResponse.data.data.items
  if (!selectedTeacherId.value && teachers.value.length > 0) {
    selectedTeacherId.value = teachers.value[0].id
  }
}

async function loadAssignments(): Promise<void> {
  if (!selectedTeacherId.value) {
    selectedCourseIds.value = []
    selectedRouteIds.value = []
    revision.value = 0
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await http.get<ApiEnvelope<TeacherAssignmentsResponse>>(
      `/api/v1/admin/teachers/${selectedTeacherId.value}/assignments`
    )
    selectedCourseIds.value = data.data.courseIds
    selectedRouteIds.value = data.data.routeIds
    revision.value = data.data.revision
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  } finally {
    loading.value = false
  }
}

async function save(): Promise<void> {
  if (!selectedTeacherId.value) {
    return
  }
  loading.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const { data } = await http.put<ApiEnvelope<TeacherAssignmentsResponse>>(
      `/api/v1/admin/teachers/${selectedTeacherId.value}/assignments`,
      {
        courseIds: selectedCourseIds.value.map(Number),
        routeIds: selectedRouteIds.value.map(Number),
        revision: revision.value
      }
    )
    selectedCourseIds.value = data.data.courseIds
    selectedRouteIds.value = data.data.routeIds
    revision.value = data.data.revision
    successMessage.value = '教师分配已保存。'
  } catch (error) {
    errorMessage.value = describeAuthError(error)
    await loadAssignments()
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    await loadBase()
    await loadAssignments()
  } catch (error) {
    errorMessage.value = describeAuthError(error)
  }
})
</script>

<template>
  <AppShell>
    <section class="head">
      <p class="stage">教师范围</p>
      <h1>教师分配</h1>
      <p class="lead">
        为教师分配负责的课程与路线。教师工作区会按这里的范围过滤；后续福利题发布也要求同时具备课程与路线分配。
      </p>
    </section>

    <section class="block">
      <div class="selector">
        <label class="field">
          <span>教师账号</span>
          <select v-model="selectedTeacherId" @change="loadAssignments">
            <option v-for="teacher in teachers" :key="teacher.id" :value="teacher.id">
              {{ teacher.username }}（{{ teacher.nickname }}）
            </option>
          </select>
        </label>
        <span class="muted small">范围修订 {{ revision }}</span>
      </div>

      <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <p v-if="successMessage" class="success" role="status">{{ successMessage }}</p>

      <div v-if="selectedTeacher" class="columns">
        <fieldset>
          <legend>负责课程</legend>
          <label v-for="course in courses" :key="course.id" class="check">
            <input
              type="checkbox"
              :checked="selectedCourseIds.includes(course.id)"
              @change="selectedCourseIds = toggle(selectedCourseIds, course.id)"
            />
            <span>{{ course.title }}（{{ course.status }}）</span>
          </label>
          <p v-if="courses.length === 0" class="muted small">还没有课程。</p>
        </fieldset>
        <fieldset>
          <legend>负责路线</legend>
          <label v-for="route in routes" :key="route.id" class="check">
            <input
              type="checkbox"
              :checked="selectedRouteIds.includes(route.id)"
              @change="selectedRouteIds = toggle(selectedRouteIds, route.id)"
            />
            <span>{{ route.title }}（{{ route.status }}）</span>
          </label>
          <p v-if="routes.length === 0" class="muted small">还没有路线。</p>
        </fieldset>
      </div>

      <button class="btn btn-primary" type="button" :disabled="loading || !selectedTeacherId" @click="save">
        {{ loading ? '保存中…' : '保存分配' }}
      </button>
    </section>
  </AppShell>
</template>

<style scoped>
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
}

.lead {
  max-width: 62ch;
  color: var(--muted);
  line-height: 1.75;
}

.block {
  margin-top: 36px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

.selector {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 18px;
  margin-bottom: 18px;
}

.selector .field {
  min-width: 240px;
}

.columns {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 28px;
  margin: 18px 0;
}

fieldset {
  margin: 0;
  padding: 0;
  border: 0;
}

legend {
  margin-bottom: 8px;
  color: var(--muted);
  font-size: 13px;
  letter-spacing: 0.06em;
}

.check {
  display: flex;
  gap: 8px;
  align-items: baseline;
  color: var(--muted);
  font-size: 14px;
  padding: 4px 0;
}

.success {
  margin: 12px 0;
  color: var(--pine);
  font-size: 14px;
}
</style>