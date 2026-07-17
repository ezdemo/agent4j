<template>
  <div class="app" :data-theme="theme">
    <!-- 启动画面 (仅桌面环境) -->
    <SplashScreen
      v-if="showSetup && isDesktopEnv"
      ref="splashRef"
      @ready="onSplashReady"
      @error="onSplashError"
    />

    <!-- 连接设置（Web 环境，后端未连上时显示） -->
    <SetupScreen
      v-if="showSetup && !isDesktopEnv"
      @connected="onConnected"
      @close="onSetupClose"
    />

    <!-- 自定义标题栏 -->
    <TitleBar
      :session="currentSessionTitle"
      :sideOn="sideOpen"
      :hasMessages="true"
      :hasSession="!!currentSession"
      :gitOn="rightPanelOpen"
      :elementOn="elementPanelOpen"
      :version="appVersion"
      :hasNewVersion="hasNewVersion || desktopHasNewVersion"
      @toggleSide="sideOpen = !sideOpen"
      @openSettings="openSettings"
      @toggleGit="toggleRightPanel()"
      @toggleElement="toggleElementPanel()"
      @viewPrompt="viewSystemPrompt"
      @showUpdate="showUpdateModal = true"
    />

    <!-- 主体区域 -->
    <div class="app-body">
    <!-- 侧边栏 -->
    <Sidebar
      v-model:sideOpen="sideOpen"
      :theme="theme"
      :currentSession="currentSession"
      :currentSessionWorkspace="currentSessionWorkspace"
      :workspaces="workspaces"
      :workspaceSessions="workspaceSessions"
      :initialDataLoaded="initialDataLoaded"
      @new-chat="createNewChat"
      @open-global-search="openGlobalSearch"
      @refresh-sessions="refreshSessionList"
      @new-project-chat="newProjectChat"
      @refresh-project="refreshProjectSessions"
      @manage-project="openProjectSessionDialog"
      @select-session="onSidebarSelectSession"
      @refresh-session-chat="refreshSessionChat"
      @delete-session="onSidebarDeleteSession"
      @toggle-theme="toggleTheme"
      @show-skill-market="mainView = 'skills'"
      @show-tools="showTools = true"
      @show-dashboard="showDashboard = true"
      @show-settings="openSettings"
      @reorder="handleReorderWorkspaces"
    />

    <!-- 主区域 -->
    <main class="main">
      <SettingsView v-if="mainView === 'skills'" market-only />
      <ChatView 
        v-else
        ref="chatRef" 
        hide-header 
        :workspace-hash="currentSessionWorkspace"
        :session-name="currentSession"
        :right-panel-open="rightPanelOpen"
        :workspaces="workspaces"
        :version="appVersion"
        style="flex:1;min-height:0"
        @session-updated="onSessionUpdated"
        @session-branched="onSessionBranched"
        @start-task="startTaskFromWelcome"
        @switch-workspace="switchWelcomeWorkspace"
        @manage-workspaces="showWorkspacePicker = true"
      />
    </main>

    <!-- 元素面板（保活：用 v-show） -->
    <div
      v-show="elementPanelOpen"
      class="element-panel-wrapper"
      :class="{ dragging: isElementDragging }"
      :style="{ width: elementPanelWidth + 'px' }"
    >
      <!-- 拖拽手柄 -->
      <div
        class="element-resize-handle"
        @mousedown.prevent="onElementResizeStart"
        title="拖拽调整宽度"
      ></div>
      <div class="element-panel-header">
        <span>元素检查</span>
        <button class="btn-icon-sm" @click="elementPanelOpen = false">×</button>
      </div>
      <ElementPanel ref="elemPanelRef" @send="onElementSend" />
    </div>

    <!-- 右侧面板 -->
    <RightPanel
      :open="rightPanelOpen"
      v-model="rightPanelTab"
      :workspace-hash="currentSessionWorkspace"
      :session-name="currentSession"
      :sessions="sessions"
      @close="rightPanelOpen = false"
      @add-to-session="addFileSelectionToSession"
    />

      <!-- 系统提示词 Modal -->
      <Teleport to="body">
        <div v-if="promptModalOpen" class="modal-mask" @click.self="promptModalOpen = false">
          <div class="modal" style="width:80vw;max-width:900px;height:80vh;display:flex;flex-direction:column">
            <div class="modal-head">
              <span>系统提示词</span>
              <span style="font-size:11px;color:var(--fg-4);background:var(--bg-3);padding:2px 8px;border-radius:4px">{{
                  promptLength
                }} 字符</span>
              <div style="flex:1"></div>
              <button class="btn-icon-sm" @click="promptModalOpen = false">×</button>
            </div>
            <div class="modal-body" style="flex:1;overflow:auto;padding:20px 24px">
              <div class="prompt-rendered" v-html="fmtPrompt(promptContent)"></div>
            </div>
            <div class="modal-foot"
                 style="display:flex;justify-content:flex-end;gap:8px;padding:10px 16px;border-top:1px solid var(--border)">
              <button class="btn btn-sm" @click="copyPrompt">复制</button>
              <button class="btn btn-sm" @click="promptModalOpen = false">关闭</button>
            </div>
          </div>
        </div>
      </Teleport>

    </div><!-- .app-body -->

    <Teleport to="body">
      <div v-if="showGlobalSearch" class="global-search-mask" @mousedown.self="closeGlobalSearch">
        <section class="global-search-panel" role="dialog" aria-modal="true" aria-label="搜索会话">
          <div class="global-search-input-wrap">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
            <input
              ref="globalSearchInput"
              v-model="globalSearchQuery"
              type="search"
              placeholder="搜索会话..."
              @keydown="handleGlobalSearchKeydown"
            />
            <kbd>Esc</kbd>
          </div>
          <div class="global-search-results" role="listbox">
            <button
              v-for="(item, index) in globalSearchResults"
              :key="`${item.workspaceHash}:${item.sessionName}`"
              class="global-search-result"
              :class="{ active: index === globalSearchActiveIndex }"
              type="button"
              role="option"
              :aria-selected="index === globalSearchActiveIndex"
              @mouseenter="globalSearchActiveIndex = index"
              @click="selectGlobalSearchResult(item)"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 5h12l4 4v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z"/><path d="M6 13h12M6 17h8"/></svg>
              <span class="global-search-result-main">{{ item.title }}</span>
              <span class="global-search-result-workspace">{{ item.workspaceName }}</span>
            </button>
            <div v-if="globalSearchResults.length === 0" class="global-search-empty">未找到会话</div>
          </div>
        </section>
      </div>
    </Teleport>

    <!-- 工作区选择弹窗 -->
    <WorkspacePickerModal
      v-model:show="showWorkspacePicker"
      :workspaces="workspaces"
      :current-session-workspace="currentSessionWorkspace"
      :is-desktop-env="isDesktopEnv"
      @switch-workspace="handleSwitchWorkspace"
      @add-workspace="handleAddWorkspace"
      @reorder="handleReorderWorkspaces"
    />

    <ActionConfirmDialog
        :model-value="projectSessionDialog.visible"
        title="管理项目会话"
        :message="`“${projectSessionDialog.name}”的会话可单独清空，或连同项目记录一起删除。`"
        :actions="projectSessionActions"
        :pending="projectSessionDialog.pending"
        @update:model-value="value => { if (!value) closeProjectSessionDialog() }"
        @action="handleProjectSessionAction"
    >
      <template #icon>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="m7 21-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21"/>
          <path d="M22 21H7"/><path d="m5 11 9 9"/>
        </svg>
      </template>
    </ActionConfirmDialog>

    <!-- 工具弹窗 -->
    <Teleport to="body">
      <div v-if="showTools" class="modal-mask" @click.self="showTools = false">
        <div class="modal modal-tools" role="dialog" aria-modal="true" aria-label="工具列表">
          <div class="modal-head tools-modal-head">
            <span class="tools-modal-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m14.7 6.3 3 3"/><path d="m5 21 5.6-5.6"/><path d="m5.5 15.5-2-2a2.1 2.1 0 0 1 3-3l2 2"/><path d="m18.5 8.5 2 2a2.1 2.1 0 0 1-3 3l-2-2"/><path d="m8 16 8-8"/></svg>
              工具列表
              <span class="tools-modal-count">{{ tools.length }}</span>
            </span>
            <div class="modal-head-actions">
              <button
                class="tools-modal-icon refresh-tools-btn"
                :class="{ refreshing: refreshingTools }"
                @click="refreshTools"
                :disabled="refreshingTools"
                title="刷新工具列表"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="23 4 23 10 17 10"/>
                  <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                </svg>
              </button>
              <button class="tools-modal-icon" type="button" title="关闭" aria-label="关闭工具列表" @click="showTools = false">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6 6 18M6 6l12 12"/></svg>
              </button>
            </div>
          </div>
          <div class="modal-body tool-modal-body">
            <!-- 筛选栏 -->
            <div class="tool-filter-bar">
              <button
                v-for="f in toolFilters"
                :key="f.value"
                class="tool-filter-btn"
                :class="{ active: toolFilter === f.value }"
                :aria-pressed="toolFilter === f.value"
                @click="toolFilter = f.value"
              >{{ f.label }}</button>
            </div>
            <div v-if="filteredTools.length === 0" class="modal-empty">暂无工具</div>
            <div v-else class="tool-list" role="list">
              <div class="tool-list-head" aria-hidden="true">
                <span>工具</span>
                <span>说明</span>
                <span>自动放行</span>
                <span>启用</span>
              </div>
              <div v-for="t in filteredTools" :key="t.name" class="tool-row" :class="{ disabled: !t.enabled }" role="listitem">
                <button class="tool-row-info" type="button" :title="t.enabled ? '点击禁用' : '点击启用'" @click="toggleTool(t)">
                  <code>{{ t.name }}</code>
                  <span v-if="!t.enabled" class="tool-disabled-state">已禁用</span>
                </button>
                <span class="tool-row-desc" :title="t.description">{{ t.description }}</span>
                <div class="tool-row-actions">
                  <button
                    class="tool-toggle-btn auto-toggle"
                    :class="{ enabled: t.autoApproved }"
                    :disabled="refreshingTools"
                    :aria-checked="t.autoApproved"
                    role="switch"
                    @click.stop="toggleAutoTool(t)"
                    title="自动放行">
                    <div class="toggle-track auto-track"><div class="toggle-thumb"></div></div>
                  </button>
                  <button
                    class="tool-toggle-btn"
                    :class="{ enabled: t.enabled }"
                    :disabled="refreshingTools"
                    :aria-checked="t.enabled"
                    role="switch"
                    @click.stop="toggleTool(t)"
                    :title="t.enabled ? '禁用' : '启用'">
                    <div class="toggle-track"><div class="toggle-thumb"></div></div>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 配置弹窗 -->
    <Teleport to="body">
      <div v-if="showConfig" class="modal-mask" @click.self="showConfig = false">
        <div class="modal">
          <div class="modal-head">
            <span>系统配置</span>
            <button class="btn-icon-sm" @click="showConfig = false">×</button>
          </div>
          <div class="modal-body">
            <!-- 系统配置 -->
            <div class="config-section">
              <div class="config-section-title">系统配置</div>
              <div v-for="(v, k) in config" :key="k" class="config-row">
                <span class="config-key">{{ k }}</span>
                <span class="config-val">{{ v }}</span>
              </div>
              <div v-if="!Object.keys(config).length" class="modal-empty">加载中...</div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 设置弹窗 -->
    <Teleport to="body">
      <div v-if="showSettings" class="modal-mask" @click.self="showSettings = false">
        <div class="modal modal-settings">
          <div class="modal-head">
            <span>设置</span>
            <button class="btn-icon-sm" @click="showSettings = false">×</button>
          </div>
          <div class="modal-body">
            <SettingsView :initial-tab="settingsInitialTab" @auto-update="handleAutoUpdate" @init-pet="handleInitPet" />
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 数据面板弹窗 -->
    <Teleport to="body">
      <div v-if="showDashboard" class="modal-mask" @click.self="showDashboard = false">
        <div class="modal modal-dashboard" role="dialog" aria-modal="true" aria-label="数据面板">
          <div class="modal-head dashboard-modal-head">
            <span class="dashboard-modal-title">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>
              数据面板
            </span>
            <button class="dashboard-modal-close" type="button" title="关闭" aria-label="关闭数据面板" @click="showDashboard = false">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6 6 18M6 6l12 12"/></svg>
            </button>
          </div>
          <div class="modal-body">
            <DashboardPanel ref="dashboardRef" />
          </div>
        </div>
      </div>
    </Teleport>

    <!-- OpenAPI 管理弹窗 -->
    <!-- 确认对话框 -->
    <ConfirmDialog />

    <!-- 版本更新弹窗 -->
    <Teleport to="body">
      <div v-if="showUpdateModal" class="update-modal-mask" @click.self="showUpdateModal = false">
        <div class="update-modal">
          <div class="update-modal-head">
            <span class="update-modal-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
              版本更新
            </span>
            <button class="btn-icon-xs" @click="showUpdateModal = false">×</button>
          </div>

          <div class="update-modal-body">
            <VersionInfoPanel
                :app-version="appVersion"
                :electron-version="electronVersion"
                :latest-version="latestVersion"
                :release-url="releaseUrl"
                :has-new-version="hasNewVersion"
                :desktop-has-new-version="desktopHasNewVersion"
                :checking="checkingVersion"
                :is-electron="platform.isElectron"
                :auto-updating="autoUpdating"
                @check="handleCheckVersion"
                @download="openDesktopDownloadUrl"
                @auto-update="handleAutoUpdate"
            />
          </div>

          <div class="update-modal-foot">
            <button class="btn" @click="showUpdateModal = false">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 核心服务版本落后提示弹窗（仅桌面端） -->
    <Teleport to="body">
      <div v-if="showCoreServiceUpdateModal" class="update-modal-mask" @click.self="!coreServiceUpdating && !coreServiceUpdateDone && (showCoreServiceUpdateModal = false)">
        <div class="update-modal" style="max-width: 520px;">
          <div class="update-modal-head">
            <span class="update-modal-title">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              核心服务更新
            </span>
            <button class="btn-icon-xs" @click="showCoreServiceUpdateModal = false" :disabled="coreServiceUpdating || coreServiceUpdateDone" v-show="!coreServiceUpdateDone">×</button>
          </div>

          <div class="update-modal-body">
            <!-- 版本对比 -->
            <div class="core-service-update-versions">
              <div class="version-row">
                <span class="version-label">桌面端</span>
                <span class="version-badge version-new">v{{ desktopAppVersion }}</span>
              </div>
              <div class="version-row">
                <span class="version-label">核心服务</span>
                <span class="version-badge version-old">v{{ appVersion }}</span>
              </div>
            </div>

            <!-- 安装日志 -->
            <div v-if="coreServiceUpdating || installLogs.length > 0" class="install-log-container">
              <div class="install-log" ref="logContainer">
                <div v-for="(line, i) in installLogs" :key="i" class="log-line">{{ line }}</div>
                <div v-if="installLogs.length === 0" class="log-line log-placeholder">等待输出...</div>
              </div>
            </div>

            <!-- 更新成功提示 -->
            <div v-if="coreServiceUpdateDone" class="update-success-tip">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <span>更新完成，请关闭程序后重新启动</span>
            </div>
          </div>

          <div class="update-modal-foot">
            <button v-if="coreServiceUpdateDone" class="btn btn-primary" @click="closeAppAfterUpdate">
              关闭程序
            </button>
            <template v-else>
              <button class="btn btn-secondary" @click="showCoreServiceUpdateModal = false" :disabled="coreServiceUpdating">
                稍后
              </button>
              <button class="btn btn-primary" @click="handleCoreServiceUpdate" :disabled="coreServiceUpdating">
                <span v-if="coreServiceUpdating" class="btn-spinner"></span>
                {{ coreServiceUpdating ? '更新中...' : '立即更新' }}
              </button>
            </template>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRouter} from 'vue-router'
