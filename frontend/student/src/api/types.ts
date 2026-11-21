export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
  traceId: string
}

export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN'

export interface UserSummary {
  id: string
  nickname: string
  role: UserRole
}

export interface MeResponse extends UserSummary {
  avatarUrl: string | null
  permissions: string[]
  revision: number
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: UserSummary
  csrfToken: string
}

export interface RefreshResponse {
  accessToken: string
  expiresIn: number
}

export interface CategoryNode {
  id: string
  name: string
  children: CategoryNode[]
}

export interface CategoryResult {
  categories: CategoryNode[]
  traceId: string
}

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface CourseSummary {
  id: string
  title: string
  coverUrl: string | null
  priceCent: number
  isFree: boolean
  status: string
  categoryId: string
}

export interface LessonView {
  id: string
  title: string
  sort: number
  hasVideo: boolean
}

export interface ChapterView {
  id: string
  title: string
  sort: number
  lessons: LessonView[]
}

export interface CourseDetail {
  id: string
  title: string
  description: string
  priceCent: number
  isFree: boolean
  status: string
  categoryId: string
  coverUrl: string | null
  catalogVersion: number
  isOwned: boolean
  chapters: ChapterView[]
}

export interface RouteMemberView {
  courseId: string
  title: string
  sort: number
  unitPriceCent: number
  isFree: boolean
  allocatedCent: number
  epochId: string | null
}

export interface RouteSummary {
  id: string
  title: string
  priceCent: number
  status: string
  memberCount: number
  revision: number
}

export interface RouteDetail {
  id: string
  title: string
  description: string
  priceCent: number
  status: string
  currentVersionNo: number
  revision: number
  draft: boolean
  members: RouteMemberView[]
}

export interface PointsSummary {
  available: number
  debt: number
  currentMonthScore: number
}

export interface PointsLedgerItem {
  id: string
  type: string
  rewardPoints: number
  balanceDelta: number
  debtDelta: number
  rankDelta: number
  balanceAfter: number
  debtAfter: number
  businessDate: string
  businessMonth: string
  occurredAt: string
  resultCode: string | null
}

export interface SignStatus {
  month: string
  date: string
  signedToday: boolean
  continuousDays: number
  todayPoints: number
  monthSignedDays: number
  days: number[]
  available: number
  debt: number
  currentMonthScore: number
}

export interface SignResult {
  date: string
  basePoints: number
  bonusPoints: number
  rewardPoints: number
  continuousDays: number
  alreadySigned: boolean
  available: number
  debt: number
  monthScore: number
}

export interface BoardItem {
  nickname: string
  score: number
  rank: number
}

export interface BoardResponse {
  month: string
  items: BoardItem[]
  total: number
  page: number
  pageSize: number
  myRank: number | null
}

export interface MyCourseItem {
  courseId: string
  sourceId: string
  sourceType: string
  grantedAt: string
}

export interface MyRouteItem {
  routeId: string
  sourceId: string
  futureCourses: boolean
  acquiredAt: string
}

export interface PlayAuthorization {
  provider: string
  playSessionId: string
  expiresAt: string
  appId: string | null
  fileId: string | null
  psign: string | null
  demoUrl: string | null
  durationMs: number
  courseId: string
}

export interface LearningLesson {
  id: string
  title: string
  sort: number
  mediaId: string | null
  durationMs: number | null
  positionMs: number
  maxPositionMs: number
  completed: boolean
  completedAt: string | null
}

export interface LearningChapter {
  id: string
  title: string
  sort: number
  lessons: LearningLesson[]
}

export interface LearningCourse {
  courseId: string
  title: string
  description: string
  catalogVersion: number
  totalCount: number
  completedCount: number
  chapters: LearningChapter[]
}

export interface ProgressResult {
  completed: boolean
  firstCompletion: boolean
  completedAt: string | null
  courseCompletedCount: number
  totalCount: number
}

export interface TradeQuote {
  quoteId: string
  status: string
  expiresAt: string
  quoteHash: string
  product: {
    type: string
    id: string
    title: string
    version: number
  }
  courses: QuoteCourseLine[]
  couponPlan: unknown[]
  totalCent: number
  creditCent: number
  discountCent: number
  payableCent: number
  refundDependencyWarnings: string[]
}

export interface QuoteCourseLine {
  courseId: string
  unitPriceCent: number
  allocatedCent: number
  creditCent: number
  sourceId: string | null
  sourceGrantId: string | null
  sourceOrderId: string | null
  remainingCent: number
}

export interface OrderItem {
  courseId: string
  courseVersion: number
  unitPriceCent: number
  allocatedCent: number
  creditCent: number
  couponCent: number
  payableCent: number
  free: boolean
  newPaid: boolean
}

export interface OrderResponse {
  id: string
  orderNo: string
  state: string
  fulfillmentState: string
  createdAt: string
  expiresAt: string
  paidAt: string | null
  pricing: {
    totalCent: number
    creditCent: number
    discountCent: number
    payableCent: number
  }
  items: OrderItem[]
  coupons: unknown[]
  mode: string
}

export interface PaymentInitiateResponse {
  paymentId: string
  payAction: string
  mode: string
  state: string
  expiresAt: string
}

export interface ClaimResult {
  claimId: string
  courseId: string
  status: string
  sourceId: string | null
}