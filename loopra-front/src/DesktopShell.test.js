/* @vitest-environment jsdom */

import {flushPromises, shallowMount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {createPinia} from 'pinia'
import DesktopShell from './DesktopShell.vue'
import {nextTick} from 'vue'
import {useAppStore} from './stores/app'

const initialElectronAPI = window.electronAPI

const {configAPI, sessionsAPI, systemAPI, switchThemeWithReveal} = vi.hoisted(() => ({
  configAPI: {
    getConfig: vi.fn(),
    listWorkspaces: vi.fn(),
    getWorkspace: vi.fn(),
    switchWorkspace: vi.fn(),
    saveWorkspaceOrder: vi.fn(),
    deleteWorkspace: vi.fn()
  },
  sessionsAPI: {
    createNew: vi.fn(),
    list: vi.fn(),
    deleteSession: vi.fn(),
    clearAll: vi.fn(),
    clearBefore: vi.fn()
  },
  systemAPI: {
    checkLatestVersion: vi.fn()
  },
  switchThemeWithReveal: vi.fn((target, apply) => apply(target))
}))

vi.mock('./services/api', () => ({configAPI, sessionsAPI, systemAPI}))
vi.mock('./utils/themeTransition', () => ({switchThemeWithReveal}))

defineGlobalResizeObserver()

function defineGlobalResizeObserver() {
  if (typeof globalThis.ResizeObserver === 'undefined') {
    globalThis.ResizeObserver = class {
      observe() {}
      disconnect() {}
    }
  }
}

async function mountShell() {
  const pinia = createPinia()
  const store = useAppStore(pinia)
  store.settings.theme = 'gray'
  const wrapper = shallowMount(DesktopShell, {
    global: {
      plugins: [pinia],
      stubs: {
        Teleport: false,
        DesktopHome: true,
        SettingsView: true,
        ToolsView: true,
        SubAgentsView: true,
        ModelChannels: true,
        DashboardPanel: true,
        ConfirmDialog: true
      }
    }
  })
  await flushPromises()
  return {wrapper, store}
}

async function openTab(wrapper, sessionName) {
  await wrapper.vm.openSession({workspaceHash: 'h1', sessionName, title: sessionName})
  await flushPromises()
}

beforeEach(() => {
  configAPI.getConfig.mockResolvedValue({success: true, data: {modelChannelsConfigured: true}})
  configAPI.listWorkspaces.mockResolvedValue({success: true, data: [{hash: 'h1', name: 'A', path: '/p/a'}]})
  configAPI.getWorkspace.mockResolvedValue({success: true, data: '/p/a'})
  configAPI.switchWorkspace.mockResolvedValue({success: true, data: {workspace: ''}})
  sessionsAPI.list.mockResolvedValue({success: true, data: []})
  systemAPI.checkLatestVersion.mockResolvedValue({success: true, data: {hasNewVersion: false}})
  vi.spyOn(window, 'open').mockImplementation(() => null)
})

afterEach(() => {
  vi.restoreAllMocks()
  document.body.querySelector('.desktop-shell-context-menu')?.remove()
  document.body.querySelector('.desktop-tab-context-menu')?.remove()
  if (initialElectronAPI === undefined) delete window.electronAPI
  else window.electronAPI = initialElectronAPI
})

describe('DesktopShell 更新按钮', () => {
  it('仅在检测到新版本时显示', async () => {
    systemAPI.checkLatestVersion.mockResolvedValueOnce({success: true, data: {hasNewVersion: false, latestVersion: '26.8.121'}})
    const {wrapper} = await mountShell()
    expect(wrapper.find('.update-check-button').exists()).toBe(false)
    wrapper.unmount()

    systemAPI.checkLatestVersion.mockResolvedValueOnce({success: true, data: {hasNewVersion: true, latestVersion: '26.8.122'}})
    const updated = await mountShell()
    expect(updated.wrapper.find('.update-check-button').exists()).toBe(true)
    updated.wrapper.unmount()
  })
})

describe('DesktopShell 首页右键菜单', () => {
  it('提供打开需求池、更新和切换主题操作', async () => {
    const {wrapper, store} = await mountShell()
    const homeButton = wrapper.find('.icon-button')

    await homeButton.trigger('contextmenu', {clientX: 40, clientY: 30})

    const menu = document.body.querySelector('.desktop-shell-context-menu')
    expect(menu).not.toBeNull()
    expect(menu.textContent).toContain('打开需求池')
    expect(menu.textContent).toContain('更新')
    expect(menu.textContent).toContain('切换主题')
    expect(homeButton.attributes('aria-expanded')).toBe('true')

    const menuItems = menu.querySelectorAll('[role="menuitem"]')
    menuItems[0].click()
    await nextTick()
    expect(window.open).toHaveBeenCalledWith(expect.stringContaining('requirementBoard=1'), '_blank')
    expect(document.body.querySelector('.desktop-shell-context-menu')).toBeNull()

    await homeButton.trigger('contextmenu', {clientX: 40, clientY: 30})
    // 菜单顺序：打开需求池 / 打开引导 / 更新 / 切换主题（引导项为新增）
    document.body.querySelectorAll('[role="menuitem"]')[2].click()
    await nextTick()
    expect(window.open).toHaveBeenLastCalledWith(expect.stringContaining('/releases'), '_blank')

    await homeButton.trigger('contextmenu', {clientX: 40, clientY: 30})
    document.body.querySelectorAll('[role="menuitem"]')[3].click()
    await nextTick()
    expect(switchThemeWithReveal).toHaveBeenCalledWith('dark', expect.any(Function))
    expect(store.settings.theme).toBe('dark')
    expect(document.body.querySelector('.desktop-shell-context-menu')).toBeNull()

    wrapper.unmount()
  })

  it('Electron 环境通过原生菜单返回动作，不渲染 DOM 菜单', async () => {
    const openNativeMenu = vi.fn().mockResolvedValue('toggle-theme')
    window.electronAPI = {desktopHomeMenu: {open: openNativeMenu}}

    const {wrapper, store} = await mountShell()
    await wrapper.find('.icon-button').trigger('contextmenu', {clientX: 40, clientY: 30})
    await nextTick()

    expect(openNativeMenu).toHaveBeenCalledWith('gray')
    expect(document.body.querySelector('.desktop-shell-context-menu')).toBeNull()
    expect(switchThemeWithReveal).toHaveBeenCalledWith('dark', expect.any(Function))
    expect(store.settings.theme).toBe('dark')

    wrapper.unmount()
  })

  it('将当前深色主题传给原生菜单', async () => {
    const openNativeMenu = vi.fn().mockResolvedValue(null)
    window.electronAPI = {desktopHomeMenu: {open: openNativeMenu}}

    const {wrapper, store} = await mountShell()
    store.settings.theme = 'dark'
    await wrapper.find('.icon-button').trigger('contextmenu', {clientX: 40, clientY: 30})

    expect(openNativeMenu).toHaveBeenCalledWith('dark')
    expect(document.body.querySelector('.desktop-shell-context-menu')).toBeNull()

    wrapper.unmount()
  })

  it('点击外部或按 Escape 会关闭菜单', async () => {
    const {wrapper} = await mountShell()
    const homeButton = wrapper.find('.icon-button')

    await homeButton.trigger('contextmenu', {clientX: 40, clientY: 30})
    expect(document.body.querySelector('.desktop-shell-context-menu')).not.toBeNull()

    window.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))
    await flushPromises()
    expect(document.body.querySelector('.desktop-shell-context-menu')).toBeNull()

    await homeButton.trigger('contextmenu', {clientX: 40, clientY: 30})
    window.dispatchEvent(new MouseEvent('click'))
    await flushPromises()
    expect(document.body.querySelector('.desktop-shell-context-menu')).toBeNull()

    wrapper.unmount()
  })
})

