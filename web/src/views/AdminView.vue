<script setup>
// 管理后台：按标签维护用户、会员档位、积分商品、订单、审计日志与死信队列
import { onMounted, ref } from 'vue'
import { api } from '../api'
import AccountShell from '../components/AccountShell.vue'
import { toast } from '../store/canvas'
import { CHANNEL_TEXT, PLAN_TEXT, formatCents, formatDate, orderStatus } from '../utils/format'

const tabs = [
  { key: 'overview', label: '概览' },
  { key: 'users', label: '用户' },
  { key: 'plans', label: '会员档位' },
  { key: 'packages', label: '积分商品' },
  { key: 'orders', label: '订单' },
  { key: 'audit', label: '审计日志' },
  { key: 'dead', label: '死信队列' }
]

const tab = ref('overview')
const loading = ref(true)
const overview = ref({})
const users = ref([])
const userKeyword = ref('')
const plans = ref([])
const packages = ref([])
const orders = ref([])
const audit = ref([])
const deadEvents = ref([])

const savingId = ref(null)
const editingUser = ref(null)
const userError = ref('')

const newPackage = ref({ code: '', name: '', credits: 100, priceCents: 1000, enabled: true, sortOrder: 0 })
const creatingPackage = ref(false)

// 各标签数据独立加载：单个接口失败只提示错误，不阻塞其他管理面板
function firstError(results) {
  const failed = results.find((item) => item.status === 'rejected')
  return failed ? failed.reason?.message : ''
}

async function loadUsers() {
  try {
    users.value = await api.adminUsers(userKeyword.value.trim(), 50)
  } catch (e) {
    toast(`加载用户失败：${e.message}`)
  }
}

async function loadPlans() {
  try {
    plans.value = (await api.adminPlans()).map((plan) => ({ ...plan }))
  } catch (e) {
    toast(`加载会员档位失败：${e.message}`)
  }
}

async function loadPackages() {
  try {
    packages.value = (await api.adminPackages()).map((item) => ({ ...item }))
  } catch (e) {
    toast(`加载积分商品失败：${e.message}`)
  }
}

async function loadOrders() {
  try {
    orders.value = await api.adminOrders(50)
  } catch (e) {
    toast(`加载订单失败：${e.message}`)
  }
}

async function loadAudit() {
  try {
    audit.value = await api.adminAudit(100)
  } catch (e) {
    toast(`加载审计日志失败：${e.message}`)
  }
}

async function loadDead() {
  try {
    deadEvents.value = await api.adminDeadEvents(50)
  } catch (e) {
    toast(`加载死信失败：${e.message}`)
  }
}

async function loadOverview() {
  try {
    overview.value = await api.adminOverview()
  } catch (e) {
    toast(`加载概览失败：${e.message}`)
  }
}

async function loadAll() {
  loading.value = true
  const results = await Promise.allSettled([
    loadOverview(),
    loadUsers(),
    loadPlans(),
    loadPackages(),
    loadOrders(),
    loadAudit(),
    loadDead()
  ])
  const message = firstError(results)
  if (message) toast(`管理数据加载失败：${message}`)
  loading.value = false
}

// 用户编辑：只有选择了会员档位才一并提交开通时长，避免误改现有会员
function openUserEditor(user) {
  userError.value = ''
  editingUser.value = {
    id: user.id,
    username: user.username,
    status: user.status || 'ACTIVE',
    role: user.role || 'USER',
    memberCode: '',
    memberDays: 30
  }
}

async function saveUser() {
  if (!editingUser.value) return
  savingId.value = editingUser.value.id
  userError.value = ''
  try {
    const body = {
      status: editingUser.value.status,
      role: editingUser.value.role
    }
    if (editingUser.value.memberCode) {
      body.memberCode = editingUser.value.memberCode
      body.memberDays = Number(editingUser.value.memberDays) || undefined
    }
    await api.adminUpdateUser(editingUser.value.id, body)
    toast('用户信息已更新')
    editingUser.value = null
    await Promise.all([loadUsers(), loadOverview()])
  } catch (e) {
    userError.value = e.message
  } finally {
    savingId.value = null
  }
}

async function savePlan(plan) {
  savingId.value = `plan-${plan.code}`
  try {
    const updated = await api.adminUpdatePlan(plan.code, {
      name: plan.name,
      description: plan.description,
      priceCents: Number(plan.priceCents) || 0,
      durationDays: Number(plan.durationDays) || 30,
      dailyCredits: Number(plan.dailyCredits) || 0,
      maxConcurrent: Number(plan.maxConcurrent) || 1,
      maxResolution: plan.maxResolution,
      dispatchWeight: Number(plan.dispatchWeight) || 1,
      enabled: !!plan.enabled,
      sortOrder: Number(plan.sortOrder) || 0
    })
    Object.assign(plan, updated)
    toast(`已保存会员档位「${plan.name}」`)
  } catch (e) {
    toast(`保存失败：${e.message}`)
  } finally {
    savingId.value = null
  }
}

