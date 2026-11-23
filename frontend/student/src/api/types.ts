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

export interface CouponAllocation {
  courseId: string
  discountCent: number
}

export interface CouponPlanLine {
  userCouponId: string
  applyOrder: number
  baseCent: number
  discountCent: number
  allocation: CouponAllocation[]
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
  couponPlan: CouponPlanLine[]
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
  coupons: CouponPlanLine[]
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

export interface CouponRuleView {
  id: string
  ruleKey: string
  versionNo: number
  type: string
  scope: string
  targetId: string | null
  thresholdCent: number | null
  amountCent: number | null
  payRateBps: number | null
  maxDiscountCent: number | null
  validityType: string
  validFrom: string | null
  validUntil: string | null
  validDays: number | null
  issued: boolean
  revision: number
}

export interface ActivityItemView {
  id: string
  ruleId: string
  rule: CouponRuleView | null
  stockTotal: number
  issuedCount: number
  perUserLimit: number
  enabled: boolean
  revision: number
}

export interface ActivityView {
  id: string
  name: string
  state: string
  openAt: string
  closeAt: string
  revision: number
  items: ActivityItemView[]
}

export interface UserCouponView {
  id: string
  rule: CouponRuleView | null
  sourceType: string
  sourceGroupId: string
  sourceItemId: string
  validFrom: string
  validUntil: string
  status: string
  displayStatus: string
  lockedOrderId: string | null
  usedOrderId: string | null
  createdAt: string
}
export interface RefundIntentView {
  requestId: string
  orderId: string
  acceptedAt: string
  sequence: number
  state: string
  reason: string | null
  completedCount: number
  totalCount: number
}

export interface RefundStatus {
  refundId: string
  orderId: string
  kind: string
  state: string
  amountCent: number
  progressN: number | null
  progressD: number | null
  channelRefundNo: string | null
  acceptedAt: string
  updatedAt: string
}

export interface RefundDetail {
  id: string
  refundNo: string
  orderId: string
  paymentId: string
  kind: string
  state: string
  amountCent: number
  acceptedAt: string
  channelRefundNo: string | null
}

export type AssignmentKind = 'CHAPTER_CHOICE' | 'COURSE_CODE' | 'BENEFIT_CHOICE' | 'BENEFIT_CODE'

export interface ChoiceOption {
  id: string
  text: string
}

export interface ChoiceQuestion {
  id: string
  type: 'SINGLE' | 'MULTIPLE'
  body: string
  score: number
  options: ChoiceOption[]
}

export interface ChoiceBody {
  questions: ChoiceQuestion[]
}

export interface CodeSegment {
  text?: string
  slotId?: string
  initialCode?: string
}

export interface CodeBody {
  scenario: string
  requirements: string
  segments: CodeSegment[]
}

export type AssignmentBody = ChoiceBody | CodeBody

export interface StudentAssignment {
  id: string
  kind: AssignmentKind
  courseId: string
  chapterId: string | null
  status: string
  currentVersionId: string | null
  currentVersionNo: number | null
  publicBody: AssignmentBody | null
  currentVersionSubmitted: boolean
  openAttemptId: string | null
}

export interface AttemptView {
  id: string
  assignmentId: string
  versionId: string
  status: string
  publicBody: AssignmentBody
  draft: Record<string, unknown> | null
  draftRevision: number
  startedAt: string
}

export interface SubmissionSummary {
  id: string
  versionNo: number | null
  score: number | null
  maxScore: number | null
  reviewState: string
  submittedAt: string
}

export interface AssignmentProgress {
  assignmentId: string
  status: string
  currentVersionId: string | null
  currentVersionNo: number | null
  currentVersionSubmitted: boolean
  openAttemptId: string | null
  openAttemptRevision: number | null
  recent: SubmissionSummary | null
  highest: SubmissionSummary | null
}

export interface SubmissionDetail {
  id: string
  assignmentId: string
  versionId: string
  versionNo: number
  publicBody: AssignmentBody
  answers: Record<string, unknown>
  score: number | null
  maxScore: number | null
  reviewState: string
  submittedAt: string
  referenceBySlot: Record<string, string> | null
  kind: AssignmentKind
  correctAnswers: Record<string, string[]> | null
  analysis: Record<string, string> | null
}

export interface AiIssue {
  slotId: string
  evidence: string
  reason: string
  suggestion: string
}

export interface AiFeedback {
  verdict: 'SATISFIED' | 'PARTIAL' | 'NEEDS_CHANGE'
  summary: string
  issues: AiIssue[]
  suggestions: string[]
  limitations: string[]
  isMock: boolean
}

export interface ReviewView {
  taskStatus: string
  feedback: AiFeedback | null
  safeErrorCode: string | null
  isMock: boolean | null
}

export interface StudentBenefit {
  id: string
  kind: AssignmentKind
  courseId: string
  status: string
  currentVersionNo: number | null
  publicBody: AssignmentBody | null
  routeId: string
  epochId: string
  currentVersionSubmitted: boolean
  openAttemptId: string | null
}
