<template>
  <div class="splash-screen" v-if="visible">
    <!-- 关闭按钮 -->
    <button class="close-btn" @click="closeApp" title="关闭程序">✕</button>
    <div class="splash-content" ref="contentRef">
      <!-- Logo -->
      <div class="logo-container">
        <img src="@/assets/logo.svg" alt="Loopra" class="logo" />
      </div>

      <h1 class="title">Loopra</h1>
      <p class="subtitle">智能 AI 代码助手</p>

      <!-- 已安装且最新：直接显示启动中 -->
      <template v-if="phase === 'ready'">
        <div class="status-bar status-ready">
          <span class="status-dot"></span>
          <span>服务已就绪</span>
        </div>
      </template>

      <!-- Web 环境：跳过安装 -->
      <template v-else-if="phase === 'waiting'">
        <div class="status-bar status-connecting">
          <span class="status-dot"></span>
          <span>正在连接服务...</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: '60%' }"></div>
        </div>
      </template>

      <!-- 等待用户确认安装 -->
      <template v-else-if="phase === 'confirm'">
        <div class="install-info">
          <div class="info-icon">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
          </div>
          <p class="info-text" v-if="installReason === 'not_installed'">
            首次运行，需要安装 Loopra 桌面运行时
          </p>
          <p class="info-text" v-else-if="installReason === 'version_mismatch'">
            检测到运行时版本较旧，需要更新桌面运行时
          </p>
          <p class="info-text" v-else-if="installReason === 'desktop_outdated'">
            当前运行时版本较新，是否前往 GitHub 更新桌面版？
          </p>
          <p class="version-details" v-if="installReason === 'version_mismatch' || installReason === 'desktop_outdated'">
            桌面端 {{ desktopVersion }} · 当前运行时 {{ runtimeVersion || '未知' }}
          </p>
          <p class="info-text" v-if="installReason !== 'not_installed' && installReason !== 'version_mismatch' && installReason !== 'desktop_outdated'">
            需要重新安装 Loopra 桌面运行时
          </p>

          <!-- 下载源选择：GitHub 直连 / 镜像加速，与更新窗口共享 localStorage -->
          <div class="install-source" v-if="installReason !== 'desktop_outdated'">
            <span>选择下载方式</span>
            <div class="source-row">
              <label class="source-option" :class="{ active: updateSource === UPDATE_SOURCE_NORMAL }">
                <input v-model="updateSource" type="radio" :value="UPDATE_SOURCE_NORMAL" />
                <span class="source-name">GitHub 直连</span>
                <span class="source-desc">官方发布源</span>
              </label>
              <label class="source-option" :class="{ active: updateSource === UPDATE_SOURCE_MIRROR }">
                <input v-model="updateSource" type="radio" :value="UPDATE_SOURCE_MIRROR" />
                <span class="source-name">镜像下载</span>
                <span class="source-desc">gh-proxy 加速</span>
              </label>
            </div>
          </div>

          <div class="install-command" v-if="installReason !== 'desktop_outdated'">
            <span>将执行以下安装命令：</span>
            <code>{{ installCommand }}</code>
          </div>

          <div class="java-check" v-if="javaInfo && installReason !== 'desktop_outdated'">
            <span class="java-badge">✓</span>
            <span class="java-text">{{ javaInfo }}</span>
          </div>

          <div class="confirm-actions">
            <button v-if="installReason === 'desktop_outdated'" class="btn btn-primary btn-lg install-btn" @click="openDesktopUpdate">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 3h7v7"/>
                <path d="M10 14 21 3"/>
                <path d="M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5"/>
              </svg>
              更新桌面版
            </button>
            <button v-else class="btn btn-primary btn-lg install-btn" @click="startInstall">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              {{ installReason === 'version_mismatch' ? '更新运行时' : '开始安装' }}
            </button>
            <button v-if="installReason === 'version_mismatch' || installReason === 'desktop_outdated'" class="btn btn-secondary btn-lg" @click="skipRuntimeUpdate">
              {{ installReason === 'desktop_outdated' ? '继续使用' : '暂不更新' }}
            </button>
            <button class="btn btn-secondary btn-lg" @click="closeApp">
              退出
            </button>
          </div>
        </div>
      </template>

      <!-- 安装进行中（在线安装） -->
      <template v-else-if="phase === 'installing'">
        <div class="status-bar status-connecting">
          <span class="status-dot"></span>
          <span>{{ installLogs.length === 0 ? '正在安装...' : '安装中，请稍候...' }}</span>
        </div>

        <!-- 安装日志控制台 -->
        <div class="install-log" ref="logContainer">
          <div v-for="(line, i) in installLogs" :key="i" class="log-line">
            {{ line }}
          </div>
          <div v-if="installLogs.length === 0" class="log-line log-placeholder">等待输出...</div>
        </div>

        <div class="progress-bar">
          <div class="progress-fill progress-indeterminate"></div>
        </div>
      </template>

      <!-- 启动服务中 -->
      <template v-else-if="phase === 'starting'">
        <div class="status-bar status-connecting">
          <span class="status-dot"></span>
          <span>{{ startupMessage || '正在启动服务...' }}</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill progress-indeterminate"></div>
        </div>
      </template>

      <!-- 错误 -->
      <template v-else-if="phase === 'error'">
        <div class="status-bar status-error">
          <span class="status-dot"></span>
          <span>{{ errorMessage }}</span>
        </div>
        <div class="error-actions">
          <button class="btn btn-primary btn-online" @click="onlineInstall">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            一键安装
          </button>
          <button class="btn btn-secondary" @click="retry">
            重试
          </button>
          <button class="btn btn-secondary" @click="closeApp">
            退出
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import {platform} from '@/services/platform'
import {systemAPI} from '@/services/api'
import {RELEASE_LATEST_URL} from '@/utils/constants'
import {UPDATE_SOURCE_MIRROR, UPDATE_SOURCE_NORMAL, buildUpdateCommand, loadUpdateSource, saveUpdateSource} from '@/utils/updateScripts'
// 动态获取当前平台的 loopraWebService
const { loopraWebService } = platform.implementation

