<template>
  <div ref="workspaceElement" class="workspace rp-live resizable-workspace" v-if="!focus">
    <button v-if="sourceMobile || studioMobile" class="workspace-panel-mask" aria-label="关闭侧栏" @click="closeMobilePanels"></button>
    <!-- ===== 左：资料 Sources ===== -->
    <div ref="sourcePanel" class="pane sources" :style="{ width: `${columnWidths.source}px`, flexBasis: `${columnWidths.source}px` }" :class="{ collapsed: srcCollapsed && !sourceMobile, 'mobile-visible': sourceMobile }" :role="sourceMobile ? 'dialog' : undefined" :aria-modal="sourceMobile || undefined" aria-label="资料" tabindex="-1" @keydown="mobilePanelKeydown($event, sourcePanel)" @click="srcCollapsed && (srcCollapsed = false)" @dragover.prevent="dragging = true" @dragleave.prevent="dragging = false" @drop.prevent="dropUpload">
      <div class="pane-head">
        <div><span class="section-kicker">SOURCES</span><span class="title">资料</span></div>
        <span class="meta">{{ selectedCount }}/{{ readyPapers.length }} 已选</span>
        <span class="spacer"></span>
        <button class="text mobile-close" aria-label="关闭资料面板" @click.stop="closeMobilePanels">×</button>
        <button class="text panel-collapse" :aria-label="srcCollapsed ? '展开资料面板' : '折叠资料面板'" :title="srcCollapsed ? '展开资料面板' : '折叠资料面板'" @click.stop="srcCollapsed = !srcCollapsed">{{ srcCollapsed ? '»' : '«' }}</button>
      </div>
      <div class="pane-body">
        <button class="upload-btn" :class="{ dragging }" @click="$refs.file.click()" :disabled="uploading">
          <span>＋</span>{{ uploading ? '上传中…' : '添加资料' }}
        </button>
        <input ref="file" type="file" accept="application/pdf,.txt,.md,.markdown,.docx,.pptx,.html,.png,.jpg,.jpeg" multiple style="display: none" @change="upload" />
        <div v-if="webSnapshots.length" class="web-source-list"><button v-for="snapshot in webSnapshots.slice(0, 3)" :key="snapshot.id" class="web-source-item" @click="openWebSnapshot(snapshot)"><span>WEB</span><strong>{{ snapshot.title || snapshot.url }}</strong><small>{{ fmt(snapshot.fetched_at) }} · {{ snapshot.content_length || 0 }} 字节</small></button></div>
        <div class="source-tools">
          <input v-model="sourceSearch" type="search" placeholder="筛选资料" aria-label="筛选资料" />
          <button class="text" @click="toggleAllSources">{{ selectedCount === readyPapers.length && readyPapers.length ? '取消全选' : '全选就绪' }}</button>
        </div>
        <div class="source-options">
          <select v-model="sourceSort" aria-label="资料排序"><option value="created">最近添加</option><option value="title">名称</option><option value="ready">就绪优先</option></select>
          <select v-model="sourceTag" aria-label="筛选资料标签"><option value="">所有标签</option><option v-for="tag in sourceTags" :key="tag" :value="tag">{{ tag }}</option></select>
          <label><input type="checkbox" v-model="sourceGrouped" /> 分组</label>
        </div>

        <!-- 上传/解析进度：真实流水线阶段 -->
        <div v-for="q in queue" :key="q.id" class="src-item" style="cursor: default">
          <div style="flex: 1; min-width: 0">
            <div class="t">{{ q.name }}</div>
            <div class="pbar"><div class="pbar-fill" :style="{ width: q.pct + '%' }"></div></div>
            <div class="s" :class="q.failed ? 'error-text' : ''">{{ q.stageText }}</div>
          </div>
          <button v-if="q.failed && q.file" class="text" title="重试上传" @click.stop="retryUpload(q)">重试</button>
          <button v-if="q.failed" class="text" title="关闭上传提示" aria-label="关闭上传提示" @click.stop="queue = queue.filter(item => item.id !== q.id)">×</button>
          <button v-if="q.uploadId && !q.failed && q.pct < 100" class="text" title="取消上传" aria-label="取消上传" @click.stop="cancelQueuedUpload(q)">×</button>
        </div>

        <template v-for="(p, index) in filteredPapers" :key="p.id">
        <div v-if="sourceGrouped && (!index || (paperMetadata(filteredPapers[index - 1]).category || '') !== (paperMetadata(p).category || ''))" class="source-group">{{ paperMetadata(p).category || '未分组' }}</div>
        <div class="src-item source-document" :class="{ disabled: p.parse_status !== 'READY' }">
          <input type="checkbox" :checked="selected.has(p.id)" v-if="p.parse_status === 'READY'"
            @click.stop @change="toggle(p.id)" />
          <div style="flex: 1; min-width: 0; cursor: pointer" @click="openReader(p)"
            :title="p.parse_status === 'READY' ? '打开阅读器' : ''">
            <div class="t" :title="p.title">{{ p.title }}</div>
            <div class="s source-meta"><span v-if="paperMetadata(p).category" class="source-category">{{ paperMetadata(p).category }}</span><span v-if="Array.isArray(paperMetadata(p).tags) && paperMetadata(p).tags.length">{{ paperMetadata(p).tags.join(' · ') }}</span></div>
            <template v-if="p.parse_status === 'READY'">
              <div class="s"><span class="status ok">已就绪</span> · 点击阅读查看摘要与原文</div>
            </template>
            <template v-else-if="['FAILED', 'PARTIAL'].includes(p.parse_status)">
              <div class="s error-text">{{ p.parse_status === 'PARTIAL' ? '部分解析，暂不可用于研究' : '解析失败' }}</div>
              <div class="s" :title="p.last_error">{{ p.last_error || '请重试解析或检查原文件' }}</div>
            </template>
            <template v-else>
              <div class="pbar"><div class="pbar-fill" :style="{ width: stagePct(p.ingest_stage) + '%' }"></div></div>
              <div class="s">{{ stageText(p.ingest_stage) }}</div>
            </template>
          </div>
          <div class="source-document-actions">
            <button v-if="['FAILED', 'PARTIAL'].includes(p.parse_status)" class="text" title="重新解析资料" aria-label="重新解析资料" :disabled="retryingPapers.has(p.id)" @click.stop="retryPaper(p)"><RotateCcw :size="13" /></button>
            <button v-if="p.parse_status === 'READY'" class="text" title="下载 BibTeX 引用" aria-label="下载 BibTeX 引用" @click.stop="downloadCitation(p)"><Download :size="13" /></button>
            <button class="text" title="编辑资料名称" aria-label="编辑资料名称" @click.stop="editPaper(p)"><Pencil :size="13" /></button>
            <button class="text" title="删除资料" aria-label="删除资料" @click.stop="del(p.id)"><Trash2 :size="13" /></button>
          </div>
        </div>
        </template>
        <div v-if="papers.length && !filteredPapers.length" class="empty">没有匹配的资料</div>
        <div v-if="!papers.length && !queue.length" class="empty">先添加几篇论文，ResearchPilot 才能依据原文回答。</div>
      </div>
    </div>

    <WorkspaceSplitter v-if="!srcCollapsed" label="资料栏宽度" :value="columnWidths.source" :min="220" :max="sourceMaxWidth" @update:model-value="sourceWidth = $event" @reset="sourceWidth = DEFAULT_COLUMNS.source" />
    <div v-else class="workspace-splitter-space"></div>

    <!-- ===== 中：研究 Research ===== -->
    <div class="pane research" :inert="sourceMobile || studioMobile">
      <div class="pane-head research-head">
        <div class="mobile-panel-actions">
          <button class="text" @click="openMobilePanel('source')">资料</button>
          <button class="text" @click="openMobilePanel('studio')">历史</button>
        </div>
        <span class="spacer"></span>
        <button class="text icon-tool layout-reset" title="重置栏宽" aria-label="重置栏宽" @click="resetColumns"><RotateCcw :size="13" /></button>
        <div class="research-title"><span class="section-kicker">NOTEBOOK</span><span>{{ topic.name }}</span></div>
      </div>

      <!-- 快速提问 -->
      <div class="pane-body chat-body"  ref="chatBody" @scroll="trackChatScroll">
        <div v-if="!msgs.length" class="research-entry-card">
          <h1>今天想研究什么？</h1>
          <textarea v-model="activeQuestion" :placeholder="mode === 'deep' ? '例如：比较这些论文的核心方法和实验设置' : '例如：这篇论文的核心贡献是什么？'" aria-label="研究问题" @keydown="submitActiveQuestion"></textarea>
          <div class="entry-scope">{{ mode === 'deep' ? '同一条对话中启动深度研究，过程和报告会持续显示。' : '基于当前选中的资料回答，附带原文引用。' }}</div>
          <div class="composer-switches">
            <label class="network-toggle"><input v-model="allowWeb" type="checkbox" /> 联网搜索</label>
            <label class="network-toggle"><input v-model="deepMode" type="checkbox" /> 深度研究 <small>生成报告</small></label>
          </div>
          <button class="entry-submit" :disabled="!activeQuestion.trim() || asking || planning || creating" @click="submitActiveQuestion">{{ mode === 'deep' ? '开始深度研究' : '讨论快速提问' }} <span>→</span></button>
        </div>
        <div v-for="(m, i) in msgs" :key="i" class="msg">
          <div class="q" v-if="m.role === 'user'">
            <span>{{ m.text }}</span>
            <button class="edit-question" title="编辑问题并创建分支" aria-label="编辑问题并创建分支" @click="editQuestion(i, m)">✎</button>
          </div>
          <div class="a" v-else :class="{ general: m.general }">
            <template v-if="m.isReport">
              <div style="font-weight: 700; margin-bottom: 6px">📄 深度研究报告已生成</div>
              <div class="md" v-html="mdHtml(m.text)"></div>
              <div class="hint" style="margin-top: 8px">报告已自动保存到左侧「研究报告」资料分类。</div>
            </template>
            <template v-else>
            <div v-if="messageSourceLabel(m)" class="hint" style="margin-bottom: 6px; color: var(--warning)">{{ messageSourceLabel(m) }}</div>
            <ChatAnswer :text="m.text" :citations="m.citations || []" :saved-source-urls="savedSourceUrls" :saving-source-urls="savingSourceUrls" @citation="openCitation" @save-source="saveCitationSource" />
            <div class="message-tools">
              <button class="text icon-tool" title="复制回答" aria-label="复制回答" @click="copyAnswer(m)"><Check v-if="copiedMessage === m" :size="14" /><Copy v-else :size="14" /></button>
              <button v-if="m.failed" class="text retry-answer" :disabled="asking" @click="ask(m)"><RotateCcw :size="13" /> 重试</button>
            </div>
            </template>
          </div>
        </div>
      <!-- 深度研究 -->
      <div class="research-in-chat" v-if="deepStage !== 'config' && (!run || String(run.session_id) === activeConversationId)">
        <!-- 配置 -->
        <div class="research-entry-card deep-entry-card" v-if="deepStage === 'config'">
          <h1>今天想研究什么？</h1>
          <textarea v-model="deepQ" placeholder="例如：比较这些论文的核心方法和实验设置" @keydown.enter.exact.prevent="genPlan()"></textarea>
          <div class="entry-scope">基于当前选中的资料开展研究，完成后生成 Markdown 研究报告。</div>
          <label class="network-toggle"><input v-model="allowWeb" type="checkbox" /> 资料不足时允许联网补充</label>
          <button class="advanced-toggle" type="button" @click="advancedOpen = !advancedOpen">{{ advancedOpen ? '收起高级约束' : '高级约束' }} <span>{{ advancedOpen ? '−' : '+' }}</span></button>
          <div v-if="advancedOpen" class="advanced-constraints">
            <label>输出长度<select v-model="constraints.output_length"><option value="balanced">平衡</option><option value="concise">简洁</option><option value="detailed">详细</option></select></label>
            <label>时间范围<input v-model="constraints.time_range" placeholder="例如：2020–2025，可留空" /></label>
            <label>来源限制<input v-model="constraints.source_limit" placeholder="例如：只看原始研究论文" /></label>
          </div>
          <button class="entry-submit" :disabled="!deepQ.trim() || !selectedCount || planning" @click="genPlan()">
            {{ planning ? '正在准备深度研究…' : '开始深度研究' }} <span>→</span>
          </button>
        </div>

        <!-- 澄清问题 -->
        <div class="deep-cfg" v-if="deepStage === 'clarify'">
          <div class="cfg-label">先弄清楚几件事，研究才能对准方向</div>
          <div v-for="(q, i) in clarifQs" :key="i" style="margin-bottom: 12px">
            <div style="font-size: 13px; margin-bottom: 4px">{{ i + 1 }}. {{ q }}</div>
            <textarea v-model="clarifAnswers[i]" rows="2" placeholder="你的回答（可留空）"></textarea>
          </div>
          <div class="row" style="margin-top: 14px">
            <button :disabled="planning" @click="submitClarifications">
              {{ planning ? '重新生成计划…' : '使用这些回答生成计划' }}
            </button>
            <button class="ghost" :disabled="planning" @click="skipClarification">跳过，使用默认设置</button>
            <button class="ghost" @click="deepStage = 'config'">返回修改问题</button>
          </div>
        </div>

        <!-- 计划确认：只展示用户需要决定的内容 -->
        <div class="deep-cfg compact-plan" v-if="deepStage === 'plan' && plan">
          <div class="cfg-label">我会这样研究</div>
          <div class="plan-summary">
            <div v-for="(s, i) in plan.steps.slice(0, 4)" :key="`${i}-${s}`" class="plan-summary-step"><span>{{ i + 1 }}</span><span>{{ s }}</span></div>
          </div>
          <div class="hint">使用 {{ plan.scope_papers?.length || selectedCount }} 篇资料<span v-if="allowWeb">，资料不足时联网补充</span>。完成后会生成 Markdown 研究报告并保存到左侧资料库。</div>
          <div class="row" style="margin-top: 14px">
            <button :disabled="creating" @click="confirmPlan">{{ creating ? '正在开始…' : '同意计划，开始研究' }}</button>
            <button class="ghost" @click="deepStage = 'config'">修改问题</button>
          </div>
        </div>

        <!-- 运行 -->
        <details v-if="deepStage === 'run' && run" class="research-activity" open>
          <summary><span>{{ researchPhase(run) }}</span><small>{{ ['COMPLETED','PARTIAL_COMPLETED'].includes(run.status) ? '已完成' : '查看进度' }}</small></summary>
          <div class="research-progress-card">
            <div class="research-progress-icon" :class="`progress-${stCls(run.status) || 'neutral'}`"><LoaderCircle v-if="!['COMPLETED','PARTIAL_COMPLETED','FAILED','CANCELLED'].includes(run.status)" :size="16" class="pending-icon" /><Check v-else :size="16" /></div>
            <div class="research-progress-copy"><strong>{{ researchPhase(run) }}</strong><span v-if="!['COMPLETED','PARTIAL_COMPLETED','FAILED','CANCELLED'].includes(run.status)">{{ currentResearchDetail(run) }} 可以离开页面，进度会持续保存。</span><span v-else-if="run.status === 'FAILED'">研究遇到问题，可以稍后重试。</span><span v-else>Markdown 报告已自动保存到左侧「研究报告」。</span></div>
          </div>
          <div v-if="run.status === 'RESEARCHING'" class="research-progress-actions"><button class="text" @click="pause">暂停</button><button class="text danger-action" @click="stop">停止</button></div>
          <div v-if="run.status === 'PAUSED'" class="research-progress-actions"><button class="text" @click="resume">继续研究</button></div>
        </details>
      </div>
        <div v-if="asking && askingConversationId === activeConversationId" class="answer-pending" role="status"><LoaderCircle :size="14" class="pending-icon" /> {{ allowWeb ? '正在搜索网页并整理回答…' : '正在阅读资料并整理回答…' }}</div>
      </div>
      <div v-if="deepStage === 'clarify' || deepStage === 'plan' || (deepStage === 'run' && run)" class="composer research-composer-locked">
        <span>{{ deepStage === 'clarify' ? '请完成或跳过澄清问题后继续。' : deepStage === 'plan' ? '研究计划已准备好，请先确认后开始。' : '研究正在进行，暂不接受普通问题。' }}</span>
        <button v-if="deepStage === 'run' && run" class="text" @click="newConversation">新建对话</button>
      </div>
      <div class="composer" v-else-if="msgs.length || deepStage !== 'config'">
        <button v-if="!chatAtBottom" class="jump-latest" @click="scrollChat"><ArrowDown :size="13" /> 最新消息</button>
        <div class="composer-box">
        <textarea ref="quickInput" v-model="activeQuestion" rows="1" placeholder="继续提问…" aria-label="继续提问" @input="resizeQuickInput" @keydown="submitActiveQuestion" />
        <div class="composer-toolbar">
        <div class="scope" v-if="selectedCount">
          <span class="dot-ok"></span> {{ selectedCount }} 篇已选资料
        </div>
        <div class="scope" v-else-if="readyPapers.length">
          <span class="dot-warn"></span> 全部 {{ readyPapers.length }} 篇资料
        </div>
        <div class="scope" v-else-if="allowWeb">
          <span class="dot-ok"></span> 联网搜索 · 将引用网页来源
        </div>
        <div class="scope" v-else>
          <span class="dot-warn"></span> 通用知识 · 无原文引用
        </div>
        <label class="network-toggle compact"><input v-model="allowWeb" type="checkbox" /> 联网搜索</label>
        <label class="network-toggle compact"><input v-model="deepMode" type="checkbox" /> 深度研究</label>
        <button v-if="asking" class="composer-send" title="取消等待（服务端可能仍在处理）" aria-label="取消等待" @click="cancelAnswer"><Square :size="14" /></button>
        <button v-else class="composer-send" title="发送问题" aria-label="发送问题" :disabled="!activeQuestion.trim() || planning || creating" @click="submitActiveQuestion"><ArrowUp :size="17" /></button>
        </div>
        </div>
      </div>


    </div>

    <!-- ===== 右：对话历史 ===== -->
    <WorkspaceSplitter v-if="!studioCollapsed" label="历史栏宽度" :value="columnWidths.studio" :min="220" :max="studioMaxWidth" reverse @update:model-value="studioWidth = $event" @reset="studioWidth = DEFAULT_COLUMNS.studio" />
    <div v-else class="workspace-splitter-space"></div>
    <div ref="studioPanel" class="pane studio" :style="{ width: `${columnWidths.studio}px`, flexBasis: `${columnWidths.studio}px` }" :class="{ collapsed: studioCollapsed && !studioMobile, 'mobile-visible': studioMobile }" :role="studioMobile ? 'dialog' : undefined" :aria-modal="studioMobile || undefined" aria-label="对话历史" tabindex="-1" @keydown="mobilePanelKeydown($event, studioPanel)" @click="studioCollapsed && (studioCollapsed = false)">
      <div class="pane-head">
        <div class="studio-tabs">
          <button :class="{ active: studioTab === 'history' }" @click.stop="studioTab = 'history'">对话</button>
        </div>
        <span class="spacer"></span>
        <button class="text mobile-close" aria-label="关闭历史面板" @click.stop="closeMobilePanels">×</button>
        <button class="text panel-collapse" :aria-label="studioCollapsed ? '展开对话历史' : '折叠对话历史'" :title="studioCollapsed ? '展开对话历史' : '折叠对话历史'" @click.stop="studioCollapsed = !studioCollapsed">{{ studioCollapsed ? '»' : '«' }}</button>
      </div>
      <div class="pane-body" v-if="false">
        <div v-if="savedArtifacts.length">
          <div class="art-card" v-for="artifact in savedArtifacts" :key="artifact.id" @click="openSavedArtifact(artifact)">
            <div class="a-title">{{ artifact.title }}</div>
            <div class="a-sub">
              <span class="status">{{ artifact.kind === 'REPORT' ? '研究报告' : '证据矩阵' }}</span> · {{ fmt(artifact.created_at) }}
            </div>
            <div class="row" style="margin-top: 8px">
              <button class="text" @click.stop="openSavedArtifact(artifact)">打开</button>
              <button class="text danger-action" @click.stop="removeSavedArtifact(artifact)">移除</button>
            </div>
          </div>
        </div>
        <div v-else-if="topicRuns.length" class="empty">研究完成后，报告会自动保存到左侧“研究报告”资料分类。</div>
        <div v-else-if="runListError" class="empty" role="alert">{{ runListError }}<button class="text" @click="loadRuns">重试</button></div>
        <div v-else class="empty">研究报告会自动归档到左侧资料库。</div>
        <button v-if="runsHasMore" class="ghost" :disabled="loadingMoreRuns" @click="loadMoreRuns">{{ loadingMoreRuns ? '加载中…' : '更早的研究' }}</button>
        <div v-if="runListError && topicRuns.length" class="error-text" role="alert">{{ runListError }}</div>
      </div>
      <div class="pane-body history-body" v-else-if="studioTab === 'history'">
        <button class="new-chat" @click="newConversation"><span>＋</span> 新建对话</button>
        <input v-model="historySearch" class="history-search" type="search" placeholder="搜索对话" aria-label="搜索对话" />
        <div v-if="conversations.length" class="history-list">
          <div v-for="c in filteredConversations" :key="c.id" class="history-item" :class="{ active: c.id === activeConversationId }" @click="switchConversation(c.id)">
            <div class="history-icon">◌</div>
            <div class="history-copy"><div class="history-title">{{ c.title }}</div><div class="history-meta">{{ c.messages.filter(m => m.role === 'user').length }} 个问题 · {{ fmt(c.updatedAt) }}</div></div>
            <button class="history-rename" title="重命名对话" @click.stop="renameConversation(c)">✎</button>
            <button class="history-delete" title="删除对话" @click.stop="deleteConversation(c.id)">×</button>
          </div>
        </div>
        <div v-else class="empty">你的提问会保存在这里。</div>
      </div>
      <div class="pane-body history-body" v-else-if="studioTab === 'notes'">
        <div v-if="notes.length" class="notes-list">
          <article v-for="note in notes" :key="note.id" class="note-card">
            <label class="note-select"><input type="checkbox" :checked="selectedNotes.has(note.id)" @change="toggleNoteForResearch(note.id)" /> 研究时使用</label>
            <strong>{{ note.favorite ? '★ ' : '' }}{{ note.title }}</strong><small>{{ fmt(note.updated_at) }}</small>
            <p>{{ note.body }}</p>
            <button v-if="note.run_id && note.evidence_id" class="text" @click="locateSavedNote(note)">定位证据</button>
            <button v-if="note.source_quote" class="text" @click="openSavedNote(note)">查看原文</button>
            <button class="text" @click="editSavedNote(note)">编辑</button>
            <button class="text" @click="toggleNoteFavorite(note)">{{ note.favorite ? '取消收藏' : '收藏' }}</button>
            <button class="text" @click="removeNote(note.id)">删除</button>
          </article>
        </div>
        <div v-else class="empty">从引用详情保存的笔记会出现在这里。</div>
      </div>
      <div class="pane-body history-body" v-else-if="studioTab === 'memory'">
        <div class="memory-add">
          <select v-model="memoryKind" aria-label="记忆类型"><option value="STABLE_PREFERENCE">稳定偏好</option><option value="CONFIRMED_FACT">确认事实</option></select>
          <textarea v-model="memoryDraft" rows="3" placeholder="只保存你明确确认的专题偏好或事实"></textarea>
          <button class="ghost" :disabled="!memoryDraft.trim() || memorySaving" @click="addProjectMemory">{{ memorySaving ? '保存中…' : '保存记忆' }}</button>
        </div>
        <div v-if="memories.length" class="notes-list">
          <article v-for="item in memories" :key="item.id" class="note-card"><small>{{ item.kind === 'STABLE_PREFERENCE' ? '稳定偏好' : '确认事实' }}</small><p>{{ item.content }}</p><button class="text" @click="removeProjectMemory(item.id)">移除</button></article>
        </div>
        <div v-else class="empty">当前专题还没有用户确认的记忆。</div>
      </div>
      <div class="pane-body history-body context-body" v-else-if="studioTab === 'context'">
        <div class="context-intro">勾选排除后，下一次回答不会装配这些内容。原始消息和研究成果仍会保留，可随时恢复。</div>
        <div v-if="contextBusy" class="empty">正在读取上下文…</div>
        <template v-else>
          <section class="context-section">
            <div class="context-section-title">最近对话</div>
            <label v-for="item in contextData.messages" :key="item.id" class="context-item">
              <input type="checkbox" :checked="!isContextExcluded('message_ids', item.id)" @change="toggleContext('message_ids', item.id, $event.target.checked)" />
              <span><b>{{ item.role === 'user' ? '你' : 'ResearchPilot' }}</b> · {{ item.content }}</span>
            </label>
            <div v-if="!contextData.messages.length" class="context-empty">暂无可选消息</div>
          </section>
          <section class="context-section">
            <div class="context-section-title">专题记忆</div>
            <label v-for="item in contextData.memories" :key="item.id" class="context-item">
              <input type="checkbox" :checked="!isContextExcluded('memory_ids', item.id)" @change="toggleContext('memory_ids', item.id, $event.target.checked)" />
              <span><b>{{ item.kind === 'STABLE_PREFERENCE' ? '偏好' : '确认事实' }}</b> · {{ item.content }}</span>
            </label>
            <div v-if="!contextData.memories.length" class="context-empty">暂无专题记忆</div>
          </section>
          <section class="context-section">
            <div class="context-section-title">研究状态</div>
            <label v-for="item in contextData.runs" :key="item.id" class="context-item">
              <input type="checkbox" :checked="!isContextExcluded('run_ids', item.id)" @change="toggleContext('run_ids', item.id, $event.target.checked)" />
              <span><b>{{ zhRun(item.status) }}</b> · {{ item.query }}</span>
            </label>
            <div v-if="!contextData.runs.length" class="context-empty">暂无关联研究任务</div>
          </section>
        </template>
      </div>
    </div>
  </div>

  <!-- ===== Focus View ===== -->
  <div class="focus" v-else>
    <div class="pane" style="width: 48px; flex: 0 0 48px; cursor: pointer" @click="closeFocus" title="返回研究">
      <div class="pane-head" style="justify-content: center"><b>»</b></div>
    </div>

    <div class="f-main">
      <div class="pane" style="flex: 1; display: flex; flex-direction: column">
        <div class="pane-head">
          <button class="text" @click="closeFocus">← 返回研究</button>
          <span class="title" style="max-width: 40%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">{{ focus.run.query }}</span>
          <span class="status" :class="stCls(focus.run.status)">{{ zhRun(focus.run.status) }}</span>
          <span class="spacer"></span>
          <button class="ghost" :class="{ on: focus.type === 'matrix' }" @click="focus.type = 'matrix'"
            :style="focus.type === 'matrix' ? 'color: var(--brand); border-color: var(--brand)' : ''">证据矩阵</button>
          <button class="ghost" @click="focus.type = 'report'"
            :style="focus.type === 'report' ? 'color: var(--brand); border-color: var(--brand)' : ''">研究报告</button>
          <button v-if="['COMPLETED', 'PARTIAL_COMPLETED'].includes(focus.run.status)" class="ghost" @click="continueFromGap">继续研究</button>
          <button v-if="focus.type === 'report' && ['COMPLETED', 'PARTIAL_COMPLETED'].includes(focus.run.status)" class="text" @click="createShare" :disabled="shareBusy">{{ shareBusy ? '生成中…' : '分享' }}</button>
          <button class="text export-action" title="导出当前内容" @click="exportFocus">导出</button>
        </div>

        <div class="pane-body" v-if="focus.type === 'matrix'" style="overflow: auto">
          <table class="matrix-table" v-if="matrix && matrix.cells">
            <thead>
              <tr><th>论文</th><th v-for="(t, fid) in matrix.fields" :key="fid">{{ t }}</th></tr>
            </thead>
            <tbody>
              <tr v-for="p in matrix.papers" :key="p">
                <td class="paper">{{ short(p) }}</td>
                <td v-for="(t, fid) in matrix.fields" :key="fid" class="cell" :class="{ empty: !cell(p, fid) }"
                  @click="cellClick(p, fid)">
                  <template v-if="cell(p, fid)">
                    {{ zh(cell(p, fid).status) }} <span class="cnt">({{ cell(p, fid).evidence.length }})</span>
                  </template>
                  <template v-else>未找到</template>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty">矩阵加载中…</div>
          <p class="hint" style="margin-top: 8px">
            未找到 = 当前未找到充分证据，不等于论文未报告 · 点击单元格查看证据详情
          </p>
        </div>

        <div class="pane-body" v-if="focus.type === 'report'" style="overflow: auto">
          <div v-if="reportVersions.length" class="report-version-bar">
            <label for="report-version">报告版本</label>
            <select id="report-version" v-model="selectedReportVersion" @change="selectReportVersion">
              <option v-for="version in reportVersions" :key="version.version" :value="String(version.version)">v{{ version.version }} · {{ fmt(version.created_at) }}</option>
            </select>
            <button v-if="previousReportVersion" class="text" @click="showReportDiff = !showReportDiff">{{ showReportDiff ? '返回报告' : '与上一版比较' }}</button>
            <span v-if="reportVersions.length > 1" class="hint">保留历史版本，当前版本不会覆盖旧报告</span>
            <span v-if="previousReportVersion && sourceDiff.added + sourceDiff.removed" class="hint source-diff-summary">来源 +{{ sourceDiff.added }} / -{{ sourceDiff.removed }}</span>
          </div>
          <div v-if="showReportDiff && previousReportVersion" class="report-diff" aria-live="polite">
            <div v-for="(line, index) in reportDiffLines" :key="index" :class="['report-diff-line', `report-diff-${line.kind}`]"><span>{{ line.marker }}</span><code>{{ line.text || ' ' }}</code></div>
          </div>
          <div v-else-if="report" class="md" v-html="mdHtml(report.report)"></div>
          <div v-else class="empty">报告尚未生成</div>
          <div v-if="shareLink" class="share-result" role="status">
            <div><strong>只读分享链接已生成</strong><span>链接只显示一次，复制后可发送给协作者。</span></div>
            <div class="share-link-row"><input :value="shareLink" readonly aria-label="只读分享链接" @focus="$event.target.select"><button class="ghost" @click="copyShareLink">{{ shareCopied ? '已复制' : '复制' }}</button><button class="text danger-action" @click="revokeShare">撤销</button></div>
          </div>
        </div>
      </div>
    </div>

    <div class="f-side">
      <div class="pane" v-if="!pdf" style="flex: 1">
        <div class="pane-head"><h3 style="margin: 0; font-size: 13px; font-weight: 700">证据详情</h3></div>
        <div class="pane-body">
          <div v-if="sideEvidence.length">
            <div v-for="e in sideEvidence" :key="e.evidence_id" style="margin-bottom: 14px; border-bottom: 1px solid var(--border-subtle); padding-bottom: 12px">
              <span class="status" :class="stCls(e.status)">{{ zh(e.status) }}</span>
               <div v-if="e.claim_text" class="claim-box"><div class="kv"><b>归纳结论</b> · {{ e.claim_id }}</div><div class="quote claim-text">{{ e.claim_text }}</div></div>
               <div v-else class="quote">{{ e.value }}</div>
               <div v-if="e.claim_text" class="kv"><b>原文摘录</b>（以下内容保持来源原文）</div>
               <div v-if="e.claim_text && e.supporting_quote" class="quote">{{ e.supporting_quote }}</div>
              <div class="kv"><b>来源</b> {{ e.section_path || '—' }} · 第 {{ e.page ?? '?' }} 页</div>
              <div class="kv"><b>来源类型</b> {{ e.source_type === 'WEB' ? '🌐 网页（补充来源）' : '📄 论文原文' }}</div>
              <div class="kv"><b>引用定位</b> {{ quoteZh(e.quote_status) }} · <b>结论支持</b> {{ claimZh(e.claim_status) }}</div>
              <div v-if="evidenceGap(e)" class="evidence-gap">
                <strong>{{ evidenceGap(e).title }}</strong>
                <span>{{ evidenceGap(e).body }}</span>
                <button class="text" @click="continueFromEvidence(e)">针对这个缺口继续研究 →</button>
              </div>
              <a v-if="e.source_type === 'WEB' && e.source_url" :href="e.source_url" target="_blank">
                <button class="ghost" style="margin-top: 6px">打开网页来源 →</button></a>
              <button v-else class="ghost" style="margin-top: 6px" @click="openPdf(e)">在 PDF 中查看</button>
            </div>
          </div>
          <div v-else class="empty">点击矩阵单元格查看证据</div>
        </div>
      </div>
      <div class="pane" v-else style="flex: 1; display: flex; flex-direction: column">
        <div class="pane-head">
          <button class="text" @click="pdf = null">← 返回证据</button>
          <span class="meta">第 {{ pdf.page ?? 1 }} 页</span>
        </div>
        <div style="flex: 1; padding: 0 12px 12px; display: flex; min-height: 0">
          <iframe class="pdf-frame" :src="pdf.url + '#page=' + (pdf.page || 1)"></iframe>
        </div>
      </div>
    </div>
  </div>

  <EvidenceDrawer v-if="drawer.open" :title="drawer.title" :sub="drawer.sub" :items="drawer.items"
    @close="drawer.open = false" @open-pdf="openPdfFromDrawer" />
  <ReaderDrawer v-if="reader.open" :paper="reader.paper" :version-id="reader.versionId"
    :file-ext="reader.fileExt" :highlight-text="reader.highlightText" @close="reader.open = false" />
  <AppDialog v-if="editingPaper" title="资料信息" confirm-text="保存" :busy="paperSaving" :error="paperEditError" :disabled="!paperForm.title.trim()" @close="closePaperEditor" @confirm="savePaper">
    <label for="paper-title">名称</label><input id="paper-title" class="rp-dialog-input" v-model="paperForm.title" maxlength="300" />
    <label for="paper-authors">作者</label><input id="paper-authors" class="rp-dialog-input" v-model="paperForm.authors" maxlength="500" />
    <label for="paper-category">分组</label><input id="paper-category" class="rp-dialog-input" v-model="paperForm.category" maxlength="80" />
    <label for="paper-tags">标签</label><input id="paper-tags" class="rp-dialog-input" v-model="paperForm.tags" placeholder="标签以逗号分隔" maxlength="1000" />
    <label for="paper-year">年份</label><input id="paper-year" class="rp-dialog-input" type="number" min="1500" max="2100" v-model="paperForm.year" />
    <label for="paper-doi">DOI</label><input id="paper-doi" class="rp-dialog-input" v-model="paperForm.doi" maxlength="300" />
    <p class="hint">{{ paperMetadata(editingPaper).provenance === 'USER_CONFIRMED' ? '人工确认' : '未人工确认' }}</p>
    <div class="row"><a :href="api.citationUrl(editingPaper.id, 'bibtex')" download>下载 BibTeX</a><a :href="api.citationUrl(editingPaper.id, 'ris')" download>下载 RIS</a></div>
  </AppDialog>
  <AppDialog v-if="noteEditor.open" :title="noteEditor.id ? '编辑笔记' : '保存引用笔记'" confirm-text="保存" :busy="noteEditor.busy" :error="noteEditor.error" :disabled="!noteEditor.body.trim()" @close="closeNoteEditor" @confirm="saveNote">
    <label for="note-title">标题</label><input id="note-title" class="rp-dialog-input" v-model="noteEditor.title" maxlength="160" />
    <label for="note-body">笔记</label><textarea id="note-body" class="rp-dialog-input note-editor-body" v-model="noteEditor.body" rows="6" maxlength="100000" />
    <p v-if="noteEditor.sourceQuote" class="quote note-editor-quote">{{ noteEditor.sourceQuote }}</p>
  </AppDialog>
  <AppDialog v-if="discoveryOpen" title="发现论文候选" confirm-text="关闭" :show-cancel="false" @close="discoveryOpen = false" @confirm="discoveryOpen = false">
    <p class="hint">候选不会自动加入资料范围。选择后打开 DOI 页面，确认后再手动保存或上传。</p>
    <div v-if="!discoveryCandidates.length" class="empty">没有找到候选，请换一个 DOI 或关键词。</div>
    <div v-for="candidate in discoveryCandidates" :key="candidate.doi || candidate.url || candidate.title" class="discovery-candidate">
      <strong>{{ candidate.title }}</strong><span>{{ candidate.authors || '作者未知' }} · {{ candidate.year || '年份未知' }}</span>
      <div class="discovery-actions"><a :href="candidate.url || `https://doi.org/${candidate.doi}`" target="_blank" rel="noreferrer">查看来源 →</a><button class="ghost" :disabled="candidateImporting.has(candidate.doi || candidate.url)" @click="importCandidate(candidate)">{{ candidateImporting.has(candidate.doi || candidate.url) ? '导入中…' : '导入资料' }}</button></div>
    </div>
  </AppDialog>
  <div v-if="toast" class="rp-toast" role="status"><span>{{ toast }}</span><button class="text" aria-label="关闭通知" @click="toast = ''">×</button></div>

  <AppDialog v-if="commandOpen" title="快速操作" message="用键盘或点击跳到研究工作区的常用位置。" confirm-text="关闭" :show-cancel="false" @close="commandOpen = false" @confirm="commandOpen = false">
    <div class="command-list" role="menu">
      <button type="button" class="command-item" @click="runCommand('input')"><span>聚焦问题输入框</span><kbd>Enter</kbd></button>
      <button type="button" class="command-item" @click="runCommand('sources')"><span>打开资料栏</span><kbd>S</kbd></button>
      <button type="button" class="command-item" @click="runCommand('history')"><span>打开历史对话</span><kbd>H</kbd></button>
      <button v-if="run && ['RESEARCHING', 'WAITING_APPROVAL'].includes(run.status)" type="button" class="command-item danger" @click="runCommand('stop')"><span>结束当前研究并保留成果</span><kbd>Esc</kbd></button>
    </div>
  </AppDialog>

  <AppDialog v-if="dialog.open" :title="dialog.title" :message="dialog.message"
    :confirm-text="dialog.confirmText" :danger="dialog.danger"
    :disabled="dialog.input && !dialog.value.trim()"
    @close="closeDialog(false)" @confirm="closeDialog(true)">
    <input v-if="dialog.input" v-model="dialog.value" class="rp-dialog-input"
      :placeholder="dialog.placeholder" aria-label="输入内容"
      @keydown.enter.prevent="dialog.value.trim() && closeDialog(true)" />
  </AppDialog>
  <AppDialog v-if="planAdjustment.open" title="计划调整提案" :confirm-text="planAdjustment.applying ? '应用中…' : canApplyPlanAdjustment ? '应用剩余步骤' : '关闭'" :busy="planAdjustment.applying" :disabled="!canApplyPlanAdjustment" :show-cancel="false" @close="planAdjustment.open = false" @confirm="canApplyPlanAdjustment ? applyPlanAdjustment() : (planAdjustment.open = false)">
    <div v-if="planAdjustment.reasons.length" class="plan-adjust-reasons">
      <div class="cfg-label">触发原因</div>
      <ul><li v-for="reason in planAdjustment.reasons" :key="reason">{{ reason }}</li></ul>
    </div>
    <div class="cfg-label">建议步骤 · {{ planAdjustment.steps.length }} 步</div>
    <ol class="plan-adjust-steps"><li v-for="(step, index) in planAdjustment.steps" :key="`${index}-${step}`"><span>{{ index + 1 }}</span>{{ step }}</li></ol>
    <p class="hint">只会替换尚未执行的步骤，已完成和正在执行的步骤会保留。应用后会写入新的计划版本。</p>
  </AppDialog>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ArrowUp, ArrowDown, Square, Copy, Check, RotateCcw, LoaderCircle, Download, Pencil, Trash2, X } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api.js'
