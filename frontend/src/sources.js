export function paperMetadata(paper) {
  try {
    const value = typeof paper.metadata === 'string' ? JSON.parse(paper.metadata) : paper.metadata
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
  } catch { return {} }
}

export function organizeSources(papers, { search = '', tag = '', sort = 'created', group = false } = {}) {
  const query = search.trim().toLocaleLowerCase()
  const filtered = papers.filter(paper => {
    const metadata = paperMetadata(paper)
    const tags = Array.isArray(metadata.tags) ? metadata.tags : []
    return (!tag || tags.includes(tag)) && (!query || [paper.title, metadata.authors, metadata.category, ...tags].join(' ').toLocaleLowerCase().includes(query))
  })
  filtered.sort((a, b) => {
    if (group) {
      const category = String(paperMetadata(a).category || '未分组').localeCompare(String(paperMetadata(b).category || '未分组'), 'zh')
      if (category) return category
    }
    if (sort === 'title') return String(a.title || '').localeCompare(String(b.title || ''), 'zh') || a.id.localeCompare(b.id)
    if (sort === 'ready') {
      const status = Number(b.parse_status === 'READY') - Number(a.parse_status === 'READY')
      if (status) return status
    }
    return String(b.created_at || '').localeCompare(String(a.created_at || '')) || a.id.localeCompare(b.id)
  })
  return filtered
}
