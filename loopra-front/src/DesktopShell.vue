<template>
  <div class="desktop-shell" :data-theme="theme">
    <SplashScreen v-if="starting" @ready="onReady" @error="onStartError" />

    <header class="desktop-titlebar">
      <div class="desktop-left-controls">
        <button
          class="icon-button"
          :class="{ active: isHomeActive }"
          type="button"
          title="会话首页"
          :aria-pressed="isHomeActive"
          @click="showHome"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M17.5 14v7M14 17.5h7"/></svg>
        </button>
      </div>

      <nav class="desktop-tabs" aria-label="会话标签">
        <div
          v-for="tab in tabs"
          :key="tab.id"
          class="desktop-tab"
          :class="{ active: tab.id === activeTabId }"
          role="tab"
          :aria-selected="tab.id === activeTabId"
          tabindex="0"
          :title="tab.title"
          @click="activateTab(tab.id)"
          @keydown.enter="activateTab(tab.id)"
          @keydown.space.prevent="activateTab(tab.id)"
        >
          <span>{{ tab.title }}</span>
          <button class="desktop-tab-close" type="button" :aria-label="`关闭 ${tab.title}`" @click.stop="closeTab(tab.id)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18"/></svg>
          </button>
        </div>
        <button class="desktop-tab-add" type="button" title="新建会话" aria-label="新建会话" @click="createTab">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14" /></svg>
        </button>
      </nav>

      <div class="desktop-window-controls">
        <button v-if="activeTabId" class="window-button" type="button" title="元素检查" @click="openElementInspector">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/></svg>
        </button>
        <button v-if="activeTabId" class="window-button" type="button" title="切换右侧栏" @click="toggleRightPanel">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M15 3v18"/><path d="M7 8h4M7 12h4M7 16h4"/></svg>
        </button>
        <button class="window-button" type="button" title="最小化" @click="minimize"><span class="minimize-mark" /></button>
        <button class="window-button" type="button" title="最大化" @click="maximize"><span class="maximize-mark" /></button>
        <button class="window-button close" type="button" title="关闭" @click="closeWindow"><span class="close-mark" /></button>
      </div>
    </header>

    <main ref="host" class="desktop-view-host">
      <div v-if="!starting && startupError" class="desktop-empty desktop-error">
        <span>{{ startupError }}</span>
        <button type="button" @click="initializeWorkspace">重试</button>
      </div>
      <DesktopHome
      v-else-if="!starting && !activeTabId && !showSettings && !showModelChannels"
        :workspaces="workspaces"
        :active-workspace-hash="activeWorkspaceHash"
        :theme="theme"
        :refresh-key="homeRefreshKey"
        @select-workspace="selectWorkspace"
        @new-session="createTab"
        @open-session="openSession"
        @open-settings="openSettings"
        @toggle-theme="toggleTheme"
        @add-workspace="addWorkspaceFromFolder"
        @delete-session="confirmDeleteSession"
        @clear-workspace="confirmClearWorkspace"
        @delete-workspace="confirmDeleteWorkspace"
      />
      <ModelChannels v-else-if="!starting && showModelChannels" class="desktop-settings" :show-back="false" />
      <SettingsView v-else-if="!starting && showSettings" class="desktop-settings" />
    </main>
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue'
import {message, Modal} from 'ant-design-vue'
import {useAppStore} from './stores/app'
import {configAPI, sessionsAPI} from './services/api'
import {platform} from './services/platform'
import SplashScreen from './components/SplashScreen.vue'
import DesktopHome from './DesktopHome.vue'
import SettingsView from './views/Settings.vue'
import ModelChannels from './ModelChannels.vue'

const store = useAppStore()
const theme = computed(() => store.settings.theme)
const starting = ref(true)
const creating = ref(false)
const startupError = ref('')
const workspaces = ref([])
const activeWorkspaceHash = ref('')
const homeRefreshKey = ref(0)
const showSettings = ref(false)
const showModelChannels = ref(false)
const tabs = ref([])
const activeTabId = ref('')
const isHomeActive = computed(() => !starting.value && !startupError.value
  && !activeTabId.value && !showSettings.value && !showModelChannels.value)
