import axios from 'axios'
import { http } from './http'
import type {
  ActivityView,
  AdminExcellentRewardView,
  AdminModerationAuditView,
  AdminOrderItem,
  AdminRefundItem,
  ApiEnvelope,
  ExchangeProjectView,
  AssignmentSummary,
  AssignmentView,
  CouponRuleView,
  PageResponse,
  TeacherAssignmentItem,
  TeacherBenefitSummary,
  TeacherBenefitView,
  TeacherCourseItem,
  TeacherRouteItem,
  TeacherSubmissionSummary,
  TeacherSubmissionView,
  TeacherQuestionSummary,
  TeacherQuestionThread,
  TeacherAnswerDetail,
  TeacherCommentDetail,
  TeacherNoteSummary,
  TeacherNoteDetail
} from './types'

export async function loadHealth(): Promise<{ status: string }> {
  const { data } = await http.get<{ status: string }>('/actuator/health')
  return data
}

export async function loadAdminOrders(
  state: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<AdminOrderItem>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<AdminOrderItem>>>('/api/v1/admin/orders', {
    params: { state: state || undefined, page, pageSize }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '订单列表加载失败')
  }
  return data.data
}

export async function loadAdminRefunds(
  state: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<AdminRefundItem>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<AdminRefundItem>>>('/api/v1/admin/refunds', {
    params: { state: state || undefined, page, pageSize }
  })
  if (data.code !== 'OK') {
    throw new Error(data.message || '退款列表加载失败')
  }
  return data.data
}

export async function reconcileRefund(refundId: string): Promise<Record<string, unknown>> {
  const { data } = await http.post<ApiEnvelope<Record<string, unknown>>>(
    `/api/v1/admin/refunds/${refundId}/reconcile`
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '退款对账失败')
  }
  return data.data
}

export async function runPaymentScenario(
  paymentRequestId: string,
  scenario: string,
  paidAt?: string
): Promise<Record<string, unknown>> {
  const payload: Record<string, string> = { scenario }
  if (paidAt) payload.paidAt = paidAt
  const { data } = await http.post<ApiEnvelope<Record<string, unknown>>>(
    `/api/v1/dev/payments/${paymentRequestId}/scenario`,
    payload
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '模拟场景执行失败')
  }
  return data.data
}

export async function loadCouponRules(): Promise<PageResponse<CouponRuleView>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<CouponRuleView>>>('/api/v1/admin/coupon-rules', {
    params: { page: 1, pageSize: 100 }
  })
  if (data.code !== 'OK') throw new Error(data.message || '券规则加载失败')
  return data.data
}

export async function createCouponRule(payload: Record<string, unknown>): Promise<CouponRuleView> {
  const { data } = await http.post<ApiEnvelope<CouponRuleView>>('/api/v1/admin/coupon-rules', payload)
  if (data.code !== 'OK') throw new Error(data.message || '券规则创建失败')
  return data.data
}

export async function loadAdminActivities(): Promise<PageResponse<ActivityView>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<ActivityView>>>('/api/v1/admin/activities', {
    params: { page: 1, pageSize: 100 }
  })
  if (data.code !== 'OK') throw new Error(data.message || '活动加载失败')
  return data.data
}

export async function createActivity(payload: Record<string, unknown>): Promise<ActivityView> {
  const { data } = await http.post<ApiEnvelope<ActivityView>>('/api/v1/admin/activities', payload)
  if (data.code !== 'OK') throw new Error(data.message || '活动创建失败')
  return data.data
}

export async function addActivityItem(
  activityId: string,
  payload: Record<string, unknown>
): Promise<ActivityView> {
  const { data } = await http.post<ApiEnvelope<ActivityView>>(
    `/api/v1/admin/activities/${activityId}/items`,
    payload
  )
  if (data.code !== 'OK') throw new Error(data.message || '活动券项创建失败')
  return data.data
}

