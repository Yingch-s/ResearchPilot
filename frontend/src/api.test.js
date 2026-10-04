import test from 'node:test'
import assert from 'node:assert/strict'
import { api } from './api.js'

test('upload rejects a duplicate HTTP response instead of treating it as success', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => new Response(JSON.stringify({ error: '相同文件已存在' }), { status: 409 })
  try {
    await assert.rejects(api.uploadPaper('topic', new Blob(['test'])), error => error.status === 409 && error.message === '相同文件已存在' && !error.retryable)
  } finally { globalThis.fetch = original }
})

test('upload returns the exact paper identity supplied by the server', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => new Response(JSON.stringify({ paper_id: 'paper-42', job_id: 'job-12' }), { status: 202 })
  try {
    assert.deepEqual(await api.uploadPaper('topic', new Blob(['test'])), { paper_id: 'paper-42', job_id: 'job-12' })
  } finally { globalThis.fetch = original }
})

test('archived topics and restore use explicit endpoints', async () => {
  const original = globalThis.fetch
  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push([url, options.method])
    return new Response(JSON.stringify(url.includes('/restore') ? { restored: true } : []), { status: 200 })
  }
  try {
    await api.topics(true)
    await api.restoreTopic('topic-42')
    assert.deepEqual(requests, [['/api/rp/topics?archived=true', 'GET'], ['/api/rp/topics/topic-42/restore', 'POST']])
  } finally { globalThis.fetch = original }
})

test('shared request parser exposes stable error code and retry policy', async () => {
  const original = globalThis.fetch
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 'PAPER_NOT_FOUND', message: '资料不存在', retryable: false }), { status: 404 })
  try {
    await assert.rejects(api.paperDetail('missing'), error =>
      error.status === 404 && error.code === 'PAPER_NOT_FOUND' && error.message === '资料不存在' && error.retryable === false)
  } finally { globalThis.fetch = original }
})

test('research controls address step retry and plan history endpoints', async () => {
  const original = globalThis.fetch
  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push([url, options.method])
    return new Response(JSON.stringify([]), { status: 200 })
  }
  try {
    await api.retryStep('run-1', 2)
    await api.planVersions('run-1')
    assert.deepEqual(requests, [
      ['/api/rp/research/run-1/steps/2/retry', 'POST'],
      ['/api/rp/research/run-1/plan-versions', 'GET']
    ])
  } finally { globalThis.fetch = original }
})

test('plan adjustment preview stays an explicit proposal request', async () => {
  const original = globalThis.fetch
  let request
  globalThis.fetch = async (url, options) => { request = [url, options.method, JSON.parse(options.body)]; return new Response('{"requires_approval":true}', { status: 200 }) }
  try {
    await api.previewPlanAdjustment('run-3', [{ stepId: '比较方法', kind: 'GAP', detail: '中文场景' }])
    assert.deepEqual(request, ['/api/rp/research/run-3/plan-adjust/preview', 'POST', { signals: [{ stepId: '比较方法', kind: 'GAP', detail: '中文场景' }] }])
  } finally { globalThis.fetch = original }
})

test('plan adjustment apply sends only the reviewed step list', async () => {
  const original = globalThis.fetch
  let request
  globalThis.fetch = async (url, options) => {
    request = [url, options.method, JSON.parse(options.body)]
    return new Response('{"status":"RESEARCHING"}', { status: 202 })
  }
  try {
    await api.applyPlanAdjustment('run-3', ['检索基线', '复核中文场景'])
    assert.deepEqual(request, ['/api/rp/research/run-3/plan-adjust/apply', 'POST', { steps: ['检索基线', '复核中文场景'] }])
  } finally { globalThis.fetch = original }
})

test('running research accepts supplemental requirements without replacing the run', async () => {
  const original = globalThis.fetch
  const requests = []
  globalThis.fetch = async (url, options) => { requests.push([url, options.method, options.body]); return new Response('{}', { status: 202 }) }
  try {
    await api.addRunRequirement('run-2', '关注中文归纳与英文原文的差异')
    await api.runRequirements('run-2')
    assert.deepEqual(requests.map(([url, method]) => [url, method]), [
      ['/api/rp/research/run-2/requirements', 'POST'],
      ['/api/rp/research/run-2/requirements', 'GET']
    ])
  } finally { globalThis.fetch = original }
})

