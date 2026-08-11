import {diffArrays} from 'diff'

const MAX_CONTENT_LENGTH = 500 * 1024
const MAX_LINE_COUNT = 5000

const COLORS = {
  gray: {
    added: '#81b88b',
    modified: '#66afe0',
    deleted: '#ca4b51'
  },
  dark: {
    added: '#587c0c',
    modified: '#0c7d9d',
    deleted: '#94151b'
  }
}

function linesOf(content) {
  return String(content || '').replace(/\r\n/g, '\n').split('\n')
}

export function computeDirtyDiff(original, modified) {
  if (original.length > MAX_CONTENT_LENGTH || modified.length > MAX_CONTENT_LENGTH) return []
  const originalLines = linesOf(original)
  const modifiedLines = linesOf(modified)
  if (originalLines.length > MAX_LINE_COUNT || modifiedLines.length > MAX_LINE_COUNT) return []

  const parts = diffArrays(originalLines, modifiedLines, {timeout: 1000})
  if (!parts) return []

  const changes = []
  let originalLine = 1
  let modifiedLine = 1
  for (let index = 0; index < parts.length;) {
    const part = parts[index]
    if (!part.added && !part.removed) {
      originalLine += part.count
      modifiedLine += part.count
      index++
      continue
    }

    let removed = 0
    let added = 0
    while (index < parts.length && (parts[index].added || parts[index].removed)) {
      if (parts[index].removed) removed += parts[index].count
      if (parts[index].added) added += parts[index].count
      index++
    }

    const range = {
      originalStartLine: originalLine,
      originalEndLine: originalLine + removed - 1,
      modifiedStartLine: modifiedLine,
      modifiedEndLine: modifiedLine + added - 1
    }
    if (removed && added) {
      changes.push({...range, type: 'modified', startLine: modifiedLine, endLine: modifiedLine + added - 1})
    } else if (added) {
      changes.push({...range, type: 'added', startLine: modifiedLine, endLine: modifiedLine + added - 1})
    } else if (removed) {
      const line = Math.max(1, modifiedLine - 1)
      changes.push({...range, type: 'deleted', startLine: line, endLine: line})
    }
    originalLine += removed
    modifiedLine += added
  }
  return changes
}

export function dirtyDiffDecorations(monaco, changes, theme) {
  const colors = COLORS[theme === 'dark' ? 'dark' : 'gray']
  return changes.map((change) => {
    const deleted = change.type === 'deleted'
    const line = Math.max(1, change.startLine)
    return {
      range: new monaco.Range(line, deleted ? Number.MAX_SAFE_INTEGER : 1, Math.max(line, change.endLine), deleted ? Number.MAX_SAFE_INTEGER : 1),
      options: {
        isWholeLine: !deleted,
        linesDecorationsClassName: `dirty-diff-glyph dirty-diff-${change.type}`,
        linesDecorationsTooltip: '点击查看更改',
        overviewRuler: {
          color: colors[change.type] + '99',
          position: monaco.editor.OverviewRulerLane.Left
        },
        minimap: {
          color: colors[change.type],
          position: monaco.editor.MinimapPosition.Gutter
        }
      }
    }
  })
}
