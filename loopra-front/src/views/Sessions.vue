<template>
  <div class="sessions-view">
    <!-- 头部 -->
    <div class="sessions-header">
      <div class="header-left">
        <div class="header-title">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <h2>会话管理</h2>
        </div>
        <span class="session-count">{{ sessions.length }} 个会话</span>
      </div>
      <div class="header-actions">
        <button class="btn btn-secondary btn-sm" @click="refreshSessions">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10"/>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
          刷新
        </button>
        <button class="btn btn-danger btn-sm" @click="clearAllSessions" :disabled="sessions.length === 0">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
          清空所有
        </button>
        <button class="btn btn-primary btn-sm" @click="createNewSession">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新建会话
        </button>
      </div>
    </div>
    
    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ totalMessages }}</div>
          <div class="stat-label">总消息数</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ totalToolCalls }}</div>
          <div class="stat-label">工具调用</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12 6 12 12 16 14"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ averageSessionLength }}</div>
          <div class="stat-label">平均时长</div>
        </div>
      </div>
      
      <div class="stat-card">
        <div class="stat-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
        </div>
        <div class="stat-content">
          <div class="stat-value">{{ totalTokens }}</div>
          <div class="stat-label">总 Token</div>
        </div>
      </div>
    </div>
    
    <!-- 会话列表 -->
    <div class="sessions-container">
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>
      
      <div v-else-if="error" class="error-state">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
        <p>{{ error }}</p>
        <button class="btn btn-secondary btn-sm" @click="refreshSessions">重试</button>
      </div>
      
      <div v-else-if="sessions.length === 0" class="empty-state">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <h3>暂无会话</h3>
        <p>创建您的第一个会话开始使用</p>
        <button class="btn btn-primary" @click="createNewSession">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          创建会话
        </button>
      </div>
      
      <div v-else class="sessions-grid">
        <div 
          v-for="session in paginatedSessions" 
          :key="session.id"
          class="session-card"
          :class="{ active: currentSession?.id === session.id }"
        >
          <div class="session-header">
            <div class="session-title">
              <h3>{{ session.title }}</h3>
              <span class="session-id">#{{ session.id }}</span>
              <span v-if="session.worktreeMode" class="session-wt-badge" title="隔离分支模式">🌲 隔离</span>
            </div>
            <div class="session-status" :class="session.status">
              {{ session.status === 'active' ? '活跃' : '已结束' }}
            </div>
          </div>
          
          <div class="session-meta">
            <div class="meta-item">
              <span class="meta-label">创建时间</span>
              <span class="meta-value">{{ session.createdAt }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">最后活动</span>
              <span class="meta-value">{{ session.lastActivity }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">消息数</span>
              <span class="meta-value">{{ session.messageCount }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Token 使用</span>
              <span class="meta-value">{{ session.tokenUsage }}</span>
            </div>
          </div>
          
          <div class="session-preview">
            <div class="preview-label">最后消息</div>
            <div class="preview-content">{{ session.lastMessage }}</div>
          </div>
          
          <div v-if="session.tags?.length" class="session-tags">
            <span v-for="tag in session.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>
          
          <div class="session-actions">
            <button class="btn btn-secondary btn-sm" @click="loadSession(session.id)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>
                <polyline points="15 3 21 3 21 9"/>
                <line x1="10" y1="14" x2="21" y2="3"/>
              </svg>
              加载
            </button>
            <button class="btn btn-ghost btn-sm" @click="exportSession(session.id)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              导出
            </button>
            <button class="btn btn-danger btn-sm" @click="deleteSession(session.id)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
              删除
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 分页 -->
    <div v-if="sessions.length > pageSize" class="pagination">
      <button 
        class="btn btn-ghost btn-sm" 
        @click="prevPage" 
        :disabled="currentPage === 1"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
        上一页
      </button>
      <span class="page-info">
        第 {{ currentPage }} 页，共 {{ totalPages }} 页
      </span>
      <button 
        class="btn btn-ghost btn-sm" 
        @click="nextPage" 
        :disabled="currentPage === totalPages"
      >
        下一页
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {configAPI, sessionsAPI} from '../services/api'

// 状态
const currentPage = ref(1)
const pageSize = 10
const loading = ref(false)
const error = ref('')
const sessions = ref([])
const currentWorkspaceHash = ref(null)
const currentSession = ref(null)
const usageStats = ref({
  totalTokens: 0,
  promptTokens: 0,
  completionTokens: 0
})

// 计算属性
const totalMessages = computed(() => {
  return sessions.value.reduce((sum, session) => sum + (session.messageCount || 0), 0)
})

const totalToolCalls = computed(() => {
  return Math.floor(totalMessages.value * 0.6)
})

const averageSessionLength = computed(() => {
  const hours = Math.floor(totalMessages.value / 10)
  const minutes = (totalMessages.value % 10) * 6
  return `${hours}h ${minutes}m`
})

const totalTokens = computed(() => {
  const tokens = usageStats.value.totalTokens
  if (tokens >= 1000000) return (tokens / 1000000).toFixed(1) + 'M'
  if (tokens >= 1000) return (tokens / 1000).toFixed(1) + 'K'
  return tokens.toString()
})

const totalPages = computed(() => {
  return Math.ceil(sessions.value.length / pageSize)
})

const paginatedSessions = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  const end = start + pageSize
  return sessions.value.slice(start, end)
})

// 方法
const loadSessions = async () => {
  loading.value = true
  error.value = ''
  
  try {
    // 获取当前项目 hash
    let workspaceHash = null
    try {
      const workspacesResponse = await configAPI.listWorkspaces()
      console.log('项目响应:', workspacesResponse) // 调试日志
      if (workspacesResponse && workspacesResponse.data && workspacesResponse.data.length > 0) {
        // 使用第一个项目（isActive 已废弃）
        workspaceHash = workspacesResponse.data[0].hash
        console.log('使用项目 hash:', workspaceHash) // 调试日志
      }
    } catch (err) {
      console.warn('获取项目信息失败:', err)
    }
    
    currentWorkspaceHash.value = workspaceHash
    console.log('加载会话列表, workspaceHash:', workspaceHash) // 调试日志
    const response = await sessionsAPI.list(workspaceHash)
    console.log('会话列表响应:', response) // 调试日志
    
    if (response && response.data) {
      sessions.value = response.data.map(session => ({
        id: session.name,
        title: session.title || session.name,
        status: session.isCurrent ? 'active' : 'completed',
        createdAt: session.createdAt || new Date().toLocaleString('zh-CN'),
        lastActivity: session.lastActivity || new Date().toLocaleString('zh-CN'),
        messageCount: session.messageCount || 0,
        tokenUsage: formatTokenUsage(session.tokenUsage || 0),
        lastMessage: session.lastMessage || `${session.messageCount || 0} 条消息`,
        tags: session.tags || [],
        worktreeMode: !!session.worktreeMode
      }))
      console.log('解析后的会话列表:', sessions.value) // 调试日志
    } else {
      error.value = (response && response.error) || '加载会话列表失败'
    }
  } catch (err) {
    console.error('加载会话列表失败:', err)
    error.value = '加载会话列表失败: ' + err.message
  } finally {
    loading.value = false
  }
}

// 格式化 Token 用量
const formatTokenUsage = (tokens) => {
  if (!tokens || tokens === 0) return '0'
  if (tokens >= 1000000) return (tokens / 1000000).toFixed(1) + 'M'
  if (tokens >= 1000) return (tokens / 1000).toFixed(1) + 'K'
  return tokens.toString()
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
    const params = currentWorkspaceHash.value ? { workspaceHash: currentWorkspaceHash.value } : {}
    const response = await sessionsAPI.createNew(params)
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
    const response = await sessionsAPI.switchSession(sessionId, currentWorkspaceHash.value)
    if (response.success) {
      window.dispatchEvent(new CustomEvent('terminal-output', { 
        detail: { 
          type: 'system', 
          text: `已切换到会话: ${sessionId}` 
        }
      }))
      
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
    a.download = `loopra-session-${sessionId}.json`
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
      const response = await sessionsAPI.deleteSession(sessionId, currentWorkspaceHash.value)
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

const clearAllSessions = async () => {
  if (!confirm('确定要清空所有会话吗？此操作不可恢复。')) return
  
  try {
    loading.value = true
    await sessionsAPI.clearAll(currentWorkspaceHash.value)
    sessions.value = []
    loading.value = false
    
    window.dispatchEvent(new CustomEvent('terminal-output', { 
      detail: { 
        type: 'system', 
        text: '所有会话已清空' 
      }
    }))
  } catch (err) {
    console.error('清空会话失败:', err)
    error.value = '清空会话失败: ' + err.message
    loading.value = false
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

// 生命周期
onMounted(() => {
  loadSessions()
  loadCurrentSession()
  loadUsageStats()
})
</script>

<style scoped>
.sessions-view {
  padding: var(--space-6);
  max-width: 1200px;
  margin: 0 auto;
}

/* 头部 */
.sessions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-6);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--glass-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.header-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--fg);
}

.header-title svg {
  color: var(--brand-primary);
}

.header-title h2 {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
}

.session-count {
  font-size: var(--text-sm);
  color: var(--fg-muted);
  background: var(--bg-tertiary);
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-full);
}

.header-actions {
  display: flex;
  gap: var(--space-2);
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
}

.stat-card:hover {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  background: var(--accent-soft);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--brand-primary);
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  color: var(--fg);
  line-height: 1.2;
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--fg-muted);
  margin-top: 0.25rem;
}

