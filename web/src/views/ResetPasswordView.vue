<script setup>
// 重置密码：使用邮件链接中的令牌设置新的登录密码
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { resetPassword } from '../store/auth'

const route = useRoute()
const token = ref(String(route.query.token || ''))
const password = ref('')
const confirm = ref('')
const busy = ref(false)
const error = ref('')
const done = ref(false)

async function submit() {
  if (busy.value) return
  error.value = ''
  if (!token.value.trim()) {
    error.value = '缺少重置令牌，请从邮件中的链接进入'
    return
  }
  if (password.value !== confirm.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  busy.value = true
  try {
    await resetPassword(token.value.trim(), password.value)
    done.value = true
    password.value = ''
    confirm.value = ''
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <div class="auth-brand">
        <span class="logo">◈</span>
        <div>
          <h1>重置密码</h1>
          <p>设置新的登录密码</p>
        </div>
      </div>

      <template v-if="done">
        <p class="auth-notice">密码已重置，请使用新密码登录。</p>
        <router-link class="btn primary block" to="/login">前往登录</router-link>
      </template>

      <template v-else>
        <form @submit.prevent="submit">
          <label class="field">
            <span class="field-label">重置令牌</span>
            <input v-model.trim="token" placeholder="从邮件链接中自动填入" required />
          </label>
          <label class="field">
            <span class="field-label">新密码</span>
            <input
              v-model="password"
              type="password"
              autocomplete="new-password"
              placeholder="8-72 位"
              required
              autofocus
            />
          </label>
          <label class="field">
            <span class="field-label">确认新密码</span>
            <input v-model="confirm" type="password" autocomplete="new-password" required />
          </label>
          <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
          <button class="btn primary block" type="submit" :disabled="busy">
            {{ busy ? '提交中…' : '重置密码' }}
          </button>
        </form>
        <div class="auth-links">
          <router-link to="/login">返回登录</router-link>
        </div>
      </template>
    </section>
  </main>
</template>
