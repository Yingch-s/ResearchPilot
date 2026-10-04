<template>
  <div class="settings-shell">
    <div class="eyebrow">WORKSPACE SETTINGS</div>
    <h1>设置</h1>

    <div class="panel">
      <div class="panel-head"><h3>服务状态</h3></div>
      <div class="panel-body">
        <div class="kv" v-for="s in services" :key="s.name">
          <b>{{ s.name }}</b>
          <span class="status" :class="s.state === 'unknown' ? 'warn' : (s.ok ? 'ok' : 'bad')" style="margin-left: 8px">{{ s.state === 'unknown' ? '未探测' : (s.ok ? '正常' : '不可用') }}</span>
        </div>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head"><h3>任务通知</h3></div>
      <div class="panel-body">
        <label class="notification-setting"><input v-model="systemNotifications" type="checkbox" @change="saveNotificationPreference" /> 研究任务完成或需要确认时使用系统通知</label>
        <p class="hint" style="margin-top: 8px">浏览器会在首次启用时请求通知权限；关闭后仍保留页面内通知。</p>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head"><h3>模型与联网服务</h3><span class="settings-restart">保存后重启后端生效</span></div>
      <div class="panel-body settings-form">
        <p class="hint">这些配置只保存在当前 ResearchPilot 实例的本地运行配置文件中。密钥不会回显到页面或接口响应。</p>
        <label>模型 API 地址<input v-model.trim="form.llm_base_url" type="url" placeholder="https://api.deepseek.com" /></label>
        <label>模型名称<input v-model.trim="form.llm_model" type="text" placeholder="deepseek-chat" /></label>
        <label>模型 API Key<input v-model="form.llm_api_key" type="password" autocomplete="new-password" :placeholder="settings.llm_api_key_configured ? `已配置 ${settings.llm_api_key_masked}，留空保持不变` : '输入模型服务 Key'" /></label>
        <label class="checkbox-row"><input v-model="form.clear_llm_api_key" type="checkbox" /> 清除已保存的模型 Key</label>
        <label>Tavily 联网搜索 API Key<input v-model="form.tavily_api_key" type="password" autocomplete="new-password" :placeholder="settings.tavily_api_key_configured ? `已配置 ${settings.tavily_api_key_masked}，留空保持不变` : '输入 Tavily Key（可选）'" /></label>
        <label class="checkbox-row"><input v-model="form.clear_tavily_api_key" type="checkbox" /> 清除已保存的联网搜索 Key</label>
        <label>解析 / Embedding 服务地址<input v-model.trim="form.sidecar_base_url" type="url" placeholder="http://localhost:8100" /></label>
        <div class="settings-actions"><button class="primary" :disabled="saving" @click="saveSettings">{{ saving ? '保存中…' : '保存配置' }}</button><span v-if="saveMessage" class="settings-message" :class="{ error: saveError }">{{ saveMessage }}</span></div>
        <p class="hint">为避免正在运行的研究前后使用不同模型，配置保存后需要重启后端服务。Docker 部署可执行 <code>docker compose restart backend</code>。</p>
      </div>
    </div>

    <div class="panel">
      <div class="panel-head"><h3>研究参数（当前默认值）</h3></div>
      <div class="panel-body">
        <div class="kv"><b>研究全局预算</b> {{ budget.timeout === '—' ? '未读取' : `${Math.round(Number(budget.timeout) / 60)} 分钟` }}</div>
        <div class="kv"><b>研究循环上限</b> {{ budget.loops }} 轮</div>
        <div class="kv"><b>无进展停止阈值</b> {{ budget.noProgress }} 轮</div>
        <div class="kv"><b>快速提问检索条数</b> 6 条</div>
        <p class="hint" style="margin-top: 8px">研究预算仍由部署配置控制；模型和联网服务配置可在上方保存。</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api.js'

const services = ref([
  { name: '后端 API（8080）', ok: false },
  { name: 'PostgreSQL', ok: false },
  { name: '检索服务', ok: false }
])
const budget = ref({ timeout: '—', loops: '—', noProgress: '—' })
const systemNotifications = ref(localStorage.getItem('researchpilot-system-notifications') === 'true')
const settings = ref({ llm_api_key_configured: false, tavily_api_key_configured: false, llm_api_key_masked: '', tavily_api_key_masked: '' })
const form = ref({ llm_base_url: '', llm_model: '', llm_api_key: '', clear_llm_api_key: false, tavily_api_key: '', clear_tavily_api_key: false, sidecar_base_url: '' })
const saving = ref(false)
const saveMessage = ref('')
const saveError = ref(false)

function copySettings(value) {
  settings.value = value
  form.value.llm_base_url = value.llm_base_url || ''
  form.value.llm_model = value.llm_model || ''
  form.value.sidecar_base_url = value.sidecar_base_url || ''
  form.value.llm_api_key = ''
  form.value.tavily_api_key = ''
  form.value.clear_llm_api_key = false
  form.value.clear_tavily_api_key = false
}

async function saveSettings() {
  saving.value = true
  saveMessage.value = ''
  saveError.value = false
  try {
    const result = await api.updateSettings(form.value)
    copySettings(result)
    saveMessage.value = '已保存。重启后端后生效。'
  } catch (error) {
    saveError.value = true
    saveMessage.value = error.message || '保存失败，请检查配置。'
  } finally {
    saving.value = false
  }
}

async function saveNotificationPreference() {
  if (systemNotifications.value && 'Notification' in window && Notification.permission === 'default') {
    const permission = await Notification.requestPermission()
    if (permission !== 'granted') systemNotifications.value = false
  }
  localStorage.setItem('researchpilot-system-notifications', String(systemNotifications.value))
}

onMounted(async () => {
  try {
    const [health, savedSettings] = await Promise.all([api.health(), api.settings()])
    copySettings(savedSettings)
    budget.value = {
      timeout: health.budget_timeout_seconds || '—',
      loops: health.budget_max_loop_visits || '—',
      noProgress: health.budget_no_progress_rounds || '—'
    }
    services.value = [
      { name: '后端 API（8080）', ok: true, state: 'ok' },
      { name: 'PostgreSQL', ok: health.database === 'ok', state: health.database },
      { name: '检索服务', ok: health.retrieval === 'ok', state: health.retrieval },
      { name: 'Sidecar 解析/Embedding', ok: health.sidecar === 'ok', state: health.sidecar }
    ]
  } catch {}
})
</script>
