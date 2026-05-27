<template>
  <div class="sessions-view">
    <div class="sessions-header">
      <h2>会话终端</h2>
      <div class="sessions-controls">
        <button class="terminal-button" @click="createNewSession">新建会话</button>
        <button class="terminal-button" @click="refreshSessions">刷新列表</button>
        <span class="session-count">{{ sessions.length }} 个会话</span>
      </div>
    </div>
    
    <div class="sessions-stats">
      <div class="stat-card">
        <div class="stat-icon">💬</div>
        <div class="stat-info">
          <div class="stat-value">{{ totalMessages }}</div>
          <div class="stat-label">总消息数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">🔧</div>
        <div class="stat-info">
          <div class="stat-value">{{ totalToolCalls }}</div>
          <div class="stat-label">工具调用</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">⏱️</div>
        <div class="stat-info">
          <div class="stat-value">{{ averageSessionLength }}</div>
          <div class="stat-label">平均时长</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">📊</div>
        <div class="stat-info">
          <div class="stat-value">{{ totalTokens }}</div>
          <div class="stat-label">总 Token</div>
        </div>
      </div>
    </div>
    
    <div class="sessions-list">
      <div v-for="session in sessions" :key="session.id" class="session-card">
        <div class="session-header">
          <div class="session-title">
            <h3>{{ session.title }}</h3>
            <span class="session-id">#{{ session.id }}</span>
          </div>
          <div class="session-status" :class="session.status">
            {{ session.status === 'active' ? '活跃' : '已结束' }}
          </div>
        </div>
        
        <div class="session-meta">
          <div class="meta-item">
            <span class="meta-label">创建时间:</span>
            <span class="meta-value">{{ session.createdAt }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">最后活动:</span>
            <span class="meta-value">{{ session.lastActivity }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">消息数:</span>
            <span class="meta-value">{{ session.messageCount }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">Token 使用:</span>
            <span class="meta-value">{{ session.tokenUsage }}</span>
          </div>
        </div>
        
        <div class="session-preview">
          <div class="preview-label">最后消息:</div>
          <div class="preview-content">{{ session.lastMessage }}</div>
        </div>
        
        <div class="session-tags">
          <span v-for="tag in session.tags" :key="tag" class="session-tag">{{ tag }}</span>
        </div>
        
        <div class="session-actions">
          <button class="terminal-button" @click="loadSession(session.id)">加载会话</button>
          <button class="terminal-button" @click="exportSession(session.id)">导出</button>
          <button class="terminal-button delete" @click="deleteSession(session.id)">删除</button>
        </div>
      </div>
    </div>
    
    <div v-if="sessions.length === 0" class="no-sessions">
      <div class="no-sessions-icon">📭</div>
      <div class="no-sessions-text">暂无会话记录</div>
      <button class="terminal-button" @click="createNewSession">创建第一个会话</button>
    </div>
    
    <div class="sessions-pagination">
      <button class="terminal-button" @click="prevPage" :disabled="currentPage === 1">
        上一页
      </button>
      <span class="page-info">第 {{ currentPage }} 页，共 {{ totalPages }} 页</span>
      <button class="terminal-button" @click="nextPage" :disabled="currentPage === totalPages">
        下一页
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { sessionsAPI, configAPI } from '../services/api'

const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)
const error = ref('')

const sessions = ref([])
const currentSession = ref(null)
const usageStats = ref({
  totalTokens: 0,
  promptTokens: 0,
  completionTokens: 0
})

const totalMessages = computed(() => {
  return sessions.value.reduce((sum, session) => sum + (session.messageCount || 0), 0)
})

const totalToolCalls = computed(() => {
  // 模拟工具调用次数
  return Math.floor(totalMessages.value * 0.6)
})

const averageSessionLength = computed(() => {
  // 模拟平均时长
  const hours = Math.floor(totalMessages.value / 10)
  return `${hours}小时${totalMessages.value % 10 * 6}分钟`
})

const totalTokens = computed(() => {
  return usageStats.value.totalTokens.toLocaleString()
})

const totalPages = computed(() => {
  return Math.ceil(sessions.value.length / pageSize)
})

const paginatedSessions = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  const end = start + pageSize
  return sessions.value.slice(start, end)
})

