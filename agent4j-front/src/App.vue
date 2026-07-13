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
      @openSettings="showSettings = true"
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
      @show-workspace-picker="showWorkspacePicker = true"
      @refresh-sessions="refreshSessionList"
      @new-project-chat="newProjectChat"
      @refresh-project="refreshProjectSessions"
      @clear-project="clearProjectSessions"
      @select-session="onSidebarSelectSession"
      @refresh-session-chat="refreshSessionChat"
      @delete-session="onSidebarDeleteSession"
      @toggle-theme="toggleTheme"
      @show-tools="showTools = true"
      @show-dashboard="showDashboard = true"
      @show-settings="showSettings = true"
    />

    <!-- 主区域 -->
    <main class="main">
      <ChatView 
        ref="chatRef" 
        hide-header 
        :workspace-hash="currentSessionWorkspace"
        :session-name="currentSession"
        :version="appVersion"
        :connected="!showSetup"
        style="flex:1;min-height:0"
        @session-updated="loadSessions"
        @session-branched="onSessionBranched"
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

    <!-- 工作区选择弹窗 -->
    <WorkspacePickerModal
      v-model:show="showWorkspacePicker"
      :workspaces="workspaces"
      :current-session-workspace="currentSessionWorkspace"
      :is-desktop-env="isDesktopEnv"
      @switch-workspace="handleSwitchWorkspace"
      @add-workspace="handleAddWorkspace"
      @delete-workspace="handleDeleteWorkspace"
    />

    <!-- 工具弹窗 -->
    <Teleport to="body">
      <div v-if="showTools" class="modal-mask" @click.self="showTools = false">
        <div class="modal">
          <div class="modal-head">
            <span>工具列表</span>
            <div class="modal-head-actions">
              <button
                class="btn-icon-sm refresh-tools-btn"
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
              <button class="btn-icon-sm" @click="showTools = false">×</button>
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
                @click="toolFilter = f.value"
              >{{ f.label }}</button>
            </div>
            <div v-if="filteredTools.length === 0" class="modal-empty">暂无工具</div>
            <div v-for="t in filteredTools" :key="t.name" class="tool-row" :class="{ disabled: !t.enabled }">
              <div class="tool-row-info" @click="toggleTool(t)">
                <code>{{ t.name }}</code>
                <span class="tool-row-desc">{{ t.description }}</span>
              </div>
              <div class="tool-row-actions">
                <span v-if="!t.enabled" class="tool-status-badge disabled">已禁用</span>
                <span v-if="t.autoApproved" class="tool-status-badge auto-approved">自动放行</span>
                <button
                  class="tool-toggle-btn"
                  :class="{ enabled: t.enabled }"
                  :disabled="refreshingTools"
                  @click.stop="toggleTool(t)"
                  :title="t.enabled ? '禁用' : '启用'">
                  <div class="toggle-track">
                    <div class="toggle-thumb"></div>
                  </div>
                </button>
                <button
                  class="tool-toggle-btn auto-toggle"
                  :class="{ enabled: t.autoApproved }"
                  :disabled="refreshingTools"
                  @click.stop="toggleAutoTool(t)"
                  title="自动放行">
                  <div class="toggle-track auto-track">
                    <div class="toggle-thumb"></div>
                  </div>
                </button>
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
            <SettingsView @auto-update="handleAutoUpdate" @init-pet="handleInitPet" />
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 数据面板弹窗 -->
    <Teleport to="body">
      <div v-if="showDashboard" class="modal-mask" @click.self="showDashboard = false">
        <div class="modal modal-dashboard">
          <div class="modal-head">
            <span>数据面板</span>
            <button class="btn-icon-sm" @click="showDashboard = false">×</button>
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
import ChatView from './views/Chat.vue'
import SettingsView from './views/Settings.vue'
import DashboardPanel from './components/Dashboard.vue'
import {platform} from '@/services/platform'

const store = useAppStore()
const router = useRouter()
const { confirm } = useConfirm()

// 主题：统一从 Pinia store 读写，确保设置页和主页一致
const theme = computed({ get: () => store.settings.theme, set: (v) => { store.settings.theme = v } })
const sideOpen = ref(true)
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
const showDashboard = ref(false)
const rightPanelOpen = ref(false)
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
    localStorage.setItem('agent4j-element-panel-width', String(elementPanelWidth.value))
  } catch { /* ignore */ }
}

// 加载保存的元素面板宽度
onMounted(() => {
  try {
    const saved = localStorage.getItem('agent4j-element-panel-width')
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
const workspaces = ref([])

// 按工作区 hash 分组的会话
const workspaceSessions = ref({})

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

const themeOrder = ['light', 'dark', 'retro', 'retro-yellow']
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
  console.log('Agent4j Web service is ready')
  showSetup.value = false
  loadData()
  startHeartbeat()
}

// 服务错误回调
const onSplashError = (error) => {
  console.error('Agent4j Web service error:', error)
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
  await loadSessions()
  initialDataLoaded.value = true
}

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
    if (r.success) workspaces.value = r.data || []
  } catch (e) {
    console.error('加载工作区列表失败:', e)
  }
}

