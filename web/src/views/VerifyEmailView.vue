<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { authState, ensureSession, verifyEmail } from '../store/auth'

const route = useRoute()
const token = ref(String(route.query.token || ''))
const busy = ref(false)
const error = ref('')
const done = ref(false)

async function submit() {
  if (busy.value) return
  if (!token.value.trim()) {
    error.value = '缺少验证令牌，请粘贴邮件中的验证令牌'
    return
  }
  busy.value = true
  error.value = ''
  try {
    await verifyEmail(token.value.trim())
    done.value = true
    if (authState.authenticated) {
      await ensureSession(true).catch(() => {})
    }
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  if (token.value) submit()
})
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <div class="auth-brand">
        <span class="logo">◈</span>
        <div>
          <h1>邮箱验证</h1>
          <p>完成后即可生成内容与充值</p>
        </div>
      </div>

      <div v-if="busy" class="auth-checking">正在验证邮箱…</div>

      <template v-else-if="done">
        <p class="auth-notice">邮箱验证成功，现在可以正常生成和充值了。</p>
        <router-link class="btn primary block" :to="authState.authenticated ? '/account' : '/login'">
          {{ authState.authenticated ? '返回我的账户' : '前往登录' }}
        </router-link>
      </template>

      <template v-else>
        <p class="auth-hint">如果邮件链接无法直接打开，可以把邮件中的验证令牌粘贴到下方。</p>
        <form @submit.prevent="submit">
          <label class="field">
            <span class="field-label">验证令牌</span>
            <input v-model.trim="token" placeholder="从验证邮件中复制" required autofocus />
          </label>
          <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
          <button class="btn primary block" type="submit">验证邮箱</button>
        </form>
        <div class="auth-links">
          <router-link to="/login">返回登录</router-link>
          <router-link to="/account">我的账户</router-link>
        </div>
      </template>
    </section>
  </main>
</template>
