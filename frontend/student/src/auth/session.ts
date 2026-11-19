import axios from 'axios'
import { computed, ref } from 'vue'
import { setTokenProvider, setUnauthorizedHandler } from '../api/http'
import type {
  ApiEnvelope,
  LoginResponse,
  MeResponse,
  RefreshResponse,
  UserRole,
  UserSummary
} from '../api/types'

const CLIENT_APP = 'STUDENT'
const CSRF_COOKIE = 'bc_student_csrf'
const CHANNEL_NAME = 'bc_student_auth'
const LOCK_NAME = 'bc_student_refresh'
const AUTH_BASE = '/api/v1/auth'

const accessToken = ref('')
const me = ref<MeResponse | null>(null)
const clearedListeners = new Set<() => void>()

type AuthMessage =
  | { type: 'TOKEN'; token: string; me: MeResponse }
  | { type: 'CLEAR' }

const channel = typeof BroadcastChannel === 'undefined'
  ? null
  : new BroadcastChannel(CHANNEL_NAME)

if (channel) {
  channel.onmessage = (event: MessageEvent<AuthMessage>) => {
    const message = event.data
    if (message.type === 'TOKEN') {
      accessToken.value = message.token
      me.value = message.me
    } else if (message.type === 'CLEAR') {
      clearLocal()
      notifyCleared()
    }
  }
}

export const currentMe = computed(() => me.value)
export const currentRole = computed<UserRole | null>(() => me.value?.role ?? null)
export const isLoggedIn = computed(() => Boolean(accessToken.value && me.value))
export const currentPermissions = computed(() => me.value?.permissions ?? [])

setTokenProvider(() => accessToken.value)
setUnauthorizedHandler(async () => {
  const refreshed = await refreshSession(true)
  if (!refreshed) {
    notifyCleared()
  }
  return refreshed
})

export function getAccessToken(): string {
  return accessToken.value
}

export function onAuthCleared(listener: () => void): void {
  clearedListeners.add(listener)
}

function notifyCleared(): void {
  for (const listener of clearedListeners) {
    listener()
  }
}

function broadcast(message: AuthMessage): void {
  channel?.postMessage(message)
}

function plainMe(value: MeResponse): MeResponse {
  return {
    id: value.id,
    nickname: value.nickname,
    role: value.role,
    avatarUrl: value.avatarUrl,
    permissions: [...value.permissions],
    revision: value.revision
  }
}

function clearLocal(): void {
  accessToken.value = ''
  me.value = null
}

function readCookie(name: string): string {
  const prefix = `${name}=`
  for (const part of document.cookie.split('; ')) {
    if (part.startsWith(prefix)) {
      return decodeURIComponent(part.slice(prefix.length))
    }
  }
  return ''
}

function toMe(user: UserSummary): MeResponse {
  return {
    id: user.id,
    nickname: user.nickname,
    role: user.role,
    avatarUrl: null,
    permissions: [],
    revision: 0
  }
}

async function loadMe(): Promise<boolean> {
  if (!accessToken.value) {
    return false
  }
  try {
    const { data } = await axios.get<ApiEnvelope<MeResponse>>('/api/v1/me', {
      headers: { Authorization: `Bearer ${accessToken.value}` },
      withCredentials: true,
      timeout: 5000
    })
    if (data.code !== 'OK') {
      return false
    }
    me.value = data.data
    return true
  } catch {
    return false
  }
}

type LockManagerLike = {
  request<T>(name: string, callback: () => Promise<T>): Promise<T>
}

function withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
  const locks = (navigator as Navigator & { locks?: LockManagerLike }).locks
  if (locks) {
    return locks.request<T>(LOCK_NAME, task)
  }
  return task()
}

let refreshPromise: Promise<boolean> | null = null

export function refreshSession(force = false): Promise<boolean> {
  if (refreshPromise) {
    return refreshPromise
  }
  refreshPromise = withRefreshLock(async () => {
    if (!force && accessToken.value) {
      return true
    }
    const csrfToken = readCookie(CSRF_COOKIE)
    if (!csrfToken) {
      clearLocal()
      return false
    }
    try {
      const { data } = await axios.post<ApiEnvelope<RefreshResponse>>(
        `${AUTH_BASE}/refresh`,
        null,
        {
          headers: {
            'X-Client-App': CLIENT_APP,
            'X-CSRF-Token': csrfToken
          },
          withCredentials: true,
          timeout: 5000
        }
      )
      if (data.code !== 'OK') {
        clearLocal()
        return false
      }
      accessToken.value = data.data.accessToken
      const loaded = await loadMe()
      if (!loaded) {
        clearLocal()
        return false
      }
      broadcast({ type: 'TOKEN', token: accessToken.value, me: plainMe(me.value!) })
      return true
    } catch {
      clearLocal()
      return false
    }
  }).finally(() => {
    refreshPromise = null
  })
  return refreshPromise
}

export async function bootstrapSession(): Promise<boolean> {
  if (accessToken.value) {
    return true
  }
  return refreshSession(false)
}

export async function login(username: string, password: string): Promise<void> {
  const { data } = await axios.post<ApiEnvelope<LoginResponse>>(
    `${AUTH_BASE}/login`,
    { username, password, clientApp: CLIENT_APP },
    { withCredentials: true, timeout: 5000 }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '登录失败')
  }
  accessToken.value = data.data.accessToken
  me.value = toMe(data.data.user)
  await loadMe()
  if (me.value) {
    broadcast({ type: 'TOKEN', token: accessToken.value, me: plainMe(me.value) })
  }
}

export async function register(username: string, password: string, nickname: string): Promise<void> {
  const { data } = await axios.post<ApiEnvelope<{ userId: string }>>(
    `${AUTH_BASE}/register`,
    { username, password, nickname },
    { timeout: 5000 }
  )
  if (data.code !== 'OK') {
    throw new Error(data.message || '注册失败')
  }
  await login(username, password)
}

export async function logout(): Promise<void> {
  const csrfToken = readCookie(CSRF_COOKIE)
  try {
    if (csrfToken) {
      await axios.post(
        `${AUTH_BASE}/logout`,
        null,
        {
          headers: {
            'X-Client-App': CLIENT_APP,
            'X-CSRF-Token': csrfToken
          },
          withCredentials: true,
          timeout: 5000
        }
      )
    }
  } catch {
    // Local session is cleared even if the server call fails.
  } finally {
    clearLocal()
    broadcast({ type: 'CLEAR' })
    notifyCleared()
  }
}

export async function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  await axios.put(
    '/api/v1/me/password',
    { oldPassword, newPassword },
    {
      headers: { Authorization: `Bearer ${accessToken.value}` },
      withCredentials: true,
      timeout: 5000
    }
  )
  clearLocal()
  broadcast({ type: 'CLEAR' })
  notifyCleared()
}

export async function reloadMe(): Promise<void> {
  await loadMe()
}

export function applyMe(updated: MeResponse): void {
  me.value = updated
  if (accessToken.value) {
    broadcast({ type: 'TOKEN', token: accessToken.value, me: plainMe(updated) })
  }
}

export function describeAuthError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiEnvelope<unknown> | undefined
    if (body?.message) {
      return body.message
    }
    if (error.response?.status === 403) {
      return '当前账号无权执行该操作。'
    }
    if (error.response?.status === 401) {
      return '用户名或密码错误，或登录已失效。'
    }
    if (error.code === 'ECONNABORTED') {
      return '请求超时，请稍后重试。'
    }
    if (!error.response) {
      return '无法连接服务，请确认本地服务已启动。'
    }
  }
  return error instanceof Error ? error.message : '操作失败，请稍后重试。'
}