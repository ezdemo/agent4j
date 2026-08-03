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

      <nav ref="tabsNav" class="desktop-tabs" aria-label="会话标签" @wheel="scrollTabs">
        <div
          v-for="tab in tabs"
          :key="tab.id"
          class="desktop-tab"
          :class="{ active: tab.id === activeTabId, dragging: tab.id === draggedTabId, 'drag-over': tab.id === dragOverTabId }"
          draggable="true"
          role="tab"
          :aria-selected="tab.id === activeTabId"
          tabindex="0"
          :title="tab.title"
          @dragstart="startTabReorder($event, tab.id)"
          @dragover="dragOverTab($event, tab.id)"
          @drop="dropTab($event, tab.id)"
          @dragend="endTabReorder"
          @click="activateTab(tab.id)"
          @auxclick="closeTabWithMiddleClick($event, tab.id)"
          @keydown.enter="activateTab(tab.id)"
          @keydown.space.prevent="activateTab(tab.id)"
        >
          <span v-if="workspaceNameOf(tab.workspaceHash)" class="desktop-tab-monogram" :class="badgeTone(workspaceNameOf(tab.workspaceHash))">{{ initial(workspaceNameOf(tab.workspaceHash)) }}</span>
          <span>{{ tab.title }}</span>
          <div class="desktop-tab-actions">
            <button class="desktop-tab-reload" type="button" :aria-label="`刷新 ${tab.title}`" title="刷新会话" @click.stop="reloadTab(tab.id)">
              <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
            </button>
            <button class="desktop-tab-close" type="button" :aria-label="`关闭 ${tab.title}`" @click.stop="closeTab(tab.id)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18"/></svg>
            </button>
          </div>
        </div>
        <button class="desktop-tab-add" type="button" title="新建会话" aria-label="新建会话" @click="createTab">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14" /></svg>
        </button>
      </nav>

      <div class="desktop-window-controls">
        <button
          class="window-button update-check-button"
          :class="{ 'has-update': hasNewVersion }"
          type="button"
          :title="hasNewVersion ? `发现新版本 v${latestVersion}，点击前往发布页` : (latestVersion ? `已是最新版本 v${latestVersion}，点击检查更新` : '检查更新')"
          @click="onUpdateButtonClick"
        >
          <svg v-if="checkingUpdate" class="update-spinner" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          <span v-if="hasNewVersion" class="update-label">更新</span>
          <i v-if="hasNewVersion" class="update-dot" />
        </button>
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
        v-else-if="!starting && !activeTabId && !showSkills && !showTools && !showSubAgents && !showSettings && !showModelChannels && !showDashboard"
        :workspaces="workspaces"
        :active-workspace-hash="activeWorkspaceHash"
        :theme="theme"
        :refresh-key="homeRefreshKey"
        :refreshing="refreshingHome"
        @select-workspace="selectWorkspace"
        @new-session="createTab"
        @open-session="openSession"
        @open-skills="openSkills"
        @open-tools="openTools"
        @open-settings="openSettings"
        @toggle-theme="toggleTheme"
        @add-workspace="addWorkspaceFromFolder"
        @refresh="refreshHome"
        @delete-session="confirmDeleteSession"
        @clear-workspace="confirmClearWorkspace"
        @delete-workspace="confirmDeleteWorkspace"
        @reorder-workspaces="reorderWorkspaces"
      />
      <SettingsView v-else-if="!starting && showSkills" class="desktop-settings" market-only />
      <ToolsView v-else-if="!starting && showTools" class="desktop-tools" />
      <SubAgentsView v-else-if="!starting && showSubAgents" class="desktop-sub-agents" />
      <ModelChannels v-else-if="!starting && showModelChannels" class="desktop-settings" :show-back="false" @saved="reloadAfterModelChannelsSaved" />
      <section v-else-if="!starting && showDashboard" class="desktop-dashboard">
        <header class="desktop-dashboard-header">
          <div>
            <h1>数据面板</h1>
            <p>查看模型调用的 Token、费用和请求统计</p>
          </div>
        </header>
        <DashboardPanel class="desktop-dashboard-content" />
      </section>
      <SettingsView v-else-if="!starting && showSettings" class="desktop-settings" @open-sub-agents="openSubAgents" @open-dashboard="openDashboard" />
    </main>
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, ref, watch} from 'vue'
import {message, Modal} from 'ant-design-vue'
import {useAppStore} from './stores/app'
import {configAPI, sessionsAPI, systemAPI} from './services/api'
import {RELEASE_LATEST_URL} from './utils/constants'
import {platform} from './services/platform'
import SplashScreen from './components/SplashScreen.vue'
import DesktopHome from './DesktopHome.vue'
import SettingsView from './views/Settings.vue'
import ToolsView from './views/Tools.vue'
import SubAgentsView from './views/SubAgents.vue'
import ModelChannels from './ModelChannels.vue'
import DashboardPanel from './components/Dashboard.vue'
import {hasConfiguredModelChannel} from './utils/modelChannels'

