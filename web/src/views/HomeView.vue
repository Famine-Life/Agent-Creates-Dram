<script setup>
// 首页入口：按登录态决定展示项目列表还是访客认证与会员宣传
import { authState } from '../store/auth'
import ProjectsView from './ProjectsView.vue'
import GuestLanding from '../components/GuestLanding.vue'
</script>

<template>
  <!-- 已登录：首页展示项目列表；未登录：展示认证面板与会员升级宣传 -->
  <ProjectsView v-if="authState.authenticated" />
  <GuestLanding v-else />
</template>
