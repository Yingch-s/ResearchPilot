<template>
  <teleport to="body">
    <div class="drawer-mask" @click="$emit('close')"></div>
    <div ref="panel" class="reader-drawer" role="dialog" aria-modal="true" aria-labelledby="reader-title" tabindex="-1" @keydown="keydown">
      <div class="r-head">
        <div style="min-width: 0">
          <b id="reader-title" style="font-size: 15px">{{ paper.title }}</b>
          <div class="kv"><span class="status" :class="pst(paper.parse_status)">{{ pzh(paper.parse_status) }}</span>
            · {{ isText ? '文本' : '第 ' + page + ' 页' }}</div>
        </div>
        <div class="row">
          <a :href="url + '?download=true'" download>下载原文件</a>
          <template v-if="kind === 'pdf'">
            <button class="ghost" :disabled="page <= 1" @click="page--">上一页</button>
            <button class="ghost" @click="page++">下一页</button>
          </template>
          <button class="d-close" aria-label="关闭阅读器" @click="$emit('close')">✕</button>
        </div>
      </div>
      <section class="reader-summary" aria-label="资料摘要与主题">
        <div class="reader-summary-head"><strong>摘要</strong><span v-if="paper.summary">原文摘要</span></div>
        <p v-if="paper.summary">{{ paper.summary }}</p>
        <p v-else class="hint">这份资料暂未提取到摘要，可在下方阅读原文。</p>
        <div v-if="paper.topics?.length" class="reader-topics"><strong>主题</strong><span v-for="topic in paper.topics" :key="topic">{{ topic }}</span></div>
      </section>
      <div v-if="highlightText" class="reader-evidence" role="note">
        <div class="reader-evidence-label">证据定位 · 原文摘录</div>
        <div class="reader-evidence-quote">{{ highlightText }}</div>
        <div class="reader-evidence-hint">以下内容来自资料原文；上方 PDF 保留页码定位，解析文本会优先显示匹配段落。</div>
      </div>
      <iframe v-if="url && kind === 'pdf'" :src="url + '#page=' + page" class="pdf-frame" title="PDF 原文"
        style="height: calc(100vh - 130px)"></iframe>
      <img v-else-if="kind === 'image'" :src="url" :alt="paper.title" class="reader-image" @error="error = '图片加载失败，可下载原文件'" />
      <div v-else class="pane-body reader-text">
        <p v-if="loading" role="status">加载中…</p>
        <p v-if="error" role="alert">{{ error }} <button class="text" @click="load()">重试</button></p>
        <pre v-if="kind === 'text'">{{ textContent }}</pre>
        <template v-else><p class="hint">解析文本不保留原文件版式；带标记的段落与证据摘录相匹配。</p><p v-for="(block, i) in blocks" :key="i" :class="{ 'reader-block-hit': isHighlightBlock(block.text) }"><span v-if="isHighlightBlock(block.text)" class="reader-block-marker">匹配</span>{{ block.text }}</p><button v-if="hasMore" class="ghost" :disabled="loading" @click="load(true)">继续阅读</button></template>
      </div>
      <p v-if="kind === 'image' && error" role="alert">{{ error }}</p>
    </div>
  </teleport>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { previewKind } from '../reader.js'

const props = defineProps({ paper: { type: Object, required: true }, versionId: String, fileExt: String, highlightText: { type: String, default: '' } })
const emit = defineEmits(['close'])
const panel = ref(null)
const trigger = document.activeElement
function keydown(event) {
  if (event.key === 'Escape') { event.preventDefault(); emit('close'); return }
  if (event.key !== 'Tab') return
  const controls = [...panel.value.querySelectorAll('a[href], button:not(:disabled), iframe')]
  const index = controls.indexOf(document.activeElement)
  if (!controls.length) return
  if (event.shiftKey && index <= 0) { event.preventDefault(); controls.at(-1).focus() }
  else if (!event.shiftKey && (index < 0 || index === controls.length - 1)) { event.preventDefault(); controls[0].focus() }
}
onMounted(() => nextTick(() => panel.value?.focus()))

