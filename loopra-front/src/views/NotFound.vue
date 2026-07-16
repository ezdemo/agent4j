<template>
  <div class="not-found-view">
    <div class="error-container">
      <!-- 错误代码 -->
      <div class="error-code">
        <span class="digit">4</span>
        <span class="digit">0</span>
        <span class="digit">4</span>
      </div>
      
      <!-- 错误信息 -->
      <div class="error-message">
        <h1>页面未找到</h1>
        <p>您访问的页面不存在或已被移除</p>
      </div>
      
      <!-- 错误详情 -->
      <div class="error-details">
        <div class="detail-item">
          <span class="detail-label">请求路径</span>
          <span class="detail-value">{{ currentPath }}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">时间</span>
          <span class="detail-value">{{ currentTime }}</span>
        </div>
      </div>
      
      <!-- ASCII 艺术 -->
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
      
      <!-- 操作按钮 -->
      <div class="error-actions">
        <button class="btn btn-primary" @click="goHome">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
            <polyline points="9 22 9 12 15 12 15 22"/>
          </svg>
          返回首页
        </button>
        <button class="btn btn-secondary" @click="goBack">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="19" y1="12" x2="5" y2="12"/>
            <polyline points="12 19 5 12 12 5"/>
          </svg>
          返回上一页
        </button>
        <button class="btn btn-ghost" @click="reportIssue">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          报告问题
        </button>
      </div>
      
      <!-- 推荐链接 -->
      <div class="suggestions">
        <h3>您可能需要</h3>
        <div class="suggestions-grid">
          <router-link to="/" class="suggestion-card">
            <div class="suggestion-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                <polyline points="9 22 9 12 15 12 15 22"/>
              </svg>
            </div>
            <div class="suggestion-content">
              <div class="suggestion-title">首页</div>
              <div class="suggestion-desc">返回主页</div>
            </div>
          </router-link>
          
          <router-link to="/chat" class="suggestion-card">
            <div class="suggestion-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
            </div>
            <div class="suggestion-content">
              <div class="suggestion-title">对话</div>
              <div class="suggestion-desc">开始与 AI 对话</div>
            </div>
          </router-link>
          
          <router-link to="/tools" class="suggestion-card">
            <div class="suggestion-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
              </svg>
            </div>
            <div class="suggestion-content">
              <div class="suggestion-title">工具箱</div>
              <div class="suggestion-desc">查看可用工具</div>
            </div>
          </router-link>
          
          <router-link to="/help" class="suggestion-card">
            <div class="suggestion-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            </div>
            <div class="suggestion-content">
              <div class="suggestion-title">帮助中心</div>
              <div class="suggestion-desc">获取使用帮助</div>
            </div>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'

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
  // 这里可以集成问题报告功能
  alert(`问题已报告: ${currentPath.value}`)
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
  padding: var(--space-6);
  background: var(--bg);
}

.error-container {
  text-align: center;
  max-width: 600px;
  width: 100%;
}

/* 错误代码 */
.error-code {
  display: flex;
  justify-content: center;
  gap: var(--space-2);
  margin-bottom: var(--space-6);
}

.digit {
  font-size: 6rem;
  font-weight: var(--font-bold);
  line-height: 1;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: bounce 2s ease-in-out infinite;
}

.digit:nth-child(2) {
  animation-delay: 0.2s;
}

.digit:nth-child(3) {
  animation-delay: 0.4s;
}

/* 错误信息 */
.error-message {
  margin-bottom: var(--space-6);
}

.error-message h1 {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  color: var(--fg);
  margin-bottom: var(--space-2);
}

.error-message p {
  font-size: var(--text-lg);
  color: var(--fg-muted);
}

/* 错误详情 */
.error-details {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  margin-bottom: var(--space-6);
  text-align: left;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) 0;
}

.detail-item:not(:last-child) {
  border-bottom: 1px solid var(--border);
}

.detail-label {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--fg-secondary);
  min-width: 80px;
}

.detail-value {
  font-size: var(--text-sm);
  font-family: var(--font-mono);
  color: var(--fg);
  word-break: break-all;
}

/* ASCII 艺术 */
.ascii-art {
  margin: var(--space-8) 0;
  color: var(--fg-muted);
  opacity: 0.3;
}

.ascii-art pre {
  font-family: var(--font-mono);
  font-size: 0.7rem;
  line-height: 1.2;
  white-space: pre;
  background: none;
  border: none;
  padding: 0;
}

/* 操作按钮 */
.error-actions {
  display: flex;
  justify-content: center;
  gap: var(--space-3);
  margin-bottom: var(--space-8);
  flex-wrap: wrap;
}

/* 推荐链接 */
.suggestions {
  margin-top: var(--space-8);
}

.suggestions h3 {
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: var(--space-4);
}

.suggestions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-4);
}

.suggestion-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  text-decoration: none;
  transition: all var(--transition-fast);
}

.suggestion-card:hover {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.suggestion-icon {
  width: 40px;
  height: 40px;
  background: var(--accent-soft);
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-primary);
  flex-shrink: 0;
}

.suggestion-content {
  flex: 1;
  text-align: left;
}

.suggestion-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: 0.25rem;
}

.suggestion-desc {
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

/* 动画 */
@keyframes bounce {
  0%, 20%, 50%, 80%, 100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-10px);
  }
  60% {
    transform: translateY(-5px);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .not-found-view {
    padding: var(--space-4);
  }
  
  .digit {
    font-size: 4rem;
  }
  
  .error-message h1 {
    font-size: var(--text-xl);
  }
  
  .error-message p {
    font-size: var(--text-base);
  }
  
  .ascii-art {
    display: none;
  }
  
  .error-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .error-actions .btn {
    width: 100%;
    max-width: 300px;
    justify-content: center;
  }
  
  .suggestions-grid {
    grid-template-columns: 1fr;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .error-details {
  background: var(--bg-secondary);
  border-color: var(--border);
}

[data-theme="dark"] .suggestion-card {
  background: var(--bg-secondary);
  border-color: var(--border);
}

[data-theme="dark"] .suggestion-card:hover {
  border-color: var(--brand-primary-light);
}
</style>