const loadSessions = async () => {
  loading.value = true
  error.value = ''
  
  try {
    const response = await sessionsAPI.list()
    if (response.success && response.data) {
      sessions.value = response.data.map(session => ({
        id: session.name,
        title: session.name,
        status: session.isCurrent ? 'active' : 'completed',
        createdAt: session.createdAt || new Date().toLocaleString('zh-CN'),
        lastActivity: session.lastActivity || new Date().toLocaleString('zh-CN'),
        messageCount: session.messageCount || 0,
        tokenUsage: session.tokenUsage || '0',
        lastMessage: session.lastMessage || '无消息',
        tags: session.tags || []
      }))
    } else {
      error.value = response.error || '加载会话列表失败'
    }
  } catch (err) {
    console.error('加载会话列表失败:', err)
    error.value = '加载会话列表失败: ' + err.message
  } finally {
    loading.value = false
  }
}

const loadCurrentSession = async () => {
  try {
    const response = await sessionsAPI.getCurrent()
    if (response.success && response.data) {
      currentSession.value = response.data
    }
  } catch (err) {
    console.error('加载当前会话失败:', err)
  }
}

const loadUsageStats = async () => {
  try {
    const response = await configAPI.getUsage()
    if (response.success && response.data) {
      usageStats.value = response.data
    }
  } catch (err) {
    console.error('加载用量统计失败:', err)
  }
}

const createNewSession = async () => {
  try {
    const response = await sessionsAPI.createNew()
    if (response.success) {
      await loadSessions()
      window.dispatchEvent(new CustomEvent('terminal-output', { 
        detail: { 
          type: 'system', 
          text: '已创建新会话' 
        }
      }))
    } else {
      error.value = response.error || '创建新会话失败'
    }
  } catch (err) {
    console.error('创建新会话失败:', err)
    error.value = '创建新会话失败: ' + err.message
  }
}

const refreshSessions = async () => {
  await loadSessions()
  await loadCurrentSession()
  await loadUsageStats()
  
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: '会话列表已刷新' 
    }
  }))
}

const loadSession = async (sessionId) => {
  try {
    const response = await sessionsAPI.switchSession(sessionId)
    if (response.success) {
      window.dispatchEvent(new CustomEvent('terminal-output', { 
        detail: { 
          type: 'system', 
          text: `已切换到会话: ${sessionId}` 
        }
      }))
      
      // 刷新当前会话状态
      await loadCurrentSession()
    } else {
      error.value = response.error || '加载会话失败'
    }
  } catch (err) {
    console.error('加载会话失败:', err)
    error.value = '加载会话失败: ' + err.message
  }
}

const exportSession = (sessionId) => {
  const session = sessions.value.find(s => s.id === sessionId)
  if (session) {
    const exportData = {
      ...session,
      exportedAt: new Date().toISOString()
    }
    
    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `agent4j-session-${sessionId}.json`
    a.click()
    URL.revokeObjectURL(url)
    
    window.dispatchEvent(new CustomEvent('terminal-output', { 
      detail: { 
        type: 'system', 
        text: `会话 ${sessionId} 已导出` 
      }
    }))
  }
}

const deleteSession = async (sessionId) => {
  if (confirm(`确定要删除会话 ${sessionId} 吗？此操作不可恢复。`)) {
    try {
      const response = await sessionsAPI.deleteSession(sessionId)
      if (response.success) {
        await loadSessions()
        
        window.dispatchEvent(new CustomEvent('terminal-output', { 
          detail: { 
            type: 'system', 
            text: `会话 ${sessionId} 已删除` 
          }
        }))
      } else {
        error.value = response.error || '删除会话失败'
      }
    } catch (err) {
      console.error('删除会话失败:', err)
      error.value = '删除会话失败: ' + err.message
    }
  }
}

const prevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value--
  }
}

const nextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value++
  }
}

