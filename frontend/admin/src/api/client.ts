import axios from 'axios'
import { http } from './http'

export async function loadHealth(): Promise<{ status: string }> {
  const { data } = await http.get<{ status: string }>('/actuator/health')
  return data
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