import {message} from 'ant-design-vue'
import {useConfirm} from './composables/useConfirm'
import {md} from './utils/highlight'
import {applyHljsTheme} from './utils/hljsTheme'
import {sanitize} from './utils/sanitize'
import {useAppStore} from './stores/app'
import {agentAPI, configAPI, sessionsAPI, systemAPI, toolsAPI} from './services/api'
import SetupScreen from './components/SetupScreen.vue'
import TitleBar from './components/TitleBar.vue'
import SplashScreen from './components/SplashScreen.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import Sidebar from './components/Sidebar.vue'
import RightPanel from './components/RightPanel.vue'
import VersionInfoPanel from './components/VersionInfoPanel.vue'
import ElementPanel from './components/ElementPanel.vue'
import WorkspacePickerModal from './components/WorkspacePickerModal.vue'
import ActionConfirmDialog from './components/ActionConfirmDialog.vue'
import ChatView from './views/Chat.vue'
import SettingsView from './views/Settings.vue'
import DashboardPanel from './components/Dashboard.vue'
import {platform} from '@/services/platform'

const store = useAppStore()
const router = useRouter()
const { confirm } = useConfirm()

// 主题：统一从 Pinia store 读写，确保设置页和主页一致
const theme = computed({ get: () => store.settings.theme, set: (v) => { store.settings.theme = v } })
watch(theme, applyHljsTheme, {immediate: true})
const sideOpen = ref(true)
const mainView = ref('chat')
const SIDEBAR_AUTO_COLLAPSE_WIDTH = 1024
const collapseSidebarForNarrowViewport = () => {
  if (window.innerWidth < SIDEBAR_AUTO_COLLAPSE_WIDTH) sideOpen.value = false
}
const sessions = ref([])
const currentSession = ref('')
const status = ref({})
const usage = ref({})
const tools = ref([])
const config = ref({})
const showTools = ref(false)
const refreshingTools = ref(false)
const toolFilter = ref('all')
const toolFilters = [
  { label: '全部', value: 'all' },
  { label: '已启用', value: 'enabled' },
  { label: '已禁用', value: 'disabled' },
  { label: '自动放行', value: 'autoApproved' },
]
const filteredTools = computed(() => {
  if (toolFilter.value === 'all') return tools.value
  if (toolFilter.value === 'enabled') return tools.value.filter(t => t.enabled)
  if (toolFilter.value === 'disabled') return tools.value.filter(t => !t.enabled)
  if (toolFilter.value === 'autoApproved') return tools.value.filter(t => t.autoApproved)
  return tools.value
})
const isDesktopEnv = ref(false)
const showSetup = ref(true)  // SplashScreen (桌面) 或 SetupScreen (Web) 成功后设为 false