const store = useAppStore()
const theme = computed(() => store.settings.theme)
const starting = ref(true)
const creating = ref(false)
const startupError = ref('')
const workspaces = ref([])
const activeWorkspaceHash = ref('')
const homeRefreshKey = ref(0)
const refreshingHome = ref(false)
const showSkills = ref(false)
const showTools = ref(false)
const showSubAgents = ref(false)
const showSettings = ref(false)
const showModelChannels = ref(false)
const modelChannelsRequireReload = ref(false)
const showDashboard = ref(false)
const tabs = ref([])
const activeTabId = ref('')
const isHomeActive = computed(() => !starting.value && !startupError.value
  && !activeTabId.value && !showSkills.value && !showTools.value && !showSubAgents.value && !showSettings.value && !showModelChannels.value && !showDashboard.value)
const tabsNav = ref(null)
const draggedTabId = ref('')
const dragOverTabId = ref('')
const host = ref(null)
let resizeObserver = null
let renderVersion = 0

// 版本更新检查：启动后立即检查一次，之后每 30 分钟自动定时检查
const UPDATE_CHECK_INTERVAL = 30 * 60 * 1000
let updateCheckTimer = null
const latestVersion = ref('')
const hasNewVersion = ref(false)
const releaseUrl = ref('')
const checkingUpdate = ref(false)

async function checkForUpdates() {
  if (checkingUpdate.value) return
  checkingUpdate.value = true
  try {
    const res = await systemAPI.checkLatestVersion()
    if (res.success && res.data) {
      latestVersion.value = res.data.latestVersion || ''
      releaseUrl.value = res.data.releaseUrl || ''
      // 对比桌面端（Electron）版本：桌面端版本 < 最新版本即提示更新
      let desktopVersion = ''
      if (platform.isElectron) {
        try {
          desktopVersion = await window.electronAPI.getElectronVersion()
        } catch (error) {
          console.warn('[desktop-shell] 获取桌面端版本失败:', error)
        }
      }
      if (desktopVersion && desktopVersion !== '未知' && latestVersion.value) {
        hasNewVersion.value = compareVersions(desktopVersion, latestVersion.value) < 0
      } else {
        // 非桌面环境（Web 模式）无桌面端版本，退化为核心服务版本对比
        hasNewVersion.value = !!res.data.hasNewVersion
      }
    }
  } catch (error) {
    console.warn('[desktop-shell] 检查更新失败:', error)
  } finally {
    checkingUpdate.value = false
  }
}

// 版本对比：支持 v 前缀与 1~4 段数字版本（与 electron/version.cjs 保持一致）
function compareVersions(a, b) {
  const pa = String(a || '').replace(/^v/i, '').split('.').map((part) => Number.parseInt(part, 10) || 0)
  const pb = String(b || '').replace(/^v/i, '').split('.').map((part) => Number.parseInt(part, 10) || 0)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const na = pa[i] || 0
    const nb = pb[i] || 0
    if (na > nb) return 1
    if (na < nb) return -1
  }
  return 0
}

// 无新版时点击手动检查；有新版本时点击跳转发布页
function onUpdateButtonClick() {
  if (hasNewVersion.value) void openReleasePage()
  else void checkForUpdates()
}

async function openReleasePage() {
  const url = (hasNewVersion.value && releaseUrl.value) || RELEASE_LATEST_URL
  if (platform.isElectron) {
    try {
      await window.electronAPI.openExternal(url)
    } catch {
      window.open(url, '_blank')
    }
  } else {
    window.open(url, '_blank')
  }
}

const tabId = (workspaceHash, sessionName) => `${workspaceHash || ''}:${sessionName}`
const tabTitle = (sessionName) => {
  const match = String(sessionName).match(/(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})/)
  return match ? '新建会话' : (String(sessionName).replace(/[-_]+/g, ' ').slice(0, 24) || '新建会话')
}

const nativeTabs = () => window.electronAPI?.desktopChatTabs