async function createPackage() {
  if (creatingPackage.value) return
  if (!newPackage.value.code.trim()) {
    toast('请填写商品编码')
    return
  }
  creatingPackage.value = true
  try {
    await api.adminCreatePackage({
      code: newPackage.value.code.trim(),
      name: newPackage.value.name.trim() || newPackage.value.code.trim(),
      credits: Number(newPackage.value.credits) || 0,
      priceCents: Number(newPackage.value.priceCents) || 0,
      enabled: !!newPackage.value.enabled,
      sortOrder: Number(newPackage.value.sortOrder) || 0
    })
    newPackage.value = { code: '', name: '', credits: 100, priceCents: 1000, enabled: true, sortOrder: 0 }
    await loadPackages()
    toast('积分商品已创建')
  } catch (e) {
    toast(`创建失败：${e.message}`)
  } finally {
    creatingPackage.value = false
  }
}

async function savePackage(item) {
  savingId.value = `pkg-${item.id}`
  try {
    const updated = await api.adminUpdatePackage(item.id, {
      code: item.code,
      name: item.name,
      credits: Number(item.credits) || 0,
      priceCents: Number(item.priceCents) || 0,
      enabled: !!item.enabled,
      sortOrder: Number(item.sortOrder) || 0
    })
    Object.assign(item, updated)
    toast('积分商品已保存')
  } catch (e) {
    toast(`保存失败：${e.message}`)
  } finally {
    savingId.value = null
  }
}

async function deletePackage(item) {
  if (!window.confirm(`确定删除积分商品「${item.name || item.code}」？`)) return
  savingId.value = `pkg-${item.id}`
  try {
    await api.adminDeletePackage(item.id)
    await loadPackages()
    toast('积分商品已删除')
  } catch (e) {
    toast(`删除失败：${e.message}`)
  } finally {
    savingId.value = null
  }
}

async function retryDead(item) {
  savingId.value = `outbox-${item.id}`
  try {
    await api.adminRetryOutbox(item.id)
    await Promise.all([loadDead(), loadOverview()])
    toast('事件已重新入队')
  } catch (e) {
    toast(`重试失败：${e.message}`)
  } finally {
    savingId.value = null
  }
}

onMounted(loadAll)
</script>

