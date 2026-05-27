<template>
  <div class="not-found-view">
    <div class="error-container">
      <div class="error-code">404</div>
      <div class="error-message">页面未找到</div>
      <div class="error-description">
        您访问的页面不存在或已被移除。
      </div>
      
      <div class="error-details">
        <div class="detail-item">
          <span class="label">请求路径:</span>
          <span class="value">{{ currentPath }}</span>
        </div>
        <div class="detail-item">
          <span class="label">时间:</span>
          <span class="value">{{ currentTime }}</span>
        </div>
      </div>
      
      <div class="ascii-art">
        <pre>
   _____ __  __ ____    ____             __ _
  / ____|  \/  |  _ \  |  _ \  _____   _/ _(_) __ _
 | (___ | |\/| | |_) | | | | |/ _ \ \ / / |_| |/ _` |
  \___ \| |  | |  _ <  | |_| |  __/\ V /|  _| | (_| |
  |____/|_|  |_|_| \_\ |____/ \___| \_/|_| |_|\__, |
                                                |___/
        </pre>
      </div>
      
      <div class="suggestions">
        <h3>建议操作:</h3>
        <ul>
          <li>
            <button class="terminal-button" @click="goHome">返回首页</button>
          </li>
          <li>
            <button class="terminal-button" @click="goBack">返回上一页</button>
          </li>
          <li>
            <button class="terminal-button" @click="reportIssue">报告问题</button>
          </li>
        </ul>
      </div>
      
      <div class="help-links">
        <h3>您可能需要:</h3>
        <div class="link-grid">
          <router-link to="/chat" class="help-link">
            <span class="link-icon">💬</span>
            <span class="link-text">开始对话</span>
          </router-link>
          <router-link to="/tools" class="help-link">
            <span class="link-icon">🔧</span>
            <span class="link-text">查看工具</span>
          </router-link>
          <router-link to="/settings" class="help-link">
            <span class="link-icon">⚙️</span>
            <span class="link-text">系统设置</span>
          </router-link>
          <router-link to="/help" class="help-link">
            <span class="link-icon">❓</span>
            <span class="link-text">帮助文档</span>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()
const currentPath = ref('')
const currentTime = ref('')

const goHome = () => {
  router.push('/')
}

const goBack = () => {
  window.history.back()
}

const reportIssue = () => {
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: `报告 404 错误: ${currentPath.value}` 
    }
  }))
}

onMounted(() => {
  currentPath.value = window.location.pathname
  currentTime.value = new Date().toLocaleString('zh-CN')
})
</script>

<style scoped>
.not-found-view {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  padding: var(--spacing-lg);
}

.error-container {
  text-align: center;
  max-width: 800px;
  width: 100%;
}

.error-code {
  font-size: 8rem;
  font-weight: bold;
  color: var(--terminal-red);
  text-shadow: 0 0 20px var(--terminal-red);
  line-height: 1;
  margin-bottom: var(--spacing-md);
  animation: glitch 2s infinite;
}

@keyframes glitch {
  0%, 100% { transform: translate(0); }
  20% { transform: translate(-2px, 2px); }
  40% { transform: translate(-2px, -2px); }
  60% { transform: translate(2px, 2px); }
  80% { transform: translate(2px, -2px); }
}

.error-message {
  font-size: var(--font-size-xl);
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-md);
  text-transform: uppercase;
  letter-spacing: 2px;
}

.error-description {
  color: var(--terminal-gray);
  margin-bottom: var(--spacing-lg);
  font-size: var(--font-size-lg);
}

.error-details {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
  text-align: left;
}

.detail-item {
  display: flex;
  margin-bottom: var(--spacing-sm);
}

.detail-item:last-child {
  margin-bottom: 0;
}

.detail-item .label {
  color: var(--terminal-amber);
  width: 120px;
  flex-shrink: 0;
}

.detail-item .value {
  color: var(--terminal-green);
  font-family: var(--font-mono);
}

.ascii-art {
  margin: var(--spacing-xl) 0;
  color: var(--terminal-red);
  text-shadow: 0 0 10px var(--terminal-red);
}

.ascii-art pre {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  line-height: 1.2;
  white-space: pre;
}

.suggestions {
  margin-bottom: var(--spacing-xl);
}

.suggestions h3 {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-md);
}

.suggestions ul {
  list-style: none;
  display: flex;
  justify-content: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

.help-links {
  margin-bottom: var(--spacing-lg);
}

.help-links h3 {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-md);
}

.link-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--spacing-md);
}

.help-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  text-decoration: none;
  transition: all 0.2s;
}

.help-link:hover {
  border-color: var(--terminal-green);
  transform: translateY(-2px);
}

.link-icon {
  font-size: 2rem;
}

.link-text {
  color: var(--terminal-green);
  font-weight: bold;
}

@media (max-width: 768px) {
  .error-code {
    font-size: 5rem;
  }
  
  .ascii-art pre {
    font-size: 0.5rem;
  }
  
  .suggestions ul {
    flex-direction: column;
    align-items: center;
  }
  
  .suggestions .terminal-button {
    width: 200px;
  }
  
  .link-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>