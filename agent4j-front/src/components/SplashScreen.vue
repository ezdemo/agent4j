<template>
  <div class="splash-screen" v-if="visible">
    <div class="splash-content">
      <div class="logo-container">
        <img src="@/assets/logo.png" alt="Agent4j" class="logo" />
      </div>
      
      <h1 class="title">Agent4j</h1>
      <p class="subtitle">智能 AI 代码助手</p>
      
      <div class="status-bar" :class="statusClass">
        <span class="status-dot"></span>
        <span>{{ statusText }}</span>
      </div>
      
      <div class="progress-bar" v-if="loading">
        <div class="progress-fill" :style="{ width: progress + '%' }"></div>
      </div>
      
      <button 
        class="btn btn-primary" 
        v-if="status === 'error'" 
        @click="retry"
      >
        重试
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { agent4jWebService } from '@/services/tauri'
import { invoke } from '@tauri-apps/api/core'

const props = defineProps({
  autoStart: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['ready', 'error'])

const visible = ref(true)
const loading = ref(true)
const status = ref('starting') // starting, installing, ready, error
const progress = ref(0)
const errorMessage = ref('')

let progressInterval = null

const statusClass = computed(() => ({
  'status-connecting': status.value === 'starting' || status.value === 'installing',
  'status-ready': status.value === 'ready',
  'status-error': status.value === 'error'
}))

const statusText = computed(() => {
  switch (status.value) {
    case 'starting':
      return '正在启动服务...'
    case 'installing':
      return '首次运行，正在安装...'
    case 'ready':
      return '服务已就绪'
    case 'error':
      return errorMessage.value || '启动失败'
    default:
      return ''
  }
})

// 模拟进度
function startProgress() {
  progress.value = 0
  progressInterval = setInterval(() => {
    if (progress.value < 90) {
      progress.value += Math.random() * 10
    }
  }, 500)
}

function stopProgress() {
  if (progressInterval) {
    clearInterval(progressInterval)
    progressInterval = null
  }
  progress.value = 100
}

// 启动服务
async function startService() {
  loading.value = true
  status.value = 'starting'
  errorMessage.value = ''
  startProgress()
  
  try {
    // 1) 直接尝试从 Rust 获取端口（setup() 中 start() 已执行完毕）
    let port = 0
    try {
      port = await invoke('get_agent4j_web_port')
    } catch {}

    // 2) 端口有效则保存；无效则通过 start() 启动
    if (port > 0) {
      localStorage.setItem('agent4j-port', String(port))
      localStorage.setItem('agent4j-api-base', `http://127.0.0.1:${port}`)
    } else {
      // 尝试启动（非 Tauri 环境会返回 0）
      const startedPort = await agent4jWebService.start()
      if (startedPort > 0) {
        port = startedPort
      }
    }

    // 3) 验证后端可达（最多 5 秒）
    const ready = await agent4jWebService.waitForReady(5, 1000)
    
    if (ready) {
      status.value = 'ready'
      stopProgress()
      
      setTimeout(() => {
        visible.value = false
        emit('ready')
      }, 500)
    } else {
      // 非 Tauri：可能用户自己启动了后端，尝试默认端口
      if (port === 0) {
        localStorage.setItem('agent4j-api-base', 'http://127.0.0.1:8097')
        localStorage.setItem('agent4j-port', '8097')
        const fallbackReady = await agent4jWebService.waitForReady(3, 1000)
        if (fallbackReady) {
          status.value = 'ready'
          stopProgress()
          setTimeout(() => {
            visible.value = false
            emit('ready')
          }, 500)
          return
        }
      }
      throw new Error('服务启动超时')
    }
  } catch (error) {
    status.value = 'error'
    errorMessage.value = error.message || '服务启动失败'
    stopProgress()
    emit('error', error)
  } finally {
    loading.value = false
  }
}

// 重试
function retry() {
  startService()
}

onMounted(() => {
  if (props.autoStart) {
    startService()
  }
})

onUnmounted(() => {
  stopProgress()
})

// 暴露方法给父组件
defineExpose({
  startService,
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
  width: 380px;
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

/* 状态条（与 SetupScreen 一致） */
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

.progress-bar {
  width: 200px;
  height: 4px;
  margin: 0 auto;
  background: var(--bg-2);
  border-radius: 2px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 0.3s ease-out;
}

/* 按钮（与 SetupScreen 一致） */
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
</style>
