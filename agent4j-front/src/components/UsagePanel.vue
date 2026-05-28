<template>
  <div class="usage-panel">
    <div class="usage-header">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 20V10"/>
        <path d="M18 20V4"/>
        <path d="M6 20v-4"/>
      </svg>
      <span>Token 用量统计</span>
      <button class="btn btn-ghost btn-sm" @click="refresh" :disabled="loading">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ 'animate-spin': loading }">
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
      </button>
    </div>

    <div v-if="loading && !usage" class="usage-loading">加载中...</div>
    <div v-else-if="!usage" class="usage-empty">暂无数据</div>
    <template v-else>
      <!-- 总 Token 数 -->
      <div class="usage-total">
        <div class="usage-total-label">会话总 Token</div>
        <div class="usage-total-value">{{ formatNumber(totalTokens) }}</div>
      </div>

      <!-- 详细指标 -->
      <div class="usage-grid">
        <div class="usage-item">
          <div class="usage-item-icon prompt">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
          </div>
          <div class="usage-item-info">
            <div class="usage-item-label">输入 Token</div>
            <div class="usage-item-value">{{ formatNumber(usage.promptTokens) }}</div>
          </div>
        </div>

        <div class="usage-item">
          <div class="usage-item-icon completion">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <path d="M14 2v6h6"/>
              <path d="M16 13H8"/>
              <path d="M16 17H8"/>
              <path d="M10 9H8"/>
            </svg>
          </div>
          <div class="usage-item-info">
            <div class="usage-item-label">输出 Token</div>
            <div class="usage-item-value">{{ formatNumber(usage.completionTokens) }}</div>
          </div>
        </div>
      </div>

      <!-- 缓存命中率 -->
      <div class="usage-cache">
        <div class="usage-cache-header">
          <span class="usage-cache-label">缓存命中率</span>
          <span class="usage-cache-percent" :class="{ high: cacheHitRate >= 50, medium: cacheHitRate >= 20 && cacheHitRate < 50, low: cacheHitRate < 20 }">
            {{ cacheHitRate.toFixed(1) }}%
          </span>
        </div>
        <div class="usage-cache-bar">
          <div class="usage-cache-bar-fill" :style="{ width: cacheHitRate + '%' }"></div>
        </div>
        <div class="usage-cache-detail">
          <span>命中: {{ formatNumber(usage.cacheHit) }}</span>
          <span>未命中: {{ formatNumber(usage.cacheMiss) }}</span>
        </div>
      </div>

      <!-- 上下文使用情况 -->
      <div class="usage-context">
        <div class="usage-context-header">
          <span class="usage-context-label">上下文使用</span>
          <span class="usage-context-percent" :class="{ high: contextUsage >= 80, medium: contextUsage >= 50 && contextUsage < 80, low: contextUsage < 50 }">
            {{ contextUsage.toFixed(1) }}%
          </span>
        </div>
        <div class="usage-context-bar">
          <!-- 折叠阈值标记 -->
          <div class="usage-context-bar-threshold" :style="{ left: '80%' }" title="折叠阈值 (80%)"></div>
          <div class="usage-context-bar-fill" :style="{ width: contextUsage + '%' }"></div>
        </div>
        <div class="usage-context-detail">
          <span>当前: {{ formatNumber(lastPromptTokens) }}</span>
          <span>窗口: {{ formatNumber(maxContextTokens) }}</span>
        </div>
        <div class="usage-context-info">
          <span>折叠阈值: {{ formatNumber(foldThreshold) }} (80%)</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { configAPI } from '../services/api'

const props = defineProps({
  autoRefresh: { type: Boolean, default: false },
  refreshInterval: { type: Number, default: 30000 }
})

const usage = ref(null)
const loading = ref(false)

const totalTokens = computed(() => {
  if (!usage.value) return 0
  return usage.value.promptTokens + usage.value.completionTokens
})

const cacheHitRate = computed(() => {
  if (!usage.value) return 0
  const total = usage.value.cacheHit + usage.value.cacheMiss
  if (total === 0) return 0
  return (usage.value.cacheHit / total) * 100
})

// 使用后端返回的 lastPromptTokens 和 maxContextTokens
const lastPromptTokens = computed(() => {
  if (!usage.value) return 0
  return usage.value.lastPromptTokens || 0
})

