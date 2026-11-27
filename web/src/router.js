import { createRouter, createWebHashHistory } from 'vue-router'
import { authState, ensureSession, isAdmin } from './store/auth'

/**
 * 使用 Hash 路由：生产环境由 Spring 直接托管 /，刷新任意子路由都不会 404 或触发鉴权拦截。
 * 首页根据登录态切换：已登录展示画布列表，未登录展示认证面板与会员升级宣传。
 */
const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('./views/HomeView.vue'),
    meta: { public: true, title: '首页' }
  },
  { path: '/login', redirect: { name: 'home', query: { tab: 'login' } } },
  { path: '/register', redirect: { name: 'home', query: { tab: 'register' } } },
  { path: '/forgot-password', redirect: { name: 'home', query: { tab: 'reset' } } },
  {
    path: '/reset-password',
    name: 'reset-password',
    component: () => import('./views/ResetPasswordView.vue'),
    meta: { public: true, title: '重置密码' }
  },
  {
    path: '/verify-email',
    name: 'verify-email',
    component: () => import('./views/VerifyEmailView.vue'),
    meta: { public: true, title: '验证邮箱' }
  },
  {
    path: '/projects/:projectId',
    name: 'project',
    component: () => import('./views/WorkspacesView.vue'),
    meta: { title: '项目画布' }
  },
  {
    path: '/canvas/:workspaceId',
    name: 'canvas',
    component: () => import('./App.vue'),
    meta: { title: '画布' }
  },
  {
    path: '/account',
    name: 'account',
    component: () => import('./views/AccountView.vue'),
    meta: { title: '我的账户' }
  },
  {
    path: '/account/billing',
    name: 'billing',
    component: () => import('./views/BillingView.vue'),
    meta: { title: '会员与充值' }
  },
  {
    path: '/account/tokens',
    name: 'tokens',
    component: () => import('./views/TokensView.vue'),
    meta: { title: 'MCP Token' }
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('./views/AdminView.vue'),
    meta: { title: '管理后台', admin: true }
  },
  { path: '/workspaces', redirect: { name: 'home' } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to) => {
  await ensureSession()
  if (to.meta.public) return true
  if (!authState.authenticated) {
    return { name: 'home', query: { tab: 'login', redirect: to.fullPath } }
  }
  if (to.meta.admin && !isAdmin.value) {
    return { name: 'home' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · Agent造剧` : 'Agent造剧'
})

export default router
