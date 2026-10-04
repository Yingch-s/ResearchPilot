<template>
  <div class="workspace-splitter" role="separator" tabindex="0" aria-orientation="vertical"
    :aria-label="label" :aria-valuemin="min" :aria-valuemax="max" :aria-valuenow="Math.round(value)"
    :aria-valuetext="`${Math.round(value)} 像素`" :class="{ dragging }" title="拖动调整宽度；双击重置"
    @pointerdown="start" @pointermove="move" @pointerup="end" @pointercancel="end" @lostpointercapture="end"
    @keydown="keydown" @dblclick="$emit('reset')"><span></span></div>
</template>

<script setup>
import { ref } from 'vue'
const props = defineProps({ label: String, value: Number, min: Number, max: Number, reverse: Boolean })
const emit = defineEmits(['update:modelValue', 'reset'])
const dragging = ref(false)
let origin = 0
let width = 0
function update(value) { emit('update:modelValue', Math.max(props.min, Math.min(props.max, value))) }
function start(event) {
  if (event.button !== 0) return
  origin = event.clientX
  width = props.value
  event.currentTarget.setPointerCapture(event.pointerId)
  event.currentTarget.focus()
  dragging.value = true
  event.preventDefault()
}
function move(event) { if (dragging.value) update(width + (event.clientX - origin) * (props.reverse ? -1 : 1)) }
function end() { dragging.value = false }
function keydown(event) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End', 'Enter'].includes(event.key)) return
  event.preventDefault()
  if (event.key === 'Enter') { emit('reset'); return }
  if (event.key === 'Home') { update(props.min); return }
  if (event.key === 'End') { update(props.max); return }
  update(props.value + (event.key === 'ArrowRight' ? 1 : -1) * (props.reverse ? -1 : 1) * (event.shiftKey ? 40 : 10))
}
</script>
