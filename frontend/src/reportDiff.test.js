import test from 'node:test'
import assert from 'node:assert/strict'
import { diffLines } from './reportDiff.js'

test('inserting a report section does not mark subsequent unchanged findings as changed', () => {
  const diff = diffLines('目标\n结论 A\n结论 B\n来源', '目标\n新增说明\n结论 A\n结论 B\n来源')
  assert.deepEqual(diff.filter(line => line.kind !== 'same'), [{ kind: 'added', marker: '+', text: '新增说明' }])
})

test('diff reconstructs both versions including duplicate lines and deletions', () => {
  const before = 'A\nB\nA\nC'
  const after = 'A\nA\nD\nC'
  const diff = diffLines(before, after)
  assert.equal(diff.filter(line => line.kind !== 'added').map(line => line.text).join('\n'), before)
  assert.equal(diff.filter(line => line.kind !== 'removed').map(line => line.text).join('\n'), after)
  assert.equal(diff.filter(line => line.kind !== 'same').length, 2)
})