import EvidenceDrawer from '../components/EvidenceDrawer.vue'
import { md as mdHtml } from '../md.js'
import ReaderDrawer from '../components/ReaderDrawer.vue'
import ChatAnswer from '../components/ChatAnswer.vue'
import AppDialog from '../components/AppDialog.vue'
import WorkspaceSplitter from '../components/WorkspaceSplitter.vue'
import { DEFAULT_COLUMNS, fitColumns } from '../workspaceLayout.js'
import { organizeSources, paperMetadata } from '../sources.js'
import { uploadResumable } from '../upload.js'
import { diffLines } from '../reportDiff.js'
import { moveStep, removeStep } from '../planSteps.js'

const route = useRoute()
const router = useRouter()
const topicId = route.params.topicId

// ---- 状态 ----
const topic = ref({})
const papers = ref([])
const selected = ref(new Set())
const sourceSearch = ref('')
const sourceSort = ref('created')
const sourceTag = ref('')
const sourceGrouped = ref(false)
const webUrl = ref('')
const discoveryQuery = ref('')
const discoveryCandidates = ref([])
const discoveryOpen = ref(false)
const discoveryBusy = ref(false)
const candidateImporting = ref(new Set())
const webSaving = ref(false)
const webSnapshots = ref([])
const savedSourceUrls = ref(new Set())
const savingSourceUrls = ref(new Set())
function isInternalFixturePaper(paper) {
  const title = String(paper?.title || '').trim().toLowerCase()
  const category = String(paperMetadata(paper)?.category || '').trim().toLowerCase()
  return category === 'test' || /^(test[-_ ]?note|非法转移测试|请求幂等测试|状态转移测试|内部验证专题)$/i.test(title)
}
const visiblePapers = computed(() => papers.value.filter(p => !isInternalFixturePaper(p)))
const sourceTags = computed(() => [...new Set(visiblePapers.value.flatMap(p => Array.isArray(paperMetadata(p).tags) ? paperMetadata(p).tags : []))].sort())
const editingPaper = ref(null)
const paperSaving = ref(false)
const paperEditError = ref('')
const paperForm = reactive({ title: '', authors: '', category: '', tags: '', year: '', doi: '' })
const mode = ref('quick')
const deepMode = computed({
  get: () => mode.value === 'deep',
  set: enabled => {
    if (enabled) upgradeQuickToDeep()
    else { mode.value = 'quick' }
  }
})
const srcCollapsed = ref(false)
const studioCollapsed = ref(false)
const sourceMobile = ref(false)
const studioMobile = ref(false)
const workspaceElement = ref(null)
const sourcePanel = ref(null)
const studioPanel = ref(null)
const sourceWidth = ref(DEFAULT_COLUMNS.source)
const studioWidth = ref(DEFAULT_COLUMNS.studio)
const workspaceWidth = ref(window.innerWidth - 28)
const columnWidths = computed(() => fitColumns(workspaceWidth.value, sourceWidth.value, studioWidth.value, srcCollapsed.value, studioCollapsed.value))
const sourceMaxWidth = computed(() => Math.max(220, Math.min(420, workspaceWidth.value - 400 - columnWidths.value.studio)))
const studioMaxWidth = computed(() => Math.max(220, Math.min(440, workspaceWidth.value - 400 - columnWidths.value.source)))
let layoutObserver
let mobilePanelTrigger
function resetColumns() { sourceWidth.value = DEFAULT_COLUMNS.source; studioWidth.value = DEFAULT_COLUMNS.studio }
function openMobilePanel(which) {
  mobilePanelTrigger = document.activeElement
  sourceMobile.value = which === 'source'
  studioMobile.value = which === 'studio'
  nextTick(() => (which === 'source' ? sourcePanel.value : studioPanel.value)?.querySelector('.mobile-close')?.focus())
}
function closeMobilePanels() {
  sourceMobile.value = false
  studioMobile.value = false
  nextTick(() => mobilePanelTrigger?.isConnected && mobilePanelTrigger.focus())
}
function mobilePanelKeydown(event, panel) {
  if (!sourceMobile.value && !studioMobile.value) return
  if (event.key === 'Escape') { event.preventDefault(); closeMobilePanels(); return }
  if (event.key !== 'Tab') return
  const controls = [...panel.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href], textarea:not(:disabled)')].filter(el => el.getClientRects().length)
  const index = controls.indexOf(document.activeElement)
  if (!controls.length) { event.preventDefault(); panel.focus(); return }
  if (event.shiftKey && index <= 0) { event.preventDefault(); controls.at(-1).focus() }
  else if (!event.shiftKey && (index < 0 || index === controls.length - 1)) { event.preventDefault(); controls[0].focus() }
}
watch(workspaceElement, element => {
  layoutObserver?.disconnect()
  if (!element) return
  layoutObserver = new ResizeObserver(entries => {
    workspaceWidth.value = entries[0].contentRect.width
    if (window.innerWidth >= 1024 && (sourceMobile.value || studioMobile.value)) closeMobilePanels()
  })
  layoutObserver.observe(element)
}, { flush: 'post' })
onUnmounted(() => layoutObserver?.disconnect())
const reader = reactive({ open: false, paper: {}, versionId: '', fileExt: '', highlightText: '' })

