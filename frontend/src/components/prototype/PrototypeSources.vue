<template>
  <aside class="sr-panel sr-sources" :class="{ collapsed }">
    <div class="sr-panel-head"><div><div class="sr-overline">SOURCES</div><h2>资料 <small>{{ selectedCount }}/3</small></h2></div><button class="sr-plain-button" @click="collapsed = !collapsed">{{ collapsed ? '→' : '←' }}</button></div>
    <template v-if="!collapsed"><div class="sr-scope"><span class="sr-scope-dot"></span>本次研究使用 {{ selectedCount }} 篇已选资料</div><button class="sr-add-button">＋ 添加 PDF</button><div class="sr-source-list"><label v-for="paper in papers" :key="paper.name" class="sr-source-item"><input type="checkbox" v-model="paper.selected" /><span class="sr-paper-mark">{{ paper.mark }}</span><span class="sr-source-copy"><strong>{{ paper.name }}</strong><small>{{ paper.status }} · {{ paper.pages }} 页</small></span><span class="sr-open">↗</span></label></div><div class="sr-source-foot">范围只影响下一次提问或研究。</div></template>
  </aside>
</template>
<script setup>
import { computed, ref } from 'vue'
const collapsed = ref(false)
const papers = ref([{ mark: 'D', name: 'dpr_2004.04906', status: '已就绪', pages: 12, selected: true }, { mark: 'G', name: 'gnn_survey_2024', status: '已就绪', pages: 18, selected: true }, { mark: 'M', name: 'materials_review', status: '处理中', pages: 24, selected: false }])
const selectedCount = computed(() => papers.value.filter(p => p.selected).length)
</script>
