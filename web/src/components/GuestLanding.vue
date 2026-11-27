<script setup>
// 访客首页：登录、注册、修改密码三合一表单，左侧展示会员套餐与升级引导
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { forgotPassword, login, register } from '../store/auth'

/* ---------------------------------------------------------------- 标签切换 */

// 当前标签与地址栏 ?tab= 保持同步，刷新或分享链接后仍停留在对应表单
const TABS = ['login', 'register', 'reset']

const route = useRoute()
const router = useRouter()

const active = ref(TABS.includes(String(route.query.tab)) ? String(route.query.tab) : 'login')
watch(
  () => route.query.tab,
  (tab) => {
    if (TABS.includes(String(tab))) active.value = String(tab)
  }
)

const heads = {
  login: { title: '登录 Agent造剧', sub: '使用邮箱与密码进入你的创作台' },
  register: { title: '注册 Agent造剧', sub: '完成邮箱验证后即可生成与充值' },
  reset: { title: '修改密码', sub: '通过注册邮箱重置登录密码' }
}

const head = computed(() => heads[active.value] || heads.login)

function setTab(tab) {
  active.value = tab
  const query = { ...route.query, tab }
  router.replace({ name: 'home', query })
}

/* ---------------------------------------------------------------- 登录 */

const REMEMBER_KEY = 'aigc.login.username'
const savedName = typeof localStorage !== 'undefined' ? localStorage.getItem(REMEMBER_KEY) || '' : ''

const loginForm = ref({ username: savedName, password: '' })
const remember = ref(Boolean(savedName))
const loginBusy = ref(false)
const loginError = ref('')

async function submitLogin() {
  if (loginBusy.value) return
  loginBusy.value = true
  loginError.value = ''
  try {
    const name = loginForm.value.username.trim()
    await login(name, loginForm.value.password)
    if (remember.value) localStorage.setItem(REMEMBER_KEY, name)
    else localStorage.removeItem(REMEMBER_KEY)
    loginForm.value.password = ''
    const redirect = String(route.query.redirect || '')
    if (redirect.startsWith('/') && !redirect.startsWith('//')) {
      router.replace(redirect)
    } else {
      router.replace({ name: 'home' })
    }
  } catch (e) {
    loginError.value = e.message
  } finally {
    loginBusy.value = false
  }
}

/* ---------------------------------------------------------------- 注册 */

const registerForm = ref({ username: '', email: '', password: '', confirm: '' })
const registerBusy = ref(false)
const registerError = ref('')
const registered = ref(null)

const verifyRoute = computed(() => {
  if (!registered.value?.devLink) return null
  try {
    const token = new URL(registered.value.devLink).searchParams.get('token')
    return token ? { name: 'verify-email', query: { token } } : null
  } catch (e) {
    return null
  }
})

async function submitRegister() {
  if (registerBusy.value) return
  registerError.value = ''
  if (registerForm.value.password !== registerForm.value.confirm) {
    registerError.value = '两次输入的密码不一致'
    return
  }
  registerBusy.value = true
  try {
    const result = await register({
      username: registerForm.value.username.trim(),
      email: registerForm.value.email.trim(),
      password: registerForm.value.password
    })
    registered.value = {
      username: result?.username || registerForm.value.username.trim(),
      devLink: result?.devVerifyLink || ''
    }
    registerForm.value.password = ''
    registerForm.value.confirm = ''
  } catch (e) {
    registerError.value = e.message
  } finally {
    registerBusy.value = false
  }
}

function backToLogin() {
  registered.value = null
  setTab('login')
}

/* ---------------------------------------------------------------- 修改密码 */

const resetEmail = ref('')
const resetBusy = ref(false)
const resetError = ref('')
const resetSent = ref(false)
const resetDevLink = ref('')

const resetRoute = computed(() => {
  if (!resetDevLink.value) return null
  try {
    const token = new URL(resetDevLink.value).searchParams.get('token')
    return token ? { name: 'reset-password', query: { token } } : null
  } catch (e) {
    return null
  }
})

async function submitReset() {
  if (resetBusy.value) return
  resetBusy.value = true
  resetError.value = ''
  try {
    const result = await forgotPassword(resetEmail.value.trim())
    resetDevLink.value = result?.devResetLink || ''
    resetSent.value = true
  } catch (e) {
    resetError.value = e.message
  } finally {
    resetBusy.value = false
  }
}
</script>

