<script setup>
// 访客首页：登录、注册、修改密码三合一表单，左侧展示 Agent 驱动的创作流程与会员套餐
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
    <div class="landing-bg" aria-hidden="true">
      <span class="orb orb-a"></span>
      <span class="orb orb-b"></span>
      <span class="grid"></span>
    </div>

    <div class="landing-layout">
      <!-- ---------------------------------------------------- 左侧：Agent 指挥台 -->
      <section class="hero">
        <header class="hero-brand">
          <span class="logo">◈</span>
          <div>
            <strong>Agent造剧</strong>
            <span>Agent 驱动的视频与漫剧工作台</span>
          </div>
        </header>

        <div class="hero-tags">
          <span class="tag hot">Agent CLI</span>
          <span class="tag">分镜视频</span>
          <span class="tag">可编辑画布</span>
        </div>

        <h1>
          专注你的创作<br />
          <span>让Agent帮你编排画布</span>
        </h1>
        <p class="hero-desc">
          Codex、Claude Code、WorkBuddy 等 Agent 通过 CLI 把创意拆成脚本、参考图与分镜视频，
          直接落到同一张画布。你只管检查、调整，推进下一轮。
        </p>

        <!-- Agent 对话 → 流水线演示 -->
        <div class="agent-flow" aria-hidden="true">
          <div class="agent-cmd">
            <span class="cmd-avatar">✳</span>
            <p>帮我制作一集古风漫剧：整理脚本和分镜，准备角色参考图，提交视频生成并关联到同一张画布。</p>
            <span class="cmd-badge">Agent</span>
          </div>

          <ol class="pipeline">
            <li class="pipe-step step-script">
              <span class="pipe-ic">
                <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                  <path d="M4 2.5h5l3 3v8H4z" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round" />
                  <path d="M9 2.5v3h3M6 8h4M6 10.5h4" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
                </svg>
              </span>
              <div class="pipe-body">
                <strong>脚本节点</strong>
                <em>古风漫剧 · 8 个分镜</em>
              </div>
              <span class="chip done">已完成</span>
            </li>
            <li class="pipe-step step-image">
              <span class="pipe-ic">
                <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                  <rect x="2.5" y="3" width="11" height="10" rx="1.6" stroke="currentColor" stroke-width="1.2" />
                  <circle cx="6" cy="6.5" r="1.2" stroke="currentColor" stroke-width="1.2" />
                  <path d="m4 11 3-3 2.2 2.2L11 8l2 2.6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
              <div class="pipe-body">
                <strong>参考图节点</strong>
                <em>角色定妆 · 3 张图片</em>
              </div>
              <span class="chip done">已关联</span>
            </li>
            <li class="pipe-step step-video">
              <span class="pipe-ic">
                <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                  <rect x="2.5" y="3" width="11" height="10" rx="2" stroke="currentColor" stroke-width="1.2" />
                  <path d="M6.8 6.2v3.6l3-1.8z" fill="currentColor" />
                </svg>
              </span>
              <div class="pipe-body">
                <strong>分镜视频</strong>
                <em>Seedance · 3 / 8 生成中</em>
              </div>
              <span class="chip run"><i></i>生成中</span>
            </li>
          </ol>
        </div>

        <ul class="caps">
          <li><b>CLI 编排</b>批量建节点</li>
          <li><b>画布可编辑</b>随时接管</li>
          <li><b>多模型接入</b>可私有化</li>
        </ul>

        <div class="plans">
          <div class="plan">
            <span>免费版</span>
            <b>¥0</b>
            <em>50 积分/日 · 720P</em>
          </div>
          <div class="plan featured">
            <span>专业版</span>
            <b>¥29.9<small>/月</small></b>
            <em>500 积分 · 2 并发</em>
          </div>
          <div class="plan">
            <span>工作室版</span>
            <b>¥99.9<small>/月</small></b>
            <em>3000 积分 · 4K</em>
          </div>
        </div>

        <button class="btn primary hero-cta" type="button" @click="setTab('register')">
          免费注册，立即体验
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
            <path d="M3 8h9M8.5 4.5 12 8l-3.5 3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        <p class="hero-note">免费版每日 50 积分，随时在「会员与充值」升级；支持私有化部署。</p>
      </section>

      <!-- ---------------------------------------------------- 右侧：认证卡 -->
      <section class="auth-card">
        <header class="auth-card-head">
          <h2>{{ head.title }}</h2>
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
  /* 落地页固定为暗黑霓虹紫基调，不随全局明暗主题切换 */
  color-scheme: dark;
  --bg: #0a0b0d;
  --panel: #12161c;
  --card: #1b1f27;
  --surface: #171b22;
  --sunken: #14181e;
  --raised: #252b35;
  --border: #2e343e;
  --line-strong: #2a303a;
  --text: #eef0f4;
  --text-soft: #c3c8d1;
  --text-strong: #dadde4;
  --muted: #9aa1ad;
  --brand: #7c6bff;
  --brand-soft: rgba(124, 107, 255, 0.14);
  --accent-text: #c9c0ff;
  position: relative;
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 44px 20px 64px;
  overflow: hidden;
  background: #08090c;
}

