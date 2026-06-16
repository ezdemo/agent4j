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
      :hasNewVersion="hasNewVersion"
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
        style="flex:1;min-height:0"
        @session-updated="loadSessions"
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
    <Teleport to="body">
      <div v-if="showWorkspacePicker" class="modal-mask" @click.self="showWorkspacePicker = false">
        <div class="modal">
          <div class="modal-head">
            <span>项目管理</span>
            <button class="btn-icon-sm" @click="showWorkspacePicker = false">×</button>
          </div>
          <div class="modal-body">
            <div class="workspace-list">
              <div v-if="workspaces.length === 0" class="modal-empty">暂无项目记录</div>
              <div
                v-for="w in workspaces"
                :key="w.hash"
                class="workspace-item"
                :class="{ active: w.hash === currentSessionWorkspace }"
                @click="handleSwitchWorkspace(w.hash)"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                </svg>
                <div class="workspace-info">
                  <div class="workspace-item-name">{{ w.name }}</div>
                  <div class="workspace-item-path">{{ w.path }}</div>
                </div>
                <span class="workspace-item-count">{{ w.sessionCount }}</span>
                <button class="btn-icon-sm workspace-del" @click.stop="handleDeleteWorkspace(w.hash)" title="删除">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                </button>
              </div>
            </div>
            <div class="workspace-add">
              <input 
                ref="workspacePathInput"
                v-model="newWorkspacePath" 
                placeholder="输入新项目路径..."
                @keyup.enter="handleAddWorkspace"
              />
              <input
                ref="folderPicker"
                type="file"
                webkitdirectory
                style="display:none"
                @change="onFolderPicked"
              />
              <button v-if="isDesktopEnv" class="btn-icon-sm" title="选择文件夹（仅桌面端）" @click="openFolderPicker">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                </svg>
              </button>
              <button class="btn-icon-sm" @click="handleAddWorkspace" :disabled="!newWorkspacePath.trim()">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

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
          <div class="modal-body">
            <div v-for="t in tools" :key="t.name" class="tool-row">
              <code>{{ t.name }}</code>
              <span>{{ t.description }}</span>
            </div>
            <div v-if="!tools.length" class="modal-empty">加载中...</div>
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
            <SettingsView @auto-update="handleAutoUpdate" />
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
            <span>一键{{ hasNewVersion ? '更新' : '重装' }}</span>
            <button class="btn-icon-xs" @click="showUpdateModal = false">×</button>
          </div>
          <div class="update-modal-body">
            <p class="update-modal-desc">在终端中执行以下命令即可完成{{ hasNewVersion ? '更新' : '重装' }}：</p>

            <div class="update-platform">
              <div class="update-platform-label">Windows（PowerShell）：</div>
              <div class="update-code-block">
                <code>irm https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.ps1 | iex</code>
                <button class="update-copy-btn" @click="copyText('irm https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.ps1 | iex')" title="复制">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                </button>
              </div>
            </div>

            <div class="update-platform">
              <div class="update-platform-label">macOS / Linux：</div>
              <div class="update-code-block">
                <code>curl -fsSL https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.sh | bash</code>
                <button class="update-copy-btn" @click="copyText('curl -fsSL https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.sh | bash')" title="复制">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                </button>
              </div>
            </div>
          </div>
          <div class="update-modal-foot">
            <button class="btn btn-secondary" :disabled="autoUpdating" @click="handleAutoUpdate">
              <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
                <path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3"/>
              </svg>
              {{ autoUpdating ? '正在创建会话...' : '自动更新' }}
            </button>
            <button class="btn" @click="showUpdateModal = false">关闭</button>
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
import {useAppStore} from './stores/app'
import {agentAPI, configAPI, sessionsAPI, systemAPI, toolsAPI} from './services/api'
import SetupScreen from './components/SetupScreen.vue'
import TitleBar from './components/TitleBar.vue'
import SplashScreen from './components/SplashScreen.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
import Sidebar from './components/Sidebar.vue'
import RightPanel from './components/RightPanel.vue'
import ElementPanel from './components/ElementPanel.vue'
import ChatView from './views/Chat.vue'
import SettingsView from './views/Settings.vue'
import DashboardPanel from './components/Dashboard.vue'
import { platform } from '@/services/platform'

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
const showUpdateModal = ref(false)
const autoUpdating = ref(false)

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
  return md.parse(c)
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
const newWorkspacePath = ref('')
const workspacePathInput = ref(null)
const folderPicker = ref(null)

// 打开文件夹选择器（桌面端用原生对话框，浏览器用输入框）
const openFolderPicker = async () => {
  if (platform.isElectron) {
    try {
      // Electron 环境：使用原生对话框（通过 IPC）
      const result = await window.electronAPI.agent4jWebService.pickFolder()
      if (result) {
        newWorkspacePath.value = result
      }
    } catch (e) {
      console.error('选择文件夹失败:', e)
    }
  } else {
    // 浏览器环境：聚焦输入框让用户手动输入
    workspacePathInput.value?.focus()
  }
}

