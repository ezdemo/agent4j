/* @vitest-environment jsdom */

import {flushPromises, mount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import EnvironmentPanel from './EnvironmentPanel.vue'

const environmentMock = vi.fn()
const worktreeCreateMock = vi.fn()
const setWorktreeModeMock = vi.fn()

vi.mock('ant-design-vue', () => ({
  message: {success: vi.fn(), error: vi.fn(), info: vi.fn(), warning: vi.fn()}
}))

vi.mock('../services/api', () => ({
  gitAPI: {
    environment: (...args) => environmentMock(...args),
    worktreeCreate: (...args) => worktreeCreateMock(...args),
    status: vi.fn()
  },
  sessionsAPI: {
    setWorktreeMode: (...args) => setWorktreeModeMock(...args)
  }
}))

const statusMock = vi.fn()
const historyMock = vi.fn()
const commitMock = vi.fn()
const mergeMock = vi.fn()
const pushMock = vi.fn()

function localEnvironment() {
  return {
    success: true,
    data: {
      mode: 'local',
      mainPath: 'C:/repo',
      mainBranch: 'dev',
      mainDirty: true,
      currentPath: 'C:/repo',
      currentBranch: 'dev',
      currentDirty: true,
      worktreeExists: false,
      agentRunning: false,
      message: '当前 Agent 使用本地项目'
    }
  }
}

beforeEach(() => {
  environmentMock.mockReset().mockResolvedValue(localEnvironment())
  worktreeCreateMock.mockReset().mockResolvedValue({success: true})
  setWorktreeModeMock.mockReset().mockResolvedValue({success: true, data: {worktreeMode: true}})
  statusMock.mockReset().mockResolvedValue({
    initialized: true,
    branch: 'dev',
    dirty: true,
    changed: [{path: 'src/App.vue', index: ' ', workTree: 'M', status: ' M'}],
    untracked: [],
    ahead: 0,
    behind: 0
  })
  historyMock.mockReset().mockResolvedValue([
    {hash: 'abc123456789', shortHash: 'abc1234', author: 'Loopra', date: '2026-08-14T10:00:00+08:00', subject: '更新环境面板'}
  ])
  commitMock.mockReset().mockResolvedValue({message: 'committed'})
  mergeMock.mockReset().mockResolvedValue({merged: true, conflictFiles: [], message: '已合并到主项目'})
  pushMock.mockReset().mockResolvedValue({message: 'pushed'})
  window.electronAPI = {
    gitEnvironment: {
      status: (...args) => statusMock(...args),
      history: (...args) => historyMock(...args),
      commit: (...args) => commitMock(...args),
      merge: (...args) => mergeMock(...args),
      push: (...args) => pushMock(...args)
    }
  }
})

afterEach(() => {
  delete window.electronAPI
})

describe('EnvironmentPanel', () => {
  it('显示后端描述的真实本地环境和 Electron Git 变更', async () => {
    const wrapper = mount(EnvironmentPanel, {props: {workspaceHash: 'h1', sessionName: 's1'}})
    await flushPromises()

    expect(environmentMock).toHaveBeenCalledWith('h1', 's1', {silent: true})
    expect(statusMock).toHaveBeenCalledWith('C:/repo')
    expect(wrapper.text()).toContain('环境信息')
    expect(wrapper.text()).toContain('本地')
    expect(wrapper.text()).toContain('dev')
    expect(wrapper.text()).toContain('src/App.vue')
    wrapper.unmount()
  })

  it('从环境面板启用并创建隔离分支', async () => {
    const wrapper = mount(EnvironmentPanel, {props: {workspaceHash: 'h1', sessionName: 's1'}})
    await flushPromises()

    await wrapper.find('.environment-mode-button').trigger('click')
    await flushPromises()

    expect(setWorktreeModeMock).toHaveBeenCalledWith('s1', 'h1', {worktreeMode: true}, {silent: true})
    expect(worktreeCreateMock).toHaveBeenCalledWith('h1', 's1', {silent: true})
    expect(wrapper.emitted('modeChange')).toEqual([[true]])
    wrapper.unmount()
  })

  it('隔离分支干净时仍展示主项目的真实变更', async () => {
    environmentMock.mockResolvedValue({
      success: true,
      data: {
        mode: 'worktree',
        mainPath: 'C:/repo',
        mainBranch: 'dev',
        mainDirty: true,
        currentPath: 'C:/worktree/s1',
        currentBranch: 'loopra/sandbox-s1',
        currentDirty: false,
        worktreeExists: true,
        agentRunning: false,
        message: '隔离分支干净'
      }
    })
    statusMock
      .mockResolvedValueOnce({
        initialized: true,
        branch: 'dev',
        dirty: true,
        changed: [{path: 'README.md', index: ' ', workTree: 'M', status: ' M'}],
        untracked: []
      })
      .mockResolvedValueOnce({
        initialized: true,
        branch: 'loopra/sandbox-s1',
        dirty: false,
        changed: [],
        untracked: []
      })

    const wrapper = mount(EnvironmentPanel, {props: {workspaceHash: 'h1', sessionName: 's1'}})
    await flushPromises()

    expect(wrapper.text()).toContain('隔离分支变更')
    expect(wrapper.text()).toContain('本地变更')
    expect(wrapper.text()).toContain('README.md')
    expect(wrapper.text()).toContain('主项目有未提交变更')
    wrapper.unmount()
  })

  it('可分别查看本地分支和隔离分支分支的提交记录', async () => {
    environmentMock.mockResolvedValue({
      success: true,
      data: {
        mode: 'worktree',
        mainPath: 'C:/repo',
        mainBranch: 'dev',
        mainDirty: false,
        currentPath: 'C:/worktree/s1',
        currentBranch: 'loopra/sandbox-s1',
        currentDirty: false,
        worktreeExists: true,
        agentRunning: false,
        message: '隔离分支干净'
      }
    })
    statusMock.mockResolvedValue({initialized: true, dirty: false, changed: [], untracked: []})

    const wrapper = mount(EnvironmentPanel, {props: {workspaceHash: 'h1', sessionName: 's1'}})
    await flushPromises()

    const buttons = wrapper.findAll('.environment-history-button')
    expect(buttons).toHaveLength(2)

    await buttons[0].trigger('click')
    await flushPromises()
    expect(historyMock).toHaveBeenLastCalledWith({cwd: 'C:/repo', branch: 'dev', limit: 30})
    expect(document.body.textContent).toContain('本地 · dev')
    expect(document.body.textContent).toContain('更新环境面板')

    await buttons[1].trigger('click')
    await flushPromises()
    expect(historyMock).toHaveBeenLastCalledWith({cwd: 'C:/worktree/s1', branch: 'loopra/sandbox-s1', limit: 30})
    expect(document.body.textContent).toContain('隔离分支 · loopra/sandbox-s1')
    wrapper.unmount()
  })

  it('Desktop Git 模块缺失时不把读取失败显示成项目干净', async () => {
    delete window.electronAPI

    const wrapper = mount(EnvironmentPanel, {props: {workspaceHash: 'h1', sessionName: 's1'}})
    await flushPromises()

    expect(wrapper.text()).toContain('Git 功能未加载')
    expect(wrapper.text()).not.toContain('暂无未提交变更')
    expect(wrapper.text()).not.toContain('主项目干净')
    wrapper.unmount()
  })
})
