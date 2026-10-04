export function diffLines(oldText, newText) {
  const before = String(oldText || '').split(/\r?\n/)
  const after = String(newText || '').split(/\r?\n/)
  const result = []
  const emit = (kind, text) => result.push({ kind, marker: { same: ' ', added: '+', removed: '-' }[kind], text })
  let prefix = 0
  while (prefix < before.length && prefix < after.length && before[prefix] === after[prefix]) emit('same', before[prefix++])
  let oldEnd = before.length
  let newEnd = after.length
  while (oldEnd > prefix && newEnd > prefix && before[oldEnd - 1] === after[newEnd - 1]) { oldEnd--; newEnd-- }
  const n = oldEnd - prefix
  const m = newEnd - prefix
  // Bound allocation for unusually large reports; preserve shared prefix/suffix in the fallback.
  if (n * m > 2_000_000) {
    for (let i = prefix; i < oldEnd; i++) emit('removed', before[i])
    for (let j = prefix; j < newEnd; j++) emit('added', after[j])
  } else {
    const table = Array.from({ length: n + 1 }, () => new Uint32Array(m + 1))
    for (let i = n - 1; i >= 0; i--) {
      for (let j = m - 1; j >= 0; j--) {
        table[i][j] = before[prefix + i] === after[prefix + j]
          ? table[i + 1][j + 1] + 1 : Math.max(table[i + 1][j], table[i][j + 1])
      }
    }
    let i = 0
    let j = 0
    while (i < n || j < m) {
      if (i < n && j < m && before[prefix + i] === after[prefix + j]) { emit('same', before[prefix + i++]); j++ }
      else if (i < n && (j === m || table[i + 1][j] >= table[i][j + 1])) emit('removed', before[prefix + i++])
      else emit('added', after[prefix + j++])
    }
  }
  for (let i = oldEnd; i < before.length; i++) emit('same', before[i])
  return result
}
