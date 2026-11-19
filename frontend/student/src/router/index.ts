import { createRouter, createWebHistory } from 'vue-router'
import { bootstrapSession, currentMe } from '../auth/session'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import ProfileView from '../views/ProfileView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import CourseListView from '../views/CourseListView.vue'
import CourseDetailView from '../views/CourseDetailView.vue'
import RouteListView from '../views/RouteListView.vue'
import RouteDetailView from '../views/RouteDetailView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    { path: '/', name: 'home', component: HomeView, meta: { roles: ['STUDENT'] } },
    { path: '/courses', name: 'courses', component: CourseListView, meta: { roles: ['STUDENT'] } },
    { path: '/courses/:id', name: 'course-detail', component: CourseDetailView, meta: { roles: ['STUDENT'] } },
    { path: '/routes', name: 'routes', component: RouteListView, meta: { roles: ['STUDENT'] } },
    { path: '/routes/:id', name: 'route-detail', component: RouteDetailView, meta: { roles: ['STUDENT'] } },
    { path: '/me', name: 'profile', component: ProfileView, meta: { roles: ['STUDENT'] } },
    { path: '/forbidden', name: 'forbidden', component: ForbiddenView, meta: { public: true } }
  ]
})

router.beforeEach(async to => {
  if (to.meta.public) {
    if (!currentMe.value && (to.name === 'login' || to.name === 'register')) {
      await bootstrapSession()
    }
    if (currentMe.value && (to.name === 'login' || to.name === 'register')) {
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