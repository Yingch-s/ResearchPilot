const { chromium } = require('C:/Users/bula7/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert = require('node:assert/strict')

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    const calls = []
    const errors = []
    page.on('pageerror', e => errors.push(e.message))
    await page.route('**/api/**', async route => {
      const r = route.request()
      const path = new URL(r.url()).pathname
      let body = []
      let status = 200
      if (path === '/api/rp/topics') body = [{ id: 'retry-review', name: '快速问答重试' }]
      else if (path.endsWith('/research/page')) body = { items: [], has_more: false }
      else if (path.endsWith('/draft')) body = { draft: null }
      else if (path.endsWith('/ask/stream')) {
        calls.push(r.postDataJSON())
        if (calls.length === 1) {
          body = 'event: error\ndata: {"message":"回答保存失败，请重试"}\n\n'
        }
        else {
          body = 'event: delta\ndata: {"text":"恢复的回答"}\n\n' +
            'event: complete\ndata: {"answer":"恢复的回答","citations":[],"used_papers":0}\n\n'
        }
      }
      await route.fulfill({ status, contentType: status === 200 && path.endsWith('/ask/stream') ? 'text/event-stream' : 'application/json', body: typeof body === 'string' ? body : JSON.stringify(body) })
    })
    await page.goto('http://127.0.0.1:5174/notebooks/retry-review')
    await page.getByRole('textbox', { name: '研究问题', exact: true }).fill('比较这两种方法')
    await page.locator('.entry-submit').click()
    await page.getByRole('button', { name: '重试', exact: true }).click()
    await page.getByText('恢复的回答', { exact: true }).waitFor()
    assert.equal(calls.length, 2)
    assert.equal(calls[0].message_id, calls[1].message_id)
    assert.ok(calls[0].message_id)
    assert.equal(await page.locator('.msg .q').count(), 1)
    assert.equal(await page.locator('.retry-answer').count(), 0)
    assert.deepEqual(errors, [])
    console.log('PASS: response failure retry reuses message ID, single user message, recovered answer, no page errors')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e); process.exitCode = 1 })
