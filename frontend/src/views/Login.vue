<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-logo">
        <svg width="22" height="22" viewBox="0 0 18 18"><rect x="1" y="1" width="7" height="16" rx="2" fill="#176d5d"/><rect x="10" y="1" width="7" height="10" rx="2" fill="#176d5d" opacity="0.45"/></svg>
        <span>ResearchPilot</span>
      </div>
      <p class="login-sub">{{ mode === 'login' ? '登录以继续你的研究' : '创建新账号' }}</p>

      <form @submit.prevent="submit">
        <label class="login-field">
          <span>用户名</span>
          <input v-model.trim="username" type="text" autocomplete="username" required autofocus />
        </label>
        <label class="login-field">
          <span>密码</span>
          <input v-model="password" type="password" autocomplete="current-password" required minlength="6" />
        </label>

        <p v-if="error" class="login-error">{{ error }}</p>

        <button class="login-submit" type="submit" :disabled="busy">
          {{ busy ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>

      <p class="login-switch">
        {{ mode === 'login' ? '还没有账号？' : '已有账号？' }}
        <a href="#" @click.prevent="switchMode">{{ mode === 'login' ? '注册' : '去登录' }}</a>
      </p>
      <p class="login-back"><router-link to="/">← 先逛逛，不登录</router-link></p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { login, register } from '../auth.js'

const router = useRouter()
const route = useRoute()
const mode = ref('login')
const username = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  error.value = ''
}

async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    if (mode.value === 'register') {
      await register(username.value, password.value)
    }
    await login(username.value, password.value)
    router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/')
  } catch (e) {
    error.value = e.message || '操作失败'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
}
.login-card {
  width: 360px;
  background: var(--surface-raised);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-panel);
  padding: 32px 28px 24px;
}
.login-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
}
.login-sub {
  margin: 6px 0 22px;
  color: var(--text-secondary);
  font-size: 12px;
}
.login-field {
  display: block;
  margin-bottom: 14px;
}
.login-field span {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}
.login-field input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-control);
  font: inherit;
  background: var(--surface-app);
  color: var(--text-primary);
}
.login-field input:focus {
  outline: 2px solid var(--brand-tint);
  border-color: var(--brand);
}
.login-error {
  margin: 0 0 12px;
  padding: 8px 10px;
  border-radius: var(--radius-control);
  background: var(--danger-bg);
  color: var(--danger);
  font-size: 12px;
}
.login-submit {
  width: 100%;
  padding: 9px 0;
  border: none;
  border-radius: var(--radius-control);
  background: var(--brand);
  color: #fff;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.login-submit:hover { background: var(--brand-hover); }
.login-submit:disabled { opacity: 0.6; cursor: default; }
.login-switch, .login-back {
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
  text-align: center;
}
.login-switch a, .login-back a { color: var(--brand); text-decoration: none; }
</style>
