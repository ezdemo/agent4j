<template>
  <main class="desktop-chat-tab" :data-theme="theme">
    <ChatView
      ref="chatRef"
      class="desktop-chat-view"
      hide-header
      :workspace-hash="workspaceHash"
      :session-name="sessionName"
      :right-panel-open="rightPanelOpen"
      :workspaces="workspaces"
      @switch-workspace="switchWorkspace"
      @session-updated="refreshTabTitle"
      @manage-workspaces="requestHome"
      @manage-models="requestModelSettings"
    />
    <RightPanel
      :open="rightPanelOpen"
      v-model="rightPanelTab"
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
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useAppStore} from './stores/app'
import {configAPI, sessionsAPI} from './services/api'
import ChatView from './views/Chat.vue'
import RightPanel from './components/RightPanel.vue'

const params = new URLSearchParams(window.location.search)
const sessionName = params.get('sessionName') || ''
const workspaceHash = ref(params.get('workspaceHash') || null)
const store = useAppStore()
const pageTheme = ref(params.get('theme') === 'dark' ? 'dark' : store.settings.theme)
const theme = computed(() => pageTheme.value)
const workspaces = ref([])
const sessions = ref([])
const chatRef = ref(null)
const rightPanelOpen = ref(false)
const rightPanelTab = ref('git')
const tabId = `${workspaceHash.value || ''}:${sessionName}`
let stopRightPanelListener = null
let stopThemeListener = null
let stopElementInspectionListener = null
let stopRefreshHistoryListener = null

onMounted(async () => {
  try {
    const response = await configAPI.listWorkspaces()
    if (response.success) workspaces.value = response.data || []
  } catch (error) {
    console.error('[desktop-chat-tab] failed to load workspaces:', error)
  }
  await loadSessions()
  stopRightPanelListener = window.electronAPI?.events?.listen('desktop-chat-tab-toggle-right-panel', () => {
    rightPanelOpen.value = !rightPanelOpen.value
  })
  stopThemeListener = window.electronAPI?.events?.listen('desktop-chat-tab-theme', (nextTheme) => {
    pageTheme.value = nextTheme === 'dark' ? 'dark' : 'gray'
    document.documentElement.setAttribute('data-theme', pageTheme.value)
  })
  stopElementInspectionListener = window.electronAPI?.events?.listen('desktop-chat-tab-element-inspection', addElementInspectionToSession)
  stopRefreshHistoryListener = window.electronAPI?.events?.listen('desktop-chat-tab-refresh-history', () => chatRef.value?.refreshHistory())
  document.documentElement.setAttribute('data-theme', pageTheme.value)
  try {
    await sessionsAPI.switchSession(sessionName, workspaceHash.value)
  } catch (error) {
    console.warn('[desktop-chat-tab] failed to synchronize session:', error)
  }
  await nextTick()
  await chatRef.value?.loadSession(sessionName, workspaceHash.value)
})

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
  stopRightPanelListener?.()
  stopThemeListener?.()
  stopElementInspectionListener?.()
  stopRefreshHistoryListener?.()
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
}

.desktop-chat-view {
  flex: 1;
  min-width: 0;
}
</style>
