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
    clearAll: vi.fn()
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
  if (initialElectronAPI === undefined) delete window.electronAPI
  else window.electronAPI = initialElectronAPI
})

describe('DesktopShell 首页右键菜单', () => {
  it('提供打开需求池和切换主题操作', async () => {
    const {wrapper, store} = await mountShell()
    const homeButton = wrapper.find('.icon-button')

    await homeButton.trigger('contextmenu', {clientX: 40, clientY: 30})

    const menu = document.body.querySelector('.desktop-shell-context-menu')
    expect(menu).not.toBeNull()
    expect(menu.textContent).toContain('打开需求池')
    expect(menu.textContent).toContain('切换主题')
    expect(homeButton.attributes('aria-expanded')).toBe('true')

    const menuItems = menu.querySelectorAll('[role="menuitem"]')
    menuItems[0].click()
    await nextTick()
    expect(window.open).toHaveBeenCalledWith(expect.stringContaining('requirementBoard=1'), '_blank')
    expect(document.body.querySelector('.desktop-shell-context-menu')).toBeNull()

    await homeButton.trigger('contextmenu', {clientX: 40, clientY: 30})
    document.body.querySelectorAll('[role="menuitem"]')[1].click()
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
