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
import SignInView from '../views/SignInView.vue'
import PointsView from '../views/PointsView.vue'
import BoardView from '../views/BoardView.vue'
import MyCoursesView from '../views/MyCoursesView.vue'
import MyRoutesView from '../views/MyRoutesView.vue'
import ActivitiesView from '../views/ActivitiesView.vue'
import MyCouponsView from '../views/MyCouponsView.vue'
import LearningView from '../views/LearningView.vue'
import AssignmentView from '../views/AssignmentView.vue'
import SubmissionDetailView from '../views/SubmissionDetailView.vue'
import RouteBenefitsView from '../views/RouteBenefitsView.vue'
import CheckoutView from '../views/CheckoutView.vue'
import OrderListView from '../views/OrderListView.vue'
import OrderDetailView from '../views/OrderDetailView.vue'
import PaymentView from '../views/PaymentView.vue'
import RefundApplyView from '../views/RefundApplyView.vue'
import RefundStatusView from '../views/RefundStatusView.vue'
import RefundDetailView from '../views/RefundDetailView.vue'

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
    { path: '/routes/:id/benefits', name: 'route-benefits', component: RouteBenefitsView, meta: { roles: ['STUDENT'] } },
    { path: '/sign-in', name: 'sign-in', component: SignInView, meta: { roles: ['STUDENT'] } },
    { path: '/me/points', name: 'points', component: PointsView, meta: { roles: ['STUDENT'] } },
    { path: '/boards', name: 'boards', component: BoardView, meta: { roles: ['STUDENT'] } },
    { path: '/me/courses', name: 'my-courses', component: MyCoursesView, meta: { roles: ['STUDENT'] } },
    { path: '/me/routes', name: 'my-routes', component: MyRoutesView, meta: { roles: ['STUDENT'] } },
    { path: '/activities', name: 'activities', component: ActivitiesView, meta: { roles: ['STUDENT'] } },
    { path: '/me/coupons', name: 'my-coupons', component: MyCouponsView, meta: { roles: ['STUDENT'] } },
    { path: '/learning/courses/:id', name: 'learning', component: LearningView, meta: { roles: ['STUDENT'] } },
    { path: '/assignments/:id', name: 'assignment', component: AssignmentView, meta: { roles: ['STUDENT'] } },
    { path: '/benefit-assignments/:id', name: 'benefit-assignment', component: AssignmentView, meta: { roles: ['STUDENT'] } },
    { path: '/submissions/:id', name: 'submission-detail', component: SubmissionDetailView, meta: { roles: ['STUDENT'] } },
    { path: '/checkout', name: 'checkout', component: CheckoutView, meta: { roles: ['STUDENT'] } },
    { path: '/orders', name: 'orders', component: OrderListView, meta: { roles: ['STUDENT'] } },
    { path: '/orders/:id/pay', name: 'order-pay', component: PaymentView, meta: { roles: ['STUDENT'] } },
    { path: '/orders/:id/refund', name: 'refund-apply', component: RefundApplyView, meta: { roles: ['STUDENT'] } },
    { path: '/orders/:id', name: 'order-detail', component: OrderDetailView, meta: { roles: ['STUDENT'] } },
    { path: '/refund-applications/:requestId', name: 'refund-application', component: RefundStatusView, meta: { roles: ['STUDENT'] } },
    { path: '/refunds/:id', name: 'refund-detail', component: RefundDetailView, meta: { roles: ['STUDENT'] } },
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