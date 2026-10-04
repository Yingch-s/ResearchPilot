const { chromium } = require('C:/Users/bula7/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert = require('node:assert/strict')

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    const errors = []
    page.on('pageerror', e => errors.push(e.message))
    const topic = '10d5d028-7046-43aa-87c9-bfbef25cf6c1'
    const run = 'aaf98be2-bab3-4e4f-b86c-79ffa04221e1'
    await page.goto(`http://127.0.0.1:5174/notebooks/${topic}?session=f2f455fa-5046-430b-a2d1-95c2667f61c3&run=${run}`)
    await page.locator('.research-activity').waitFor()
    assert.equal(await page.locator('.research-activity').getAttribute('open'), null)
    assert.equal(await page.locator('textarea[aria-label="继续提问"]').isVisible(), true)
    await page.getByRole('button', { name: '对话', exact: true }).click()
    const target = page.locator('.history-item:not(.active)').first()
    await target.waitFor()
    await target.click()
    await page.waitForURL(url => !url.searchParams.has('run') && !url.searchParams.has('result'))
    await page.waitForFunction(() => !document.querySelector('.research-activity'))
    const input = page.locator('.research textarea:visible').first()
    await input.fill('导航回归草稿')
    const checkbox = page.locator('.research label').filter({ hasText: '深度研究' }).locator('input').first()
    await checkbox.check()
    assert.equal(await input.inputValue(), '导航回归草稿')
    await checkbox.uncheck()
    assert.equal(await input.inputValue(), '导航回归草稿')
    await input.fill('')
    assert.deepEqual(errors, [])
    console.log('PASS: old run remains inline; history removes stale run; composer and draft survive mode toggles')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e); process.exitCode = 1 })