// 文件夹选中回调（浏览器回退方案）
const onFolderPicked = (e) => {
  const files = e.target.files
  if (files && files.length > 0) {
    newWorkspacePath.value = files[0].webkitRelativePath.split('/')[0] || ''
  }
  e.target.value = ''
}

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
  return m ? `${m[2]}/${m[3]} ${m[4]}:${m[5]}` : n.replace(/[-_]+/g, ' ').slice(0, 24)
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
const handleAddWorkspace = async () => {
  const path = newWorkspacePath.value.trim()
  if (!path) return
  
  try {
    const r = await configAPI.switchWorkspace(path)
    if (r.success) {
      workspace.value = r.data.workspace
      newWorkspacePath.value = ''
      showWorkspacePicker.value = false
      await loadWorkspaces()
      await loadSessions()
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

// 获取版本信息
async function fetchVersionInfo() {
  try {
    const res = await systemAPI.getCurrentVersion()
    if (res.success && res.data) {
      appVersion.value = res.data.version || ''
    }
  } catch { /* 版本获取失败静默处理 */ }
  try {
    const checkRes = await systemAPI.checkLatestVersion()
    if (checkRes.success && checkRes.data) {
      hasNewVersion.value = checkRes.data.hasNewVersion
      if (!appVersion.value) {
        appVersion.value = checkRes.data.currentVersion || ''
      }
    }
  } catch { /* 版本检查失败静默处理 */ }
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

// 自动更新：选择工作区 → 创建新会话 → 发送更新命令
const handleAutoUpdate = async () => {
  autoUpdating.value = true
  try {
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
      return
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
      message.success('已新建更新会话')
    } else {
      message.error('新建会话失败')
      return
    }

    // 4. 关闭设置和更新弹窗
    showSettings.value = false
    showUpdateModal.value = false

    // 5. 稍等一帧让 UI 刷新，然后发送更新命令
    await new Promise(resolve => requestAnimationFrame(() => setTimeout(resolve, 300)))

    const updateCommand = "请帮我执行 Agent4j 自动更新。根据当前操作系统平台，选择并运行对应的更新脚本：\n\n- Windows 系统：在 PowerShell 中运行 `irm https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.ps1 | iex`\n- macOS / Linux 系统：在终端中运行 `curl -fsSL https://raw.giteeusercontent.com/ezdemo/agent4j/raw/main/.release/setup.sh | bash`\n\n请先判断当前系统平台，然后执行对应的脚本。执行完成后请报告结果。"
    await chatRef.value?.sendCommand(updateCommand)
  } catch (e) {
    console.error('自动更新失败:', e)
    message.error('自动更新失败: ' + (e.message || '未知错误'))
  } finally {
    autoUpdating.value = false
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

/* 工作区选择弹窗 */
.workspace-list {
  margin-bottom: 8px;
}

.modal .workspace-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  border-radius: var(--r);
  transition: background var(--t);
}
.modal .workspace-item:hover {
  background: var(--bg-2);
}
.modal .workspace-item.active {
  background: var(--accent-bg);
}
.modal .workspace-item svg {
  color: var(--fg-3);
  flex-shrink: 0;
}
.modal .workspace-item .workspace-info {
  flex: 1;
  min-width: 0;
}
.modal .workspace-item .workspace-item-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.modal .workspace-item .workspace-item-path {
  font-size: 11px;
  color: var(--fg-4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.modal .workspace-item .workspace-item-count {
  font-size: 11px;
  color: var(--fg-3);
  background: var(--bg-3);
  padding: 1px 5px;
  border-radius: var(--r-sm);
}
.modal .workspace-item .workspace-del {
  opacity: 0;
  transition: opacity var(--t);
}
.modal .workspace-item:hover .workspace-del {
  opacity: 1;
}
.modal .workspace-item .workspace-del:hover {
  color: var(--red);
}

.modal .workspace-add {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 0 0;
  border-top: 1px solid var(--border);
}
.modal .workspace-add input {
  flex: 1;
  padding: 5px 8px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  font-size: 12px;
  color: var(--fg);
}
.modal .workspace-add input:focus {
  outline: none;
  border-color: var(--accent);
}
.modal .workspace-add input::placeholder {
  color: var(--fg-4);
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
  align-items: baseline;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.tool-row:last-child { border-bottom: none; }
.tool-row code {
  font-weight: 600;
  color: var(--accent);
  flex-shrink: 0;
  min-width: 100px;
}
.tool-row span { color: var(--fg-3); }

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
  width: 600px;
  max-width: 90vw;
  background: var(--bg);
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
}

:global(.update-modal-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  font-size: 15px;
  font-weight: 600;
  color: var(--fg);
}

:global(.update-modal-body) {
  padding: 20px;
}

:global(.update-modal-desc) {
  font-size: 13px;
  color: var(--fg-3);
  margin: 0 0 16px;
}

:global(.update-platform) {
  margin-bottom: 16px;
}

:global(.update-platform:last-child) {
  margin-bottom: 0;
}

:global(.update-platform-label) {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
  margin-bottom: 6px;
}

:global(.update-code-block) {
  display: flex;
  align-items: center;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 10px 12px;
  gap: 8px;
}

:global(.update-code-block code) {
  flex: 1;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--fg);
  word-break: break-all;
  line-height: 1.5;
  user-select: all;
}

:global(.update-copy-btn) {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-3);
  transition: all var(--t);
  cursor: pointer;
  border: none;
  background: transparent;
}

:global(.update-copy-btn:hover) {
  background: var(--bg-3);
  color: var(--accent);
}

:global(.update-modal-foot) {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--border);
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
</style>
