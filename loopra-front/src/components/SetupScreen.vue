<template>
  <div class="setup-screen">
    <!-- 关闭按钮 -->
    <button class="close-btn" @click="handleClose" title="关闭程序">✕</button>

    <div class="setup-content">
      <!-- Logo & 标题 -->
      <div class="logo-section">
        <div class="logo-placeholder">A</div>
        <h1 class="title">Loopra</h1>
        <p class="subtitle">智能 AI 代码助手</p>
      </div>

      <!-- 状态提示 -->
      <div class="status-bar" :class="statusClass">
        <span class="status-dot"></span>
        <span>{{ statusText }}</span>
      </div>

      <!-- 版本信息 -->
      <div v-if="versionInfo" class="version-info">
        版本 {{ versionInfo.version }} · {{ versionInfo.buildTime }}
      </div>

      <!-- 配置表单 -->
      <div class="form-section">
        <label class="form-label">服务端地址</label>
        <div class="input-row">
          <input
            v-model="serverUrl"
            class="form-input"
            :placeholder="DEFAULT_API_BASE"
            @keyup.enter="handleConnect"
          />
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="actions">
        <button class="btn btn-primary" @click="handleConnect" :disabled="connecting">
          {{ connecting ? '连接中...' : '检测连接' }}
        </button>
        <button class="btn btn-secondary" @click="handleCheckVersion" :disabled="connecting">
          检查版本
        </button>
      </div>

      <!-- 错误信息 -->
      <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>


    </div>
  </div>
</template>

<script setup>
// 监听 status 变化更新 UI
import {onMounted, ref, watch} from 'vue'
import {DEFAULT_API_BASE, systemAPI} from '../services/api'
import {platform} from '@/services/platform'
// 动态获取当前平台的 loopraWebService
const { loopraWebService } = platform.implementation


const emit = defineEmits(['connected', 'close'])

const serverUrl = ref('')
const status = ref('idle') // idle | connecting | connected | error
const connecting = ref(false)
const errorMsg = ref('')
const versionInfo = ref(null)

const statusClass = ref('')
const statusText = ref('')


watch(status, (v) => {
  switch (v) {
    case 'idle':
      statusClass.value = ''
      statusText.value = '等待连接...'
      break
    case 'connecting':
      statusClass.value = 'status-connecting'
      statusText.value = '正在连接服务器...'
      break
    case 'connected':
      statusClass.value = 'status-connected'
      statusText.value = '已连接'
      break
    case 'error':
      statusClass.value = 'status-error'
      statusText.value = '连接失败'
      break
  }
})

// 保存自定义地址到 localStorage
function saveServerUrl(url) {
  const trimmed = url.trim()
  if (trimmed) {
    localStorage.setItem('loopra-api-base', trimmed)
  } else {
    localStorage.removeItem('loopra-api-base')
  }
}

// 尝试从服务同步最新端口（桌面模式下端口会变）
async function syncPortFromService() {
  try {
    const port = await loopraWebService.getCurrentPort()
    if (port > 0) {
      localStorage.setItem('loopra-port', String(port))
      const baseUrl = `http://127.0.0.1:${port}`
      localStorage.setItem('loopra-api-base', baseUrl)
      serverUrl.value = baseUrl
    }
  } catch { /* 非桌面环境，忽略 */ }
}

// 关闭程序
async function handleClose() {
  // 使用平台抽象层关闭窗口
  try {
    await platform.implementation.window.close()
    return
  } catch (e) {
    console.warn('[Setup] Failed to close window via platform API:', e)
  }
  // 浏览器环境：尝试关闭标签页
  window.close()
  // 大多数浏览器会拦截 window.close()，提示用户
  setTimeout(() => {
    alert('请手动关闭此浏览器标签页')
  }, 200)
}

