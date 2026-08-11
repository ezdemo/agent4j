/* @vitest-environment jsdom */

import {flushPromises, mount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import FileEditor from './FileEditor.vue'
import monacoMock from '../utils/monaco'

const readMock = vi.fn()
const writeMock = vi.fn()

vi.mock('ant-design-vue', () => ({
  message: {success: vi.fn(), error: vi.fn()}
}))

vi.mock('../utils/monaco', () => {
  class MockModel {
    constructor(value, uri) {
      this.value = value
      this.uri = uri
      this.version = 1
      this.listeners = new Set()
      this.dispose = vi.fn()
    }

    getValue() { return this.value }
    getAlternativeVersionId() { return this.version }
    setValue(value) {
      this.value = value
      this.version++
      for (const listener of this.listeners) listener()
    }
    onDidChangeContent(listener) {
      this.listeners.add(listener)
      return {dispose: () => this.listeners.delete(listener)}
    }
  }

  const createdModels = []
  const editorInstance = {
    addCommand: vi.fn(),
    dispose: vi.fn(),
    focus: vi.fn(),
    getModel: vi.fn(() => editorInstance.model || null),
    layout: vi.fn(),
    restoreViewState: vi.fn(),
    saveViewState: vi.fn(() => null),
    setModel: vi.fn((model) => { editorInstance.model = model })
  }
  const api = {
    KeyCode: {KeyS: 49},
    KeyMod: {CtrlCmd: 2048},
    Uri: {file: (path) => ({path})},
    __createdModels: createdModels,
    __editorInstance: editorInstance,
    editor: {
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

beforeEach(() => {
  readMock.mockReset().mockImplementation(async (path) => ({success: true, data: path.endsWith('demo.txt') ? 'original' : 'second'}))
  writeMock.mockReset().mockResolvedValue({success: true})
  monacoMock.__createdModels.length = 0
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
    const wrapper = mount(FileEditor, {props: {activeFile: firstFile, theme: 'gray'}})
    await flushPromises()

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
    const wrapper = mount(FileEditor, {props: {activeFile: firstFile, theme: 'gray'}})
    await flushPromises()
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
    const wrapper = mount(FileEditor, {props: {activeFile: firstFile, theme: 'gray'}})
    await flushPromises()
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

  it('reuses one editor while switching file models', async () => {
    const wrapper = mount(FileEditor, {props: {activeFile: firstFile, theme: 'gray'}})
    await flushPromises()
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