<template>
  <main class="landing">
    <div class="landing-layout">
      <section class="promo">
        <div class="promo-inner">
          <div class="promo-tags">
            <span class="promo-tag plan">会员升级</span>
            <span class="promo-tag">额度 · 并发 · 分辨率</span>
          </div>
          <h2>升级会员<br />解锁更高<span>额度与并发</span></h2>
          <p class="promo-desc">
            免费版每日 50 积分，专业版 500 积分与双并发，工作室版 3000 积分、5 并发与 4K 权益。
            登录后可在「会员与充值」随时升级。
          </p>

          <ul class="promo-plans">
            <li class="promo-plan">
              <div class="plan-line">
                <span class="plan-name">免费版</span>
                <span class="plan-price">¥0<small>/月</small></span>
              </div>
              <div class="plan-spec">每日 50 积分 · 1 并发 · 720P</div>
            </li>
            <li class="promo-plan featured">
              <div class="plan-line">
                <span class="plan-name">专业版 <em>推荐</em></span>
                <span class="plan-price">¥29.9<small>/月</small></span>
              </div>
              <div class="plan-spec">每日 500 积分 · 2 并发 · 1080P</div>
            </li>
            <li class="promo-plan">
              <div class="plan-line">
                <span class="plan-name">工作室版</span>
                <span class="plan-price">¥99.9<small>/月</small></span>
              </div>
              <div class="plan-spec">每日 3000 积分 · 5 并发 · 4K</div>
            </li>
          </ul>

          <button class="btn primary promo-cta" type="button" @click="setTab('register')">
            免费注册，立即体验
          </button>
          <p class="promo-note">注册后可先体验免费版，随时在账户中升级。</p>
        </div>
      </section>

      <section class="auth-card">
        <header class="auth-card-head">
          <h1>{{ head.title }}</h1>
          <p>{{ head.sub }}</p>
        </header>

        <nav class="auth-tabs">
          <button :class="{ active: active === 'login' }" type="button" @click="setTab('login')">登录</button>
          <button :class="{ active: active === 'register' }" type="button" @click="setTab('register')">注册</button>
          <button :class="{ active: active === 'reset' }" type="button" @click="setTab('reset')">修改密码</button>
        </nav>

        <div class="auth-body">
          <!-- 登录 -->
          <template v-if="active === 'login'">
            <div v-if="loginError" class="notice danger" role="alert">
              <span class="ic">
                <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                  <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="1.3" />
                  <path d="M8 4.8v3.8M8 11.2v.2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                </svg>
              </span>
              <span>{{ loginError }}</span>
            </div>
            <form @submit.prevent="submitLogin">
              <label class="field">
                <span class="field-label">邮箱 / 用户名</span>
                <input
                  v-model.trim="loginForm.username"
                  name="username"
                  autocomplete="username"
                  placeholder="you@example.com"
                  required
                  autofocus
                />
              </label>
              <label class="field">
                <span class="field-label">密码 <em>至少 8 位</em></span>
                <input
                  v-model="loginForm.password"
                  name="password"
                  type="password"
                  autocomplete="current-password"
                  required
                />
              </label>
              <div class="auth-row">
                <label class="auth-remember">
                  <input v-model="remember" type="checkbox" />
                  记住我
                </label>
                <button class="auth-link" type="button" @click="setTab('reset')">忘记密码？</button>
              </div>
              <button class="btn primary auth-submit" type="submit" :disabled="loginBusy">
                {{ loginBusy ? '登录中…' : '登录' }}
              </button>
            </form>
            <p class="auth-alt">
              还没有账号？
              <button class="auth-link" type="button" @click="setTab('register')">免费注册</button>
              · 免费版每日 50 积分
            </p>
          </template>

          <!-- 注册 -->
          <template v-else-if="active === 'register'">
            <div v-if="registered" class="notice ok">
              <span class="ic">
                <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                  <path d="M3 8.5 6.5 12 13 4.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <span>
                账号 <b>{{ registered.username }}</b> 注册成功，验证邮件已发送。完成邮箱验证后即可生成与充值。
              </span>
            </div>
            <template v-if="registered">
              <p v-if="registered.devLink" class="auth-dev">
                开发环境验证链接：
                <router-link v-if="verifyRoute" :to="verifyRoute">打开验证链接</router-link>
                <span class="mono">{{ registered.devLink }}</span>
              </p>
              <button class="btn primary auth-submit" type="button" @click="backToLogin">前往登录</button>
            </template>
            <template v-else>
              <div v-if="registerError" class="notice danger" role="alert">
                <span class="ic">
                  <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                    <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="1.3" />
                    <path d="M8 4.8v3.8M8 11.2v.2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                  </svg>
                </span>
                <span>{{ registerError }}</span>
              </div>
              <form @submit.prevent="submitRegister">
                <label class="field">
                  <span class="field-label">用户名</span>
                  <input
                    v-model.trim="registerForm.username"
                    autocomplete="username"
                    placeholder="3-32 位字母、数字或下划线"
                    required
                  />
                </label>
                <label class="field">
                  <span class="field-label">邮箱 <em>将发送验证邮件</em></span>
                  <input v-model.trim="registerForm.email" type="email" autocomplete="email" placeholder="you@example.com" required />
                </label>
                <label class="field">
                  <span class="field-label">密码 <em>8-72 位</em></span>
                  <input
                    v-model="registerForm.password"
                    type="password"
                    autocomplete="new-password"
                    placeholder="至少 8 位"
                    required
                  />
                </label>
                <label class="field">
                  <span class="field-label">确认密码</span>
                  <input v-model="registerForm.confirm" type="password" autocomplete="new-password" required />
                </label>
                <button class="btn primary auth-submit" type="submit" :disabled="registerBusy">
                  {{ registerBusy ? '注册中…' : '注册并发送验证邮件' }}
                </button>
              </form>
              <p class="auth-alt">
                已有账号？
                <button class="auth-link" type="button" @click="setTab('login')">直接登录</button>
              </p>
            </template>
          </template>

          <!-- 修改密码 -->
          <template v-else>
            <div v-if="resetSent" class="notice ok">
              <span class="ic">
                <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                  <path d="M3 8.5 6.5 12 13 4.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <span>如果该邮箱已注册，重置链接已发送，请在 30 分钟内完成重置。</span>
            </div>
            <template v-if="resetSent">
              <p v-if="resetDevLink" class="auth-dev">
                开发环境重置链接：
                <router-link v-if="resetRoute" :to="resetRoute">打开重置链接</router-link>
                <span class="mono">{{ resetDevLink }}</span>
              </p>
              <button class="btn primary auth-submit" type="button" @click="setTab('login')">返回登录</button>
            </template>
            <template v-else>
              <p class="auth-hint">输入注册邮箱，我们会发送一封包含重置链接的邮件，链接 30 分钟内有效。</p>
              <div v-if="resetError" class="notice danger" role="alert">
                <span class="ic">
                  <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                    <circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="1.3" />
                    <path d="M8 4.8v3.8M8 11.2v.2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
                  </svg>
                </span>
                <span>{{ resetError }}</span>
              </div>
              <form @submit.prevent="submitReset">
                <label class="field">
                  <span class="field-label">注册邮箱</span>
                  <input v-model.trim="resetEmail" type="email" autocomplete="email" placeholder="you@example.com" required autofocus />
                </label>
                <button class="btn primary auth-submit" type="submit" :disabled="resetBusy">
                  {{ resetBusy ? '发送中…' : '发送重置链接' }}
                </button>
              </form>
              <p class="auth-alt">
                已拿到重置令牌？
                <router-link class="auth-link" to="/reset-password">直接重置</router-link>
              </p>
            </template>
          </template>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.landing {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 20px 56px;
  background: var(--bg);
}