// 检测连接
async function handleConnect() {
  // 连接前先刷新端口（桌面环境下确保是最新的）
  await syncPortFromService()

  connecting.value = true
  status.value = 'connecting'
  errorMsg.value = ''

  // 保存用户输入的地址
  saveServerUrl(serverUrl.value)

  try {
    const r = await systemAPI.healthCheck()
    if (r.success) {
      status.value = 'connected'
      // 连接成功，通知父组件
      setTimeout(() => emit('connected'), 600)
    } else {
      status.value = 'error'
      errorMsg.value = r.message || '服务器返回异常'
    }
  } catch (e) {
    status.value = 'error'
    errorMsg.value = e.message || '无法连接到服务器，请检查地址是否正确'
  } finally {
    connecting.value = false
  }
}

// 检查版本
async function handleCheckVersion() {
  connecting.value = true
  errorMsg.value = ''
  lastResponse.value = ''
  lastError.value = ''

  // 先确保地址已保存
  saveServerUrl(serverUrl.value)

  try {
    const r = await systemAPI.getVersion()
    if (r.success && r.data) {
      versionInfo.value = r.data
      status.value = 'connected'
    } else {
      errorMsg.value = r.message || '获取版本信息失败'
    }
  } catch (e) {
    errorMsg.value = e.message || '无法连接到服务器'
    status.value = 'error'
  } finally {
    connecting.value = false
  }
}

// 自动连接：尝试连接已保存的地址
async function autoConnect() {
  // 先同步最新端口
  await syncPortFromService()

  const saved = localStorage.getItem('loopra-api-base')
  if (saved) {
    serverUrl.value = saved
  }
  // 快速尝试一次连接
  try {
    const r = await systemAPI.healthCheck()
    if (r.success) {
      status.value = 'connected'
      emit('connected')
      return
    }
  } catch {}
  status.value = 'idle'
}
const isDesktopEnv = ref(false)

// 异步检测环境
async function detectEnvironment() {
  if (platform.isElectron) {
    isDesktopEnv.value = true
    console.log('[Setup] Electron environment detected')
  } else {
    isDesktopEnv.value = false
    console.log('[Setup] Browser environment detected')
  }
}
onMounted(() => {
  detectEnvironment()
  // 桌面模式下 SplashScreen 处理，SetupScreen 不自动连接
  if (!isDesktopEnv.value) {
    autoConnect()
  }
})
</script>

<style scoped>
.setup-screen {
  position: fixed;
  inset: 0;
  background: var(--bg);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
}

.setup-content {
  width: 380px;
  padding: 40px;
  text-align: center;
}

.logo-section {
  margin-bottom: 32px;
}

.logo-placeholder {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  background: var(--accent);
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  color: #fff;
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
  margin: 0;
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
  margin-bottom: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--fg-4);
}

.status-connecting .status-dot {
  background: var(--accent);
  animation: pulse 1s ease-in-out infinite;
}

.status-connected .status-dot {
  background: #10b981;
}

.status-error .status-dot {
  background: #ef4444;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* 版本信息 */
.version-info {
  font-size: 12px;
  color: var(--fg-4);
  margin-bottom: 24px;
}

/* 表单 */
.form-section {
  text-align: left;
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--fg-2);
  margin-bottom: 6px;
}

.input-row {
  display: flex;
  gap: 8px;
}

.form-input {
  flex: 1;
  padding: 9px 12px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  font-size: 14px;
  color: var(--fg);
  font-family: var(--mono);
}

.form-input:focus {
  outline: none;
  border-color: var(--accent);
}

.form-input::placeholder {
  color: var(--fg-4);
  font-family: inherit;
}

/* 按钮 */
.actions {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.btn {
  flex: 1;
  padding: 9px 16px;
  border-radius: var(--r);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--accent);
  color: #fff;
}
.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-secondary {
  background: var(--bg-2);
  color: var(--fg);
  border-color: var(--border);
}
.btn-secondary:hover:not(:disabled) {
  background: var(--bg-3);
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

/* 错误信息 */
.error-msg {
  padding: 10px 12px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: var(--r);
  font-size: 13px;
  color: #ef4444;
  text-align: left;
  word-break: break-all;
}
</style>
