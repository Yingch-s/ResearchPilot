<template>
  <section class="sr-home">
    <div class="sr-home-head">
      <div><div class="sr-overline">NOTEBOOKS / 02</div><h1>你的研究专题</h1><p>把资料、问题和证据放在同一个安静的工作空间。</p></div>
      <button class="sr-dark-button" @click="$emit('open')">＋ 新建研究专题</button>
    </div>
    <div class="sr-toolbar"><div class="sr-search">⌕ <span>搜索研究专题</span></div><span class="sr-muted">最近更新</span></div>
    <div class="sr-home-grid">
      <article v-for="topic in topics" :key="topic.name" class="sr-topic-row" @click="$emit('open')">
        <div class="sr-topic-symbol">{{ topic.symbol }}</div><div class="sr-topic-copy"><h2>{{ topic.name }}</h2><p>{{ topic.description }}</p><div class="sr-topic-meta">{{ topic.papers }} 篇资料 <span>·</span> {{ topic.activity }}</div></div><span class="sr-arrow">→</span>
      </article>
    </div>
    <div class="sr-home-note"><span class="sr-note-line"></span><span>研究专题承载资料、对话、研究任务和成果。</span></div>
  </section>
</template>

<script setup>
defineEmits(['open'])
const topics = [
  { symbol: 'D', name: 'Dense Retrieval', description: '跨文献对比密集检索方法', papers: 12, activity: '昨天有新对话' },
  { symbol: 'G', name: '图神经网络研究', description: '模型结构、训练数据与实验设置', papers: 8, activity: '2 个待审阅结果' },
  { symbol: 'M', name: 'Materials Research', description: '材料发现与实验方法综述', papers: 5, activity: '上周更新' }
]
</script>
