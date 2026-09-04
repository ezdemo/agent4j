<template>
  <div class="desktop-shell" :data-theme="theme">
    <header class="desktop-titlebar">
      <div class="desktop-left-controls">
        <button
          ref="homeButton"
          class="icon-button"
          :class="{ active: isHomeActive }"
          type="button"
          title="会话首页"
          :aria-pressed="isHomeActive"
          aria-haspopup="menu"
          :aria-expanded="homeContextMenu.visible"
          @click="showHome"
          @contextmenu.prevent.stop="openHomeContextMenu"
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
          :aria-haspopup="'menu'"
          :aria-expanded="tabContextMenu.visible && tabContextMenu.tabId === tab.id"
          tabindex="0"
          :title="tab.title"
          @dragstart="startTabReorder($event, tab.id)"
          @dragover="dragOverTab($event, tab.id)"
          @drop="dropTab($event, tab.id)"
          @dragend="endTabReorder"
          @click="activateTab(tab.id)"
          @contextmenu.prevent.stop="openTabContextMenu($event, tab.id)"
          @mousedown.middle.prevent.stop="closeTab(tab.id)"
          @keydown.enter="activateTab(tab.id)"
          @keydown.space.prevent="activateTab(tab.id)"
        >
          <span v-if="workspaceNameOf(tab.workspaceHash)" class="desktop-tab-monogram" :class="badgeTone(workspaceNameOf(tab.workspaceHash))">{{ initial(workspaceNameOf(tab.workspaceHash)) }}</span>
          <span v-else class="desktop-tab-monogram desktop-tab-monogram-default" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>
          </span>
          <span class="desktop-tab-title">{{ tab.title }}</span>
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
          v-if="hasNewVersion"
          class="window-button update-check-button"
          :class="{ 'has-update': hasNewVersion }"
          type="button"
          :title="hasNewVersion ? `发现新版本 v${latestVersion}，点击打开更新` : (latestVersion ? `已是最新版本 v${latestVersion}，点击打开更新窗口` : '点击打开更新窗口')"
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
        <button v-if="activeTabId" class="window-button" type="button" title="终端" aria-label="终端" @click="toggleTerminal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="m7 9 3 3-3 3M13 15h4"/></svg>
        </button>
        <button class="window-button" type="button" title="引导" aria-label="引导" @click="openOnboarding">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M15 4V2M15 10V8M11.5 5.5H9.5M20.5 5.5H18.5M17.99 8.5 19.5 10M12.01 8.5 10.5 10"/><path d="m3 21 8-8"/></svg>
        </button>
        <button class="window-button" type="button" title="最小化" @click="minimize"><span class="minimize-mark" /></button>
        <button class="window-button" type="button" title="最大化" @click="maximize"><span class="maximize-mark" /></button>
        <button class="window-button close" type="button" title="关闭" @click="closeWindow"><span class="close-mark" /></button>
      </div>
    </header>

    <Teleport to="body">
      <div
        v-if="homeContextMenu.visible"
        class="desktop-shell-context-menu"
        role="menu"
        aria-label="首页菜单"
        :style="{ left: `${homeContextMenu.x}px`, top: `${homeContextMenu.y}px` }"
        @contextmenu.prevent
      >
        <button type="button" role="menuitem" @click="chooseHomeContextAction('open-requirement-board')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/></svg>
          需求池
        </button>
        <button type="button" role="menuitem" @click="chooseHomeContextAction('open-onboarding')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M15 4V2M15 10V8M11.5 5.5H9.5M20.5 5.5H18.5M17.99 8.5 19.5 10M12.01 8.5 10.5 10"/><path d="m3 21 8-8"/></svg>
          引导
        </button>
        <button type="button" role="menuitem" @click="chooseHomeContextAction('open-update')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
          更新
        </button>
        <button
          type="button"
          role="menuitem"
          :title="theme === 'dark' ? '浅色' : '暗色'"
          @click="chooseHomeContextAction('toggle-theme')"
        >
          <svg v-if="theme === 'dark'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20.2 14.1A8.5 8.5 0 0 1 9.9 3.8 8.5 8.5 0 1 0 20.2 14.1Z"/></svg>
          {{ theme === 'dark' ? '浅色' : '暗色' }}
        </button>
      </div>
      <div
        v-if="tabContextMenu.visible"
        class="desktop-tab-context-menu"
        role="menu"
        aria-label="会话标签菜单"
        :style="{ left: `${tabContextMenu.x}px`, top: `${tabContextMenu.y}px` }"
        @contextmenu.prevent
      >
        <button type="button" role="menuitem" @click="chooseTabContextAction('reload')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 1 0 2 5"/><path d="M20 4v7h-7"/></svg>
          刷新
        </button>
        <button type="button" role="menuitem" @click="chooseTabContextAction('close')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18"/></svg>
          关闭
        </button>
        <button type="button" role="menuitem" :disabled="!hasTabsToClose('left')" @click="chooseTabContextAction('close-left')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 5v14M9 8h10M9 12h7M9 16h10"/></svg>
          关闭左侧标签
        </button>
        <button type="button" role="menuitem" :disabled="!hasTabsToClose('right')" @click="chooseTabContextAction('close-right')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M19 5v14M5 8h10M8 12h7M5 16h10"/></svg>
          关闭右侧标签
        </button>
      </div>
    </Teleport>

    <main ref="host" class="desktop-view-host">
      <div v-if="startupError" class="desktop-empty desktop-error">
        <span>{{ startupError }}</span>
        <button type="button" @click="initializeWorkspace">重试</button>
      </div>
      <DesktopHome
        v-else-if="!activeTabId && !showSkills && !showSettings && !showModelChannels"
        :workspaces="workspaces"
        :active-workspace-hash="activeWorkspaceHash"
        :theme="theme"
        :refresh-key="homeRefreshKey"
        :refreshing="refreshingHome"
        @select-workspace="selectWorkspace"
        @new-session="createTab"
        @open-session="openSession"
        @open-skills="openSkills"
        @open-requirement-board="openRequirementBoard"
        @open-tools="openTools"
        @open-sub-agents="openSubAgents"
        @open-settings="openSettings"
        @toggle-theme="toggleTheme"
        @add-workspace="addWorkspaceFromFolder"
        @refresh="refreshHome"
        @delete-session="confirmDeleteSession"
        @delete-sessions="confirmDeleteSessions"
        @session-renamed="onSessionRenamed"
        @clear-workspace="confirmClearWorkspace"
        @clear-old-sessions="confirmClearOldSessions"
        @delete-workspace="confirmDeleteWorkspace"
        @delete-workspaces="confirmDeleteWorkspaces"
        @reorder-workspaces="reorderWorkspaces"
      />
      <SettingsView v-else-if="showSkills" class="desktop-settings" market-only />
      <ModelChannels v-else-if="showModelChannels" class="desktop-settings" :show-back="false" @saved="reloadAfterModelChannelsSaved" />
      <SettingsView v-else-if="showSettings" class="desktop-settings" :initial-tab="settingsTab" />
    </main>
  <ConfirmDialog />
  <ActionConfirmDialog
    :model-value="deleteConfirm.visible"
    :title="deleteConfirm.title"
    :message="deleteConfirm.message"
    :actions="deleteConfirmActions"
    @update:model-value="dismissDeleteConfirm"
    @action="handleDeleteConfirmAction"
  />
