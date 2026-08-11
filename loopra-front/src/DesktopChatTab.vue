<template>
  <main class="desktop-chat-tab" :data-theme="theme">
    <!-- 会话进行中的波动条：横跨两侧边栏之上 -->
    <div v-if="sessionActive" class="desktop-streaming-bar">
      <div class="desktop-streaming-bar-inner"></div>
    </div>
    <aside class="desktop-files-left" :class="{ collapsed: !leftPanelOpen }" aria-label="项目文件">
      <div class="desktop-files-head">
        <div class="desktop-files-title">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
            <path d="M3 6.5A2.5 2.5 0 0 1 5.5 4H10l2 2.5h6.5A2.5 2.5 0 0 1 21 9v8.5A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5z"/>
          </svg>
          <span>文件</span>
        </div>
        <div class="desktop-files-actions">
          <button class="desktop-files-action" type="button" title="刷新文件树" aria-label="刷新文件树" @click="fileRef?.refresh?.()">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
          </button>
          <button class="desktop-files-action" type="button" title="收起左侧栏" aria-label="收起左侧栏" @click="leftPanelOpen = false">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m15 18-6-6 6-6"/></svg>
          </button>
        </div>
      </div>
      <FilePanel v-if="leftPanelMounted" ref="fileRef" :workspace-hash="workspaceHash" @add-to-session="addFileToSession" />
    </aside>
    <div class="desktop-chat-area">
      <ChatView
        ref="chatRef"
        class="desktop-chat-view"
        hide-header
        :streaming-bar-hidden="true"
        :workspace-hash="workspaceHash"
        :session-name="sessionName"
        :initially-empty="newSession"
        :right-panel-open="rightPanelOpen"
        :workspaces="workspaces"
        @switch-workspace="switchWorkspace"
        @session-updated="refreshTabTitle"
        @session-active-change="sessionActive = $event"
        @welcome-change="onWelcomeChange"
        @manage-workspaces="requestHome"
        @manage-models="requestModelSettings"
      />
    </div>
    <!-- 终端：独立右侧面板（与右侧栏并排，可拖宽/收起） -->
    <TerminalView v-if="terminalMounted" vertical :open="showTerminal" :cwd="activeWorkspacePath" :theme="theme" @close="showTerminal = false" />
    <RightPanel
      v-if="rightPanelMounted"
      :open="rightPanelOpen"
      v-model="rightPanelTab"
      :show-files-tab="false"
      :workspace-hash="workspaceHash"
      :session-name="sessionName"
      :sessions="sessions"
      @close="rightPanelOpen = false"
      @add-to-session="addFileToSession"
    />
  </main>
</template>

<script setup>
import {message} from 'ant-design-vue'
import {computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useAppStore} from './stores/app'
import {configAPI, sessionsAPI} from './services/api'
import ChatView from './views/Chat.vue'

const FilePanel = defineAsyncComponent(() => import('./components/FilePanel.vue'))
const RightPanel = defineAsyncComponent(() => import('./components/RightPanel.vue'))
const TerminalView = defineAsyncComponent(() => import('./components/TerminalView.vue'))

const params = new URLSearchParams(window.location.search)
const sessionName = params.get('sessionName') || ''
const newSession = params.get('newSession') === '1'
const workspaceHash = ref(params.get('workspaceHash') || null)
const store = useAppStore()
const pageTheme = ref(params.get('theme') === 'dark' ? 'dark' : store.settings.theme)
const theme = computed(() => pageTheme.value)
const workspaces = ref([])
const sessions = ref([])
const chatRef = ref(null)
const fileRef = ref(null)
const rightPanelOpen = ref(false)
const rightPanelMounted = ref(false)
const leftPanelOpen = ref(false)
const leftPanelMounted = ref(false)
const showTerminal = ref(false)
const terminalMounted = ref(false)
const sessionActive = ref(false)
const rightPanelTab = ref('git')
// 终端初始工作目录 = 当前工作区路径（终端面板与当前会话绑定）
const activeWorkspacePath = computed(() => {
  const workspace = workspaces.value.find((item) => item.hash === workspaceHash.value)
  return workspace?.path || ''
})
const tabId = `${workspaceHash.value || ''}:${sessionName}`
let stopLeftPanelListener = null
let stopRightPanelListener = null
let stopTerminalListener = null
let stopThemeListener = null
let stopElementInspectionListener = null
let stopRefreshHistoryListener = null
let stopFocusComposerListener = null
let stopSendCommandListener = null

