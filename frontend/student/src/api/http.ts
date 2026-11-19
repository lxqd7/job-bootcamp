import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiEnvelope } from './types'

let tokenProvider: () => string = () => ''
let unauthorizedHandler: (() => Promise<boolean>) | null = null

export const http = axios.create({
  baseURL: '',
  withCredentials: true,
  timeout: 5000
})

export function setTokenProvider(provider: () => string): void {
  tokenProvider = provider
}

export function setUnauthorizedHandler(handler: () => Promise<boolean>): void {
  unauthorizedHandler = handler
}

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenProvider()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  response => response,
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const status = error.response?.status
    const url = original?.url ?? ''
    const isAuthCall = url.includes('/api/v1/auth/')
    if (status === 401 && original && !original._retried && !isAuthCall && unauthorizedHandler) {
      original._retried = true
      const refreshed = await unauthorizedHandler()
      if (refreshed) {
        return http(original)
      }
    }
    return Promise.reject(error)
  }
)