const { chromium, request: playwrightRequest } = require('C:/Users/bula7/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const base = process.env.RP_E2E_BASE_URL || 'http://127.0.0.1:8080'
const ui = process.env.RP_E2E_UI_URL || 'http://127.0.0.1:5174'

async function main() {
  const browser = await chromium.launch({ headless: true, channel: 'msedge' })
  const topicName = `E2E-${Date.now()}`
  let topicId
  let sessionId
  const tracePath = path.resolve('target', 'real-e2e-trace.zip')
  fs.mkdirSync(path.dirname(tracePath), { recursive: true })
  try {
    const request = await requestContext(browser)
    const created = await request.post(`${base}/api/rp/topics`, {
      form: { name: topicName, researchGoal: '真实浏览器链路冒烟' }
    })
    assert.equal(created.ok(), true, `创建专题失败: ${created.status()}`)
    const topic = await created.json()
    topicId = topic.id

    const sessions = await request.get(`${base}/api/rp/notebooks/${topicId}/sessions`)
    assert.equal(sessions.ok(), true,
      `会话 API 不可用: ${sessions.status()}；当前后端可能是旧实例，请先启动包含 ResearchPilot 会话 API 的源码服务`)
    const list = await sessions.json()
    // 新专题允许先进入工作区再发问；显式创建空会话后验证草稿与浏览器恢复链路。
    if (!Array.isArray(list) || list.length === 0) {
      const createdSession = await request.post(`${base}/api/rp/notebooks/${topicId}/sessions`, {
        data: { title: 'E2E 会话' }
      })
      assert.equal(createdSession.status(), 201, `创建真实会话失败: ${createdSession.status()}`)
      sessionId = (await createdSession.json()).id
    }
    else {
      sessionId = list[0].id
    }

    await request.post(`${base}/api/rp/notebooks/${topicId}/sessions/${sessionId}/draft`, {
      data: { quickQ: '真实草稿恢复校验', mode: 'quick', allowWeb: false }
    }).then(async response => assert.equal(response.ok(), true, `草稿保存失败: ${response.status()}`))
    const draft = await request.get(`${base}/api/rp/notebooks/${topicId}/sessions/${sessionId}/draft`)
    assert.equal(draft.ok(), true)
    assert.match((await draft.json()).draft, /真实草稿恢复校验/)

    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
       const tracing = process.env.RP_E2E_TRACE === 'true'
       if (tracing) await context.tracing.start({ screenshots: true, snapshots: true, sources: true })
    const page = await context.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
       await page.goto(`${ui}/notebooks/${topicId}?session=${sessionId}`, { waitUntil: 'domcontentloaded' })
       await page.waitForTimeout(2000)
       const candidates = page.locator('textarea[aria-label="研究问题"], textarea[aria-label="继续提问…"]')
       let questionBox = null
       for (let i = 0; i < await candidates.count(); i++) {
         const candidate = candidates.nth(i)
         if (await candidate.isVisible()) { questionBox = candidate; break }
       }
       assert.ok(questionBox, '研究输入框未显示')
       assert.equal(await questionBox.inputValue(), '真实草稿恢复校验')
    assert.deepEqual(errors, [])
       if (tracing) await context.tracing.stop({ path: tracePath })
    await context.close()
    await request.dispose()
       console.log(`PASS: real backend e2e topic=${topicId} session=${sessionId}${tracing ? ` trace=${tracePath}` : ''}`)
  } finally {
    if (topicId) {
      const cleanup = await requestContext(browser)
      await cleanup.post(`${base}/api/rp/topics/${topicId}/archive`).catch(() => {})
      await cleanup.dispose()
    }
    await browser.close()
  }
}

async function requestContext() {
  return playwrightRequest.newContext({ timeout: 15000, extraHTTPHeaders: { 'X-ResearchPilot-E2E': 'true' } })
}

main().catch(error => {
  console.error(`FAIL: ${error.stack || error.message}`)
  process.exitCode = 1
})
