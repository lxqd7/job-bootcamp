import axios from 'axios'
import { http } from './http'
import type {
  ApiEnvelope,
  BoardResponse,
  CategoryNode,
  ClaimResult,
  CourseDetail,
  CourseSummary,
  LearningCourse,
  MyCourseItem,
  MyRouteItem,
  OrderResponse,
  PageResponse,
  PaymentInitiateResponse,
  PlayAuthorization,
  PointsLedgerItem,
  PointsSummary,
  ProgressResult,
  RefundDetail,
  RefundIntentView,
  RefundStatus,
  RouteDetail,
  RouteSummary,
  SignResult,
  SignStatus,
  TradeQuote,
  ActivityView,
  UserCouponView,
  AssignmentProgress,
  AttemptView,
  ReviewView,
  StudentAssignment,
  StudentBenefit,
  SubmissionDetail,
  SubmissionSummary,
  QuestionSummary,
  QuestionDetail,
  QuestionThread,
  AnswerDetail,
  CommentDetail,
  LikeResult,
  NoteSummary,
  NoteDetail
} from './types'

export interface CategoryResult {
  categories: CategoryNode[]
  traceId: string
}

export async function loadHealth(): Promise<{ status: string }> {
  const { data } = await http.get<{ status: string }>('/actuator/health')
  return data
}

export async function loadCategories(): Promise<CategoryResult> {
  const { data } = await http.get<ApiEnvelope<CategoryNode[]>>('/api/v1/categories')
  if (data.code !== 'OK') {
    throw new Error(data.message || '分类接口返回异常')
  }
  return { categories: data.data, traceId: data.traceId }
}

export async function loadCourses(params: {
  keyword?: string
  categoryId?: string
  page?: number
  pageSize?: number
}): Promise<PageResponse<CourseSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<CourseSummary>>>('/api/v1/courses', { params })
  if (data.code !== 'OK') {
    throw new Error(data.message || '课程接口返回异常')
  }
  return data.data
}

export async function loadCourse(id: string): Promise<CourseDetail> {
  const { data } = await http.get<ApiEnvelope<CourseDetail>>(`/api/v1/courses/${id}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '课程详情接口返回异常')
  }
  return data.data
}

export async function loadRoutes(page = 1, pageSize = 20): Promise<PageResponse<RouteSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<RouteSummary>>>('/api/v1/routes', {
    params: { page, pageSize }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '路线接口返回异常')
  }
  return data.data
}

export async function loadRoute(id: string): Promise<RouteDetail> {
  const { data } = await http.get<ApiEnvelope<RouteDetail>>(`/api/v1/routes/${id}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '路线详情接口返回异常')
  }
  return data.data
}

export async function loadPointsSummary(): Promise<PointsSummary> {
  const { data } = await http.get<ApiEnvelope<PointsSummary>>('/api/v1/me/points')
  if (data.code !== 'OK') {
    throw new Error(data.message || '积分接口返回异常')
  }
  return data.data
}

export async function loadPointsLedger(page = 1, pageSize = 20): Promise<PageResponse<PointsLedgerItem>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<PointsLedgerItem>>>(
    '/api/v1/me/points/ledger',
    { params: { page, pageSize } }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '积分流水接口返回异常')
  }
  return data.data
}

export async function loadSignStatus(month?: string): Promise<SignStatus> {
  const { data } = await http.get<ApiEnvelope<SignStatus>>('/api/v1/sign-ins', {
    params: month ? { month } : undefined
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '签到状态接口返回异常')
  }
  return data.data
}

export async function signIn(): Promise<SignResult> {
  const { data } = await http.post<ApiEnvelope<SignResult>>('/api/v1/sign-ins')
  if (data.code !== 'OK') {
    throw new Error(data.message || '签到失败')
  }
  return data.data
}

export async function loadBoard(month: string, page = 1, pageSize = 20): Promise<BoardResponse> {
  const { data } = await http.get<ApiEnvelope<BoardResponse>>('/api/v1/boards/monthly', {
    params: { month, page, pageSize }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '月榜接口返回异常')
  }
  return data.data
}

export async function loadMyCourses(page = 1, pageSize = 100): Promise<PageResponse<MyCourseItem>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<MyCourseItem>>>('/api/v1/me/courses', {
    params: { page, pageSize }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '我的课程接口返回异常')
  }
  return data.data
}