describe('DesktopShell 会话标签右键菜单', () => {
  function chatTabsBridge() {
    return {
      create: vi.fn().mockResolvedValue({success: true}),
      show: vi.fn().mockResolvedValue({success: true}),
      hide: vi.fn().mockResolvedValue({success: true}),
      close: vi.fn().mockResolvedValue({success: true}),
      reload: vi.fn().mockResolvedValue({success: true}),
      sendCommand: vi.fn().mockResolvedValue(true)
    }
  }

  it('标签溢出时中键按下关闭标签并阻止自动滚动', async () => {
    const desktopChatTabs = chatTabsBridge()
    window.electronAPI = {desktopChatTabs}
    const {wrapper} = await mountShell()
    await openTab(wrapper, 'a')
    await openTab(wrapper, 'b')

    const tab = wrapper.findAll('.desktop-tab')[1]
    const middleMouseDown = new MouseEvent('mousedown', {button: 1, bubbles: true, cancelable: true})
    expect(tab.element.dispatchEvent(middleMouseDown)).toBe(false)
    await flushPromises()

    expect(desktopChatTabs.close).toHaveBeenCalledWith('h1:b')
    expect(wrapper.findAll('.desktop-tab').map((item) => item.attributes('title'))).toEqual(['a'])

    wrapper.unmount()
  })

  it('提供刷新、关闭和关闭左右标签操作', async () => {
    const desktopChatTabs = chatTabsBridge()
    window.electronAPI = {desktopChatTabs}
    const {wrapper} = await mountShell()
    await openTab(wrapper, 'a')
    await openTab(wrapper, 'b')
    await openTab(wrapper, 'c')

    let tab = wrapper.findAll('.desktop-tab')[1]
    await tab.trigger('contextmenu', {clientX: 40, clientY: 30})
    let menu = document.body.querySelector('.desktop-tab-context-menu')
    expect(menu).not.toBeNull()
    expect(menu.textContent).toContain('刷新')
    expect(menu.textContent).toContain('关闭')
    expect(menu.textContent).toContain('关闭左侧标签')
    expect(menu.textContent).toContain('关闭右侧标签')

    let menuItems = menu.querySelectorAll('[role="menuitem"]')
    menuItems[0].click()
    await flushPromises()
    expect(desktopChatTabs.reload).toHaveBeenCalledWith('h1:b')

    tab = wrapper.findAll('.desktop-tab')[1]
    await tab.trigger('contextmenu', {clientX: 40, clientY: 30})
    menu = document.body.querySelector('.desktop-tab-context-menu')
    menuItems = menu.querySelectorAll('[role="menuitem"]')
    expect(menuItems[2].disabled).toBe(false)
    expect(menuItems[3].disabled).toBe(false)
    menuItems[2].click()
    await flushPromises()
    expect(desktopChatTabs.close).toHaveBeenCalledWith('h1:a')
    expect(wrapper.findAll('.desktop-tab').map((item) => item.attributes('title'))).toEqual(['b', 'c'])

    tab = wrapper.findAll('.desktop-tab')[0]
    await tab.trigger('contextmenu', {clientX: 40, clientY: 30})
    menu = document.body.querySelector('.desktop-tab-context-menu')
    menuItems = menu.querySelectorAll('[role="menuitem"]')
    expect(menuItems[2].disabled).toBe(true)
    menuItems[3].click()
    await flushPromises()
    expect(desktopChatTabs.close).toHaveBeenCalledWith('h1:c')
    expect(wrapper.findAll('.desktop-tab').map((item) => item.attributes('title'))).toEqual(['b'])

    await wrapper.find('.desktop-tab').trigger('contextmenu', {clientX: 40, clientY: 30})
    menu = document.body.querySelector('.desktop-tab-context-menu')
    menu.querySelectorAll('[role="menuitem"]')[1].click()
    await flushPromises()
    expect(desktopChatTabs.close).toHaveBeenCalledWith('h1:b')
    expect(wrapper.findAll('.desktop-tab')).toHaveLength(0)

    wrapper.unmount()
  })

  it('更新请求创建快速路径标签并投递命令', async () => {
    const desktopChatTabs = chatTabsBridge()
    const listeners = {}
    sessionsAPI.createNew.mockResolvedValue({success: true, data: {sessionName: 'update-session', workspaceHash: 'h1'}})
    window.electronAPI = {
      desktopChatTabs,
      events: {
        listen: vi.fn((channel, callback) => {
          listeners[channel] = callback
          return vi.fn()
        })
      }
    }
    const {wrapper} = await mountShell()

    listeners['chat-update-request']({source: 'mirror'})
    await vi.waitFor(() => expect(desktopChatTabs.sendCommand).toHaveBeenCalledTimes(1))

    expect(desktopChatTabs.create).toHaveBeenCalledWith(expect.objectContaining({
      id: 'h1:update-session',
      sessionName: 'update-session',
      newSession: true
    }))
    expect(desktopChatTabs.sendCommand).toHaveBeenCalledWith(
      'h1:update-session',
      expect.stringContaining('setup-gui-mirror')
    )
    wrapper.unmount()
  })

  it('加载失败时移除已创建的死标签', async () => {
    const desktopChatTabs = chatTabsBridge()
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    desktopChatTabs.create.mockRejectedValue(new Error('ERR_FAILED'))
    window.electronAPI = {desktopChatTabs}
    const {wrapper} = await mountShell()

    await openTab(wrapper, 'failed')

    expect(wrapper.findAll('.desktop-tab')).toHaveLength(0)
    expect(wrapper.find('desktop-home-stub').exists()).toBe(true)
    expect(desktopChatTabs.show).not.toHaveBeenCalled()
    expect(desktopChatTabs.close).toHaveBeenCalledWith('h1:failed')
    expect(desktopChatTabs.hide).toHaveBeenCalled()
    expect(consoleError).toHaveBeenCalledWith('[desktop-shell] failed to show tab:', expect.any(Error))
    wrapper.unmount()
  })

  it('显示失败时关闭已创建的原生标签', async () => {
    const desktopChatTabs = chatTabsBridge()
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    desktopChatTabs.show.mockRejectedValue(new Error('ERR_FAILED'))
    window.electronAPI = {desktopChatTabs}
    const {wrapper} = await mountShell()

    await openTab(wrapper, 'show-failed')

    expect(desktopChatTabs.create).toHaveBeenCalledWith(expect.objectContaining({id: 'h1:show-failed'}))
    expect(desktopChatTabs.close).toHaveBeenCalledWith('h1:show-failed')
    expect(desktopChatTabs.hide).toHaveBeenCalled()
    expect(wrapper.findAll('.desktop-tab')).toHaveLength(0)
    expect(consoleError).toHaveBeenCalledWith('[desktop-shell] failed to show tab:', expect.any(Error))
    wrapper.unmount()
  })

  it('快速切换会话时只显示最后一次渲染请求', async () => {
    const desktopChatTabs = chatTabsBridge()
    let resolveFirstCreate
    const firstCreate = new Promise((resolve) => { resolveFirstCreate = resolve })
    desktopChatTabs.create
      .mockImplementationOnce(() => firstCreate)
      .mockResolvedValue({success: true})
    window.electronAPI = {desktopChatTabs}
    const {wrapper} = await mountShell()

    const firstOpen = wrapper.vm.openSession({workspaceHash: 'h1', sessionName: 'a', title: 'a'})
    await vi.waitFor(() => expect(desktopChatTabs.create).toHaveBeenCalledTimes(1))
    const secondOpen = wrapper.vm.openSession({workspaceHash: 'h1', sessionName: 'b', title: 'b'})
    resolveFirstCreate({success: true})
    await Promise.all([firstOpen, secondOpen])
    await flushPromises()

    expect(desktopChatTabs.show).toHaveBeenCalledTimes(1)
    expect(desktopChatTabs.show).toHaveBeenCalledWith('h1:b', expect.any(Object))
    expect(wrapper.findAll('.desktop-tab').map((item) => item.attributes('title'))).toEqual(['a', 'b'])
    wrapper.unmount()
  })

  it('关闭在途创建的标签后清理原生视图', async () => {
    const desktopChatTabs = chatTabsBridge()
    let resolveCreate
    const pendingCreate = new Promise((resolve) => { resolveCreate = resolve })
    desktopChatTabs.create.mockImplementationOnce(() => pendingCreate)
    window.electronAPI = {desktopChatTabs}
    const {wrapper} = await mountShell()

    const open = wrapper.vm.openSession({workspaceHash: 'h1', sessionName: 'closing', title: 'closing'})
    await vi.waitFor(() => expect(desktopChatTabs.create).toHaveBeenCalledTimes(1))
    const close = wrapper.vm.closeTab('h1:closing')
    resolveCreate({success: true})
    await Promise.all([open, close])

    expect(desktopChatTabs.show).not.toHaveBeenCalled()
    expect(desktopChatTabs.close).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('.desktop-tab')).toHaveLength(0)
    wrapper.unmount()
  })

  it('Electron 环境通过原生菜单返回标签动作', async () => {
    const desktopChatTabs = chatTabsBridge()
    const openNativeMenu = vi.fn().mockResolvedValue('close-left')
    window.electronAPI = {desktopChatTabs, desktopTabMenu: {open: openNativeMenu}}
    const {wrapper} = await mountShell()
    await openTab(wrapper, 'a')
    await openTab(wrapper, 'b')
    await openTab(wrapper, 'c')

    await wrapper.findAll('.desktop-tab')[1].trigger('contextmenu', {clientX: 40, clientY: 30})
    await flushPromises()

    expect(openNativeMenu).toHaveBeenCalledWith({tabId: 'h1:b', index: 1, tabCount: 3, theme: 'gray'})
    expect(desktopChatTabs.close).toHaveBeenCalledWith('h1:a')
    expect(document.body.querySelector('.desktop-tab-context-menu')).toBeNull()
    expect(wrapper.findAll('.desktop-tab').map((item) => item.attributes('title'))).toEqual(['b', 'c'])

    wrapper.unmount()
  })

  it('桌面壳缺少原生菜单 API 时不回退 DOM 菜单', async () => {
    const desktopChatTabs = chatTabsBridge()
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const originalUrl = window.location.href
    window.history.replaceState({}, '', '/?desktopShell=1')
    window.electronAPI = {desktopChatTabs}
    const {wrapper} = await mountShell()
    await openTab(wrapper, 'a')

    await wrapper.find('.desktop-tab').trigger('contextmenu', {clientX: 40, clientY: 30})
    expect(warn).toHaveBeenCalledWith('[desktop-shell] native tab menu API is unavailable')
    expect(document.body.querySelector('.desktop-tab-context-menu')).toBeNull()

    wrapper.unmount()
    window.history.replaceState({}, '', originalUrl)
  })
})

