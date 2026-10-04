<template>
  <teleport to="body">
    <div class="rp-dialog-backdrop" @click.self="close">
      <section ref="panel" class="rp-dialog" role="dialog" aria-modal="true" :aria-labelledby="titleId" :aria-describedby="message ? messageId : undefined" :aria-busy="busy" tabindex="-1" @keydown="keydown">
        <header class="rp-dialog-head">
          <div>
            <div class="rp-dialog-kicker">RESEARCHPILOT</div>
            <h2 :id="titleId">{{ title }}</h2>
          </div>
          <button class="rp-dialog-close" type="button" aria-label="关闭对话框" :disabled="busy" @click="close">×</button>
        </header>
        <div class="rp-dialog-body">
          <p v-if="message" :id="messageId" class="rp-dialog-message">{{ message }}</p>
          <slot />
          <p v-if="error" class="error-text" role="alert">{{ error }}</p>
        </div>
        <footer class="rp-dialog-actions">
          <button v-if="showCancel" class="text dialog-cancel" type="button" :disabled="busy" @click="close">{{ cancelText }}</button>
          <button type="button" :class="danger ? 'dialog-danger' : 'dialog-primary'" :disabled="busy || disabled" @click="$emit('confirm')">{{ busy ? '处理中…' : confirmText }}</button>
        </footer>
      </section>
    </div>
  </teleport>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
const props = defineProps({ title: String, message: String, error: String, busy: Boolean, disabled: Boolean, danger: Boolean, confirmText: { type: String, default: '确认' }, showCancel: { type: Boolean, default: true }, cancelText: { type: String, default: '取消' } })
const emit = defineEmits(['close', 'confirm'])
const panel = ref(null)
const titleId = `dialog-${crypto.randomUUID()}`
const messageId = `${titleId}-message`
const trigger = document.activeElement
function close() { if (!props.busy) emit('close') }
function controls() { return [...panel.value.querySelectorAll('input:not(:disabled), textarea:not(:disabled), select:not(:disabled), button:not(:disabled), [tabindex="0"]')].filter(element => element.getClientRects().length) }
function keydown(event) {
  if (event.key === 'Escape' && !props.busy) { event.preventDefault(); emit('close') }
  if (event.key !== 'Tab') return
  const list = controls()
  const index = list.indexOf(document.activeElement)
  if (!list.length) { event.preventDefault(); panel.value.focus(); return }
  if (event.shiftKey && index <= 0) { event.preventDefault(); list.at(-1).focus() }
  else if (!event.shiftKey && (index < 0 || index === list.length - 1)) { event.preventDefault(); list[0].focus() }
}
onMounted(() => nextTick(() => (controls()[0] || panel.value)?.focus()))
onUnmounted(() => nextTick(() => { if (trigger?.isConnected) trigger.focus() }))
</script>
