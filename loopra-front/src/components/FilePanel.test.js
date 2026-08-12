/* @vitest-environment jsdom */

import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import FilePanel from './FilePanel.vue'

const listMock = vi.fn().mockResolvedValue({success: true, data: []})
const searchMock = vi.fn().mockResolvedValue({success: true, data: []})
const removeMock = vi.fn().mockResolvedValue({success: true, data: '已删除'})
const workingFileContentMock = vi.fn().mockResolvedValue({success: true, data: {content: 'hi'}})
const diffContentMock = vi.fn().mockResolvedValue({success: true, data: {diff: ''}})

vi.mock('ant-design-vue', () => ({
  message: {success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn()}
}))

vi.mock('../services/api', () => ({
  filesAPI: {
    list: (...args) => listMock(...args),
    search: (...args) => searchMock(...args),
    remove: (...args) => removeMock(...args)
  },
  gitAPI: {
    workingFileContent: (...args) => workingFileContentMock(...args),
    diffContent: (...args) => diffContentMock(...args)
  }
}))

function mountPanel(props) {
  return mount(FilePanel, {
    props: {workspaceHash: 'h1', ...props},
    global: {stubs: {DiffViewer: true}}
  })
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** 输入关键词并等待防抖(200ms)+请求返回 */
async function typeQuery(wrapper, value) {
  await wrapper.find('input[aria-label="筛选文件"]').setValue(value)
  await sleep(250)
  await flushPromises()
}

/** 在删除确认对话框中点击确认按钮 */
async function confirmDeleteDialog() {
  const dialog = document.body.querySelector('.action-confirm-dialog')
  expect(dialog).not.toBeNull()
  const confirmButton = [...dialog.querySelectorAll('button')].find((b) => b.textContent.includes('删除'))
  await confirmButton.click()
  await flushPromises()
}

/** 挂载一棵含 src 目录 + readme.md 文件的树 */
function mountTree() {
  listMock.mockResolvedValue({
    success: true,
    data: [
      {name: 'src', path: 'src', directory: true},
      {name: 'readme.md', path: 'readme.md', directory: false}
    ]
  })
  return mountPanel()
}

/** 右键第 index 个文件树行 */
async function openMenuOn(wrapper, index) {
  await flushPromises()
  const row = wrapper.findAll('.file-tree-row')[index]
  await row.trigger('contextmenu', {clientX: 100, clientY: 120})
  return row
}

describe('FilePanel 文件筛选', () => {
  beforeEach(() => {
    listMock.mockReset().mockResolvedValue({success: true, data: []})
    searchMock.mockReset().mockResolvedValue({success: true, data: []})
    removeMock.mockReset().mockResolvedValue({success: true, data: '已删除'})
    workingFileContentMock.mockReset().mockResolvedValue({success: true, data: {content: 'hi'}})
    diffContentMock.mockReset().mockResolvedValue({success: true, data: {diff: ''}})
  })

  it('输入关键词走后端递归搜索，展示子目录文件（名字+路径）', async () => {
    searchMock.mockResolvedValue({
      success: true,
      data: [
        {name: 'App.vue', path: 'src/views/App.vue', directory: false},
        {name: 'main.js', path: 'src/main.js', directory: false}
      ]
    })
    const wrapper = mountPanel()
    await flushPromises()
    await typeQuery(wrapper, 'App.vue')

    expect(searchMock).toHaveBeenCalledWith('h1', 'App.vue')
    const results = wrapper.findAll('.file-search-result')
    expect(results).toHaveLength(2)
    expect(results[0].text()).toContain('App.vue')
    expect(results[0].text()).toContain('src/views/App.vue')
    wrapper.unmount()
  })

  it('清空关键词后恢复目录树', async () => {
    searchMock.mockResolvedValue({success: true, data: [{name: 'a.js', path: 'a.js', directory: false}]})
    const wrapper = mountPanel()
    await flushPromises()
    await typeQuery(wrapper, 'a')
    expect(wrapper.findAll('.file-search-result')).toHaveLength(1)

    await typeQuery(wrapper, '')
    expect(wrapper.find('[role="tree"]').exists()).toBe(true)
    expect(wrapper.findAll('.file-search-result')).toHaveLength(0)
    wrapper.unmount()
  })

  it('无匹配时显示空状态', async () => {
    searchMock.mockResolvedValue({success: true, data: []})
    const wrapper = mountPanel()
    await flushPromises()
    await typeQuery(wrapper, 'zzz')
    expect(wrapper.text()).toContain('未找到匹配的文件')
    wrapper.unmount()
  })

  it('点击搜索结果打开 DiffViewer（读取文件内容）', async () => {
    searchMock.mockResolvedValue({success: true, data: [{name: 'a.js', path: 'src/a.js', directory: false}]})
    const wrapper = mountPanel()
    await flushPromises()
    await typeQuery(wrapper, 'a.js')
    await wrapper.find('.file-search-result').trigger('click')
    await flushPromises()

    expect(workingFileContentMock).toHaveBeenCalledWith('h1', 'src/a.js')
    expect(wrapper.findComponent({name: 'DiffViewer'}).props('open')).toBe(true)
    wrapper.unmount()
  })

  it('快速连续输入只展示最新搜索结果（旧请求不覆盖）', async () => {
    let resolveFirst
    searchMock.mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
    searchMock.mockResolvedValue({success: true, data: [{name: 'cd.js', path: 'cd.js', directory: false}]})
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.find('input[aria-label="筛选文件"]').setValue('ab')
    await sleep(250) // 触发第一次搜索（挂起中）
    await wrapper.find('input[aria-label="筛选文件"]').setValue('cd')
    await sleep(250)
    await flushPromises()
    expect(wrapper.text()).toContain('cd.js')

    // 第一次搜索此时才返回，应被忽略
    resolveFirst({success: true, data: [{name: 'old.js', path: 'old.js', directory: false}]})
    await flushPromises()
    expect(wrapper.text()).not.toContain('old.js')
    expect(wrapper.text()).toContain('cd.js')
    wrapper.unmount()
  })
})

describe('FilePanel 右键菜单', () => {
  beforeEach(() => {
    listMock.mockReset().mockResolvedValue({success: true, data: []})
    searchMock.mockReset().mockResolvedValue({success: true, data: []})
    removeMock.mockReset().mockResolvedValue({success: true, data: '已删除'})
    workingFileContentMock.mockReset().mockResolvedValue({success: true, data: {content: 'hi'}})
    diffContentMock.mockReset().mockResolvedValue({success: true, data: {diff: ''}})
  })

  it('右键文件显示菜单（打开/添加到上下文/删除）', async () => {
    const wrapper = mountTree()
    await openMenuOn(wrapper, 1) // readme.md（src 目录在前）
    const menu = wrapper.find('.file-context-menu')
    expect(menu.exists()).toBe(true)
    expect(menu.findAll('button').map((item) => item.text())).toEqual(['打开文件', '添加到上下文', '删除'])
    wrapper.unmount()
  })

  it('点击“添加到上下文”向会话发送文件路径', async () => {
    const wrapper = mountTree()
    await openMenuOn(wrapper, 1)
    await wrapper.find('.file-context-menu button:nth-child(2)').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('addToSession')).toEqual([[{file: 'readme.md'}]])
    expect(wrapper.find('.file-context-menu').exists()).toBe(false)
    wrapper.unmount()
  })

  it('点击“打开文件”打开 DiffViewer', async () => {
    const wrapper = mountTree()
    await openMenuOn(wrapper, 1)
    await wrapper.find('.file-context-menu button:nth-child(1)').trigger('click')
    await flushPromises()
    expect(workingFileContentMock).toHaveBeenCalledWith('h1', 'readme.md')
    expect(wrapper.findComponent({name: 'DiffViewer'}).props('open')).toBe(true)
    wrapper.unmount()
  })

  it('删除文件：确认对话框确认后调用删除接口并从树中移除', async () => {
    const wrapper = mountTree()
    await openMenuOn(wrapper, 1)
    await wrapper.find('.file-context-menu button.danger').trigger('click')
    await flushPromises()
    await confirmDeleteDialog()

    expect(removeMock).toHaveBeenCalledWith('h1', 'readme.md')
    // 树中已无 readme.md，src 目录仍在
    expect(wrapper.findAll('.file-tree-row')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('readme.md')
    expect(wrapper.text()).toContain('src')
    wrapper.unmount()
  })

  it('右键目录显示刷新/删除，删除后整目录从树中移除', async () => {
    const wrapper = mountTree()
    await openMenuOn(wrapper, 0) // src 目录
    expect(wrapper.find('.file-context-menu').text()).toContain('刷新')
    expect(wrapper.find('.file-context-menu').text()).toContain('删除')

    await wrapper.find('.file-context-menu button.danger').trigger('click')
    await flushPromises()
    await confirmDeleteDialog()
    expect(removeMock).toHaveBeenCalledWith('h1', 'src')
    expect(wrapper.findAll('.file-tree-row')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('src')
    expect(wrapper.text()).toContain('readme.md')
    wrapper.unmount()
  })

  it('子文件夹内的文件支持右键菜单与删除', async () => {
    listMock.mockReset()
      .mockResolvedValueOnce({success: true, data: [{name: 'src', path: 'src', directory: true}]})
      .mockResolvedValue({success: true, data: [{name: 'child.js', path: 'src/child.js', directory: false}]})
    const wrapper = mountPanel()
    await flushPromises()

    // 展开 src 目录
    await wrapper.find('.file-tree-row').trigger('click')
    await flushPromises()
    const rows = wrapper.findAll('.file-tree-row')
    expect(rows).toHaveLength(2)

    // 右键子文件 child.js（第二行）
    await rows[1].trigger('contextmenu', {clientX: 100, clientY: 120})
    const menu = wrapper.find('.file-context-menu')
    expect(menu.exists()).toBe(true)
    expect(menu.findAll('button').map((item) => item.text())).toEqual(['打开文件', '添加到上下文', '删除'])

    await menu.find('button.danger').trigger('click')
    await flushPromises()
    await confirmDeleteDialog()
    expect(removeMock).toHaveBeenCalledWith('h1', 'src/child.js')
    expect(wrapper.findAll('.file-tree-row')).toHaveLength(1)
    wrapper.unmount()
  })

  it('搜索模式下删除结果项，结果列表同步移除', async () => {
    searchMock.mockResolvedValue({
      success: true,
      data: [
        {name: 'a.js', path: 'src/deep/a.js', directory: false},
        {name: 'b.js', path: 'src/b.js', directory: false}
      ]
    })
    const wrapper = mountPanel()
    await flushPromises()
    await typeQuery(wrapper, 'js')
    expect(wrapper.findAll('.file-search-result')).toHaveLength(2)

    await wrapper.find('.file-search-result').trigger('contextmenu', {clientX: 100, clientY: 120})
    await wrapper.find('.file-context-menu button.danger').trigger('click')
    await flushPromises()
    await confirmDeleteDialog()

    expect(removeMock).toHaveBeenCalledWith('h1', 'src/deep/a.js')
    expect(wrapper.findAll('.file-search-result')).toHaveLength(1)
    expect(wrapper.text()).toContain('src/b.js')
    expect(wrapper.text()).not.toContain('src/deep/a.js')
    wrapper.unmount()
  })
})
