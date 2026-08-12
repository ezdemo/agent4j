import {describe, expect, it} from 'vitest'
import {computeDirtyDiff, dirtyDiffDecorations} from './dirtyDiff'

describe('dirtyDiff', () => {
  it('classifies added, modified, and deleted line ranges', () => {
    expect(computeDirtyDiff('a\nb', 'a\nnew\nb')).toEqual([
      expect.objectContaining({type: 'added', startLine: 2, endLine: 2})
    ])
    expect(computeDirtyDiff('a\nb\nc', 'a\nchanged\nc')).toEqual([
      expect.objectContaining({type: 'modified', startLine: 2, endLine: 2, originalStartLine: 2})
    ])
    expect(computeDirtyDiff('a\nb\nc', 'a\nc')).toEqual([
      expect.objectContaining({type: 'deleted', startLine: 1, endLine: 1, originalStartLine: 2})
    ])
  })

  it('creates gutter, overview ruler, and minimap decorations', () => {
    class Range {
      constructor(startLineNumber, startColumn, endLineNumber, endColumn) {
        Object.assign(this, {startLineNumber, startColumn, endLineNumber, endColumn})
      }
    }
    const monaco = {
      Range,
      editor: {
        MinimapPosition: {Gutter: 2},
        OverviewRulerLane: {Left: 1}
      }
    }

    const [decoration] = dirtyDiffDecorations(monaco, [
      {type: 'modified', startLine: 3, endLine: 4}
    ], 'dark')

    expect(decoration.range).toMatchObject({startLineNumber: 3, endLineNumber: 4})
    expect(decoration.options.linesDecorationsClassName).toContain('dirty-diff-modified')
    expect(decoration.options.linesDecorationsTooltip).toBe('点击查看更改')
    expect(decoration.options.overviewRuler.position).toBe(1)
    expect(decoration.options.minimap.position).toBe(2)
  })
})
