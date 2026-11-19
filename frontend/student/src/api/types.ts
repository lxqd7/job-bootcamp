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