/* ---------------------------------------------------------------- 环境光背景 */

.landing-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.55;
}

.orb-a {
  width: 640px;
  height: 640px;
  top: -220px;
  left: -180px;
  background: radial-gradient(circle, rgba(124, 107, 255, 0.45), transparent 66%);
  animation: drift 20s ease-in-out infinite;
}

.orb-b {
  width: 560px;
  height: 560px;
  right: -180px;
  bottom: -220px;
  background: radial-gradient(circle, rgba(79, 179, 217, 0.3), transparent 66%);
  animation: drift 26s ease-in-out infinite reverse;
}

.landing-bg .grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(124, 107, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(124, 107, 255, 0.06) 1px, transparent 1px);
  background-size: 46px 46px;
  -webkit-mask-image: radial-gradient(circle at 50% 24%, #000 0%, transparent 72%);
  mask-image: radial-gradient(circle at 50% 24%, #000 0%, transparent 72%);
}

.landing-layout {
  position: relative;
  z-index: 1;
  width: min(1180px, 100%);
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 44px;
  align-items: center;
}

/* ---------------------------------------------------------------- 左侧 Hero */

.hero {
  min-width: 0;
}

.hero-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-brand strong {
  display: block;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.6px;
  color: #f4f5fa;
  text-shadow: 0 0 14px rgba(124, 107, 255, 0.35);
}

.hero-brand span {
  color: var(--text-soft);
  font-size: 12px;
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 22px;
}

.tag {
  padding: 3px 11px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: rgba(124, 107, 255, 0.06);
  color: #c2c8d8;
  font-size: 12.5px;
  letter-spacing: 0.3px;
}

.tag.hot {
  border-color: rgba(124, 107, 255, 0.42);
  background: rgba(124, 107, 255, 0.14);
  color: #c6bfff;
}

.hero h1 {
  margin: 14px 0 0;
  font-size: clamp(30px, 3.2vw, 42px);
  line-height: 1.3;
  font-weight: 700;
  letter-spacing: 0.4px;
  color: var(--text);
}

.hero h1 span {
  background: linear-gradient(100deg, #b9aeff 0%, #7c6bff 45%, #6fd0ea 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-desc {
  margin: 14px 0 0;
  max-width: 500px;
  color: var(--text-soft);
  font-size: 14px;
  line-height: 1.8;
}

/* ---------------- Agent 对话与流水线 ---------------- */

.agent-flow {
  margin-top: 20px;
  max-width: 500px;
}

.agent-cmd {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 12px;
  border-radius: 14px;
  border: 1px solid rgba(124, 107, 255, 0.3);
  background: linear-gradient(155deg, rgba(124, 107, 255, 0.16), rgba(17, 19, 23, 0.7));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05), 0 10px 30px rgba(0, 0, 0, 0.22);
}

.cmd-avatar {
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: rgba(124, 107, 255, 0.22);
  color: #c6bfff;
  font-size: 11px;
  animation: avatarPulse 3s ease-in-out infinite;
}

.agent-cmd p {
  margin: 1px 0 0;
  color: #e6e9f0;
  font-size: 13.5px;
  line-height: 1.65;
}

.cmd-badge {
  flex: 0 0 auto;
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid rgba(124, 107, 255, 0.35);
  background: rgba(124, 107, 255, 0.12);
  color: #b9aeff;
  font-size: 10.5px;
  letter-spacing: 0.4px;
}

.pipeline {
  position: relative;
  list-style: none;
  margin: 10px 0 0;
  padding: 0 0 0 4px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.pipeline::before {
  content: '';
  position: absolute;
  left: 21px;
  top: 18px;
  bottom: 18px;
  width: 1px;
  background: linear-gradient(180deg, rgba(70, 211, 154, 0.5), rgba(124, 107, 255, 0.4), rgba(240, 180, 41, 0.45));
}

.pipe-step {
  position: relative;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 12px 10px 10px;
  border-radius: 11px;
  border: 1px solid var(--border);
  background: var(--surface);
  opacity: 0;
  transform: translateY(8px);
  animation: stepIn 0.55s cubic-bezier(0.2, 0.7, 0.3, 1) forwards;
}

.step-script {
  animation-delay: 0.15s;
}

.step-image {
  animation-delay: 0.35s;
}

.step-video {
  animation-delay: 0.55s;
}

.pipe-ic {
  flex: 0 0 auto;
  position: relative;
  z-index: 1;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  border: 1px solid rgba(124, 107, 255, 0.28);
  background: var(--panel);
  color: #b9aeff;
}

.step-script .pipe-ic {
  border-color: rgba(70, 211, 154, 0.32);
  color: #7fe0b5;
}

.step-video .pipe-ic {
  border-color: rgba(240, 180, 41, 0.32);
  color: #f0c46a;
}

.pipe-body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.pipe-body strong {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text);
}

.pipe-body em {
  font-style: normal;
  color: var(--text-soft);
  font-size: 12px;
}

.chip {
  flex: 0 0 auto;
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 9px;
  border-radius: 999px;
  font-size: 10.5px;
  white-space: nowrap;
}

.chip.done {
  border: 1px solid rgba(70, 211, 154, 0.3);
  background: rgba(70, 211, 154, 0.1);
  color: #9fe3c4;
}

.chip.run {
  border: 1px solid rgba(240, 180, 41, 0.32);
  background: rgba(240, 180, 41, 0.1);
  color: #f0c46a;
}

.chip.run i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #f0b429;
  animation: blink 1.2s ease-in-out infinite;
}