const host = ref(null)
let resizeObserver = null
let renderVersion = 0

const tabId = (workspaceHash, sessionName) => `${workspaceHash || ''}:${sessionName}`
const tabTitle = (sessionName) => {
  const match = String(sessionName).match(/(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})/)
  return match ? '新建会话' : (String(sessionName).replace(/[-_]+/g, ' ').slice(0, 24) || '新建会话')
}

const nativeTabs = () => window.electronAPI?.desktopChatTabs
watch(theme, (value) => { void nativeTabs()?.setTheme(value) })
const stopTitleListener = window.electronAPI?.events?.listen('desktop-chat-tab-title', ({ tabId, title }) => {
  if (!tabId || !title) return
  tabs.value = tabs.value.map((tab) => tab.id === tabId ? { ...tab, title } : tab)
})
const stopOpenSettingsListener = window.electronAPI?.events?.listen('desktop-shell-open-model-channels', () => { void openModelChannels() })

async function renderActiveTab() {
  const current = tabs.value.find((tab) => tab.id === activeTabId.value)
  const bridge = nativeTabs()
  if (!bridge) return
  const version = ++renderVersion
  if (!current) {
    await bridge.hide()
    return
  }
  await nextTick()
  if (!host.value) return
  try {
    await bridge.create({
      id: current.id,
      sessionName: current.sessionName,
      workspaceHash: current.workspaceHash,
      theme: theme.value
    })
    if (version !== renderVersion) return
    const bounds = host.value.getBoundingClientRect()
    await bridge.show(current.id, {
      x: Math.round(bounds.left), y: Math.round(bounds.top), width: Math.round(bounds.width), height: Math.round(bounds.height)
    })
  } catch (error) {
    console.error('[desktop-shell] failed to show tab:', error)
    message.error('打开会话失败：' + (error.message || '未知错误'))
  }
}

async function createTab() {
  if (creating.value || starting.value) return
  if (!activeWorkspaceHash.value) {
    startupError.value = '未找到可用工作区，请先在网页版添加工作区。'
    return
  }
  creating.value = true
  try {
    const response = await sessionsAPI.createNew({ workspaceHash: activeWorkspaceHash.value })
    if (!response.success || !response.data?.sessionName) throw new Error(response.message || '创建会话失败')
    const sessionName = response.data.sessionName
    const workspaceHash = response.data.workspaceHash || ''
    const id = tabId(workspaceHash, sessionName)
    tabs.value = [...tabs.value, { id, sessionName, workspaceHash, title: tabTitle(sessionName) }]
    activeTabId.value = id
    startupError.value = ''
    await renderActiveTab()
  } catch (error) {
    const errorMessage = '新建会话失败：' + (error.message || '未知错误')
    message.error(errorMessage)
    if (tabs.value.length === 0) startupError.value = errorMessage
  } finally {
    creating.value = false
  }
}

async function openSession({ workspaceHash, sessionName, title }) {
  if (!workspaceHash || !sessionName) {
    message.error('会话信息不完整，无法打开')
    return
  }
  const id = tabId(workspaceHash, sessionName)
  if (!tabs.value.some((tab) => tab.id === id)) {
    tabs.value = [...tabs.value, { id, sessionName, workspaceHash, title: title || tabTitle(sessionName) }]
  }
  activeTabId.value = id
  try {
    await renderActiveTab()
  } catch (error) {
    message.error('打开会话失败：' + (error.message || '未知错误'))
  }
  void selectWorkspace(workspaceHash).catch((error) => {
    console.warn('[desktop-shell] failed to synchronize workspace:', error)
  })
}

