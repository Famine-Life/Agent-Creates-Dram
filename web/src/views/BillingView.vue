<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../api'
import AccountShell from '../components/AccountShell.vue'
import { authState, emailVerified, refreshAccount } from '../store/auth'
import { toast } from '../store/canvas'
import { CHANNEL_TEXT, PLAN_TEXT, formatCents, formatDate, orderStatus } from '../utils/format'

const tab = ref('MEMBERSHIP')
const plans = ref([])
const packages = ref([])
const channels = ref({})
const channel = ref('')
const loading = ref(true)

const buying = ref(null)
const payment = ref(null)
const order = ref(null)
const payError = ref('')
const payBusy = ref(false)
const paidNotice = ref(false)
let pollTimer = null

const channelOptions = computed(() => {
  const list = []
  if (channels.value.mock) list.push({ value: 'mock', label: CHANNEL_TEXT.mock })
  if (channels.value.wechat) list.push({ value: 'wechat', label: CHANNEL_TEXT.wechat })
  if (channels.value.alipay) list.push({ value: 'alipay', label: CHANNEL_TEXT.alipay })
  if (!list.length && channels.value.default) {
    list.push({ value: channels.value.default, label: CHANNEL_TEXT[channels.value.default] || channels.value.default })
  }
  return list
})

const currentStatus = computed(() => orderStatus(order.value?.status || payment.value?.status))
const isPaid = computed(() => order.value?.status === 'PAID')
const isClosed = computed(() => order.value?.status === 'CLOSED')

