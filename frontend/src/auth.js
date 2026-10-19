// P0-A 前端鉴权态：登录/注册/登出 + 全局 401 感知。
// 后端 RP_AUTH_MODE=off（默认）时所有接口不返回 401 —— 鉴权态保持未登录但不影响任何功能。

import { reactive } from 'vue'

export const authState = reactive({ user: null, checked: false })

/** 启动探测一次 /me：有会话则记录用户，无则保持未登录（不阻塞路由）。 */
export async function probeAuth() {
  try {
    const r = await fetch('/api/rp/auth/me')
    authState.user = r.ok ? await r.json() : null
  } catch {
    authState.user = null
  }
  authState.checked = true
  return authState.user
}

export async function login(username, password) {
  const r = await fetch('/api/rp/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const body = await r.json().catch(() => ({}))
  if (!r.ok) {
    const error = new Error(body.error || `登录失败 (${r.status})`)
    error.status = r.status
    throw error
  }
  authState.user = { username: body.username, role: body.role }
  return authState.user
}

export async function register(username, password) {
  const r = await fetch('/api/rp/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  })
  const body = await r.json().catch(() => ({}))
  if (!r.ok) {
    const error = new Error(body.error || `注册失败 (${r.status})`)
    error.status = r.status
    throw error
  }
  return body
}

export async function logout() {
  try {
    await fetch('/api/rp/auth/logout', { method: 'POST' })
  } catch {
    // 网络失败也照常清除本地会话态
  } finally {
    authState.user = null
  }
}