const emit = defineEmits(['ready', 'error'])

const visible = ref(true)
const phase = ref('checking') // checking | confirm | installing | starting | ready | waiting | error
const installReason = ref('')
const runtimeVersion = ref('')
const desktopVersion = ref('')
const javaInfo = ref('')
const errorMessage = ref('')
const startupMessage = ref('')
const resourceDir = ref('')

// 安装步骤和进度
const installSteps = ref([])
const installProgress = ref(0)

// 是否在桌面环境（Electron）中
const isDesktop = ref(false)

// 下载源（GitHub 直连 / 镜像），与更新窗口共享 localStorage
const updateSource = ref(loadUpdateSource())
watch(updateSource, (value) => saveUpdateSource(value))

// 安装命令随下载源切换（直连 setup / 镜像 setup-mirror）
const installCommand = computed(() => {
  const cmd = buildUpdateCommand(updateSource.value, platform.isElectron)
  return window.electronAPI?.platform === 'win32' ? cmd.windows : cmd.unix
})

// 在线安装日志
const installLogs = ref([])
const logContainer = ref(null)
let unlistenInstallOutput = null

// 窗口自适应：内容高度变化时通知主进程调整窗口（仅桌面环境）
const contentRef = ref(null)
let contentObserver = null
function observeContentSize() {
  const el = contentRef.value
  if (!el || !window.electronAPI?.splash?.resize) return
  contentObserver = new ResizeObserver(() => {
    window.electronAPI.splash.resize({ height: el.offsetHeight + 16 })
  })
  contentObserver.observe(el)
}

onMounted(async () => {
  await checkEnvironment()
  observeContentSize()
})

onUnmounted(() => {
  if (contentObserver) contentObserver.disconnect()
})

async function checkEnvironment() {
  phase.value = 'checking'

  // 检查运行环境
  if (platform.isElectron) {
    console.log('[Splash] Electron environment detected')
    isDesktop.value = true
  } else {
    console.log('[Splash] Web environment detected')
    isDesktop.value = false
    // Web 环境直接等待服务
    phase.value = 'waiting'
    await waitForService()
    return
  }

  // 桌面环境：获取资源目录
  try {
    const dir = await loopraWebService.getResourceDir()
    if (dir) {
      resourceDir.value = dir
    }
  } catch (e) {
    console.warn('[Splash] Failed to get resource dir:', e)
  }

  // 检查是否需要安装
  await checkInstall()
}

