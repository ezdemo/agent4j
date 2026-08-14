/* @vitest-environment jsdom */

import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import BashSessionManager from './BashSessionManager.vue'
import {agentAPI} from '@/services/api'

vi.mock('@/services/api', () => ({
  agentAPI: {
    getBashSessions: vi.fn(),
    terminateBashSession: vi.fn(),
    getBashSessionLog: vi.fn()
  }
}))

enableAutoUnmount(afterEach)

describe('BashSessionManager', () => {
  beforeEach(() => {
    agentAPI.getBashSessions.mockReset()
    agentAPI.getBashSessions.mockResolvedValue({data: []})
    agentAPI.terminateBashSession.mockReset()
    agentAPI.terminateBashSession.mockResolvedValue({success: true})
    agentAPI.getBashSessionLog.mockReset()
    agentAPI.getBashSessionLog.mockResolvedValue({
      success: true,
      data: {sessionId: 'cmd_1', output: 'hello'}
    })
    Object.defineProperty(navigator, 'clipboard', {
      value: {writeText: vi.fn().mockResolvedValue(undefined)},
      configurable: true
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('点击按钮后拉取并渲染运行中与已结束会话', async () => {
    agentAPI.getBashSessions.mockResolvedValue({
      data: [
        {
          sessionId: 'cmd_1',
          workspace: 'C:/work/proj-a',
          command: 'npm run dev',
          workdir: 'C:/work/proj-a',
          startedAt: Date.now() - 10_000,
          status: 'running'
        },
        {
          sessionId: 'cmd_2',
          workspace: 'C:/work/proj-b',
          command: 'python server.py',
          workdir: 'C:/work/proj-b',
          startedAt: Date.now() - 120_000,
          status: 'completed'
        }
      ]
    })
    const wrapper = mount(BashSessionManager)
    expect(wrapper.find('.bash-popover').isVisible()).toBe(false)

    await wrapper.find('.tb-bash-btn').trigger('click')
    await flushPromises()

    expect(agentAPI.getBashSessions).toHaveBeenCalledTimes(1)
    const rows = wrapper.findAll('.bash-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('npm run dev')
    expect(rows[0].text()).toContain('运行中')
    expect(rows[1].text()).toContain('python server.py')
    expect(rows[1].text()).toContain('已结束')
    expect(wrapper.find('.bash-live-dot').exists()).toBe(true)
  })

  it('空列表显示空态且无运行中指示点', async () => {
    const wrapper = mount(BashSessionManager)
    await wrapper.find('.tb-bash-btn').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('暂无后台进程')
    expect(wrapper.find('.bash-live-dot').exists()).toBe(false)
  })

  it('打开后每 3s 轮询刷新', async () => {
    vi.useFakeTimers()
    const wrapper = mount(BashSessionManager)
    await wrapper.find('.tb-bash-btn').trigger('click')
    await flushPromises()

    const callsAfterOpen = agentAPI.getBashSessions.mock.calls.length
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    expect(agentAPI.getBashSessions.mock.calls.length).toBeGreaterThan(callsAfterOpen)
  })

  it('请求失败显示错误信息', async () => {
    agentAPI.getBashSessions.mockRejectedValue({message: '服务器错误'})
    const wrapper = mount(BashSessionManager)
    await wrapper.find('.tb-bash-btn').trigger('click')
    await flushPromises()

    expect(wrapper.find('.bash-error').text()).toContain('服务器错误')
  })

  it('关闭 popover 后停止轮询', async () => {
    vi.useFakeTimers()
    const wrapper = mount(BashSessionManager)
    await wrapper.find('.tb-bash-btn').trigger('click')
    await flushPromises()

    await wrapper.find('.tb-bash-btn').trigger('click')
    expect(wrapper.find('.bash-popover').isVisible()).toBe(false)

    const callsAfterClose = agentAPI.getBashSessions.mock.calls.length
    await vi.advanceTimersByTimeAsync(9000)
    expect(agentAPI.getBashSessions.mock.calls.length).toBe(callsAfterClose)
  })

  it('embedded 模式挂载即拉取并渲染会话，无按钮外壳', async () => {
    agentAPI.getBashSessions.mockResolvedValue({
      data: [
        {
          sessionId: 'cmd_1',
          workspace: 'C:/work/proj-a',
          command: 'npm run dev',
          workdir: 'C:/work/proj-a',
          startedAt: Date.now() - 10_000,
          status: 'running'
        }
      ]
    })
    const wrapper = mount(BashSessionManager, {props: {embedded: true}})
    await flushPromises()

    expect(agentAPI.getBashSessions).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.tb-bash-btn').exists()).toBe(false)
    expect(wrapper.find('.bash-popover').isVisible()).toBe(true)
    expect(wrapper.find('.bash-row').text()).toContain('npm run dev')
  })

  it('embedded 模式自动轮询并在卸载后停止', async () => {
    vi.useFakeTimers()
    const wrapper = mount(BashSessionManager, {props: {embedded: true}})
    await flushPromises()

    const callsAfterMount = agentAPI.getBashSessions.mock.calls.length
    await vi.advanceTimersByTimeAsync(6000)
    expect(agentAPI.getBashSessions.mock.calls.length).toBeGreaterThan(callsAfterMount)

    wrapper.unmount()
    const callsAfterUnmount = agentAPI.getBashSessions.mock.calls.length
    await vi.advanceTimersByTimeAsync(9000)
    expect(agentAPI.getBashSessions.mock.calls.length).toBe(callsAfterUnmount)
  })

  it('运行中会话可手动关闭并弹通知', async () => {
    agentAPI.getBashSessions.mockResolvedValue({
      data: [
        {
          sessionId: 'cmd_1',
          workspace: 'C:/work/proj-a',
          command: 'npm run dev',
          workdir: 'C:/work/proj-a',
          startedAt: Date.now() - 10_000,
          status: 'running'
        },
        {
          sessionId: 'cmd_2',
          workspace: 'C:/work/proj-b',
          command: 'python server.py',
          workdir: 'C:/work/proj-b',
          startedAt: Date.now() - 120_000,
          status: 'completed'
        }
      ]
    })
    agentAPI.terminateBashSession.mockResolvedValue({
      success: true,
      data: '✅ 已关闭后台进程\nsession_id: cmd_1\nstatus: completed\nterminated: true'
    })
    const wrapper = mount(BashSessionManager, {props: {embedded: true}})
    await flushPromises()

    // 运行中会话有日志+关闭按钮（2 个），已结束会话只有日志按钮（1 个）
    expect(wrapper.findAll('.bash-actions .icon-btn')).toHaveLength(3)
    expect(wrapper.findAll('.bash-actions .icon-btn.danger')).toHaveLength(1)

    const notifyListener = vi.fn()
    window.addEventListener('app-notify', notifyListener)

    await wrapper.find('.bash-actions .icon-btn.danger').trigger('click')
    await flushPromises()

    expect(agentAPI.terminateBashSession).toHaveBeenCalledWith('cmd_1')
    expect(notifyListener).toHaveBeenCalledTimes(1)
    expect(notifyListener.mock.calls[0][0].detail).toContain('已关闭后台进程')
    expect(notifyListener.mock.calls[0][0].detail).toContain('cmd_1')
    expect(agentAPI.getBashSessions).toHaveBeenCalled()

    window.removeEventListener('app-notify', notifyListener)
  })

  it('关闭失败显示错误且不弹成功通知', async () => {
    agentAPI.getBashSessions.mockResolvedValue({
      data: [
        {
          sessionId: 'cmd_1',
          workspace: 'C:/work/proj-a',
          command: 'npm run dev',
          workdir: 'C:/work/proj-a',
          startedAt: Date.now() - 10_000,
          status: 'running'
        }
      ]
    })
    agentAPI.terminateBashSession.mockResolvedValue({success: false, message: '会话不存在'})
    const wrapper = mount(BashSessionManager, {props: {embedded: true}})
    await flushPromises()

    const notifyListener = vi.fn()
    window.addEventListener('app-notify', notifyListener)

    await wrapper.find('.bash-actions .icon-btn.danger').trigger('click')
    await flushPromises()

    expect(notifyListener).not.toHaveBeenCalled()
    expect(wrapper.find('.bash-error').text()).toContain('会话不存在')

    window.removeEventListener('app-notify', notifyListener)
  })

  it('单击命令与路径复制到剪贴板并短暂高亮', async () => {
    vi.useFakeTimers()
    agentAPI.getBashSessions.mockResolvedValue({
      data: [
        {
          sessionId: 'cmd_1',
          workspace: 'C:/work/proj-a',
          command: 'npm run dev',
          workdir: 'C:/work/proj-a/sub',
          startedAt: Date.now() - 10_000,
          status: 'running'
        }
      ]
    })
    const wrapper = mount(BashSessionManager, {props: {embedded: true}})
    await flushPromises()

    const writeText = navigator.clipboard.writeText
    const copySuccessListener = vi.fn()
    window.addEventListener('copy-success', copySuccessListener)

    // 命令
    await wrapper.find('.bash-title strong').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('npm run dev')
    expect(wrapper.find('.bash-title strong').classes()).toContain('copied')
    expect(copySuccessListener).toHaveBeenCalledTimes(1)
    expect(copySuccessListener.mock.calls[0][0].detail).toBe('复制成功')

    // 项目（复制完整路径而非短名）
    await wrapper.find('.bash-workspace').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('C:/work/proj-a')

    // 工作目录
    await wrapper.find('.bash-workdir').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith('C:/work/proj-a/sub')
    expect(wrapper.find('.bash-workdir').classes()).toContain('copied')
    expect(copySuccessListener).toHaveBeenCalledTimes(3)

    // 1.5s 后高亮恢复
    await vi.advanceTimersByTimeAsync(1600)
    await flushPromises()
    expect(wrapper.find('.bash-workdir').classes()).not.toContain('copied')

    window.removeEventListener('copy-success', copySuccessListener)
  })

  it('点击日志按钮打开弹窗并展示累积输出', async () => {
    agentAPI.getBashSessions.mockResolvedValue({
      data: [
        {
          sessionId: 'cmd_1',
          workspace: 'C:/work/proj-a',
          command: 'npm run dev',
          workdir: 'C:/work/proj-a',
          startedAt: Date.now() - 10_000,
          status: 'running'
        }
      ]
    })
    agentAPI.getBashSessionLog.mockResolvedValue({
      success: true,
      data: {
        sessionId: 'cmd_1',
        output: 'Compiled successfully\nwatch mode enabled'
      }
    })
    const wrapper = mount(BashSessionManager, {props: {embedded: true}})
    await flushPromises()

    await wrapper.find('.bash-actions .icon-btn').trigger('click')
    await flushPromises()

    expect(agentAPI.getBashSessionLog).toHaveBeenCalledWith('cmd_1')
    expect(wrapper.find('.bash-log-dialog').exists()).toBe(true)
    expect(wrapper.find('.bash-log-dialog').text()).toContain('npm run dev')
    expect(wrapper.find('.bash-log-dialog').text()).toContain('运行中')
    expect(wrapper.find('.bash-log-body').text()).toContain('Compiled successfully')
  })

  it('日志弹窗刷新按钮重新拉取日志', async () => {
    agentAPI.getBashSessions.mockResolvedValue({
      data: [
        {
          sessionId: 'cmd_1',
          workspace: 'C:/work/proj-a',
          command: 'npm run dev',
          workdir: 'C:/work/proj-a',
          startedAt: Date.now() - 10_000,
          status: 'running'
        }
      ]
    })
    const wrapper = mount(BashSessionManager, {props: {embedded: true}})
    await flushPromises()

    await wrapper.find('.bash-actions .icon-btn').trigger('click')
    await flushPromises()
    const callsAfterOpen = agentAPI.getBashSessionLog.mock.calls.length

    await wrapper.find('.bash-log-dialog .icon-btn').trigger('click')
    await flushPromises()
    expect(agentAPI.getBashSessionLog.mock.calls.length).toBeGreaterThan(callsAfterOpen)
  })

  it('日志读取失败显示错误', async () => {
    agentAPI.getBashSessions.mockResolvedValue({
      data: [
        {
          sessionId: 'cmd_1',
          workspace: 'C:/work/proj-a',
          command: 'npm run dev',
          workdir: 'C:/work/proj-a',
          startedAt: Date.now() - 10_000,
          status: 'running'
        }
      ]
    })
    agentAPI.getBashSessionLog.mockResolvedValue({success: false, message: '会话不存在或已结束'})
    const wrapper = mount(BashSessionManager, {props: {embedded: true}})
    await flushPromises()

    await wrapper.find('.bash-actions .icon-btn').trigger('click')
    await flushPromises()

    expect(wrapper.find('.bash-log-dialog').text()).toContain('会话不存在或已结束')
  })

  it('关闭日志弹窗后恢复列表', async () => {
    agentAPI.getBashSessions.mockResolvedValue({
      data: [
        {
          sessionId: 'cmd_1',
          workspace: 'C:/work/proj-a',
          command: 'npm run dev',
          workdir: 'C:/work/proj-a',
          startedAt: Date.now() - 10_000,
          status: 'running'
        }
      ]
    })
    const wrapper = mount(BashSessionManager, {props: {embedded: true}})
    await flushPromises()

    await wrapper.find('.bash-actions .icon-btn').trigger('click')
    await flushPromises()
    expect(wrapper.find('.bash-log-dialog').exists()).toBe(true)

    await wrapper.find('.bash-log-dialog .bash-log-actions .icon-btn:last-child').trigger('click')
    await flushPromises()
    expect(wrapper.find('.bash-log-dialog').exists()).toBe(false)
  })
})
