<template>
  <div class="sr-entry-card">
    <h1>今天想研究什么？</h1>
    <PrototypeModeSwitch :model-value="mode" @update:model-value="$emit('update:mode', $event)" />
    <textarea placeholder="例如：这篇论文的核心贡献是什么？"></textarea>
    <div class="sr-entry-scope">基于当前选中的资料回答，附带原文引用。</div>
    <button class="sr-entry-submit">{{ mode === 'deep' ? '开始深度研究' : '讨论快速提问' }} <span>→</span></button>
  </div>
</template>
<script setup>
import PrototypeModeSwitch from './PrototypeModeSwitch.vue'
defineProps({ mode: { type: String, default: 'quick' } })
defineEmits(['update:mode'])
</script>
