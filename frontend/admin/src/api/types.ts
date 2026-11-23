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

export interface PageResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface AdminUserItem {
  id: string
  username: string
  nickname: string
  role: UserRole
  enabled: boolean
  revision: number
  createdAt: string
}

export interface CategoryTreeNode {
  id: string
  name: string
  parentId: string
  sort: number
  enabled: boolean
  revision: number
  children: CategoryTreeNode[]
}

export interface CategoryItem {
  id: string
  name: string
  parentId: string
  sort: number
  enabled: boolean
  revision: number
}

export interface AdminCourseItem {
  id: string
  title: string
  coverUrl: string | null
  priceCent: number
  isFree: boolean
  status: string
  categoryId: string
  catalogVersion: number
  revision: number
}

export interface AdminLessonView {
  id: string
  title: string
  sort: number
  mediaId: string | null
  revision: number
}

export interface AdminChapterView {
  id: string
  title: string
  sort: number
  revision: number
  lessons: AdminLessonView[]
}

export interface AdminCourseDetail {
  id: string
  title: string
  description: string
  priceCent: number
  isFree: boolean
  status: string
  categoryId: string
  coverUrl: string | null
  catalogVersion: number
  revision: number
  chapters: AdminChapterView[]
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

export interface TeacherAssignmentsResponse {
  teacherId: string
  courseIds: string[]
  routeIds: string[]
  revision: number
}

export interface MediaItemView {
  id: string
  provider: string
  appId: string | null
  fileId: string | null
  demoPath: string | null
  durationMs: number | null
  status: string
  detached: boolean
  origin: string
  verifiedAt: string | null
  revision: number
}

export interface MediaPreviewItem {
  oldId: string
  action: string
  mediaId: string | null
  reason: string | null
}

export interface MediaImportPreviewResponse {
  operationKey: string
  batchId: string
  inputHash: string
  items: MediaPreviewItem[]
  duplicates: string[]
  missing: string[]
}

export interface MediaImportResultItem {
  oldId: string
  mediaId: string | null
  status: string
  message: string | null
}

export interface MediaImportResponse {
  operationKey: string
  batchId: string
  items: MediaImportResultItem[]
}

export interface MediaAuthorization {
  provider: string
  playSessionId: string
  expiresAt: string
  appId: string | null
  fileId: string | null
  psign: string | null
  demoUrl: string | null
  durationMs: number | null
}

export interface AdminOrderItem {
  id: string
  orderNo: string
  userId: string
  state: string
  fulfillmentState: string
  payableCent: number
  createdAt: string
  expiresAt: string
  paidAt: string | null
  paymentRequestId: string | null
  paymentRequestState: string | null
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
export interface AdminRefundItem {
  id: string
  refundNo: string
  orderId: string
  userId: string | null
  kind: string
  state: string
  amountCent: number
  progressN: number | null
  progressD: number | null
  channelRefundNo: string | null
  acceptedAt: string
  updatedAt: string
}

export interface AssignmentSummary {
  id: string
  kind: string
  courseId: string
  chapterId: string | null
  status: string
  currentVersionNo: number | null
  revision: number
  updatedAt: string
}

export interface AssignmentView {
  id: string
  kind: string
  courseId: string
  chapterId: string | null
  status: string
  currentVersionNo: number | null
  draftPublicBody: unknown
  draftAnswerBody: unknown
  currentPublicBody: unknown
  currentAnswerBody: unknown
  revision: number
  updatedAt: string
}

export interface TeacherCourseItem {
  id: string
  title: string
  status: string
  categoryId: string
}

export interface TeacherAssignmentItem {
  id: string
  kind: string
  chapterId: string | null
  status: string
  currentVersionNo: number | null
}

export interface TeacherSubmissionSummary {
  id: string
  assignmentId: string
  kind: string
  chapterId: string | null
  studentId: string
  studentNickname: string | null
  versionNo: number | null
  score: number | null
  maxScore: number | null
  reviewState: string
  submittedAt: string
}

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

export interface TeacherSubmissionDetail {
  id: string
  assignmentId: string
  versionId: string
  versionNo: number
  publicBody: ChoiceBody | CodeBody
  answers: Record<string, unknown>
  score: number | null
  maxScore: number | null
  reviewState: string
  submittedAt: string
  referenceBySlot: Record<string, string> | null
  kind: string
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
  verdict: string
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

export interface TeacherSubmissionView {
  submissionId: string
  assignmentId: string
  kind: string
  studentId: string
  studentNickname: string | null
  submission: TeacherSubmissionDetail
  review: ReviewView
}

export interface TeacherRouteItem {
  id: string
  title: string
  status: string
  priceCent: number
  memberCount: number
}

export interface BoundRouteView {
  routeId: string
  epochId: string
  boundAt: string
}

export interface TeacherBenefitSummary {
  id: string
  kind: string
  courseId: string
  status: string
  currentVersionNo: number | null
  revision: number
  updatedAt: string
  routeIds: string[]
}

export interface TeacherBenefitView {
  id: string
  kind: string
  courseId: string
  status: string
  currentVersionNo: number | null
  draftPublicBody: unknown
  draftAnswerBody: unknown
  currentPublicBody: unknown
  currentAnswerBody: unknown
  revision: number
  updatedAt: string
  routes: BoundRouteView[]
}
