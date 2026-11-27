<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { state, resetCanvas } from './store/canvas'
import { clearAuth } from './store/auth'

const route = useRoute()
const router = useRouter()

/** 任意接口返回 401：清理会话与画布状态后回到首页登录面板 */
function onUnauthorized() {
  clearAuth()
  resetCanvas()
  if (route.name !== 'home') {
    router.push({ name: 'home', query: { tab: 'login', redirect: route.fullPath } })
  }
}

onMounted(() => window.addEventListener('aigc:unauthorized', onUnauthorized))
onBeforeUnmount(() => window.removeEventListener('aigc:unauthorized', onUnauthorized))
</script>

<template>
  <router-view />
  <!-- 画布页自带 toast，其余页面用这个全局提示位 -->
  <div v-if="route.name !== 'canvas' && state.toast" class="toast">{{ state.toast }}</div>
</template>