export async function loadMyRoutes(page = 1, pageSize = 100): Promise<PageResponse<MyRouteItem>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<MyRouteItem>>>('/api/v1/me/routes', {
    params: { page, pageSize }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '我的路线接口返回异常')
  }
  return data.data
}

export async function loadActivities(page = 1, pageSize = 20): Promise<PageResponse<ActivityView>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<ActivityView>>>('/api/v1/activities', {
    params: { page, pageSize }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '活动列表接口返回异常')
  }
  return data.data
}

export async function loadActivity(id: string): Promise<ActivityView> {
  const { data } = await http.get<ApiEnvelope<ActivityView>>(`/api/v1/activities/${id}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '活动接口返回异常')
  }
  return data.data
}

export async function claimCoupon(itemId: string, requestKey: string): Promise<UserCouponView> {
  const { data } = await http.post<ApiEnvelope<UserCouponView>>(
    `/api/v1/activity-items/${itemId}/claims`,
    null,
    { headers: { 'Idempotency-Key': requestKey } }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '领券失败')
  }
  return data.data
}

export async function loadMyCoupons(
  displayStatus: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<UserCouponView>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<UserCouponView>>>('/api/v1/me/coupons', {
    params: { displayStatus: displayStatus || undefined, page, pageSize }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '我的券接口返回异常')
  }
  return data.data
}

export async function loadLearningCourse(courseId: string): Promise<LearningCourse> {
  const { data } = await http.get<ApiEnvelope<LearningCourse>>(`/api/v1/learning/courses/${courseId}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '学习课程接口返回异常')
  }
  return data.data
}

export async function authorizeLesson(lessonId: string): Promise<PlayAuthorization> {
  const { data } = await http.post<ApiEnvelope<PlayAuthorization>>(
    `/api/v1/lessons/${lessonId}/play-authorizations`
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '播放授权失败')
  }
  return data.data
}

export async function reportProgress(
  lessonId: string,
  positionMs: number,
  playSessionId: string
): Promise<ProgressResult> {
  const { data } = await http.put<ApiEnvelope<ProgressResult>>(
    `/api/v1/learning/lessons/${lessonId}/progress`,
    { positionMs, playSessionId }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '进度上报失败')
  }
  return data.data
}

export async function createQuote(
  productType: 'COURSE' | 'ROUTE',
  productId: string,
  mode: 'NONE' | 'AUTO' | 'MANUAL' = 'NONE',
  couponIds: string[] = []
): Promise<TradeQuote> {
  const { data } = await http.post<ApiEnvelope<TradeQuote>>('/api/v1/trade/quotes', {
    productType,
    productId: Number(productId),
    couponSelection: { mode, ids: couponIds.map(Number) }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '报价失败')
  }
  return data.data
}

export async function loadQuote(quoteId: string): Promise<TradeQuote> {
  const { data } = await http.get<ApiEnvelope<TradeQuote>>(`/api/v1/trade/quotes/${quoteId}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '报价查询失败')
  }
  return data.data
}

export async function createOrder(
  quoteId: string,
  quoteHash: string,
  idempotencyKey: string,
  selectedCouponIds: string[] = []
): Promise<OrderResponse> {
  const { data } = await http.post<ApiEnvelope<OrderResponse>>(
    '/api/v1/orders',
    { quoteId, quoteHash, selectedCouponIds: selectedCouponIds.map(Number) },
    { headers: { 'Idempotency-Key': idempotencyKey } }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '创建订单失败')
  }
  return data.data
}

export async function loadOrders(
  page = 1,
  pageSize = 20,
  state?: string
): Promise<PageResponse<OrderResponse>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<OrderResponse>>>('/api/v1/orders', {
    params: { page, pageSize, state: state || undefined }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '订单列表加载失败')
  }
  return data.data
}

export async function loadOrder(orderId: string): Promise<OrderResponse> {
  const { data } = await http.get<ApiEnvelope<OrderResponse>>(`/api/v1/orders/${orderId}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '订单详情加载失败')
  }
  return data.data
}

export async function initiatePayment(orderId: string): Promise<PaymentInitiateResponse> {
  const { data } = await http.post<ApiEnvelope<PaymentInitiateResponse>>(
    `/api/v1/orders/${orderId}/payment`,
    { channel: 'MOCK' }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '创建支付单失败')
  }
  return data.data
}

