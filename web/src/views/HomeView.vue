<script setup>
import { authState } from '../store/auth'
import ProjectsView from './ProjectsView.vue'
import GuestLanding from '../components/GuestLanding.vue'
</script>

<template>
  <!-- 已登录：首页展示项目列表；未登录：展示认证面板与会员升级宣传 -->
  <ProjectsView v-if="authState.authenticated" />
  <GuestLanding v-else />
</template>
