/* @vitest-environment jsdom */

import {flushPromises, mount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import FileEditor from './FileEditor.vue'
import monacoMock from '../utils/monaco'

const readMock = vi.fn()
const writeMock = vi.fn()
const fileContentMock = vi.hoisted(() => vi.fn())

vi.mock('ant-design-vue', () => ({
  message: {success: vi.fn(), error: vi.fn()}
}))

vi.mock('../services/api', () => ({
  gitAPI: {fileContent: (...args) => fileContentMock(...args)}
}))

vi.mock('monaco-editor/nls/lang/zh-cn', () => ({}))

vi.mock('../utils/monaco', () => {
  class MockModel {
    constructor(value, uri) {
      this.value = value
      this.uri = uri
      this.version = 1
      this.listeners = new Set()
      this.decorations = []
      this.dispose = vi.fn()
    }

    getValue() { return this.value }
    getAlternativeVersionId() { return this.version }
    getEOL() { return '\n' }
    getFullModelRange() { return {startLineNumber: 1, startColumn: 1, endLineNumber: 1, endColumn: this.value.length + 1} }
    setValue(value) {
      this.value = value
      this.version++
      for (const listener of this.listeners) listener()
    }
    deltaDecorations(_oldIds, decorations) {
      this.decorations = decorations
      return decorations.map((_, index) => `decoration-${index}`)
    }
    isDisposed() { return false }
    onDidChangeContent(listener) {
      this.listeners.add(listener)
      return {dispose: () => this.listeners.delete(listener)}
    }
  }

  const createdModels = []
  const zones = new Map()
  let mouseHandler = null
  let mouseMoveHandler = null
  let mouseLeaveHandler = null
  let keyHandler = null
  let nextZoneId = 1
  const editorInstance = {
    addCommand: vi.fn(),
    changeViewZones: vi.fn((callback) => callback({
      addZone: (zone) => {
        const id = `zone-${nextZoneId++}`
        zones.set(id, zone)
        return id
      },
      removeZone: (id) => zones.delete(id)
    })),
    dispose: vi.fn(),
    executeEdits: vi.fn((_source, edits) => {
      editorInstance.model?.setValue(edits[0].text)
      return true
    }),
    focus: vi.fn(),
    getModel: vi.fn(() => editorInstance.model || null),
    layout: vi.fn(),
    onKeyDown: vi.fn((handler) => {
      keyHandler = handler
      return {dispose: vi.fn()}
    }),
    onMouseDown: vi.fn((handler) => {
      mouseHandler = handler
      return {dispose: vi.fn()}
    }),
    onMouseMove: vi.fn((handler) => {
      mouseMoveHandler = handler
      return {dispose: vi.fn()}
    }),
    onDidMouseLeave: vi.fn((handler) => {
      mouseLeaveHandler = handler
      return {dispose: vi.fn()}
    }),
    pushUndoStop: vi.fn(),
    restoreViewState: vi.fn(),
    revealLineInCenter: vi.fn(),
    saveViewState: vi.fn(() => null),
    setModel: vi.fn((model) => { editorInstance.model = model })
  }
  const api = {
    KeyCode: {Escape: 9, KeyS: 49},
    KeyMod: {CtrlCmd: 2048},
    Range: class {
      constructor(startLineNumber, startColumn, endLineNumber, endColumn) {
        Object.assign(this, {startLineNumber, startColumn, endLineNumber, endColumn})
      }
    },
    Uri: {file: (path) => ({path})},
    __createdModels: createdModels,
    __editorInstance: editorInstance,
    __fireKeyDown: (event) => keyHandler?.(event),
    __fireMouseDown: (event) => mouseHandler?.(event),
    __fireMouseMove: (event) => mouseMoveHandler?.(event),
    __fireMouseLeave: () => mouseLeaveHandler?.(),
    __zones: zones,
    editor: {
      MinimapPosition: {Gutter: 2},
      MouseTargetType: {GUTTER_LINE_DECORATIONS: 4},
      OverviewRulerLane: {Left: 1},
      create: vi.fn(() => editorInstance),
      createModel: vi.fn((value, _language, uri) => {
        const model = new MockModel(value, uri)
        createdModels.push(model)
        return model
      }),
      setTheme: vi.fn()
    }
  }
  return {default: api}
})

const initialElectronAPI = window.electronAPI
const firstFile = {id: 'file-1', path: 'C:/workspace/demo.txt', name: 'demo.txt', dirty: false}

async function mountEditor(props = {}) {
  const wrapper = mount(FileEditor, {props: {activeFile: firstFile, theme: 'gray', ...props}})
  await flushPromises()
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  readMock.mockReset().mockImplementation(async (path) => ({success: true, data: path.endsWith('demo.txt') ? 'original' : 'second'}))
  writeMock.mockReset().mockResolvedValue({success: true})
  fileContentMock.mockReset().mockResolvedValue({success: true, data: {content: 'original'}})
  monacoMock.__createdModels.length = 0
  monacoMock.__zones.clear()
  monacoMock.__editorInstance.model = null
  vi.clearAllMocks()
  window.electronAPI = {
    fileExplorer: {
      read: (...args) => readMock(...args),
      write: (...args) => writeMock(...args)
    }
  }
})