export async function mockPay(orderId: string): Promise<OrderResponse> {
  const { data } = await http.post<ApiEnvelope<OrderResponse>>(
    `/api/v1/orders/${orderId}/payment/mock-pay`
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '模拟付款失败')
  }
  return data.data
}

export async function cancelOrder(orderId: string): Promise<OrderResponse> {
  const { data } = await http.post<ApiEnvelope<OrderResponse>>(`/api/v1/orders/${orderId}/cancel`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '取消订单失败')
  }
  return data.data
}

export async function applyRefund(orderId: string, reason?: string): Promise<RefundIntentView> {
  const { data } = await http.post<ApiEnvelope<RefundIntentView>>(
    `/api/v1/orders/${orderId}/refund-applications`,
    { reason: reason || null }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '退款申请提交失败')
  }
  return data.data
}

export async function loadRefundApplication(requestId: string): Promise<RefundIntentView> {
  const { data } = await http.get<ApiEnvelope<RefundIntentView>>(
    `/api/v1/refund-applications/${requestId}`
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '退款申请查询失败')
  }
  return data.data
}

export async function loadRefundByOrder(orderId: string): Promise<RefundStatus> {
  const { data } = await http.get<ApiEnvelope<RefundStatus>>(`/api/v1/refunds/by-order/${orderId}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '退款单查询失败')
  }
  return data.data
}

export async function loadRefund(refundId: string): Promise<RefundDetail> {
  const { data } = await http.get<ApiEnvelope<RefundDetail>>(`/api/v1/refunds/${refundId}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '退款单查询失败')
  }
  return data.data
}

export async function claimCourse(courseId: string): Promise<ClaimResult> {
  const { data } = await http.post<ApiEnvelope<ClaimResult>>(`/api/v1/courses/${courseId}/claim`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '免费领取失败')
  }
  return data.data
}

export async function loadClaim(claimId: string): Promise<ClaimResult> {
  const { data } = await http.get<ApiEnvelope<ClaimResult>>(`/api/v1/claims/${claimId}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '领取状态加载失败')
  }
  return data.data
}

export function describeTradeError(error: unknown): string {
  const responseData = (error as { response?: { data?: { message?: string } } }).response?.data
  if (responseData?.message) {
    return responseData.message
  }
  return describeError(error)
}

export function describeError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED') {
      return '请求超时，请确认服务仍在运行。'
    }
    if (error.response) {
      if (error.response.status >= 500) {
        return '服务暂时不可用，请确认服务已启动后重试。'
      }
      return `请求被拒绝（HTTP ${error.response.status}），请查看服务端日志。`
    }
    return '无法连接服务，请确认本地服务已启动。'
  }
  return error instanceof Error ? error.message : '发生未知错误。'
}
export async function loadCourseAssignments(courseId: string): Promise<StudentAssignment[]> {
  const { data } = await http.get<ApiEnvelope<StudentAssignment[]>>(
    `/api/v1/courses/${courseId}/assignments`
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '课程作业加载失败')
  }
  return data.data
}

export async function loadAssignment(id: string): Promise<StudentAssignment> {
  const { data } = await http.get<ApiEnvelope<StudentAssignment>>(`/api/v1/assignments/${id}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '作业加载失败')
  }
  return data.data
}

export async function loadAssignmentProgress(id: string): Promise<AssignmentProgress> {
  const { data } = await http.get<ApiEnvelope<AssignmentProgress>>(
    `/api/v1/assignments/${id}/progress`
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '作业成绩加载失败')
  }
  return data.data
}

export async function startAttempt(assignmentId: string): Promise<AttemptView> {
  const { data } = await http.post<ApiEnvelope<AttemptView>>(
    `/api/v1/assignments/${assignmentId}/attempts`
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '开始作答失败')
  }
  return data.data
}