async function checkInstall() {
  phase.value = 'checking'

  if (!resourceDir.value) {
    // 无法获取资源目录，尝试直接启动
    console.warn('[Splash] No resource dir, trying to start directly')
    await startService()
    return
  }

  try {
    // 检查是否需要安装
    const result = await loopraWebService.checkInstallNeeded(resourceDir.value)
    console.log('[Splash] Install check:', result)

    if (!result.needed) {
      // 已安装且版本匹配，直接启动
      await startService()
      return
    }

    // 需要安装：显示确认页
    installReason.value = result.reason
    runtimeVersion.value = result.runtimeVersion || ''
    desktopVersion.value = result.desktopVersion || ''
    javaInfo.value = '准备安装'

    phase.value = 'confirm'
  } catch (e) {
    console.error('[Splash] Check install failed:', e)
    // 回退：尝试直接启动
    await startService()
  }
}

async function startInstall() {
  await onlineInstall()
}

async function skipRuntimeUpdate() {
  await startService()
}

async function openDesktopUpdate() {
  const url = RELEASE_LATEST_URL
  try {
    if (platform.isElectron && window.electronAPI?.openExternal) {
      await window.electronAPI.openExternal(url)
    } else {
      window.open(url, '_blank', 'noopener,noreferrer')
    }
  } catch {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

async function startService() {
  phase.value = 'starting'
  startupMessage.value = '正在启动 Java 服务...'

  try {
    // 1) 获取当前端口
    let port = 0
    try {
      port = await loopraWebService.getCurrentPort()
    } catch {}

    if (port > 0) {
      localStorage.setItem('loopra-port', String(port))
      localStorage.setItem('loopra-api-base', `http://127.0.0.1:${port}`)
      startupMessage.value = `Java 服务已启动，端口 ${port}，等待健康检查...`
    } else {
      // 通过 start 命令启动
      startupMessage.value = '正在启动 Java 进程...'
      const startedPort = await loopraWebService.start()
      if (startedPort > 0) {
        port = startedPort
        localStorage.setItem('loopra-port', String(port))
        localStorage.setItem('loopra-api-base', `http://127.0.0.1:${port}`)
        startupMessage.value = `Java 服务已启动，端口 ${port}，等待健康检查...`
      } else {
        // 未返回端口，使用 localStorage 缓存的端口
        port = parseInt(localStorage.getItem('loopra-port') || '0', 10)
        if (port > 0) {
          localStorage.setItem('loopra-api-base', `http://127.0.0.1:${port}`)
          startupMessage.value = `尝试连接端口 ${port}...`
        } else {
          startupMessage.value = '未找到可用端口，请检查服务是否已启动'
        }
      }
    }

    // 2) 轮询健康检查接口，等待服务完全就绪
    startupMessage.value = '轮询健康检查接口...'
    const ready = await pollHealthCheck(port, 20, 1500)

    if (ready) {
      await registerBrowserBridge()
      phase.value = 'ready'
      startupMessage.value = '服务已就绪！'
      await sleep(600)
      visible.value = false
      emit('ready')
      return
    }

    throw new Error(`服务启动超时，端口 ${port} 未响应健康检查`)
  } catch (e) {
    if (/loopra (?:not found|desktop runtime not found):/i.test(e.message || '')) {
      installReason.value = 'not_installed'
      javaInfo.value = '准备安装'
      phase.value = 'confirm'
      return
    }
    phase.value = 'error'
    errorMessage.value = e.message || '服务启动失败'
    emit('error', e)
  }
}

async function registerBrowserBridge() {
  if (!window.electronAPI?.aiBrowserWindow?.getBridgeAddress) return
  try {
    const address = await window.electronAPI.aiBrowserWindow.getBridgeAddress()
    await systemAPI.setBrowserBridge(address)
    console.log('[Splash] AI browser bridge registered:', address)
  } catch (error) {
    // 浏览器功能不可用不应阻止主服务启动；工具调用会给出明确错误。
    console.warn('[Splash] Failed to register AI browser bridge:', error)
  }
}

/**
 * 轮询健康检查接口，直到返回 OK 或超时
 * @param {number} port - 服务端口
 * @param {number} maxAttempts - 最大尝试次数
 * @param {number} intervalMs - 每次间隔（毫秒）
 * @returns {Promise<boolean>}
 */
async function pollHealthCheck(port, maxAttempts = 20, intervalMs = 1500) {
  const baseUrl = `http://127.0.0.1:${port}`
  const healthUrl = `${baseUrl}/api/system/health`

  for (let i = 1; i <= maxAttempts; i++) {
    try {
      const resp = await fetch(healthUrl, {
        method: 'GET',
        signal: AbortSignal.timeout(3000),
      })
      if (resp.ok) {
        console.log(`[Splash] Health check OK on attempt ${i}/${maxAttempts}, port ${port}`)
        startupMessage.value = `健康检查通过 (${i}/${maxAttempts})`
        return true
      }
      console.log(`[Splash] Health check HTTP ${resp.status} on attempt ${i}/${maxAttempts}`)
    } catch (e) {
      console.log(`[Splash] Health check failed on attempt ${i}/${maxAttempts}: ${e.message || e}`)
    }
    startupMessage.value = `等待服务就绪... (${i}/${maxAttempts})`
    await sleep(intervalMs)
  }

  console.error(`[Splash] Health check timed out after ${maxAttempts} attempts on port ${port}`)
  return false
}

async function waitForService() {
  // 非桌面环境：尝试连接已有服务
  // web 环境下始终使用 config.json 中的 apiBase 配置，不自行拼接端口
  try {
    const baseUrl = await loopraWebService.getBaseUrl()
    console.log('[Splash] Web environment, base URL:', baseUrl || '(同域)')

    // 尝试连接服务
    const ready = await loopraWebService.waitForReady(3, 1500)
    if (ready) {
      phase.value = 'ready'
      await sleep(500)
      visible.value = false
      emit('ready')
      return
    }

    // 服务不可达，显示错误
    const hint = baseUrl
      ? `请确认 ${baseUrl}/api/system/health 可访问`
      : '请确认后端服务已启动并在同域下提供 /api 接口'
    phase.value = 'error'
    errorMessage.value = `无法连接服务。${hint}`
  } catch (e) {
    phase.value = 'error'
    errorMessage.value = e.message || '连接服务失败'
  }
}

function retry() {
  checkEnvironment()
}

// 在线一键安装
async function onlineInstall() {
  phase.value = 'installing'
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
    console.warn('[Splash] Failed to listen install output:', e)
  }

  try {
    await loopraWebService.installOnline(updateSource.value)
    // 安装成功，启动服务
    installLogs.value.push('')
    installLogs.value.push('✅ 安装完成，正在启动服务...')
    await startService()
  } catch (e) {
    phase.value = 'error'
    errorMessage.value = `安装失败: ${e.message || e}`
  } finally {
    if (unlistenInstallOutput) {
      unlistenInstallOutput()
      unlistenInstallOutput = null
    }
  }
}

async function closeApp() {
  // 使用平台抽象层关闭窗口
  try {
    await platform.implementation.window.close()
    return
  } catch (e) {
    console.warn('[Splash] Failed to close window via platform API:', e)
  }
  // 浏览器环境
  window.close()
  setTimeout(() => {
    alert('请手动关闭此浏览器标签页')
  }, 200)
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

defineExpose({
  retry,
  onlineInstall,
  hide: () => { visible.value = false }
})
</script>

<style scoped>
.splash-screen {
  position: fixed;
  inset: 0;
  background: var(--bg);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
  /* 无边框窗口：整体作为拖动区域，交互元素单独排除（见下方 no-drag） */
  -webkit-app-region: drag;
}

/* 拖动区域内的可交互元素需排除，否则点击/滚动会被窗口拖动吞掉 */
.splash-screen .close-btn,
.splash-screen .btn,
.splash-screen .source-option,
.splash-screen .install-log,
.splash-screen .install-command code {
  -webkit-app-region: no-drag;
}

.splash-content {
  width: 400px;
  padding: 40px;
  text-align: center;
}

.logo-container {
  margin-bottom: 24px;
}

.logo {
  width: 80px;
  height: 80px;
  object-fit: contain;
}

.title {
  font-size: 28px;
  font-weight: 700;
  color: var(--fg);
  margin: 0 0 4px;
}

.subtitle {
  font-size: 14px;
  color: var(--fg-4);
  margin: 0 0 32px;
}

/* 状态条 */
.status-bar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 13px;
  background: var(--bg-2);
  color: var(--fg-3);
  margin-bottom: 24px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--fg-4);
}
.status-bar.status-connecting .status-dot {
  background: var(--accent);
  animation: pulse 1s ease-in-out infinite;
}
.status-bar.status-ready .status-dot {
  background: #10b981;
}
.status-bar.status-error .status-dot {
  background: #ef4444;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* 进度条 */
.progress-bar {
  width: 200px;
  height: 4px;
  margin: 16px auto 0;
  background: var(--bg-2);
  border-radius: 2px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 0.4s ease-out;
}

.progress-fill.progress-indeterminate {
  width: 100%;
  background: linear-gradient(90deg, transparent, var(--accent), transparent);
  animation: indeterminate 1.5s ease-in-out infinite;
}

@keyframes indeterminate {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

/* 安装确认页 */
.install-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.info-icon {
  color: var(--accent);
  opacity: 0.8;
}

.info-text {
  font-size: 14px;
  color: var(--fg-3);
  line-height: 1.5;
  margin: 0;
}

.version-details {
  color: var(--fg-4);
  font-size: 12px;
  margin: -8px 0 0;
}

.install-command {
  width: 100%;
  text-align: left;
  color: var(--fg-4);
  font-size: 12px;
}

.install-command code {
  display: block;
  margin-top: 8px;
  padding: 10px 12px;
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg-2);
  color: var(--fg-2);
  line-height: 1.5;
  white-space: nowrap;
}

/* 下载源选择（GitHub 直连 / 镜像） */
.install-source {
  width: 100%;
  text-align: left;
  color: var(--fg-4);
  font-size: 12px;
}

.source-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.source-option {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-2);
  cursor: pointer;
  transition: all 0.15s;
}

