export const DEFAULT_COLUMNS = { source: 248, studio: 284 }

export function fitColumns(width, source, studio, sourceCollapsed = false, studioCollapsed = false) {
  const available = Math.max(440, width - 380 - 20)
  let left = sourceCollapsed ? 44 : Math.max(220, Math.min(420, source))
  let right = studioCollapsed ? 44 : Math.max(220, Math.min(440, studio))
  const over = Math.max(0, left + right - available)
  if (over) {
    const leftRoom = sourceCollapsed ? 0 : left - 220
    const rightRoom = studioCollapsed ? 0 : right - 220
    const room = leftRoom + rightRoom
    if (room) { left -= Math.min(over, room) * leftRoom / room; right -= Math.min(over, room) * rightRoom / room }
  }
  return { source: Math.floor(left), studio: Math.floor(right) }
}