afterEach(() => {
  vi.restoreAllMocks()
  if (initialElectronAPI === undefined) delete window.electronAPI
  else window.electronAPI = initialElectronAPI
})

describe('FileEditor Monaco models', () => {
  it('saves model changes through Electron', async () => {
    const wrapper = await mountEditor()

    const model = monacoMock.__createdModels[0]
    expect(readMock).toHaveBeenCalledWith(firstFile.path)
    expect(model.getValue()).toBe('original')

    model.setValue('changed')
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('dirtyChange')?.at(-1)).toEqual([firstFile.path, true])

    await wrapper.vm.save()
    await flushPromises()

    expect(writeMock).toHaveBeenCalledWith(firstFile.path, 'changed')
    expect(wrapper.emitted('saved')?.at(-1)).toEqual([firstFile.path])
    expect(wrapper.emitted('dirtyChange')?.at(-1)).toEqual([firstFile.path, false])
    wrapper.unmount()
  })

  it('keeps later edits dirty while an earlier version is saving', async () => {
    let finishWrite
    writeMock.mockReturnValue(new Promise((resolve) => { finishWrite = resolve }))
    const wrapper = await mountEditor()
    const model = monacoMock.__createdModels[0]

    model.setValue('saving version')
    await wrapper.vm.$nextTick()
    void wrapper.vm.save()
    await wrapper.vm.$nextTick()
    expect(writeMock).toHaveBeenCalledWith(firstFile.path, 'saving version')

    model.setValue('newer unsaved version')
    finishWrite({success: true})
    await flushPromises()

    expect(wrapper.emitted('dirtyChange')?.at(-1)).toEqual([firstFile.path, true])
    wrapper.unmount()
  })

  it('ignores stale reads when switching back to a loading file', async () => {
    const pendingReads = []
    readMock.mockImplementation((path) => new Promise((resolve) => pendingReads.push({path, resolve})))
    const wrapper = await mountEditor()
    const secondFile = {id: 'file-2', path: 'C:/workspace/second.js', name: 'second.js', dirty: false}

    await wrapper.setProps({activeFile: secondFile})
    await flushPromises()
    await wrapper.setProps({activeFile: firstFile})
    await flushPromises()
    expect(pendingReads.map(({path}) => path)).toEqual([firstFile.path, secondFile.path, firstFile.path])

    pendingReads[0].resolve({success: true, data: 'stale'})
    await flushPromises()
    expect(monacoMock.__createdModels).toHaveLength(0)

    pendingReads[2].resolve({success: true, data: 'latest'})
    await flushPromises()
    expect(monacoMock.__createdModels[0].getValue()).toBe('latest')

    pendingReads[1].resolve({success: true, data: 'second'})
    await flushPromises()
    expect(monacoMock.__createdModels).toHaveLength(2)
    wrapper.unmount()
  })

  it('loads the HEAD baseline with a relative path and decorates live changes', async () => {
    const wrapper = await mountEditor({workspaceHash: 'workspace-1', workspacePath: 'C:/workspace'})
    await flushPromises()
    const model = monacoMock.__createdModels[0]

    expect(fileContentMock).toHaveBeenCalledWith('workspace-1', 'demo.txt', 'HEAD', {silent: true})
    model.setValue('changed')
    await new Promise((resolve) => setTimeout(resolve, 320))

    expect(model.decorations).toHaveLength(1)
    expect(model.decorations[0].options.linesDecorationsClassName).toContain('dirty-diff-modified')
    wrapper.unmount()
  })

  it('expands the complete multi-line hunk while hovering one gutter segment', async () => {
    readMock.mockResolvedValue({success: true, data: 'new 1\nnew 2\nnew 3'})
    fileContentMock.mockResolvedValue({success: true, data: {content: 'old 1\nold 2\nold 3'}})
    const wrapper = await mountEditor({workspaceHash: 'workspace-1', workspacePath: 'C:/workspace'})
    const model = monacoMock.__createdModels[0]

    monacoMock.__fireMouseMove({
      target: {
        type: monacoMock.editor.MouseTargetType.GUTTER_LINE_DECORATIONS,
        element: {classList: {contains: (name) => name === 'dirty-diff-glyph'}},
        position: {lineNumber: 2}
      }
    })

    expect(model.decorations).toHaveLength(1)
    expect(model.decorations[0].range).toMatchObject({startLineNumber: 1, endLineNumber: 3})
    expect(model.decorations[0].options.linesDecorationsClassName).toContain('dirty-diff-hover')

    monacoMock.__fireMouseLeave()
    expect(model.decorations).toHaveLength(0)
    wrapper.unmount()
  })

  it('opens and closes a diff peek from a gutter decoration', async () => {
    const wrapper = await mountEditor({workspaceHash: 'workspace-1', workspacePath: 'C:/workspace'})
    const model = monacoMock.__createdModels[0]
    model.setValue('changed')
    await new Promise((resolve) => setTimeout(resolve, 320))

    monacoMock.__fireMouseDown({
      target: {
        type: monacoMock.editor.MouseTargetType.GUTTER_LINE_DECORATIONS,
        element: {classList: {contains: (name) => name === 'dirty-diff-glyph'}},
        position: {lineNumber: 1}
      }
    })

    expect(monacoMock.__zones.size).toBe(1)
    const zone = [...monacoMock.__zones.values()][0]
    expect(zone.domNode.classList.contains('dirty-diff-peek-zone')).toBe(true)
    expect(zone.domNode.querySelector('.dirty-diff-peek')).not.toBeNull()
    expect(zone.domNode.querySelectorAll('.dirty-diff-peek-button')).toHaveLength(4)
    expect(zone.domNode.querySelector('.codicon-discard')).not.toBeNull()
    expect(zone.domNode.textContent).toContain('1 / 1')
    expect(zone.domNode.textContent).not.toContain('demo.txt')
    expect(zone.domNode.textContent).toContain('original')

    monacoMock.__fireKeyDown({keyCode: monacoMock.KeyCode.Escape, preventDefault: vi.fn(), stopPropagation: vi.fn()})
    expect(monacoMock.__zones.size).toBe(0)
    wrapper.unmount()
  })

  it('reverts only the selected dirty diff hunk', async () => {
    const wrapper = await mountEditor({workspaceHash: 'workspace-1', workspacePath: 'C:/workspace'})
    const model = monacoMock.__createdModels[0]
    model.setValue('changed')
    await new Promise((resolve) => setTimeout(resolve, 320))

    monacoMock.__fireMouseDown({
      target: {
        type: monacoMock.editor.MouseTargetType.GUTTER_LINE_DECORATIONS,
        element: {classList: {contains: (name) => name === 'dirty-diff-glyph'}},
        position: {lineNumber: 1}
      }
    })
    const zone = [...monacoMock.__zones.values()][0]
    zone.domNode.querySelector('[title="回滚此更改"]').click()

    expect(model.getValue()).toBe('original')
    expect(monacoMock.__editorInstance.executeEdits).toHaveBeenCalledWith(
      'dirtyDiff.revert',
      expect.any(Array)
    )
    expect(monacoMock.__zones.size).toBe(0)
    wrapper.unmount()
  })

  it('reuses one editor while switching file models', async () => {
    const wrapper = await mountEditor()
    const firstModel = monacoMock.__createdModels[0]
    const secondFile = {id: 'file-2', path: 'C:/workspace/second.js', name: 'second.js', dirty: false}

    await wrapper.setProps({activeFile: secondFile})
    await flushPromises()
    const secondModel = monacoMock.__createdModels[1]
    expect(readMock).toHaveBeenCalledWith(secondFile.path)
    expect(monacoMock.__editorInstance.setModel).toHaveBeenLastCalledWith(secondModel)

    await wrapper.setProps({activeFile: firstFile})
    await flushPromises()
    expect(readMock.mock.calls.filter(([path]) => path === firstFile.path)).toHaveLength(1)
    expect(monacoMock.__editorInstance.setModel).toHaveBeenLastCalledWith(firstModel)
    wrapper.unmount()
  })
})