describe('DesktopShell 清空三天前的会话', () => {
  function chatTabsBridge() {
    return {
      create: vi.fn().mockResolvedValue({success: true}),
      show: vi.fn().mockResolvedValue({success: true}),
      hide: vi.fn().mockResolvedValue({success: true}),
      close: vi.fn().mockResolvedValue({success: true}),
      reload: vi.fn().mockResolvedValue({success: true}),
      sendCommand: vi.fn().mockResolvedValue(true)
    }
  }

  it('确认后按三天前阈值调用清理接口，仅关闭被删除会话的标签', async () => {
    sessionsAPI.clearBefore.mockResolvedValue({success: true, data: {sessionNames: ['old-1']}})
    const desktopChatTabs = chatTabsBridge()
    window.electronAPI = {desktopChatTabs}
    const {wrapper} = await mountShell()
    await openTab(wrapper, 'old-1')
    await openTab(wrapper, 'recent')

    wrapper.vm.confirmClearOldSessions({hash: 'h1', name: 'A'})
    expect(wrapper.vm.deleteConfirm.visible).toBe(true)
    expect(wrapper.vm.deleteConfirm.title).toBe('清空三天前的会话？')
    wrapper.vm.handleDeleteConfirmAction('confirm')
    await flushPromises()

    const [workspaceHash, before] = sessionsAPI.clearBefore.mock.calls[0]
    expect(workspaceHash).toBe('h1')
    expect(before).toBeLessThanOrEqual(Date.now())
    expect(before).toBeGreaterThan(Date.now() - 4 * 24 * 60 * 60 * 1000)
    expect(desktopChatTabs.close).toHaveBeenCalledWith('h1:old-1')
    expect(wrapper.findAll('.desktop-tab').map((item) => item.attributes('title'))).toEqual(['recent'])

    wrapper.unmount()
  })

  it('没有过期会话时不关闭任何标签', async () => {
    sessionsAPI.clearBefore.mockResolvedValue({success: true, data: {sessionNames: []}})
    const desktopChatTabs = chatTabsBridge()
    window.electronAPI = {desktopChatTabs}
    const {wrapper} = await mountShell()
    await openTab(wrapper, 'recent')

    wrapper.vm.confirmClearOldSessions({hash: 'h1', name: 'A'})
    wrapper.vm.handleDeleteConfirmAction('confirm')
    await flushPromises()

    expect(desktopChatTabs.close).not.toHaveBeenCalled()
    expect(wrapper.findAll('.desktop-tab')).toHaveLength(1)

    wrapper.unmount()
  })

  it('接口失败时提示错误', async () => {
    sessionsAPI.clearBefore.mockResolvedValue({success: false, message: '服务不可用'})
    const {wrapper} = await mountShell()

    wrapper.vm.confirmClearOldSessions({hash: 'h1', name: 'A'})
    wrapper.vm.handleDeleteConfirmAction('confirm')
    await flushPromises()

    expect(sessionsAPI.clearBefore).toHaveBeenCalledWith('h1', expect.any(Number))

    wrapper.unmount()
  })
})