test('context controls use a session scoped read and update contract', async () => {
  const original = globalThis.fetch
  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push([url, options.method, options.body])
    return new Response('{"saved":true}', { status: 202 })
  }
  try {
    await api.context('topic-1', 'session-1')
    await api.updateContext('topic-1', 'session-1', { message_ids: ['m-1'], memory_ids: [], run_ids: [] })
    assert.equal(requests[0][0], '/api/rp/notebooks/topic-1/sessions/session-1/context')
    assert.equal(requests[0][1], 'GET')
    assert.deepEqual(JSON.parse(requests[1][2]), { message_ids: ['m-1'], memory_ids: [], run_ids: [] })
  } finally { globalThis.fetch = original }
})

test('goal changes create an explicit research branch', async () => {
  const original = globalThis.fetch
  let request
  globalThis.fetch = async (url, options) => {
    request = [url, options.method, JSON.parse(options.body)]
    return new Response('{"run_id":"child-1","parent_run_id":"run-1"}', { status: 202 })
  }
  try {
    await api.branchRun('run-1', '改为比较中文部署成本', 'client-branch-1')
    assert.deepEqual(request, ['/api/rp/research/run-1/branch', 'POST', { query: '改为比较中文部署成本', client_request_id: 'client-branch-1' }])
  } finally { globalThis.fetch = original }
})

test('report history has a separate endpoint from the current report', async () => {
  const original = globalThis.fetch
  let request
  globalThis.fetch = async (url, options) => { request = [url, options.method]; return new Response('[]', { status: 200 }) }
  try {
    await api.reportVersions('run-9')
    assert.deepEqual(request, ['/api/rp/research/run-9/report-versions', 'GET'])
  } finally { globalThis.fetch = original }
})

test('report sharing uses the research route and supports revocation', async () => {
  const original = globalThis.fetch
  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push([url, options.method])
    return new Response(JSON.stringify({ share_id: 'share-1', token: 't'.repeat(64), path: '/api/rp/research/shared/' + 't'.repeat(64) }), { status: options.method === 'POST' ? 201 : 200 })
  }
  try {
    await api.createReportShare('run-1')
    await api.revokeReportShare('run-1', 'share-1')
    assert.deepEqual(requests, [
      ['/api/rp/research/run-1/share', 'POST'],
      ['/api/rp/research/run-1/share/share-1', 'DELETE']
    ])
  } finally { globalThis.fetch = original }
})

test('plan draft preserves explicit advanced constraints in the request', async () => {
  const original = globalThis.fetch
  let body
  globalThis.fetch = async (url, options) => { body = JSON.parse(options.body); return new Response(JSON.stringify({ steps: [] }), { status: 200 }) }
  try {
    await api.planDraft({ query: '比较方法', constraints: { output_length: 'detailed', time_range: '2020-2025' } })
    assert.deepEqual(body.constraints, { output_length: 'detailed', time_range: '2020-2025' })
  } finally { globalThis.fetch = original }
})

test('edited question sends persisted branch provenance', async () => {
  const original = globalThis.fetch
  let body
  globalThis.fetch = async (url, options) => { body = JSON.parse(options.body); return new Response(JSON.stringify({ answer: 'ok', citations: [] }), { status: 200 }) }
  try {
    await api.ask('topic-1', '改写问题', [], false, 'child-session', undefined, false, 'message-1', 'parent-session', 'branch-message')
    assert.deepEqual(body, { question: '改写问题', paper_ids: [], allow_general: false, allow_web: false, session_id: 'child-session', message_id: 'message-1', parent_session_id: 'parent-session', branch_message_id: 'branch-message' })
  } finally { globalThis.fetch = original }
})

test('streaming ask keeps the same idempotent request envelope', async () => {
  const original = globalThis.fetch
  let request
  globalThis.fetch = async (url, options) => { request = [url, options.method, JSON.parse(options.body)]; return new Response('event: complete\ndata: {}\n\n', { status: 200 }) }
  try {
    const body = await api.askStream('topic-1', '流式问题', ['paper-1'], false, 'session-1', undefined, true, 'message-1')
    assert.equal(typeof body.getReader, 'function')
    assert.deepEqual(request, ['/api/rp/notebooks/topic-1/ask/stream', 'POST', { question: '流式问题', paper_ids: ['paper-1'], allow_general: false, allow_web: true, session_id: 'session-1', message_id: 'message-1' }])
  } finally { globalThis.fetch = original }
})

