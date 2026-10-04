export function previewKind(extension) {
  const ext = String(extension || '').toLowerCase()
  if (ext === 'pdf') return 'pdf'
  if (['png', 'jpg', 'jpeg'].includes(ext)) return 'image'
  if (['txt', 'md', 'markdown'].includes(ext)) return 'text'
  return 'extracted'
}