describe('DesktopShell 删除项目', () => {
  it('确认后调用删除接口并刷新项目列表', async () => {
    configAPI.deleteWorkspace.mockResolvedValue({success: true})
    const {wrapper} = await mountShell()

    wrapper.vm.confirmDeleteWorkspace({hash: 'h1', name: 'A'})
    expect(wrapper.vm.deleteConfirm.visible).toBe(true)
    expect(wrapper.vm.deleteConfirm.title).toBe('删除项目？')
    wrapper.vm.handleDeleteConfirmAction('confirm')
    await flushPromises()

    expect(configAPI.deleteWorkspace).toHaveBeenCalledWith('h1')
    expect(wrapper.vm.workspaces.some((workspace) => workspace.hash === 'h1')).toBe(false)

    wrapper.unmount()
  })

  it('删除接口失败时提示错误且不刷新列表', async () => {
    configAPI.deleteWorkspace.mockResolvedValue({success: false, message: '服务不可用'})
    const {wrapper} = await mountShell()

    wrapper.vm.confirmDeleteWorkspace({hash: 'h1', name: 'A'})
    wrapper.vm.handleDeleteConfirmAction('confirm')
    await flushPromises()

    expect(configAPI.deleteWorkspace).toHaveBeenCalledWith('h1')
    expect(wrapper.vm.workspaces.some((workspace) => workspace.hash === 'h1')).toBe(true)

    wrapper.unmount()
  })
})
