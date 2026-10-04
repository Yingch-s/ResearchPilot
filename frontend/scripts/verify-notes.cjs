const assert = require('node:assert/strict')
const { chromium } = require('C:/Users/bula7/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  const errors = []
  page.on('pageerror', error => errors.push(error.message))

await page.route('**/api/rp/topics**', async route => {
  await route.fulfill({ json: [{ id: 'note-review', name: '研究专题' }] })
})
await page.route('**/api/rp/topics/note-review/papers**', async route => {
  await route.fulfill({ json: [] })
})
await page.route('**/api/rp/topics/note-review/conversations**', async route => {
  await route.fulfill({ json: [] })
})
await page.goto('http://127.0.0.1:5174/notebooks/note-review', { waitUntil: 'networkidle' })

// Notes remain a compatibility API, but the default product surface no longer exposes
// a separate notes tab or an action that saves arbitrary evidence as a note.
  assert.equal(await page.getByRole('button', { name: '笔记', exact: true }).count(), 0)
  assert.equal(await page.getByText('记一条笔记', { exact: true }).count(), 0)
  assert.equal(await page.getByText('保存为笔记', { exact: true }).count(), 0)
  assert.ok(await page.getByRole('button', { name: '新建对话' }).count())
  assert.deepEqual(errors, [])
  await browser.close()
  console.log('notes surface verification passed')
}

main().catch(error => { console.error(error); process.exitCode = 1 })