<template>
  <AccountShell subtitle="管理后台">
    <div class="page-header">
      <div>
        <h1 class="page-title">管理后台</h1>
        <p class="page-sub">管理用户、会员档位、积分商品、订单与系统事件。</p>
      </div>
      <button class="btn ghost" :disabled="loading" @click="loadAll">刷新数据</button>
    </div>

    <div class="admin-tabs">
      <button
        v-for="item in tabs"
        :key="item.key"
        class="tab"
        :class="{ active: tab === item.key }"
        @click="tab = item.key"
      >{{ item.label }}</button>
    </div>

    <div v-if="loading" class="loading">正在加载管理数据…</div>

    <template v-else>
      <template v-if="tab === 'overview'">
        <div class="stat-grid">
          <div class="stat">
            <div class="stat-label">用户总数</div>
            <div class="stat-value">{{ overview.users ?? '-' }}</div>
          </div>
          <div class="stat">
            <div class="stat-label">最近订单</div>
            <div class="stat-value">{{ overview.orders ?? '-' }}</div>
            <div class="stat-hint">统计最近 50 条</div>
          </div>
          <div class="stat">
            <div class="stat-label">死信事件</div>
            <div class="stat-value">{{ overview.deadEvents ?? '-' }}</div>
            <div class="stat-hint">需要在死信队列中处理</div>
          </div>
        </div>
      </template>

      <template v-else-if="tab === 'users'">
        <div class="section-head">
          <div class="row">
            <input
              v-model.trim="userKeyword"
              class="search-input"
              placeholder="按用户名或邮箱搜索"
              @keyup.enter="loadUsers"
            />
            <button class="btn" @click="loadUsers">搜索</button>
          </div>
          <span class="muted">共 {{ users.length }} 个</span>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>邮箱</th>
                <th>角色</th>
                <th>状态</th>
                <th>邮箱验证</th>
                <th>会员</th>
                <th>会员到期</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>{{ user.id }}</td>
                <td>{{ user.username }}</td>
                <td>{{ user.email }}</td>
                <td>{{ user.role === 'ADMIN' ? '管理员' : '用户' }}</td>
                <td>
                  <span class="pill" :class="user.status === 'ACTIVE' ? 'tone-done' : 'tone-error'">
                    {{ user.status === 'ACTIVE' ? '启用' : '停用' }}
                  </span>
                </td>
                <td>{{ user.emailVerified ? '已验证' : '未验证' }}</td>
                <td>{{ PLAN_TEXT[user.memberCode] || user.memberCode || '-' }}</td>
                <td>{{ user.memberExpiresAt ? formatDate(user.memberExpiresAt) : '-' }}</td>
                <td>
                  <button class="btn ghost sm" @click="openUserEditor(user)">编辑</button>
                </td>
              </tr>
              <tr v-if="!users.length"><td colspan="9" class="empty">没有匹配的用户</td></tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else-if="tab === 'plans'">
        <div class="card-grid">
          <article v-for="plan in plans" :key="plan.code" class="card plan-card">
            <header>
              <div class="row">
                <h2>{{ plan.name }}</h2>
                <span class="pill tone-info">{{ plan.code }}</span>
              </div>
              <label class="row muted">
                <input v-model="plan.enabled" type="checkbox" /> 启用
              </label>
            </header>
            <div class="plan-edit">
              <label class="field"><span class="field-label">名称</span><input v-model.trim="plan.name" /></label>
              <label class="field"><span class="field-label">价格（分）</span><input v-model.number="plan.priceCents" type="number" /></label>
              <label class="field"><span class="field-label">周期（天）</span><input v-model.number="plan.durationDays" type="number" /></label>
              <label class="field"><span class="field-label">每日积分</span><input v-model.number="plan.dailyCredits" type="number" /></label>
              <label class="field"><span class="field-label">最大并发</span><input v-model.number="plan.maxConcurrent" type="number" /></label>
              <label class="field"><span class="field-label">最高分辨率</span><input v-model.trim="plan.maxResolution" /></label>
              <label class="field"><span class="field-label">调度权重</span><input v-model.number="plan.dispatchWeight" type="number" /></label>
              <label class="field"><span class="field-label">排序</span><input v-model.number="plan.sortOrder" type="number" /></label>
              <label class="field"><span class="field-label">描述</span><input v-model.trim="plan.description" /></label>
            </div>
            <button class="btn primary sm" :disabled="savingId === `plan-${plan.code}`" @click="savePlan(plan)">
              {{ savingId === `plan-${plan.code}` ? '保存中…' : '保存档位' }}
            </button>
          </article>
        </div>
      </template>

      <template v-else-if="tab === 'packages'">
        <div class="card">
          <div class="section-head"><h2>新增积分商品</h2></div>
          <form class="form-grid" @submit.prevent="createPackage">
            <label class="field"><span class="field-label">编码</span><input v-model.trim="newPackage.code" placeholder="如 credits_100" required /></label>
            <label class="field"><span class="field-label">名称</span><input v-model.trim="newPackage.name" placeholder="100 积分包" /></label>
            <label class="field"><span class="field-label">积分数量</span><input v-model.number="newPackage.credits" type="number" /></label>
            <label class="field"><span class="field-label">价格（分）</span><input v-model.number="newPackage.priceCents" type="number" /></label>
            <label class="field"><span class="field-label">排序</span><input v-model.number="newPackage.sortOrder" type="number" /></label>
            <label class="row"><input v-model="newPackage.enabled" type="checkbox" /> 启用</label>
            <div class="form-actions" style="margin: 0">
              <button class="btn primary" type="submit" :disabled="creatingPackage">
                {{ creatingPackage ? '创建中…' : '创建商品' }}
              </button>
            </div>
          </form>
        </div>

        <div class="table-wrap" style="margin-top: 14px">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>编码</th>
                <th>名称</th>
                <th>积分</th>
                <th>价格（分）</th>
                <th>排序</th>
                <th>启用</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in packages" :key="item.id">
                <td>{{ item.id }}</td>
                <td><input v-model.trim="item.code" class="table-input" /></td>
                <td><input v-model.trim="item.name" class="table-input" /></td>
                <td><input v-model.number="item.credits" class="table-input" type="number" /></td>
                <td><input v-model.number="item.priceCents" class="table-input" type="number" /></td>
                <td><input v-model.number="item.sortOrder" class="table-input" type="number" /></td>
                <td><input v-model="item.enabled" type="checkbox" /></td>
                <td>
                  <div class="inline-actions">
                    <button class="btn ghost sm" :disabled="savingId === `pkg-${item.id}`" @click="savePackage(item)">保存</button>
                    <button class="btn danger sm" :disabled="savingId === `pkg-${item.id}`" @click="deletePackage(item)">删除</button>
                  </div>
                </td>
              </tr>
              <tr v-if="!packages.length"><td colspan="8" class="empty">暂无积分商品</td></tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else-if="tab === 'orders'">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>订单号</th>
                <th>用户 ID</th>
                <th>类型</th>
                <th>商品</th>
                <th class="num">金额</th>
                <th>渠道</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>支付时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in orders" :key="item.orderNo">
                <td class="mono">{{ item.orderNo }}</td>
                <td>{{ item.userId }}</td>
                <td>{{ item.productType === 'MEMBERSHIP' ? '会员' : '积分' }}</td>
                <td>{{ item.productCode }}</td>
                <td class="num">{{ formatCents(item.amountCents) }}</td>
                <td>{{ CHANNEL_TEXT[item.channel] || item.channel }}</td>
                <td>
                  <span class="pill" :class="`tone-${orderStatus(item.status).tone}`">
                    {{ orderStatus(item.status).text }}
                  </span>
                </td>
                <td>{{ formatDate(item.createdAt) }}</td>
                <td>{{ item.paidAt ? formatDate(item.paidAt) : '-' }}</td>
              </tr>
              <tr v-if="!orders.length"><td colspan="9" class="empty">暂无订单</td></tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else-if="tab === 'audit'">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户 ID</th>
                <th>操作</th>
                <th>对象</th>
                <th>详情</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in audit" :key="item.id">
                <td>{{ item.id }}</td>
                <td>{{ item.userId ?? '-' }}</td>
                <td>{{ item.action }}</td>
                <td class="mono">{{ item.targetType }}{{ item.targetId ? `#${item.targetId}` : '' }}</td>
                <td class="wrap">{{ item.detail || '-' }}</td>
                <td>{{ formatDate(item.createdAt) }}</td>
              </tr>
              <tr v-if="!audit.length"><td colspan="6" class="empty">暂无审计记录</td></tr>
            </tbody>
          </table>
        </div>
      </template>

      <template v-else-if="tab === 'dead'">
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>事件类型</th>
                <th>聚合对象</th>
                <th>用户 ID</th>
                <th class="num">尝试次数</th>
                <th>最后错误</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in deadEvents" :key="item.id">
                <td>{{ item.id }}</td>
                <td>{{ item.eventType }}</td>
                <td class="mono">{{ item.aggregateType }}{{ item.aggregateId ? `#${item.aggregateId}` : '' }}</td>
                <td>{{ item.userId ?? '-' }}</td>
                <td class="num">{{ item.attempts ?? 0 }}</td>
                <td class="wrap">{{ item.lastError || '-' }}</td>
                <td>{{ formatDate(item.createdAt) }}</td>
                <td>
                  <button class="btn ghost sm" :disabled="savingId === `outbox-${item.id}`" @click="retryDead(item)">
                    {{ savingId === `outbox-${item.id}` ? '重试中…' : '重试' }}
                  </button>
                </td>
              </tr>
              <tr v-if="!deadEvents.length"><td colspan="8" class="empty">没有死信事件</td></tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>

    <div v-if="editingUser" class="overlay" @click.self="editingUser = null">
      <div class="dialog" role="dialog" aria-modal="true">
        <header class="dialog-head">
          <div>
            <h2>编辑用户</h2>
            <p class="muted">{{ editingUser.username }} · ID {{ editingUser.id }}</p>
          </div>
          <button class="icon-btn" aria-label="关闭" @click="editingUser = null">×</button>
        </header>
        <div class="dialog-body">
          <div class="form-grid">
            <label class="field">
              <span class="field-label">账号状态</span>
              <select v-model="editingUser.status">
                <option value="ACTIVE">启用</option>
                <option value="DISABLED">停用</option>
              </select>
            </label>
            <label class="field">
              <span class="field-label">角色</span>
              <select v-model="editingUser.role">
                <option value="USER">普通用户</option>
                <option value="ADMIN">管理员</option>
              </select>
            </label>
            <label class="field">
              <span class="field-label">开通会员档位</span>
              <select v-model="editingUser.memberCode">
                <option value="">不修改</option>
                <option v-for="plan in plans" :key="plan.code" :value="plan.code">
                  {{ plan.name }}（{{ plan.code }}）
                </option>
              </select>
            </label>
            <label class="field">
              <span class="field-label">会员天数</span>
              <input v-model.number="editingUser.memberDays" type="number" min="1" :disabled="!editingUser.memberCode" />
            </label>
          </div>
          <p v-if="userError" class="auth-error" style="margin-top: 12px">{{ userError }}</p>
        </div>
        <footer class="dialog-foot">
          <button class="btn ghost" @click="editingUser = null">取消</button>
          <button class="btn primary" :disabled="savingId === editingUser.id" @click="saveUser">
            {{ savingId === editingUser.id ? '保存中…' : '保存' }}
          </button>
        </footer>
      </div>
    </div>
  </AccountShell>
</template>