// 异步检测环境
async function detectEnvironment() {
  if (platform.isElectron) {
    isDesktopEnv.value = true
    console.log('[App] Electron environment detected')
  } else {
    isDesktopEnv.value = false
    console.log('[App] Browser environment detected')
  }
}
const showConfig = ref(false)
const showSettings = ref(false)
const settingsInitialTab = ref('general')
const showDashboard = ref(false)
const rightPanelOpen = ref(false)
const openSettings = () => {
  settingsInitialTab.value = 'general'
  showSettings.value = true
}
const createNewChat = async () => {
  mainView.value = 'chat'
  await newChat()
}
const rightPanelTab = ref('git')
const elementPanelOpen = ref(false)
const elementPanelWidth = ref(360)
const isElementDragging = ref(false)
const initialDataLoaded = ref(false)
const elemPanelRef = ref(null)

// 从 AI 消息中的链接点击「在元素界面打开」
function onOpenInElement(e) {
  const url = e.detail?.url
  if (!url) return
  elementPanelOpen.value = true
  // 等 DOM 更新后再导航
  nextTick(() => {
    elemPanelRef.value?.loadUrl?.(url)
  })
}

// 切换右侧面板（直接 toggle，不关心当前 tab）
function toggleRightPanel() {
  rightPanelOpen.value = !rightPanelOpen.value
}

// 切换元素面板
function toggleElementPanel() {
  elementPanelOpen.value = !elementPanelOpen.value
}

// 元素面板拖拽调整宽度
const ELEMENT_MIN_WIDTH = 280
const ELEMENT_MAX_WIDTH = 800

function onElementResizeStart(e) {
  isElementDragging.value = true
  document.body.style.cursor = 'ew-resize'
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onElementResizeMove)
  document.addEventListener('mouseup', onElementResizeEnd)
}

function onElementResizeMove(e) {
  if (!isElementDragging.value) return
  const viewportWidth = window.innerWidth
  const newWidth = viewportWidth - e.clientX
  elementPanelWidth.value = Math.min(ELEMENT_MAX_WIDTH, Math.max(ELEMENT_MIN_WIDTH, newWidth))
}

function onElementResizeEnd() {
  isElementDragging.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  document.removeEventListener('mousemove', onElementResizeMove)
  document.removeEventListener('mouseup', onElementResizeEnd)
  // 持久化宽度
  try {
    localStorage.setItem('loopra-element-panel-width', String(elementPanelWidth.value))
  } catch { /* ignore */ }
}

// 加载保存的元素面板宽度
onMounted(() => {
  collapseSidebarForNarrowViewport()
  window.addEventListener('resize', collapseSidebarForNarrowViewport)
  try {
    const saved = localStorage.getItem('loopra-element-panel-width')
    if (saved) {
      const w = parseInt(saved, 10)
      if (w >= ELEMENT_MIN_WIDTH && w <= ELEMENT_MAX_WIDTH) {
        elementPanelWidth.value = w
      }
    }
  } catch { /* ignore */ }
})

// 元素面板发送消息 → 直接发给当前会话的 AI
function onElementSend(payload) {
  const comp = payload.component
  // 组装完整元素信息为 Markdown
  let md = '---\n'
  md += '**组件路径:** ' + (comp.path ? comp.path.join(' > ') : comp.name) + '\n'
  md += '**文件路径:** ' + (comp.file || '-') + '\n'
  md += '**标签:** `' + (comp.tag || '-') + '`\n'
  md += '**文本:** ' + (comp.text || '-') + '\n'
  md += '**CSS 选择器:** `' + (comp.selector || '-') + '`\n'
  if (comp.attrs && comp.attrs.length) {
    md += '**属性:** '
    md += comp.attrs.map(a => '`' + a.key + '="' + a.val + '"`').join(' ')
    md += '\n'
  }
  md += '---\n'
  md += payload.message
  chatRef.value?.sendCommand?.(md)
}

function addFileSelectionToSession(selection) {
  if (!selection?.content?.trim()) return
  chatRef.value?.appendFileSelection?.(selection)
}

const chatRef = ref(null)
const dashboardRef = ref(null)
const workspace = ref('')

// 版本信息
const appVersion = ref('')
const hasNewVersion = ref(false)
const latestVersion = ref('')
const releaseUrl = ref('')
const showUpdateModal = ref(false)
const autoUpdating = ref(false)

// Electron 桌面端版本
const electronVersion = ref('')
const desktopHasNewVersion = ref(false)
const checkingVersion = ref(false)

// 核心服务版本落后提示（仅桌面端）
const showCoreServiceUpdateModal = ref(false)
const coreServiceUpdating = ref(false)
const coreServiceUpdateDone = ref(false)
const desktopAppVersion = ref('')
const installLogs = ref([])
const logContainer = ref(null)
let unlistenInstallOutput = null

// 系统提示词弹窗
const promptModalOpen = ref(false)
const promptContent = ref('')
const promptLength = ref(0)
const loadingPrompt = ref(false)

const viewSystemPrompt = async () => {
  loadingPrompt.value = true
  try {
    const params = {}
    if (currentSessionWorkspace.value) params.workspaceHash = currentSessionWorkspace.value
    if (currentSession.value) params.sessionName = currentSession.value
    const {agentAPI} = await import('./services/api')
    const res = await agentAPI.getSystemPrompt(params)
    if (res.success && res.data) {
      promptContent.value = res.data.content || ''
      promptLength.value = res.data.length || 0
      promptModalOpen.value = true
    } else {
      message.error(res.message || '获取提示词失败')
    }
  } catch (e) {
    message.error('获取提示词失败: ' + (e.message || '未知错误'))
  } finally {
    loadingPrompt.value = false
  }
}

const fmtPrompt = c => {
  if (!c) return ''
  return sanitize(md.parse(c))
}

const copyPrompt = () => {
  if (!promptContent.value) return
  navigator.clipboard.writeText(promptContent.value).then(() => {
    message.success('提示词已复制')
  }).catch(() => {
  })
}

// 工作区相关
const showWorkspacePicker = ref(false)
const pendingStarterPrompt = ref('')
const workspaces = ref([])

// 按工作区 hash 分组的会话
const workspaceSessions = ref({})
const showGlobalSearch = ref(false)
const globalSearchQuery = ref('')
const globalSearchInput = ref(null)
const globalSearchActiveIndex = ref(0)

const globalSearchResults = computed(() => {
  const query = globalSearchQuery.value.trim().toLowerCase()
  const items = workspaces.value.flatMap(workspace =>
    (workspaceSessions.value[workspace.hash] || []).map(session => ({
      workspaceHash: workspace.hash,
      workspaceName: workspace.name,
      sessionName: session.name,
      title: session.title || formatName(session.name),
      mtime: session.mtime || 0
    }))
  )

  return items
    .filter(item => !query || [item.title, item.sessionName, item.workspaceName]
      .some(value => value?.toLowerCase().includes(query)))
    .sort((a, b) => b.mtime - a.mtime)
    .slice(0, 50)
})

const currentSessionTitle = computed(() => {
  if (!currentSession.value) return '新对话'
  const s = sessions.value.find(s => s.name === currentSession.value)
  return (s && s.title) || formatName(currentSession.value)
})

const workspaceName = computed(() => {
  if (!workspace.value) return '选择工作区'
  const parts = workspace.value.split(/[\\/]/)
  return parts[parts.length - 1] || workspace.value
})

// 当前会话所属的工作区 hash
const currentSessionWorkspace = ref(null)

// 当前工作区下的会话列表
const currentWorkspaceSessions = computed(() => {
  if (!currentSessionWorkspace.value) return []
  return workspaceSessions.value[currentSessionWorkspace.value] || []
})

const fmtTokens = n => !n ? '0' : n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n)

const formatName = n => {
  const m = n.match(/(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})/)
  return m ? `${m[2]}/${m[3]} ${m[4]}:${m[5]}${n.slice(m.index + m[0].length)}` : n.replace(/[-_]+/g, ' ').slice(0, 24)
}

const themeOrder = ['gray', 'dark']
const toggleTheme = () => {
  const idx = themeOrder.indexOf(store.settings.theme)
  store.settings.theme = themeOrder[(idx + 1) % themeOrder.length]
}

let heartbeatTimer = null

const startHeartbeat = () => {
  stopHeartbeat()
  heartbeatTimer = setInterval(async () => {
    if (showSetup.value) return  // 已在设置页，跳过
    try {
      await agentAPI.getStatus()
    } catch {
      console.warn('[heartbeat] 后端不可达，切换到设置页')
      showSetup.value = true
    }
  }, 5000)
}

