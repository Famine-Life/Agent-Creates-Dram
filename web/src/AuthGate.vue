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
    <div class="auth-bg" aria-hidden="true">
      <span class="orb orb-a"></span>
      <span class="orb orb-b"></span>
      <span class="grid"></span>
    </div>

    <div class="auth-layout">
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
          <span class="tag hot">私有工作空间</span>
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

        <p class="hero-note">登录后进入私有工作空间，素材与任务数据留在你自己的服务器。</p>
      </section>

      <!-- ---------------------------------------------------- 右侧：登录卡 -->
      <section class="auth-card">
        <header class="auth-card-head">
          <h2>登录 Agent造剧</h2>
          <p>使用账号与密码进入你的私有创作台</p>
        </header>

        <div class="auth-body">
          <div v-if="checking" class="auth-checking">正在检查登录状态…</div>

          <form v-else @submit.prevent="login">
            <label class="field">
              <span class="field-label">用户名</span>
              <input v-model.trim="username" name="username" autocomplete="username" required autofocus />
            </label>
            <label class="field">
              <span class="field-label">密码</span>
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

          <p class="auth-foot">登录后才能访问画布、REST 接口与实时连接。</p>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.auth-shell {
  /* 登录页固定为暗黑霓虹紫基调，不随全局明暗主题切换 */
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

.auth-bg {
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

.auth-bg .grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(124, 107, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(124, 107, 255, 0.06) 1px, transparent 1px);
  background-size: 46px 46px;
  -webkit-mask-image: radial-gradient(circle at 50% 24%, #000 0%, transparent 72%);
  mask-image: radial-gradient(circle at 50% 24%, #000 0%, transparent 72%);
}

.auth-layout {
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

/* ---------------- 能力标签 ---------------- */

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

/* ---------------------------------------------------------------- 右侧登录卡 */

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

.auth-body {
  padding: 20px 24px 22px;
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

.auth-error {
  margin: 0 0 13px;
  padding: 9px 11px;
  border-radius: 9px;
  border: 1px solid rgba(242, 85, 90, 0.26);
  background: rgba(242, 85, 90, 0.08);
  color: #f2a0a3;
  font-size: 11.5px;
  line-height: 1.55;
}

.auth-submit {
  width: 100%;
  height: 36px;
  margin-top: 2px;
  font-weight: 600;
  box-shadow: 0 4px 16px rgba(124, 107, 255, 0.28);
}

.auth-submit:disabled {
  cursor: wait;
  opacity: 0.65;
}

.auth-foot {
  margin: 14px 0 0;
  color: var(--muted);
  font-size: 10.5px;
  text-align: center;
}

.auth-checking {
  padding: 16px 0 6px;
  color: var(--muted);
  font-size: 11.5px;
  text-align: center;
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
  .auth-shell {
    padding: 28px 18px 48px;
  }

  .auth-layout {
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
</style>
