const { chromium } = require('C:/Users/bula7/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert = require('node:assert/strict')

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
    const errors = []
    const drafts = new Map()
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/api/**', async route => {
      const request = route.request()
      const path = new URL(request.url()).pathname
      let body = []
      if (path === '/api/rp/topics') body = [{ id: 'state-review', name: '会话状态隔离验收' }]
      else if (path.endsWith('/research/page')) body = { items: [], has_more: false }
      else if (path.endsWith('/draft')) {
        const id = path.split('/').at(-2)
        if (request.method() === 'POST') drafts.set(id, request.postDataJSON())
        body = { draft: drafts.has(id) ? JSON.stringify(drafts.get(id)) : null }
      }
      else if (path.endsWith('/plan-draft')) body = { goal: '研究问题', steps: ['搜索论文', '核对来源'], scope_papers: [], dimensions: [] }
      await route.fulfill({ json: body })
    })
    await page.goto('http://127.0.0.1:5174/notebooks/state-review')
    const question = page.getByRole('textbox', { name: '研究问题', exact: true })
    await question.fill('第一段对话的研究目标')
    await page.getByRole('checkbox', { name: '联网搜索', exact: true }).check()
    await page.getByRole('checkbox', { name: '深度研究 生成报告' }).check()
    const firstId = new URL(page.url()).searchParams.get('session')
    await page.getByRole('button', { name: '＋ 新建对话' }).click()
    assert.equal(await question.inputValue(), '')
    assert.equal(await page.getByRole('checkbox', { name: '联网搜索', exact: true }).isChecked(), false)
    assert.equal(await page.getByRole('checkbox', { name: '深度研究 生成报告' }).isChecked(), false)
    const secondId = new URL(page.url()).searchParams.get('session')
    assert.notEqual(firstId, secondId)
    await question.fill('第二段的快速问题')
    await page.locator('.history-item').nth(1).click()
    assert.equal(await question.inputValue(), '第一段对话的研究目标')
    assert.equal(await page.getByRole('checkbox', { name: '联网搜索', exact: true }).isChecked(), true)
    assert.equal(await page.getByRole('checkbox', { name: '深度研究 生成报告' }).isChecked(), true)
    await page.locator('.entry-submit').click()
    await page.locator('.compact-plan').waitFor()
    assert.equal(await page.locator('.compact-plan').count(), 1)
    await page.getByRole('button', { name: '＋ 新建对话' }).click()
    assert.equal(await page.locator('.compact-plan').count(), 0)
    assert.equal(await page.locator('.research-activity').count(), 0)
    assert.equal(await question.inputValue(), '')
    await page.waitForTimeout(900)
    assert.equal(drafts.get(secondId)?.quickQ, '第二段的快速问题')
    assert.deepEqual(errors, [])
    console.log('PASS: new chat defaults, session-specific drafts and switches, no stale plan/run, delayed writes keep original session identity')
  } finally { await browser.close() }
}
main().catch(error => { console.error(error); process.exitCode = 1 })