.landing-layout {
  width: min(1216px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 28px;
  align-items: center;
}

/* ---------------------------------------------------------------- 左侧会员宣传 */

.promo {
  position: relative;
  min-height: 520px;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid var(--border);
  background:
    radial-gradient(900px 420px at 18% 8%, rgba(124, 107, 255, 0.28), transparent 60%),
    radial-gradient(700px 380px at 92% 88%, rgba(79, 179, 217, 0.16), transparent 62%),
    linear-gradient(160deg, #14161c 0%, #0c0e12 100%);
}

.promo::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, rgba(124, 107, 255, 0.22) 1px, transparent 1px);
  background-size: 24px 24px;
  opacity: 0.5;
  -webkit-mask-image: linear-gradient(180deg, #000 0%, transparent 85%);
  mask-image: linear-gradient(180deg, #000 0%, transparent 85%);
}

.promo-inner {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 26px;
}

.promo-tags {
  display: flex;
  align-items: center;
  gap: 6px;
}

.promo-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1.5px 6px;
  border-radius: 5px;
  border: 1px solid #2a2f38;
  background: #1c2027;
  color: #8d939e;
  font-size: 9.5px;
  line-height: 1.5;
}

.promo-tag.plan {
  color: #c6bfff;
  background: rgba(124, 107, 255, 0.12);
  border-color: rgba(124, 107, 255, 0.3);
}

.promo h2 {
  margin: 22px 0 8px;
  color: #e9ebef;
  font-size: 26px;
  line-height: 1.3;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.promo h2 span {
  color: #b9aeff;
}

.promo-desc {
  margin: 0;
  max-width: 400px;
  color: #8d939e;
  font-size: 12px;
  line-height: 1.8;
}

.promo-plans {
  list-style: none;
  margin: 18px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.promo-plan {
  padding: 10px 12px;
  border-radius: 11px;
  border: 1px solid rgba(124, 107, 255, 0.16);
  background: rgba(17, 19, 23, 0.72);
}

.promo-plan.featured {
  border-color: rgba(124, 107, 255, 0.45);
  background: rgba(124, 107, 255, 0.1);
}

.plan-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}

.plan-name {
  color: #e9ebef;
  font-size: 12.5px;
  font-weight: 600;
}

.plan-name em {
  margin-left: 5px;
  padding: 1px 5px;
  border-radius: 999px;
  border: 1px solid rgba(240, 180, 41, 0.4);
  background: rgba(240, 180, 41, 0.14);
  color: #f0c46a;
  font-size: 9px;
  font-style: normal;
  font-weight: 500;
}

.plan-price {
  color: #e9ebef;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 14px;
}

.plan-price small {
  margin-left: 1px;
  color: #5f6672;
  font-family: inherit;
  font-size: 9.5px;
}

.plan-spec {
  margin-top: 4px;
  color: #8d939e;
  font-size: 10.5px;
}

.promo-cta {
  width: 100%;
  height: 36px;
  margin-top: 16px;
  font-weight: 600;
  box-shadow: 0 4px 16px rgba(124, 107, 255, 0.28);
}

.promo-note {
  margin: 8px 0 0;
  color: #5f6672;
  font-size: 10px;
  text-align: center;
}

/* ---------------------------------------------------------------- 右侧认证卡 */

.auth-card {
  width: 100%;
  max-width: 430px;
  margin-left: auto;
  border-radius: 18px;
  border: 1px solid var(--line-strong);
  background: var(--panel);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.45);
  overflow: hidden;
  color: var(--text);
}

.auth-card-head {
  padding: 22px 24px 0;
}

.auth-card-head h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.2px;
}

