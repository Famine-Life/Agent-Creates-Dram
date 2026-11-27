<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api'
import AccountShell from '../components/AccountShell.vue'
import { authState, refreshAccount, resendVerification } from '../store/auth'
import { toast } from '../store/canvas'
import {
  CHANNEL_TEXT,
  LEDGER_STATUS,
  LEDGER_TYPE,
  PLAN_TEXT,
  formatCents,
  formatDate,
  orderStatus
} from '../utils/format'

const ledgers = ref([])
const orders = ref([])
const loading = ref(true)
const resending = ref(false)
const verifyMessage = ref('')
const devLink = ref('')

const user = computed(() => authState.user || {})
const planName = computed(() => user.value.memberName || PLAN_TEXT[user.value.memberCode] || user.value.memberCode || '-')

const verifyRoute = computed(() => {
  if (!devLink.value) return null
  try {
    const token = new URL(devLink.value).searchParams.get('token')
    return token ? { name: 'verify-email', query: { token } } : null
  } catch (e) {
    return null
  }
})

async function load() {
  loading.value = true
  try {
    const [ledgerList, orderList] = await Promise.all([api.ledgers(50), api.paymentOrders(20)])
    ledgers.value = Array.isArray(ledgerList) ? ledgerList : []
    orders.value = Array.isArray(orderList) ? orderList : []
    await refreshAccount()
  } catch (e) {
    toast(`加载账户失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

async function resend() {
  if (resending.value || !user.value.email) return
  resending.value = true
  verifyMessage.value = ''
  devLink.value = ''
  try {
    const result = await resendVerification(user.value.email)
    devLink.value = result?.devVerifyLink || ''
    verifyMessage.value = '验证邮件已发送，请查收邮箱并完成验证。'
  } catch (e) {
    verifyMessage.value = `发送失败：${e.message}`
  } finally {
    resending.value = false
  }
}

onMounted(load)
</script>

<template>
  <AccountShell subtitle="我的账户">
    <div class="page-header">
      <div>
        <h1 class="page-title">我的账户</h1>
        <p class="page-sub">查看会员权益、积分余额与最近的使用记录。</p>
      </div>
      <router-link class="btn primary" to="/account/billing">充值 / 升级会员</router-link>
    </div>

    <div v-if="!user.emailVerified" class="banner warn">
      <span>邮箱尚未验证，生成内容与充值前请先完成邮箱验证。</span>
      <button class="btn ghost sm" :disabled="resending" @click="resend">
        {{ resending ? '发送中…' : '重新发送验证邮件' }}
      </button>
    </div>
    <div v-if="verifyMessage" class="banner" :class="devLink ? 'done' : 'info'">
      <span>
        {{ verifyMessage }}
        <router-link v-if="verifyRoute" :to="verifyRoute">打开验证链接</router-link>
      </span>
    </div>

    <div v-if="loading" class="loading">正在加载账户信息…</div>

    <template v-else>
      <section class="stat-grid" style="margin-top: 16px">
        <div class="stat">
          <div class="stat-label">可用积分</div>
          <div class="stat-value">{{ user.availableCredits ?? 0 }}</div>
          <div class="stat-hint">可用于生成视频与图片</div>
        </div>
        <div class="stat">
          <div class="stat-label">冻结积分</div>
          <div class="stat-value">{{ user.frozenCredits ?? 0 }}</div>
          <div class="stat-hint">生成中的任务预扣</div>
        </div>
        <div class="stat">
          <div class="stat-label">今日消耗</div>
          <div class="stat-value">{{ user.todayCredits ?? 0 }}</div>
          <div class="stat-hint">每日额度 {{ user.dailyCredits ?? 0 }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">今日调用</div>
          <div class="stat-value">{{ user.todayCalls ?? 0 }}<small> 次</small></div>
          <div class="stat-hint">并发上限 {{ user.maxConcurrent ?? 1 }}</div>
        </div>
      </section>

      <section class="card-grid" style="margin-top: 16px">
        <div class="card">
          <div class="section-head"><h2>账户资料</h2></div>
          <dl>
            <div class="kv"><dt>用户名</dt><dd>{{ user.username || '-' }}</dd></div>
            <div class="kv"><dt>邮箱</dt><dd>{{ user.email || '-' }}</dd></div>
            <div class="kv">
              <dt>邮箱验证</dt>
              <dd>
                <span class="pill" :class="user.emailVerified ? 'tone-done' : 'tone-error'">
                  {{ user.emailVerified ? '已验证' : '未验证' }}
                </span>
              </dd>
            </div>
            <div class="kv"><dt>角色</dt><dd>{{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}</dd></div>
          </dl>
        </div>

        <div class="card">
          <div class="section-head"><h2>会员权益</h2></div>
          <dl>
            <div class="kv"><dt>当前档位</dt><dd>{{ planName }}</dd></div>
            <div class="kv"><dt>到期时间</dt><dd>{{ user.memberExpiresAt ? formatDate(user.memberExpiresAt) : '长期有效' }}</dd></div>
            <div class="kv"><dt>每日积分</dt><dd>{{ user.dailyCredits ?? 0 }}</dd></div>
            <div class="kv"><dt>最大并发</dt><dd>{{ user.maxConcurrent ?? 1 }}</dd></div>
            <div class="kv"><dt>最高分辨率</dt><dd>{{ user.maxResolution || '-' }}</dd></div>
          </dl>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <h2>积分流水</h2>
          <span class="muted">最近 50 条</span>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>类型</th>
                <th class="num">变动</th>
                <th class="num">可用余额</th>
                <th class="num">冻结</th>
                <th>状态</th>
                <th>备注</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in ledgers" :key="row.id">
                <td>{{ formatDate(row.createdAt) }}</td>
                <td>{{ LEDGER_TYPE[row.type] || row.type }}</td>
                <td class="num" :style="{ color: (row.amount || 0) < 0 ? 'var(--danger-text)' : 'var(--done)' }">
                  {{ (row.amount || 0) > 0 ? '+' : '' }}{{ row.amount ?? 0 }}
                </td>
                <td class="num">{{ row.availableAfter ?? '-' }}</td>
                <td class="num">{{ row.frozenAfter ?? '-' }}</td>
                <td>{{ LEDGER_STATUS[row.status] || row.status || '-' }}</td>
                <td class="wrap">{{ row.note || '-' }}</td>
              </tr>
              <tr v-if="!ledgers.length"><td colspan="7" class="empty">暂无积分流水</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="section">
        <div class="section-head">
          <h2>订单记录</h2>
          <router-link class="muted" to="/account/billing">去充值 →</router-link>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>订单号</th>
                <th>商品</th>
                <th class="num">金额</th>
                <th>渠道</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>支付时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in orders" :key="row.orderNo">
                <td class="mono">{{ row.orderNo }}</td>
                <td>{{ row.productType === 'MEMBERSHIP' ? '会员' : '积分' }} · {{ row.productCode }}</td>
                <td class="num">{{ formatCents(row.amountCents) }}</td>
                <td>{{ CHANNEL_TEXT[row.channel] || row.channel }}</td>
                <td>
                  <span class="pill" :class="`tone-${orderStatus(row.status).tone}`">
                    {{ orderStatus(row.status).text }}
                  </span>
                </td>
                <td>{{ formatDate(row.createdAt) }}</td>
                <td>{{ row.paidAt ? formatDate(row.paidAt) : '-' }}</td>
              </tr>
              <tr v-if="!orders.length"><td colspan="7" class="empty">暂无订单</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </AccountShell>
</template>
