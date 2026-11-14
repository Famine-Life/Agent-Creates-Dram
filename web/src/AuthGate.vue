<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import App from './App.vue'
import { api } from './api'
import { toast } from './store/canvas'

const checking = ref(true)
const authenticated = ref(false)
const busy = ref(false)
const username = ref('')
const password = ref('')
const error = ref('')

function onUnauthorized() {
  authenticated.value = false
  password.value = ''
}

async function login() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.login(username.value, password.value)
    password.value = ''
    authenticated.value = true
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function logout() {
  try {
    await api.logout()
    authenticated.value = false
    password.value = ''
    try {
      await api.csrf()
    } catch (e) {
      error.value = `会话已退出，但 CSRF 初始化失败：${e.message}`
    }
  } catch (e) {
    toast(`退出登录失败：${e.message}`)
  }
}

onMounted(async () => {
  window.addEventListener('aigc:unauthorized', onUnauthorized)
  try {
    await api.csrf()
    const session = await api.session()
    authenticated.value = session.authenticated === true
  } catch (e) {
    error.value = `无法连接服务器：${e.message}`
  } finally {
    checking.value = false
  }
})

onBeforeUnmount(() => window.removeEventListener('aigc:unauthorized', onUnauthorized))
</script>

<template>
  <App v-if="authenticated" @logout="logout" />

  <main v-else class="auth-shell">
    <section class="auth-card">
      <div class="auth-brand">
        <span class="auth-logo">◈</span>
        <div>
          <h1>AIGC 画布工作台</h1>
          <p>私有工作空间</p>
        </div>
      </div>

      <div v-if="checking" class="auth-checking">正在检查登录状态…</div>

      <form v-else @submit.prevent="login">
        <p class="auth-intro">登录后才能访问画布、REST 接口与实时连接。</p>
        <label>
          <span>用户名</span>
          <input v-model.trim="username" name="username" autocomplete="username" required autofocus />
        </label>
        <label>
          <span>密码</span>
          <input
            v-model="password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
          />
        </label>
        <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
        <button class="btn primary auth-submit" type="submit" :disabled="busy">
          {{ busy ? '登录中…' : '登录' }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 20% 20%, rgba(88, 101, 242, 0.16), transparent 38%),
    radial-gradient(circle at 80% 80%, rgba(42, 157, 143, 0.12), transparent 34%),
    #101219;
}

.auth-card {
  width: min(400px, 100%);
  padding: 32px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 18px;
  background: rgba(25, 28, 39, 0.96);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.35);
  color: #f2f4f8;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}

.auth-logo {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: #5865f2;
  font-size: 25px;
}

.auth-brand h1 {
  margin: 0 0 5px;
  font-size: 20px;
}

.auth-brand p,
.auth-intro {
  margin: 0;
  color: #9da4b5;
  font-size: 13px;
}

.auth-intro {
  margin-bottom: 24px;
  line-height: 1.7;
}

form label {
  display: block;
  margin-bottom: 18px;
}

form label span {
  display: block;
  margin-bottom: 8px;
  color: #c7ccda;
  font-size: 13px;
}

form input {
  box-sizing: border-box;
  width: 100%;
  height: 42px;
  padding: 0 12px;
  border: 1px solid #373d4e;
  border-radius: 9px;
  outline: none;
  background: #12151e;
  color: #f2f4f8;
  font: inherit;
}

form input:focus {
  border-color: #7180ff;
  box-shadow: 0 0 0 3px rgba(88, 101, 242, 0.18);
}

.auth-error {
  margin: 0 0 16px;
  color: #ff9292;
  font-size: 13px;
  line-height: 1.6;
}

.auth-submit {
  width: 100%;
  height: 42px;
  justify-content: center;
}

.auth-submit:disabled {
  cursor: wait;
  opacity: 0.65;
}

.auth-checking {
  padding: 20px 0 8px;
  color: #aeb5c6;
  text-align: center;
}
</style>