async function load() {
  loading.value = true
  try {
    const [planList, packageList, channelConfig] = await Promise.all([
      api.plans(),
      api.packages(),
      api.paymentChannels()
    ])
    plans.value = Array.isArray(planList) ? planList : []
    packages.value = Array.isArray(packageList) ? packageList : []
    channels.value = channelConfig || {}
    const values = channelOptions.value.map((item) => item.value)
    channel.value = values.includes(channels.value.default) ? channels.value.default : (values[0] || '')
  } catch (e) {
    toast(`加载商品失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

async function buy(productType, item) {
  if (!emailVerified.value) {
    toast('请先完成邮箱验证后再充值')
    return
  }
  if (!channel.value) {
    toast('当前没有可用的支付渠道')
    return
  }
  buying.value = {
    productType,
    productCode: item.code,
    name: item.name,
    priceCents: item.priceCents
  }
  payment.value = null
  order.value = null
  payError.value = ''
  paidNotice.value = false
  payBusy.value = true
  try {
    payment.value = await api.createPaymentOrder({
      productType,
      productCode: item.code,
      channel: channel.value
    })
    order.value = { ...payment.value }
    startPolling()
  } catch (e) {
    payError.value = e.message
  } finally {
    payBusy.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(refreshOrder, 2500)
}

function stopPolling() {
  clearInterval(pollTimer)
  pollTimer = null
}

function applyOrder(latest) {
  if (!latest) return
  order.value = latest
  if (latest.status === 'PAID') {
    stopPolling()
    if (!paidNotice.value) {
      paidNotice.value = true
      toast('支付成功，权益已到账')
      refreshAccount().catch(() => {})
    }
  } else if (latest.status === 'CLOSED') {
    stopPolling()
  }
}

async function refreshOrder() {
  if (!payment.value?.orderNo) return
  try {
    applyOrder(await api.paymentOrder(payment.value.orderNo))
  } catch (e) {
    if (e.status === 404) stopPolling()
  }
}

async function mockPay() {
  if (payBusy.value || !payment.value?.orderNo) return
  payBusy.value = true
  try {
    applyOrder(await api.mockPay(payment.value.orderNo))
  } catch (e) {
    payError.value = e.message
  } finally {
    payBusy.value = false
  }
}

async function closeOrder() {
  if (payBusy.value || !payment.value?.orderNo) return
  payBusy.value = true
  try {
    applyOrder(await api.closeOrder(payment.value.orderNo))
  } catch (e) {
    payError.value = e.message
  } finally {
    payBusy.value = false
  }
}

function closeDialog() {
  stopPolling()
  buying.value = null
  payment.value = null
  order.value = null
  payError.value = ''
  paidNotice.value = false
}

onMounted(load)
onBeforeUnmount(stopPolling)
</script>

<template>
  <AccountShell subtitle="会员与充值">
    <div class="page-header">
      <div>
        <h1 class="page-title">会员与充值</h1>
        <p class="page-sub">升级会员获取每日积分与更高并发，或单独购买积分包。</p>
      </div>
      <router-link class="btn ghost" to="/account">查看账户</router-link>
    </div>

    <div v-if="authState.user && !authState.user.emailVerified" class="banner warn">
      <span>邮箱尚未验证，完成验证后才能下单充值。</span>
      <router-link class="btn ghost sm" to="/account">去验证邮箱</router-link>
    </div>

    <div class="section-head" style="margin-top: 18px">
      <div class="tabs">
        <button class="tab" :class="{ active: tab === 'MEMBERSHIP' }" @click="tab = 'MEMBERSHIP'">会员套餐</button>
        <button class="tab" :class="{ active: tab === 'CREDITS' }" @click="tab = 'CREDITS'">积分充值</button>
      </div>
      <label class="row">
        <span class="muted">支付渠道</span>
        <select v-model="channel" class="search-input" :disabled="!channelOptions.length">
          <option v-if="!channelOptions.length" value="">暂无可用渠道</option>
          <option v-for="item in channelOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </label>
    </div>

    <div v-if="loading" class="loading">正在加载商品…</div>

    <template v-else-if="tab === 'MEMBERSHIP'">
      <div v-if="!plans.length" class="card empty">暂无可购买的会员套餐。</div>
      <div v-else class="product-grid">
        <article
          v-for="plan in plans"
          :key="plan.code"
          class="product-card"
          :class="{ current: authState.user?.memberCode === plan.code }"
        >
          <div class="section-head">
            <h3>{{ plan.name }}</h3>
            <span v-if="authState.user?.memberCode === plan.code" class="pill tone-done">当前档位</span>
            <span v-else class="pill">{{ PLAN_TEXT[plan.code] || plan.code }}</span>
          </div>
          <div class="product-price">
            {{ formatCents(plan.priceCents) }}
            <small>/ {{ plan.durationDays || 30 }} 天</small>
          </div>
          <p class="muted">{{ plan.description || '解锁更高生成额度与并发能力。' }}</p>
          <ul class="product-features">
            <li>每日 {{ plan.dailyCredits ?? 0 }} 积分</li>
            <li>最大并发 {{ plan.maxConcurrent ?? 1 }}</li>
            <li>最高分辨率 {{ plan.maxResolution || '-' }}</li>
          </ul>
          <button
            class="btn primary"
            :disabled="!emailVerified || !channelOptions.length"
            :title="!emailVerified ? '请先完成邮箱验证' : ''"
            @click="buy('MEMBERSHIP', plan)"
          >
            {{ authState.user?.memberCode === plan.code ? '续费' : '购买 / 升级' }}
          </button>
        </article>
      </div>
    </template>

    <template v-else>
      <div v-if="!packages.length" class="card empty">暂无可购买的积分包。</div>
      <div v-else class="product-grid">
        <article v-for="item in packages" :key="item.code" class="product-card">
          <div class="section-head">
            <h3>{{ item.name }}</h3>
            <span class="pill tone-info">{{ item.credits }} 积分</span>
          </div>
          <div class="product-price">{{ formatCents(item.priceCents) }}</div>
          <p class="muted">积分到账后可直接用于视频与图片生成。</p>
          <button
            class="btn primary"
            :disabled="!emailVerified || !channelOptions.length"
            :title="!emailVerified ? '请先完成邮箱验证' : ''"
            @click="buy('CREDITS', item)"
          >
            立即充值
          </button>
        </article>
      </div>
    </template>

    <div v-if="buying" class="overlay" @click.self="closeDialog">
      <div class="dialog" role="dialog" aria-modal="true">
        <header class="dialog-head">
          <div>
            <h2>支付订单</h2>
            <p class="muted">{{ buying.name }} · {{ formatCents(buying.priceCents) }}</p>
          </div>
          <button class="icon-btn" aria-label="关闭" @click="closeDialog">×</button>
        </header>

        <div class="dialog-body">
          <div v-if="payBusy && !payment" class="loading">正在创建订单…</div>
          <p v-if="payError" class="auth-error">{{ payError }}</p>

          <template v-if="payment">
            <div class="pay-panel">
              <div class="pay-row"><span>订单号</span><strong class="mono">{{ payment.orderNo }}</strong></div>
              <div class="pay-row"><span>支付渠道</span><strong>{{ CHANNEL_TEXT[payment.channel] || payment.channel }}</strong></div>
              <div class="pay-row"><span>订单金额</span><strong>{{ formatCents(payment.amountCents) }}</strong></div>
              <div class="pay-row"><span>订单状态</span>
                <span class="pill" :class="`tone-${currentStatus.tone}`">{{ currentStatus.text }}</span>
              </div>
              <div v-if="payment.expiresAt" class="pay-row">
                <span>过期时间</span><strong>{{ formatDate(payment.expiresAt) }}</strong>
              </div>

              <div v-if="isPaid" class="banner done">支付成功，权益已到账。</div>
              <div v-else-if="isClosed" class="banner err">订单已关闭，请重新下单。</div>

              <template v-else>
                <div v-if="payment.mock" class="banner info">
                  <span>当前为模拟支付环境，点击下方按钮即可完成支付。</span>
                  <button class="btn primary sm" :disabled="payBusy" @click="mockPay">
                    {{ payBusy ? '处理中…' : '模拟支付' }}
                  </button>
                </div>

                <template v-if="payment.codeUrl">
                  <div class="pay-qr">二维码占位<br />请使用 {{ CHANNEL_TEXT[payment.channel] || payment.channel }} 扫描</div>
                  <p class="pay-code">
                    支付链接：
                    <a :href="payment.codeUrl" target="_blank" rel="noopener">{{ payment.codeUrl }}</a>
                  </p>
                </template>

                <template v-if="payment.formHtml">
                  <p class="muted">支付表单（如未自动跳转，请在表单中手动确认支付）：</p>
                  <div class="pay-html-preview" v-html="payment.formHtml"></div>
                </template>

                <p class="muted">订单支付结果每 2.5 秒自动刷新，也可稍后在「我的账户」查看订单状态。</p>
              </template>
            </div>
          </template>
        </div>

        <footer class="dialog-foot">
          <button v-if="payment && !isPaid && !isClosed" class="btn ghost" :disabled="payBusy" @click="closeOrder">关闭订单</button>
          <button class="btn" @click="closeDialog">完成</button>
        </footer>
      </div>
    </div>
  </AccountShell>
</template>