const stopHeartbeat = () => {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

// 连接设置页回调：后端连接成功
const onConnected = () => {
  showSetup.value = false
  loadData()
  startHeartbeat()
}

// 用户关闭设置页（仅 Web 环境走到这里）
const onSetupClose = () => {
  showSetup.value = false
  loadData()
  startHeartbeat()
}

// 服务就绪回调（SplashScreen 安装/启动完成后调用）
const onSplashReady = () => {
  console.log('Loopra Web service is ready')
  showSetup.value = false
  loadData()
  startHeartbeat()
}

// 服务错误回调
const onSplashError = (error) => {
  console.error('Loopra Web service error:', error)
}

// 加载数据
const loadData = async () => {
  try {
    const [s, t, cf] = await Promise.allSettled([
      agentAPI.getStatus(), toolsAPI.list(), configAPI.getConfig()
    ])
    if (s.status === 'fulfilled' && s.value.success) {
      status.value = s.value.data || {}
      if (s.value.data?.workspace) {
        workspace.value = s.value.data.workspace
      }
    }
    if (t.status === 'fulfilled' && t.value.success) tools.value = t.value.data || []
    if (cf.status === 'fulfilled' && cf.value.success) {
      config.value = cf.value.data || {}
      if (cf.value.data?.workspace && !workspace.value) {
        workspace.value = cf.value.data.workspace
      }
    }
  } catch {}
  await loadWorkspaces()
  await initializeWorkspaceContext()
  await loadSessions()
  initialDataLoaded.value = true
}

const openGlobalSearch = async () => {
  globalSearchQuery.value = ''
  globalSearchActiveIndex.value = 0
  showGlobalSearch.value = true
  await nextTick()
  globalSearchInput.value?.focus()
}

const closeGlobalSearch = () => {
  showGlobalSearch.value = false
  globalSearchQuery.value = ''
}

const selectGlobalSearchResult = (item) => {
  closeGlobalSearch()
  onSidebarSelectSession(item)
}

const handleGlobalSearchKeydown = (event) => {
  const resultCount = globalSearchResults.value.length
  if (event.key === 'Escape') {
    closeGlobalSearch()
  } else if (event.key === 'ArrowDown' && resultCount > 0) {
    event.preventDefault()
    globalSearchActiveIndex.value = Math.min(globalSearchActiveIndex.value + 1, resultCount - 1)
  } else if (event.key === 'ArrowUp' && resultCount > 0) {
    event.preventDefault()
    globalSearchActiveIndex.value = Math.max(globalSearchActiveIndex.value - 1, 0)
  } else if (event.key === 'Enter' && resultCount > 0) {
    event.preventDefault()
    selectGlobalSearchResult(globalSearchResults.value[globalSearchActiveIndex.value])
  }
}

watch(globalSearchQuery, () => {
  globalSearchActiveIndex.value = 0
})

// 刷新工具列表（不干扰其他数据）
const refreshTools = async () => {
  refreshingTools.value = true
  try {
    const r = await toolsAPI.list()
    if (r.success) tools.value = r.data || []
  } catch {}
  refreshingTools.value = false
}

// 切换工具启用/禁用状态
const toggleTool = async (tool) => {
  refreshingTools.value = true
  try {
    await toolsAPI.toggle(tool.name)
    tool.enabled = !tool.enabled
  } catch (err) {
    console.error('切换工具状态失败:', err)
  }
  refreshingTools.value = false
}

// 切换工具自动放行状态
const toggleAutoTool = async (tool) => {
  refreshingTools.value = true
  try {
    await toolsAPI.autoToggle(tool.name)
    tool.autoApproved = !tool.autoApproved
  } catch (err) {
    console.error('切换自动放行状态失败:', err)
  }
  refreshingTools.value = false
}

const loadSessions = async () => {
  try {
    // 获取所有工作区
    const wsList = workspaces.value
    if (wsList.length === 0) {
      workspaceSessions.value = {}
      return
    }
    
    // 并行加载每个工作区的会话
    const results = await Promise.all(
      wsList.map(w =>
        sessionsAPI.list(w.hash)
          .then(r => r.success ? (r.data || []) : [])
          .catch(() => [])
      )
    )
    
    const grouped = {}
    wsList.forEach((w, i) => {
      grouped[w.hash] = results[i] || []
    })
    workspaceSessions.value = grouped
    
    // 也更新 flat sessions（兼容旧代码）
    const wsHash = currentSessionWorkspace.value
    if (wsHash && grouped[wsHash]) {
      sessions.value = grouped[wsHash] || []
    }
  } catch {}
}

const onSessionUpdated = (sessionName, optimistic = false) => {
  if (!optimistic || !sessionName || !currentSessionWorkspace.value) {
    return loadSessions()
  }

  const workspaceHash = currentSessionWorkspace.value
  const existing = workspaceSessions.value[workspaceHash] || []
  if (existing.some(session => session.name === sessionName)) return

  const addedSession = {
    name: sessionName,
    title: null,
    messageCount: 1,
    active: sessionName === currentSession.value,
    mtime: Date.now()
  }
  const updated = [addedSession, ...existing]
  workspaceSessions.value = {...workspaceSessions.value, [workspaceHash]: updated}
  if (workspaceHash === currentSessionWorkspace.value) {
    sessions.value = updated
  }
}

// 分支会话：切换到新会话并刷新列表
const onSessionBranched = async (newSessionName) => {
  currentSession.value = newSessionName
  await nextTick()
  await Promise.all([
    loadSessions(),
    chatRef.value?.loadSession(newSessionName, currentSessionWorkspace.value)
  ])
  message.success("已分支到新会话")
}

// 加载工作区列表
const loadWorkspaces = async () => {
  try {
    const r = await configAPI.listWorkspaces()
    if (r.success) workspaces.value = await applyWorkspaceOrder(r.data || [])
  } catch (e) {
    console.error('加载工作区列表失败:', e)
  }
}

// 从服务端恢复工作区排序
async function applyWorkspaceOrder(list) {
  if (!list || list.length === 0) return list
  try {
    const r = await configAPI.getWorkspaceOrder()
    if (r.success && r.data && r.data.length) {
      const map = new Map(list.map(w => [w.hash, w]))
      const ordered = r.data.filter(h => map.has(h)).map(h => map.get(h))
      const rest = list.filter(w => !r.data.includes(w.hash))
      return [...ordered, ...rest]
    }
  } catch {}
  return list
}

// 拖拽排序后保存到服务端
async function handleReorderWorkspaces(newList) {
  workspaces.value = newList
  try {
    await configAPI.saveWorkspaceOrder(newList.map(w => w.hash))
  } catch (e) {
    console.error('保存工作区排序失败:', e)
  }
}

// 切换工作区（仅持久化上下文，不新建会话）
const switchWorkspaceContext = async (hash) => {
  const ws = workspaces.value.find(w => w.hash === hash)
  if (!ws) return
  await configAPI.switchWorkspace(ws.path)
  workspace.value = ws.path
}

const initializeWorkspaceContext = async () => {
  if (currentSessionWorkspace.value || workspaces.value.length === 0) return
  const activeWorkspace = workspaces.value.find(item => item.path === workspace.value)
  const targetWorkspace = activeWorkspace || workspaces.value[0]
  await switchWorkspaceContext(targetWorkspace.hash)
  currentSessionWorkspace.value = targetWorkspace.hash
}

// 切换工作区（用户主动操作，切换后新建会话）
const handleSwitchWorkspace = async (hash) => {
  try {
    const ws = workspaces.value.find(w => w.hash === hash)
    if (!ws) { message.error('工作区不存在'); return }
    const r = await configAPI.switchWorkspace(ws.path)
    if (r.success) {
      workspace.value = r.data.workspace
      showWorkspacePicker.value = false
      await loadWorkspaces()
      await loadSessions()
      currentSessionWorkspace.value = hash
      await newChat(true)
      await applyPendingStarterPrompt()
      message.success('已切换工作区')
    } else {
      message.error(r.message || '切换工作区失败')
    }
  } catch (e) {
    message.error('切换工作区失败: ' + e.message)
  }
}

// 添加新工作区
const handleAddWorkspace = async (path) => {
  if (!path) return
  
  try {
    const r = await configAPI.switchWorkspace(path)
    if (r.success) {
      workspace.value = r.data.workspace
      showWorkspacePicker.value = false
      await loadWorkspaces()
      await loadSessions()
      // 通过路径匹配新工作区的 hash 并切换上下文
      const newWs = workspaces.value.find(w => w.path === r.data.workspace)
      if (newWs) currentSessionWorkspace.value = newWs.hash
      await newChat(true)
      await applyPendingStarterPrompt()
      message.success('已添加工作区')
    } else {
      message.error(r.message || '添加工作区失败')
    }
  } catch (e) {
    message.error('添加工作区失败: ' + e.message)
  }
}

// 删除工作区
const deleteWorkspace = async (hash) => {
  try {
    const r = await configAPI.deleteWorkspace(hash)
    if (r.success) {
      if (currentSessionWorkspace.value === hash) {
        currentSession.value = ''
        currentSessionWorkspace.value = null
        chatRef.value?.resetLocalMessages()
      }
      await loadWorkspaces()
      await loadSessions()
      message.success('工作区已删除')
      return true
    } else {
      message.error(r.message || '删除工作区失败')
      return false
    }
  } catch (e) {
    message.error('删除工作区失败: ' + e.message)
    return false
  }
}

// 加载指定项目下的会话
const loadProjectSession = async (wsHash, sessionName) => {
  await switchWorkspaceContext(wsHash)
  currentSession.value = sessionName
  currentSessionWorkspace.value = wsHash
  chatRef.value?.loadSession(sessionName, wsHash)
}

// 删除指定项目下的会话
const deleteProjectSession = async (wsHash, sessionName) => {
  const ok = await confirm({ message: `确定要删除此会话吗？` })
  if (!ok) return
  try {
    await sessionsAPI.deleteSession(sessionName, wsHash)
    await loadSessions()
    message.success('会话已删除')
  } catch (e) {
    message.error('删除会话失败: ' + e.message)
  }
}

// 在指定项目中创建新会话
const newProjectChat = async (wsHash) => {
  if (!wsHash) return
  try {
    const r = await sessionsAPI.createNew({ workspaceHash: wsHash })
    if (r.success && r.data?.sessionName) {
      await switchWorkspaceContext(wsHash)
      currentSession.value = r.data.sessionName
      currentSessionWorkspace.value = wsHash
      mainView.value = 'chat'
      await nextTick()
      chatRef.value?.resetLocalMessages()
      await loadSessions()
      message.success('已新建对话')
    } else {
      message.error('新建对话失败')
    }
  } catch (e) {
    console.error('新建会话失败:', e)
    message.error('新建对话失败: ' + e.message)
  }
}

// 刷新指定项目的会话列表
const refreshProjectSessions = async (wsHash) => {
  if (!wsHash) return
  try {
    const r = await sessionsAPI.list(wsHash)
    if (r.success) {
      const grouped = { ...workspaceSessions.value }
      grouped[wsHash] = r.data || []
      workspaceSessions.value = grouped
      // 更新 flat sessions
      if (currentSessionWorkspace.value === wsHash) {
        sessions.value = grouped[wsHash] || []
      }
    }
    message.success('会话列表已刷新')
  } catch (e) {
    message.error('刷新失败: ' + (e.message || '未知错误'))
  }
}

// 清空指定项目（或当前活跃项目）的所有会话
const clearProjectSessions = async (wsHash) => {
  const hash = wsHash || currentSessionWorkspace.value
  if (!hash) return
  try {
    await sessionsAPI.clearAll(hash)
    await loadSessions()
    if (currentSessionWorkspace.value === hash) {
      currentSession.value = ''
      chatRef.value?.resetLocalMessages()
    }
    message.success('会话已清空')
    return true
  } catch (e) {
    message.error('清空会话失败: ' + e.message)
    return false
  }
}

const projectSessionDialog = ref({visible: false, workspaceHash: null, name: '', pending: false})
const projectSessionActions = [
  {key: 'cancel', label: '取消'},
  {key: 'clear', label: '清空会话列表', variant: 'accent'},
  {key: 'delete', label: '删除项目', variant: 'danger'}
]

const openProjectSessionDialog = (workspaceHash) => {
  const workspace = workspaces.value.find(w => w.hash === workspaceHash)
  projectSessionDialog.value = {
    visible: true,
    workspaceHash,
    name: workspace?.name || workspaceHash,
    pending: false
  }
}

const closeProjectSessionDialog = () => {
  if (projectSessionDialog.value.pending) return
  projectSessionDialog.value.visible = false
}

const confirmClearProjectSessions = async () => {
  const {workspaceHash} = projectSessionDialog.value
  if (!workspaceHash) return
  projectSessionDialog.value.pending = true
  const cleared = await clearProjectSessions(workspaceHash)
  projectSessionDialog.value.pending = false
  if (cleared) projectSessionDialog.value.visible = false
}

const confirmDeleteWorkspace = async () => {
  const {workspaceHash} = projectSessionDialog.value
  if (!workspaceHash) return
  projectSessionDialog.value.pending = true
  const deleted = await deleteWorkspace(workspaceHash)
  projectSessionDialog.value.pending = false
  if (deleted) projectSessionDialog.value.visible = false
}

const handleProjectSessionAction = (action) => {
  if (action === 'cancel') {
    closeProjectSessionDialog()
  } else if (action === 'clear') {
    confirmClearProjectSessions()
  } else if (action === 'delete') {
    confirmDeleteWorkspace()
  }
}

// Sidebar 事件：选择会话
const onSidebarSelectSession = ({ workspaceHash, sessionName }) => {
  mainView.value = 'chat'
  loadProjectSession(workspaceHash, sessionName)
}

// Sidebar 事件：删除会话
const onSidebarDeleteSession = ({ workspaceHash, sessionName }) => {
  deleteProjectSession(workspaceHash, sessionName)
}

const newChat = async (skipReload = false) => {
  try {
    const params = {}
    if (currentSessionWorkspace.value) params.workspaceHash = currentSessionWorkspace.value
    const r = await sessionsAPI.createNew(params)
    if (r.success && r.data?.sessionName) {
      currentSession.value = r.data.sessionName
      if (r.data.workspaceHash) currentSessionWorkspace.value = r.data.workspaceHash
      chatRef.value?.resetLocalMessages()
      if (!skipReload) {
        await loadSessions()
        await loadWorkspaces()
        message.success('已新建对话')
      }
    } else {
      message.error('新建对话失败')
    }
  } catch (e) {
    console.error('新建会话失败:', e)
    message.error('新建对话失败: ' + e.message)
  }
}

const startTaskFromWelcome = async (request) => {
  const prompt = typeof request === 'string' ? request : request?.prompt || ''
  const workspaceHash = typeof request === 'string' ? null : request?.workspaceHash

  if (!workspaceHash) {
    pendingStarterPrompt.value = prompt
    showWorkspacePicker.value = true
    return
  }

  try {
    await switchWorkspaceContext(workspaceHash)
    currentSessionWorkspace.value = workspaceHash
    await newChat(true)
    await nextTick()
    await chatRef.value?.startWelcomePrompt(prompt)
  } catch (e) {
    console.error('从欢迎页创建会话失败:', e)
    message.error('创建新会话失败: ' + (e.message || '未知错误'))
  }
}

const switchWelcomeWorkspace = async (workspaceHash) => {
  if (!workspaceHash || workspaceHash === currentSessionWorkspace.value) return
  try {
    await switchWorkspaceContext(workspaceHash)
    currentSessionWorkspace.value = workspaceHash
  } catch (e) {
    console.error('从欢迎页切换工作区失败:', e)
    message.error('切换工作区失败: ' + (e.message || '未知错误'))
  }
}

const applyPendingStarterPrompt = async () => {
  const prompt = pendingStarterPrompt.value
  pendingStarterPrompt.value = ''
  if (!prompt) return
  await nextTick()
  chatRef.value?.setDraft(prompt)
}

const loadSession = name => {
  currentSession.value = name
  chatRef.value?.loadSession(name, null)
}

// 刷新侧边栏会话列表
const refreshSessionList = async () => {
  try {
    await loadSessions()
    message.success('会话列表已刷新')
  } catch (e) {
    message.error('刷新失败: ' + (e.message || '未知错误'))
  }
}

// 刷新指定会话的聊天记录（强制从后端加载）
const refreshSessionChat = async (name) => {
  chatRef.value?.refreshHistory(name)
}

const deleteSession = async name => {
  const ok = await confirm({ message: `确定要删除此会话吗？` })
  if (!ok) return
  try {
    // 从 workspaceSessions 中找到该会话所属的工作区
    let workspaceHash = null
    for (const [hash, sessions] of Object.entries(workspaceSessions.value)) {
      if (sessions.some(s => s.name === name)) {
        workspaceHash = hash
        break
      }
    }
    await sessionsAPI.deleteSession(name, workspaceHash)
    await loadSessions()
    message.success('会话已删除')
  } catch (e) {
    message.error('删除会话失败: ' + e.message)
  }
}

const clearAllSessions = async () => {
  const ok = await confirm({ message: '确定要清空所有会话吗？此操作不可恢复。' })
  if (!ok) return
  try {
    let workspaceHash = currentSessionWorkspace.value
    await sessionsAPI.clearAll(workspaceHash)
    sessions.value = []
    currentSession.value = ''
    chatRef.value?.resetLocalMessages()
    message.success('所有会话已清空')
  } catch (e) {
    message.error('清空会话失败: ' + e.message)
  }
}

const clearChat = async () => {
  const ok = await confirm({ message: '确定要清空当前对话吗？' })
  if (!ok) return
  try {
    const r = await sessionsAPI.createNew({})
    if (r.success && r.data?.sessionName) {
      currentSession.value = r.data.sessionName
      chatRef.value?.resetLocalMessages()
      await loadSessions()
      await loadWorkspaces()
      message.success('对话已清空')
    } else {
      message.error('清空对话失败')
    }
  } catch (e) {
    console.error('清空对话失败:', e)
    message.error('清空对话失败: ' + e.message)
  }
}

onMounted(async () => {
  // 先检测环境，再决定显示哪个启动屏
  await detectEnvironment()
  // 清空过期的 localStorage 端口（桌面端每次启动端口都不同）
  localStorage.removeItem('loopra-port')
  if (isDesktopEnv.value) {
    // 仅桌面环境清除 api-base（端口每次启动都变，由 SplashScreen 重新检测）
    localStorage.removeItem('loopra-api-base')
  }
  console.log('[App] Cleared stale port from localStorage')

  // 异步获取版本信息（不阻塞启动）
  fetchVersionInfo()

  // 监听从 ChatMessage 发出的「在元素界面打开」事件
  window.addEventListener('loopra:open-in-element', onOpenInElement)
})

onBeforeUnmount(() => {
  stopHeartbeat()
  window.removeEventListener('resize', collapseSidebarForNarrowViewport)
  window.removeEventListener('loopra:open-in-element', onOpenInElement)
})

// 获取版本信息 — 纯后端获取，最多重试 3 次（每次间隔 3 秒）
async function fetchVersionInfo(retryCount = 0) {
  try {
    // 先尝试获取当前版本
    const res = await systemAPI.getCurrentVersion()
    if (res.success && res.data && res.data.version) {
      appVersion.value = res.data.version
    }
    // 再检查最新版本
    try {
      const checkRes = await systemAPI.checkLatestVersion()
      if (checkRes.success && checkRes.data) {
        hasNewVersion.value = checkRes.data.hasNewVersion
        latestVersion.value = checkRes.data.latestVersion || ''
        if (checkRes.data.currentVersion) {
          appVersion.value = checkRes.data.currentVersion
        }
      }
    } catch { /* 版本检查失败 */ }
    // 两个接口都失败了且未获取到版本
    if (!appVersion.value) {
      throw new Error('version not obtained')
    }
  } catch {
    if (retryCount < 2) {
      // 3 秒后重试
      await new Promise(r => setTimeout(r, 3000))
      return fetchVersionInfo(retryCount + 1)
    }
    // 3 次都失败，显示未知版本
    appVersion.value = '未知版本'
  }
  // 桌面端：额外获取 Electron 版本并对比
  if (platform.isElectron) {
    await fetchElectronVersion()
    // 检查核心服务版本是否落后于桌面端版本
    checkCoreServiceVersion()
  }
}

// 版本对比工具
function compareVersions(a, b) {
  const pa = a.split('.').map(Number)
  const pb = b.split('.').map(Number)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const na = pa[i] || 0
    const nb = pb[i] || 0
    if (na > nb) return 1
    if (na < nb) return -1
  }
  return 0
}

