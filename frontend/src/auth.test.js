import test from 'node:test'
import assert from 'node:assert/strict'
import { authState, login, register, logout } from './auth.js'

test('login 成功记录用户态', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => new Response(JSON.stringify({ username: 'alice', role: 'user' }), { status: 200 })
  try {
    authState.user = null
    const user = await login('alice', 'pw-123456')
    assert.equal(user.username, 'alice')
    assert.equal(authState.user.username, 'alice')
  } finally { globalThis.fetch = original }
})

test('login 401 抛出错误且不污染用户态', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => new Response(JSON.stringify({ error: '用户名或密码错误' }), { status: 401 })
  try {
    authState.user = null
    await assert.rejects(login('alice', 'wrong-pw'), error => error.status === 401 && error.message === '用户名或密码错误')
    assert.equal(authState.user, null)
  } finally { globalThis.fetch = original }
})

test('register 409 透传后端错误信息', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => new Response(JSON.stringify({ error: '用户名已存在' }), { status: 409 })
  try {
    await assert.rejects(register('alice', 'pw-123456'), error => error.status === 409 && error.message === '用户名已存在')
  } finally { globalThis.fetch = original }
})

test('logout 清空用户态（即使后端调用失败）', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => { throw new Error('network down') }
  try {
    authState.user = { username: 'alice' }
    await logout()
    assert.equal(authState.user, null)
  } finally { globalThis.fetch = original }
})