// 上传队列（本地进度）
const queue = ref([]) // [{name, since}]
const uploading = ref(false)
const retryingPapers = ref(new Set())
const dragging = ref(false)

// 快速提问
const msgs = ref([])
const quickQ = ref('')
const allowWeb = ref(false)
const asking = ref(false)
const askingConversationId = ref('')
const chatBody = ref(null)
const quickInput = ref(null)
const chatAtBottom = ref(true)
const copiedMessage = ref(null)
let answerController
const studioTab = ref('history')
const conversations = ref([])
const activeConversationId = ref('')
const historySearch = ref('')
const filteredConversations = computed(() => conversations.value.filter(c => !historySearch.value.trim() || c.title.toLowerCase().includes(historySearch.value.trim().toLowerCase())))
const suggestions = ['这几篇论文的核心方法分别是什么？', '它们用了哪些数据集？', '各自的局限是什么？']

// 深度研究
const deepStage = ref('config')
const deepQ = ref('')
const activeQuestion = computed({
  get: () => quickQ.value,
  set: value => { quickQ.value = value }
})
const dims = ['核心方法', '训练需求', '实验数据集', '评价指标', '研究局限', '可复现性', '理论基础']
const plan = ref(null)
const clarifQs = ref([])
const clarifAnswers = ref([])
const recentEvidence = ref([])
const runEvents = ref([])
const runReturnSummary = ref('')
const runUnreadCount = ref(0)
let prevStatus = ''
const toast = ref('')
const planDimInput = ref('')
const planEdit = ref('')
const planDrag = ref(null)
const planning = ref(false)
const creating = ref(false)
const run = ref(null)
const runPlanVersions = ref([])
const selectedPlanVersion = ref('')
const planVersionDiff = computed(() => {
  const index = runPlanVersions.value.findIndex(version => String(version.version) === String(selectedPlanVersion.value))
  if (index <= 0) return []
  return diffLines(planVersionSteps(runPlanVersions.value[index - 1]), planVersionSteps(runPlanVersions.value[index]))
})
const requirements = ref([])
const planAdjustment = reactive({ open: false, loading: false, applying: false, steps: [], reasons: [] })
const canApplyPlanAdjustment = computed(() => ['RESEARCHING', 'WAITING_APPROVAL'].includes(run.value?.status))
const requirementInput = ref('')
const requirementSaving = ref(false)
const deepClientRequestId = ref('')
const advancedOpen = ref(false)
const constraints = reactive({ output_length: 'balanced', time_range: '', source_limit: '' })
const assumptionsAccepted = ref(false)
const draftKey = `researchpilot-draft-${topicId}`
let serverDraftTimer
let switchingConversation = false
let disposed = false
let routeRevision = 0
let workspaceReady = false
const commandOpen = ref(false)
const dialog = reactive({ open: false, title: '', message: '', input: false, value: '', placeholder: '', confirmText: '确认', danger: false, resolve: null })

