const { chromium } = require('C:/Users/bula7/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    const chunks = new Set()
    const sent = []
    let creates = 0
    let completed = 0
    let failOnce = true
    await page.route('**/api/**', async route => {
      const request = route.request()
      const url = new URL(request.url())
      const path = url.pathname
      let body = []
      let status = 200
      if (path === '/api/rp/topics') body = [{ id: 'upload-review', name: 'Upload Review' }]
      else if (path === '/api/rp/research/page') body = { items: [], has_more: false, next_cursor: null }
      else if (path.endsWith('/draft')) body = { draft: null }
      else if (path.endsWith('/upload-sessions')) { creates++; body = { upload_id: 'browser-upload', chunk_size: 4194304 }; status = 201 }
      else if (path.endsWith('/browser-upload')) body = { status: completed ? 'COMPLETED' : 'UPLOADING', chunk_size: 4194304, received: [...chunks].map(chunk_index => ({ chunk_index })) }
      else if (path.includes('/chunks/')) {
        const index = Number(path.split('/').at(-1))
        assert.equal(request.headers()['content-type'], 'application/octet-stream')
        assert.equal(request.postDataBuffer().length, index === 2 ? 1048576 : 4194304)
        if (index === 1 && failOnce) { failOnce = false; status = 502; body = { code: 'TEST_OFFLINE', message: 'Connection interrupted', retryable: true } }
        else { chunks.add(index); sent.push(index); body = { chunk_index: index } }
      } else if (path.endsWith('/complete')) { completed++; body = { paper_id: 'browser-paper' }; status = 202 }
      await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    })
    await page.goto('http://127.0.0.1:5174/notebooks/upload-review')
    await page.locator('.research-entry-card').waitFor()
    const file = { name: 'large-test.txt', mimeType: 'text/plain', buffer: Buffer.alloc(9 * 1024 * 1024, 'x') }
    await page.locator('input[type=file]').setInputFiles(file)
    await page.getByText('Connection interrupted', { exact: true }).waitFor()
    assert.deepEqual(sent, [0])
    await page.reload()
    await page.locator('.research-entry-card').waitFor()
    await page.locator('input[type=file]').setInputFiles(file)
    await page.getByText('已上传，等待解析…', { exact: true }).waitFor()
    assert.deepEqual(sent, [0, 1, 2])
    assert.equal(creates, 1)
    assert.equal(completed, 1)
    assert.deepEqual(errors, [])
    assert.equal(await page.locator('.mobile-panel-actions').isVisible(), false)
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true)
    await page.waitForFunction(() => {
      const bar = document.querySelector('.pbar-fill')
      return bar && Math.abs(bar.getBoundingClientRect().width - bar.parentElement.getBoundingClientRect().width) < 1
    })
    fs.mkdirSync('target/browser-verification', { recursive: true })
    await page.screenshot({ path: 'target/browser-verification/upload-desktop.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    await page.getByRole('button', { name: '资料', exact: true }).click()
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true)
    await page.screenshot({ path: 'target/browser-verification/upload-mobile.png', fullPage: true })
    console.log('PASS: 9MB file, binary request bodies, interruption, reload/reselect resume, single completion, desktop/mobile overflow and no page errors')
  } finally { await browser.close() }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
