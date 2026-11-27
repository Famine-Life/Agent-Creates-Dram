<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import AccountShell from '../components/AccountShell.vue'
import { copyText, toast } from '../store/canvas'
import { formatDate } from '../utils/format'

const tokens = ref([])
const loading = ref(true)
const creating = ref(false)
const revoking = ref(null)
const form = ref({ name: '', expiresInDays: 30 })
const created = ref(null)

async function load() {
  loading.value = true
  try {
    tokens.value = await api.mcpTokens()
  } catch (e) {
    toast(`加载 Token 失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

async function create() {
  if (creating.value) return
  creating.value = true
  try {
    const result = await api.createMcpToken({
      name: form.value.name.trim() || '默认 Token',
      expiresInDays: Number(form.value.expiresInDays) || 0
    })
    created.value = result
    form.value.name = ''
    await load()
  } catch (e) {
    toast(`创建 Token 失败：${e.message}`)
  } finally {
    creating.value = false
  }
}

async function revoke(item) {
  if (!window.confirm(`确定撤销 Token「${item.name}」？撤销后使用该 Token 的客户端将立即失效。`)) return
  revoking.value = item.id
  try {
    await api.revokeMcpToken(item.id)
    await load()
    toast('Token 已撤销')
  } catch (e) {
    toast(`撤销失败：${e.message}`)
  } finally {
    revoking.value = null
  }
}

onMounted(load)
</script>

<template>
  <AccountShell subtitle="MCP Token">
    <div class="page-header">
      <div>
        <h1 class="page-title">MCP Token</h1>
        <p class="page-sub">为 AI 客户端创建个人访问 Token，供 MCP 连接画布使用。</p>
      </div>
    </div>

    <div class="card">
      <div class="section-head"><h2>新建 Token</h2></div>
      <form class="token-create" @submit.prevent="create">
        <label class="field">
          <span class="field-label">名称</span>
          <input v-model.trim="form.name" placeholder="例如：Trae / 本机 CLI" />
        </label>
        <label class="field">
          <span class="field-label">有效期</span>
          <select v-model.number="form.expiresInDays">
            <option :value="30">30 天</option>
            <option :value="90">90 天</option>
            <option :value="365">365 天</option>
            <option :value="0">永久有效</option>
          </select>
        </label>
        <button class="btn primary" type="submit" :disabled="creating">
          {{ creating ? '创建中…' : '创建 Token' }}
        </button>
      </form>
    </div>

    <div v-if="created" class="section">
      <div class="card">
        <div class="section-head">
          <h2>新 Token 已创建</h2>
          <span class="pill tone-busy">仅显示一次</span>
        </div>
        <p class="muted">请立即复制并妥善保存，离开本页后无法再次查看完整 Token。</p>
        <div class="token-once" style="margin-top: 12px">
          <code>{{ created.token }}</code>
          <button class="btn primary sm" @click="copyText(created.token, 'Token 已复制')">复制</button>
          <button class="btn ghost sm" @click="created = null">已保存</button>
        </div>
      </div>
    </div>

    <section class="section">
      <div class="section-head">
        <h2>我的 Token</h2>
        <span class="muted">{{ tokens.length }} 个</span>
      </div>
      <div v-if="loading" class="loading">正在加载 Token…</div>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>前缀</th>
              <th>创建时间</th>
              <th>过期时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in tokens" :key="item.id">
              <td>{{ item.name }}</td>
              <td class="mono">{{ item.prefix }}…</td>
              <td>{{ formatDate(item.createdAt) }}</td>
              <td>{{ item.expiresAt ? formatDate(item.expiresAt) : '永久' }}</td>
              <td>
                <span class="pill" :class="item.revokedAt ? 'tone-error' : 'tone-done'">
                  {{ item.revokedAt ? '已撤销' : '有效' }}
                </span>
              </td>
              <td>
                <button
                  class="btn danger sm"
                  :disabled="!!item.revokedAt || revoking === item.id"
                  @click="revoke(item)"
                >
                  {{ revoking === item.id ? '撤销中…' : '撤销' }}
                </button>
              </td>
            </tr>
            <tr v-if="!tokens.length"><td colspan="6" class="empty">还没有创建过 Token</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <p class="muted" style="margin-top: 16px">
      在 MCP 客户端中填入画布服务的 SSE 地址，并在请求头使用
      <span class="mono">Authorization: Bearer &lt;Token&gt;</span> 访问。
    </p>
  </AccountShell>
</template>
