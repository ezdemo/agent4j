<template>
  <main class="desktop-chat-tab" :data-theme="theme">
    <!-- 会话进行中的波动条：横跨两侧边栏之上 -->
    <div v-if="sessionActive" class="desktop-streaming-bar">
      <div class="desktop-streaming-bar-inner"></div>
    </div>
    <aside class="desktop-files-left" :class="{ collapsed: !leftPanelOpen }" aria-label="项目文件">
      <FileExplorer
        v-if="leftPanelMounted"
        ref="fileExplorerRef"
        :root-path="activeWorkspacePath"
        :workspace-hash="workspaceHash"
        @add-to-session="addFileToSession"
        @open-file="openFileTab"
        @file-deleted="onFileDeleted"
        @file-renamed="onFileRenamed"
      />
    </aside>
    <div class="desktop-chat-area">
      <!-- 编辑器标签栏：Chat 固定第一且不可关闭，文件标签可关闭 -->
      <EditorTabs v-if="fileTabs.length > 0" :tabs="editorTabs" :active-id="activeTabId" @set-active="setActiveTab" @close="closeFileTab" />
      <!-- 内容区：Chat 保活 + 单个 Monaco 实例复用多个文件 model -->
      <div v-show="activeTabId === CHAT_TAB_ID" class="editor-pane">
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
      <div v-if="fileTabs.length > 0" v-show="activeTabId !== CHAT_TAB_ID" class="editor-pane">
        <FileEditor
          ref="fileEditorRef"
          :active-file="activeFileTab"
          :theme="theme"
          @saved="onFileSaved"
          @dirty-change="onFileDirtyChange"
        />
      </div>
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
    <ActionConfirmDialog
      :model-value="closeConfirm.visible"
      title="关闭未保存文件"
      :message="`“${closeConfirm.name}”包含未保存的修改。关闭后，这些修改将丢失。`"
      :actions="closeConfirmActions"
      @update:model-value="dismissCloseConfirm"
      @action="handleCloseConfirmAction"
    />
  </main>
</template>

<script setup>
import {message} from 'ant-design-vue'
import {computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useAppStore} from './stores/app'
import {configAPI, sessionsAPI} from './services/api'
import ChatView from './views/Chat.vue'
import EditorTabs from './components/EditorTabs.vue'
import FileEditor from './components/FileEditor.vue'
import ActionConfirmDialog from './components/ActionConfirmDialog.vue'
import {fileIconFor} from './utils/fileIcons'

const RightPanel = defineAsyncComponent(() => import('./components/RightPanel.vue'))
const TerminalView = defineAsyncComponent(() => import('./components/TerminalView.vue'))
const FileExplorer = defineAsyncComponent(() => import('./components/FileExplorer.vue'))

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
const rightPanelOpen = ref(false)
const rightPanelMounted = ref(false)
const leftPanelOpen = ref(false)
const leftPanelMounted = ref(false)
const showTerminal = ref(false)
const terminalMounted = ref(false)
const sessionActive = ref(false)
const rightPanelTab = ref('git')
// 编辑器标签：Chat 固定第一且不可关闭，文件标签可关闭
const CHAT_TAB_ID = 'chat'
const fileExplorerRef = ref(null)
const fileEditorRef = ref(null)
const fileTabs = ref([]) // [{ id, path, name, dirty }]
const activeTabId = ref(CHAT_TAB_ID)
const closeConfirm = ref({visible: false, tabId: '', name: ''})
const closeConfirmActions = [
  {key: 'cancel', label: '取消'},
  {key: 'close', label: '关闭文件', variant: 'danger'}
]
const activeFileTab = computed(() => fileTabs.value.find((tab) => tab.id === activeTabId.value) || null)
let fileTabSeq = 0
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

// ── 编辑器标签（VS Code 风格：Chat 固定 + 文件可关闭） ──
const editorTabs = computed(() => [
  { id: CHAT_TAB_ID, label: '对话', icon: 'codicon-comment-discussion', closable: false, title: '对话（固定标签）' },
  ...fileTabs.value.map((tab) => ({
    id: tab.id,
    label: tab.name,
    title: tab.path,
    fileIcon: fileIconFor(tab.name),
    dirty: tab.dirty
  }))
])

function fileBaseName(path) {
  const parts = String(path || '').replace(/\\/g, '/').split('/')
  return parts.pop() || String(path || '')
}

function openFileTab(path) {
  if (!path) return
  const existing = fileTabs.value.find((tab) => tab.path === path)
  if (existing) {
    activeTabId.value = existing.id
    return
  }
  const tab = { id: `file-${++fileTabSeq}`, path, name: fileBaseName(path), dirty: false }
  fileTabs.value.push(tab)
  activeTabId.value = tab.id
}

function setActiveTab(id) {
  activeTabId.value = id
}

function closeFileTab(id, force = false) {
  const index = fileTabs.value.findIndex((tab) => tab.id === id)
  if (index < 0) return
  const tab = fileTabs.value[index]
  if (!force && tab.dirty) {
    closeConfirm.value = {visible: true, tabId: tab.id, name: tab.name}
    return
  }
  removeFileTab(index)
}

function dismissCloseConfirm() {
  closeConfirm.value = {visible: false, tabId: '', name: ''}
}

function handleCloseConfirmAction(action) {
  if (action === 'close') {
    const index = fileTabs.value.findIndex((tab) => tab.id === closeConfirm.value.tabId)
    if (index >= 0) removeFileTab(index)
  }
  dismissCloseConfirm()
}

function removeFileTab(index) {
  const tab = fileTabs.value[index]
  const id = tab?.id
  if (tab) fileEditorRef.value?.closeFile?.(tab.path)
  fileTabs.value.splice(index, 1)
  // 关闭的是当前标签 → 激活相邻标签，否则回 Chat
  if (activeTabId.value === id) {
    activeTabId.value = fileTabs.value[index] ? fileTabs.value[index].id : CHAT_TAB_ID
  }
}

function onFileDeleted(path) {
  const index = fileTabs.value.findIndex((tab) => tab.path === path)
  if (index >= 0) removeFileTab(index)
}

function onFileRenamed(oldPath, newPath) {
  const tab = fileTabs.value.find((item) => item.path === oldPath)
  if (tab) {
    fileEditorRef.value?.renameFile?.(oldPath, newPath)
    tab.path = newPath
    tab.name = fileBaseName(newPath)
  }
}

function onFileDirtyChange(path, dirty) {
  const tab = fileTabs.value.find((item) => item.path === path)
  if (tab) tab.dirty = dirty
}

function onFileSaved() {
  // 保存后刷新文件树 Git 装饰
  fileExplorerRef.value?.refresh?.()
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

// 工作区变化时自动上报，确保标签栏图标实时更新；同时清空已打开的文件标签
watch(workspaceHash, (hash) => {
  if (hash) window.electronAPI?.desktopChatTabs?.reportWorkspace({ tabId, workspaceHash: hash })
  fileEditorRef.value?.closeAll?.()
  fileTabs.value = []
  activeTabId.value = CHAT_TAB_ID
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

.desktop-files-left :deep(.file-explorer) {
  min-height: 0;
}

.desktop-chat-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 内容区面板（Chat / 文件编辑器）：v-show 切换时保持 flex 布局 */
.editor-pane {
  flex: 1;
  min-height: 0;
  min-width: 0;
  display: flex;
}

.desktop-chat-view {
  flex: 1;
  min-height: 0;
  min-width: 0;
}
[data-theme="dark"] .desktop-files-left {
  background: var(--bg-2, #222327);
}
</style>