// 项目图标：首字符 + 色调（与 TitleBar/DesktopHome 保持一致）
const initial = (name) => String(name || 'L').trim().charAt(0).toUpperCase() || 'L'
const badgeTone = (name) => {
  let hash = 0
  for (const char of String(name || '')) hash = ((hash * 31) + char.charCodeAt(0)) >>> 0
  return `tone-${hash % 8}`
}
const workspaceNameOf = (workspaceHash) => {
  if (!workspaceHash) return ''
  const ws = workspaces.value.find((item) => item.hash === workspaceHash)
  return ws ? ws.name : ''
}

function scrollTabs(event) {
  const nav = tabsNav.value
  if (!nav || nav.scrollWidth <= nav.clientWidth) return
  const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
  if (!delta) return
  event.preventDefault()
  nav.scrollLeft += delta
}

function startTabReorder(event, tabId) {
  if (event.target.closest('button')) {
    event.preventDefault()
    return
  }
  draggedTabId.value = tabId
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', tabId)
}

function dragOverTab(event, tabId) {
  if (!draggedTabId.value || tabId === draggedTabId.value) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
  dragOverTabId.value = tabId
}

function dropTab(event, targetTabId) {
  event.preventDefault()
  const sourceTabId = draggedTabId.value
  endTabReorder()
  if (!sourceTabId || sourceTabId === targetTabId) return
  const sourceIndex = tabs.value.findIndex((tab) => tab.id === sourceTabId)
  const targetIndex = tabs.value.findIndex((tab) => tab.id === targetTabId)
  if (sourceIndex < 0 || targetIndex < 0) return
  const reorderedTabs = [...tabs.value]
  const sourceTab = reorderedTabs[sourceIndex]
  reorderedTabs[sourceIndex] = reorderedTabs[targetIndex]
  reorderedTabs[targetIndex] = sourceTab
  tabs.value = reorderedTabs
}

function endTabReorder() {
  draggedTabId.value = ''
  dragOverTabId.value = ''
}