/* ---------------- 能力标签与套餐 ---------------- */

.caps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  list-style: none;
  margin: 16px 0 0;
  padding: 0;
}

.caps li {
  padding: 5px 12px;
  border-radius: 7px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-soft);
  font-size: 13px;
}

.caps b {
  margin-right: 4px;
  color: var(--text-soft);
  font-weight: 600;
}

.plans {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 18px;
  max-width: 500px;
}

.plan {
  padding: 9px 11px;
  border-radius: 11px;
  border: 1px solid var(--border);
  background: var(--surface);
}

.plan.featured {
  border-color: rgba(124, 107, 255, 0.5);
  background: linear-gradient(155deg, rgba(124, 107, 255, 0.18), rgba(124, 107, 255, 0.05));
  box-shadow: 0 8px 26px rgba(124, 107, 255, 0.16);
}

.plan span {
  display: block;
  color: var(--text-soft);
  font-size: 12px;
}

.plan b {
  display: block;
  margin-top: 2px;
  color: var(--text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 19px;
  font-weight: 600;
}

.plan b small {
  margin-left: 1px;
  color: var(--text-soft);
  font-family: inherit;
  font-size: 11px;
  font-weight: 400;
}

.plan em {
  display: block;
  margin-top: 3px;
  color: var(--text-soft);
  font-style: normal;
  font-size: 12.5px;
  line-height: 1.5;
}

.hero-cta {
  height: 44px;
  margin-top: 20px;
  padding: 0 24px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  background: linear-gradient(120deg, #8b7cff, #6a55e8);
  border-color: transparent;
  box-shadow: 0 10px 30px rgba(124, 107, 255, 0.34);
  transition: transform 0.18s, box-shadow 0.18s;
}

.hero-cta:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 36px rgba(124, 107, 255, 0.44);
  background: linear-gradient(120deg, #9a8dff, #7561f0);
}

.hero-note {
  margin: 12px 0 0;
  color: var(--text-soft);
  font-size: 12px;
}

/* 暗色登录卡：提升字段标签对比度（仅作用于本组件） */
.field-label {
  font-size: 12.5px;
  color: #d8dcea;
}
.field-label em {
  color: #aeb4c6;
}

/* ---------------------------------------------------------------- 右侧认证卡 */

.auth-card {
  position: relative;
  width: 100%;
  max-width: 430px;
  margin-left: auto;
  border-radius: 20px;
  border: 1px solid var(--line-strong);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent 38%),
    var(--panel);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  box-shadow: 0 32px 90px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  overflow: hidden;
  color: var(--text);
}

.auth-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--brand), #6fd0ea, transparent);
  opacity: 0.85;
}

.auth-card-head {
  padding: 24px 24px 0;
}

.auth-card-head h2 {
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
  transition: border-color 0.15s, box-shadow 0.15s;
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

/* ---------------------------------------------------------------- 动效 */

@keyframes drift {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(40px, 26px, 0) scale(1.06);
  }
}

@keyframes stepIn {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.25;
  }
}

@keyframes avatarPulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(124, 107, 255, 0.35);
  }
  50% {
    box-shadow: 0 0 0 6px rgba(124, 107, 255, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .orb,
  .cmd-avatar,
  .chip.run i {
    animation: none;
  }

  .pipe-step {
    opacity: 1;
    transform: none;
    animation: none;
  }
}

/* ---------------------------------------------------------------- 响应式 */

@media (max-width: 980px) {
  .landing {
    padding: 28px 18px 48px;
  }

  .landing-layout {
    grid-template-columns: 1fr;
    gap: 28px;
    width: min(560px, 100%);
  }

  .auth-card {
    order: 1;
    max-width: none;
    margin: 0 auto;
  }

  .hero {
    order: 2;
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

  .hero h1 {
    font-size: 24px;
  }

  .hero-desc {
    font-size: 12px;
  }
}

@media (max-width: 520px) {
  .plans {
    grid-template-columns: 1fr;
  }

  .plan {
    display: flex;
    align-items: baseline;
    gap: 8px;
  }

  .plan span {
    flex: 1 1 auto;
  }

  .plan b {
    margin: 0;
  }

  .plan em {
    margin: 0;
  }

  .hero-cta {
    width: 100%;
  }
}
</style>