// 获取 Electron 版本并判断是否落后
async function fetchElectronVersion() {
  if (!platform.isElectron) return
  try {
    const ver = await window.electronAPI.getElectronVersion()
    electronVersion.value = ver
    desktopAppVersion.value = ver
    if (latestVersion.value && ver && ver !== '未知') {
      desktopHasNewVersion.value = compareVersions(ver, latestVersion.value) < 0
    }
  } catch (e) {
    electronVersion.value = '未知'
    console.warn('获取 Electron 版本失败:', e)
  }
}

// 检查核心服务版本是否落后于桌面端版本
function checkCoreServiceVersion() {
  if (!platform.isElectron) return
  if (!appVersion.value || !desktopAppVersion.value) return
  if (appVersion.value === '未知版本' || desktopAppVersion.value === '未知') return
  
  try {
    // 比较核心服务版本和桌面端版本
    const compareResult = compareVersions(appVersion.value, desktopAppVersion.value)
    if (compareResult < 0) {
      // 核心服务版本落后于桌面端版本
      console.log(`[App] 核心服务版本落后: 服务版本=${appVersion.value}, 桌面端版本=${desktopAppVersion.value}`)
      showCoreServiceUpdateModal.value = true
    }
  } catch (e) {
    console.warn('[App] 版本比较失败:', e)
  }
}

