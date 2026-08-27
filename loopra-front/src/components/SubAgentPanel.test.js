/* @vitest-environment jsdom */

import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import SubAgentPanel from './SubAgentPanel.vue'

const {subSessionsAPI} = vi.hoisted(() => ({
  subSessionsAPI: {
    list: vi.fn(),
    events: vi.fn(),
    remove: vi.fn()
  }
}))

vi.mock('../services/api', () => ({subSessionsAPI}))

function mountPanel(props = {}) {
  return mount(SubAgentPanel, {
    props: {
      workspaceHash: 'h1',
      sessionName: 's1',
      ...props
    }
  })
}

/** 在删除确认对话框中点击「删除」确认按钮 */
async function confirmDeleteDialog() {
  const dialog = document.body.querySelector('.action-confirm-dialog')
  expect(dialog).not.toBeNull()
  const confirmButton = [...dialog.querySelectorAll('button')].find((b) => b.textContent.includes('删除'))
  await confirmButton.click()
  await flushPromises()
}

const item = (overrides = {}) => ({
  subSessionId: 'sub-1',
  task: '探索项目结构',
  name: '初音未来',
  title: '探索项目结构。',
  profile: 'explore',
  status: 'completed',
  startedAt: 1000,
  endedAt: 2000,
  eventCount: 5,
  mtime: 2000,
  ...overrides
})

describe('SubAgentPanel（左侧子代理会话列表）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    subSessionsAPI.list.mockResolvedValue({success: true, data: []})
  })

  it('loads list on mount with workspace and session', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: [item()]})
    const wrapper = mountPanel()
    await flushPromises()
    expect(subSessionsAPI.list).toHaveBeenCalledWith('h1', 's1')
    expect(wrapper.findAll('.sub-agent-item')).toHaveLength(1)
    expect(wrapper.text()).toContain('初音未来')
  })

  it('shows empty state when no records', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无子代理执行记录')
  })

  it('single click selects and opens the replay tab', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: [item()]})
    const wrapper = mountPanel()
    await flushPromises()
    const row = wrapper.find('.sub-agent-item')

    await row.trigger('click')
    expect(wrapper.vm.selectedSubId).toBe('sub-1')
    expect(wrapper.emitted('open')).toHaveLength(1)
    expect(wrapper.emitted('open')[0][0]).toEqual(item())
  })

  it('double click also opens (two clicks, idempotent on top)', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: [item()]})
    const wrapper = mountPanel()
    await flushPromises()
    const row = wrapper.find('.sub-agent-item')

    // 真实双击 = 两次 click：每次都会 open，上层 openSubAgentTab 幂等（重复激活同一标签）
    await row.trigger('click')
    await row.trigger('click')
    expect(wrapper.emitted('open').length).toBe(2)
  })

  it('refresh() silently reloads list', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: []})
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.vm.refresh()
    expect(subSessionsAPI.list).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.loadingList).toBe(false)
  })

  it('does not render any tab group or content area (pure list)', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: [item()]})
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('.sub-agent-tabs').exists()).toBe(false)
    expect(wrapper.find('.sub-agent-viewer').exists()).toBe(false)
  })

  it('shows name and time in a single row', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: [item()]})
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('.sub-agent-item-name').text()).toBe('初音未来')
    expect(wrapper.find('.sub-agent-item-time').text()).toContain('1970-01-01')
    // 标题/角色/事件数不再占行（悬停提示中）
    expect(wrapper.find('.sub-agent-item-title').exists()).toBe(false)
    expect(wrapper.find('.sub-agent-item-meta').exists()).toBe(false)
    expect(wrapper.find('.sub-agent-item-row').exists()).toBe(true)
  })

  it('falls back to task when name missing (legacy records)', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: [item({name: null})]})
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('.sub-agent-item-name').text()).toBe('探索项目结构')
    // 悬停提示含标题
    expect(wrapper.find('.sub-agent-item').attributes('title')).toContain('探索项目结构')
  })

  it('deletes session via unified confirm dialog, emits removed and reloads list', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: [item()]})
    subSessionsAPI.remove.mockResolvedValue({success: true, data: {subSessionId: 'sub-1', deleted: true}})
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.find('.sub-agent-delete').trigger('click')
    await flushPromises()
    const dialog = document.body.querySelector('.action-confirm-dialog')
    expect(dialog).not.toBeNull()
    expect(dialog.textContent).toContain('初音未来')

    await confirmDeleteDialog()
    expect(subSessionsAPI.remove).toHaveBeenCalledWith('sub-1', 'h1', 's1')
    expect(wrapper.emitted('removed')).toHaveLength(1)
    expect(wrapper.emitted('removed')[0][0]).toBe('sub-1')
    expect(subSessionsAPI.list).toHaveBeenCalledTimes(2) // 删除后刷新
    expect(document.body.querySelector('.action-confirm-dialog')).toBeNull()
  })

  it('does not delete when cancelling the confirm dialog', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: [item()]})
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.find('.sub-agent-delete').trigger('click')
    await flushPromises()
    const dialog = document.body.querySelector('.action-confirm-dialog')
    const cancelButton = [...dialog.querySelectorAll('button')].find((b) => b.textContent.includes('取消'))
    await cancelButton.click()
    await flushPromises()

    expect(subSessionsAPI.remove).not.toHaveBeenCalled()
    expect(wrapper.emitted('removed')).toBeUndefined()
    expect(document.body.querySelector('.action-confirm-dialog')).toBeNull()
  })

  it('delete click does not trigger row select/open', async () => {
    subSessionsAPI.list.mockResolvedValue({success: true, data: [item()]})
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.find('.sub-agent-delete').trigger('click')
    await flushPromises()
    expect(wrapper.vm.selectedSubId).toBeNull()
    expect(wrapper.emitted('open')).toBeUndefined()
    // 关闭对话框，避免污染后续用例
    document.body.querySelector('.action-confirm-dialog')?.remove()
  })
})
