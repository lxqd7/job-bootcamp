import { createRouter, createWebHistory } from 'vue-router'
import { bootstrapSession, currentMe } from '../auth/session'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import AdminUsersView from '../views/AdminUsersView.vue'
import CategoryAdminView from '../views/CategoryAdminView.vue'
import CourseAdminListView from '../views/CourseAdminListView.vue'
import CourseAdminEditView from '../views/CourseAdminEditView.vue'
import RouteAdminListView from '../views/RouteAdminListView.vue'
import RouteAdminEditView from '../views/RouteAdminEditView.vue'
import TeacherAssignmentsView from '../views/TeacherAssignmentsView.vue'
import MediaAdminView from '../views/MediaAdminView.vue'
import TeacherHomeView from '../views/TeacherHomeView.vue'
import TeacherSubmissionsView from '../views/TeacherSubmissionsView.vue'
import AdminTradesView from '../views/AdminTradesView.vue'
import AdminRefundsView from '../views/AdminRefundsView.vue'
import AssignmentAdminView from '../views/AssignmentAdminView.vue'
import ActivityAdminView from '../views/ActivityAdminView.vue'
import ProfileView from '../views/ProfileView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'home', component: HomeView, meta: { roles: ['TEACHER', 'ADMIN'] } },
    { path: '/admin/users', name: 'admin-users', component: AdminUsersView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/categories', name: 'admin-categories', component: CategoryAdminView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/courses', name: 'admin-courses', component: CourseAdminListView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/courses/:id', name: 'admin-course-edit', component: CourseAdminEditView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/routes', name: 'admin-routes', component: RouteAdminListView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/routes/:id', name: 'admin-route-edit', component: RouteAdminEditView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/teachers', name: 'admin-teachers', component: TeacherAssignmentsView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/media', name: 'admin-media', component: MediaAdminView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/trades', name: 'admin-trades', component: AdminTradesView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/refunds', name: 'admin-refunds', component: AdminRefundsView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/assignments', name: 'admin-assignments', component: AssignmentAdminView, meta: { roles: ['ADMIN'] } },
    { path: '/admin/activities', name: 'admin-activities', component: ActivityAdminView, meta: { roles: ['ADMIN'] } },
    { path: '/teacher', name: 'teacher-home', component: TeacherHomeView, meta: { roles: ['TEACHER', 'ADMIN'] } },
    { path: '/teacher/submissions', name: 'teacher-submissions', component: TeacherSubmissionsView, meta: { roles: ['TEACHER'] } },
    { path: '/me', name: 'profile', component: ProfileView, meta: { roles: ['TEACHER', 'ADMIN'] } },
    { path: '/forbidden', name: 'forbidden', component: ForbiddenView, meta: { public: true } }
  ]
})

router.beforeEach(async to => {
  if (to.meta.public) {
    if (!currentMe.value && to.name === 'login') {
      await bootstrapSession()
    }
    if (currentMe.value && to.name === 'login') {
      return { name: 'home' }
    }
    return true
  }

  if (!currentMe.value) {
    const restored = await bootstrapSession()
    if (!restored) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }

  const roles = to.meta.roles as string[] | undefined
  if (roles && currentMe.value && !roles.includes(currentMe.value.role)) {
    return { name: 'forbidden' }
  }
  return true
})

export default router