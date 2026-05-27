<template>
  <div class="home-view">
    <div class="ascii-art">
      <pre>
   _____           _    _____            _     _            
  / ____|         | |  |_   _|          | |   | |           
 | (___   ___  ___| |_   | | ___ __  ___| |__ | | _____  __
  \___ \ / _ \/ __| __|  | |/ __|\ \/ / '_ \| |/ _ \ \/ /
  ____) |  __/ (__| |_   | | (__ >  <| |_) | | (_) >  < 
 |_____/ \___|\___|\__|  \_/\___/_/\_\_.__/|_|\___/_/\_\
      </pre>
    </div>
    
    <div class="system-info">
      <div class="info-line">
        <span class="label">系统:</span>
        <span class="value">Agent4J - Java AI Agent Framework</span>
      </div>
      <div class="info-line">
        <span class="label">版本:</span>
        <span class="value">1.0-SNAPSHOT</span>
      </div>
      <div class="info-line">
        <span class="label">状态:</span>
        <span class="value status-online">● 在线</span>
      </div>
      <div class="info-line">
        <span class="label">连接:</span>
        <span class="value">{{ connectionStatus }}</span>
      </div>
    </div>
    
    <div class="welcome-menu">
      <h3>可用命令:</h3>
      <ul class="command-list">
        <li v-for="cmd in commands" :key="cmd.name" @click="executeCommand(cmd.name)">
          <span class="command-name">{{ cmd.name }}</span>
          <span class="command-desc">{{ cmd.desc }}</span>
        </li>
      </ul>
    </div>
    
    <div class="recent-activity">
      <h3>最近活动:</h3>
      <div class="activity-list">
        <div v-for="activity in recentActivities" :key="activity.id" class="activity-item">
          <span class="activity-time">{{ activity.time }}</span>
          <span class="activity-text">{{ activity.text }}</span>
        </div>
      </div>
    </div>
    
    <div class="quick-start">
      <h3>快速开始:</h3>
      <div class="quick-actions">
        <button class="terminal-button" @click="navigateTo('/chat')">
          开始对话
        </button>
        <button class="terminal-button" @click="navigateTo('/tools')">
          查看工具
        </button>
        <button class="terminal-button" @click="navigateTo('/settings')">
          配置设置
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const connectionStatus = ref('连接中...')

const commands = ref([
  { name: '/chat', desc: '开始与AI助手对话' },
  { name: '/tools', desc: '查看可用工具列表' },
  { name: '/sessions', desc: '管理会话历史' },
  { name: '/settings', desc: '系统配置设置' },
  { name: '/help', desc: '查看帮助信息' },
  { name: 'clear', desc: '清空终端输出' },
  { name: 'exit', desc: '退出系统' }
])

const recentActivities = ref([
  { id: 1, time: '09:30', text: '系统启动完成' },
  { id: 2, time: '09:31', text: '工具注册完成 (27个工具)' },
  { id: 3, time: '09:32', text: '会话服务初始化' },
  { id: 4, time: '09:33', text: '等待用户输入...' }
])

const executeCommand = (cmd) => {
  if (cmd.startsWith('/')) {
    router.push(cmd)
  } else {
    // 对于非路由命令，触发事件
    window.dispatchEvent(new CustomEvent('terminal-command', { 
      detail: { command: cmd }
    }))
  }
}

const navigateTo = (path) => {
  router.push(path)
}

onMounted(() => {
  // 模拟连接状态
  setTimeout(() => {
    connectionStatus.value = '已连接到 Agent4J 后端'
  }, 1500)
})
</script>

<style scoped>
.home-view {
  max-width: 800px;
  margin: 0 auto;
}

.ascii-art {
  text-align: center;
  margin-bottom: var(--spacing-xl);
  color: var(--terminal-green);
  text-shadow: 0 0 10px var(--terminal-green);
}

.ascii-art pre {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  line-height: 1.2;
  white-space: pre;
}

.system-info {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.info-line {
  display: flex;
  margin-bottom: var(--spacing-sm);
}

.info-line:last-child {
  margin-bottom: 0;
}

.label {
  color: var(--terminal-amber);
  width: 100px;
  flex-shrink: 0;
}

.value {
  color: var(--terminal-green);
}

.status-online {
  color: var(--terminal-green);
  animation: blink 2s infinite;
}

.welcome-menu {
  margin-bottom: var(--spacing-lg);
}

.welcome-menu h3 {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  padding-bottom: var(--spacing-sm);
}

.command-list {
  list-style: none;
}

.command-list li {
  display: flex;
  padding: var(--spacing-sm);
  border-bottom: 1px solid var(--border-color);
  cursor: pointer;
  transition: background 0.2s;
}

.command-list li:hover {
  background: var(--bg-tertiary);
}

.command-name {
  color: var(--terminal-cyan);
  width: 150px;
  flex-shrink: 0;
  font-weight: bold;
}

.command-desc {
  color: var(--terminal-gray);
}

.recent-activity {
  margin-bottom: var(--spacing-lg);
}

.recent-activity h3 {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  padding-bottom: var(--spacing-sm);
}

.activity-list {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  padding: var(--spacing-md);
}

.activity-item {
  display: flex;
  margin-bottom: var(--spacing-sm);
}

.activity-item:last-child {
  margin-bottom: 0;
}

.activity-time {
  color: var(--terminal-gray);
  width: 60px;
  flex-shrink: 0;
}

.activity-text {
  color: var(--terminal-green);
}

.quick-start {
  margin-bottom: var(--spacing-lg);
}

.quick-start h3 {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  padding-bottom: var(--spacing-sm);
}

.quick-actions {
  display: flex;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0.5; }
}

@media (max-width: 600px) {
  .ascii-art pre {
    font-size: 0.6rem;
  }
  
  .command-list li {
    flex-direction: column;
    gap: var(--spacing-xs);
  }
  
  .command-name {
    width: auto;
  }
  
  .quick-actions {
    flex-direction: column;
  }
  
  .terminal-button {
    width: 100%;
  }
}
</style>