export async function loadAttempt(attemptId: string): Promise<AttemptView> {
  const { data } = await http.get<ApiEnvelope<AttemptView>>(`/api/v1/attempts/${attemptId}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '作答进度加载失败')
  }
  return data.data
}

export async function saveDraft(
  attemptId: string,
  answers: Record<string, unknown>,
  draftRevision: number
): Promise<AttemptView> {
  const { data } = await http.put<ApiEnvelope<AttemptView>>(
    `/api/v1/attempts/${attemptId}/draft`,
    { answers, draftRevision }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '草稿保存失败')
  }
  return data.data
}

export async function submitAttempt(
  attemptId: string,
  answers: Record<string, unknown>,
  idempotencyKey: string
): Promise<SubmissionDetail> {
  const { data } = await http.post<ApiEnvelope<SubmissionDetail>>(
    `/api/v1/attempts/${attemptId}/submissions`,
    { answers },
    { headers: { 'Idempotency-Key': idempotencyKey } }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '提交失败')
  }
  return data.data
}

export async function loadSubmissions(
  assignmentId: string,
  page = 1,
  pageSize = 50
): Promise<PageResponse<SubmissionSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<SubmissionSummary>>>(
    `/api/v1/assignments/${assignmentId}/submissions`,
    { params: { page, pageSize } }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '提交历史加载失败')
  }
  return data.data
}

export async function loadSubmission(id: string): Promise<SubmissionDetail> {
  const { data } = await http.get<ApiEnvelope<SubmissionDetail>>(`/api/v1/submissions/${id}`)
  if (data.code !== 'OK') {
    throw new Error(data.message || '提交详情加载失败')
  }
  return data.data
}

export async function loadReview(id: string): Promise<ReviewView> {
  // 轮询时避免浏览器把上一次“排队中”的响应当作可缓存结果。
  const { data } = await http.get<ApiEnvelope<ReviewView>>(`/api/v1/submissions/${id}/review`, {
    params: { _t: Date.now() }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || 'AI 点评状态加载失败')
  }
  return data.data
}

export function httpStatus(error: unknown): number | null {
  return (error as { response?: { status?: number } }).response?.status ?? null
}

export async function loadRouteBenefits(routeId: string, courseId: string): Promise<StudentBenefit[]> {
  const { data } = await http.get<ApiEnvelope<StudentBenefit[]>>(
    `/api/v1/routes/${routeId}/benefit-assignments`,
    { params: { courseId: Number(courseId) } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '福利题列表加载失败')
  return data.data
}

export async function loadBenefitAssignment(id: string): Promise<StudentBenefit> {
  const { data } = await http.get<ApiEnvelope<StudentBenefit>>(`/api/v1/benefit-assignments/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '福利题加载失败')
  return data.data
}

export async function startBenefitAttempt(id: string): Promise<AttemptView> {
  const { data } = await http.post<ApiEnvelope<AttemptView>>(
    `/api/v1/benefit-assignments/${id}/attempts`
  )
  if (data.code !== 'OK') throw new Error(data.message || '开始作答失败')
  return data.data
}

export async function loadBenefitProgress(id: string): Promise<AssignmentProgress> {
  const { data } = await http.get<ApiEnvelope<AssignmentProgress>>(
    `/api/v1/benefit-assignments/${id}/progress`
  )
  if (data.code !== 'OK') throw new Error(data.message || '福利题成绩加载失败')
  return data.data
}