.source-option:hover {
  border-color: var(--accent);
}

.source-option.active {
  border-color: var(--accent);
  background: rgba(47, 128, 237, 0.08);
}

.source-option input {
  display: none;
}

.source-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}

.source-desc {
  font-size: 11px;
  color: var(--fg-4);
}

.java-check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 16px;
  background: rgba(16, 185, 129, 0.1);
  font-size: 12px;
}

.java-badge {
  color: #10b981;
  font-weight: 700;
}

.java-text {
  color: var(--fg-3);
}

.install-btn {
  margin-top: 8px;
}

/* 安装步骤列表 */
.steps-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 20px;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-radius: 8px;
  background: var(--bg-2);
  font-size: 13px;
  color: var(--fg-4);
  transition: all 0.3s;
}

.step-item.step-active {
  background: var(--bg-2);
  color: var(--fg);
}

.step-item.step-done {
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
}

.step-item.step-error {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}

.step-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--bg);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.step-label {
  flex: 1;
  text-align: left;
}

.step-detail {
  font-size: 11px;
  color: var(--fg-4);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 按钮 */
.btn {
  margin-top: 24px;
  padding: 9px 32px;
  border-radius: var(--r);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}
.btn-primary {
  background: var(--accent);
  color: #fff;
}
.btn-primary:hover {
  opacity: 0.9;
}
.btn-secondary {
  background: var(--bg-2);
  color: var(--fg);
  border-color: var(--border);
}
.btn-secondary:hover {
  background: var(--bg-3);
}
.error-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.confirm-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-top: 8px;
}

/* 关闭按钮 */
.close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border: none;
  background: var(--bg-2);
  border-radius: 8px;
  color: var(--fg-3);
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--t);
  z-index: 1;
}
.close-btn:hover {
  background: var(--bg-3);
  color: var(--fg);
}
.btn-lg {
  padding: 10px 36px;
  font-size: 14px;
}

/* JDK 下载详情 */
.download-detail {
  margin-top: 16px;
  padding: 10px 16px;
  background: var(--bg-2);
  border-radius: 8px;
  font-size: 12px;
}

.download-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.download-label {
  color: var(--fg-4);
}

.download-value {
  color: var(--fg);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.download-message {
  color: var(--fg-4);
  font-size: 11px;
  margin-top: 4px;
}

/* 在线安装日志控制台 */
.install-log {
  margin-top: 16px;
  width: 100%;
  max-height: 240px;
  overflow-y: auto;
  background: #1a1a2e;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 12px;
  text-align: left;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
  font-size: 11px;
  line-height: 1.6;
}

.log-line {
  color: #a0aec0;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-placeholder {
  color: #4a5568;
  font-style: italic;
}

.btn-online {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
  border: none;
}
.btn-online:hover {
  opacity: 0.92;
}
</style>
