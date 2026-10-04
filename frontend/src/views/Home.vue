<template>
  <div class="home-shell">
    <div class="home-hero">
      <div>
        <div class="eyebrow">RESEARCH WORKSPACE</div>
        <h1>你的研究专题</h1>
        <p>从资料出发，提问、比较并沉淀可追溯的研究成果。</p>
      </div>
      <button class="new-topic" @click="showNew = true"><span>＋</span> 新建专题</button>
    </div>

    <div class="home-toolbar">
      <div class="mode-switch"><button :class="{ active: !archived }" @click="setArchived(false)">专题</button><button :class="{ active: archived }" @click="setArchived(true)">已归档</button></div>
      <div class="search-wrap"><span>⌕</span><input type="text" v-model="q" placeholder="搜索专题" /></div>
      <span class="topic-count">{{ filtered.length }} 个专题</span>
    </div>

    <section v-if="activeRuns.length" class="active-runs" aria-labelledby="active-runs-title">
      <div class="section-line"><span id="active-runs-title">正在进行的研究</span><span>{{ activeRuns.length }} 个任务</span></div>
      <button v-for="r in activeRuns" :key="r.id" class="active-run" @click="$router.push({ path: `/notebooks/${r.topic_id}`, query: { run: r.id } })">
        <span class="run-pulse"></span><span class="active-run-copy"><strong>{{ r.query }}</strong><small>{{ runLabel(r.status) }} · 已完成 {{ r.execution_cursor || 0 }} 步</small></span><span>→</span>
      </button>
    </section>

    <div v-if="filtered.length" class="topic-grid">
      <div v-for="t in filtered" :key="t.id" class="topic-card" @click="$router.push(`/notebooks/${t.id}`)">
        <div class="topic-icon">{{ (t.name || '研').slice(0, 1) }}</div>
        <div class="topic-main">
          <div class="topic-title">{{ t.name }}</div>
          <div class="topic-goal">{{ t.research_goal || '还没有添加研究说明' }}</div>
          <div class="topic-meta"><span>{{ t.paper_count || 0 }} 篇资料</span><span>{{ readyCount(t.id) }} 篇已就绪</span></div>
        </div>
        <span class="topic-arrow">→</span>
        <div class="topic-actions" @click.stop>
          <button v-if="!archived" class="text" title="编辑专题" @click="editTopic(t)">编辑</button>
          <button v-if="!archived" class="text" title="归档专题" @click="confirmAction('archive', t)">归档</button>
          <button v-else class="text" title="恢复专题" @click="confirmAction('restore', t)">恢复</button>
          <button v-if="!Number(t.paper_count)" class="text danger-action" title="删除空专题" @click="confirmAction('delete', t)">删除</button>
        </div>
      </div>
    </div>
    <div v-else-if="loading" class="empty-state" role="status">正在加载专题…</div>
    <div v-else-if="loadError" class="empty-state" role="alert"><strong>{{ loadError }}</strong><button @click="loadTopics">重试</button></div>
    <div v-else-if="q || archived" class="empty-state"><strong>{{ q ? '没有匹配的专题' : '没有已归档专题' }}</strong><button v-if="q" class="ghost" @click="q = ''">清除搜索</button></div>
    <div v-else class="empty-state">
      <div class="empty-icon">＋</div><strong>还没有研究专题</strong><span>新建一个专题，上传论文后开始研究。</span><button @click="showNew = true">新建第一个专题</button>
    </div>

    <AppDialog v-if="showNew" title="新建研究专题" confirm-text="创建并进入" :busy="busy" :error="actionError" :disabled="!form.name.trim()" @close="closeDialogs" @confirm="create">
      <label for="topic-name">名称</label><input id="topic-name" class="rp-dialog-input" v-model="form.name" maxlength="160" @keydown.enter.prevent="create" />
      <label for="topic-goal">研究说明</label><input id="topic-goal" class="rp-dialog-input" v-model="form.goal" />
    </AppDialog>
    <AppDialog v-if="editing" title="编辑研究专题" confirm-text="保存" :busy="busy" :error="actionError" :disabled="!editForm.name.trim()" @close="closeDialogs" @confirm="saveTopic">
      <label for="edit-topic-name">名称</label><input id="edit-topic-name" class="rp-dialog-input" v-model="editForm.name" maxlength="160" />
      <label for="edit-topic-goal">研究说明</label><input id="edit-topic-goal" class="rp-dialog-input" v-model="editForm.goal" />
    </AppDialog>
    <AppDialog v-if="action" :title="actionTitle" :message="actionMessage" :confirm-text="actionTitle" :danger="action.type === 'delete'" :busy="busy" :error="actionError" @close="closeDialogs" @confirm="performAction" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppDialog from '../components/AppDialog.vue'
import { useRouter } from 'vue-router'
import { api } from '../api.js'