// Studio / Focus
const focus = ref(null)
const matrix = ref(null)
const report = ref(null)
const savedArtifacts = ref([])
const savingArtifact = ref(false)
const reportVersions = ref([])
const shareBusy = ref(false)
const shareCopied = ref(false)
const shareLink = ref('')
const shareId = ref('')
const selectedReportVersion = ref('')
const showReportDiff = ref(false)
const previousReportVersion = computed(() => {
  const index = reportVersions.value.findIndex(version => String(version.version) === String(selectedReportVersion.value))
  return index > 0 ? reportVersions.value[index - 1] : null
})
const reportDiffLines = computed(() => {
  if (!previousReportVersion.value) return []
  return diffLines(previousReportVersion.value.report, report.value?.report)
})
function versionSources(version) {
  if (!version) return []
  if (Array.isArray(version.source_ids)) return version.source_ids
  try { const parsed = JSON.parse(version.source_ids || '[]'); return Array.isArray(parsed) ? parsed : [] } catch { return [] }
}
const sourceDiff = computed(() => {
  const before = new Set(versionSources(previousReportVersion.value))
  const current = new Set(versionSources(reportVersions.value.find(version => String(version.version) === String(selectedReportVersion.value))))
  return { added: [...current].filter(id => !before.has(id)).length, removed: [...before].filter(id => !current.has(id)).length }
})
const sideEvidence = ref([])
const notes = ref([])
const selectedNotes = ref(new Set())
const memories = ref([])
const contextBusy = ref(false)
const contextData = reactive({ messages: [], memories: [], runs: [], exclusions: { message_ids: [], memory_ids: [], run_ids: [] } })
const memoryKind = ref('STABLE_PREFERENCE')
const memoryDraft = ref('')
const memorySaving = ref(false)
const noteEditor = reactive({ open: false, id: '', title: '', body: '', runId: '', evidenceId: '', sourceQuote: '', busy: false, error: '' })
const pdf = ref(null)
const drawer = reactive({ open: false, title: '', sub: '', items: [] })

function upgradeQuickToDeep() {
  mode.value = 'deep'
}

const readyPapers = computed(() => visiblePapers.value.filter(p => p.parse_status === 'READY'))
const filteredPapers = computed(() => organizeSources(visiblePapers.value, { search: sourceSearch.value, tag: sourceTag.value, sort: sourceSort.value, group: sourceGrouped.value }))
const selectedCount = computed(() => [...selected.value].filter(id => visiblePapers.value.some(p => String(p.id) === String(id))).length)
const topicRuns = computed(() => runsAll.value.filter(r => r.topic_id === topicId))
const runsAll = ref([])
const runsCursor = ref(null)
const runsHasMore = ref(false)
const loadingMoreRuns = ref(false)
const runListError = ref('')
let runListVersion = 0
const knownPaperIds = ref(new Set())

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
function notifySystem(title, body) {
  try {
    if (localStorage.getItem('researchpilot-system-notifications') !== 'true' || !('Notification' in window) || Notification.permission !== 'granted') return
    new Notification(title, { body, tag: `researchpilot-${topicId}` })
  } catch { /* notification APIs can be unavailable or denied */ }
}
const short = (s) => (s === '__web__' ? '🌐 网络来源' : String(s || '').slice(0, 8))
const conversationKey = `researchpilot-conversations-${topicId}`

