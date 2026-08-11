/* @vitest-environment jsdom */

import {flushPromises, mount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import FileExplorer from './FileExplorer.vue'

const listMock = vi.fn()
const createMock = vi.fn()
const renameMock = vi.fn()
const removeMock = vi.fn()
const readMock = vi.fn()
const searchMock = vi.fn()
const gitStatusMock = vi.fn()
const modalConfirmMock = vi.fn()

vi.mock('../services/api', () => ({
  gitAPI: {status: (...args) => gitStatusMock(...args)}
}))

vi.mock('ant-design-vue', () => ({
  Modal: {confirm: (...args) => modalConfirmMock(...args)},
  message: {success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn()}
}))

const initialElectronAPI = window.electronAPI

function mountExplorer(props) {
  return mount(FileExplorer, {
    props: {rootPath: 'C:/workspace', ...props},
    global: {stubs: {DiffViewer: true}}
  })
}

/** 挂载一棵含 src 目录 + readme.md 文件的树 */
function mountTree() {
  listMock.mockResolvedValue({
    success: true,
    data: [
      {name: 'src', path: 'C:/workspace/src', directory: true},
      {name: 'readme.md', path: 'C:/workspace/readme.md', directory: false}
    ]
  })
  return mountExplorer()
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

beforeEach(() => {
  listMock.mockReset().mockResolvedValue({success: true, data: []})
  createMock.mockReset().mockResolvedValue({success: true, data: {name: 'new.js', path: 'C:/workspace/new.js', directory: false}})
  renameMock.mockReset().mockResolvedValue({success: true, data: {name: 'renamed.md', path: 'C:/workspace/renamed.md', directory: false}})
  removeMock.mockReset().mockResolvedValue({success: true})
  readMock.mockReset().mockResolvedValue({success: true, data: 'hello'})
  searchMock.mockReset().mockResolvedValue({success: true, data: []})
  gitStatusMock.mockReset().mockResolvedValue({success: true, data: {initialized: false}})
  modalConfirmMock.mockReset().mockImplementation(({onOk}) => onOk?.())
  window.electronAPI = {
    fileExplorer: {
      list: (...args) => listMock(...args),
      create: (...args) => createMock(...args),
      rename: (...args) => renameMock(...args),
      remove: (...args) => removeMock(...args),
      read: (...args) => readMock(...args),
      search: (...args) => searchMock(...args)
    },
    openFolder: vi.fn().mockResolvedValue({success: true})
  }
})

afterEach(() => {
  vi.restoreAllMocks()
  if (initialElectronAPI === undefined) delete window.electronAPI
  else window.electronAPI = initialElectronAPI
})

describe('FileExplorer 文件树', () => {
  it('挂载时列出根目录并渲染节点', async () => {
    const wrapper = mountTree()
    await flushPromises()

    expect(listMock).toHaveBeenCalledWith('C:/workspace')
    expect(wrapper.findAll('.fen-row').length).toBe(2)
    expect(wrapper.text()).toContain('readme.md')
    expect(wrapper.text()).toContain('src')
    wrapper.unmount()
  })

  it('展开目录时懒加载子目录', async () => {
    const wrapper = mountTree()
    await flushPromises()
    listMock.mockResolvedValue({
      success: true,
      data: [{name: 'main.js', path: 'C:/workspace/src/main.js', directory: false}]
    })

    await wrapper.findAll('.fen-row')[0].trigger('click')
    await flushPromises()

    expect(listMock).toHaveBeenLastCalledWith('C:/workspace/src')
    expect(wrapper.text()).toContain('main.js')
    wrapper.unmount()
  })

  it('按深度移动节点内容，导引线始终停留在图标左侧', async () => {
    listMock.mockResolvedValueOnce({
      success: true,
      data: [{name: 'root', path: 'C:/workspace/root', directory: true}]
    }).mockResolvedValueOnce({
      success: true,
      data: [{name: 'child', path: 'C:/workspace/root/child', directory: true}]
    }).mockResolvedValueOnce({
      success: true,
      data: [{name: 'leaf.txt', path: 'C:/workspace/root/child/leaf.txt', directory: false}]
    })
    const wrapper = mountExplorer()
    await flushPromises()

    await wrapper.findAll('.fen-row')[0].trigger('click')
    await flushPromises()

    const rows = wrapper.findAll('.fen-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].find('.fen-name').text()).toContain('root')
    expect(rows[0].find('.fen-name').text()).toContain('child')
    expect(rows[0].find('.fen-compact-separator').exists()).toBe(true)
    const leafRow = rows[1]
    expect(leafRow.find('.fen-twistie-placeholder').element.style.marginLeft).toBe('16px')
    expect(leafRow.find('.fen-indent').element.style.left).toBe('16px')
    expect(leafRow.findAll('.fen-indent-guide')).toHaveLength(1)
    wrapper.unmount()
  })
  it('按扩展名显示彩色文件图标', async () => {
    const wrapper = mountTree()
    await flushPromises()

    const icon = wrapper.find('.fen-file-icon[data-icon="markdown"]')
    expect(icon.exists()).toBe(true)
    expect(icon.text().codePointAt(0)).toBe(0xE05C)
    wrapper.unmount()
  })

  it('显示文件状态字母和父目录 Git 装饰圆点', async () => {
    gitStatusMock.mockResolvedValue({
      success: true,
      data: {
        initialized: true,
        changed: [{path: 'src/main.js', status: 'M'}],
        untracked: []
      }
    })
    listMock.mockResolvedValueOnce({
      success: true,
      data: [{name: 'src', path: 'C:/workspace/src', directory: true}]
    }).mockResolvedValueOnce({
      success: true,
      data: [{name: 'main.js', path: 'C:/workspace/src/main.js', directory: false}]
    })
    const wrapper = mountExplorer({workspaceHash: 'workspace-hash'})
    await flushPromises()

    expect(gitStatusMock).toHaveBeenCalledWith('workspace-hash')
    expect(wrapper.find('.fen-decoration.is-directory').exists()).toBe(true)
    await wrapper.find('.fen-row').trigger('click')
    await flushPromises()
    const icon = wrapper.find('.fen-file-icon[data-icon="javascript"]')
    expect(icon.exists()).toBe(true)
    expect(icon.text().codePointAt(0)).toBe(0xE04D)
    expect(wrapper.find('.fen-decoration:not(.is-directory)').text()).toBe('M')
    wrapper.unmount()
  })

  it('右键文件可添加到当前对话', async () => {
    const wrapper = mountTree()
    await flushPromises()

    await wrapper.findAll('.fen-row')[1].trigger('contextmenu', {clientX: 100, clientY: 120})
    const menu = document.body.querySelector('.fe-context-menu')
    const addButton = [...menu.querySelectorAll('button')].find((button) => button.textContent.includes('添加到对话'))
    expect(addButton).toBeTruthy()
    await addButton.click()

    expect(wrapper.emitted('addToSession')).toEqual([[{file: 'C:/workspace/readme.md'}]])
    wrapper.unmount()
    menu.remove()
  })

  it('拖动文件时写入聊天输入区识别的文件路径格式', async () => {
    const wrapper = mountTree()
    await flushPromises()
    const setData = vi.fn()
    const dataTransfer = {effectAllowed: '', setData}
    const fileRow = wrapper.findAll('.fen-row')[1]

    expect(fileRow.attributes('draggable')).toBe('true')
    await fileRow.trigger('dragstart', {dataTransfer})

    expect(dataTransfer.effectAllowed).toBe('copy')
    expect(setData).toHaveBeenCalledWith('application/x-loopra-file-path', 'C:/workspace/readme.md')
    expect(setData).toHaveBeenCalledWith('text/plain', 'C:/workspace/readme.md')
    wrapper.unmount()
  })

  it('单击文件仅选中，双击打开预览', async () => {
    const wrapper = mountTree()
    await flushPromises()
    const rows = wrapper.findAll('.fen-row')
    const fileRow = rows[1]

    await fileRow.trigger('click')
    expect(wrapper.find('.fen-row.active').text()).toContain('readme.md')

    await fileRow.trigger('dblclick')
    await flushPromises()
    expect(readMock).toHaveBeenCalledWith('C:/workspace/readme.md')
    wrapper.unmount()
  })
})

describe('FileExplorer 新建/重命名/删除', () => {
  it('顶部新建文件：输入名称回车后调用 create 并刷新目录', async () => {
    const wrapper = mountExplorer()
    await flushPromises()

    await wrapper.find('button[aria-label="新建文件"]').trigger('click')
    await flushPromises()
    const input = wrapper.find('input.fen-edit-input')
    expect(input.exists()).toBe(true)

    await input.setValue('new.js')
    await input.trigger('keydown.enter')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith('C:/workspace', 'new.js', 'file')
    // 创建成功后重新拉取目录
    expect(listMock).toHaveBeenLastCalledWith('C:/workspace')
    wrapper.unmount()
  })

  it('右键目录新建文件夹：占位节点 + 回车创建目录', async () => {
    const wrapper = mountTree()
    await flushPromises()
    listMock.mockResolvedValue({success: true, data: []})

    // 展开 src 目录
    await wrapper.findAll('.fen-row')[0].trigger('click')
    await flushPromises()

    // 右键 src 目录 → 新建文件夹
    await wrapper.findAll('.fen-row')[0].trigger('contextmenu', {clientX: 100, clientY: 120})
    const menu = document.body.querySelector('.fe-context-menu')
    const buttons = menu.querySelectorAll('button')
    const newFolderButton = [...buttons].find((b) => b.textContent.includes('新建文件夹'))
    await newFolderButton.click()
    await flushPromises()

    const input = wrapper.find('input.fen-edit-input')
    expect(input.exists()).toBe(true)
    await input.setValue('docs')
    await input.trigger('keydown.enter')
    await flushPromises()

    expect(createMock).toHaveBeenCalledWith('C:/workspace/src', 'docs', 'directory')
    wrapper.unmount()
    menu.remove()
  })

  it('右键重命名：预填旧名，回车调用 rename', async () => {
    const wrapper = mountTree()
    await flushPromises()

    await wrapper.findAll('.fen-row')[1].trigger('contextmenu', {clientX: 100, clientY: 120})
    const menu = document.body.querySelector('.fe-context-menu')
    const buttons = menu.querySelectorAll('button')
    const renameButton = [...buttons].find((b) => b.textContent.includes('重命名'))
    await renameButton.click()
    await flushPromises()

    const input = wrapper.find('input.fen-edit-input')
    expect(input.element.value).toBe('readme.md')
    await input.setValue('renamed.md')
    await input.trigger('keydown.enter')
    await flushPromises()

    expect(renameMock).toHaveBeenCalledWith('C:/workspace/readme.md', 'renamed.md')
    expect(wrapper.text()).toContain('renamed.md')
    wrapper.unmount()
    menu.remove()
  })

  it('右键删除：确认后调用 remove 并从树中移除', async () => {
    const wrapper = mountTree()
    await flushPromises()

    await wrapper.findAll('.fen-row')[1].trigger('contextmenu', {clientX: 100, clientY: 120})
    const menu = document.body.querySelector('.fe-context-menu')
    const buttons = menu.querySelectorAll('button')
    const deleteButton = [...buttons].find((b) => b.textContent.includes('删除'))
    await deleteButton.click()
    await flushPromises()

    expect(removeMock).toHaveBeenCalledWith('C:/workspace/readme.md')
    expect(wrapper.findAll('.fen-row').length).toBe(1)
    expect(wrapper.text()).not.toContain('readme.md')
    wrapper.unmount()
    menu.remove()
  })

  it('空名称回车不提交（提示并保留输入框）', async () => {
    const wrapper = mountExplorer()
    await flushPromises()

    await wrapper.find('button[aria-label="新建文件"]').trigger('click')
    await flushPromises()
    const input = wrapper.find('input.fen-edit-input')
    await input.setValue('   ')
    await input.trigger('keydown.enter')
    await flushPromises()

    expect(createMock).not.toHaveBeenCalled()
    expect(wrapper.find('input.fen-edit-input').exists()).toBe(true)
    wrapper.unmount()
  })
})

describe('FileExplorer 搜索', () => {
  it('输入关键字防抖后调用 search 并展示结果', async () => {
    const wrapper = mountExplorer()
    await flushPromises()
    searchMock.mockResolvedValue({
      success: true,
      data: [{name: 'api.js', path: 'C:/workspace/src/api.js', directory: false}]
    })

    await wrapper.find('button[aria-label="搜索文件"]').trigger('click')
    const input = wrapper.find('input[aria-label="搜索文件"]')
    await input.setValue('api')
    await sleep(250)
    await flushPromises()

    expect(searchMock).toHaveBeenCalledWith('C:/workspace', 'api')
    expect(wrapper.text()).toContain('api.js')
    wrapper.unmount()
  })

  it('清空关键字恢复目录树', async () => {
    const wrapper = mountTree()
    await flushPromises()
    searchMock.mockResolvedValue({
      success: true,
      data: [{name: 'api.js', path: 'C:/workspace/src/api.js', directory: false}]
    })

    await wrapper.find('button[aria-label="搜索文件"]').trigger('click')
    const input = wrapper.find('input[aria-label="搜索文件"]')
    await input.setValue('api')
    await sleep(250)
    await flushPromises()
    expect(wrapper.text()).toContain('api.js')

    await input.setValue('')
    await flushPromises()
    expect(wrapper.text()).toContain('readme.md')
    wrapper.unmount()
  })
})