const router = useRouter()
const topics = ref([])
const archived = ref(false)
const loading = ref(false)
const loadError = ref('')
const busy = ref(false)
const actionError = ref('')
const action = ref(null)
let loadVersion = 0
const actionTitle = computed(() => ({ archive: '归档专题', restore: '恢复专题', delete: '删除空专题' }[action.value?.type]))
const actionMessage = computed(() => `“${action.value?.topic.name || ''}”${action.value?.type === 'delete' ? '将被永久删除。含有研究记录的专题须通过归档保留成果。' : action.value?.type === 'archive' ? '将移至已归档，资料与成果仍会保留。' : '将重新显示在专题列表。'}`)
function closeDialogs() { if (busy.value) return; showNew.value = false; editing.value = null; action.value = null; actionError.value = '' }
function confirmAction(type, topic) { actionError.value = ''; action.value = { type, topic } }
const q = ref('')
const showNew = ref(false)
const editing = ref(null)
const editForm = ref({ name: '', goal: '' })
const form = ref({ name: '', goal: '' })
const runs = ref([])
function isInternalRun(run) {
  const value = `${run?.query || ''} ${run?.thread_id || ''}`.trim().toLowerCase()
  return /^(测试查询|请求幂等测试|非法转移测试)$/.test(String(run?.query || '').trim())
    || /artifact-test|parallel-|it3-|iso-|e2e-|idempotency|真实链路冒烟|真实浏览器链路冒烟/.test(value)
}
function isInternalTopic(topic) {
  const name = String(topic?.name || '').trim().toLowerCase()
  const value = `${name} ${topic?.research_goal || ''}`.toLowerCase()
  // Test fixtures stay in the database for automated coverage, but are not
  // part of the user's notebook list. Keep this deliberately narrow so a
  // normal topic such as “实验设计” is never hidden just for being technical.
  return /^(e2e验证专题|e2e-\d+|quick idempotency|inspect-\d+|ping|rel|notes|x\d+|dbg-\d+|ask-\d+|memory-contract-\d+)$/.test(name)
    || /真实链路冒烟|真实浏览器链路冒烟|请求幂等测试|非法转移测试|artifact-test|parallel-|it3-|idempotency/.test(value)
}
const activeRuns = computed(() => runs.value
  .filter(r => !isInternalRun(r) && !['COMPLETED', 'PARTIAL_COMPLETED', 'FAILED', 'CANCELLED'].includes(r.status))
  .slice(0, 5))
function runLabel(status) {
  return { PLANNING: '整理计划', WAITING_APPROVAL: '等待确认', RESEARCHING: '研究中', VERIFYING: '校验引用', FINALIZING: '整理成果' }[status] || status
}

const filtered = computed(() =>
  topics.value
    .filter(t => !isInternalTopic(t))
    .filter(t => !q.value || (t.name || '').toLowerCase().includes(q.value.toLowerCase())))

function readyCount(topicId) {
  return (papersCache[topicId] || []).filter(p => p.parse_status === 'READY').length
}
const papersCache = reactive({})

async function create() {
  if (busy.value || !form.value.name.trim()) return
  busy.value = true; actionError.value = ''
  try { const t = await api.createTopic(form.value.name.trim(), form.value.goal.trim()); router.push(`/notebooks/${t.id}`) }
  catch (error) { actionError.value = error.message || '创建失败' }
  finally { busy.value = false }
}
function editTopic(topic) {
  actionError.value = ''
  editing.value = topic
  editForm.value = { name: topic.name || '', goal: topic.research_goal || '' }
}
async function saveTopic() {
  if (busy.value || !editForm.value.name.trim()) return
  busy.value = true; actionError.value = ''
  try {
    await api.updateTopic(editing.value.id, editForm.value.name.trim(), editForm.value.goal.trim())
    Object.assign(editing.value, { name: editForm.value.name.trim(), research_goal: editForm.value.goal.trim() })
    editing.value = null
  } catch (error) { actionError.value = error.message || '保存失败' }
  finally { busy.value = false }
}
async function performAction() {
  if (busy.value) return
  busy.value = true; actionError.value = ''
  try {
    const { type, topic } = action.value
    await ({ archive: api.archiveTopic, restore: api.restoreTopic, delete: api.deleteTopic }[type])(topic.id)
    topics.value = topics.value.filter(t => t.id !== topic.id)
    action.value = null
  } catch (error) { actionError.value = error.message || '操作失败，请重试' }
  finally { busy.value = false }
}
async function setArchived(value) { archived.value = value; await loadTopics() }
async function loadTopics() {
  const version = ++loadVersion
  loading.value = true; loadError.value = ''; topics.value = []
  try {
    const loaded = await api.topics(archived.value)
    if (version !== loadVersion) return
    topics.value = loaded
    for (const t of loaded) { try { papersCache[t.id] = await api.papers(t.id) } catch { papersCache[t.id] = [] } }
  } catch (error) { if (version === loadVersion) loadError.value = error.message || '专题加载失败' }
  finally { if (version === loadVersion) loading.value = false }
}

onMounted(async () => {
  await loadTopics()
  try { runs.value = (await api.runPage({ activeOnly: true, limit: 5 })).items } catch { runs.value = [] }
})
</script>
