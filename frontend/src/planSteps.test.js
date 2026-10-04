import test from 'node:test'
import assert from 'node:assert/strict'
import { moveStep, removeStep } from './planSteps.js'

test('reorders steps without mutating the reviewed plan', () => {
  const original = ['Scope', 'Compare', 'Synthesize']
  assert.deepEqual(moveStep(original, 2, 0), ['Synthesize', 'Scope', 'Compare'])
  assert.deepEqual(original, ['Scope', 'Compare', 'Synthesize'])
})

test('keeps at least one step and rejects invalid removals', () => {
  assert.deepEqual(removeStep(['Scope', 'Compare'], 0), ['Compare'])
  const one = ['Scope']
  assert.equal(removeStep(one, 0), one)
  assert.equal(removeStep(one, 3), one)
})
