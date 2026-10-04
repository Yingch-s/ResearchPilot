<template>
  <div class="chat-answer">
    <div class="md" v-html="html" @click="followCitation" />
    <details v-if="citations.length" class="answer-sources" :open="citations.some(item => item.citation.sourceType === 'WEB')">
      <summary>{{ citations.length }} 处引用 <span>查看来源</span></summary>
      <article v-for="item in citations" :key="item.number" class="answer-source">
        <button class="answer-source-open" @click="$emit('citation', item.citation)">
          <span class="source-number">{{ item.number }}</span>
          <span class="source-copy"><strong>{{ item.citation.title || item.citation.paperTitle || item.citation.sectionPath || '原文片段' }}</strong><small>{{ item.citation.sourceType === 'WEB' ? '网页来源' : `第 ${item.citation.page ?? '?'} 页` }} · {{ (item.citation.text || '').slice(0, 120) }}</small></span>
        </button>
        <a v-if="item.citation.sourceType === 'WEB' && item.citation.url" :href="item.citation.url" target="_blank" rel="noopener noreferrer" class="web-source-link">{{ item.citation.url }} ↗</a>
        <button v-if="item.citation.sourceType === 'WEB' && item.citation.url" class="source-save" type="button"
          :disabled="savedSourceUrls.has(item.citation.url) || savingSourceUrls.has(item.citation.url)"
          @click="$emit('save-source', item.citation)">
          {{ savedSourceUrls.has(item.citation.url) ? '已保存' : savingSourceUrls.has(item.citation.url) ? '保存中…' : '保存到资料库' }}
        </button>
      </article>
    </details>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { md } from '../md.js'

const props = defineProps({ text: String, citations: { type: Array, default: () => [] }, savedSourceUrls: { type: Object, default: () => new Set() }, savingSourceUrls: { type: Object, default: () => new Set() } })
const emit = defineEmits(['citation', 'save-source'])
const citations = computed(() => {
  const seen = new Set()
  return (props.citations || []).map((citation, index) => ({ citation, number: index + 1 })).filter(({ citation }) => {
    const key = [citation.paperId || citation.paper_id || citation.title || '', citation.sectionPath || citation.section_path || '', citation.page ?? '', citation.text || ''].join('|')
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
})
const html = computed(() => {
  const doc = new DOMParser().parseFromString(md(props.text), 'text/html')
  // Preserve model-written bibliography content, but keep it out of the reading flow.
  if (props.citations.length) {
    const heading = [...doc.body.querySelectorAll(':scope > h1, :scope > h2, :scope > h3, :scope > h4')]
      .find(el => /^(参考文献|引用来源|References|Sources)$/i.test(el.textContent.trim()))
    if (heading && ![...doc.body.children].slice([...doc.body.children].indexOf(heading) + 1).some(el => /^H[1-4]$/.test(el.tagName))) {
      const details = doc.createElement('details')
      details.className = 'model-references'
      const summary = doc.createElement('summary')
      summary.textContent = heading.textContent
      details.append(summary)
      let sibling = heading.nextSibling
      while (sibling) {
        const next = sibling.nextSibling
        details.append(sibling)
        sibling = next
      }
      heading.replaceWith(details)
    }
  }
  const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT)
  const nodes = []
  while (walker.nextNode()) nodes.push(walker.currentNode)
  for (const node of nodes) {
    if (node.parentElement.closest('pre, code, a')) continue
    const pattern = /\[(\d+)\]/g
    const fragment = doc.createDocumentFragment()
    let end = 0
    for (const match of node.textContent.matchAll(pattern)) {
      const citation = props.citations[Number(match[1]) - 1]
      if (!citation) continue
      fragment.append(node.textContent.slice(end, match.index))
      const button = doc.createElement('button')
      button.className = 'inline-citation'
      button.dataset.citation = match[1]
      button.textContent = match[1]
      button.setAttribute('aria-label', `查看引用 ${match[1]}`)
      button.title = `${citation.sectionPath || '原文片段'} · 第 ${citation.page ?? '?'} 页\n${(citation.text || '').slice(0, 160)}`
      fragment.append(button)
      end = match.index + match[0].length
    }
    if (end) {
      fragment.append(node.textContent.slice(end))
      node.replaceWith(fragment)
    }
  }
  return doc.body.innerHTML
})
function followCitation(event) {
  const button = event.target.closest('[data-citation]')
  if (button) emit('citation', props.citations[Number(button.dataset.citation) - 1])
}
</script>
