<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { applyRefund, describeTradeError, loadOrder } from '../api/client'
import type { OrderResponse } from '../api/types'
import { formatCent } from '../order/format'

const route = useRoute()
const router = useRouter()
const order = ref<OrderResponse | null>(null)
const reason = ref('')
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref('')

const orderId = computed(() => String(route.params.id))
const canApply = computed(() => Boolean(order.value && order.value.state === 'PAID'))

async function load(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    order.value = await loadOrder(orderId.value)
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    loading.value = false
  }
}

async function submit(): Promise<void> {
  if (!order.value || !canApply.value || submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    const intent = await applyRefund(order.value.id, reason.value.trim() || undefined)
    await router.push({ name: 'refund-application', params: { requestId: intent.requestId } })
  } catch (error) {
    errorMessage.value = describeTradeError(error)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <AppShell>
    <p class="back"><RouterLink :to="`/orders/${orderId}`">← 返回订单详情</RouterLink></p>
    <p v-if="loading" class="empty">正在读取订单…</p>
    <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>

    <template v-else-if="order">
      <section class="head">
        <p class="stage">退款申请</p>
        <h1>申请退款</h1>
        <p class="lead">
          退款只退订单实际支付金额；已用优惠券不退、不恢复，课程抵扣不换现金。
          受理时按购买时固定的小节清单计算进度，之后课程增删小节不影响本次申请。
        </p>
      </section>

      <section class="card">
        <div class="row"><span>订单</span><strong>{{ order.orderNo }}</strong></div>
        <div class="row"><span>实付金额</span><strong>{{ formatCent(order.pricing.payableCent) }}</strong></div>
        <div class="row"><span>订单状态</span><span>{{ order.state }}</span></div>
        <p v-if="!canApply" class="warning">只有已支付且已进入发权流程的订单可以申请退款。</p>
      </section>

      <label class="reason">
        <span>申请说明（可选）</span>
        <textarea v-model="reason" maxlength="200" rows="4" placeholder="例如：课程内容与预期不符" />
      </label>

      <section class="rules">
        <h2>受理规则</h2>
        <ul>
          <li>支付成功后 7×24 小时内、固定小节清单完成数不超过 20% 才可自动受理。</li>
          <li>若订单被其他路线用于抵扣，且该路线未退款，会提示先处理关联路线。</li>
          <li>同一订单最多一个进行中的退款申请；重复提交返回原申请。</li>
        </ul>
      </section>

      <div class="actions">
        <button class="btn btn-primary" type="button" :disabled="!canApply || submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交退款申请' }}
        </button>
      </div>
    </template>
  </AppShell>
</template>

<style scoped>
.back, .stage { margin: 0 0 12px; color: var(--muted); font-size: 13px; letter-spacing: .06em; }
h1 { margin: 0; font-family: var(--serif); font-size: clamp(32px, 5vw, 48px); font-weight: 500; }
.lead { max-width: 66ch; color: var(--muted); line-height: 1.8; }
.card { margin-top: 28px; padding: 16px 0; border-top: 2px solid var(--ink); border-bottom: 1px solid var(--line); }
.row { display: flex; justify-content: space-between; gap: 16px; padding: 8px 0; border-bottom: 1px solid var(--line); font-size: 14px; }
.row:last-child { border-bottom: 0; }
.reason { display: grid; gap: 8px; margin-top: 24px; font-size: 14px; }
.reason textarea { width: 100%; padding: 10px; border: 1px solid var(--line); background: transparent; font: inherit; color: var(--ink); resize: vertical; }
.rules { margin-top: 24px; }
.rules h2 { margin: 0 0 8px; font-family: var(--serif); font-size: 21px; font-weight: 500; }
.rules ul { margin: 0; padding-left: 20px; color: var(--muted); line-height: 1.8; font-size: 13px; }
.warning { margin: 12px 0 0; color: var(--amber); font-size: 13px; }
.actions { margin-top: 24px; }
.empty { color: var(--muted); }
</style>