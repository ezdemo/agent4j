<template>
  <div class="statusbar">
    <div class="status-left">
      <div class="status-item connection-status">
        <span class="status-dot" :class="{ online: connected, offline: !connected }"></span>
        <span class="status-text">{{ connected ? '已连接' : '连接中...' }}</span>
      </div>
      
      <div class="status-divider"></div>
      
      <div class="status-item" v-if="model">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
          <line x1="8" y1="21" x2="16" y2="21"/>
          <line x1="12" y1="17" x2="12" y2="21"/>
        </svg>
        <span class="model-name">{{ model }}</span>
      </div>
    </div>
    
    <div class="status-center">
      <div v-if="busy" class="busy-indicator">
        <div class="busy-spinner"></div>
        <span>处理中...</span>
      </div>
      <div v-else class="ready-indicator">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="20 6 9 17 4 12"/>
        </svg>
        <span>就绪</span>
      </div>
    </div>
    
    <div class="status-right">
      <div class="status-item" v-if="usage.totalTokens">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
        </svg>
        <span class="token-count">{{ formatTokens(usage.totalTokens) }}</span>
        <span class="token-label">tokens</span>
      </div>
      
      <div class="status-item" v-if="usage.cacheHit">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 12a9 9 0 0 1-9 9m9-9a9 9 0 0 0-9-9m9 9H3m9 9a9 9 0 0 1-9-9m9 9c1.66 0 3-4.03 3-9s-1.34-9-3-9m0 18c-1.66 0-3-4.03-3-9s1.34-9 3-9"/>
        </svg>
        <span class="cache-label">缓存</span>
        <span class="cache-count">{{ formatTokens(usage.cacheHit) }}</span>
      </div>
      
      <div class="status-item version-item" v-if="version" @click="$emit('checkVersion')" :class="{ 'has-update': hasNewVersion }" :title="hasNewVersion ? '有新版本可用，点击查看' : ''">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <span class="version-text">v{{ version }}</span>
        <span v-if="hasNewVersion" class="version-badge">●</span>
      </div>
      
      <div class="status-item time-item">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <polyline points="12 6 12 12 16 14"/>
        </svg>
        <span>{{ currentTime }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  usage: { type: Object, default: () => ({ totalTokens: 0, cacheHit: 0 }) },
  model: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  connected: { type: Boolean, default: true },
  version: { type: String, default: '' },
  hasNewVersion: { type: Boolean, default: false }
})

const emit = defineEmits(['checkVersion'])

const currentTime = ref('')
let timeInterval = null

const formatTokens = (n) => {
  if (!n) return '0'
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

const updateTime = () => {
  currentTime.value = new Date().toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

onMounted(() => {
  updateTime()
  timeInterval = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timeInterval) {
    clearInterval(timeInterval)
  }
})
</script>

<style scoped>
.statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--footer-height, 48px);
  padding: 0 var(--space-4);
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border-top: 1px solid var(--glass-border);
  font-size: var(--text-xs);
  color: var(--fg-muted);
  user-select: none;
  transition: all var(--transition-fast);
}

.status-left,
.status-center,
.status-right {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.status-left {
  flex: 1;
}

.status-right {
  flex: 1;
  justify-content: flex-end;
}

.status-item {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
}

.status-item:hover {
  background: var(--surface-hover);
  color: var(--fg-secondary);
}

.status-item svg {
  color: var(--fg-muted);
  flex-shrink: 0;
}

/* 连接状态 */
.connection-status {
  gap: var(--space-2);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: all var(--transition-fast);
}

.status-dot.online {
  background: var(--success);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
}

.status-dot.offline {
  background: var(--warning);
  animation: pulse 2s ease-in-out infinite;
}

.status-text {
  font-weight: var(--font-medium);
}

/* 分割线 */
.status-divider {
  width: 1px;
  height: 16px;
  background: var(--border);
  margin: 0 var(--space-1);
}

/* 模型名称 */
.model-name {
  font-family: var(--font-mono);
  font-weight: var(--font-medium);
  color: var(--fg-secondary);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 中心状态 */
.busy-indicator,
.ready-indicator {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
}

.busy-indicator {
  background: var(--warning-bg);
  color: var(--warning);
}

.busy-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(245, 158, 11, 0.3);
  border-top-color: var(--warning);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.ready-indicator {
  background: var(--success-bg);
  color: var(--success);
}

.ready-indicator svg {
  color: var(--success);
}

/* Token 统计 */
.token-count,
.cache-count {
  font-family: var(--font-mono);
  font-weight: var(--font-semibold);
  color: var(--fg-secondary);
}

.token-label,
.cache-label {
  color: var(--fg-muted);
}

/* 时间 */
.time-item {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

/* 动画 */
@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(0.95);
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .statusbar {
    height: 40px;
    padding: 0 var(--space-3);
    font-size: 10px;
  }
  
  .status-center {
    display: none;
  }
  
  .model-name {
    max-width: 80px;
  }
  
  .time-item {
    display: none;
  }
  
  .status-divider {
    display: none;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .statusbar {
  background: var(--glass-bg-2);
  border-color: var(--glass-border);
}

[data-theme="dark"] .status-item:hover {
  background: var(--bg-tertiary);
}

[data-theme="dark"] .status-dot.online {
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.3);
}

/* 版本信息 */
.version-item {
  cursor: pointer;
  position: relative;
}

.version-item:hover .version-text {
  color: var(--accent);
}

.version-text {
  font-family: var(--font-mono);
  font-weight: var(--font-medium);
}

.version-badge {
  color: #ef4444;
  font-size: 8px;
  animation: pulse 2s ease-in-out infinite;
}

.has-update .version-text {
  color: #ef4444;
}

@media (max-width: 768px) {
  .version-item {
    display: none;
  }
}
</style>