/* 会话容器 */
.sessions-container {
  margin-bottom: var(--space-6);
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12);
  color: var(--fg-muted);
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--border);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: var(--space-4);
}

/* 错误状态 */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12);
  text-align: center;
  color: var(--fg-muted);
}

.error-state svg {
  color: var(--danger);
  margin-bottom: var(--space-4);
}

.error-state p {
  margin-bottom: var(--space-4);
  color: var(--danger);
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-12);
  text-align: center;
  color: var(--fg-muted);
}

.empty-state svg {
  color: var(--fg-muted);
  opacity: 0.5;
  margin-bottom: var(--space-4);
}

.empty-state h3 {
  font-size: var(--text-xl);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: var(--space-2);
}

.empty-state p {
  margin-bottom: var(--space-6);
}

/* 会话网格 */
.sessions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: var(--space-4);
}

/* 会话卡片 */
.session-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  transition: all var(--transition-fast);
}

.session-card:hover {
  border-color: var(--border-focus);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.session-card.active {
  border-color: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

/* 会话头部 */
.session-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.session-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.session-title h3 {
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin: 0;
}

.session-id {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  font-family: var(--font-mono);
}

.session-wt-badge {
  font-size: var(--text-xs);
  padding: 0.1rem 0.4rem;
  border-radius: var(--radius-full);
  background: var(--success-bg);
  color: var(--success);
}

.session-status {
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  padding: 0.25rem 0.5rem;
  border-radius: var(--radius-full);
}

.session-status.active {
  background: var(--success-bg);
  color: var(--success);
}

.session-status.completed {
  background: var(--bg-tertiary);
  color: var(--fg-muted);
}

/* 会话元数据 */
.session-meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  padding: var(--space-3);
  background: var(--bg-secondary);
  border-radius: var(--radius);
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.meta-label {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.meta-value {
  font-size: var(--text-sm);
  color: var(--fg);
  font-family: var(--font-mono);
}

/* 会话预览 */
.session-preview {
  margin-bottom: var(--space-4);
}

.preview-label {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  margin-bottom: var(--space-2);
}

.preview-content {
  font-size: var(--text-sm);
  color: var(--fg-secondary);
  font-style: italic;
  padding: var(--space-3);
  background: var(--bg-secondary);
  border-left: 3px solid var(--brand-primary);
  border-radius: 0 var(--radius) var(--radius) 0;
  line-height: 1.5;
}

/* 会话标签 */
.session-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.tag {
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  padding: 0.25rem 0.5rem;
  background: var(--accent-soft);
  color: var(--brand-primary);
  border-radius: var(--radius-full);
}

/* 会话操作 */
.session-actions {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border);
}

.page-info {
  font-size: var(--text-sm);
  color: var(--fg-muted);
}

/* 动画 */
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .sessions-view {
    padding: var(--space-4);
  }
  
  .sessions-header {
    flex-direction: column;
    gap: var(--space-4);
    align-items: flex-start;
  }
  
  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }
  
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .sessions-grid {
    grid-template-columns: 1fr;
  }
  
  .session-meta {
    grid-template-columns: 1fr;
  }
  
  .session-actions {
    flex-direction: column;
  }
  
  .session-actions .btn {
    width: 100%;
    justify-content: center;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .session-card {
  background: var(--bg-secondary);
  border-color: var(--border);
}

[data-theme="dark"] .session-card:hover {
  border-color: var(--brand-primary-light);
}

[data-theme="dark"] .stat-card {
  background: var(--bg-secondary);
  border-color: var(--border);
}
</style>