</div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import {message} from 'ant-design-vue'
import {useAppStore} from './stores/app'
import {configAPI, sessionsAPI, systemAPI} from './services/api'
import {RELEASE_LATEST_URL} from './utils/constants'
import {buildUpdatePrompt} from './utils/updateScripts'
import {platform} from './services/platform'
import DesktopHome from './DesktopHome.vue'
import SettingsView from './views/Settings.vue'
import ModelChannels from './ModelChannels.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import ActionConfirmDialog from './components/ActionConfirmDialog.vue'
import {hasConfiguredModelChannel} from './utils/modelChannels'
import {switchThemeWithReveal} from './utils/themeTransition'

const store = useAppStore()
const theme = computed(() => store.settings.theme)
const creating = ref(false)
const startupError = ref('')
const workspaces = ref([])
const activeWorkspaceHash = ref('')
const homeRefreshKey = ref(0)
const refreshingHome = ref(false)
const showSkills = ref(false)
const showSettings = ref(false)
const showModelChannels = ref(false)
const modelChannelsRequireReload = ref(false)
// 设置页打开的初始 tab（工具/子代理/数据面板已收进设置页左侧菜单）
const settingsTab = ref('general')
const tabs = ref([])
const activeTabId = ref('')
const isHomeActive = computed(() => !startupError.value
  && !activeTabId.value && !showSkills.value && !showSettings.value && !showModelChannels.value)
const tabsNav = ref(null)
const draggedTabId = ref('')
const dragOverTabId = ref('')
const host = ref(null)
const homeButton = ref(null)
const homeContextMenu = reactive({visible: false, x: 0, y: 0})
const tabContextMenu = reactive({visible: false, tabId: '', x: 0, y: 0})
const HOME_CONTEXT_MENU_WIDTH = 176
const HOME_CONTEXT_MENU_HEIGHT = 148
const TAB_CONTEXT_MENU_WIDTH = 188
const TAB_CONTEXT_MENU_HEIGHT = 146
let resizeObserver = null
let renderVersion = 0
let renderQueue = Promise.resolve()

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

