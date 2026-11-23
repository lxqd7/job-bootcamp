import axios from 'axios'
import { http } from './http'
import type {
  ActivityView,
  AdminOrderItem,
  AdminRefundItem,
  ApiEnvelope,
  AssignmentSummary,
  AssignmentView,
  CouponRuleView,
  PageResponse
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
