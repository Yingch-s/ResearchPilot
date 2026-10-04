export function moveStep(steps, from, to) {
  if (!Array.isArray(steps) || from === to || from < 0 || to < 0 || from >= steps.length || to >= steps.length) return steps
  const next = [...steps]
  const [step] = next.splice(from, 1)
  next.splice(to, 0, step)
  return next
}

export function removeStep(steps, index) {
  if (!Array.isArray(steps) || steps.length <= 1 || index < 0 || index >= steps.length) return steps
  return steps.filter((_, i) => i !== index)
}
