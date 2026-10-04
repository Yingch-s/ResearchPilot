<template>
  <div class="sr-app">
    <PrototypeShell :view="view" @navigate="navigate">
      <PrototypeHome v-if="view === 'home'" @open="navigate('quick')" />
      <PrototypeWorkspace v-else :initial-mode="view === 'deep' ? 'deep' : 'quick'" />
    </PrototypeShell>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PrototypeShell from '../components/prototype/PrototypeShell.vue'
import PrototypeHome from '../components/prototype/PrototypeHome.vue'
import PrototypeWorkspace from '../components/prototype/PrototypeWorkspace.vue'

const route = useRoute()
const router = useRouter()
const view = computed(() => route.params.view || 'home')
function navigate(next) { router.push(`/prototype/${next}`) }
</script>
