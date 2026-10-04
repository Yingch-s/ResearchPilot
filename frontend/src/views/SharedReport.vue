<template>
  <main class="shared-report">
    <header class="shared-report-head">
      <div class="shared-brand"><span class="shared-mark">R</span><span>ResearchPilot</span></div>
      <span class="shared-label">只读研究报告</span>
    </header>
    <section v-if="loading" class="shared-state">正在加载报告…</section>
    <section v-else-if="error" class="shared-state shared-error"><h1>链接不可用</h1><p>{{ error }}</p></section>
    <article v-else class="shared-paper">
      <div class="shared-meta">{{ statusLabel }} · {{ report.completed_steps ?? 0 }}/{{ report.total_steps ?? 0 }} 个步骤完成</div>
      <h1>{{ report.query || '研究报告' }}</h1>
      <div class="md" v-html="html"></div>
      <p class="shared-foot">此页面为只读分享，不包含专题资料全文或批注。</p>
    </article>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { api } from '../api.js'

const route = useRoute()
const report = ref(null)
const loading = ref(true)
const error = ref('')
const html = computed(() => report.value ? DOMPurify.sanitize(marked.parse(report.value.report || '')) : '')
const statusLabel = computed(() => report.value?.status === 'PARTIAL_COMPLETED' ? '部分完成' : '已完成')

onMounted(async () => {
  try { report.value = await api.sharedReport(route.params.token) }
  catch (e) { error.value = e.status === 404 ? '分享已撤销、过期或不存在。' : (e.message || '无法加载报告。') }
  finally { loading.value = false }
})
</script>
