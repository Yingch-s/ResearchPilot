<template>
  <div class="sr-mode-switch"><button :class="{ active: modelValue === 'quick' }" @click="$emit('update:modelValue', 'quick')">快速提问</button><button :class="{ active: modelValue === 'deep' }" @click="$emit('update:modelValue', 'deep')">深度研究</button></div>
</template>
<script setup>
defineProps({ modelValue: String })
defineEmits(['update:modelValue'])
</script>