async function initializeWorkspaceContext() {
  const [workspacesResult, currentWorkspaceResult] = await Promise.all([
    configAPI.listWorkspaces(),
    configAPI.getWorkspace()
  ])
  if (!workspacesResult.success) {
    throw new Error(workspacesResult.message || '加载工作区失败')
  }

  workspaces.value = workspacesResult.data || []
  if (workspaces.value.length === 0) {
    throw new Error('未找到可用工作区，请先在网页版添加工作区。')
  }

  const currentPath = currentWorkspaceResult.success
    ? (currentWorkspaceResult.data?.workspace || currentWorkspaceResult.data)
    : ''
  const selectedWorkspace = workspaces.value.find((item) => item.path === currentPath) || workspaces.value[0]
  const switchResult = await configAPI.switchWorkspace(selectedWorkspace.path)
  if (!switchResult.success) {
    throw new Error(switchResult.message || '切换默认工作区失败')
  }
  activeWorkspaceHash.value = selectedWorkspace.hash
}

async function initializeWorkspace() {
  if (creating.value) return
  startupError.value = ''
  try {
    await initializeWorkspaceContext()
  } catch (error) {
    console.error('[desktop-shell] failed to initialize workspace:', error)
    startupError.value = error.message || '初始化默认工作区失败'
  }
}

async function selectWorkspace(workspaceHash) {
  const workspace = workspaces.value.find((item) => item.hash === workspaceHash)
  if (!workspace) throw new Error('工作区不存在')
  if (workspaceHash === activeWorkspaceHash.value) return
  const response = await configAPI.switchWorkspace(workspace.path)
  if (!response.success) throw new Error(response.message || '切换工作区失败')
  activeWorkspaceHash.value = workspaceHash
}

async function addWorkspaceFromFolder() {
  try {
    const path = await window.electronAPI?.loopraWebService?.pickFolder?.()
    if (!path) return
    const response = await configAPI.switchWorkspace(path)
    if (!response.success) throw new Error(response.message || '添加项目失败')
    const workspacesResult = await configAPI.listWorkspaces()
    if (!workspacesResult.success) throw new Error(workspacesResult.message || '刷新项目列表失败')
    workspaces.value = workspacesResult.data || []
    const selectedPath = response.data?.workspace || path
    const workspace = workspaces.value.find((item) => item.path === selectedPath)
    if (!workspace) throw new Error('项目添加成功，但未找到项目记录')
    activeWorkspaceHash.value = workspace.hash
    homeRefreshKey.value++
    message.success('项目已添加')
  } catch (error) {
    message.error('添加项目失败：' + (error.message || '未知错误'))
  }
}

async function showHome() {
  showSettings.value = false
  showModelChannels.value = false
  activeTabId.value = ''
  await renderActiveTab()
}

async function openSettings() {
  showModelChannels.value = false
  showSettings.value = true
  activeTabId.value = ''
  await renderActiveTab()
}

async function openModelChannels() {
  showSettings.value = false
  showModelChannels.value = true
  activeTabId.value = ''
  await renderActiveTab()
}

function toggleTheme() {
  store.settings.theme = theme.value === 'dark' ? 'gray' : 'dark'
}

async function openElementInspector() {
  try {
    await window.electronAPI?.elementInspectorWindow?.open()
  } catch (error) {
    message.error('打开元素检查失败：' + (error.message || '未知错误'))
  }
}

async function toggleRightPanel() {
  if (!activeTabId.value) return
  try {
    await nativeTabs()?.toggleRightPanel(activeTabId.value)
  } catch (error) {
    message.error('切换右侧栏失败：' + (error.message || '未知错误'))
  }
}

async function activateTab(id) {
  if (id === activeTabId.value) return
  const tab = tabs.value.find((item) => item.id === id)
  if (!tab) return
  try {
    await selectWorkspace(tab.workspaceHash)
    activeTabId.value = id
    await renderActiveTab()
  } catch (error) {
    message.error('切换会话失败：' + (error.message || '未知错误'))
  }
}

