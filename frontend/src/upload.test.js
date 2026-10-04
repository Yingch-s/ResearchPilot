import test from 'node:test'
import assert from 'node:assert/strict'
import { uploadResumable } from './upload.js'

function fixture() {
  const values = new Map()
  const storage = { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
  const file = new File(['abcdefghij'], 'paper.txt')
  const received = new Set()
  const sent = []
  let creates = 0
  let completed = false
  const api = {
    createUploadSession: async () => { creates++; return { upload_id: 'upload-1' } },
    uploadStatus: async () => ({ chunk_size: 4, status: completed ? 'COMPLETED' : 'UPLOADING', received: [...received].map(chunk_index => ({ chunk_index })) }),
    uploadChunk: async (id, index, blob) => { sent.push(index); received.add(index); assert.equal(blob.size, index === 2 ? 2 : 4) },
    completeUpload: async () => { completed = true; return { paper_id: 'paper-1' } }
  }
  return { api, file, storage, values, received, sent, creates: () => creates }
}

test('reselecting the same file after a reload resumes only missing parts', async () => {
  const f = fixture()
  const send = f.api.uploadChunk
  let interrupted = false
  f.api.uploadChunk = async (...args) => { if (args[1] === 1 && !interrupted) { interrupted = true; throw new Error('offline') }; return send(...args) }
  await assert.rejects(uploadResumable(f.api, 'topic', { file: f.file }, f.storage), /offline/)
  assert.equal(f.values.size, 1)
  assert.deepEqual(await uploadResumable(f.api, 'topic', { file: f.file }, f.storage), { paper_id: 'paper-1' })
  assert.deepEqual(f.sent, [0, 1, 2])
  assert.equal(f.creates(), 1)
  assert.equal(f.values.size, 0)
})

test('lost completion response recovers the original paper without resending', async () => {
  const f = fixture()
  const complete = f.api.completeUpload
  let interrupted = false
  f.api.completeUpload = async () => { const result = await complete(); if (!interrupted) { interrupted = true; throw new Error('response lost') }; return result }
  await assert.rejects(uploadResumable(f.api, 'topic', { file: f.file }, f.storage), /response lost/)
  assert.deepEqual(await uploadResumable(f.api, 'topic', { file: f.file }, f.storage), { paper_id: 'paper-1' })
  assert.deepEqual(f.sent, [0, 1, 2])
  assert.equal(f.creates(), 1)
})

test('cancelled transfer never sends another part or completes', async () => {
  const f = fixture()
  const item = { file: f.file }
  let completes = 0
  f.api.completeUpload = async () => { completes++ }
  await assert.rejects(uploadResumable(f.api, 'topic', item, f.storage, () => { item.cancelled = true }), error => error.name === 'AbortError')
  assert.deepEqual(f.sent, [0])
  assert.equal(completes, 0)
})

test('unavailable local storage does not prevent uploading or lose in-page retry identity', async () => {
  const f = fixture()
  const storage = Object.fromEntries(['getItem', 'setItem', 'removeItem'].map(method => [method, () => { throw new Error('storage blocked') }]))
  const item = { file: f.file }
  const send = f.api.uploadChunk
  let fail = true
  f.api.uploadChunk = async (...args) => { if (args[1] === 1 && fail) { fail = false; throw new Error('offline') }; return send(...args) }
  await assert.rejects(uploadResumable(f.api, 'topic', item, storage), /offline/)
  assert.deepEqual(await uploadResumable(f.api, 'topic', item, storage), { paper_id: 'paper-1' })
  assert.equal(f.creates(), 1)
  assert.deepEqual(f.sent, [0, 1, 2])
})
