<template>
  <div class="sr-workspace">
    <PrototypeSources />
    <section class="sr-panel sr-research">
      <header class="sr-research-head"><div><div class="sr-overline">NOTEBOOK / DENSE RETRIEVAL</div><h1>研究工作区</h1></div></header>
      <div class="sr-scope-bar"><span>当前资料范围</span><b>2 篇已选</b><span>·</span><button>查看范围</button></div>
      <PrototypeEntryCard v-model:mode="mode" />
    </section>
    <PrototypeHistory v-if="mode === 'quick'" />
    <PrototypeStudio v-else />
  </div>
</template>
<script setup>
import { ref } from 'vue'
import PrototypeSources from './PrototypeSources.vue'
import PrototypeHistory from './PrototypeHistory.vue'
import PrototypeEntryCard from './PrototypeEntryCard.vue'
import PrototypeStudio from './PrototypeStudio.vue'
const props = defineProps({ initialMode: { type: String, default: 'quick' } })
const mode = ref(props.initialMode)
</script>
