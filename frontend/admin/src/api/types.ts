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