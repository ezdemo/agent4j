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
import { ref, computed } from 'vue'

const currentPage = ref(1)
const pageSize = 10

const sessions = ref([
  {
    id: 'sess-001',
    title: 'Java 项目分析',
    status: 'active',
    createdAt: '2024-03-20 14:30',
    lastActivity: '2024-03-20 15:45',
    messageCount: 12,
    tokenUsage: '15,420',
    lastMessage: '请帮我分析一下这个项目的架构设计...',
    tags: ['Java', '架构分析', '代码审查']
  },
  {
    id: 'sess-002',
    title: 'Vue3 组件开发',
    status: 'completed',
    createdAt: '2024-03-19 10:15',
    lastActivity: '2024-03-19 12:30',
    messageCount: 8,
    tokenUsage: '9,850',
    lastMessage: '这个组件已经完成了，可以正常工作。',
    tags: ['Vue3', '前端开发', '组件']
  },
  {
    id: 'sess-003',
    title: '数据库优化',
    status: 'completed',
    createdAt: '2024-03-18 16:20',
    lastActivity: '2024-03-18 18:10',
    messageCount: 15,
    tokenUsage: '18,720',
    lastMessage: '优化后的查询性能提升了3倍。',
    tags: ['SQL', '性能优化', '数据库']
  },
  {
    id: 'sess-004',
    title: 'API 设计讨论',
    status: 'active',
    createdAt: '2024-03-20 09:00',
    lastActivity: '2024-03-20 09:45',
    messageCount: 5,
    tokenUsage: '6,340',
    lastMessage: '我们需要设计一个RESTful API来处理用户认证。',
    tags: ['API', 'RESTful', '设计']
  }
])

const totalMessages = computed(() => {
  return sessions.value.reduce((sum, session) => sum + session.messageCount, 0)
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
  return sessions.value.reduce((sum, session) => {
    return sum + parseInt(session.tokenUsage.replace(/,/g, ''))
  }, 0).toLocaleString()
})

const totalPages = computed(() => {
  return Math.ceil(sessions.value.length / pageSize)
})

const paginatedSessions = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  const end = start + pageSize
  return sessions.value.slice(start, end)
})

const createNewSession = () => {
  const newSession = {
    id: `sess-${String(sessions.value.length + 1).padStart(3, '0')}`,
    title: `新会话 ${sessions.value.length + 1}`,
    status: 'active',
    createdAt: new Date().toLocaleString('zh-CN'),
    lastActivity: new Date().toLocaleString('zh-CN'),
    messageCount: 0,
    tokenUsage: '0',
    lastMessage: '会话刚刚创建',
    tags: ['新会话']
  }
  
  sessions.value.unshift(newSession)
  
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: `已创建新会话: ${newSession.id}` 
    }
  }))
}

const refreshSessions = () => {
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: '会话列表已刷新' 
    }
  }))
}

const loadSession = (sessionId) => {
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: `正在加载会话: ${sessionId}` 
    }
  }))
  
  // 实际应用中会导航到聊天页面并加载会话
  // router.push('/chat?session=' + sessionId)
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

const deleteSession = (sessionId) => {
  if (confirm(`确定要删除会话 ${sessionId} 吗？此操作不可恢复。`)) {
    const index = sessions.value.findIndex(s => s.id === sessionId)
    if (index > -1) {
      sessions.value.splice(index, 1)
      
      window.dispatchEvent(new CustomEvent('terminal-output', { 
        detail: { 
          type: 'system', 
          text: `会话 ${sessionId} 已删除` 
        }
      }))
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