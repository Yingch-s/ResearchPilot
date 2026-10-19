// ResearchPilot 前端 API 封装。
// 契约：SSE 不是业务状态来源 —— 凭 run_id 轮询/查询状态恢复（V0.4 决策）。
async function req(method, url, body, signal, raw = false) {
  const opt = { method, headers: {}, signal }
  if (body !== undefined) {
    opt.headers['Content-Type'] = raw ? 'application/octet-stream' : 'application/json'
    opt.body = raw ? body : JSON.stringify(body)
  }
  const r = await fetch(url, opt)
  if (!r.ok) {
    // P0-A：会话失效 → 通知鉴权层跳登录页（off 模式永不触发）
    if (r.status === 401 && !url.startsWith('/api/rp/auth/')) {
      window.dispatchEvent(new CustomEvent('rp:unauthorized'))
    }
    let detail = null
    try { detail = await r.json() } catch {}
    const error = new Error(detail?.message || detail?.error || `${method} ${url} → ${r.status}`)
    error.status = r.status
    error.code = detail?.code || `HTTP_${r.status}`
    error.retryable = detail?.retryable ?? (r.status >= 500 || r.status === 429)
    throw error
  }
  return r.status === 204 ? null : r.json()
}

export const api = {
  // ---- 专题 ----
  topics: (archived = false) => req('GET', `/api/rp/topics?archived=${archived}`),
  health: () => req('GET', '/api/rp/health'),
  settings: () => req('GET', '/api/rp/settings'),
  updateSettings: (settings) => req('PUT', '/api/rp/settings', settings),
  createTopic: (name, researchGoal) =>
    req('POST', `/api/rp/topics?name=${encodeURIComponent(name)}` +
      (researchGoal ? `&researchGoal=${encodeURIComponent(researchGoal)}` : '')),
  updateTopic: (topicId, name, researchGoal) => req('PATCH', `/api/rp/topics/${topicId}`, { name, research_goal: researchGoal }),
  archiveTopic: (topicId) => req('POST', `/api/rp/topics/${topicId}/archive`),
  restoreTopic: (topicId) => req('POST', `/api/rp/topics/${topicId}/restore`),
  deleteTopic: (topicId) => req('DELETE', `/api/rp/topics/${topicId}`),
  artifacts: (topicId) => req('GET', `/api/rp/topics/${topicId}/artifacts`),
  saveArtifact: (topicId, runId, kind, title) => req('POST', `/api/rp/topics/${topicId}/artifacts`, { run_id: runId, kind, title }),
  deleteArtifact: (topicId, artifactId) => req('DELETE', `/api/rp/topics/${topicId}/artifacts/${artifactId}`),
  notes: (topicId) => req('GET', `/api/rp/topics/${topicId}/notes`),
  createNote: (topicId, note) => req('POST', `/api/rp/topics/${topicId}/notes`, note),
  updateNote: (noteId, note) => req('PATCH', `/api/rp/notes/${noteId}`, note),
  deleteNote: (noteId) => req('DELETE', `/api/rp/notes/${noteId}`),
  favoriteNote: (noteId, favorite) => req('POST', `/api/rp/notes/${noteId}/favorite`, { favorite }),
  memory: (topicId) => req('GET', `/api/rp/topics/${topicId}/memory`),
  addMemory: (topicId, kind, content) => req('POST', `/api/rp/topics/${topicId}/memory`, { kind, content }),
  deleteMemory: (topicId, memoryId) => req('DELETE', `/api/rp/topics/${topicId}/memory/${memoryId}`),
  userMemory: () => req('GET', '/api/rp/user-memory'),
  addUserMemory: (kind, content) => req('POST', '/api/rp/user-memory', { kind, content }),
  dismissUserMemory: (memoryId) => req('DELETE', `/api/rp/user-memory/${memoryId}`),

  // ---- 论文 ----
  papers: (topicId) => req('GET', `/api/rp/topics/${topicId}/papers`),
  webSnapshots: (topicId) => req('GET', `/api/rp/topics/${topicId}/web-snapshots`),
  webSnapshot: (snapshotId) => req('GET', `/api/rp/web-snapshots/${snapshotId}`),
  saveWebSnapshot: (topicId, url) => req('POST', `/api/rp/topics/${topicId}/web-snapshots`, { url }),
  paperDetail: (paperId) => req('GET', `/api/rp/papers/${paperId}`),
  discoverPapers: (topicId, query, limit = 5) => req('POST', `/api/rp/topics/${topicId}/discover`, { query, limit }),
  importDiscoveredPaper: (topicId, url) => req('POST', `/api/rp/topics/${topicId}/discover/import`, { url }),
  citationUrl: (paperId, format = 'bibtex') => `/api/rp/papers/${paperId}/citation?format=${encodeURIComponent(format)}`,
  uploadPaper: async (topicId, file) => {
    const fd = new FormData()
    fd.append('file', file)
    const r = await fetch(`/api/rp/topics/${topicId}/papers`, { method: 'POST', body: fd })
    const data = await r.json()
    if (!r.ok) {
      const error = new Error(data?.message || data?.error || `上传失败 (${r.status})`)
      error.status = r.status
      error.retryable = data?.retryable ?? (r.status >= 500 || r.status === 429)
      throw error
    }
    return data
  },
  createUploadSession: (topicId, payload) => req('POST', `/api/rp/topics/${topicId}/upload-sessions`, payload),
  uploadStatus: (uploadId) => req('GET', `/api/rp/upload-sessions/${uploadId}`),
  uploadChunk: (uploadId, index, blob, signal) => req('PUT', `/api/rp/upload-sessions/${uploadId}/chunks/${index}`, blob, signal, true),
  completeUpload: (uploadId, title) => req('POST', `/api/rp/upload-sessions/${uploadId}/complete` + (title ? `?title=${encodeURIComponent(title)}` : '')),
  cancelUpload: (uploadId) => req('DELETE', `/api/rp/upload-sessions/${uploadId}`),
  retryPaper: (paperId) => req('POST', `/api/rp/papers/${paperId}/retry`),
  deletePaper: (paperId) => req('DELETE', `/api/rp/papers/${paperId}`),
  updatePaper: (paperId, title, metadata = {}) => req('PATCH', `/api/rp/papers/${paperId}`, { title, metadata: JSON.stringify(metadata) }),
  paperFileUrl: (versionId) => `/api/rp/versions/${versionId}/file`,

  // ---- 研究任务（深度研究）----
  runs: () => req('GET', '/api/rp/research'),
  runPage: ({ topicId, limit = 20, cursor, activeOnly = false } = {}) => {
    const query = new URLSearchParams({ limit: String(limit), active_only: String(activeOnly) })
    if (topicId) query.set('topic_id', topicId)
    if (cursor?.before_time && cursor?.before_id) {
      query.set('before_time', cursor.before_time)
      query.set('before_id', cursor.before_id)
    }
    return req('GET', `/api/rp/research/page?${query}`)
  },
  run: (runId) => req('GET', `/api/rp/research/${runId}`),
  planVersions: (runId) => req('GET', `/api/rp/research/${runId}/plan-versions`),
  previewPlanAdjustment: (runId, signals = []) => req('POST', `/api/rp/research/${runId}/plan-adjust/preview`, { signals }),
  applyPlanAdjustment: (runId, steps) => req('POST', `/api/rp/research/${runId}/plan-adjust/apply`, { steps }),
  planDraft: (payload) => req('POST', '/api/rp/research/plan-draft', payload),
  createRun: (payload) => req('POST', '/api/rp/research', payload),
  continueRun: (runId, gap, clientRequestId) => req('POST', `/api/rp/research/${runId}/continue`, { gap, client_request_id: clientRequestId }),
  branchRun: (runId, query, clientRequestId) => req('POST', `/api/rp/research/${runId}/branch`, { query, client_request_id: clientRequestId }),
  approve: (runId) => req('POST', `/api/rp/research/${runId}/approve`),
  resume: (runId) => req('POST', `/api/rp/research/${runId}/resume`),
  retryStep: (runId, stepIndex) => req('POST', `/api/rp/research/${runId}/steps/${stepIndex}/retry`),
  addRunRequirement: (runId, requirement) => req('POST', `/api/rp/research/${runId}/requirements`, { requirement }),
  runRequirements: (runId) => req('GET', `/api/rp/research/${runId}/requirements`),
  pause: (runId) => req('POST', `/api/rp/research/${runId}/pause`),
  reject: (runId) => req('POST', `/api/rp/research/${runId}/reject`),
  stop: (runId) => req('POST', `/api/rp/research/${runId}/stop`),
  report: (runId) => req('GET', `/api/rp/research/${runId}/report`),
  reportVersions: (runId) => req('GET', `/api/rp/research/${runId}/report-versions`),
  saveResearchReport: (runId, title) => req('POST', `/api/rp/research/${runId}/save-as-paper`, title ? { title } : {}),
  createReportShare: (runId) => req('POST', `/api/rp/research/${runId}/share`),
  revokeReportShare: (runId, shareId) => req('DELETE', `/api/rp/research/${runId}/share/${shareId}`),
  sharedReport: (token) => req('GET', `/api/rp/research/shared/${encodeURIComponent(token)}`),
  events: (runId, limit = 60, after = 0) => req('GET', `/api/rp/research/${runId}/events?limit=${limit}&after=${after}`),

  // ---- 快速提问（UX-1）----
  ask: (topicId, question, paperIds, allowGeneral = false, sessionId, signal, allowWeb = false, messageId, parentSessionId, branchMessageId) =>
    req('POST', `/api/rp/notebooks/${topicId}/ask`, { question, paper_ids: paperIds, allow_general: allowGeneral, allow_web: allowWeb, session_id: sessionId, message_id: messageId, parent_session_id: parentSessionId, branch_message_id: branchMessageId }, signal),
  askStream: async (topicId, question, paperIds, allowGeneral = false, sessionId, signal, allowWeb = false, messageId, parentSessionId, branchMessageId) => {
    const response = await fetch(`/api/rp/notebooks/${topicId}/ask/stream`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, signal,
      body: JSON.stringify({ question, paper_ids: paperIds, allow_general: allowGeneral, allow_web: allowWeb, session_id: sessionId, message_id: messageId, parent_session_id: parentSessionId, branch_message_id: branchMessageId })
    })
    if (!response.ok) {
      let detail = null
      try { detail = await response.json() } catch {}
      const error = new Error(detail?.message || `POST /api/rp/notebooks/${topicId}/ask/stream → ${response.status}`)
      error.status = response.status; error.code = detail?.code || `HTTP_${response.status}`; error.retryable = response.status >= 500 || response.status === 429
      throw error
    }
    return response.body
  },
  cancelAskStream: (topicId, messageId) => req('POST', `/api/rp/notebooks/${topicId}/ask/stream/${encodeURIComponent(messageId)}/cancel`),

  sessions: (topicId) => req('GET', `/api/rp/notebooks/${topicId}/sessions`),
  createSession: (topicId, title = '新对话') => req('POST', `/api/rp/notebooks/${topicId}/sessions`, { title }),
  messages: (topicId, sessionId) => req('GET', `/api/rp/notebooks/${topicId}/sessions/${sessionId}/messages`),
  draft: (topicId, sessionId) => req('GET', `/api/rp/notebooks/${topicId}/sessions/${sessionId}/draft`),
  saveDraft: (topicId, sessionId, draft) => req('POST', `/api/rp/notebooks/${topicId}/sessions/${sessionId}/draft`, draft),
  context: (topicId, sessionId) => req('GET', `/api/rp/notebooks/${topicId}/sessions/${sessionId}/context`),
  updateContext: (topicId, sessionId, exclusions) => req('POST', `/api/rp/notebooks/${topicId}/sessions/${sessionId}/context`, exclusions),
  renameSession: (topicId, sessionId, title) => req('POST', `/api/rp/notebooks/${topicId}/sessions/${sessionId}/rename`, { title }),
  deleteSession: (topicId, sessionId) => req('DELETE', `/api/rp/notebooks/${topicId}/sessions/${sessionId}`),
  appendMessage: (topicId, sessionId, role, content) => req('POST', `/api/rp/notebooks/${topicId}/sessions/${sessionId}/messages`, { role, content }),

  // ---- 证据 ----
  matrix: (runId) => req('GET', `/api/rp/research/${runId}/matrix`),
  evidence: (runId) => req('GET', `/api/rp/research/${runId}/evidence`),
}
