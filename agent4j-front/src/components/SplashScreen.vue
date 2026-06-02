<template>
  <div class="splash-screen" v-if="visible">
    <!-- 关闭按钮 -->
    <button class="close-btn" @click="closeApp" title="关闭程序">✕</button>
    <div class="splash-content">
      <!-- Logo -->
      <div class="logo-container">
        <img src="@/assets/logo.png" alt="Agent4j" class="logo" />
      </div>

      <h1 class="title">Agent4j</h1>
      <p class="subtitle">智能 AI 代码助手</p>

      <!-- 已安装且最新：直接显示启动中 -->
      <template v-if="phase === 'ready'">
        <div class="status-bar status-ready">
          <span class="status-dot"></span>
          <span>服务已就绪</span>
        </div>
      </template>

      <!-- 非 Tauri 环境：跳过安装 -->
      <template v-else-if="phase === 'non-tauri'">
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
            首次运行，需要安装 Agent4j Web 服务
          </p>
          <p class="info-text" v-else-if="installReason === 'version_mismatch'">
            检测到新版本，需要更新安装
          </p>
          <p class="info-text" v-else>
            需要重新安装 Agent4j Web 服务
          </p>

          <div class="java-check" v-if="javaInfo">
            <span class="java-badge">✓</span>
            <span class="java-text">{{ javaInfo }}</span>
          </div>

          <div class="confirm-actions">
            <button class="btn btn-primary btn-lg install-btn" @click="startInstall">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              开始安装
            </button>
            <button class="btn btn-secondary btn-lg" @click="closeApp">
              退出
            </button>
          </div>
        </div>
      </template>

      <!-- 安装进行中 -->
      <template v-else-if="phase === 'installing'">
        <div class="steps-list">
          <div
            v-for="(step, idx) in installSteps"
            :key="idx"
            class="step-item"
            :class="{
              'step-done': step.status === 'done',
              'step-active': step.status === 'active',
              'step-error': step.status === 'error'
            }"
          >
            <span class="step-icon">
              <svg v-if="step.status === 'done'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
              <svg v-else-if="step.status === 'error'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
              <span v-else class="step-spinner"></span>
            </span>
            <span class="step-label">{{ step.label }}</span>
          </div>
        </div>

        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: installProgress + '%' }"></div>
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
          <button class="btn btn-primary" @click="retry">
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
import { ref, computed, onMounted } from 'vue'
import { agent4jWebService } from '@/services/tauri'
import { invoke } from '@tauri-apps/api/core'

const emit = defineEmits(['ready', 'error'])

const visible = ref(true)
const phase = ref('checking') // checking | confirm | installing | starting | ready | non-tauri | error
const installReason = ref('')
const javaInfo = ref('')
const errorMessage = ref('')
const startupMessage = ref('')
const resourceDir = ref('')

// 安装步骤和进度
const installSteps = ref([])
const installProgress = ref(0)

// 是否在 Tauri 环境中
const isTauri = ref(false)

onMounted(async () => {
  await checkEnvironment()
})

async function checkEnvironment() {
  phase.value = 'checking'

  // 检查是否在 Tauri 环境
  try {
    const sysInfo = await invoke('get_system_info')
    isTauri.value = true
    console.log('[Splash] Tauri environment:', sysInfo)
  } catch {
    // 非 Tauri 环境，直接等待服务
    console.log('[Splash] Non-Tauri environment, waiting for service...')
    phase.value = 'non-tauri'
    await waitForService()
    return
  }

  // Tauri 环境：获取资源目录
  try {
    const dir = await agent4jWebService.getResourceDir()
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
    // 先检查 Java
    const status = await agent4jWebService.getStatus()

    // 检查是否需要安装
    const result = await agent4jWebService.checkInstallNeeded(resourceDir.value)
    console.log('[Splash] Install check:', result)

    if (!result.needed) {
      // 已安装且版本匹配，直接启动
      await startService()
      return
    }

    // 需要安装：检查 Java 并显示确认页
    installReason.value = result.reason
    try {
      const sysInfo = await invoke('get_system_info')
      javaInfo.value = `Java 环境已检测`
    } catch {}

    phase.value = 'confirm'
  } catch (e) {
    console.error('[Splash] Check install failed:', e)
    // 回退：尝试直接启动
    await startService()
  }
}

