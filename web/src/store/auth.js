import { computed, reactive } from 'vue'
import { api } from '../api'

/** 当前登录会话与账户摘要，路由守卫、顶栏和各页面共用 */
export const authState = reactive({
  ready: false,
  loading: false,
  authenticated: false,
  user: null,
  error: ''
})

let bootstrapPromise = null

function applySession(session) {
  const authenticated = session?.authenticated === true
  authState.authenticated = authenticated
  authState.user = authenticated ? session.user || null : null
  if (!authenticated) authState.error = ''
}

async function loadSession() {
  authState.loading = true
  try {
    const session = await api.session()
    applySession(session)
    authState.error = ''
  } catch (e) {
    authState.authenticated = false
    authState.user = null
    authState.error = e.message
  } finally {
    authState.ready = true
    authState.loading = false
  }
}

/** 首次进入或需要强制刷新时读取 session；并发调用共享同一次请求 */
export async function ensureSession(force = false) {
  if (authState.ready && !force) return authState.authenticated
  if (!bootstrapPromise) {
    bootstrapPromise = loadSession().finally(() => {
      bootstrapPromise = null
    })
  }
  await bootstrapPromise
  return authState.authenticated
}

export function clearAuth() {
  authState.authenticated = false
  authState.user = null
  authState.ready = true
}

/** 登录：先取 CSRF，再用表单编码提交用户名密码 */
export async function login(username, password) {
  const csrf = await api.csrf()
  await api.login(username, password, csrf?.token)
  await ensureSession(true)
  if (!authState.authenticated) {
    throw new Error(authState.error || '登录失败，请检查用户名和密码')
  }
  return authState.user
}

export async function logout() {
  try {
    await api.csrf()
  } catch (e) {
    /* 服务不可达时也允许本地退出 */
  }
  try {
    await api.logout()
  } catch (e) {
    /* 会话已失效时继续清理本地状态 */
  }
  clearAuth()
}

export async function refreshAccount() {
  const summary = await api.account()
  authState.user = summary
  authState.authenticated = true
  return summary
}

export function register(body) {
  return api.register(body)
}

export function verifyEmail(token) {
  return api.verifyEmail(token)
}

export function resendVerification(email) {
  return api.resendVerification(email)
}

export function forgotPassword(email) {
  return api.forgotPassword(email)
}

export function resetPassword(token, password) {
  return api.resetPassword(token, password)
}

export const isAdmin = computed(() => authState.user?.role === 'ADMIN')
export const emailVerified = computed(() => authState.user?.emailVerified === true)
