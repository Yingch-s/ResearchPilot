const { chromium } = require('C:/Users/bula7/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    const errors = []
    page.on('pageerror', e => errors.push(e.message))
    await page.route('**/api/**', async route => {
      const path = new URL(route.request().url()).pathname
      let body = []
      if (path === '/api/rp/topics') body = [{ id: 'layout-review', name: '跨语言检索与证据校验研究' }]
      else if (path.endsWith('/research/page')) body = { items: [], has_more: false }
      else if (path.endsWith('/draft')) body = { draft: null }
      else if (path.endsWith('/papers')) body = Array.from({ length: 18 }, (_, i) => ({ id: `paper-${i}`, title: `研究资料 ${i + 1} — Multilingual Evidence Retrieval`, parse_status: 'READY', chunk_count: 100, metadata: '{}'}))
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    })
    await page.goto('http://127.0.0.1:5174/notebooks/layout-review')
    const question = page.getByRole('textbox', { name: '研究问题', exact: true })
    await question.fill('不要丢失当前输入')
    const source = page.locator('.pane.sources')
    const studio = page.locator('.pane.studio')
    const left = page.getByRole('separator', { name: '资料栏宽度' })
    await left.waitFor()
    const initial = (await source.boundingBox()).width
    const handle = await left.boundingBox()
    await page.mouse.move(handle.x + handle.width / 2, handle.y + 150)
    await page.mouse.down()
    await page.mouse.move(handle.x + 100, handle.y + 150, { steps: 8 })
    await page.mouse.up()
    assert.ok((await source.boundingBox()).width > initial + 70)
    await left.focus()
    await page.keyboard.press('Home')
    assert.equal((await source.boundingBox()).width, 220)
    await page.keyboard.press('End')
    assert.ok((await source.boundingBox()).width <= 420)
    await page.getByRole('button', { name: '重置栏宽', exact: true }).click()
    assert.equal((await source.boundingBox()).width, 248)
    assert.equal((await studio.boundingBox()).width, 284)
    await page.getByRole('button', { name: '折叠资料面板', exact: true }).click()
    assert.equal((await source.boundingBox()).width, 44)
    await page.getByRole('button', { name: '展开资料面板', exact: true }).click()
    assert.equal((await source.boundingBox()).width, 248)
    const right = page.getByRole('separator', { name: '历史栏宽度' })
    await right.focus()
    await page.keyboard.press('ArrowLeft')
    assert.equal((await studio.boundingBox()).width, 294)
    fs.mkdirSync('target/browser-verification', { recursive: true })
    await page.getByRole('button', { name: '编辑资料名称', exact: true }).first().click()
    const dialog = page.getByRole('dialog', { name: '资料信息', exact: true })
    await dialog.waitFor()
    await page.getByRole('button', { name: '取消', exact: true }).focus()
    await page.keyboard.press('Shift+Tab')
    assert.equal(await page.evaluate(() => !!document.activeElement.closest('.rp-dialog')), true)
    await page.screenshot({ path: 'target/browser-verification/layout-dialog.png' })
    await page.keyboard.press('Escape')
    assert.equal(await page.getByRole('button', { name: '编辑资料名称', exact: true }).first().evaluate(el => el === document.activeElement), true)
    await page.screenshot({ path: 'target/browser-verification/layout-desktop.png' })
    for (const width of [1100, 1024, 900, 390]) {
      await page.setViewportSize({ width, height: 844 })
      await page.waitForTimeout(100)
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true, `overflow at ${width}`)
      if (width >= 1024) {
        assert.ok((await page.locator('.pane.research').boundingBox()).width >= 380)
      } else {
        assert.equal(await left.isVisible(), false)
        const trigger = page.getByRole('button', { name: '资料', exact: true })
        await trigger.click()
        const close = page.getByRole('button', { name: '关闭资料面板', exact: true })
        await close.waitFor()
        await page.keyboard.press('Shift+Tab')
        assert.equal(await page.evaluate(() => document.activeElement.closest('.pane.sources') !== null), true)
        await page.keyboard.press('Escape')
        assert.equal(await trigger.evaluate(el => el === document.activeElement), true)
        assert.equal(await question.inputValue(), '不要丢失当前输入')
        await page.getByRole('button', { name: '历史', exact: true }).click()
        await page.getByRole('button', { name: '关闭历史面板', exact: true }).waitFor()
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true)
        if (width === 390) await page.screenshot({ path: 'target/browser-verification/layout-mobile.png' })
        await page.getByRole('button', { name: '关闭历史面板', exact: true }).click()
      }
    }
    assert.deepEqual(errors, [])
    console.log('PASS: drag, keyboard resize, reset, collapse, desktop/tablet/mobile overflow, drawers focus/Escape, input retained')
  } finally { await browser.close() }
}
main().catch(e => { console.error(e); process.exitCode = 1 })