test('stream cancellation targets the original message id', async () => {
  const original = globalThis.fetch
  let request
  globalThis.fetch = async (url, options) => { request = [url, options.method]; return new Response('{}', { status: 200 }) }
  try {
    await api.cancelAskStream('topic-1', 'message-1')
    assert.deepEqual(request, ['/api/rp/notebooks/topic-1/ask/stream/message-1/cancel', 'POST'])
  } finally { globalThis.fetch = original }
})

test('paper discovery remains a read-only candidate request', async () => {
  const original = globalThis.fetch
  let request
  globalThis.fetch = async (url, options) => { request = [url, options.method, JSON.parse(options.body)]; return new Response('{"candidates":[],"imported":false}', { status: 200 }) }
  try {
    await api.discoverPapers('topic-1', '10.1234/example', 3)
    assert.deepEqual(request, ['/api/rp/topics/topic-1/discover', 'POST', { query: '10.1234/example', limit: 3 }])
  } finally { globalThis.fetch = original }
})

test('selected discovery candidates use an explicit import endpoint', async () => {
  const original = globalThis.fetch
  let request
  globalThis.fetch = async (url, options) => { request = [url, options.method, JSON.parse(options.body)]; return new Response('{}', { status: 201 }) }
  try {
    await api.importDiscoveredPaper('topic-1', 'https://doi.org/10.1234/example')
    assert.deepEqual(request, ['/api/rp/topics/topic-1/discover/import', 'POST', { url: 'https://doi.org/10.1234/example' }])
  } finally { globalThis.fetch = original }
})

test('notes keep source binding endpoints separate from session messages', async () => {
  const original = globalThis.fetch
  const requests = []
  globalThis.fetch = async (url, options) => {
    requests.push([url, options.method, options.body])
    return new Response(JSON.stringify({ id: 'note-1', created: true }), { status: 201 })
  }
  try {
    await api.createNote('topic-1', { title: '结论', body: '保留原文', evidence_id: 'ev-1', source_quote: 'quote' })
    await api.updateNote('note-1', { title: '修订', body: '更新' })
    await api.deleteNote('note-1')
    assert.deepEqual(requests.map(([url, method]) => [url, method]), [
      ['/api/rp/topics/topic-1/notes', 'POST'],
      ['/api/rp/notes/note-1', 'PATCH'],
      ['/api/rp/notes/note-1', 'DELETE']
    ])
  } finally { globalThis.fetch = original }
})

test('web snapshots preserve URL and immutable content endpoints', async () => {
  const original = globalThis.fetch
  const requests = []
  globalThis.fetch = async (url, options) => { requests.push([url, options.method, options.body]); return new Response('{}', { status: 201 }) }
  try {
    await api.saveWebSnapshot('topic-1', 'https://example.com/paper')
    await api.webSnapshots('topic-1')
    await api.webSnapshot('snapshot-1')
    assert.deepEqual(requests.map(([url, method]) => [url, method]), [
      ['/api/rp/topics/topic-1/web-snapshots', 'POST'],
      ['/api/rp/topics/topic-1/web-snapshots', 'GET'],
      ['/api/rp/web-snapshots/snapshot-1', 'GET']
    ])
  } finally { globalThis.fetch = original }
})

test('resumable upload exposes session, chunk and completion endpoints', async () => {
  const original = globalThis.fetch
  const requests = []
  globalThis.fetch = async (url, options) => { requests.push([url, options.method]); return new Response(JSON.stringify({}), { status: 200 }) }
  try {
    await api.createUploadSession('topic-1', { filename: 'a.pdf', total_size: 4 })
    await api.uploadStatus('upload-1')
    await api.uploadChunk('upload-1', 0, new Blob(['data']))
    await api.completeUpload('upload-1', 'A')
    await api.cancelUpload('upload-1')
    assert.deepEqual(requests, [
      ['/api/rp/topics/topic-1/upload-sessions', 'POST'],
      ['/api/rp/upload-sessions/upload-1', 'GET'],
      ['/api/rp/upload-sessions/upload-1/chunks/0', 'PUT'],
      ['/api/rp/upload-sessions/upload-1/complete?title=A', 'POST']
      , ['/api/rp/upload-sessions/upload-1', 'DELETE']
    ])
  } finally { globalThis.fetch = original }
})