// 点击更新按钮始终打开更新窗口，窗口内会自行检查版本
function onUpdateButtonClick() {
  void openUpdateWindow()
}

async function openUpdateWindow() {
  if (platform.isElectron) {
    try {
      await window.electronAPI?.updateWindow?.open()
    } catch (error) {
      console.warn('[desktop-shell] failed to open update window:', error)
      openReleasePage()
    }
  } else {
    openReleasePage()
  }
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
const isElectronRuntime = () => {
  const hasNativeMenuAPI = Boolean(window.electronAPI?.desktopHomeMenu || window.electronAPI?.desktopTabMenu)
  const isDesktopShellRoute = typeof window !== 'undefined'
    && new URLSearchParams(window.location.search).get('desktopShell') === '1'
  return platform.isElectron || hasNativeMenuAPI || isDesktopShellRoute
}

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
// 更新窗口发起的「更新核心服务」：新建会话并由 Agent 在聊天框执行更新命令
const stopChatUpdateListener = window.electronAPI?.events?.listen('chat-update-request', ({ source }) => {
  void runChatUpdate(source)
})

function renderActiveTab() {
  const version = ++renderVersion
  const task = renderQueue.then(() => renderActiveTabNow(version))
  renderQueue = task.catch(() => {})
  return task
}

async function renderActiveTabNow(version) {
  const current = tabs.value.find((tab) => tab.id === activeTabId.value)
  const bridge = nativeTabs()
  if (!bridge) return true
  if (!current) {
    try { await bridge.hide() } catch (error) { console.warn('[desktop-shell] failed to hide tabs:', error) }
    return true
  }
  await nextTick()
  if (!host.value) return true
  try {
    await bridge.create({
      id: current.id,
      sessionName: current.sessionName,
      workspaceHash: current.workspaceHash,
      theme: theme.value,
      newSession: current.newSession === true
    })
    if (!tabs.value.some((tab) => tab.id === current.id)) {
      try { await bridge.close(current.id) } catch (cleanupError) { console.warn('[desktop-shell] failed to clean up closed tab:', cleanupError) }
      return false
    }
    if (version !== renderVersion) return false
    const bounds = host.value.getBoundingClientRect()
    await bridge.show(current.id, {
      x: Math.round(bounds.left), y: Math.round(bounds.top), width: Math.round(bounds.width), height: Math.round(bounds.height)
    })
    return true
  } catch (error) {
    if (!tabs.value.some((tab) => tab.id === current.id)) {
      try { await bridge.close(current.id) } catch (cleanupError) { console.warn('[desktop-shell] failed to clean up closed tab:', cleanupError) }
      return false
    }
    console.error('[desktop-shell] failed to show tab:', error)
    tabs.value = tabs.value.filter((tab) => tab.id !== current.id)
    try { await bridge.close(current.id) } catch (cleanupError) { console.warn('[desktop-shell] failed to clean up failed tab:', cleanupError) }
    if (activeTabId.value === current.id) {
      activeTabId.value = ''
      try { await bridge.hide() } catch (hideError) { console.warn('[desktop-shell] failed to hide tabs after load error:', hideError) }
    }
    message.error('打开会话失败：' + (error.message || '未知错误'))
    return false
  }
}

async function createTab() {
  if (creating.value) return
  const targetHash = activeWorkspaceHash.value || (workspaces.value[0] && workspaces.value[0].hash)
  if (!targetHash) {
    startupError.value = '未找到可用项目，请先在网页版添加项目。'
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
    tabs.value = [...tabs.value, { id, sessionName, workspaceHash, title: tabTitle(sessionName), newSession: true }]
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

// 更新窗口「更新核心服务」：新建会话并由 Agent 在聊天框执行更新命令（优先于在线安装）
async function runChatUpdate(source) {
  if (creating.value) return
  const targetHash = activeWorkspaceHash.value || (workspaces.value[0] && workspaces.value[0].hash)
  if (!targetHash) {
    message.warning('未找到可用项目，请先添加项目')
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
    tabs.value = [...tabs.value, { id, sessionName, workspaceHash, title: tabTitle(sessionName), newSession: true }]
    activeTabId.value = id
    startupError.value = ''
    if (!await renderActiveTab()) return
    // 发送更新命令（主进程会在标签加载完成后投递给聊天框）
    const delivered = await nativeTabs()?.sendCommand(id, buildUpdatePrompt(source, true))
    if (!delivered) throw new Error('更新命令未能投递到会话')
    message.success('已新建更新会话，正在聊天框中执行更新…')
  } catch (error) {
    message.error('新建更新会话失败：' + (error.message || '未知错误'))
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
    throw new Error(workspacesResult.message || '加载项目失败')
  }

  workspaces.value = workspacesResult.data || []
  if (workspaces.value.length === 0) {
    throw new Error('未找到可用项目，请先在网页版添加项目。')
  }

  const currentPath = currentWorkspaceResult.success
    ? (currentWorkspaceResult.data?.workspace || currentWorkspaceResult.data)
    : ''
  const selectedWorkspace = workspaces.value.find((item) => item.path === currentPath) || workspaces.value[0]
  const switchResult = await configAPI.switchWorkspace(selectedWorkspace.path)
  if (!switchResult.success) {
    throw new Error(switchResult.message || '切换默认项目失败')
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
    startupError.value = error.message || '初始化默认项目失败'
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
  if (!workspace) throw new Error('项目不存在')
  if (workspaceHash === activeWorkspaceHash.value) return
  const response = await configAPI.switchWorkspace(workspace.path)
  if (!response.success) throw new Error(response.message || '切换项目失败')
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

function setContextMenuPosition(menu, event, rect, width, height) {
  const hasPointerPosition = Number.isFinite(event.clientX) && Number.isFinite(event.clientY)
    && (event.clientX !== 0 || event.clientY !== 0)
  const x = hasPointerPosition ? event.clientX : (rect?.left || 0)
  const y = hasPointerPosition ? event.clientY : (rect?.bottom || 0)
  const maxX = Math.max(8, window.innerWidth - width - 8)
  const maxY = Math.max(8, window.innerHeight - height - 8)
  menu.x = Math.max(8, Math.min(x, maxX))
  menu.y = Math.max(8, Math.min(y, maxY))
}

async function openHomeContextMenu(event = {}) {
  const nativeMenu = window.electronAPI?.desktopHomeMenu?.open
  if (isElectronRuntime()) {
    if (!nativeMenu) {
      console.warn('[desktop-shell] native home menu API is unavailable')
      return
    }
    closeContextMenus()
    try {
      const action = await nativeMenu(theme.value)
      if (action) chooseHomeContextAction(action)
    } catch (error) {
      console.warn('[desktop-shell] failed to open native home menu:', error)
    }
    return
  }

  setContextMenuPosition(homeContextMenu, event, homeButton.value?.getBoundingClientRect(), HOME_CONTEXT_MENU_WIDTH, HOME_CONTEXT_MENU_HEIGHT)
  closeTabContextMenu()
  homeContextMenu.visible = true
}

async function openTabContextMenu(event, id) {
  const index = tabs.value.findIndex((tab) => tab.id === id)
  if (index < 0) return
  const nativeMenu = window.electronAPI?.desktopTabMenu?.open
  if (isElectronRuntime()) {
    if (!nativeMenu) {
      console.warn('[desktop-shell] native tab menu API is unavailable')
      return
    }
    closeContextMenus()
    try {
      const action = await nativeMenu({
        tabId: id,
        index,
        tabCount: tabs.value.length,
        theme: theme.value
      })
      if (action) chooseTabContextAction(action, id)
    } catch (error) {
      console.warn('[desktop-shell] failed to open native tab menu:', error)
    }
    return
  }

  setContextMenuPosition(tabContextMenu, event, event.currentTarget?.getBoundingClientRect(), TAB_CONTEXT_MENU_WIDTH, TAB_CONTEXT_MENU_HEIGHT)
  closeHomeContextMenu()
  tabContextMenu.tabId = id
  tabContextMenu.visible = true
}

function closeHomeContextMenu() {
  homeContextMenu.visible = false
}

function closeTabContextMenu() {
  tabContextMenu.visible = false
  tabContextMenu.tabId = ''
}

function closeContextMenus() {
  closeHomeContextMenu()
  closeTabContextMenu()
}

function chooseHomeContextAction(action) {
  closeContextMenus()
  if (action === 'open-requirement-board') openRequirementBoard()
  else if (action === 'open-onboarding') void openOnboarding()
  else if (action === 'open-update') void openUpdateWindow()
  else if (action === 'toggle-theme') toggleTheme()
}

function hasTabsToClose(side) {
  const index = tabs.value.findIndex((tab) => tab.id === tabContextMenu.tabId)
  return side === 'left' ? index > 0 : index >= 0 && index < tabs.value.length - 1
}

function chooseTabContextAction(action, id = tabContextMenu.tabId) {
  closeContextMenus()
  if (!id) return
  if (action === 'reload') void reloadTab(id)
  else if (action === 'close') void closeTab(id)
  else if (action === 'close-left') void closeTabsToSide(id, 'left')
  else if (action === 'close-right') void closeTabsToSide(id, 'right')
}

function onWindowClick() {
  closeContextMenus()
}

function onWindowKeydown(event) {
  if (event.key === 'Escape') closeContextMenus()
}

async function showHome() {
  closeContextMenus()
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

function openRequirementBoard() {
  // 桌面端：打开独立 BrowserWindow；Web 端：新标签页打开看板
  if (window.electronAPI?.requirementBoardWindow?.open) {
    window.electronAPI.requirementBoardWindow.open().catch((error) => {
      message.error('打开需求池失败：' + (error.message || '未知错误'))
    })
  } else {
    window.open(`${window.location.pathname}?requirementBoard=1`, '_blank')
  }
}

async function openTools() {
  await openSettings('tools')
}

async function openSubAgents() {
  await openSettings('sub-agents')
}

async function openSettings(tab = 'general') {
  settingsTab.value = tab
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

// 终端面板：转发给当前会话 tab 控制（与会话绑定、收起不销毁）
async function toggleTerminal() {
  if (!activeTabId.value) return
  try {
    await nativeTabs()?.toggleTerminal(activeTabId.value)
  } catch (error) {
    message.error('切换终端失败：' + (error.message || '未知错误'))
  }
}

function hideStandaloneViews() {
  closeContextMenus()
  showSkills.value = false
  showSettings.value = false
  showModelChannels.value = false
}

function toggleTheme() {
  // 中心扩散动画：动画完成后再真正切换主题
  switchThemeWithReveal(theme.value === 'dark' ? 'gray' : 'dark', (v) => { store.settings.theme = v })
}

async function openElementInspector() {
  try {
    await window.electronAPI?.elementInspectorWindow?.open()
  } catch (error) {
    message.error('打开元素检查失败：' + (error.message || '未知错误'))
  }
}

async function openOnboarding() {
  try {
    await window.electronAPI?.onboarding?.open()
  } catch (error) {
    message.error('打开引导失败：' + (error.message || '未知错误'))
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

async function closeTabsToSide(id, side) {
  const index = tabs.value.findIndex((tab) => tab.id === id)
  if (index < 0) return
  const removedTabs = tabs.value.filter((_, tabIndex) => side === 'left' ? tabIndex < index : tabIndex > index)
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
  const activeWasRemoved = removedIds.has(activeTabId.value)
  if (activeWasRemoved) activeTabId.value = id
  if (activeWasRemoved) await renderActiveTab()
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

// 删除/清空确认对话框（系统统一 ActionConfirmDialog）
const deleteConfirm = ref({ visible: false, kind: '', title: '', message: '', payload: null })
const deleteConfirmActions = computed(() => {
  const okLabel = deleteConfirm.value.kind === 'deleteWorkspace' || deleteConfirm.value.kind === 'deleteWorkspaces' ? '删除项目'
    : deleteConfirm.value.kind === 'clearWorkspace' || deleteConfirm.value.kind === 'clearOldSessions' ? '清空'
    : '删除'
  return [
    { key: 'cancel', label: '取消' },
    { key: 'confirm', label: okLabel, variant: 'danger' }
  ]
})
const openDeleteConfirm = (kind, title, message, payload) => {
  deleteConfirm.value = { visible: true, kind, title, message, payload }
}
const dismissDeleteConfirm = () => {
  deleteConfirm.value.visible = false
  deleteConfirm.value.payload = null
}
const handleDeleteConfirmAction = (action) => {
  if (action !== 'confirm') return dismissDeleteConfirm()
  const { kind, payload } = deleteConfirm.value
  dismissDeleteConfirm()
  if (kind === 'session') void performDeleteSession(payload)
  else if (kind === 'sessions') void performDeleteSessions(payload)
  else if (kind === 'clearWorkspace') void performClearWorkspace(payload)
  else if (kind === 'clearOldSessions') void performClearOldSessions(payload)
  else if (kind === 'deleteWorkspace') void performDeleteWorkspace(payload)
  else if (kind === 'deleteWorkspaces') void performDeleteWorkspaces(payload)
}

function onSessionRenamed({ workspaceHash, sessionName, title }) {
  if (!sessionName || !title) return
  // 同步已打开标签页的标题，保持与列表一致
  tabs.value = tabs.value.map((tab) =>
    tab.sessionName === sessionName && tab.workspaceHash === workspaceHash ? { ...tab, title } : tab
  )
}

function confirmDeleteSession(session) {
  const title = session?.title || session?.name || '此会话'
  openDeleteConfirm('session', '删除会话？', `“${title}”将被永久删除，无法恢复。`, session)
}

async function performDeleteSession(session) {
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

function confirmDeleteSessions(sessions) {
  const list = sessions || []
  const names = list.map((session) => `“${session.title || session.name}”`)
  const brief = names.length > 3 ? `选中的 ${names.length} 个会话（${names.slice(0, 3).join('、')} 等）` : names.join('、')
  openDeleteConfirm('sessions', '删除会话？', `${brief}将被永久删除，无法恢复。`, list)
}

async function performDeleteSessions(sessions) {
  const deleted = []
  const failed = []
  for (const session of sessions || []) {
    try {
      const response = await sessionsAPI.deleteSession(session.name, session.workspaceHash)
      if (response.success) deleted.push(session)
      else failed.push(session.title || session.name)
    } catch (error) {
      console.warn('[desktop-shell] failed to delete session:', session.name, error)
      failed.push(session.title || session.name)
    }
  }
  if (deleted.length) {
    // 关闭仍打开着但已被删除的会话标签
    await Promise.all(deleted.map(async (session) => {
      try { await closeTab(tabId(session.workspaceHash, session.name)) } catch (error) { console.warn('[desktop-shell] failed to close deleted session tab:', error) }
    }))
    homeRefreshKey.value++
  }
  if (deleted.length && !failed.length) {
    message.success(`已删除 ${deleted.length} 个会话`)
  } else if (deleted.length) {
    message.warning(`已删除 ${deleted.length} 个会话，${failed.length} 个失败：${failed.join('、')}`)
  } else {
    message.error(`删除失败：${failed.join('、')}`)
  }
}

function confirmClearWorkspace(workspace) {
  openDeleteConfirm('clearWorkspace', '清空项目会话？', `“${workspace.name}”中的全部会话将被永久删除，无法恢复。`, workspace)
}

async function performClearWorkspace(workspace) {
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

function confirmClearOldSessions(workspace) {
  openDeleteConfirm('clearOldSessions', '清空三天前的会话？', `“${workspace.name}”中超过 3 天未活动的会话将被永久删除，无法恢复。`, workspace)
}

async function performClearOldSessions(workspace) {
  try {
    const before = Date.now() - 3 * 24 * 60 * 60 * 1000
    const response = await sessionsAPI.clearBefore(workspace.hash, before)
    if (!response.success) throw new Error(response.message || '清理会话失败')
    const deletedNames = new Set(response.data?.sessionNames || [])
    // 关闭仍打开着但已被删除的会话标签
    if (deletedNames.size) {
      const removedTabs = tabs.value.filter((tab) => tab.workspaceHash === workspace.hash && deletedNames.has(tab.sessionName))
      await Promise.all(removedTabs.map(async (tab) => {
        try { await nativeTabs()?.close(tab.id) } catch (error) { console.warn('[desktop-shell] failed to close tab:', error) }
      }))
      const removedIds = new Set(removedTabs.map((tab) => tab.id))
      tabs.value = tabs.value.filter((tab) => !removedIds.has(tab.id))
      if (removedIds.has(activeTabId.value)) activeTabId.value = tabs.value[0]?.id || ''
      await renderActiveTab()
    }
    homeRefreshKey.value++
    message.success(deletedNames.size ? `已清理 ${deletedNames.size} 个三天前的会话` : '没有需要清理的会话')
  } catch (error) {
    message.error('清理会话失败：' + (error.message || '未知错误'))
  }
}

function confirmDeleteWorkspace(workspace) {
  openDeleteConfirm('deleteWorkspace', '删除项目？', `“${workspace.name}”将从项目列表移除；项目文件不会被删除。`, workspace)
}

async function performDeleteWorkspace(workspace) {
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

function confirmDeleteWorkspaces(workspaces) {
  const list = workspaces || []
  const names = list.map((workspace) => `“${workspace.name}”`)
  const brief = names.length > 3 ? `选中的 ${names.length} 个项目（${names.slice(0, 3).join('、')} 等）` : names.join('、')
  openDeleteConfirm('deleteWorkspaces', '删除项目？', `${brief}将从项目列表移除；项目文件不会被删除。`, list)
}

async function performDeleteWorkspaces(workspaceList) {
  const deleted = []
  const failed = []
  for (const workspace of workspaceList || []) {
    try {
      const response = await configAPI.deleteWorkspace(workspace.hash)
      if (response.success) deleted.push(workspace)
      else failed.push(workspace.name)
    } catch (error) {
      console.warn('[desktop-shell] failed to delete workspace:', workspace.hash, error)
      failed.push(workspace.name)
    }
  }
  if (deleted.length) {
    await Promise.all(deleted.map((workspace) => closeWorkspaceTabs(workspace.hash)))
    const removed = new Set(deleted.map((workspace) => workspace.hash))
    workspaces.value = workspaces.value.filter((workspace) => !removed.has(workspace.hash))
    if (deleted.some((workspace) => workspace.hash === activeWorkspaceHash.value)) {
      activeWorkspaceHash.value = ''
      if (workspaces.value[0]) await selectWorkspace(workspaces.value[0].hash)
    }
    homeRefreshKey.value++
  }
  if (deleted.length && !failed.length) {
    message.success(`已删除 ${deleted.length} 个项目`)
  } else if (deleted.length) {
    message.warning(`已删除 ${deleted.length} 个项目，${failed.length} 个失败：${failed.join('、')}`)
  } else {
    message.error(`删除失败：${failed.join('、')}`)
  }
}

onMounted(() => {
  // 服务已由启动窗口（SplashScreen）完成检测/安装/启动，主窗口直接初始化
  resizeObserver = new ResizeObserver(() => { void renderActiveTab() })
  if (host.value) resizeObserver.observe(host.value)
  // 启动后立即检查更新，并开启定时检查
  void checkForUpdates()
  updateCheckTimer = setInterval(() => { void checkForUpdates() }, UPDATE_CHECK_INTERVAL)
  window.addEventListener('click', onWindowClick)
  window.addEventListener('keydown', onWindowKeydown)
  void (async () => {
    if (await redirectToModelChannelsWhenUnconfigured()) return
    await initializeWorkspace()
  })()
  // 首次运行（未完成过引导）自动打开引导窗口；失败静默，不阻塞主界面
  if (platform.isElectron && !localStorage.getItem('loopra-onboarding-done')) {
    setTimeout(() => { void openOnboarding() }, 1200)
  }
})

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

async function minimize() { await platform.implementation.window.minimize() }
async function maximize() { await platform.implementation.window.maximize() }
async function closeWindow() { await platform.implementation.window.close() }

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  if (updateCheckTimer) {
    clearInterval(updateCheckTimer)
    updateCheckTimer = null
  }
  window.removeEventListener('click', onWindowClick)
  window.removeEventListener('keydown', onWindowKeydown)
  stopTitleListener?.()
  stopWorkspaceListener?.()
  stopOpenHomeListener?.()
  stopOpenSettingsListener?.()
  stopChatUpdateListener?.()
  void nativeTabs()?.hide()
})
</script>

<style scoped>
.desktop-shell { width: 100vw; height: 100vh; display: flex; flex-direction: column; overflow: hidden; background: var(--bg, #fbfbfc); color: var(--fg, #27272a); }
.desktop-titlebar { position: relative; height: 44px; min-height: 44px; display: flex; align-items: center; background: var(--bg, #fbfbfc); -webkit-app-region: drag; user-select: none; }
.desktop-titlebar::after { position: absolute; right: 0; bottom: 0; left: 0; height: 1px; background: var(--border, #eeeeF0); content: ''; pointer-events: none; }
.desktop-left-controls { display: flex; align-items: center; gap: 4px; padding: 0 8px 0 32px; flex: 0 0 auto; }
.icon-button, .desktop-tab, .desktop-tab-add, .window-button { -webkit-app-region: no-drag; border: 0; background: transparent; color: var(--fg-3, #71717a); }
.icon-button { width: 32px; height: 32px; padding: 6px; border-radius: 8px; transition: background-color var(--t), color var(--t); }
.icon-button svg, .desktop-tab svg, .desktop-tab-add svg { width: 18px; height: 18px; }
.icon-button:hover, .icon-button.active, .desktop-tab-add:hover, .window-button:hover { background: var(--bg-hover, #f6f6f7); color: var(--fg, #27272a); }
.desktop-tabs { height: 100%; display: flex; align-items: center; gap: 4px; min-width: 80px; flex: 1; overflow-x: auto; padding: 0 18px 0 8px; scrollbar-width: none; }
.desktop-tab.dragging { opacity: 0.55; }
.desktop-tab.drag-over { background: var(--bg-hover, #f6f6f7); box-shadow: inset 0 0 0 1px var(--border, #e8e8eb); }
.desktop-tabs::-webkit-scrollbar { display: none; }
.desktop-tab { display: inline-flex; align-items: center; gap: 7px; height: 30px; padding: 0 10px; border-radius: 8px; cursor: pointer; flex: 0 1 16vw; min-width: 96px; max-width: 230px; text-align: left; container-type: inline-size; transition: background-color var(--t), color var(--t); }
.desktop-tab:hover { background: var(--bg-hover, #f6f6f7); color: var(--fg, #27272a); }
.desktop-tab.active { background: var(--bg-active, #f1f1f3); color: var(--fg, #27272a); }
.desktop-tab.active .desktop-tab-title { font-weight: 500; }
.desktop-tab-title { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; flex: 1 1 auto; min-width: 0; font-size: 14px; font-weight: 400; }
.desktop-tab-actions { display: flex; align-items: center; gap: 2px; flex: 0 0 auto; }
.desktop-tab-reload, .desktop-tab-close { display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; border: 0; border-radius: 4px; background: transparent; color: inherit; cursor: pointer; }
.desktop-tab-reload { display: none; }
.desktop-tab:hover .desktop-tab-reload, .desktop-tab:focus-within .desktop-tab-reload { display: inline-flex; }
.desktop-tab-reload svg { width: 12px; height: 12px; }
.desktop-tab-close svg { width: 14px; height: 14px; }
.desktop-tab-reload:hover, .desktop-tab-close:hover { background: var(--bg-hover, #f6f6f7); }
.desktop-tab-monogram { width: 16px; height: 16px; display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; border-radius: 4px; color: #fff; font-size: 10px; font-weight: 700; line-height: 1; text-shadow: 0 1px rgba(0, 0, 0, 0.25); box-shadow: inset 0 1px rgba(255, 255, 255, 0.25), 0 1px 1px rgba(0, 0, 0, 0.16); }
.desktop-tab-monogram.tone-0 { background: linear-gradient(135deg, #8b95a3, #5e6878); }
.desktop-tab-monogram.tone-1 { background: linear-gradient(135deg, #3dd0e8, #18b4d0); }
.desktop-tab-monogram.tone-2 { background: linear-gradient(135deg, #ffa86b, #ff7a3d); }
.desktop-tab-monogram.tone-3 { background: linear-gradient(135deg, #9aacf5, #6d80e8); }
.desktop-tab-monogram.tone-4 { background: linear-gradient(135deg, #6dd49d, #3eb878); }
.desktop-tab-monogram.tone-5 { background: linear-gradient(135deg, #f87fb5, #e85a9c); }
.desktop-tab-monogram.tone-6 { background: linear-gradient(135deg, #fcd34d, #f5b800); }
.desktop-tab-monogram.tone-7 { background: linear-gradient(135deg, #4dd9a6, #20c084); }
.desktop-tab-monogram-default { background: var(--bg-3, #f3f4f6); color: var(--fg-3, #9ca3af); box-shadow: none; text-shadow: none; }
.desktop-tab-monogram-default svg { width: 12px; height: 12px; }

/* 会话标签分级收缩：会话变多时标签自动变窄，最窄仅保留 图标 + 两字标题 + 关闭按钮 */
/* 注意：@container 内不能修改容器自身影响 content-box 的属性（padding/gap 会被 Chromium 忽略），只能作用于子元素 */
@container (max-width: 149px) {
  .desktop-tab-reload { display: none !important; }
}
@container (max-width: 99px) {
  .desktop-tab-title { flex: 0 1 auto; max-width: 2em; }
}
.desktop-tab-add { display: inline-flex; width: 32px; height: 32px; align-items: center; justify-content: center; border-radius: 8px; flex: 0 0 auto; cursor: pointer; transition: background-color var(--t), color var(--t); }
.desktop-window-controls { height: 100%; display: flex; align-items: center; padding-right: 14px; flex: 0 0 auto; -webkit-app-region: no-drag; }
.window-button { width: 44px; height: 30px; display: inline-flex; align-items: center; justify-content: center; border-radius: 8px; transition: background-color var(--t), color var(--t); }
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
.desktop-empty { height: 100%; display: grid; place-items: center; color: var(--fg-4, #9ca3af); font-size: 14px; }
.desktop-error { align-content: center; gap: 12px; }
.desktop-error button { justify-self: center; border: 1px solid var(--border, #e5e7eb); border-radius: 5px; background: var(--bg, #fff); color: var(--fg, #202124); padding: 6px 14px; cursor: pointer; }
.desktop-error button:hover { background: var(--bg-3, #f3f4f6); }
.desktop-shell-context-menu, .desktop-tab-context-menu { box-sizing: border-box; position: fixed; z-index: 1000; padding: 4px; border: 1px solid var(--border, #e5e7eb); border-radius: 6px; background: var(--bg, #fff); box-shadow: var(--shadow-lg, 0 10px 28px rgba(0, 0, 0, 0.16)); }
.desktop-shell-context-menu { width: 176px; }
.desktop-tab-context-menu { width: 188px; }
.desktop-shell-context-menu button, .desktop-tab-context-menu button { width: 100%; height: 34px; display: flex; align-items: center; gap: 8px; padding: 0 8px; border: 0; border-radius: 4px; background: transparent; color: var(--fg-2, #525866); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.desktop-shell-context-menu button:hover, .desktop-shell-context-menu button:focus-visible, .desktop-tab-context-menu button:hover, .desktop-tab-context-menu button:focus-visible { color: var(--fg, #202124); background: var(--bg-3, #f2f3f5); outline: 0; }
.desktop-tab-context-menu button:disabled { opacity: 0.45; cursor: default; }
.desktop-shell-context-menu svg, .desktop-tab-context-menu svg { width: 15px; height: 15px; flex: 0 0 auto; }
</style>