// 处理核心服务更新
async function handleCoreServiceUpdate() {
  coreServiceUpdating.value = true
  coreServiceUpdateDone.value = false
  installLogs.value = []

  // 监听安装日志事件
  try {
    unlistenInstallOutput = await platform.implementation.events.listen('install-output', (payload) => {
      if (payload && payload.line) {
        installLogs.value.push(payload.line)
        // 自动滚动到底部
        nextTick(() => {
          const el = logContainer.value
          if (el) el.scrollTop = el.scrollHeight
        })
      }
    })
  } catch (e) {
    console.warn('[App] Failed to listen install output:', e)
  }

  try {
    // 调用在线安装接口
    const result = await window.electronAPI.loopraWebService.installOnline()
    if (result && result.success) {
      installLogs.value.push('')
      installLogs.value.push('✅ 更新完成！')
      coreServiceUpdateDone.value = true
      message.success('核心服务更新成功！')
    } else {
      installLogs.value.push('')
      installLogs.value.push('❌ 更新失败，请稍后重试')
      message.error('更新失败，请稍后重试')
    }
  } catch (e) {
    installLogs.value.push('')
    installLogs.value.push('❌ 更新失败: ' + (e.message || '未知错误'))
    message.error('更新失败: ' + (e.message || '未知错误'))
  } finally {
    coreServiceUpdating.value = false
    if (unlistenInstallOutput) {
      unlistenInstallOutput()
      unlistenInstallOutput = null
    }
  }
}

// 更新完成后关闭程序
function closeAppAfterUpdate() {
  window.electronAPI.window.close()
}

// 检查最新版本（后端 + Electron 统一检查）
async function handleCheckVersion() {
  checkingVersion.value = true
  try {
    const res = await systemAPI.checkLatestVersion()
    if (res.success && res.data) {
      hasNewVersion.value = res.data.hasNewVersion
      latestVersion.value = res.data.latestVersion || ''
      releaseUrl.value = res.data.releaseUrl || ''
      if (res.data.currentVersion) {
        appVersion.value = res.data.currentVersion
      }
      // 刷新后重新对比 Electron 版本
      if (platform.isElectron) {
        await fetchElectronVersion()
      }
    }
  } catch { /* 忽略 */ }
  checkingVersion.value = false
}

// 打开下载页面
async function openDesktopDownloadUrl() {
  const url = 'https://gitee.com/ezdemo/loopra/releases/latest'
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

// 复制文本到剪贴板
function copyText(text) {
  try {
    navigator.clipboard.writeText(text)
    message.success('已复制到剪贴板')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    message.success('已复制到剪贴板')
  }
}

// 公共流程：选择工作区 → 创建新会话 → 关闭弹窗 → 等待一帧 → 发送命令
// 用于"自动更新"、"初始化宠物"等需要跳到聊天界面跑命令的场景
const runInFreshSession = async (cmd, opts = {}) => {
  const {
    successMessage = '已新建会话',
    closeSettings = true,
    closeUpdateModal = false
  } = opts

  // 1. 获取工作区列表
  const wsRes = await configAPI.listWorkspaces()
  let wsHash = null
  if (wsRes.success && wsRes.data && wsRes.data.length > 0) {
    // 优先使用当前工作区，否则选择第一个
    const currentWs = wsRes.data.find(w => w.hash === currentSessionWorkspace.value)
    if (currentWs) {
      wsHash = currentWs.hash
    } else {
      wsHash = wsRes.data[0].hash
    }
  } else {
    message.error('没有可用的工作区，请先打开一个项目')
    return false
  }

  // 2. 切换工作区上下文
  const ws = wsRes.data.find(w => w.hash === wsHash)
  if (ws) {
    await configAPI.switchWorkspace(ws.path)
    workspaces.value = await applyWorkspaceOrder(wsRes.data)
  }

  // 3. 创建新会话
  const params = { workspaceHash: wsHash }
  const r = await sessionsAPI.createNew(params)
  if (r.success && r.data?.sessionName) {
    currentSession.value = r.data.sessionName
    if (r.data.workspaceHash) currentSessionWorkspace.value = r.data.workspaceHash
    chatRef.value?.resetLocalMessages()
    await loadSessions()
    await loadWorkspaces()
    message.success(successMessage)
  } else {
    message.error('新建会话失败')
    return false
  }

  // 4. 关闭弹窗
  if (closeSettings) showSettings.value = false
  if (closeUpdateModal) showUpdateModal.value = false

  // 5. 稍等一帧让 UI 刷新，然后发送命令
  await new Promise(resolve => requestAnimationFrame(() => setTimeout(resolve, 300)))
  await chatRef.value?.sendCommand(cmd)
  return true
}

// 自动更新：复用 runInFreshSession 跳到聊天界面发送更新命令
const handleAutoUpdate = async () => {
  autoUpdating.value = true
  try {
    const updateCommand = "请帮我执行 Loopra 自动更新。根据当前操作系统平台，选择并运行对应的更新脚本：\n\n- Windows 系统：在 PowerShell 中运行 `irm https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.ps1 | iex`\n- macOS / Linux 系统：在终端中运行 `curl -fsSL https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.sh | bash`\n\n请先判断当前系统平台，然后执行对应的脚本。执行完成后请报告结果。"
    await runInFreshSession(updateCommand, {
      successMessage: '已新建更新会话',
      closeSettings: true,
      closeUpdateModal: true
    })
  } catch (e) {
    console.error('自动更新失败:', e)
    message.error('自动更新失败: ' + (e.message || '未知错误'))
  } finally {
    autoUpdating.value = false
  }
}

// 初始化宠物：复用 runInFreshSession 跳到聊天界面发送 npx petdex install 命令
const handleInitPet = async () => {
  try {
    const initCommand = '调用 npx petdex@latest install boba 初始化一个宠物'
    await runInFreshSession(initCommand, {
      successMessage: '已新建宠物初始化会话',
      closeSettings: true,
      closeUpdateModal: false
    })
  } catch (e) {
    console.error('初始化宠物失败:', e)
    message.error('初始化宠物失败: ' + (e.message || '未知错误'))
  }
}

// 设置弹窗关闭时刷新工作区和会话（用户可能在设置中切换了工作目录）
watch(showSettings, (newVal) => {
  if (!newVal) {
    loadWorkspaces()
    loadSessions()
  }
})
</script>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  user-select: none;
  -webkit-user-select: none;
  border-radius: 10px;
  border: 1px solid var(--border-2);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
  background: var(--bg);
}