async function closeTab(id) {
  const index = tabs.value.findIndex((tab) => tab.id === id)
  if (index < 0) return
  try { await nativeTabs()?.close(id) } catch (error) { console.warn('[desktop-shell] failed to close tab:', error) }
  const remaining = tabs.value.filter((tab) => tab.id !== id)
  const wasActive = activeTabId.value === id
  tabs.value = remaining
  if (wasActive) activeTabId.value = remaining[Math.min(index, remaining.length - 1)]?.id || ''
  await renderActiveTab()
}

async function closeWorkspaceTabs(workspaceHash) {
  const removedTabs = tabs.value.filter((tab) => tab.workspaceHash === workspaceHash)
  if (!removedTabs.length) return
  await Promise.all(removedTabs.map(async (tab) => {
    try {
      await nativeTabs()?.close(tab.id)
    } catch (error) {
      console.warn('[desktop-shell] failed to close tab:', error)
    }
  }))
  const removedIds = new Set(removedTabs.map((tab) => tab.id))
  tabs.value = tabs.value.filter((tab) => !removedIds.has(tab.id))
  if (removedIds.has(activeTabId.value)) activeTabId.value = tabs.value[0]?.id || ''
  await renderActiveTab()
}

function confirmDeleteSession(session) {
  const title = session?.title || session?.name || '此会话'
  Modal.confirm({
    title: '删除会话？',
    content: `“${title}”将被永久删除，无法恢复。`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const response = await sessionsAPI.deleteSession(session.name, session.workspaceHash)
        if (!response.success) throw new Error(response.message || '删除会话失败')
        await closeTab(tabId(session.workspaceHash, session.name))
        homeRefreshKey.value++
        message.success('会话已删除')
      } catch (error) {
        message.error('删除会话失败：' + (error.message || '未知错误'))
      }
    }
  })
}

function confirmClearWorkspace(workspace) {
  Modal.confirm({
    title: '清空项目会话？',
    content: `“${workspace.name}”中的全部会话将被永久删除，无法恢复。`,
    okText: '清空',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const response = await sessionsAPI.clearAll(workspace.hash)
        if (!response.success) throw new Error(response.message || '清空会话失败')
        await closeWorkspaceTabs(workspace.hash)
        homeRefreshKey.value++
        message.success('项目会话已清空')
      } catch (error) {
        message.error('清空会话失败：' + (error.message || '未知错误'))
      }
    }
  })
}

function confirmDeleteWorkspace(workspace) {
  Modal.confirm({
    title: '删除项目？',
    content: `“${workspace.name}”将从项目列表移除；项目文件不会被删除。`,
    okText: '删除项目',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const response = await configAPI.deleteWorkspace(workspace.hash)
        if (!response.success) throw new Error(response.message || '删除项目失败')
        await closeWorkspaceTabs(workspace.hash)
        workspaces.value = workspaces.value.filter((item) => item.hash !== workspace.hash)
        if (activeWorkspaceHash.value === workspace.hash) {
          activeWorkspaceHash.value = ''
          if (workspaces.value[0]) await selectWorkspace(workspaces.value[0].hash)
        }
        homeRefreshKey.value++
        message.success('项目已删除')
      } catch (error) {
        message.error('删除项目失败：' + (error.message || '未知错误'))
      }
    }
  })
}

async function onReady() {
  starting.value = false
  await nextTick()
  resizeObserver = new ResizeObserver(() => { void renderActiveTab() })
  if (host.value) resizeObserver.observe(host.value)
  await initializeWorkspace()
}

function onStartError(error) {
  starting.value = false
  startupError.value = '桌面服务启动失败：' + (error?.message || error || '未知错误')
}

async function minimize() { await platform.implementation.window.minimize() }
async function maximize() { await platform.implementation.window.maximize() }
async function closeWindow() { await platform.implementation.window.close() }

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  stopTitleListener?.()
  stopOpenSettingsListener?.()
  void nativeTabs()?.hide()
})
</script>

