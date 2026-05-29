<template>
  <div class="splash-screen" v-if="visible">
    <div class="splash-content">
      <div class="logo-container">
        <img src="@/assets/logo.svg" alt="Agent4j" class="logo" />
      </div>
      
      <h1 class="title">Agent4j</h1>
      <p class="subtitle">智能 AI 代码助手</p>
      
      <div class="status-container">
        <div class="loading-spinner" v-if="loading"></div>
        <div class="status-icon" :class="statusClass" v-else>
          <span v-if="status === 'ready'">✓</span>
          <span v-else-if="status === 'error'">✗</span>
        </div>
        
        <p class="status-text">{{ statusText }}</p>
      </div>
      
      <div class="progress-bar" v-if="loading">
        <div class="progress-fill" :style="{ width: progress + '%' }"></div>
      </div>
      
      <button 
        class="retry-button" 
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
    // 检查状态
    const serviceStatus = await agent4jWebService.getStatus()
    
    if (!serviceStatus.installed) {
      status.value = 'installing'
      // 安装会自动在 Tauri setup 中完成
      // 等待安装完成
      await new Promise(resolve => setTimeout(resolve, 2000))
    }
    
    // 如果服务没在运行，启动它
    if (!serviceStatus.running) {
      await agent4jWebService.start()
    }
    
    // 等待服务就绪
    status.value = 'starting'
    const ready = await agent4jWebService.waitForReady(30, 1000)
    
    if (ready) {
      status.value = 'ready'
      stopProgress()
      
      // 延迟一下再隐藏，让用户看到成功状态
      setTimeout(() => {
        visible.value = false
        emit('ready')
      }, 500)
    } else {
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
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 9999;
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.splash-content {
  text-align: center;
  padding: 40px;
}

.logo-container {
  margin-bottom: 24px;
}

.logo {
  width: 96px;
  height: 96px;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.05); opacity: 0.8; }
}

.title {
  font-size: 32px;
  font-weight: 700;
  color: #ffffff;
  margin: 0 0 8px 0;
  letter-spacing: 2px;
}

.subtitle {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.7);
  margin: 0 0 48px 0;
}

.status-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid rgba(255, 255, 255, 0.2);
  border-top-color: #4f8cff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.status-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 18px;
  font-weight: bold;
}

.status-ready {
  background: #10b981;
  color: white;
}

.status-error {
  background: #ef4444;
  color: white;
}

.status-text {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
}

.progress-bar {
  width: 240px;
  height: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  overflow: hidden;
  margin: 0 auto;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #4f8cff, #6366f1);
  border-radius: 2px;
  transition: width 0.3s ease-out;
}

.retry-button {
  margin-top: 24px;
  padding: 10px 32px;
  background: #4f8cff;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}

.retry-button:hover {
  background: #3b7aed;
}
</style>
