import test from 'node:test'
import assert from 'node:assert/strict'
import { previewKind } from './reader.js'

test('every accepted upload format has a preview path', () => {
  for (const ext of ['pdf', 'PDF']) assert.equal(previewKind(ext), 'pdf')
  for (const ext of ['png', 'jpg', 'jpeg']) assert.equal(previewKind(ext), 'image')
  for (const ext of ['txt', 'md', 'markdown']) assert.equal(previewKind(ext), 'text')
  for (const ext of ['docx', 'pptx', 'html', 'htm', '']) assert.equal(previewKind(ext), 'extracted')
})