export async function changeActivityState(
  activityId: string,
  action: 'activate' | 'pause' | 'end',
  revision: number
): Promise<ActivityView> {
  const { data } = await http.post<ApiEnvelope<ActivityView>>(
    `/api/v1/admin/activities/${activityId}/${action}`,
    { revision }
  )
  if (data.code !== 'OK') throw new Error(data.message || '活动状态切换失败')
  return data.data
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
export async function loadAssignments(
  courseId: string,
  status: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<AssignmentSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<AssignmentSummary>>>('/api/v1/admin/assignments', {
    params: {
      courseId: courseId ? Number(courseId) : undefined,
      status: status || undefined,
      page,
      pageSize
    }
  })
  if (data.code !== 'OK') throw new Error(data.message || '作业列表加载失败')
  return data.data
}

export async function loadAssignment(id: string): Promise<AssignmentView> {
  const { data } = await http.get<ApiEnvelope<AssignmentView>>(`/api/v1/admin/assignments/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '作业详情加载失败')
  return data.data
}

export async function createAssignment(payload: Record<string, unknown>): Promise<AssignmentView> {
  const { data } = await http.post<ApiEnvelope<AssignmentView>>('/api/v1/admin/assignments', payload)
  if (data.code !== 'OK') throw new Error(data.message || '作业草稿创建失败')
  return data.data
}

export async function updateAssignment(id: string, payload: Record<string, unknown>): Promise<AssignmentView> {
  const { data } = await http.patch<ApiEnvelope<AssignmentView>>(`/api/v1/admin/assignments/${id}`, payload)
  if (data.code !== 'OK') throw new Error(data.message || '作业草稿保存失败')
  return data.data
}

export async function publishAssignment(id: string, revision: number): Promise<AssignmentView> {
  const { data } = await http.post<ApiEnvelope<AssignmentView>>(`/api/v1/admin/assignments/${id}/publish`, { revision })
  if (data.code !== 'OK') throw new Error(data.message || '作业发布失败')
  return data.data
}

export async function offlineAssignment(id: string, revision: number): Promise<AssignmentView> {
  const { data } = await http.post<ApiEnvelope<AssignmentView>>(`/api/v1/admin/assignments/${id}/offline`, { revision })
  if (data.code !== 'OK') throw new Error(data.message || '作业下架失败')
  return data.data
}

export async function deleteAssignment(id: string, revision: number): Promise<void> {
  const { data } = await http.delete<ApiEnvelope<null>>(`/api/v1/admin/assignments/${id}`, {
    params: { revision }
  })
  if (data.code !== 'OK') throw new Error(data.message || '作业删除失败')
}

export async function loadTeacherCourses(): Promise<TeacherCourseItem[]> {
  const { data } = await http.get<ApiEnvelope<TeacherCourseItem[]>>('/api/v1/teacher/courses')
  if (data.code !== 'OK') throw new Error(data.message || '负责课程加载失败')
  return data.data
}

export async function loadTeacherAssignments(courseId: string): Promise<TeacherAssignmentItem[]> {
  const { data } = await http.get<ApiEnvelope<TeacherAssignmentItem[]>>(
    `/api/v1/teacher/courses/${courseId}/assignments`
  )
  if (data.code !== 'OK') throw new Error(data.message || '课程作业加载失败')
  return data.data
}

export async function loadTeacherSubmissions(
  courseId: string,
  assignmentId: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<TeacherSubmissionSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<TeacherSubmissionSummary>>>(
    `/api/v1/teacher/courses/${courseId}/submissions`,
    { params: { assignmentId: assignmentId ? Number(assignmentId) : undefined, page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '学生作答加载失败')
  return data.data
}

export async function loadTeacherSubmission(id: string): Promise<TeacherSubmissionView> {
  const { data } = await http.get<ApiEnvelope<TeacherSubmissionView>>(`/api/v1/teacher/submissions/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '作答详情加载失败')
  return data.data
}

export async function loadTeacherRoutes(): Promise<TeacherRouteItem[]> {
  const { data } = await http.get<ApiEnvelope<TeacherRouteItem[]>>('/api/v1/teacher/routes')
  if (data.code !== 'OK') throw new Error(data.message || '负责路线加载失败')
  return data.data
}

export async function loadTeacherBenefits(courseId: string): Promise<PageResponse<TeacherBenefitSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<TeacherBenefitSummary>>>(
    '/api/v1/teacher/benefit-assignments',
    { params: { courseId: Number(courseId), page: 1, pageSize: 100 } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '福利题列表加载失败')
  return data.data
}

export async function loadTeacherBenefit(id: string): Promise<TeacherBenefitView> {
  const { data } = await http.get<ApiEnvelope<TeacherBenefitView>>(`/api/v1/teacher/benefit-assignments/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '福利题加载失败')
  return data.data
}

export async function createTeacherBenefit(payload: Record<string, unknown>): Promise<TeacherBenefitView> {
  const { data } = await http.post<ApiEnvelope<TeacherBenefitView>>('/api/v1/teacher/benefit-assignments', payload)
  if (data.code !== 'OK') throw new Error(data.message || '福利题草稿创建失败')
  return data.data
}

export async function updateTeacherBenefit(id: string, payload: Record<string, unknown>): Promise<TeacherBenefitView> {
  const { data } = await http.patch<ApiEnvelope<TeacherBenefitView>>(`/api/v1/teacher/benefit-assignments/${id}`, payload)
  if (data.code !== 'OK') throw new Error(data.message || '福利题草稿保存失败')
  return data.data
}

export async function publishTeacherBenefit(id: string, revision: number): Promise<TeacherBenefitView> {
  const { data } = await http.post<ApiEnvelope<TeacherBenefitView>>(
    `/api/v1/teacher/benefit-assignments/${id}/publish`,
    { revision }
  )
  if (data.code !== 'OK') throw new Error(data.message || '福利题发布失败')
  return data.data
}

export async function offlineTeacherBenefit(id: string, revision: number): Promise<TeacherBenefitView> {
  const { data } = await http.post<ApiEnvelope<TeacherBenefitView>>(
    `/api/v1/teacher/benefit-assignments/${id}/offline`,
    { revision }
  )
  if (data.code !== 'OK') throw new Error(data.message || '福利题下架失败')
  return data.data
}

export async function deleteTeacherBenefit(id: string, revision: number): Promise<void> {
  const { data } = await http.delete<ApiEnvelope<null>>(`/api/v1/teacher/benefit-assignments/${id}`, {
    params: { revision }
  })
  if (data.code !== 'OK') throw new Error(data.message || '福利题删除失败')
}

export async function loadTeacherQuestions(
  courseId: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<TeacherQuestionSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<TeacherQuestionSummary>>>(
    `/api/v1/courses/${courseId}/questions`, { params: { page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '课程问题加载失败')
  return data.data
}

export async function loadTeacherQuestion(id: string): Promise<TeacherQuestionThread> {
  const { data } = await http.get<ApiEnvelope<TeacherQuestionThread>>(`/api/v1/questions/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '问题详情加载失败')
  return data.data
}

export async function createTeacherAnswer(questionId: string, body: string): Promise<TeacherAnswerDetail> {
  const { data } = await http.post<ApiEnvelope<TeacherAnswerDetail>>(
    `/api/v1/questions/${questionId}/answers`, { body }
  )
  if (data.code !== 'OK') throw new Error(data.message || '回答失败')
  return data.data
}

export async function loadAnswerComments(answerId: string): Promise<PageResponse<TeacherCommentDetail>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<TeacherCommentDetail>>>(
    `/api/v1/answers/${answerId}/comments`, { params: { page: 1, pageSize: 50 } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '评论加载失败')
  return data.data
}

export async function loadTeacherCourseNotes(
  courseId: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<TeacherNoteSummary>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<TeacherNoteSummary>>>(
    `/api/v1/courses/${courseId}/notes`, { params: { page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '公开笔记加载失败')
  return data.data
}

export async function loadTeacherNote(id: string): Promise<TeacherNoteDetail> {
  const { data } = await http.get<ApiEnvelope<TeacherNoteDetail>>(`/api/v1/notes/${id}`)
  if (data.code !== 'OK') throw new Error(data.message || '笔记加载失败')
  return data.data
}

export async function evaluateTeacherNote(id: string, contentRevision: number): Promise<TeacherNoteDetail> {
  const { data } = await http.put<ApiEnvelope<TeacherNoteDetail>>(
    `/api/v1/teacher/notes/${id}/excellent`, { contentRevision }
  )
  if (data.code !== 'OK') throw new Error(data.message || '评优失败')
  return data.data
}

export async function cancelTeacherNote(id: string): Promise<TeacherNoteDetail> {
  const { data } = await http.delete<ApiEnvelope<TeacherNoteDetail>>(
    `/api/v1/teacher/notes/${id}/excellent`
  )
  if (data.code !== 'OK') throw new Error(data.message || '取消评优失败')
  return data.data
}

export async function loadExcellentRewards(
  state: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<AdminExcellentRewardView>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<AdminExcellentRewardView>>>(
    '/api/v1/admin/excellent-rewards', { params: { state: state || undefined, page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '优秀奖励加载失败')
  return data.data
}

export async function loadModerationAudits(
  action: string,
  page = 1,
  pageSize = 20
): Promise<PageResponse<AdminModerationAuditView>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<AdminModerationAuditView>>>(
    '/api/v1/admin/moderation-actions', { params: { action: action || undefined, page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '审计记录加载失败')
  return data.data
}

export async function revokeExcellentReward(noteId: string, reason: string): Promise<void> {
  const { data } = await http.post<ApiEnvelope<unknown>>(
    `/api/v1/admin/notes/${noteId}/revoke-reward`, { reason }
  )
  if (data.code !== 'OK') throw new Error(data.message || '撤回失败')
}

export async function loadExchangeProjects(
  state: string,
  page = 1,
  pageSize = 50
): Promise<PageResponse<ExchangeProjectView>> {
  const { data } = await http.get<ApiEnvelope<PageResponse<ExchangeProjectView>>>(
    '/api/v1/admin/exchange-projects', { params: { state: state || undefined, page, pageSize } }
  )
  if (data.code !== 'OK') throw new Error(data.message || '兑换项目加载失败')
  return data.data
}

export async function createExchangeProject(payload: Record<string, unknown>): Promise<ExchangeProjectView> {
  const { data } = await http.post<ApiEnvelope<ExchangeProjectView>>(
    '/api/v1/admin/exchange-projects', payload
  )
  if (data.code !== 'OK') throw new Error(data.message || '兑换项目创建失败')
  return data.data
}

export async function updateExchangeProject(
  id: string,
  payload: Record<string, unknown>
): Promise<ExchangeProjectView> {
  const { data } = await http.patch<ApiEnvelope<ExchangeProjectView>>(
    `/api/v1/admin/exchange-projects/${id}`, payload
  )
  if (data.code !== 'OK') throw new Error(data.message || '兑换项目更新失败')
  return data.data
}

export async function changeExchangeProjectState(
  id: string,
  action: 'online' | 'offline',
  revision: number
): Promise<ExchangeProjectView> {
  const { data } = await http.post<ApiEnvelope<ExchangeProjectView>>(
    `/api/v1/admin/exchange-projects/${id}/${action}`, { revision }
  )
  if (data.code !== 'OK') throw new Error(data.message || '兑换项目状态更新失败')
  return data.data
}
