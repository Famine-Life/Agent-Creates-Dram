import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 20000,
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN'
})

/** 402 错误码对应的补充提示，后端 message 仍会保留在前端展示 */
const PAYMENT_ERROR_HINTS = {
  INSUFFICIENT_BALANCE: '积分不足，请充值后再试',
  QUOTA_EXCEEDED: '今日生成额度已用完',
  EMAIL_NOT_VERIFIED: '请先完成邮箱验证'
}

function readCsrfCookie() {
  if (typeof document === 'undefined') return ''
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

/**
 * 所有写请求都携带最新的 CSRF Token：
 * 优先读取 Cookie（登录后 Spring 会轮换 Token），缺失时先请求 /auth/csrf 再重试。
 */
http.interceptors.request.use(async (config) => {
  const method = (config.method || 'get').toLowerCase()
  if (method === 'get' || method === 'head' || method === 'options') return config
  let token = readCsrfCookie()
  if (!token) {
    try {
      const data = await http.get('/auth/csrf')
      token = data?.token || readCsrfCookie()
    } catch (e) {
      /* 交给后端返回 403，前端不做额外处理 */
    }
  }
  if (token) {
    if (typeof config.headers?.set === 'function') config.headers.set('X-XSRF-TOKEN', token)
    else {
      config.headers = config.headers || {}
      config.headers['X-XSRF-TOKEN'] = token
    }
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error?.response?.status
    const requestUrl = error?.config?.url || ''
    if (status === 401 && !requestUrl.startsWith('/auth/')) {
      window.dispatchEvent(new CustomEvent('aigc:unauthorized'))
    }
    const data = error?.response?.data
    const serverMessage = data?.message || error.message || '请求失败'
    const errorCode = data?.errorCode || ''
    let message = serverMessage
    if (status === 402) {
      const hint = PAYMENT_ERROR_HINTS[errorCode]
      const suffix = errorCode ? `（${errorCode}）` : ''
      message = hint && hint !== serverMessage ? `${serverMessage}，${hint}${suffix}` : `${serverMessage}${suffix}`
    }
    const wrapped = new Error(message)
    wrapped.status = status
    wrapped.errorCode = errorCode
    wrapped.serverMessage = serverMessage
    wrapped.data = data
    return Promise.reject(wrapped)
  }
)

export const api = {
  // ---------------------------------------------------------------- 认证
  csrf: () => http.get('/auth/csrf'),
  session: () => http.get('/auth/session'),
  login: (username, password, csrfToken) =>
    http.post('/auth/login', new URLSearchParams({ username, password }), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {})
      }
    }),
  logout: () => http.post('/auth/logout'),
  register: (body) => http.post('/auth/register', body),
  verifyEmail: (token) => http.post('/auth/verify-email', { token }),
  resendVerification: (email) => http.post('/auth/resend-verification', { email }),
  forgotPassword: (email) => http.post('/auth/forgot-password', { email }),
  resetPassword: (token, password) => http.post('/auth/reset-password', { token, password }),

  // ---------------------------------------------------------------- 账户
  account: () => http.get('/account'),
  ledgers: (limit = 50) => http.get('/account/ledgers', { params: { limit } }),
  plans: () => http.get('/account/plans'),
  packages: () => http.get('/account/packages'),
  mcpTokens: () => http.get('/account/mcp-tokens'),
  createMcpToken: (body = {}) => http.post('/account/mcp-tokens', body),
  revokeMcpToken: (id) => http.delete(`/account/mcp-tokens/${id}`),

  // ---------------------------------------------------------------- 支付
  paymentChannels: () => http.get('/payments/channels'),
  createPaymentOrder: (body) => http.post('/payments/orders', body),
  paymentOrders: (limit = 20) => http.get('/payments/orders', { params: { limit } }),
  paymentOrder: (orderNo) => http.get(`/payments/orders/${orderNo}`),
  mockPay: (orderNo) => http.post(`/payments/orders/${orderNo}/mock-pay`),
  closeOrder: (orderNo) => http.post(`/payments/orders/${orderNo}/close`),

  // ---------------------------------------------------------------- 管理后台
  adminOverview: () => http.get('/admin/overview'),
  adminUsers: (keyword = '', limit = 50) =>
    http.get('/admin/users', { params: { keyword, limit } }),
  adminUpdateUser: (id, body) => http.put(`/admin/users/${id}`, body),
  adminPlans: () => http.get('/admin/plans'),
  adminUpdatePlan: (code, body) => http.put(`/admin/plans/${code}`, body),
  adminPackages: () => http.get('/admin/credit-packages'),
  adminCreatePackage: (body) => http.post('/admin/credit-packages', body),
  adminUpdatePackage: (id, body) => http.put(`/admin/credit-packages/${id}`, body),
  adminDeletePackage: (id) => http.delete(`/admin/credit-packages/${id}`),
  adminOrders: (limit = 50) => http.get('/admin/orders', { params: { limit } }),
  adminAudit: (limit = 100) => http.get('/admin/audit', { params: { limit } }),
  adminDeadEvents: (limit = 50) => http.get('/admin/outbox/dead', { params: { limit } }),
  adminRetryOutbox: (id) => http.post(`/admin/outbox/${id}/retry`),

  // ---------------------------------------------------------------- 项目
  /** 项目列表：每项为 { id, name, canvasCount, updatedAt } */
  projects: () => http.get('/projects'),
  project: (projectId) => http.get(`/projects/${projectId}`),
  createProject: (name) => http.post('/projects', { name }),
  renameProject: (projectId, name) => http.put(`/projects/${projectId}`, { name }),
  deleteProject: (projectId) => http.delete(`/projects/${projectId}`),

  // ---------------------------------------------------------------- 画布
  meta: () => http.get('/meta'),
  /** 画布列表：传 projectId 时只返回该项目下的画布 */
  workspaces: (projectId) =>
    http.get('/workspaces', { params: projectId ? { projectId } : {} }),
  /** 新建画布：传 projectId 时归入该项目，否则使用当前用户的默认项目 */
  createWorkspace: (projectId) => http.post('/workspaces', projectId ? { projectId } : {}),
  renameWorkspace: (workspaceId, name) => http.put(`/workspaces/${workspaceId}`, { name }),
  copyWorkspace: (workspaceId) => http.post(`/workspaces/${workspaceId}/copy`),
  deleteWorkspace: (workspaceId) => http.delete(`/workspaces/${workspaceId}`),
  canvas: (workspaceId) => http.get(`/canvas/${workspaceId}`),
  promptRecords: (workspaceId) => http.get(`/canvas/${workspaceId}/prompt-records`),
  createShot: (workspaceId, body) => http.post(`/canvas/${workspaceId}/shots`, body),
  createStoryboard: (workspaceId, body) => http.post(`/canvas/${workspaceId}/storyboards`, body),
  savePrompt: (taskId, body) => http.put(`/tasks/${taskId}/prompt`, body),
  retry: (taskId) => http.post(`/tasks/${taskId}/retry`),
  cancel: (taskId) => http.post(`/tasks/${taskId}/cancel`),
  removeTask: (taskId) => http.delete(`/tasks/${taskId}`),
  // lite 只返回版本号，避免切换画布前回传整张画布的节点数据
  saveSnapshot: (workspaceId, snapshot, baseVersion) =>
    http.put(`/canvas/${workspaceId}/snapshot?lite=1`, { snapshot, baseVersion }),
  saveOrder: (workspaceId, items) => http.put(`/canvas/${workspaceId}/order`, { items }),
  createEdge: (workspaceId, body) => http.post(`/canvas/${workspaceId}/edges`, body),
  removeEdge: (workspaceId, edgeId) => http.delete(`/canvas/${workspaceId}/edges/${edgeId}`),
  removeImage: (workspaceId, imageId) => http.delete(`/canvas/${workspaceId}/images/${imageId}`),
  createImage: (workspaceId, body) => http.post(`/canvas/${workspaceId}/images`, body),
  updateImage: (workspaceId, imageId, body) => http.put(`/canvas/${workspaceId}/images/${imageId}`, body),
  createCanvasNode: (workspaceId, body) => http.post(`/canvas/${workspaceId}/nodes`, body),
  updateCanvasNode: (workspaceId, nodeId, body) => http.put(`/canvas/${workspaceId}/nodes/${nodeId}`, body),
  removeCanvasNode: (workspaceId, nodeId) => http.delete(`/canvas/${workspaceId}/nodes/${nodeId}`),
  generateScript: (workspaceId, nodeId, prompt, model) =>
    http.post(`/canvas/${workspaceId}/nodes/${nodeId}/script`, { prompt, model }),
  createAiImage: (workspaceId, body) => http.post(`/canvas/${workspaceId}/images/ai`, body),
  optimizePrompt: (workspaceId, prompt, model) =>
    http.post(`/canvas/${workspaceId}/ai/prompt/optimize`, { prompt, model }),
  generateImage: (workspaceId, imageId, prompt, model, referenceUrls, settings = {}) =>
    http.post(`/canvas/${workspaceId}/images/${imageId}/generate`, {
      prompt,
      model,
      referenceUrls,
      ...settings
    }),
  upload: (workspaceId, file, x, y) => {
    const form = new FormData()
    form.append('file', file)
    form.append('workspaceId', workspaceId)
    let url = `/uploads?workspaceId=${workspaceId}`
    if (x != null && y != null) url += `&x=${Math.round(x)}&y=${Math.round(y)}`
    return http.post(url, form)
  }
}