onMounted(() => {
  // 先注册主进程事件，避免初始化请求期间丢失聚焦或自动发送命令。
  stopLeftPanelListener = window.electronAPI?.events?.listen('desktop-chat-tab-toggle-left-panel', toggleLeftPanel)
  stopRightPanelListener = window.electronAPI?.events?.listen('desktop-chat-tab-toggle-right-panel', toggleRightPanel)
  stopTerminalListener = window.electronAPI?.events?.listen('desktop-chat-tab-toggle-terminal', toggleTerminal)
  stopThemeListener = window.electronAPI?.events?.listen('desktop-chat-tab-theme', (nextTheme) => {
    pageTheme.value = nextTheme === 'dark' ? 'dark' : 'gray'
    document.documentElement.setAttribute('data-theme', pageTheme.value)
  })
  stopElementInspectionListener = window.electronAPI?.events?.listen('desktop-chat-tab-element-inspection', addElementInspectionToSession)
  stopRefreshHistoryListener = window.electronAPI?.events?.listen('desktop-chat-tab-refresh-history', () => chatRef.value?.refreshHistory())
  stopFocusComposerListener = window.electronAPI?.events?.listen('desktop-chat-tab-focus-composer', () => {
    void chatRef.value?.focusComposer?.()
  })
  // 主窗口（DesktopShell）发来的命令（如「更新核心服务」由 Agent 在聊天框执行）
  stopSendCommandListener = window.electronAPI?.events?.listen('desktop-chat-tab-send-command', (command) => {
    if (command) void chatRef.value?.sendCommand?.(command)
  })
  // Agent 调用 bash_start 时自动展开右侧栏“命令”页签（仅当前 tab 响应）
  window.addEventListener('loopra:bash-start', onBashStart)
  window.electronAPI?.desktopChatTabs?.ready?.()
  document.documentElement.setAttribute('data-theme', pageTheme.value)
  void initializeTabContext()
})

async function initializeTabContext() {
  const tasks = [loadWorkspaces()]
  if (!newSession) {
    tasks.push(sessionsAPI.switchSession(sessionName, workspaceHash.value).catch((error) => {
      console.warn('[desktop-chat-tab] failed to synchronize session:', error)
    }))
  }
  await Promise.all(tasks)
}

async function loadWorkspaces() {
  try {
    const response = await configAPI.listWorkspaces()
    if (response.success) workspaces.value = response.data || []
  } catch (error) {
    console.error('[desktop-chat-tab] failed to load workspaces:', error)
  }
}

function toggleLeftPanel() {
  if (!leftPanelOpen.value) leftPanelMounted.value = true
  leftPanelOpen.value = !leftPanelOpen.value
}

function toggleRightPanel() {
  if (!rightPanelOpen.value) {
    rightPanelMounted.value = true
    if (sessions.value.length === 0) void loadSessions()
  }
  rightPanelOpen.value = !rightPanelOpen.value
}

// Agent 调用 bash_start 时自动展开右侧栏并切到“命令”页签（仅当前 tab 响应）
function onBashStart(event) {
  const detail = event?.detail || {}
  if (detail.workspaceHash && detail.workspaceHash !== workspaceHash.value) return
  if (detail.sessionName && detail.sessionName !== sessionName) return
  if (!rightPanelOpen.value) {
    rightPanelMounted.value = true
    if (sessions.value.length === 0) void loadSessions()
  }
  rightPanelOpen.value = true
  rightPanelTab.value = 'bash'
}

async function toggleTerminal() {
  if (showTerminal.value) {
    showTerminal.value = false
    return
  }
  if (!activeWorkspacePath.value) await loadWorkspaces()
  if (!activeWorkspacePath.value) {
    message.warning('项目路径尚未加载完成，请稍后重试')
    return
  }
  terminalMounted.value = true
  showTerminal.value = true
}

async function switchWorkspace(nextWorkspaceHash) {
  if (!nextWorkspaceHash || nextWorkspaceHash === workspaceHash.value) return
  const workspace = workspaces.value.find((item) => item.hash === nextWorkspaceHash)
  if (!workspace) {
    message.error('工作区不存在')
    return
  }
  try {
    const response = await configAPI.switchWorkspace(workspace.path)
    if (!response.success) throw new Error(response.message || '切换工作区失败')
    workspaceHash.value = nextWorkspaceHash
    await loadSessions()
    await refreshTabTitle()
  } catch (error) {
    console.error('[desktop-chat-tab] failed to switch workspace:', error)
    message.error('切换工作区失败：' + (error.message || '未知错误'))
  }
}