function closeDialog(confirmed) {
  if (!dialog.open) return
  const resolve = dialog.resolve
  dialog.open = false
  dialog.resolve = null
  resolve?.(confirmed ? (dialog.input ? dialog.value.trim() : true) : null)
}
function openCommand(event) {
  if (!((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k')) return
  event.preventDefault()
  commandOpen.value = true
}
function runCommand(action) {
  commandOpen.value = false
  if (action === 'input') nextTick(() => quickInput.value?.focus())
  else if (action === 'sources') { sourceMobile.value = true; studioMobile.value = false; srcCollapsed.value = false }
  else if (action === 'history') { studioTab.value = 'history'; studioMobile.value = true; sourceMobile.value = false; studioCollapsed.value = false }
  else if (action === 'artifacts') { studioTab.value = 'artifacts'; studioMobile.value = true; sourceMobile.value = false; studioCollapsed.value = false }
  else if (action === 'stop') stop()
}
function showDialog(options) {
  if (dialog.open) closeDialog(false)
  Object.assign(dialog, { open: true, title: '', message: '', input: false, value: '', placeholder: '', confirmText: '确认', danger: false, ...options })
  return new Promise(resolve => {
    dialog.resolve = resolve
  })
}
function saveDraft() {
  if (switchingConversation) return
  const payload = { quickQ: quickQ.value, deepQ: deepQ.value, mode: mode.value, allowWeb: allowWeb.value, selectedNoteIds: [...selectedNotes.value], clarifQs: clarifQs.value, clarifAnswers: clarifAnswers.value, planEdit: planEdit.value, planDimInput: planDimInput.value, deepStage: deepStage.value, plan: plan.value, assumptionsAccepted: assumptionsAccepted.value, advancedOpen: advancedOpen.value, constraints: { ...constraints } }
  const sessionId = activeConversationId.value
  const current = conversations.value.find(c => c.id === sessionId)
  if (current) current.draft = payload
  try {
    localStorage.setItem(draftKey, JSON.stringify(payload))
  } catch { /* storage can be unavailable in private browsing */ }
  clearTimeout(serverDraftTimer)
  serverDraftTimer = setTimeout(() => {
    if (sessionId) api.saveDraft(topicId, sessionId, payload).catch(() => {})
  }, 700)
}
function restoreDraft() {
  try {
    const draft = JSON.parse(localStorage.getItem(draftKey) || 'null')
    if (!draft) return
    if (typeof draft.quickQ === 'string') quickQ.value = draft.quickQ
    if (typeof draft.deepQ === 'string') deepQ.value = draft.deepQ
    if (typeof draft.allowWeb === 'boolean') allowWeb.value = draft.allowWeb
    if (['quick', 'deep'].includes(draft.mode)) mode.value = draft.mode
    if (Array.isArray(draft.clarifQs)) clarifQs.value = draft.clarifQs
    if (Array.isArray(draft.clarifAnswers)) clarifAnswers.value = draft.clarifAnswers
    if (typeof draft.planEdit === 'string') planEdit.value = draft.planEdit
    if (typeof draft.planDimInput === 'string') planDimInput.value = draft.planDimInput
    if (Array.isArray(draft.selectedNoteIds)) selectedNotes.value = new Set(draft.selectedNoteIds)
    if (draft.plan && typeof draft.plan === 'object') plan.value = draft.plan
    if (['config', 'clarify', 'plan'].includes(draft.deepStage) && (draft.deepQ || draft.plan)) deepStage.value = draft.deepStage
  } catch { /* ignore malformed stale drafts */ }
}

// ---- 状态文案 ----
function zhRun(s) {
  return { PLANNING: '正在整理研究计划', WAITING_APPROVAL: '需要你确认后继续', RESEARCHING: '正在查找论文证据',
    VERIFYING: '正在检查引用', FINALIZING: '正在整理研究报告', COMPLETED: '研究已完成',
    PARTIAL_COMPLETED: '部分完成', FAILED: '研究未完成', CANCELLED: '研究已结束' }[s] || s
}
function pzh(s) {
  return { READY: '已就绪', FAILED: '处理失败', PARTIAL: '部分就绪' }[s] || '准备中'
}
function pst(s) { return { READY: 'ok', FAILED: 'bad', PARTIAL: 'warn' }[s] || '' }
function stCls(s) {
  return { COMPLETED: 'ok', READY: 'ok', SUPPORTED: 'ok', FAILED: 'bad', CONTRADICTED: 'bad', SKIPPED: 'bad',
    NEEDS_REVIEW: 'warn', RUNNING: 'warn', WAITING_APPROVAL: 'warn', PARTIAL_COMPLETED: 'warn' }[s] || ''
}
function zh(s) {
  return { SUPPORTED: '已支持', NEEDS_REVIEW: '待复核', CONTRADICTED: '存在反向证据', NOT_FOUND: '未找到' }[s] || s
}
function quoteZh(s) {
  return { QUOTE_SUPPORTED: '已定位', QUOTE_WEAK: '定位较弱', QUOTE_NOT_FOUND: '无法准确定位' }[s] || '未记录'
}
function claimZh(s) {
  return { SUPPORTED: '支持', CONTRADICTED: '存在反向证据', INSUFFICIENT: '证据不足', NOT_EVALUATED: '待复核' }[s] || '待复核'
}
function runGuidance(status) {
  return {
    PLANNING: { title: '正在理解研究问题', body: '系统会先整理范围和步骤，完成后会显示需要你确认的计划。' },
    WAITING_APPROVAL: { title: '需要你确认研究计划', body: '检查资料范围和步骤后批准，研究才会开始。' },
    RESEARCHING: { title: '正在查找和验证证据', body: '可以离开页面；已完成的步骤和证据会持续保存。' },
    VERIFYING: { title: '正在检查引用可靠性', body: '系统正在核对原文定位、支持关系和可能的反向证据。' },
    FINALIZING: { title: '正在整理研究报告', body: '报告会保留已完成内容，并标记仍待核验或未完成的部分。' },
    COMPLETED: { title: '研究已完成', body: '可以打开研究报告，或针对具体缺口继续研究。' },
    PARTIAL_COMPLETED: { title: '研究部分完成', body: '已有内容已保存到研究报告；建议先查看未完成步骤，再决定是否重试或续研。' },
    FAILED: { title: '研究未完成', body: '已完成的成果仍然保留，可以重试失败步骤或调整目标后创建分支。' },
    CANCELLED: { title: '研究已结束', body: '已保存停止前的成果；如需继续，可从证据缺口发起续研。' }
  }[status] || { title: '研究状态已更新', body: '可展开进展时间线查看最近发生的变化。' }
}
function evidenceGap(e) {
  if (!e) return null
  if (e.claim_status === 'CONTRADICTED' || e.status === 'CONTRADICTED') return { title: '存在反向证据', body: '原文支持与当前结论相反的方向，继续研究时应补充条件、范围或冲突来源。' }
  if (e.claim_status === 'INSUFFICIENT' || e.status === 'NEEDS_REVIEW') return { title: '只有相邻证据', body: '来源与问题相关，但尚未直接证明这条结论，建议补充同一指标或同一场景的原文。' }
  if (e.status === 'NOT_FOUND') return { title: '没有直接证据', body: '当前资料中尚未找到可定位的原文，不代表论文没有报告该内容。' }
  if (e.quote_status === 'QUOTE_WEAK' || e.quote_status === 'QUOTE_NOT_FOUND') return { title: '引用定位较弱', body: '结论可能相关，但原文位置不够精确，建议继续查找可直接引用的段落。' }
  return null
}
function eventLabel(type) {
  return { RUN_STARTED: '研究任务已开始', RUN_RESUMED: '研究已恢复', PLAN_CREATED: '研究计划已保存', STEP_SCHEDULED: '步骤已排队', STEP_STARTED: '正在执行研究步骤', STEP_COMPLETED: '步骤完成', STEP_FAILED: '步骤失败，将按预算重试', EVIDENCE_ACCEPTED: '证据已接纳', RUN_PAUSED: '已请求暂停', RUN_FINALIZING: '正在整理成果', RUN_COMPLETED: '研究完成', RUN_PARTIAL_COMPLETED: '研究部分完成', RUN_FAILED: '研究失败' }[type] || '研究状态已更新'
}
function researchPhase(current) {
  const status = current?.status
  return ({
    PLANNING: '正在理解问题', WAITING_APPROVAL: '研究计划已准备好', RESEARCHING: '正在检索和分析资料',
    VERIFYING: '正在核对引用', FINALIZING: '正在整理 Markdown 报告', COMPLETED: '研究报告已完成',
    PARTIAL_COMPLETED: '报告已完成，部分内容待补充', FAILED: '研究未完成', CANCELLED: '研究已停止', PAUSED: '研究已暂停'
  })[status] || '正在准备研究'
}
function currentResearchDetail(current) {
  const steps = Array.isArray(current?.steps) ? current.steps : []
  const active = steps.find(step => ['RUNNING', 'IN_PROGRESS', 'PENDING'].includes(String(step.status || '').toUpperCase()))
  if (active?.step_id || active?.title) return `当前方向：${active.title || active.step_id}`
  if (current?.execution_cursor != null) return `已完成 ${current.execution_cursor} 个研究步骤。`
  return '正在检索相关来源。'
}
function messageSourceLabel(message) {
  const citations = Array.isArray(message?.citations) ? message.citations : []
  const webCount = citations.filter(citation => String(citation?.sourceType || citation?.source_type || '').toUpperCase() === 'WEB').length
  if (webCount) return `已使用 ${webCount} 个网页来源`
  if (message?.general) return '通用回答 · 未依据专题资料'
  if (citations.length) return `基于 ${citations.length} 个资料来源`
  return ''
}
function runSeenKey(runId) { return `researchpilot-run-seen-${topicId}-${runId}` }
function eventSequence(event) { return Number(event?.sequence || 0) || 0 }
function markRunSeen() {
  if (!run.value?.id) return
  const max = Math.max(0, ...runEvents.value.map(eventSequence))
  try { localStorage.setItem(runSeenKey(run.value.id), String(max)) } catch {}
  runReturnSummary.value = ''
  runUnreadCount.value = 0
}
function updateRunReturnSummary(events) {
  if (!run.value?.id || !Array.isArray(events)) return
  const max = Math.max(0, ...events.map(eventSequence))
  let seen = 0
  try { seen = Number(localStorage.getItem(runSeenKey(run.value.id)) || 0) || 0 } catch {}
  if (!seen) {
    try { localStorage.setItem(runSeenKey(run.value.id), String(max)) } catch {}
    return
  }
  const fresh = events.filter(event => eventSequence(event) > seen)
  if (!fresh.length) return
  runUnreadCount.value = fresh.length
  runReturnSummary.value = eventLabel(fresh.at(-1).event_type)
}
function runDuration(current) {
  if (!current?.started_at) return ''
  const end = current.finished_at ? new Date(current.finished_at) : new Date()
  const seconds = Math.max(0, Math.round((end - new Date(current.started_at)) / 1000))
  return seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}
function runUsageSummary(events) {
  if (!Array.isArray(events)) return null
  const calls = events.filter(event => event?.event_type === 'LLM_CALL_COMPLETED')
  if (!calls.length) return null
  let duration = 0
  let tokens = 0
  let tokenCalls = 0
  for (const event of calls) {
    const detail = typeof event.detail === 'string' ? (() => { try { return JSON.parse(event.detail) } catch { return {} } })() : (event.detail || {})
    duration += Number(detail.duration_ms) || 0
    const prompt = Number(detail.prompt_tokens)
    const completion = Number(detail.completion_tokens)
    if (Number.isFinite(prompt) || Number.isFinite(completion)) {
      tokens += (Number.isFinite(prompt) ? prompt : 0) + (Number.isFinite(completion) ? completion : 0)
      tokenCalls++
    }
  }
  const seconds = Math.round(duration / 1000)
  return { calls: calls.length, duration: seconds ? `${seconds} 秒` : '', tokens: tokenCalls ? tokens.toLocaleString() : '', unknown: tokenCalls < calls.length }
}
function planVersionSteps(version) {
  if (!version) return ''
  try {
    const snapshot = typeof version.snapshot === 'string' ? JSON.parse(version.snapshot) : version.snapshot
    return Array.isArray(snapshot?.steps) ? snapshot.steps.map((step, index) => `${index + 1}. ${step}`).join('\n') : ''
  } catch { return '' }
}

function persistConversations() {
  const current = conversations.value.find(c => c.id === activeConversationId.value)
  if (current) {
    current.messages = msgs.value
    current.updatedAt = new Date().toISOString()
    const firstQuestion = msgs.value.find(m => m.role === 'user')
    if (firstQuestion && (!current.title || current.title === '新对话')) current.title = firstQuestion.text.slice(0, 30)
  }
  localStorage.setItem(conversationKey, JSON.stringify(conversations.value))
}
async function loadConversations() {
  try {
    const remote = await api.sessions(topicId)
    if (Array.isArray(remote) && remote.length) {
      conversations.value = []
      for (const session of remote) {
        const rows = await api.messages(topicId, session.id)
        const remoteDraft = await api.draft(topicId, session.id).catch(() => null)
        let draft = null
        try { draft = remoteDraft?.draft ? JSON.parse(remoteDraft.draft) : null } catch { draft = null }
        conversations.value.push({
          id: session.id, title: session.title || '新对话',
          parentSessionId: session.parent_session_id || null, branchMessageId: session.branch_message_id || null,
          messages: (rows || []).map(m => {
            const metadata = parseJson(m.metadata)
            return { id: m.id, role: m.role, text: m.content, citations: parseCitations(m.citations), isReport: metadata?.type === 'research_report', runId: metadata?.run_id }
          }),
          updatedAt: session.updated_at || new Date().toISOString()
        })
        if (session.id === String(route.query.session || '') && draft) applyDraft(draft)
      }
    }
  }
  catch { try { conversations.value = JSON.parse(localStorage.getItem(conversationKey) || '[]') } catch { conversations.value = [] } }
  if (!conversations.value.length) {
    const c = { id: crypto.randomUUID(), title: '新对话', messages: [], updatedAt: new Date().toISOString() }
    conversations.value = [c]
  }
  const requested = String(route.query.session || '')
  const initial = conversations.value.find(c => c.id === requested) || conversations.value[0]
  activeConversationId.value = initial.id
  msgs.value = initial.messages || []
  if (initial.id !== requested) {
    const remoteDraft = await api.draft(topicId, initial.id).catch(() => null)
    try { if (remoteDraft?.draft) applyDraft(JSON.parse(remoteDraft.draft)) } catch {}
  }
  if (initial.id !== requested) router.replace({ query: { ...route.query, session: initial.id } })
  await loadContext()
}
async function loadContext() {
  if (!activeConversationId.value) return
  contextBusy.value = true
  try {
    const data = await api.context(topicId, activeConversationId.value)
    contextData.messages = Array.isArray(data?.messages) ? data.messages : []
    contextData.memories = Array.isArray(data?.memories) ? data.memories : []
    contextData.runs = Array.isArray(data?.runs) ? data.runs : []
    contextData.exclusions = {
      message_ids: Array.isArray(data?.exclusions?.message_ids) ? data.exclusions.message_ids : [],
      memory_ids: Array.isArray(data?.exclusions?.memory_ids) ? data.exclusions.memory_ids : [],
      run_ids: Array.isArray(data?.exclusions?.run_ids) ? data.exclusions.run_ids : []
    }
  } catch {
    contextData.messages = []; contextData.memories = []; contextData.runs = []
    contextData.exclusions = { message_ids: [], memory_ids: [], run_ids: [] }
  } finally { contextBusy.value = false }
}
function isContextExcluded(kind, id) { return contextData.exclusions[kind]?.includes(id) }
async function toggleContext(kind, id, included) {
  const values = new Set(contextData.exclusions[kind] || [])
  included ? values.delete(id) : values.add(id)
  contextData.exclusions[kind] = [...values]
  try {
    await api.updateContext(topicId, activeConversationId.value, contextData.exclusions)
    toast.value = included ? '已恢复到上下文' : '已从后续上下文排除'
  } catch (error) {
    values[ included ? 'add' : 'delete' ](id)
    contextData.exclusions[kind] = [...values]
    toast.value = error.message || '上下文设置保存失败'
  }
}
function applyDraft(draft) {
  if (typeof draft.quickQ === 'string') quickQ.value = draft.quickQ
  if (typeof draft.deepQ === 'string') deepQ.value = draft.deepQ
  if (typeof draft.allowWeb === 'boolean') allowWeb.value = draft.allowWeb
  if (['quick', 'deep'].includes(draft.mode)) mode.value = draft.mode
  if (Array.isArray(draft.clarifQs)) clarifQs.value = draft.clarifQs
  if (Array.isArray(draft.clarifAnswers)) clarifAnswers.value = draft.clarifAnswers
  if (draft.constraints && typeof draft.constraints === 'object') Object.assign(constraints, draft.constraints)
  if (typeof draft.advancedOpen === 'boolean') advancedOpen.value = draft.advancedOpen
  if (typeof draft.planEdit === 'string') planEdit.value = draft.planEdit
  if (typeof draft.planDimInput === 'string') planDimInput.value = draft.planDimInput
  if (Array.isArray(draft.selectedNoteIds)) selectedNotes.value = new Set(draft.selectedNoteIds)
  if (draft.plan && typeof draft.plan === 'object') plan.value = draft.plan
  if (typeof draft.assumptionsAccepted === 'boolean') assumptionsAccepted.value = draft.assumptionsAccepted
  if (['config', 'clarify', 'plan'].includes(draft.deepStage) && (draft.deepQ || draft.plan)) deepStage.value = draft.deepStage
}
function parseCitations(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  try { return JSON.parse(String(value)) || [] } catch { return [] }
}
function parseJson(value) {
  if (!value) return null
  if (typeof value === 'object') return value
  try { return JSON.parse(String(value)) || null } catch { return null }
}
function editPaper(paper) {
  const metadata = paperMetadata(paper)
  Object.assign(paperForm, { title: paper.title || '', authors: typeof metadata.authors === 'string' ? metadata.authors : '', category: typeof metadata.category === 'string' ? metadata.category : '', tags: Array.isArray(metadata.tags) ? metadata.tags.join(', ') : '', year: Number.isInteger(metadata.year) ? metadata.year : '', doi: typeof metadata.doi === 'string' ? metadata.doi : '' })
  paperEditError.value = ''
  editingPaper.value = paper
}
function closePaperEditor() { if (!paperSaving.value) editingPaper.value = null }
async function savePaper() {
  if (paperSaving.value || !paperForm.title.trim()) return
  paperSaving.value = true
  paperEditError.value = ''
  try {
    const metadata = { ...paperMetadata(editingPaper.value), authors: paperForm.authors.trim(), category: paperForm.category.trim(), tags: [...new Set(paperForm.tags.split(/[,，]/).map(tag => tag.trim()).filter(Boolean))], year: paperForm.year === '' ? null : Number(paperForm.year), doi: paperForm.doi.trim() }
    await api.updatePaper(editingPaper.value.id, paperForm.title.trim(), metadata)
    await loadPapers()
    editingPaper.value = null
  } catch (error) {
    paperEditError.value = error.message || '资料更新失败'
  } finally { paperSaving.value = false }
}
function resetConversationState() {
  routeRevision++
  clearTimeout(serverDraftTimer)
  run.value = null
  focus.value = null
  mode.value = 'quick'
  allowWeb.value = false
  quickQ.value = ''
  deepQ.value = ''
  deepStage.value = 'config'
  plan.value = null
  clarifQs.value = []
  clarifAnswers.value = []
  planEdit.value = ''
  planDimInput.value = ''
  assumptionsAccepted.value = false
  advancedOpen.value = false
  selectedNotes.value = new Set()
  Object.assign(constraints, { output_length: 'balanced', time_range: '', source_limit: '' })
  runEvents.value = []
  recentEvidence.value = []
  requirements.value = []
  deepClientRequestId.value = ''
  requirementInput.value = ''
  report.value = null
  drawer.open = false
  reader.open = false
  planAdjustment.open = false
}
function newConversation() {
  switchingConversation = false
  saveDraft()
  const previousId = activeConversationId.value
  const previous = conversations.value.find(c => c.id === previousId)
  if (previous) api.saveDraft(topicId, previousId, previous.draft).catch(() => {})
  persistConversations()
  resetConversationState()
  const c = { id: crypto.randomUUID(), title: '新对话', messages: [], updatedAt: new Date().toISOString() }
  conversations.value.unshift(c)
  activeConversationId.value = c.id
  msgs.value = []
  studioTab.value = 'history'
  const query = { ...route.query, session: c.id }
  delete query.run
  delete query.result
  router.replace({ query })
}
async function switchConversation(id) {
  if (id === activeConversationId.value) return
  saveDraft()
  const previousId = activeConversationId.value
  const previous = conversations.value.find(c => c.id === previousId)
  if (previous) api.saveDraft(topicId, previousId, previous.draft).catch(() => {})
  persistConversations()
  const c = conversations.value.find(item => item.id === id)
  if (!c) return
  const nextDraft = c.draft
  switchingConversation = true
  resetConversationState()
  activeConversationId.value = id
  routeRevision++
  const query = { ...route.query, session: id }
  delete query.run
  delete query.result
  router.replace({ query })
  // A run/focus panel belongs to the previous thread. Reset it before loading
  // the selected conversation so the right history item visibly changes the
  // center workspace as well.
  run.value = null
  focus.value = null
  if (nextDraft) applyDraft(nextDraft)
  try {
    const rows = await api.messages(topicId, id)
    c.messages = (rows || []).map(m => {
      const metadata = parseJson(m.metadata)
      return { id: m.id, role: m.role, text: m.content, citations: parseCitations(m.citations), isReport: metadata?.type === 'research_report', runId: metadata?.run_id }
    })
  } catch { /* keep the local copy when the remote session is temporarily unavailable */ }
  if (activeConversationId.value !== id) return
  msgs.value = c.messages || []
  const remoteDraft = await api.draft(topicId, id).catch(() => null)
  if (activeConversationId.value !== id) return
  try { if (remoteDraft?.draft) applyDraft(JSON.parse(remoteDraft.draft)) } catch { /* malformed draft cannot block history navigation */ }
  await nextTick()
  switchingConversation = false
  studioTab.value = 'history'
  loadContext()
  await nextTick()
  setTimeout(scrollChat, 50)
}
async function renameConversation(conversation) {
  const title = await showDialog({ title: '重命名对话', input: true, value: conversation.title, placeholder: '输入一个便于查找的标题', confirmText: '保存' })
  if (!title || title === conversation.title) return
  conversation.title = title.slice(0, 120)
  try { await api.renameSession(topicId, conversation.id, conversation.title) } catch {}
  persistConversations()
}
async function deleteConversation(id) {
  const conversation = conversations.value.find(item => item.id === id)
  const confirmed = await showDialog({ title: '删除这段对话？', message: `“${conversation?.title || '新对话'}”及其消息将从当前专题历史中移除。`, confirmText: '删除对话', danger: true })
  if (!confirmed) return
  try { await api.deleteSession(topicId, id) } catch {}
  conversations.value = conversations.value.filter(c => c.id !== id)
  if (!conversations.value.length) {
    const c = { id: crypto.randomUUID(), title: '新对话', messages: [], updatedAt: new Date().toISOString() }
    conversations.value = [c]
  }
  if (!conversations.value.some(c => c.id === activeConversationId.value)) {
    activeConversationId.value = conversations.value[0].id
    msgs.value = conversations.value[0].messages || []
    router.replace({ query: { ...route.query, session: activeConversationId.value } })
  }
  localStorage.setItem(conversationKey, JSON.stringify(conversations.value))
}

// ---- Sources ----
function toggle(id) {
  const n = new Set(selected.value)
  n.has(id) ? n.delete(id) : n.add(id)
  selected.value = n
}
function toggleAllSources() {
  const n = new Set(selected.value)
  if (selectedCount.value === readyPapers.value.length && readyPapers.value.length) readyPapers.value.forEach(p => n.delete(p.id))
  else readyPapers.value.forEach(p => n.add(p.id))
  selected.value = n
}
async function upload(e) {
  await uploadFiles([...e.target.files])
  e.target.value = ''
}
async function dropUpload(e) {
  dragging.value = false
  await uploadFiles([...e.dataTransfer.files])
}
async function uploadFiles(files) {
  if (!files.length || uploading.value) return
  uploading.value = true
  try {
    for (const f of files) {
      const item = reactive({ id: crypto.randomUUID(), name: f.name, file: f, pct: 5, stageText: '上传中…', failed: false })
      queue.value.push(item)
      await submitUpload(item)
    }
    await loadPapers()
  } catch (error) {
    toast.value = error.message || '资料列表暂时无法刷新'
  } finally {
    uploading.value = false
  }
}
async function retryUpload(item) {
  if (!item.file || uploading.value) return
  uploading.value = true
  try {
    await submitUpload(item)
    await loadPapers()
  } catch (error) {
    toast.value = error.message || '资料列表暂时无法刷新'
  } finally {
    uploading.value = false
  }
}
async function cancelQueuedUpload(item) {
  item.cancelled = true
  item.controller?.abort()
  try {
    if (item.uploadId) await api.cancelUpload(item.uploadId)
    if (item.uploadKey) localStorage.removeItem(item.uploadKey)
    queue.value = queue.value.filter(candidate => candidate.id !== item.id)
  } catch (error) { item.failed = true; item.stageText = error.message || '取消失败，请重试' }
}
async function submitUpload(item) {
  item.cancelled = false
  item.failed = false
  item.pct = 5
  item.stageText = '上传中…'
  try {
    const response = item.file.size > 8 * 1024 * 1024 ? await resumableUpload(item) : await api.uploadPaper(topicId, item.file)
    item.paperId = response.paper_id
    item.pct = 100
    item.stageText = '已上传，等待解析…'
    if (response.warning) toast.value = response.warning
  } catch (error) {
    if (item.cancelled) return
    item.failed = true
    item.pct = 100
    item.stageText = error.message || '上传失败'
    if (error.status === 409 || error.status === 413 || error.status === 415) item.file = null
  }
}
async function resumableUpload(item) {
  return uploadResumable(api, topicId, item, localStorage, (done, total) => {
    item.pct = Math.round(done / total * 95)
    item.stageText = `上传分片 ${done}/${total}…`
  })
}
async function retryPaper(paper) {
  if (retryingPapers.value.has(paper.id)) return
  retryingPapers.value.add(paper.id)
  try {
    await api.retryPaper(paper.id)
    await loadPapers()
    toast.value = '已重新排队解析'
  } catch (error) {
    toast.value = error.message || '重新解析失败'
  } finally {
    retryingPapers.value.delete(paper.id)
  }
}
// 流水线阶段 → 用户语言 + 进度百分比（后端真实 stage，非伪造）
function stageText(stage) {
  return { PARSING: '解析文档中…', CHUNKING: '整理段落…', EMBEDDING: '生成语义向量…', INDEXING: '构建检索索引…' }[stage] || '排队中…'
}
function stagePct(stage) {
  return { PARSING: 30, CHUNKING: 55, EMBEDDING: 78, INDEXING: 92 }[stage] || 5
}
async function openReader(p) {
  if (p.parse_status !== 'READY') return
  const d = await api.paperDetail(p.id)
  let metadata = {}
  try { metadata = typeof d.metadata === 'string' ? JSON.parse(d.metadata || '{}') : (d.metadata || {}) } catch { metadata = {} }
  const abstract = d.abstract_text || d.summary || ''
  reader.paper = { ...p, summary: abstract, topics: metadata.tags || metadata.topics || (metadata.category ? [metadata.category] : deriveTopics(abstract)) }
  reader.versionId = d.version_id
  reader.fileExt = d.file_ext || ''
  reader.highlightText = ''
  reader.open = true
}
function deriveTopics(text) {
  const value = String(text || '').toLowerCase()
  const rules = [
    [/question answering|问答/, '问答系统'],
    [/dense retriev|dense representation|稠密检索/, '稠密检索'],
    [/dual.?encoder|双编码器/, '双编码器'],
    [/bm25|sparse vector|稀疏检索/, '信息检索'],
    [/benchmark|dataset|数据集/, '基准评测']
  ]
  return rules.filter(([pattern]) => pattern.test(value)).map(([, label]) => label).slice(0, 5)
}
async function del(id) {
  const paper = papers.value.find(item => item.id === id)
  const confirmed = await showDialog({ title: '移除这份资料？', message: `“${paper?.title || '这份资料'}”将不再用于后续研究，已有成果会保留。`, confirmText: '移除资料', danger: true })
  if (!confirmed) return
  await api.deletePaper(id)
  await loadPapers()
}

// ---- 快速提问 ----
async function ask(retryMessage = null) {
  const retry = retryMessage?.failed ? retryMessage : null
  const q = retry ? retry.question : quickQ.value.trim()
  if (!q || asking.value) return
  // 范围语义：已勾选 → 仅勾选资料；未勾选但有就绪资料 → 全部就绪资料（徽标已明示）；
  // 无任何就绪资料 → 通用知识回答（前端明示）
  const noReady = readyPapers.value.length === 0
  const conversation = conversations.value.find(c => c.id === activeConversationId.value)
  const targetMessages = msgs.value
  const scopeCount = selectedCount.value
  const scopePaperIds = noReady ? [] : [...selected.value]
  if (!retry) targetMessages.push({ role: 'user', text: q })
  else targetMessages.splice(targetMessages.indexOf(retry), 1)
  quickQ.value = ''
  resizeQuickInput()
  asking.value = true
  askingConversationId.value = activeConversationId.value
  const controller = new AbortController()
  const messageId = retry?.requestId || crypto.randomUUID()
  let streamedMessage = null
  answerController = controller
  await nextTick()
  scrollChat()
  try {
    const body = await api.askStream(topicId, q, scopePaperIds, noReady, activeConversationId.value, controller.signal, allowWeb.value, messageId, conversation?.parentSessionId || null, conversation?.branchMessageId || null)
    if (!body?.getReader) throw new Error('当前浏览器不支持流式回答')
    streamedMessage = { role: 'assistant', text: '', citations: [], scope: scopeCount, general: noReady, streaming: true, requestId: messageId }
    targetMessages.push(streamedMessage)
    const reader = body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let completed = false
    while (true) {
      const part = await reader.read()
      buffer += decoder.decode(part.value || new Uint8Array(), { stream: !part.done })
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() || ''
      for (const block of blocks) {
        const eventName = block.split('\n').find(line => line.startsWith('event:'))?.slice(6).trim()
        const dataLine = block.split('\n').find(line => line.startsWith('data:'))
        if (!dataLine) continue
        let event
        try {
          event = JSON.parse(dataLine.slice(5).trim())
        } catch { continue }
          if (eventName === 'error') throw new Error(event.message || '回答生成失败，请重试')
          if (eventName === 'complete') completed = true
          if (event.text) streamedMessage.text += event.text
          if (event.citations) streamedMessage.citations = parseCitations(event.citations)
          if (event.answer && !streamedMessage.text) streamedMessage.text = event.answer
        await nextTick()
        if (chatAtBottom.value) scrollChat()
      }
      if (completed) { await reader.cancel(); break }
      if (part.done) throw new Error('回答连接中断，未收到完成确认，请重试')
    }
    streamedMessage.streaming = false
    if (controller.signal.aborted) return
  } catch (error) {
    if (streamedMessage) { streamedMessage.streaming = false; streamedMessage.failed = true; streamedMessage.question = q; streamedMessage.requestId = messageId; streamedMessage.text = streamedMessage.text || (error.name === 'AbortError' ? '已取消等待，可重新提问。' : '回答生成失败，你可以重试这个问题。') }
    else targetMessages.push({ role: 'assistant', text: error.name === 'AbortError' ? '已取消等待，可重新提问。' : '回答生成失败，你可以重试这个问题。', failed: true, question: q, requestId: messageId })
  } finally {
    asking.value = false
    if (conversation && conversations.value.includes(conversation)) {
      conversation.messages = targetMessages
      conversation.updatedAt = new Date().toISOString()
      localStorage.setItem(conversationKey, JSON.stringify(conversations.value))
    }
    await nextTick()
    if (askingConversationId.value === activeConversationId.value && chatAtBottom.value) scrollChat()
  }
}
function scrollChat() {
  const el = chatBody.value
  if (el) { el.scrollTop = el.scrollHeight; chatAtBottom.value = true }
}
function trackChatScroll() {
  const el = chatBody.value
  if (el) chatAtBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 64
}
function resizeQuickInput() {
  const el = quickInput.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, 160)}px`
}
function submitQuick(event) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && !event.ctrlKey && !event.altKey && !event.metaKey) {
    event.preventDefault()
    ask()
  }
}
function submitActiveQuestion(event) {
  if (event?.key && (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.ctrlKey || event.altKey || event.metaKey)) return
  event?.preventDefault?.()
  if (mode.value === 'deep') startDeepResearch()
  else ask()
}
function editQuestion(index, message) {
  const branch = conversations.value.find(c => c.id === activeConversationId.value)
  if (!branch) return
  // Preserve the original conversation and continue from a new local branch.
  const next = { id: crypto.randomUUID(), title: `${message.text.slice(0, 26)}（分支）`, messages: branch.messages.slice(0, index), updatedAt: new Date().toISOString(), parentSessionId: branch.id, branchMessageId: message.id || null }
  conversations.value.unshift(next)
  activeConversationId.value = next.id
  msgs.value = next.messages
  quickQ.value = message.text
  router.replace({ query: { ...route.query, session: next.id } })
  nextTick(() => quickInput.value?.focus())
}
function cancelAnswer() {
  answerController?.abort()
  const conversation = conversations.value.find(c => c.id === askingConversationId.value)
  const pending = conversation?.messages?.find(m => m.streaming)
  if (pending?.requestId) api.cancelAskStream(topicId, pending.requestId).catch(() => {})
}
async function copyAnswer(message) {
  try { await navigator.clipboard.writeText(message.text); copiedMessage.value = message }
  catch { copiedMessage.value = null }
}
function openCitation(c) {
  if (c.sourceType === 'WEB' && c.url) { window.open(c.url, '_blank'); return }
  drawer.open = true
  drawer.title = '引用来源'
  drawer.sub = `第 ${c.page ?? '?'} 页 · ${c.sectionPath || ''}`
  drawer.items = [{ evidence_id: c.chunkId, value: c.text, supporting_quote: c.text, status: 'SUPPORTED',
    quote_status: 'QUOTE_SUPPORTED', claim_status: 'NOT_EVALUATED', section_path: c.sectionPath, page: c.page, paper_id: c.paperId }]
}
function openNoteEditor(e) {
  noteEditor.open = true
  noteEditor.id = ''
  noteEditor.title = ''
  noteEditor.body = ''
  noteEditor.runId = focus.value?.run?.id || ''
  noteEditor.evidenceId = e.evidence_id || e.chunk_id || ''
  noteEditor.sourceQuote = e.supporting_quote || e.value || e.text || ''
  noteEditor.error = ''
}
function closeNoteEditor() { if (!noteEditor.busy) noteEditor.open = false }
async function saveNote() {
  noteEditor.busy = true; noteEditor.error = ''
  try {
    const payload = { title: noteEditor.title, body: noteEditor.body, run_id: noteEditor.runId || null, evidence_id: noteEditor.evidenceId || null, source_quote: noteEditor.sourceQuote || null }
    if (noteEditor.id) await api.updateNote(noteEditor.id, payload)
    else await api.createNote(topicId, payload)
    noteEditor.open = false
    await loadNotes()
    toast.value = '笔记已保存'
  } catch (error) { noteEditor.error = error.message || '笔记保存失败' }
  finally { noteEditor.busy = false }
}
async function addRequirement() {
  const value = requirementInput.value.trim()
  if (!value || !run.value || requirementSaving.value) return
  requirementSaving.value = true
  try {
    await api.addRunRequirement(run.value.id, value)
    requirements.value = [...requirements.value, { requirement: value, status: 'PENDING' }]
    requirementInput.value = ''
    toast.value = '补充要求已加入后续研究步骤'
  } catch (error) { toast.value = error.message || '补充要求提交失败' }
  finally { requirementSaving.value = false }
}
async function previewPlanAdjustment() {
  if (!run.value || planAdjustment.loading) return
  planAdjustment.loading = true
  try {
    const proposal = await api.previewPlanAdjustment(run.value.id)
    planAdjustment.steps = Array.isArray(proposal.steps) ? proposal.steps : []
    planAdjustment.reasons = Array.isArray(proposal.reasons) ? proposal.reasons : []
    planAdjustment.open = true
  } catch (error) {
    toast.value = error.message || '计划调整分析失败'
  } finally { planAdjustment.loading = false }
}
async function applyPlanAdjustment() {
  if (!run.value || planAdjustment.applying || !planAdjustment.steps.length) return
  planAdjustment.applying = true
  try {
    await api.applyPlanAdjustment(run.value.id, planAdjustment.steps)
    planAdjustment.open = false
    await loadRun(run.value.id)
    toast.value = '剩余研究步骤已更新，并保存为新的计划版本'
  } catch (error) {
    toast.value = error.message || '计划调整应用失败'
  } finally { planAdjustment.applying = false }
}
async function loadNotes() {
  try { notes.value = await api.notes(topicId) } catch { notes.value = [] }
}
function openSavedNote(note) {
  drawer.open = true
  drawer.title = note.title
  drawer.sub = '已保存笔记的原文摘录'
  drawer.items = [{ evidence_id: note.evidence_id || note.id, value: note.body, supporting_quote: note.source_quote, status: 'NOT_EVALUATED', quote_status: 'NOT_EVALUATED', claim_status: 'NOT_EVALUATED' }]
}
async function locateSavedNote(note) {
  if (!note.run_id || !note.evidence_id) return
  try {
    const targetRun = await api.run(note.run_id)
    if (targetRun.topic_id !== topicId) throw new Error('这条笔记不属于当前专题')
    const evidence = await api.evidence(note.run_id)
    const target = evidence.find(item => String(item.evidence_id || item.id || item.source_chunk_id) === String(note.evidence_id))
    if (!target) { toast.value = '原证据已不在当前矩阵中，仍可查看笔记原文'; return }
    await openFocus(targetRun, 'matrix')
    sideEvidence.value = [target]
    pdf.value = null
    toast.value = '已定位到证据详情'
  } catch (error) { toast.value = error.message || '证据定位失败' }
}
function editSavedNote(note) {
  Object.assign(noteEditor, { open: true, id: note.id, title: note.title || '', body: note.body || '', runId: note.run_id || '', evidenceId: note.evidence_id || '', sourceQuote: note.source_quote || '', error: '' })
}
async function toggleNoteFavorite(note) {
  try { const result = await api.favoriteNote(note.id, !note.favorite); note.favorite = result.favorite } catch (error) { toast.value = error.message || '收藏状态更新失败' }
}
async function removeNote(id) {
  try { await api.deleteNote(id); await loadNotes(); toast.value = '笔记已删除' } catch (error) { toast.value = error.message || '笔记删除失败' }
}
async function loadMemory() { try { memories.value = await api.memory(topicId) } catch { memories.value = [] } }
async function addProjectMemory() {
  if (!memoryDraft.value.trim() || memorySaving.value) return
  memorySaving.value = true
  try { await api.addMemory(topicId, memoryKind.value, memoryDraft.value.trim()); memoryDraft.value = ''; await loadMemory(); toast.value = '专题记忆已保存' }
  catch (error) { toast.value = error.message || '专题记忆保存失败' }
  finally { memorySaving.value = false }
}
async function removeProjectMemory(id) {
  try { await api.deleteMemory(topicId, id); await loadMemory(); toast.value = '专题记忆已移除' }
  catch (error) { toast.value = error.message || '专题记忆移除失败' }
}
async function continueFromGap() {
  const gap = await showDialog({ title: '针对证据缺口继续研究', message: '描述需要补充的章节、结论或对比维度。已有已接纳证据会作为背景保留。', input: true, placeholder: '例如：补充中文场景下的失败案例和反向证据', confirmText: '开始继续研究' })
  if (!gap || !focus.value?.run?.id) return
  try {
    const child = await api.continueRun(focus.value.run.id, gap, crypto.randomUUID())
    toast.value = '续研任务已创建'
    await loadRuns()
    await openRunById(child.run_id)
    mode.value = 'deep'
    deepStage.value = 'run'
  } catch (error) { toast.value = error.message || '续研任务创建失败' }
}
async function continueFromEvidence(evidence) {
  if (!focus.value?.run?.id) return
  const hint = evidenceGap(evidence)
  const suggested = hint ? `${hint.title}：${hint.body}` : ''
  const gap = await showDialog({
    title: '针对这条证据继续研究',
    message: '只会创建新的续研任务，当前报告和已接纳证据保持不变。',
    input: true,
    value: suggested,
    placeholder: '描述需要补充的章节、指标或反向证据',
    confirmText: '开始续研'
  })
  if (!gap) return
  try {
    const child = await api.continueRun(focus.value.run.id, gap, crypto.randomUUID())
    toast.value = '证据缺口续研任务已创建'
    await loadRuns()
    await openRunById(child.run_id)
    mode.value = 'deep'
    deepStage.value = 'run'
  } catch (error) { toast.value = error.message || '续研任务创建失败' }
}
async function branchFromRun() {
  if (!run.value?.id) return
  const query = await showDialog({
    title: '创建新的研究目标分支',
    message: '原任务和已接纳证据会保留。新的目标会在同一专题和会话中作为独立研究运行。',
    input: true,
    value: '',
    placeholder: '例如：改为比较中文部署成本与可解释性',
    confirmText: '创建分支'
  })
  if (!query) return
  try {
    const child = await api.branchRun(run.value.id, query, crypto.randomUUID())
    toast.value = '目标分支已创建，原任务保持不变'
    await loadRuns()
    await openRunById(child.run_id)
    mode.value = 'deep'
    deepStage.value = 'run'
  } catch (error) { toast.value = error.message || '目标分支创建失败' }
}

// ---- 深度研究 ----
async function genPlan(clarList = null, skipClarification = false) {
  planning.value = true
  try {
    const d = await api.planDraft({
      query: deepQ.value, topic_id: topicId, dimensions: [],
      paper_ids: [...selected.value],
      clarifications: (clarList || []).map(answer => answer.trim()),
      skip_clarification: skipClarification,
      constraints: { ...constraints }
    })
    if (d.clarifying_questions && d.clarifying_questions.length) {
      clarifQs.value = d.clarifying_questions
      clarifAnswers.value = d.clarifying_questions.map(() => '')
      deepStage.value = 'clarify'
      return
    }
    plan.value = d
    assumptionsAccepted.value = false
    deepStage.value = 'plan'
  } finally { planning.value = false }
}
function skipClarification() {
  return genPlan([], true)
}
function submitClarifications() {
  const answers = clarifAnswers.value.map(answer => answer.trim()).filter(Boolean)
  return answers.length ? genPlan(answers) : skipClarification()
}
// Deep research shares the same composer as quick answers. The plan remains
// inspectable in the conversation, but the default path starts it in one action.
async function startDeepResearch() {
  if (!quickQ.value.trim() || planning.value || creating.value) return
  deepQ.value = quickQ.value.trim()
  if (!msgs.value.some(message => message.role === 'user' && message.text === deepQ.value)) {
    msgs.value.push({ role: 'user', text: deepQ.value })
  }
  await genPlan()
  // The plan is an explicit checkpoint. Starting the run requires the user to agree.
}
function addPlanDim() {
  const d = (planDimInput.value || '').trim()
  if (d && plan.value && !plan.value.dimensions.includes(d)) plan.value.dimensions.push(d)
  planDimInput.value = ''
}
function rmPlanDim(d) {
  if (plan.value) plan.value.dimensions = plan.value.dimensions.filter(x => x !== d)
}
function movePlanStep(from, to) {
  if (!plan.value || from == null || to == null || from === to || from < 0 || to < 0 || from >= plan.value.steps.length || to >= plan.value.steps.length) return
  plan.value = { ...plan.value, steps: moveStep(plan.value.steps, from, to) }
  planDrag.value = null
}
function removePlanStep(index) {
  if (plan.value) plan.value = { ...plan.value, steps: removeStep(plan.value.steps, index) }
}
function toggleNoteForResearch(id) {
  const next = new Set(selectedNotes.value)
  next.has(id) ? next.delete(id) : next.add(id)
  selectedNotes.value = next
}
async function revisePlan() {
  const instruction = planEdit.value.trim()
  if (!instruction || planning.value) return
  planning.value = true
  try {
    const d = await api.planDraft({
      query: `${deepQ.value}\n用户对当前计划的修改要求：${instruction}`,
      topic_id: topicId, dimensions: plan.value?.dimensions || [],
      paper_ids: plan.value?.scope_papers || [...selected.value], clarifications: clarifAnswers.value.filter(Boolean), constraints: { ...constraints }
    })
    if (d.clarifying_questions?.length) {
      clarifQs.value = d.clarifying_questions
      clarifAnswers.value = d.clarifying_questions.map(() => '')
      deepStage.value = 'clarify'
    }
    else {
      plan.value = d
      assumptionsAccepted.value = false
      planEdit.value = ''
      deepClientRequestId.value = ''
      const planMessage = msgs.value.find(message => message.metadata?.type === 'plan_draft')
      if (planMessage) planMessage.text = `研究计划已更新\n\n${d.steps.map((step, index) => `${index + 1}. ${step}`).join('\n')}\n\n默认假设：${(d.assumptions || []).join('；')}\n\n请确认后开始研究。`
    }
  }
  finally { planning.value = false }
}
async function confirmPlan() {
  creating.value = true
  try {
    if (!deepClientRequestId.value) deepClientRequestId.value = crypto.randomUUID()
    const d = await api.createRun({
      query: deepQ.value, topic_id: topicId, dimensions: plan.value.dimensions,
      steps: [...plan.value.steps], clarifications: clarifAnswers.value.map(a => a.trim()).filter(Boolean),
      paper_ids: plan.value.scope_papers, note_ids: [...selectedNotes.value], auto_accept_plan: true,
      client_request_id: deepClientRequestId.value, session_id: activeConversationId.value, allow_web: allowWeb.value,
      assumptions: plan.value.assumptions || [], assumptions_accepted: assumptionsAccepted.value,
      constraints: { ...constraints }
    })
    await openRunById(d.run_id)
    deepStage.value = 'run'
  } finally { creating.value = false }
}
async function act(action) {
  await api[action](run.value.id)
  await loadRun(run.value.id)
}
async function stop() {
  await api.stop(run.value.id)
  await loadRun(run.value.id)
}
async function pause() {
  if (!run.value) return
  await api.pause(run.value.id)
  await loadRun(run.value.id)
}
async function resume() {
  if (!run.value) return
  await api.resume(run.value.id)
  await loadRun(run.value.id)
}
async function retryStep(stepIndex) {
  if (!run.value) return
  try {
    await api.retryStep(run.value.id, stepIndex)
    await loadRun(run.value.id)
    deepStage.value = 'run'
  } catch (error) { toast.value = error.message || '步骤暂不可重试' }
}

// ---- Run ----
async function openReportFocus(runId) {
  const r = await api.run(runId)
  await openFocus(r, 'matrix')
}
async function openRun(r) { await openRunById(r.id); mode.value = 'deep'; deepStage.value = 'run'; router.replace({ query: { ...route.query, run: r.id } }) }
async function openRunById(id) {
  const nextRun = await api.run(id)
  if (nextRun.topic_id !== topicId) throw new Error('这项研究不属于当前专题')
  if (disposed) return
  run.value = nextRun
  await loadRunPlanVersions(id)
  try { requirements.value = await api.runRequirements(id) } catch { requirements.value = [] }
  try { runEvents.value = await api.events(id, 60); updateRunReturnSummary(runEvents.value) } catch { runEvents.value = [] }
  prevStatus = nextRun.status
}
async function loadRun(id) {
  run.value = await api.run(id)
  await loadRunPlanVersions(id)
  try { requirements.value = await api.runRequirements(id) } catch { requirements.value = [] }
}
async function loadRunPlanVersions(id) {
  try {
    const versions = await api.planVersions(id)
    runPlanVersions.value = Array.isArray(versions) ? versions : []
    selectedPlanVersion.value = runPlanVersions.value.length ? String(runPlanVersions.value.at(-1).version) : ''
  } catch {
    runPlanVersions.value = []
    selectedPlanVersion.value = ''
  }
}
async function loadRuns() {
  const version = ++runListVersion
  try {
    const page = await api.runPage({ topicId })
    if (disposed || version !== runListVersion) return
    const hadOlder = runsCursor.value !== null
    const incoming = new Set(page.items.map(r => r.id))
    runsAll.value = [...page.items, ...runsAll.value.filter(r => !incoming.has(r.id))]
    if (!hadOlder) { runsCursor.value = page.next_cursor; runsHasMore.value = page.has_more }
    runListError.value = ''
  } catch (error) { if (!disposed) runListError.value = error.message || '研究列表加载失败' }
}
async function loadSavedArtifacts() {
  try { savedArtifacts.value = await api.artifacts(topicId) } catch { savedArtifacts.value = [] }
}
function isArtifactSaved(kind) {
  return savedArtifacts.value.some(item => item.run_id === focus.value?.run?.id && item.kind === kind)
}
async function saveCurrentArtifact() {
  if (!focus.value?.run?.id || savingArtifact.value) return
  const kind = focus.value.type === 'report' ? 'REPORT' : 'MATRIX'
  if (isArtifactSaved(kind)) return
  savingArtifact.value = true
  try {
    const title = `${kind === 'REPORT' ? '研究报告' : '证据矩阵'} · ${focus.value.run.query}`.slice(0, 200)
    await api.saveArtifact(topicId, focus.value.run.id, kind, title)
    await loadSavedArtifacts()
    toast.value = '已保存到成果'
  } catch (error) { toast.value = error.message || '成果保存失败' }
  finally { savingArtifact.value = false }
}
async function removeSavedArtifact(artifact) {
  try { await api.deleteArtifact(topicId, artifact.id); await loadSavedArtifacts() }
  catch (error) { toast.value = error.message || '成果移除失败' }
}
async function openSavedArtifact(artifact) {
  const target = runsAll.value.find(item => String(item.id) === String(artifact.run_id))
  if (!target) { toast.value = '原研究任务不在当前历史页，请先加载更早的研究'; return }
  await openFocus(target, artifact.kind === 'REPORT' ? 'report' : 'matrix')
}
async function loadMoreRuns() {
  if (loadingMoreRuns.value || !runsHasMore.value) return
  loadingMoreRuns.value = true
  try {
    const page = await api.runPage({ topicId, cursor: runsCursor.value })
    if (disposed) return
    const known = new Set(runsAll.value.map(r => r.id))
    runsAll.value.push(...page.items.filter(r => !known.has(r.id)))
    runsCursor.value = page.next_cursor
    runsHasMore.value = page.has_more
    runListError.value = ''
  } catch (error) { runListError.value = error.message || '更早的研究加载失败，可重试' }
  finally { loadingMoreRuns.value = false }
}

// ---- Focus ----
function closeFocus() {
  focus.value = null
  pdf.value = null
  const query = { ...route.query }
  delete query.result
  router.replace({ query })
}
async function openFocus(r, type) {
  focus.value = { run: r, type }
  shareLink.value = ''
  shareId.value = ''
  shareCopied.value = false
  router.replace({ query: { ...route.query, run: r.id, result: type } })
  if (type === 'matrix') matrix.value = await api.matrix(r.id)
  else {
    try {
      report.value = await api.report(r.id)
      reportVersions.value = await api.reportVersions(r.id)
      selectedReportVersion.value = reportVersions.value.length ? String(reportVersions.value.at(-1).version) : ''
    } catch { report.value = null; reportVersions.value = [] }
  }
}
async function createShare() {
  if (!focus.value?.run?.id || shareBusy.value) return
  shareBusy.value = true
  try {
    const data = await api.createReportShare(focus.value.run.id)
    shareId.value = data.share_id || ''
    shareLink.value = `${window.location.origin}${data.path || `/api/rp/research/shared/${data.token}`}`
  } catch (error) {
    toast.value = error.message || '分享链接生成失败'
  } finally { shareBusy.value = false }
}
async function copyShareLink() {
  if (!shareLink.value) return
  try { await navigator.clipboard.writeText(shareLink.value); shareCopied.value = true; setTimeout(() => { shareCopied.value = false }, 1800) }
  catch { toast.value = '复制失败，请手动复制链接' }
}
async function revokeShare() {
  if (!focus.value?.run?.id || !shareId.value) return
  try { await api.revokeReportShare(focus.value.run.id, shareId.value); shareLink.value = ''; shareId.value = ''; toast.value = '分享链接已撤销' }
  catch (error) { toast.value = error.message || '撤销分享失败' }
}
function selectReportVersion() {
  const selected = reportVersions.value.find(version => String(version.version) === String(selectedReportVersion.value))
  if (selected) report.value = { ...report.value, report: selected.report, completed_steps: selected.completed_steps, total_steps: selected.total_steps }
  showReportDiff.value = false
}
function cell(p, fid) {
  return (matrix.value?.cells ?? []).find(c => c.paper_id === p && c.field_id === fid && c.evidence.length)
}
function cellClick(p, fid) {
  const c = cell(p, fid)
  if (!c) return
  sideEvidence.value = c.evidence
  pdf.value = null
}
function exportFocus() {
  if (!focus.value) return
  let content = ''
  let type = 'text/markdown;charset=utf-8'
  let suffix = 'md'
  if (focus.value.type === 'report') {
    content = `# ${focus.value.run.query}\n\n${report.value?.report || '报告尚未生成'}\n`
  }
  else {
    type = 'text/csv;charset=utf-8'
    suffix = 'csv'
    const fields = Object.values(matrix.value?.fields || {})
    const rows = [['论文', ...fields]]
    for (const paper of matrix.value?.papers || []) {
      rows.push([short(paper), ...Object.keys(matrix.value.fields || {}).map(fid => {
        const value = cell(paper, fid)
        return value ? `${zh(value.status)} (${value.evidence.length})` : '未找到'
      })])
    }
    content = rows.map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n')
  }
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `researchpilot-${new Date().toISOString().slice(0, 10)}.${suffix}`
  link.click()
  URL.revokeObjectURL(url)
}
function openPdf(e) {
  const ready = e.document_version_id ? Promise.resolve({ version_id: e.document_version_id }) : api.paperDetail(e.paper_id)
  ready.then(d => { pdf.value = { url: api.paperFileUrl(d.version_id), page: e.page || 1 } })
}
function openPdfFromDrawer(e) {
  drawer.open = false
  const paper = papers.value.find(item => String(item.id) === String(e.paper_id)) || { id: e.paper_id, title: e.section_path || '原文资料' }
  api.paperDetail(e.paper_id).then(d => {
    reader.paper = paper
    reader.versionId = e.document_version_id || d.version_id
    reader.fileExt = d.file_ext || ''
    reader.highlightText = e.supporting_quote || e.value || ''
    reader.open = true
  })
}