const page = ref(1)
const url = ref('')
const kind = computed(() => previewKind(props.fileExt))
const isText = computed(() => kind.value !== 'pdf')
const textContent = ref('')
const blocks = ref([])
const hasMore = ref(false)
const loading = ref(false)
const error = ref('')
let revision = 0
let controller
async function load(more = false) {
  if (more && loading.value) return
  const current = ++revision
  controller?.abort()
  controller = new AbortController()
  error.value = ''
  if (!more) { page.value = 1; textContent.value = ''; blocks.value = []; hasMore.value = false }
  if (!props.versionId) return
  url.value = `/api/rp/versions/${props.versionId}/file`
  if (!['text', 'extracted'].includes(kind.value)) return
  loading.value = true
  try {
    const r = await fetch(kind.value === 'text' ? url.value : `/api/rp/versions/${props.versionId}/preview?offset=${blocks.value.length}`, { signal: controller.signal })
    if (!r.ok) throw new Error('内容暂不可用，可重试或下载原文件')
    const data = kind.value === 'text' ? await r.text() : await r.json()
    if (current !== revision) return
    if (kind.value === 'text') textContent.value = data
    else { blocks.value.push(...data.blocks); hasMore.value = data.has_more }
  } catch (e) { if (current === revision && e.name !== 'AbortError') error.value = e.message }
  finally { if (current === revision) loading.value = false }
}
watch(() => [props.versionId, props.fileExt], () => load(), { immediate: true })
onUnmounted(() => { revision++; controller?.abort(); nextTick(() => { if (trigger?.isConnected) trigger.focus() }) })
function pst(s) { return { READY: 'ok', FAILED: 'bad', PARTIAL: 'warn' }[s] || '' }
function pzh(s) { return { READY: '已就绪', FAILED: '处理失败', PARTIAL: '部分就绪' }[s] || '准备中' }
function normalized(value) { return String(value || '').replace(/\s+/g, ' ').trim().toLocaleLowerCase() }
function isHighlightBlock(value) {
  const quote = normalized(props.highlightText)
  const block = normalized(value)
  if (!quote || !block) return false
  const probe = quote.length > 240 ? quote.slice(0, 240) : quote
  return block.includes(probe) || (probe.length >= 48 && probe.split(' ').slice(0, 10).join(' ') && block.includes(probe.split(' ').slice(0, 10).join(' ')))
}
</script>

<style scoped>
.reader-image { display:block; width:100%; max-height:calc(100dvh - 130px); object-fit:contain; }
.reader-summary { flex:0 0 auto; margin:0 12px 8px; padding:10px 12px; border:1px solid var(--border-subtle); border-left:3px solid var(--brand); background:var(--brand-tint); }
.reader-summary-head { display:flex; align-items:center; gap:8px; color:var(--brand); font-size:12px; }
.reader-summary-head span { color:var(--text-muted); font-size:10px; font-weight:400; }
.reader-summary p { margin:6px 0 0; color:var(--text-primary); font-size:12px; line-height:1.55; white-space:pre-wrap; }
.reader-topics { display:flex; flex-wrap:wrap; align-items:center; gap:5px; margin-top:8px; color:var(--text-secondary); font-size:11px; }
.reader-topics span { padding:2px 7px; border:1px solid var(--border-subtle); border-radius:999px; background:var(--surface-panel); color:var(--brand); }
.reader-text { overflow-y:auto; height:calc(100dvh - 130px); padding:16px; font-size:13px; line-height:1.7; }
.reader-text pre { font:inherit; white-space:pre-wrap; overflow-wrap:anywhere; }
.reader-text p { white-space:pre-wrap; overflow-wrap:anywhere; }
.r-head a { font-size:12px; color:var(--brand); }
.reader-evidence { flex: 0 0 auto; margin: 0 0 8px; padding: 8px 11px; border: 1px solid var(--border-subtle); border-left: 3px solid var(--brand); background: var(--brand-tint); }
.reader-evidence-label { color: var(--brand); font-size: 10.5px; font-weight: 700; }
.reader-evidence-quote { max-height: 64px; margin-top: 3px; overflow: auto; color: var(--text-primary); font-size: 11.5px; line-height: 1.5; white-space: pre-wrap; }
.reader-evidence-hint { margin-top: 4px; color: var(--text-secondary); font-size: 10px; }
.reader-block-hit { position: relative; margin: 8px -5px !important; padding: 7px 8px 7px 38px; border-left: 2px solid var(--brand); background: var(--brand-tint); }
.reader-block-marker { position: absolute; left: 8px; top: 8px; color: var(--brand); font-size: 10px; font-weight: 700; }
</style>