.app input,
.app textarea,
.app [contenteditable="true"] {
  user-select: text;
  -webkit-user-select: text;
}

:global(body > [class*="modal"]),
:global(body > [class*="dialog"]) {
  user-select: none;
  -webkit-user-select: none;
}

:global(body > [class*="modal"] input),
:global(body > [class*="modal"] textarea),
:global(body > [class*="modal"] [contenteditable="true"]),
:global(body > [class*="dialog"] input),
:global(body > [class*="dialog"] textarea),
:global(body > [class*="dialog"] [contenteditable="true"]) {
  user-select: text;
  -webkit-user-select: text;
}

[data-theme="dark"] .app {
  border-color: var(--border);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.4);
}

/* 主体区域（侧边栏 + 主区域） */
.app-body {
  display: flex;
  flex: 1;
  min-height: 0;
  /* 不能 overflow: hidden，否则会裁剪 Chat.vue 中
     绝对定位的斜杠命令弹出框（position: absolute; bottom: 100%） */
  overflow: visible;
}


/* 主区域 */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg);
  position: relative;
}

.global-search-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: min(16vh, 140px) 16px 24px;
  background: rgba(18, 18, 20, 0.1);
}

.global-search-panel {
  width: min(680px, 100%);
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg);
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.18);
}

.global-search-input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  padding: 0 14px;
  border-bottom: 1px solid var(--border);
  color: var(--fg-4);
}

.global-search-input-wrap input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--fg);
  font: inherit;
  font-size: 15px;
}

.global-search-input-wrap input::placeholder { color: var(--fg-4); }

.global-search-input-wrap kbd {
  padding: 2px 6px;
  border: 1px solid var(--border);
  border-radius: 4px;
  color: var(--fg-4);
  font-family: var(--mono);
  font-size: 11px;
}

.global-search-results {
  max-height: min(55vh, 440px);
  overflow-y: auto;
  padding: 6px;
}

.global-search-result {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 42px;
  padding: 8px 10px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--fg-3);
  cursor: pointer;
  text-align: left;
}

.global-search-result:hover,
.global-search-result.active {
  background: var(--bg-3);
  color: var(--fg);
}

.global-search-result-main,
.global-search-result-workspace {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.global-search-result-main {
  color: var(--fg);
  font-size: 14px;
}

.global-search-result-workspace {
  max-width: 180px;
  color: var(--fg-4);
  font-size: 12px;
}

.global-search-empty {
  padding: 28px 12px;
  color: var(--fg-4);
  font-size: 13px;
  text-align: center;
}

/* Git 面板滑动动画 */
.git-panel-enter-active,
.git-panel-leave-active {
  transition: width 0.2s ease, opacity 0.2s ease;
  overflow: hidden;
}
.git-panel-enter-from,
.git-panel-leave-to {
  width: 0;
  opacity: 0;
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.25);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal {
  width: min(520px, 90vw);
  max-height: 70vh;
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--glass-shadow);
}

.modal-settings {
  width: min(800px, 95vw);
  max-height: 85vh;
}

.modal-dashboard {
  width: 980px;
  height: 780px;
  max-width: 92vw;
  max-height: 84vh;
  background: var(--bg);
  border-color: var(--border);
}

.dashboard-modal-head {
  min-height: 48px;
  padding: 0 18px;
  background: var(--bg);
}

.dashboard-modal-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.dashboard-modal-title svg {
  color: var(--accent);
}

.dashboard-modal-close {
  display: inline-grid;
  width: 28px;
  height: 28px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--fg-3);
  cursor: pointer;
}

.dashboard-modal-close:hover {
  background: var(--bg-3);
  color: var(--fg);
}

.modal-dashboard .modal-body {
  padding: 14px 18px 20px;
  background: var(--bg-2);
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
  font-weight: 600;
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px;
}

.modal-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--fg-4);
}

/* 当前工作区 CSS 已迁移到 .modal .workspace-* 下 */

/* 工作目录切换 */
.config-section {
  margin-bottom: 16px;
}

.config-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--border);
}

.modal-head-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.modal-tools {
  width: 980px;
  height: 780px;
  max-width: 92vw;
  max-height: 84vh;
  background: var(--bg);
  border-color: var(--border);
}

.tools-modal-head {
  min-height: 48px;
  padding: 0 18px;
  background: var(--bg);
}

.tools-modal-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.tools-modal-title > svg { color: var(--accent); }

.tools-modal-count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: var(--r-sm);
  background: var(--bg-3);
  color: var(--fg-3);
  font: 600 11px var(--mono);
  text-align: center;
}

.tools-modal-icon {
  display: inline-grid;
  width: 28px;
  height: 28px;
  place-items: center;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--fg-3);
  cursor: pointer;
}

.tools-modal-icon:hover:not(:disabled) { background: var(--bg-3); color: var(--fg); }
.tools-modal-icon:disabled { cursor: wait; opacity: 0.55; }

.refresh-tools-btn {
  transition: all var(--transition-fast);
}
.refresh-tools-btn:hover {
  color: var(--accent);
}
.refresh-tools-btn.refreshing svg {
  animation: spin 0.8s linear infinite;
}
.refresh-tools-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tool-row {
  display: grid;
  grid-template-columns: minmax(154px, 0.7fr) minmax(260px, 1.7fr) 74px 52px;
  align-items: center;
  column-gap: 14px;
  min-height: 50px;
  padding: 0 12px;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  transition: background var(--transition-fast), opacity var(--transition-fast);
}
.tool-row:last-child { border-bottom: none; }
.tool-row:hover { background: var(--bg-2); }
.tool-row.disabled {
  opacity: 0.7;
}

