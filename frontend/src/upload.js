export async function uploadResumable(api, topicId, item, storage, onProgress = () => {}) {
  const storageCall = (method, ...args) => {
    try { return storage[method](...args) } catch { return null }
  }
  const digest = await crypto.subtle.digest('SHA-256', await item.file.arrayBuffer())
  const sha256 = [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, '0')).join('')
  item.uploadKey = `researchpilot-upload-${topicId}-${sha256}`
  let uploadId = item.uploadId || storageCall('getItem', item.uploadKey)
  let state = null
  if (uploadId) {
    try {
      state = await api.uploadStatus(uploadId)
      if (!['UPLOADING', 'COMPLETED'].includes(state.status)) { uploadId = null; state = null }
    } catch (error) { if (error.status === 404) uploadId = null; else throw error }
  }
  if (!uploadId) {
    const session = await api.createUploadSession(topicId, { filename: item.file.name, content_type: item.file.type, total_size: item.file.size, sha256 })
    uploadId = session.upload_id
    storageCall('setItem', item.uploadKey, uploadId)
  }
  item.uploadId = uploadId
  state = state || await api.uploadStatus(uploadId)
  if (state.status === 'COMPLETED') {
    const result = await api.completeUpload(uploadId)
    storageCall('removeItem', item.uploadKey)
    return result
  }
  item.controller = new AbortController()
  const received = new Set((state.received || []).map(chunk => chunk.chunk_index))
  const total = Math.ceil(item.file.size / state.chunk_size)
  for (let index = 0; index < total; index += 1) {
    if (item.cancelled) throw new DOMException('Upload cancelled', 'AbortError')
    if (!received.has(index)) {
      const start = index * state.chunk_size
      await api.uploadChunk(uploadId, index, item.file.slice(start, Math.min(start + state.chunk_size, item.file.size)), item.controller.signal)
    }
    onProgress(index + 1, total)
  }
  if (item.cancelled) throw new DOMException('Upload cancelled', 'AbortError')
  const result = await api.completeUpload(uploadId)
  storageCall('removeItem', item.uploadKey)
  return result
}
