import test from 'node:test'
import assert from 'node:assert/strict'
import { organizeSources, paperMetadata } from './sources.js'

const papers = [
  { id: 'a', title: '中文研究', created_at: '2026-01-01', parse_status: 'READY', metadata: { category: '检索', tags: ['跨语言'], authors: 'Alice' } },
  { id: 'b', title: 'Agent Methods', created_at: '2026-02-01', parse_status: 'FAILED', metadata: JSON.stringify({ category: 'Agent', tags: ['协同'] }) },
  { id: 'c', title: 'Citation Review', created_at: '2026-03-01', parse_status: 'READY', metadata: null }
]

test('search includes tags and authors, with tag intersection', () => {
  assert.deepEqual(organizeSources(papers, { search: 'alice', tag: '跨语言' }).map(p => p.id), ['a'])
  assert.deepEqual(organizeSources(papers, { search: 'alice', tag: '协同' }), [])
})
test('sorting and grouping do not mutate source order or selection identities', () => {
  assert.deepEqual(organizeSources(papers).map(p => p.id), ['c', 'b', 'a'])
  assert.deepEqual(organizeSources(papers, { sort: 'ready' }).map(p => p.id), ['c', 'a', 'b'])
  const grouped = organizeSources(papers, { group: true })
  assert.equal(new Set(grouped.map(p => p.id)).size, 3)
  assert.deepEqual(papers.map(p => p.id), ['a', 'b', 'c'])
})
test('legacy malformed metadata safely falls back to an empty object', () => {
  for (const metadata of ['null', '[]', 'invalid', 42, null]) assert.deepEqual(paperMetadata({ metadata }), {})
})
