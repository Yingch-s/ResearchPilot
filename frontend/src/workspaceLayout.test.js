import test from 'node:test'
import assert from 'node:assert/strict'
import { fitColumns, DEFAULT_COLUMNS } from './workspaceLayout.js'

test('large requested sidebars leave at least 380px for research on desktop', () => {
  for (const width of [996, 1100, 1440, 1920]) {
    const fitted = fitColumns(width, 420, 440)
    assert.ok(fitted.source >= 220 && fitted.studio >= 220)
    assert.ok(width - fitted.source - fitted.studio - 20 >= 380)
  }
})

test('collapsed sidebar occupies 44px and reset fits default proportions', () => {
  assert.deepEqual(fitColumns(1400, 420, 440, true, true), { source: 44, studio: 44 })
  assert.deepEqual(fitColumns(1400, DEFAULT_COLUMNS.source, DEFAULT_COLUMNS.studio), DEFAULT_COLUMNS)
})
