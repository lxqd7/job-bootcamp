import axios from 'axios'
import { http } from './http'
import type {
  ApiEnvelope,
  BoardResponse,
  CategoryNode,
  CourseDetail,
  CourseSummary,
  LearningCourse,
  MyCourseItem,
  PageResponse,
  PlayAuthorization,
  PointsLedgerItem,
  PointsSummary,
  ProgressResult,
  RouteDetail,
  RouteSummary,
  SignResult,
  SignStatus
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