onMounted(() => {
  loadSessions()
  loadCurrentSession()
  loadUsageStats()
})
</script>

<style scoped>
.sessions-view {
  max-width: 1000px;
  margin: 0 auto;
}

.sessions-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
}

.sessions-header h2 {
  color: var(--terminal-amber);
  font-size: var(--font-size-xl);
}

.sessions-controls {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
}

.session-count {
  color: var(--terminal-gray);
  font-size: var(--font-size-sm);
}

.sessions-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.stat-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  padding: var(--spacing-md);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  transition: all 0.2s;
}

.stat-card:hover {
  border-color: var(--terminal-green);
}

.stat-icon {
  font-size: 2rem;
}

.stat-info {
  flex: 1;
}

.stat-value {
  color: var(--terminal-green);
  font-size: var(--font-size-xl);
  font-weight: bold;
}

.stat-label {
  color: var(--terminal-gray);
  font-size: var(--font-size-sm);
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.session-card {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  padding: var(--spacing-md);
  transition: all 0.2s;
}

.session-card:hover {
  border-color: var(--terminal-green);
}

.session-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-md);
}

.session-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.session-title h3 {
  color: var(--terminal-green);
  font-size: var(--font-size-lg);
  margin: 0;
}

.session-id {
  color: var(--terminal-gray);
  font-size: var(--font-size-sm);
}

.session-status {
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 1px;
  border-radius: 4px;
}

.session-status.active {
  background: rgba(51, 255, 51, 0.1);
  color: var(--terminal-green);
  border: 1px solid var(--terminal-green);
}

.session-status.completed {
  background: rgba(136, 136, 136, 0.1);
  color: var(--terminal-gray);
  border: 1px solid var(--terminal-gray);
}

.session-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  padding: var(--spacing-sm);
  background: var(--bg-tertiary);
  border-radius: 4px;
}

.meta-item {
  display: flex;
  flex-direction: column;
}

.meta-label {
  color: var(--terminal-gray);
  font-size: var(--font-size-xs);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.meta-value {
  color: var(--terminal-green);
  font-size: var(--font-size-sm);
}

.session-preview {
  margin-bottom: var(--spacing-md);
}

.preview-label {
  color: var(--terminal-amber);
  font-size: var(--font-size-sm);
  margin-bottom: var(--spacing-xs);
}

.preview-content {
  color: var(--terminal-green);
  font-style: italic;
  padding: var(--spacing-sm);
  background: var(--bg-tertiary);
  border-left: 3px solid var(--terminal-green);
}

.session-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.session-tag {
  background: rgba(51, 255, 255, 0.1);
  color: var(--terminal-cyan);
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-size-xs);
  border: 1px solid var(--terminal-cyan);
  border-radius: 4px;
}

.session-actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.session-actions .delete {
  background: rgba(255, 51, 51, 0.1);
  color: var(--terminal-red);
  border-color: var(--terminal-red);
}

.session-actions .delete:hover {
  background: var(--terminal-red);
  color: var(--bg-primary);
}

.no-sessions {
  text-align: center;
  padding: var(--spacing-xl);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  margin-bottom: var(--spacing-lg);
}

.no-sessions-icon {
  font-size: 4rem;
  margin-bottom: var(--spacing-md);
}

.no-sessions-text {
  color: var(--terminal-gray);
  margin-bottom: var(--spacing-md);
}

.sessions-pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--spacing-md);
}

.page-info {
  color: var(--terminal-gray);
}

@media (max-width: 768px) {
  .sessions-header {
    flex-direction: column;
    gap: var(--spacing-md);
    align-items: flex-start;
  }
  
  .sessions-controls {
    width: 100%;
    justify-content: space-between;
  }
  
  .sessions-stats {
    grid-template-columns: 1fr 1fr;
  }
  
  .session-header {
    flex-direction: column;
    gap: var(--spacing-sm);
  }
  
  .session-meta {
    grid-template-columns: 1fr 1fr;
  }
  
  .session-actions {
    flex-direction: column;
  }
  
  .session-actions .terminal-button {
    width: 100%;
  }
}
</style>