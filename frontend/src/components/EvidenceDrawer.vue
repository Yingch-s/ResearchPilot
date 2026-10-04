<template>
  <teleport to="body">
    <div class="drawer-mask" @click="$emit('close')"></div>
    <div class="drawer">
      <div class="d-head">
        <div>
          <b style="font-size: 16px">{{ title || '证据详情' }}</b>
          <div class="kv">{{ sub }}</div>
        </div>
        <button class="d-close" @click="$emit('close')">✕</button>
      </div>

      <div v-for="e in items" :key="e.evidence_id" style="margin-bottom: 18px; border-bottom: 1px solid var(--border-subtle); padding-bottom: 14px">
        <div style="margin-bottom: 8px">
          <span class="status" :class="st(e.status)">{{ zh(e.status) }}</span>
        </div>
        <div v-if="e.claim_text" class="claim-box"><div class="kv"><b>归纳结论</b> · {{ e.claim_id }}</div><div class="quote claim-text">{{ e.claim_text }}</div></div>
        <div v-else class="quote">{{ e.value }}</div>
        <div class="kv"><b>原文摘录</b><span v-if="e.claim_text">（以下内容仅作为原文，不是翻译或改写）</span></div>
        <div class="quote" v-if="e.supporting_quote">{{ e.supporting_quote }}</div>
        <div class="kv"><b>来源</b> {{ e.source_type === 'WEB' ? '🌐 网页 · ' : '' }}{{ e.section_path || '—' }} · 第 {{ e.page ?? '?' }} 页</div>
        <div class="kv">
          <b>引用定位</b> {{ quoteZh(e.quote_status) }}
          <span style="margin-left: 10px"><b>结论支持</b> {{ claimZh(e.claim_status) }}</span>
        </div>
        <a v-if="e.source_type === 'WEB' && e.source_url" :href="e.source_url" target="_blank">
          <button class="ghost" style="margin-top: 6px">打开网页来源 →</button></a>
        <button v-else class="ghost" style="margin-top: 6px" @click="$emit('open-pdf', e)">在 PDF 中查看</button>
      </div>
    </div>
  </teleport>
</template>

<script setup>
defineProps({ title: String, sub: String, items: { type: Array, default: () => [] } })
defineEmits(['close', 'open-pdf'])

function st(s) {
  return { SUPPORTED: 'ok', READY: 'ok', ACCEPTED: 'ok', CONTRADICTED: 'bad', FAILED: 'bad' }[s] ||
    (s === 'NEEDS_REVIEW' ? 'warn' : '')
}
function zh(s) {
  return { SUPPORTED: '已支持', NEEDS_REVIEW: '待复核', CONTRADICTED: '存在反向证据', NOT_FOUND: '未找到', PARSE_UNCERTAIN: '解析不确定' }[s] || s
}
function quoteZh(s) {
  return { QUOTE_SUPPORTED: '已在来源中定位', QUOTE_WEAK: '定位较弱', QUOTE_NOT_FOUND: '无法准确定位' }[s] || '未记录'
}
function claimZh(s) {
  return { SUPPORTED: '支持', CONTRADICTED: '存在反向证据候选', INSUFFICIENT: '证据不足', NOT_EVALUATED: '待复核' }[s] || '待复核'
}
</script>
