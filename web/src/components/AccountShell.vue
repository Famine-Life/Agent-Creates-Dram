<script setup>
// 账户页外壳：统一顶栏、导航、积分与会员信息和退出登录入口，页面内容通过默认插槽渲染
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { authState, isAdmin, logout } from '../store/auth'
import { resetCanvas, toast } from '../store/canvas'
import { theme, toggleTheme } from '../theme'

defineProps({
  subtitle: { type: String, default: '会员创作空间' }
})

const router = useRouter()
const credits = computed(() => authState.user?.availableCredits ?? 0)
const member = computed(() => authState.user?.memberName || authState.user?.memberCode || '免费版')

// 退出登录：服务端注销失败也要清理本地画布状态，并回到首页
async function handleLogout() {
  try {
    await logout()
  } catch (e) {
    toast(`退出登录失败：${e.message}`)
  }
  resetCanvas()
  router.push({ name: 'home' })
}
</script>

<template>
  <div class="page-shell">
    <header class="page-topbar">
      <router-link class="brand" to="/">
        <span class="logo">◈</span>
        <div class="brand-text">
          <strong>Agent造剧</strong>
          <span class="muted">{{ subtitle }}</span>
        </div>
      </router-link>

      <nav class="page-nav">
        <router-link to="/">我的项目</router-link>
        <router-link to="/account">我的账户</router-link>
        <router-link to="/account/billing">会员与充值</router-link>
        <router-link to="/account/tokens">MCP Token</router-link>
        <router-link v-if="isAdmin" to="/admin">管理后台</router-link>
      </nav>

      <div class="page-actions">
        <span v-if="authState.user" class="account-chip static">
          <span class="account-credits">{{ credits }} 积分</span>
          <span class="account-member">{{ member }}</span>
        </span>
        <button class="btn ghost" @click="toggleTheme">
          {{ theme === 'dark' ? '白天模式' : '夜间模式' }}
        </button>
        <button class="btn logout" @click="handleLogout">退出登录</button>
      </div>
    </header>

    <main class="page-container">
      <slot />
    </main>
  </div>
</template>
