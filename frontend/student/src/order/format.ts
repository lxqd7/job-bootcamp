export const orderStateLabels: Record<string, string> = {
  CREATING: '创建中',
  WAIT_PAY: '待支付',
  PAID: '已支付',
  CLOSING: '关闭中',
  CLOSED: '已关闭',
  CREATE_FAILED: '创建失败',
  RECONCILING: '对账中'
}

export const fulfillmentLabels: Record<string, string> = {
  PENDING: '待开通',
  RUNNING: '开通中',
  DONE: '已开通',
  ATTENTION: '需关注'
}

export function orderStateLabel(state: string): string {
  return orderStateLabels[state] ?? state
}

export function fulfillmentLabel(state: string): string {
  return fulfillmentLabels[state] ?? state
}

export function formatCent(value: number): string {
  return `¥${(value / 100).toFixed(2)}`
}

export function formatDateTime(value: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

export function remainingSeconds(expiresAt: string, nowMs = Date.now()): number {
  const expires = new Date(expiresAt.endsWith('Z') ? expiresAt : `${expiresAt}Z`).getTime()
  return Math.max(0, Math.floor((expires - nowMs) / 1000))
}

export function formatCountdown(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}