<style scoped>
.desktop-shell { width: 100vw; height: 100vh; display: flex; flex-direction: column; overflow: hidden; background: var(--bg, #fff); color: var(--fg, #202124); }
.desktop-titlebar { height: 44px; min-height: 44px; display: flex; align-items: center; border-bottom: 1px solid var(--border, #e8e8e8); background: var(--bg, #fff); -webkit-app-region: drag; user-select: none; }
.desktop-left-controls { display: flex; align-items: center; padding: 0 14px 0 32px; flex: 0 0 auto; }
.icon-button, .desktop-tab, .desktop-tab-add, .window-button { -webkit-app-region: no-drag; border: 0; background: transparent; color: var(--fg-2, #5f6368); }
.icon-button { width: 28px; height: 28px; padding: 5px; border-radius: 5px; }
.icon-button svg, .desktop-tab svg, .desktop-tab-add svg { width: 18px; height: 18px; }
.icon-button:hover, .icon-button.active, .desktop-tab-add:hover, .window-button:hover { background: var(--bg-3, #f3f4f6); color: var(--fg, #202124); }
.desktop-tabs { height: 100%; display: flex; align-items: center; gap: 4px; min-width: 80px; flex: 1; overflow-x: auto; padding: 0 18px; scrollbar-width: none; }
.desktop-tabs::-webkit-scrollbar { display: none; }
.desktop-tab { display: inline-flex; align-items: center; gap: 7px; width: clamp(156px, 16vw, 230px); height: 30px; padding: 0 10px; border-radius: 6px; cursor: pointer; flex: 0 0 auto; text-align: left; }
.desktop-tab:hover { background: var(--bg-3, #f3f4f6); color: var(--fg, #202124); }
.desktop-tab.active { background: var(--bg-3, #f1f2f4); color: var(--fg, #202124); }
.desktop-tab.active > span:not(.desktop-tab-close) { font-weight: 500; }
.desktop-tab > span:not(.desktop-tab-close) { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; flex: 1; font-size: 14px; font-weight: 400; }
.desktop-tab-close { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; border-radius: 4px; flex: 0 0 auto; }
.desktop-tab-close svg { width: 14px; height: 14px; }
.desktop-tab-close:hover { background: rgba(0, 0, 0, 0.08); }
.desktop-tab-add { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border-radius: 5px; flex: 0 0 auto; cursor: pointer; }
.desktop-window-controls { height: 100%; display: flex; align-items: center; padding-right: 14px; flex: 0 0 auto; -webkit-app-region: no-drag; }
.window-button { width: 44px; height: 30px; display: inline-flex; align-items: center; justify-content: center; border-radius: 5px; }
.window-button svg { width: 17px; height: 17px; }
.window-button.close:hover { background: #e81123; color: #fff; }
.minimize-mark { width: 13px; border-top: 1.5px solid currentColor; }
.maximize-mark { width: 13px; height: 13px; border: 1.5px solid currentColor; border-radius: 2px; }
.close-mark { width: 14px; height: 14px; position: relative; }
.close-mark::before, .close-mark::after { content: ''; position: absolute; top: 6px; left: 0; width: 14px; border-top: 1.5px solid currentColor; transform: rotate(45deg); }
.close-mark::after { transform: rotate(-45deg); }
.desktop-view-host { flex: 1; min-width: 0; min-height: 0; background: var(--bg, #fff); }
.desktop-settings { height: 100%; min-height: 0; overflow: hidden; }
.desktop-empty { height: 100%; display: grid; place-items: center; color: var(--fg-4, #9ca3af); font-size: 14px; }
.desktop-error { align-content: center; gap: 12px; }
.desktop-error button { justify-self: center; border: 1px solid var(--border, #e5e7eb); border-radius: 5px; background: var(--bg, #fff); color: var(--fg, #202124); padding: 6px 14px; cursor: pointer; }
.desktop-error button:hover { background: var(--bg-3, #f3f4f6); }
</style>
