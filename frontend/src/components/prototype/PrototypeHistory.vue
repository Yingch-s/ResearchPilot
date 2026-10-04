<template>
  <aside class="sr-panel sr-history" :class="{ collapsed }"><div class="sr-panel-head"><div><div class="sr-overline">HISTORY</div><h2>对话</h2></div><button class="sr-plain-button" @click="collapsed = !collapsed">{{ collapsed ? '←' : '→' }}</button></div><template v-if="!collapsed"><button class="sr-new-chat">＋ 新建对话</button><div class="sr-history-list"><button v-for="(chat, i) in chats" :key="chat.title" class="sr-history-item" :class="{ active: i === active }" @click="active = i"><span class="sr-chat-dot"></span><span><strong>{{ chat.title }}</strong><small>{{ chat.count }} 个问题 · {{ chat.time }}</small></span></button></div><div class="sr-history-foot">历史对话属于当前研究专题。</div></template></aside>
</template>
<script setup>
import { ref } from 'vue'
const collapsed = ref(false); const active = ref(0)
const chats = [{ title: '论文核心方法比较', count: 4, time: '今天 14:20' }, { title: '训练数据与评价指标', count: 3, time: '昨天 18:05' }, { title: '先从 DPR 论文开始', count: 2, time: '9 月 30 日' }]
</script>