// ---- 轮询与装载 ----
let timer
let ticking = false
const savedReportRuns = new Set()
async function tick() {
  if (ticking || disposed) return
  ticking = true
  try {
  try { await loadPapers() } catch { /* keep the last known source state during transient failures */ }
  await loadRuns()
  if (run.value) {
    const before = run.value.status
    if (!['COMPLETED', 'PARTIAL_COMPLETED', 'FAILED', 'CANCELLED'].includes(before)) {
      await loadRun(run.value.id)
      // 进度人性化：展示最近收集的证据要点
      try {
        const evs = await api.evidence(run.value.id)
        recentEvidence.value = (evs || []).slice(-3).reverse()
      } catch { recentEvidence.value = [] }
      try { runEvents.value = await api.events(run.value.id, 40); updateRunReturnSummary(runEvents.value) } catch { runEvents.value = [] }
    }
    const st = run.value.status
    if (['COMPLETED', 'PARTIAL_COMPLETED'].includes(st) && !savedReportRuns.has(run.value.id)) {
      try { await api.saveResearchReport(run.value.id); savedReportRuns.add(run.value.id); await loadPapers() } catch { /* retry on the next poll */ }
    }
    if (prevStatus && prevStatus !== st) {
      if (st === 'WAITING_APPROVAL') { toast.value = '研究计划已准备好，等待你的确认。'; notifySystem('研究计划待确认', '打开 ResearchPilot 查看并确认研究计划。') }
      else if (st === 'COMPLETED') { toast.value = '研究已完成，报告已返回发起对话。'; notifySystem('研究已完成', '研究报告和证据已准备好。') }
      else if (st === 'PARTIAL_COMPLETED') { toast.value = '研究部分完成，报告已标记未完成项。'; notifySystem('研究部分完成', '报告已生成，但仍有未完成或待校验内容。') }
      else if (st === 'FAILED') { toast.value = '研究未完成，请查看任务进展或重试。'; notifySystem('研究未完成', '研究任务失败，可返回查看原因并重试。') }
    }
    // 深度研究完成 → 报告进入聊天流（ChatGPT/Gemini 式）
    if (prevStatus && prevStatus !== st && ['COMPLETED', 'PARTIAL_COMPLETED'].includes(st)) {
      try {
        if (!savedReportRuns.has(run.value.id)) {
          await api.saveResearchReport(run.value.id)
          savedReportRuns.add(run.value.id)
        }
        // The server writes the report to the Run's originating session. Reload that
        // session instead of appending to whichever conversation is currently open.
        const completedRunId = run.value.id
        const originSessionId = run.value.session_id
        if (originSessionId) {
          const rows = await api.messages(topicId, originSessionId)
          const origin = conversations.value.find(c => c.id === originSessionId)
          const reportMessage = (rows || []).find(m => parseJson(m.metadata)?.run_id === completedRunId)
          if (origin && reportMessage && !origin.messages.some(m => m.runId === completedRunId)) {
            const m = reportMessage
            const metadata = parseJson(m.metadata)
            origin.messages.push({ role: m.role, text: m.content, citations: parseCitations(m.citations), isReport: metadata?.type === 'research_report', runId: metadata?.run_id })
            origin.updatedAt = new Date().toISOString()
            if (originSessionId === activeConversationId.value) msgs.value = origin.messages
            localStorage.setItem(conversationKey, JSON.stringify(conversations.value))
          }
        }
        if (originSessionId === activeConversationId.value) {
          if (mode.value !== 'quick') mode.value = 'quick'
          if (chatAtBottom.value) setTimeout(scrollChat, 50)
        }
        await loadPapers()
      } catch {}
    }
    prevStatus = st
  }
  } catch (error) {
    if (!disposed) toast.value = error.message || '暂时无法刷新研究状态'
  } finally { ticking = false }
}
async function loadPapers() {
  const nextPapers = await api.papers(topicId)
  if (disposed) return
  const previousIds = knownPaperIds.value
  const nextIds = new Set(nextPapers.map(p => p.id))
  const visibleNextIds = new Set(nextPapers.filter(p => !isInternalFixturePaper(p)).map(p => p.id))
  const nextSelected = new Set([...selected.value].filter(id => visibleNextIds.has(id)))
  // Newly discovered ready papers are selected by default; an explicit user
  // uncheck survives background polling because the id is already known.
  for (const p of nextPapers) {
    if (!isInternalFixturePaper(p) && !previousIds.has(p.id) && p.parse_status === 'READY') nextSelected.add(p.id)
  }
  papers.value = nextPapers
  savedSourceUrls.value = new Set(nextPapers.map(p => paperMetadata(p).source_url).filter(Boolean))
  queue.value = queue.value.filter(item => item.failed || !item.paperId || !nextIds.has(item.paperId))
  knownPaperIds.value = nextIds
  selected.value = nextSelected
}
async function loadWebSnapshots() { try { webSnapshots.value = await api.webSnapshots(topicId) } catch { webSnapshots.value = [] } }
async function saveWebSource() {
  if (!webUrl.value.trim() || webSaving.value) return
  webSaving.value = true
  try { await api.saveWebSnapshot(topicId, webUrl.value.trim()); webUrl.value = ''; await Promise.all([loadWebSnapshots(), loadPapers()]); toast.value = '网页正文已保存为资料，正在解析' }
  catch (error) { toast.value = error.message || '网页快照保存失败' }
  finally { webSaving.value = false }
}
async function saveCitationSource(citation) {
  const url = String(citation?.url || '').trim()
  if (!url || savedSourceUrls.value.has(url) || savingSourceUrls.value.has(url)) return
  savingSourceUrls.value = new Set(savingSourceUrls.value).add(url)
  try {
    await api.saveWebSnapshot(topicId, url)
    savedSourceUrls.value = new Set(savedSourceUrls.value).add(url)
    await Promise.all([loadPapers(), loadWebSnapshots()])
    toast.value = '引用来源已保存到资料库，正在解析'
  } catch (error) {
    toast.value = error.message || '引用来源保存失败'
  } finally {
    const next = new Set(savingSourceUrls.value)
    next.delete(url)
    savingSourceUrls.value = next
  }
}
async function discoverPapers() {
  if (!discoveryQuery.value.trim() || discoveryBusy.value) return
  discoveryBusy.value = true
  discoveryCandidates.value = []
  try {
    const data = await api.discoverPapers(topicId, discoveryQuery.value.trim(), 5)
    discoveryCandidates.value = data.candidates || []
    discoveryOpen.value = true
  }
  catch (error) { toast.value = error.message || '论文发现失败' }
  finally { discoveryBusy.value = false }
}
async function importCandidate(candidate) {
  const url = candidate.url || (candidate.doi ? `https://doi.org/${candidate.doi}` : '')
  if (!url || candidateImporting.value.has(url)) return
  const next = new Set(candidateImporting.value); next.add(url); candidateImporting.value = next
  try { await api.importDiscoveredPaper(topicId, url); toast.value = '候选已导入资料，正在解析'; discoveryOpen.value = false; await Promise.all([loadPapers(), loadWebSnapshots()]) }
  catch (error) { toast.value = error.message || '候选导入失败' }
  finally { const done = new Set(candidateImporting.value); done.delete(url); candidateImporting.value = done }
}
async function openWebSnapshot(snapshot) {
  try { const detail = await api.webSnapshot(snapshot.id); drawer.open = true; drawer.title = detail.title || detail.url; drawer.sub = `抓取于 ${fmt(detail.fetched_at)} · SHA-256 ${detail.content_sha256}`; drawer.items = [{ evidence_id: detail.id, value: detail.content || '此历史快照尚未提取正文，请重新保存网页来源。', supporting_quote: detail.content || '', status: 'NOT_EVALUATED', quote_status: 'NOT_EVALUATED', claim_status: 'NOT_EVALUATED', source_type: 'WEB', source_url: detail.final_url }] }
  catch (error) { toast.value = error.message || '网页快照读取失败' }
}
async function downloadCitation(paper) {
  try {
    const response = await fetch(api.citationUrl(paper.id, 'bibtex'))
    if (!response.ok) throw new Error('引用格式生成失败')
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${String(paper.title || 'citation').replace(/[\\/:*?"<>|]/g, '_')}.bib`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) { toast.value = error.message || '引用格式生成失败' }
}

async function restoreRouteState() {
  const revision = ++routeRevision
  const sessionId = String(route.query.session || '')
  const conversation = conversations.value.find(c => c.id === sessionId)
  if (conversation && activeConversationId.value !== sessionId) {
    persistConversations()
    activeConversationId.value = sessionId
    msgs.value = conversation.messages || []
  }
  const runId = String(route.query.run || '')
  if (!runId) { run.value = null; focus.value = null; return }
  try {
    const loaded = await api.run(runId)
    if (disposed || revision !== routeRevision) return
    if (loaded.topic_id !== topicId) throw new Error('这项研究不属于当前专题')
    run.value = loaded
    let loadedRequirements = []
    try { loadedRequirements = await api.runRequirements(runId) } catch {}
    if (disposed || revision !== routeRevision) return
    requirements.value = loadedRequirements
    prevStatus = loaded.status
    mode.value = 'deep'
    deepStage.value = 'run'
    const result = String(route.query.result || '')
    if (['matrix', 'report'].includes(result)) {
      const data = result === 'matrix' ? await api.matrix(runId) : await api.report(runId)
      if (disposed || revision !== routeRevision) return
      if (result === 'matrix') matrix.value = data
      else {
        report.value = data
        try {
          const versions = await api.reportVersions(runId)
          if (disposed || revision !== routeRevision) return
          reportVersions.value = versions
          selectedReportVersion.value = reportVersions.value.length
            ? String(reportVersions.value.at(-1).version)
            : ''
        } catch {
          if (disposed || revision !== routeRevision) return
          reportVersions.value = []
          selectedReportVersion.value = ''
        }
      }
      if (disposed || revision !== routeRevision) return
      focus.value = { run: loaded, type: result }
    } else focus.value = null
  } catch (error) {
    if (!disposed && revision === routeRevision) { focus.value = null; run.value = null; toast.value = error.message || '研究链接已失效' }
  }
}
onMounted(async () => {
  window.addEventListener('keydown', openCommand)
  restoreDraft()
  await loadConversations()
  if (disposed) return
  topic.value = (await api.topics()).find(t => t.id === topicId) || {}
  await loadPapers()
  await loadWebSnapshots()
  await loadNotes()
  await loadMemory()
  if (disposed) return
  await loadRuns()
  await loadSavedArtifacts()
  workspaceReady = true
  await restoreRouteState()
  if (disposed) return
  timer = setInterval(tick, 5000)
})
watch(() => [route.query.session, route.query.run, route.query.result], () => { if (workspaceReady) restoreRouteState() })
watch(msgs, persistConversations, { deep: true })
watch([quickQ, deepQ, mode, allowWeb, selectedNotes, clarifQs, clarifAnswers, planEdit, planDimInput, deepStage, plan, assumptionsAccepted], saveDraft, { deep: true })
onUnmounted(() => { disposed = true; routeRevision++; window.removeEventListener('keydown', openCommand); closeDialog(false); answerController?.abort(); clearInterval(timer); clearTimeout(serverDraftTimer) })
</script>