const maxContextTokens = computed(() => {
  if (!usage.value) return 128000
  return usage.value.maxContextTokens || 128000
})

// 折叠阈值 = maxContextTokens * 0.8
const foldThreshold = computed(() => {
  return Math.floor(maxContextTokens.value * 0.8)
})

const contextUsage = computed(() => {
  if (!usage.value || maxContextTokens.value === 0) return 0
  return (lastPromptTokens.value / maxContextTokens.value) * 100
})

const contextRemaining = computed(() => {
  if (!usage.value) return maxContextTokens.value
  return Math.max(0, maxContextTokens.value - lastPromptTokens.value)
})

const formatNumber = (num) => {
  if (num === undefined || num === null) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}

const refresh = async () => {
  loading.value = true
  try {
    const r = await configAPI.getUsage()
    if (r.success) {
      usage.value = r.data
    }
  } catch (e) {
    console.error('获取 usage 失败:', e)
  } finally {
    loading.value = false
  }
}

// 自动刷新
let timer = null
const startAutoRefresh = () => {
  if (props.autoRefresh) {
    timer = setInterval(refresh, props.refreshInterval)
  }
}

const stopAutoRefresh = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onMounted(() => {
  refresh()
  startAutoRefresh()
})

// 暴露方法给父组件
defineExpose({ refresh })
</script>

<style scoped>
.usage-panel {
  background: var(--bg-2);
  border-radius: var(--r);
  padding: 16px;
}

.usage-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  color: var(--fg);
  font-weight: 600;
  font-size: 14px;
}

.usage-header svg {
  color: var(--accent);
}

.usage-header .btn {
  margin-left: auto;
  padding: 4px;
}

.usage-loading,
.usage-empty {
  text-align: center;
  color: var(--fg-3);
  padding: 20px 0;
  font-size: 13px;
}

/* 总 Token 数 */
.usage-total {
  text-align: center;
  padding: 16px;
  background: var(--bg);
  border-radius: var(--r);
  margin-bottom: 16px;
}

.usage-total-label {
  font-size: 12px;
  color: var(--fg-3);
  margin-bottom: 4px;
}

.usage-total-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--accent);
  font-family: var(--mono);
}

/* 详细指标网格 */
.usage-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.usage-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: var(--bg);
  border-radius: var(--r);
}

.usage-item-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
}

.usage-item-icon.prompt {
  background: var(--blue-bg);
  color: var(--blue);
}

.usage-item-icon.completion {
  background: var(--green-bg);
  color: var(--green);
}

.usage-item-info {
  flex: 1;
}

.usage-item-label {
  font-size: 11px;
  color: var(--fg-3);
  margin-bottom: 2px;
}

.usage-item-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--fg);
  font-family: var(--mono);
}

/* 缓存命中率 */
.usage-cache,
.usage-context {
  padding: 12px;
  background: var(--bg);
  border-radius: var(--r);
  margin-bottom: 12px;
}

.usage-cache:last-child,
.usage-context:last-child {
  margin-bottom: 0;
}

.usage-cache-header,
.usage-context-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.usage-cache-label,
.usage-context-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--fg-2);
}

.usage-cache-percent,
.usage-context-percent {
  font-size: 14px;
  font-weight: 600;
  font-family: var(--mono);
}

.usage-cache-percent.high,
.usage-context-percent.high {
  color: var(--green);
}

.usage-cache-percent.medium,
.usage-context-percent.medium {
  color: var(--yellow);
}

.usage-cache-percent.low,
.usage-context-percent.low {
  color: var(--fg-3);
}

.usage-cache-bar,
.usage-context-bar {
  height: 6px;
  background: var(--bg-3);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 8px;
  position: relative;
}

.usage-context-bar-threshold {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--yellow);
  z-index: 1;
  opacity: 0.8;
}

.usage-cache-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--green), var(--blue));
  border-radius: 3px;
  transition: width 0.3s ease;
}

.usage-context-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--blue), var(--accent));
  border-radius: 3px;
  transition: width 0.3s ease;
}

.usage-cache-detail,
.usage-context-detail {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--fg-4);
}

.usage-context-info {
  margin-top: 4px;
  font-size: 11px;
  color: var(--fg-4);
  text-align: center;
}

/* 动画 */
.animate-spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