.auth-card-head p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 11.5px;
}

.auth-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
  margin: 16px 24px 0;
  padding: 3px;
  border-radius: 9px;
  border: 1px solid var(--border);
  background: var(--surface);
}

.auth-tabs button {
  height: 27px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  font-family: inherit;
  font-size: 11.5px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.auth-tabs button:hover {
  color: var(--text);
}

.auth-tabs button.active {
  background: var(--card);
  color: var(--text);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

.auth-body {
  padding: 18px 24px 22px;
}

.auth-card :deep(.field) {
  margin-bottom: 13px;
}

.auth-card :deep(.field input) {
  height: 34px;
  padding: 8px 10px;
  border-radius: 8px;
  border-color: var(--border);
  background: var(--surface);
  font-size: 12px;
}

.auth-card :deep(.field input:focus) {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.notice {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  margin-bottom: 13px;
  padding: 9px 11px;
  border-radius: 9px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.55;
}

.notice b {
  color: var(--text);
  font-weight: 600;
}

.notice .ic {
  flex: 0 0 auto;
  margin-top: 1px;
}

.notice.danger {
  background: rgba(242, 85, 90, 0.08);
  border-color: rgba(242, 85, 90, 0.26);
  color: #f2a0a3;
}

.notice.ok {
  background: rgba(70, 211, 154, 0.07);
  border-color: rgba(70, 211, 154, 0.24);
  color: #9fe3c4;
}

.auth-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 2px 0 14px;
  font-size: 11px;
}

.auth-remember {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--muted);
  cursor: pointer;
}

.auth-remember input {
  accent-color: var(--brand);
  width: 12px;
  height: 12px;
  margin: 0;
}

.auth-link {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--accent-text);
  font-family: inherit;
  font-size: 11px;
  cursor: pointer;
  text-decoration: none;
}

.auth-link:hover {
  text-decoration: underline;
}

.auth-row .auth-link {
  margin-left: auto;
}

.auth-submit {
  width: 100%;
  height: 36px;
  font-weight: 600;
  box-shadow: 0 4px 16px rgba(124, 107, 255, 0.28);
}

.auth-alt {
  margin: 14px 0 0;
  color: var(--muted);
  font-size: 11px;
  text-align: center;
}

.auth-hint {
  margin: 0 0 13px;
  color: var(--muted);
  font-size: 11.5px;
  line-height: 1.7;
}

.auth-dev {
  margin: 0 0 13px;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
  word-break: break-all;
}

.auth-dev a {
  color: var(--accent-text);
}

/* ---------------------------------------------------------------- 响应式 */

@media (max-width: 900px) {
  .promo h2 {
    font-size: 22px;
  }
}

@media (max-width: 768px) {
  .landing {
    padding: 20px 16px 40px;
  }

  .landing-layout {
    grid-template-columns: 1fr;
  }

  .auth-card {
    order: 1;
    max-width: none;
    margin: 0 auto;
  }

  .promo {
    order: 2;
    min-height: 0;
  }
}

@media (max-width: 720px) {
  .auth-card-head {
    padding: 20px 18px 0;
  }

  .auth-tabs {
    margin: 14px 18px 0;
  }

  .auth-body {
    padding: 16px 18px 20px;
  }

  .promo-inner {
    padding: 20px;
  }
}
</style>