// 切换工作区（仅持久化上下文，不新建会话）
const switchWorkspaceContext = async (hash) => {
  const ws = workspaces.value.find(w => w.hash === hash)
  if (!ws) return
  await configAPI.switchWorkspace(ws.path)
  workspace.value = ws.path
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
      message.success('已添加工作区')
    } else {
      message.error(r.message || '添加工作区失败')
    }
  } catch (e) {
    message.error('添加工作区失败: ' + e.message)
  }
}

// 删除工作区
const handleDeleteWorkspace = async (hash) => {
  const ok = await confirm({ message: '确定要删除此工作区吗？' })
  if (!ok) return
  
  try {
    const r = await configAPI.deleteWorkspace(hash)
    if (r.success) {
      await loadWorkspaces()
      message.success('工作区已删除')
    } else {
      message.error(r.message || '删除工作区失败')
    }
  } catch (e) {
    message.error('删除工作区失败: ' + e.message)
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
  const ws = workspaces.value.find(w => w.hash === hash)
  const name = ws ? ws.name : hash
  const ok = await confirm({ message: `确定要清空「${name}」的所有会话吗？此操作不可恢复。` })
  if (!ok) return
  try {
    await sessionsAPI.clearAll(hash)
    await loadSessions()
    if (currentSessionWorkspace.value === hash) {
      currentSession.value = ''
      chatRef.value?.resetLocalMessages()
    }
    message.success('会话已清空')
  } catch (e) {
    message.error('清空会话失败: ' + e.message)
  }
}

// Sidebar 事件：选择会话
const onSidebarSelectSession = ({ workspaceHash, sessionName }) => {
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
  localStorage.removeItem('agent4j-port')
  if (isDesktopEnv.value) {
    // 仅桌面环境清除 api-base（端口每次启动都变，由 SplashScreen 重新检测）
    localStorage.removeItem('agent4j-api-base')
  }
  console.log('[App] Cleared stale port from localStorage')

  // 异步获取版本信息（不阻塞启动）
  fetchVersionInfo()

  // 监听从 ChatMessage 发出的「在元素界面打开」事件
  window.addEventListener('agent4j:open-in-element', onOpenInElement)
})

onBeforeUnmount(() => {
  stopHeartbeat()
  window.removeEventListener('agent4j:open-in-element', onOpenInElement)
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
    const result = await window.electronAPI.agent4jWebService.installOnline()
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
  const url = 'https://gitee.com/ezdemo/agent4j/releases/latest'
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
    workspaces.value = wsRes.data
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
    const updateCommand = "请帮我执行 Agent4j 自动更新。根据当前操作系统平台，选择并运行对应的更新脚本：\n\n- Windows 系统：在 PowerShell 中运行 `irm https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.ps1 | iex`\n- macOS / Linux 系统：在终端中运行 `curl -fsSL https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.sh | bash`\n\n请先判断当前系统平台，然后执行对应的脚本。执行完成后请报告结果。"
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
  border-radius: 10px;
  border: 1px solid var(--border-2);
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
  background: var(--bg);
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
  width: min(900px, 95vw);
  max-height: 85vh;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  transition: opacity var(--transition-fast);
}
.tool-row:last-child { border-bottom: none; }
.tool-row.disabled {
  opacity: 0.55;
}
.tool-row.disabled code {
  text-decoration: line-through;
}

.tool-row-info {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex: 1;
  min-width: 0;
  cursor: default;
}
.tool-row-info code {
  font-weight: 600;
  color: var(--accent);
  flex-shrink: 0;
  min-width: 100px;
}
.tool-row-desc {
  color: var(--fg-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.tool-status-badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--danger-bg);
  color: var(--danger);
  white-space: nowrap;
}

/* 切换开关 */
.tool-toggle-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px;
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
  background: var(--bg-tertiary);
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
  width: 28px;
  height: 16px;
}
.tool-toggle-btn.auto-toggle.enabled .toggle-track.auto-track {
  background: var(--brand-primary);
  border-color: var(--brand-primary);
}
.tool-toggle-btn.auto-toggle .toggle-thumb {
  width: 12px;
  height: 12px;
  top: 2px;
  left: 2px;
}
.tool-toggle-btn.auto-toggle.enabled .toggle-thumb {
  left: 14px;
}
.tool-status-badge.auto-approved {
  background: var(--accent-soft, #e8f4fd);
  color: var(--brand-primary, #3b82f6);
}

/* 工具弹窗 body 限制高度 */
.tool-modal-body {
  max-height: 60vh;
  overflow-y: auto;
}

/* 筛选栏 */
.tool-filter-bar {
  display: flex;
  gap: 6px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 4px;
}
.tool-filter-btn {
  background: none;
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 3px 10px;
  font-size: 12px;
  color: var(--fg-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
}
.tool-filter-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.tool-filter-btn.active {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
  font-weight: 600;
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