export async function loadBenefitSubmissions(
  id: string,
  page = 1,
  pageSize = 50
): Promise<PageResponse<SubmissionSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<SubmissionSummary>>>(
    `/api/v1/benefit-assignments/${id}/submissions`,
    { params: { page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '福利题提交历史加载失败')
  return data.data
}

export async function loadQuestions(courseId: string, page = 1, pageSize = 20): Promise<PageResponse<QuestionSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<QuestionSummary>>>(
    `/api/v1/courses/${courseId}/questions`, { params: { page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '问题列表加载失败')
  return data.data
}

export async function createQuestion(
  courseId: string,
  payload: { lessonId?: number | null; title: string; body: string; anonymous: boolean }
): Promise<QuestionDetail> {
  const { data } = await http.post<ApiEnvelope<QuestionDetail>>(
    `/api/v1/courses/${courseId}/questions`, payload
  )
  if (data.code !== 'OK') throw new Error(data.message || '提问失败')
  return data.data
}

export async function loadQuestion(id: string, page = 1, pageSize = 20): Promise<QuestionThread> {
  const { data } = await http.get<ApiEnvelope<QuestionThread>>(`/api/v1/questions/${id}`, {
    params: { page, pageSize }
  })
  if (data.code !== 'OK') throw new Error(data.message || '问题详情加载失败')
  return data.data
}

export async function deleteQuestion(id: string): Promise<void> {
  const { data } = await http.delete<ApiEnvelope<null>>(`/api/v1/questions/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '删除问题失败')
}

export async function createAnswer(questionId: string, body: string): Promise<AnswerDetail> {
  const { data } = await http.post<ApiEnvelope<AnswerDetail>>(
    `/api/v1/questions/${questionId}/answers`, { body }
  )
  if (data.code !== 'OK') throw new Error(data.message || '回答失败')
  return data.data
}

export async function deleteAnswer(id: string): Promise<void> {
  const { data } = await http.delete<ApiEnvelope<null>>(`/api/v1/answers/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '删除回答失败')
}

export async function loadComments(answerId: string, page = 1, pageSize = 50): Promise<PageResponse<CommentDetail>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<CommentDetail>>>(
    `/api/v1/answers/${answerId}/comments`, { params: { page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '评论加载失败')
  return data.data
}

export async function createComment(answerId: string, body: string): Promise<CommentDetail> {
  const { data } = await http.post<ApiEnvelope<CommentDetail>>(
    `/api/v1/answers/${answerId}/comments`, { body }
  )
  if (data.code !== 'OK') throw new Error(data.message || '评论失败')
  return data.data
}

export async function deleteComment(id: string): Promise<void> {
  const { data } = await http.delete<ApiEnvelope<null>>(`/api/v1/comments/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '删除评论失败')
}

export async function likeTarget(type: 'ANSWER' | 'COMMENT', id: string): Promise<LikeResult> {
  const { data } = await http.put<ApiEnvelope<LikeResult>>(`/api/v1/likes/${type}/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '点赞失败')
  return data.data
}

export async function unlikeTarget(type: 'ANSWER' | 'COMMENT', id: string): Promise<LikeResult> {
  const { data } = await http.delete<ApiEnvelope<LikeResult>>(`/api/v1/likes/${type}/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '取消点赞失败')
  return data.data
}

export async function loadCourseNotes(courseId: string, page = 1, pageSize = 20): Promise<PageResponse<NoteSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<NoteSummary>>>(
    `/api/v1/courses/${courseId}/notes`, { params: { page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '公开笔记加载失败')
  return data.data
}

export async function createNote(
  courseId: string,
  payload: { lessonId?: number | null; positionMs?: number | null; body: string; visibility: 'PRIVATE' | 'COURSE_PUBLIC' }
): Promise<NoteDetail> {
  const { data } = await http.post<ApiEnvelope<NoteDetail>>(`/api/v1/courses/${courseId}/notes`, payload)
  if (data.code !== 'OK') throw new Error(data.message || '创建笔记失败')
  return data.data
}

export async function loadMyNotes(type = '', page = 1, pageSize = 20): Promise<PageResponse<NoteSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<NoteSummary>>>('/api/v1/me/notes', {
    params: { type: type || undefined, page, pageSize }
  })
  if (data.code !== 'OK') throw new Error(data.message || '我的笔记加载失败')
  return data.data
}

export async function loadNote(id: string): Promise<NoteDetail> {
  const { data } = await http.get<ApiEnvelope<NoteDetail>>(`/api/v1/notes/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '笔记加载失败')
  return data.data
}

export async function updateNote(
  id: string,
  payload: { body?: string; visibility?: 'PRIVATE' | 'COURSE_PUBLIC'; revision: number }
): Promise<NoteDetail> {
  const { data } = await http.patch<ApiEnvelope<NoteDetail>>(`/api/v1/notes/${id}`, payload)
  if (data.code !== 'OK') throw new Error(data.message || '笔记保存失败')
  return data.data
}

export async function deleteNote(id: string): Promise<void> {
  const { data } = await http.delete<ApiEnvelope<null>>(`/api/v1/notes/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '笔记删除失败')
}

export async function collectNote(id: string): Promise<NoteDetail> {
  const { data } = await http.post<ApiEnvelope<NoteDetail>>(`/api/v1/notes/${id}/collect`)
  if (data.code !== 'OK') throw new Error(data.message || '采集失败')
  return data.data
}