async function startInstall() {
  phase.value = 'installing'
  installProgress.value = 0

  // 初始化步骤
  installSteps.value = [
    { label: '检查 Java 环境', status: 'pending' },
    { label: '解压安装包', status: 'pending' },
    { label: '复制文件', status: 'pending' },
    { label: '配置环境', status: 'pending' },
  ]

  try {
    const result = await agent4jWebService.install(resourceDir.value)
    console.log('[Splash] Install result:', result)

    if (result.success && result.steps) {
      // 根据返回的步骤更新状态
      const stepsData = result.steps
      for (let i = 0; i < installSteps.value.length; i++) {
        installSteps.value[i].status = 'done'
        installProgress.value = ((i + 1) / installSteps.value.length) * 100
        // 模拟每步的延迟感
        await sleep(200)
      }
      installProgress.value = 100
      await sleep(300)

      // 安装完成，启动服务
      await startService()
    }
  } catch (e) {
    console.error('[Splash] Install failed:', e)
    // 标记当前步骤为错误
    const activeIdx = installSteps.value.findIndex(s => s.status === 'active' || s.status === 'pending')
    if (activeIdx >= 0) {
      installSteps.value[activeIdx].status = 'error'
    }
    phase.value = 'error'
    errorMessage.value = `安装失败: ${e.message || e}`
  }
}

async function startService() {
  phase.value = 'starting'
  startupMessage.value = '正在启动 Java 服务...'

  try {
    // 1) 从 Rust 获取端口（start 命令已启动 Java 进程）
    let port = 0
    try {
      port = await invoke('get_agent4j_web_port')
    } catch {}

    if (port > 0) {
      localStorage.setItem('agent4j-port', String(port))
      localStorage.setItem('agent4j-api-base', `http://127.0.0.1:${port}`)
      startupMessage.value = `Java 服务已启动，端口 ${port}，等待健康检查...`
    } else {
      // 通过 start 命令启动
      startupMessage.value = '正在启动 Java 进程...'
      const startedPort = await agent4jWebService.start()
      if (startedPort > 0) {
        port = startedPort
        localStorage.setItem('agent4j-port', String(port))
        localStorage.setItem('agent4j-api-base', `http://127.0.0.1:${port}`)
        startupMessage.value = `Java 服务已启动，端口 ${port}，等待健康检查...`
      } else {
        // 非 Tauri：Rust 未返回端口，使用 localStorage 缓存的端口
        port = parseInt(localStorage.getItem('agent4j-port') || '0', 10)
        if (port > 0) {
          localStorage.setItem('agent4j-api-base', `http://127.0.0.1:${port}`)
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
      phase.value = 'ready'
      startupMessage.value = '服务已就绪！'
      await sleep(600)
      visible.value = false
      emit('ready')
      return
    }

    throw new Error(`服务启动超时，端口 ${port} 未响应健康检查`)
  } catch (e) {
    phase.value = 'error'
    errorMessage.value = e.message || '服务启动失败'
    emit('error', e)
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
  // 非 Tauri 环境：尝试连接已有服务，或提示用户手动启动
  try {
    const ready = await agent4jWebService.waitForReady(3, 1500)
    if (ready) {
      phase.value = 'ready'
      await sleep(500)
      visible.value = false
      emit('ready')
      return
    }

    // 默认端口不行，尝试从 Rust 获取
    let port = 0
    try {
      port = await invoke('get_agent4j_web_port')
    } catch {}

    if (port > 0) {
      localStorage.setItem('agent4j-api-base', `http://127.0.0.1:${port}`)
      localStorage.setItem('agent4j-port', String(port))
      const ready2 = await agent4jWebService.waitForReady(3, 1000)
      if (ready2) {
        phase.value = 'ready'
        await sleep(500)
        visible.value = false
        emit('ready')
        return
      }
    }

    // 都不行：显示错误，让用户手动启动
    phase.value = 'error'
    errorMessage.value = '无法连接服务，请确认 agent4j-web 已启动'
  } catch (e) {
    phase.value = 'error'
    errorMessage.value = e.message || '连接服务失败'
  }
}

function retry() {
  checkEnvironment()
}

async function closeApp() {
  // 尝试关闭 Tauri 窗口
  try {
    const { getCurrentWindow } = await import('@tauri-apps/api/window')
    const win = getCurrentWindow()
    await win.close()
    return
  } catch {
    // 非 Tauri 环境
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
</style>
