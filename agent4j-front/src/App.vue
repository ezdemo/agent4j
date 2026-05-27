<template>
  <div class="terminal-app">
    <header class="terminal-header">
      <div class="header-left">
        <span class="logo">Agent4J</span>
        <span class="version">v0.1.0</span>
      </div>
      <div class="header-center">
        <span class="status-indicator">{{ connectionStatus }}</span>
      </div>
      <div class="header-right">
        <span class="time">{{ currentTime }}</span>
      </div>
    </header>
    
    <main class="terminal-main">
      <router-view />
    </main>
    
    <footer class="terminal-footer">
      <div class="input-line">
        <span class="prompt">agent4j@{{ currentDir }}$</span>
        <input 
          v-model="commandInput" 
          @keydown.enter="executeCommand"
          class="command-input"
          placeholder="输入命令..."
          autofocus
        />
      </div>
    </footer>
    
    <div class="scanlines"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const commandInput = ref('')
const currentDir = ref('~')
const connectionStatus = ref('● 已连接')
const currentTime = ref('')

let timeInterval = null

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
}

const executeCommand = () => {
  const cmd = commandInput.value.trim()
  if (!cmd) return
  
  // 解析简单命令
  if (cmd === 'help') {
    router.push('/help')
  } else if (cmd === 'chat' || cmd === '/chat') {
    router.push('/chat')
  } else if (cmd === 'tools' || cmd === '/tools') {
    router.push('/tools')
  } else if (cmd === 'sessions' || cmd === '/sessions') {
    router.push('/sessions')
  } else if (cmd === 'settings' || cmd === '/settings') {
    router.push('/settings')
  } else if (cmd === 'clear') {
    // 清空输出（将在子组件中处理）
    window.dispatchEvent(new CustomEvent('terminal-clear'))
  } else if (cmd === 'exit' || cmd === 'quit') {
    if (confirm('确定要退出 Agent4J 吗？')) {
      window.close()
    }
  } else {
    // 未知命令
    window.dispatchEvent(new CustomEvent('terminal-output', { 
      detail: { type: 'error', text: `未知命令: ${cmd}. 输入 'help' 查看可用命令。` }
    }))
  }
  
  commandInput.value = ''
}

onMounted(() => {
  updateTime()
  timeInterval = setInterval(updateTime, 1000)
  
  // 监听目录变化事件
  window.addEventListener('directory-change', (e) => {
    currentDir.value = e.detail.path || '~'
  })
})

onUnmounted(() => {
  if (timeInterval) {
    clearInterval(timeInterval)
  }
})
</script>

<style scoped>
.terminal-app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(180deg, #0a0a0a 0%, #1a1a1a 100%);
  position: relative;
  overflow: hidden;
}

.terminal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 1rem;
  background: #0d0d0d;
  border-bottom: 1px solid #333;
  font-size: 0.9rem;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.logo {
  color: #33ff33;
  font-weight: bold;
  text-shadow: 0 0 10px #33ff33;
}

.version {
  color: #888;
  font-size: 0.8rem;
}

.status-indicator {
  color: #33ff33;
  animation: blink 2s infinite;
}

.time {
  color: #aaa;
}

.terminal-main {
  flex: 1;
  overflow-y: auto;
  padding: 1rem;
  position: relative;
  z-index: 1;
}

.terminal-footer {
  background: #0d0d0d;
  border-top: 1px solid #333;
  padding: 0.5rem 1rem;
  position: relative;
  z-index: 2;
}

.input-line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.prompt {
  color: #33ff33;
  white-space: nowrap;
}

.command-input {
  flex: 1;
  background: transparent;
  border: none;
  color: #33ff33;
  font-family: 'Courier New', monospace;
  font-size: 1rem;
  outline: none;
}

.command-input::placeholder {
  color: #555;
}

/* 扫描线效果 */
.scanlines {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  background: repeating-linear-gradient(
    0deg,
    rgba(0, 0, 0, 0.15),
    rgba(0, 0, 0, 0.15) 1px,
    transparent 1px,
    transparent 2px
  );
  z-index: 1000;
  animation: scanline 8s linear infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0.5; }
}

@keyframes scanline {
  0% { background-position: 0 0; }
  100% { background-position: 0 100%; }
}
</style>