<template>
  <div class="topbar" v-if="!isPrototype && !isShared">
    <router-link to="/" class="logo" style="text-decoration: none">
      <svg width="18" height="18" viewBox="0 0 18 18"><rect x="1" y="1" width="7" height="16" rx="2" fill="#176d5d"/><rect x="10" y="1" width="7" height="10" rx="2" fill="#176d5d" opacity="0.45"/></svg>
      ResearchPilot
    </router-link>
    <router-link v-if="route.path !== '/'" to="/" class="dashboard-back">← 研究专题</router-link>
    <span class="crumb"><slot name="crumb" /></span>
    <span class="spacer"></span>
    <router-link to="/settings" class="settings"><span class="settings-dot"></span>设置</router-link>
    <router-link v-if="auth.user" to="/login" class="settings login-chip" @click.prevent="doLogout">{{ auth.user.username }} · 退出</router-link>
    <router-link v-else-if="auth.checked && !isLogin" to="/login" class="settings">登录</router-link>
  </div>
  <router-view :key="route.path" />
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authState, probeAuth, logout } from './auth.js'
const route = useRoute()
const router = useRouter()
const auth = authState
const isPrototype = computed(() => String(route.path).startsWith('/prototype'))
const isShared = computed(() => String(route.path).startsWith('/shared/'))
const isLogin = computed(() => route.path === '/login')
onMounted(() => {
  probeAuth()
  // 会话失效（on 模式 401）→ 跳登录页并记住来源
  window.addEventListener('rp:unauthorized', () => {
    auth.user = null
    if (route.path !== '/login') router.push({ path: '/login', query: { redirect: route.fullPath } })
  })
})
async function doLogout() {
  await logout()
  router.push('/')
}
</script>