async function refreshTabTitle() {
  if (!workspaceHash.value || !sessionName) return
  try {
    const response = await sessionsAPI.list(workspaceHash.value)
    const session = response.success ? (response.data || []).find((item) => item.name === sessionName) : null
    const title = String(session?.title || '').trim()
    if (title) window.electronAPI?.desktopChatTabs?.reportTitle({ tabId, title })
  } catch (error) {
    console.warn('[desktop-chat-tab] failed to refresh session title:', error)
  }
}

async function loadSessions() {
  if (!workspaceHash.value) { sessions.value = []; return }
  try {
    const response = await sessionsAPI.list(workspaceHash.value)
    if (response.success) sessions.value = response.data || []
  } catch (error) {
    console.warn('[desktop-chat-tab] failed to load sessions:', error)
  }
}

async function addFileToSession(payload) {
  await chatRef.value?.appendFileSelection(payload)
}

// 欢迎页不展示左侧文件栏；进入会话保持当前状态（默认折叠，不覆盖用户手动开关）
function onWelcomeChange(active) {
  if (active) leftPanelOpen.value = false
}

async function addElementInspectionToSession(payload) {
  await nextTick()
  const attached = await chatRef.value?.appendElementInspection?.(payload)
  if (attached) await chatRef.value?.setDraft?.(payload?.message || '')
}

function requestHome() {
  window.electronAPI?.desktopChatTabs?.openHome()
}

function requestModelSettings() {
  window.electronAPI?.desktopChatTabs?.openModelChannels()
}

onBeforeUnmount(() => {
  stopLeftPanelListener?.()
  stopRightPanelListener?.()
  stopTerminalListener?.()
  stopThemeListener?.()
  stopElementInspectionListener?.()
  stopRefreshHistoryListener?.()
  stopFocusComposerListener?.()
  stopSendCommandListener?.()
  window.removeEventListener('loopra:bash-start', onBashStart)
})

// 工作区变化时自动上报，确保标签栏图标实时更新
watch(workspaceHash, (hash) => {
  if (hash) window.electronAPI?.desktopChatTabs?.reportWorkspace({ tabId, workspaceHash: hash })
}, { immediate: true })
</script>

<style scoped>
.desktop-chat-tab {
  width: 100vw;
  height: 100vh;
  display: flex;
  overflow: hidden;
  background: var(--bg);
  position: relative;
}

/* 会话进行中的波动条：绝对定位横跨两侧边栏之上 */
.desktop-streaming-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--bg-3, rgba(0,0,0,0.06));
  overflow: hidden;
  z-index: 30;
  pointer-events: none;
}

.desktop-streaming-bar-inner {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 40%;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  border-radius: 1px;
  will-change: transform;
  animation: desktop-streaming-slide 1.4s ease-in-out infinite;
}

@keyframes desktop-streaming-slide {
  0% { transform: translate3d(-100%, 0, 0); }
  100% { transform: translate3d(250%, 0, 0); }
}

.desktop-files-left {
  width: 300px;
  min-width: 240px;
  max-width: 34vw;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--bg-2, #f7f7f8);
  border-right: 1px solid var(--border);
  transition: width 0.2s, opacity 0.2s;
}

.desktop-files-left.collapsed {
  width: 0;
  min-width: 0;
  opacity: 0;
  border-right: none;
  pointer-events: none;
}

.desktop-files-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 46px;
  padding: 0 8px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
  background: var(--bg-3, #f1f1f3);
}

.desktop-files-title {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--fg);
  font-size: 13px;
  font-weight: 600;
}

.desktop-files-title svg {
  color: var(--fg-3);
}

.desktop-files-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.desktop-files-action {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--fg-3);
  cursor: pointer;
}

.desktop-files-action:hover {
  background: var(--bg-2);
  color: var(--fg);
}

.desktop-files-left :deep(.file-panel) {
  min-height: 0;
}

.desktop-chat-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.desktop-chat-view {
  flex: 1;
  min-height: 0;
  min-width: 0;
}
[data-theme="dark"] .desktop-files-left {
  background: var(--bg-2, #222327);
}
[data-theme="dark"] .desktop-files-head {
  background: var(--bg-3, #2a2b2f);
}
[data-theme="dark"] .desktop-files-action:hover {
  background: #36373d;
}
</style>
