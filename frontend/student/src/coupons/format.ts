import type { CouponRuleView } from '../api/types'
import { formatCent } from '../order/format'

export function describeCouponRule(rule: CouponRuleView | null): string {
  if (!rule) return '规则不可用'
  if (rule.type === 'DISCOUNT') {
    const rate = ((rule.payRateBps ?? 10000) / 1000).toFixed(1)
    return `折扣券：${rate} 折，最高优惠 ${formatCent(rule.maxDiscountCent ?? 0)}`
  }
  if (rule.type === 'NO_THRESHOLD') {
    return `无门槛券：减 ${formatCent(rule.amountCent ?? 0)}`
  }
  return `满 ${formatCent(rule.thresholdCent ?? 0)} 减 ${formatCent(rule.amountCent ?? 0)}`
}

export function describeCouponScope(rule: CouponRuleView | null): string {
  if (!rule) return ''
  switch (rule.scope) {
    case 'ALL':
      return '全场'
    case 'COURSE':
      return `课程 ${rule.targetId ?? ''}`
    case 'CATEGORY':
      return `分类 ${rule.targetId ?? ''}`
    case 'ROUTE':
      return `路线 ${rule.targetId ?? ''}`
    default:
      return rule.scope
  }
}

export function describeCouponValidity(rule: CouponRuleView | null): string {
  if (!rule) return ''
  if (rule.validityType === 'DAYS') {
    return `取得后 ${rule.validDays ?? 0} 天内有效`
  }
  return `${formatTimestamp(rule.validFrom)} 至 ${formatTimestamp(rule.validUntil)}`
}

export function formatTimestamp(value: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

export const couponStatusLabels: Record<string, string> = {
  AVAILABLE: '可用',
  LOCKED: '已锁定',
  USED: '已使用',
  EXPIRED: '已过期',
  NOT_STARTED: '未生效'
}