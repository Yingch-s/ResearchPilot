<template>
  <header class="sr-topbar">
    <button class="sr-brand" @click="$emit('navigate', 'home')"><span class="sr-brand-mark">R</span><span>ResearchPilot</span></button>
    <span class="sr-top-sep">/</span><span class="sr-top-context">北欧研究工作台</span>
    <span class="sr-fill"></span>
    <span class="sr-proto-label">研究工作台</span>
    <button class="sr-icon-button" title="设置">⚙</button>
  </header>
  <main class="sr-main"><slot /></main>
</template>

<script setup>
defineEmits(['navigate'])
</script>