watch(theme, (value) => { void nativeTabs()?.setTheme(value) })
const stopTitleListener = window.electronAPI?.events?.listen('desktop-chat-tab-title', ({ tabId, title }) => {
  if (!tabId || !title) return
  tabs.value = tabs.value.map((tab) => tab.id === tabId ? { ...tab, title } : tab)
})
const stopWorkspaceListener = window.electronAPI?.events?.listen('desktop-chat-tab-workspace', ({ tabId, workspaceHash }) => {
  if (!tabId || !workspaceHash) return
  tabs.value = tabs.value.map((tab) => tab.id === tabId ? { ...tab, workspaceHash } : tab)
})
const stopOpenHomeListener = window.electronAPI?.events?.listen('desktop-shell-open-home', () => { void showHome() })
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
  const targetHash = activeWorkspaceHash.value || (workspaces.value[0] && workspaces.value[0].hash)
  if (!targetHash) {
    startupError.value = '未找到可用工作区，请先在网页版添加工作区。'
    return
  }
  creating.value = true
  try {
    const response = await sessionsAPI.createNew({ workspaceHash: targetHash })
    if (!response.success || !response.data?.sessionName) throw new Error(response.message || '创建会话失败')
    const sessionName = response.data.sessionName
    const workspaceHash = response.data.workspaceHash || targetHash
    const id = tabId(workspaceHash, sessionName)
    hideStandaloneViews()
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
  hideStandaloneViews()
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

async function refreshHome() {
  if (refreshingHome.value) return
  refreshingHome.value = true
  try {
    const response = await configAPI.listWorkspaces()
    if (!response.success) throw new Error(response.message || '刷新项目列表失败')
    workspaces.value = response.data || []
    if (activeWorkspaceHash.value && !workspaces.value.some((workspace) => workspace.hash === activeWorkspaceHash.value)) {
      activeWorkspaceHash.value = ''
    }
    homeRefreshKey.value++
  } catch (error) {
    message.error('刷新失败：' + (error.message || '未知错误'))
  } finally {
    refreshingHome.value = false
  }
}

// 项目拖拽排序：本地立即重排，并持久化到服务端；失败时回滚重新加载
async function reorderWorkspaces(orderedHashes) {
  if (!Array.isArray(orderedHashes) || orderedHashes.length === 0) return
  const byHash = new Map(workspaces.value.map((workspace) => [workspace.hash, workspace]))
  const reordered = orderedHashes.map((hash) => byHash.get(hash)).filter(Boolean)
  if (reordered.length !== workspaces.value.length) return
  workspaces.value = reordered
  try {
    const response = await configAPI.saveWorkspaceOrder(orderedHashes)
    if (!response.success) throw new Error(response.message || '保存排序失败')
  } catch (error) {
    message.error('保存排序失败：' + (error.message || '未知错误'))
    await refreshHome()
  }
}

async function selectWorkspace(workspaceHash) {
  if (!workspaceHash) {
    // 取消选中，展示所有会话
    activeWorkspaceHash.value = ''
    return
  }
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
  hideStandaloneViews()
  activeTabId.value = ''
  await renderActiveTab()
}

async function openSkills() {
  hideStandaloneViews()
  showSkills.value = true
  activeTabId.value = ''
  await renderActiveTab()
}

async function openTools() {
  hideStandaloneViews()
  showTools.value = true
  activeTabId.value = ''
  await renderActiveTab()
}

async function openSubAgents() {
  hideStandaloneViews()
  showSubAgents.value = true
  activeTabId.value = ''
  await renderActiveTab()
}

async function openSettings() {
  hideStandaloneViews()
  showSettings.value = true
  activeTabId.value = ''
  await renderActiveTab()
}

async function openModelChannels({requireReload = false} = {}) {
  modelChannelsRequireReload.value = requireReload
  hideStandaloneViews()
  showModelChannels.value = true
  activeTabId.value = ''
  await renderActiveTab()
}

async function openDashboard() {
  hideStandaloneViews()
  showDashboard.value = true
  activeTabId.value = ''
  await renderActiveTab()
}

function hideStandaloneViews() {
  showSkills.value = false
  showTools.value = false
  showSubAgents.value = false
  showSettings.value = false
  showModelChannels.value = false
  showDashboard.value = false
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
    hideStandaloneViews()
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

async function reloadTab(id) {
  try {
    await nativeTabs()?.reload(id)
  } catch (error) {
    message.error('刷新会话失败：' + (error.message || '未知错误'))
  }
}

function closeTabWithMiddleClick(event, id) {
  if (event.button !== 1) return
  event.preventDefault()
  void closeTab(id)
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
  // 启动后立即检查更新，并开启定时检查
  void checkForUpdates()
  updateCheckTimer = setInterval(() => { void checkForUpdates() }, UPDATE_CHECK_INTERVAL)
  if (await redirectToModelChannelsWhenUnconfigured()) return
  await initializeWorkspace()
}

async function redirectToModelChannelsWhenUnconfigured() {
  try {
    const response = await configAPI.getConfig()
    if (response.success && !hasConfiguredModelChannel(response.data)) {
      await openModelChannels({requireReload: true})
      return true
    }
  } catch (error) {
    console.warn('[desktop-shell] failed to load model channels:', error)
  }
  return false
}

function reloadAfterModelChannelsSaved() {
  if (modelChannelsRequireReload.value) {
    window.location.reload()
    return
  }
  modelChannelsRequireReload.value = false
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
  if (updateCheckTimer) {
    clearInterval(updateCheckTimer)
    updateCheckTimer = null
  }
  stopTitleListener?.()
  stopWorkspaceListener?.()
  stopOpenHomeListener?.()
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
.desktop-tab.dragging { opacity: 0.55; }
.desktop-tab.drag-over { background: var(--bg-3, #f3f4f6); box-shadow: inset 0 0 0 1px var(--border, #d6dae1); }
.desktop-tabs::-webkit-scrollbar { display: none; }
.desktop-tab { display: inline-flex; align-items: center; gap: 7px; width: clamp(156px, 16vw, 230px); height: 30px; padding: 0 10px; border-radius: 6px; cursor: pointer; flex: 0 0 auto; text-align: left; }
.desktop-tab:hover { background: var(--bg-3, #f3f4f6); color: var(--fg, #202124); }
.desktop-tab.active { background: var(--bg-3, #f1f2f4); color: var(--fg, #202124); }
.desktop-tab.active > span:not(.desktop-tab-monogram) { font-weight: 500; }
.desktop-tab > span:not(.desktop-tab-monogram) { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; flex: 1; font-size: 14px; font-weight: 400; }
.desktop-tab-actions { display: flex; align-items: center; gap: 2px; flex: 0 0 auto; }
.desktop-tab-reload, .desktop-tab-close { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; border: 0; border-radius: 4px; background: transparent; color: inherit; cursor: pointer; }
.desktop-tab-reload { display: none; }
.desktop-tab:hover .desktop-tab-reload, .desktop-tab:focus-within .desktop-tab-reload { display: inline-flex; }
.desktop-tab-reload svg { width: 12px; height: 12px; }
.desktop-tab-close svg { width: 14px; height: 14px; }
.desktop-tab-reload:hover, .desktop-tab-close:hover { background: rgba(0, 0, 0, 0.08); }
.desktop-tab-monogram { width: 16px; height: 16px; display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; border-radius: 4px; color: #fff; font-size: 10px; font-weight: 700; line-height: 1; text-shadow: 0 1px rgba(0, 0, 0, 0.25); box-shadow: inset 0 1px rgba(255, 255, 255, 0.25), 0 1px 1px rgba(0, 0, 0, 0.16); }
.desktop-tab-monogram.tone-0 { background: linear-gradient(135deg, #8b95a3, #5e6878); }
.desktop-tab-monogram.tone-1 { background: linear-gradient(135deg, #3dd0e8, #18b4d0); }
.desktop-tab-monogram.tone-2 { background: linear-gradient(135deg, #ffa86b, #ff7a3d); }
.desktop-tab-monogram.tone-3 { background: linear-gradient(135deg, #9aacf5, #6d80e8); }
.desktop-tab-monogram.tone-4 { background: linear-gradient(135deg, #6dd49d, #3eb878); }
.desktop-tab-monogram.tone-5 { background: linear-gradient(135deg, #f87fb5, #e85a9c); }
.desktop-tab-monogram.tone-6 { background: linear-gradient(135deg, #fcd34d, #f5b800); }
.desktop-tab-monogram.tone-7 { background: linear-gradient(135deg, #4dd9a6, #20c084); }
.desktop-tab-add { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border-radius: 5px; flex: 0 0 auto; cursor: pointer; }
.desktop-window-controls { height: 100%; display: flex; align-items: center; padding-right: 14px; flex: 0 0 auto; -webkit-app-region: no-drag; }
.window-button { width: 44px; height: 30px; display: inline-flex; align-items: center; justify-content: center; border-radius: 5px; }
.update-check-button.has-update { width: auto; padding: 0 12px; gap: 5px; background: rgba(59, 130, 246, 0.1); color: #2563eb; }
.update-check-button.has-update:hover { background: rgba(59, 130, 246, 0.16); color: #1d4ed8; }
.update-label { font-size: 12px; font-weight: 500; line-height: 1; }
.update-dot { width: 5px; height: 5px; border-radius: 50%; background: #ff4d4f; flex: 0 0 auto; }
.update-spinner { animation: update-spin 0.9s linear infinite; }
@keyframes update-spin { to { transform: rotate(360deg); } }
[data-theme="dark"] .update-check-button.has-update { background: rgba(96, 165, 250, 0.14); color: #93c5fd; }
[data-theme="dark"] .update-check-button.has-update:hover { background: rgba(96, 165, 250, 0.22); color: #bfdbfe; }
.window-button svg { width: 17px; height: 17px; }
.update-check-button svg { width: 14px; height: 14px; }
.window-button.close:hover { background: #e81123; color: #fff; }
.minimize-mark { width: 13px; border-top: 1.5px solid currentColor; }
.maximize-mark { width: 13px; height: 13px; border: 1.5px solid currentColor; border-radius: 2px; }
.close-mark { width: 14px; height: 14px; position: relative; }
.close-mark::before, .close-mark::after { content: ''; position: absolute; top: 6px; left: 0; width: 14px; border-top: 1.5px solid currentColor; transform: rotate(45deg); }
.close-mark::after { transform: rotate(-45deg); }
.desktop-view-host { flex: 1; min-width: 0; min-height: 0; background: var(--bg, #fff); }
.desktop-settings { height: 100%; min-height: 0; overflow: hidden; }
.desktop-tools, .desktop-sub-agents { box-sizing: border-box; height: 100%; min-height: 0; overflow: auto; }
.desktop-dashboard { height: 100%; min-height: 0; overflow: auto; }
.desktop-dashboard-header { height: 64px; display: flex; align-items: center; padding: 0 28px; border-bottom: 1px solid var(--border, #e8e8e8); }
.desktop-dashboard-header h1 { margin: 0; font-size: 16px; font-weight: 600; }
.desktop-dashboard-header p { margin: 3px 0 0; color: var(--fg-4, #9ca3af); font-size: 12px; }
.desktop-dashboard-content { box-sizing: border-box; width: min(100%, 960px); margin: 0 auto; padding: 28px 24px 48px; }
.desktop-empty { height: 100%; display: grid; place-items: center; color: var(--fg-4, #9ca3af); font-size: 14px; }
.desktop-error { align-content: center; gap: 12px; }
.desktop-error button { justify-self: center; border: 1px solid var(--border, #e5e7eb); border-radius: 5px; background: var(--bg, #fff); color: var(--fg, #202124); padding: 6px 14px; cursor: pointer; }
.desktop-error button:hover { background: var(--bg-3, #f3f4f6); }
</style>