.tool-row-info {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  min-width: 0;
  color: inherit;
  cursor: pointer;
  text-align: left;
}
.tool-row-info code {
  overflow: hidden;
  padding: 3px 6px;
  border-radius: var(--r-sm);
  background: var(--bg-3);
  font-weight: 600;
  color: var(--fg-2);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tool-row:hover .tool-row-info code { color: var(--accent); }

.tool-disabled-state {
  flex-shrink: 0;
  color: var(--fg-4);
  font-size: 11px;
}

.tool-row-desc {
  color: var(--fg-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-row-actions {
  display: contents;
}

/* 切换开关 */
.tool-toggle-btn {
  background: none;
  border: none;
  cursor: pointer;
  justify-self: center;
  padding: 3px;
  display: flex;
  align-items: center;
}
.tool-toggle-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.tool-toggle-btn .toggle-track {
  width: 34px;
  height: 18px;
  background: var(--fg-4);
  border: 1px solid var(--border);
  border-radius: 9px;
  position: relative;
  transition: all var(--transition-fast);
}
.tool-toggle-btn.enabled .toggle-track {
  background: var(--success);
  border-color: var(--success);
}
.tool-toggle-btn .toggle-thumb {
  width: 14px;
  height: 14px;
  background: white;
  border-radius: 50%;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: all var(--transition-fast);
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}
.tool-toggle-btn.enabled .toggle-thumb {
  left: 18px;
}

/* 自动放行开关 */
.tool-toggle-btn.auto-toggle .toggle-track.auto-track {
  width: 34px;
  height: 18px;
}
.tool-toggle-btn.auto-toggle.enabled .toggle-track.auto-track {
  background: var(--brand-primary);
  border-color: var(--brand-primary);
}
.tool-toggle-btn.auto-toggle .toggle-thumb {
  width: 14px;
  height: 14px;
  top: 2px;
  left: 2px;
}
.tool-toggle-btn.auto-toggle.enabled .toggle-thumb {
  left: 18px;
}

/* 工具弹窗 body 限制高度 */
.tool-modal-body {
  max-height: none;
  overflow-y: auto;
  padding: 14px 18px 18px;
  background: var(--bg-2);
}

/* 筛选栏 */
.tool-filter-bar {
  display: flex;
  gap: 2px;
  width: fit-content;
  padding: 3px;
  margin-bottom: 12px;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--bg);
}
.tool-filter-btn {
  min-height: 28px;
  padding: 3px 10px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  font-size: 12px;
  color: var(--fg-3);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.tool-filter-btn:hover {
  background: var(--bg-3);
  color: var(--accent);
}
.tool-filter-btn.active {
  background: color-mix(in srgb, var(--accent) 10%, var(--bg));
  color: var(--accent);
  font-weight: 600;
}

.tool-list {
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--bg);
}

.tool-list-head {
  display: grid;
  grid-template-columns: minmax(154px, 0.7fr) minmax(260px, 1.7fr) 74px 52px;
  column-gap: 14px;
  align-items: center;
  min-height: 34px;
  padding: 0 12px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-3);
  color: var(--fg-3);
  font-size: 11px;
  font-weight: 600;
}

.tool-list-head span:nth-child(n+3) { text-align: center; }

@media (max-width: 700px) {
  .modal-tools { width: min(96vw, 620px); }
  .tool-row,
  .tool-list-head { grid-template-columns: minmax(120px, 0.8fr) minmax(120px, 1.2fr) 62px 48px; column-gap: 8px; }
  .tool-row { padding: 0 8px; }
  .tool-list-head { padding: 0 8px; }
  .tool-filter-bar { width: 100%; overflow-x: auto; }
  .tool-filter-btn { flex: 1 0 auto; }
}

.config-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.config-row:last-child { border-bottom: none; }
.config-key {
  font-family: var(--mono);
  font-weight: 500;
  color: var(--fg-2);
}
.config-val {
  font-family: var(--mono);
  color: var(--fg-3);
  word-break: break-all;
  text-align: right;
  max-width: 60%;
}

/* 提示词 Markdown 渲染样式 */
.prompt-rendered {
  font-size: 14px;
  line-height: 1.7;
  color: var(--fg);
  overflow-x: hidden;
}

.prompt-rendered h1, .prompt-rendered h2, .prompt-rendered h3, .prompt-rendered h4 {
  margin: 0.8em 0 0.4em;
  font-weight: 600;
}

.prompt-rendered h1 {
  font-size: 1.4em;
}

.prompt-rendered h2 {
  font-size: 1.25em;
}

.prompt-rendered h3 {
  font-size: 1.1em;
}

.prompt-rendered p {
  margin: 0.6em 0;
}

.prompt-rendered ul, .prompt-rendered ol {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.prompt-rendered li {
  margin: 0.25em 0;
}

.prompt-rendered pre {
  margin: 0.8em 0;
  padding: 12px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  overflow-x: auto;
}

.prompt-rendered code {
  font-family: var(--mono);
  font-size: 12px;
}

.prompt-rendered pre code {
  background: none;
  padding: 0;
}

.prompt-rendered strong {
  font-weight: 600;
}

.prompt-rendered a {
  color: var(--accent);
  text-decoration: none;
}

.prompt-rendered a:hover {
  text-decoration: underline;
}

.prompt-rendered blockquote {
  margin: 0.6em 0;
  padding: 0.6em 1em;
  border-left: 3px solid var(--accent);
  background: var(--bg-3);
  border-radius: 0 var(--r) var(--r) 0;
}

.prompt-rendered table {
  border-collapse: collapse;
  width: 100%;
  margin: 0.6em 0;
}

.prompt-rendered th, .prompt-rendered td {
  border: 1px solid var(--border);
  padding: 8px 12px;
  text-align: left;
}

.prompt-rendered hr {
  margin: 1em 0;
  border: none;
  border-top: 1px solid var(--border);
}

/* ==================== 版本更新弹窗 ==================== */
:global(.update-modal-mask) {
  position: fixed;
  inset: 0;
  z-index: 10000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

:global(.update-modal) {
  width: 520px;
  max-width: 90vw;
  background: var(--bg);
  border-radius: 14px;
  box-shadow: 0 25px 80px rgba(0, 0, 0, 0.35);
  overflow: hidden;
}

:global(.update-modal-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--border);
}

:global(.update-modal-title) {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--fg);
}

:global(.update-modal-title svg) {
  color: var(--accent);
}

:global(.update-modal-body) {
  padding: 24px;
}

/* 版本双栏卡片 */
:global(.update-versions-grid) {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 20px;
}

:global(.update-version-card) {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 10px;
  transition: border-color 0.2s;
}

:global(.update-version-card:hover) {
  border-color: var(--accent);
}

:global(.uvc-icon) {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-3);
  border-radius: 8px;
  color: var(--fg-2);
  flex-shrink: 0;
}

:global(.uvc-info) {
  flex: 1;
  min-width: 0;
}

:global(.uvc-name) {
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-3);
  margin-bottom: 2px;
}

:global(.uvc-version) {
  font-size: 13px;
  font-weight: 700;
  color: var(--fg);
  font-family: var(--font-mono);
  letter-spacing: -0.3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.uvc-status) {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}

:global(.uvc-status.ok) {
  background: rgba(34, 197, 94, 0.12);
  color: #22c55e;
}

:global(.uvc-status.warn) {
  background: rgba(255, 159, 28, 0.12);
  color: #ff9f1c;
}

/* 最新版本行 */
:global(.update-latest-row) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.06), rgba(168, 85, 247, 0.06));
  border: 1px solid rgba(99, 102, 241, 0.15);
  border-radius: 10px;
  margin-bottom: 16px;
}

:global(.ulr-label) {
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-3);
  flex-shrink: 0;
}

:global(.ulr-version) {
  font-size: 18px;
  font-weight: 700;
  color: var(--fg);
  font-family: var(--font-mono);
  letter-spacing: -0.5px;
}

:global(.ulr-version.has-update) {
  color: var(--accent);
}

:global(.ulr-link) {
  margin-left: auto;
  font-size: 12px;
  color: var(--accent);
  text-decoration: none;
  font-weight: 500;
  flex-shrink: 0;
  opacity: 0.8;
  transition: opacity 0.2s;
}

:global(.ulr-link:hover) {
  opacity: 1;
  text-decoration: underline;
}

/* 更新命令 */
:global(.update-commands) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

:global(.uc-label) {
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-3);
}

:global(.uc-list) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

:global(.uc-item) {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}

:global(.uc-item:hover) {
  border-color: var(--accent);
  background: var(--bg-3);
}

:global(.uc-item code) {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--fg-2);
  user-select: all;
}

:global(.uc-badge) {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  flex-shrink: 0;
  letter-spacing: 0.3px;
}

:global(.uc-badge.win) {
  background: rgba(0, 120, 215, 0.15);
  color: #0078d7;
}

:global(.uc-badge.unix) {
  background: rgba(51, 51, 51, 0.12);
  color: var(--fg-2);
}

:global(.update-modal-foot) {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  padding: 14px 24px;
  border-top: 1px solid var(--border);
  background: var(--bg-1);
}

[data-theme="dark"] :global(.update-modal-mask) {
  background: rgba(0, 0, 0, 0.6);
}

[data-theme="dark"] :global(.update-modal) {
  border: 1px solid var(--border);
}

/* ==================== 元素面板 ==================== */
.element-panel-wrapper {
  position: fixed;
  top: 36px;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border-left: 1px solid var(--glass-border);
  box-shadow: -4px 0 20px rgba(0, 0, 0, 0.1);
  transition: width 0.05s;
}

.element-panel-wrapper.dragging {
  transition: none !important;
}

/* 拖拽手柄 */
.element-resize-handle {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  z-index: 10;
  cursor: ew-resize;
  background: transparent;
  transition: background 0.15s;
}

.element-resize-handle:hover,
.element-panel-wrapper.dragging .element-resize-handle {
  background: var(--accent);
  opacity: 0.5;
}

.element-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--glass-border);
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  min-height: 36px;
}

.element-panel-header .btn-icon-sm {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: none;
  color: var(--fg-4);
  cursor: pointer;
  border-radius: var(--r);
  transition: all 0.12s;
}

.element-panel-header .btn-icon-sm:hover {
  background: var(--bg-3);
  color: var(--fg);
}

/* ==================== 核心服务更新弹窗 ==================== */
.core-service-update-info {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: var(--bg-2);
  border-radius: var(--r);
  margin-bottom: 16px;
}

.core-service-update-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--r);
  background: var(--accent-bg);
  color: var(--accent);
  flex-shrink: 0;
}

.core-service-update-text {
  flex: 1;
  min-width: 0;
}

.core-service-update-desc {
  font-size: 13px;
  color: var(--fg-2);
  line-height: 1.5;
  margin: 0;
}

.core-service-update-desc strong {
  color: var(--fg);
  font-weight: 600;
  font-family: var(--font-mono);
  font-size: 12px;
}

.core-service-update-versions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.version-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: var(--bg-2);
  border-radius: var(--r);
}

.version-label {
  font-size: 13px;
  color: var(--fg-3);
}

.version-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--r-sm);
  font-family: var(--font-mono);
}

.version-badge.version-new {
  background: var(--green-bg);
  color: var(--green);
}

.version-badge.version-old {
  background: var(--yellow-bg);
  color: var(--yellow);
}

.btn-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* ==================== 安装日志 ==================== */
.install-log-container {
  margin-top: 16px;
}

.install-log {
  max-height: 240px;
  overflow-y: auto;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 12px;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.6;
}

.log-line {
  color: var(--fg-3);
  white-space: pre-wrap;
  word-break: break-all;
}

.log-placeholder {
  color: var(--fg-4);
  font-style: italic;
}

/* ==================== 更新成功提示 ==================== */
.update-success-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: var(--green-bg);
  border-radius: var(--r);
  margin-top: 16px;
  color: var(--green);
  font-size: 13px;
  font-weight: 500;
}

.update-success-tip svg {
  flex-shrink: 0;
}
</style>
