<template>
  <div class="app" :data-theme="theme">
    <!-- 全局加载指示器 -->
    <div v-if="globalLoading" class="global-loading">
      <div class="loading-spinner"></div>
    </div>
    
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: !sideOpen, 'mobile-open': mobileMenuOpen }">
      <!-- 侧边栏头部 -->
      <div class="sidebar-header">
        <div class="brand">
          <div class="brand-logo">
            <div class="logo-icon">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" fill="url(#gradient)" stroke="url(#gradient)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                <defs>
                  <linearGradient id="gradient" x1="3" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
                    <stop stop-color="#6366f1"/>
                    <stop offset="1" stop-color="#8b5cf6"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <div class="brand-text">
              <span class="brand-name">Agent4j</span>
              <span class="brand-tagline">AI Code Agent</span>
            </div>
          </div>
          <button class="btn-icon-sm sidebar-toggle" @click="sideOpen = !sideOpen" title="收起侧边栏">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path v-if="sideOpen" d="M11 17l-5-5 5-5M18 17l-5-5 5-5"/>
              <path v-else d="M13 17l5-5-5-5M6 17l5-5-5-5"/>
            </svg>
          </button>
        </div>
        
        <button class="btn btn-primary btn-sm new-chat-btn" @click="newChat">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新建对话
        </button>
      </div>
      
      <!-- 搜索框 -->
      <div class="sidebar-search">
        <div class="search-input-wrapper">
          <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input 
            v-model="searchQuery" 
            type="text" 
            placeholder="搜索会话..." 
            class="search-input"
            @focus="searchFocused = true"
            @blur="searchFocused = false"
          />
          <button v-if="searchQuery" class="btn-icon-sm search-clear" @click="searchQuery = ''">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
      </div>
      
      <!-- 会话列表 -->
      <div class="sidebar-content">
        <div class="session-section">
          <div class="section-header">
            <span class="section-title">最近会话</span>
            <span class="session-count">{{ filteredSessions.length }}</span>
          </div>
          
          <div v-if="loadingSessions" class="loading-state">
            <div class="loading-dots">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
          
          <div v-else-if="filteredSessions.length === 0" class="empty-state">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-icon">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            <p class="empty-text">{{ searchQuery ? '无匹配结果' : '暂无会话' }}</p>
            <p class="empty-hint">{{ searchQuery ? '尝试其他关键词' : '点击"新建对话"开始' }}</p>
          </div>
          
          <div v-else class="session-list">
            <div 
              v-for="(session, index) in filteredSessions" 
              :key="session.name"
              class="session-item"
              :class="{ active: session.name === currentSession }"
              :style="{ animationDelay: `${index * 50}ms` }"
              @click="loadSession(session.name)"
            >
              <div class="session-indicator"></div>
              <div class="session-info">
                <div class="session-title">{{ session.summary || formatSessionName(session.name) }}</div>
                <div class="session-meta">
                  <span class="meta-item">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                    </svg>
                    {{ session.messageCount || 0 }} 条
                  </span>
                  <span class="meta-separator">·</span>
                  <span class="meta-item">{{ timeAgo(session.mtime) }}</span>
                </div>
              </div>
              <div class="session-actions">
                <button class="btn-icon-sm session-action" @click.stop="deleteSession(session.name)" title="删除会话">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 侧边栏底部 -->
      <div class="sidebar-footer">
        <button class="footer-btn" @click="showTools = true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
          </svg>
          工具箱
        </button>
        <button class="footer-btn" @click="toggleTheme">
          <svg v-if="theme === 'light'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="5"/>
            <line x1="12" y1="1" x2="12" y2="3"/>
            <line x1="12" y1="21" x2="12" y2="23"/>
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/>
            <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/>
            <line x1="1" y1="12" x2="3" y2="12"/>
            <line x1="21" y1="12" x2="23" y2="12"/>
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/>
            <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
          </svg>
          {{ theme === 'light' ? '深色模式' : '浅色模式' }}
        </button>
        <button class="footer-btn" @click="showConfig = true">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
          设置
        </button>
      </div>
    </aside>
    
    <!-- 移动端遮罩 -->
    <div v-if="mobileMenuOpen" class="mobile-overlay" @click="mobileMenuOpen = false"></div>
    
    <!-- 主内容区 -->
    <main class="main-content">
      <!-- 顶部栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <button class="btn-icon-sm mobile-menu-btn" @click="mobileMenuOpen = true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          
          <div class="session-info-bar">
            <h1 class="current-session-title">{{ currentSession || '新对话' }}</h1>
            <div class="session-status">
              <span class="status-dot" :class="{ online: status.ready }"></span>
              <span class="status-text">{{ status.ready ? '就绪' : '连接中...' }}</span>
            </div>
          </div>
        </div>
        
        <div class="topbar-center">
          <div v-if="streaming" class="streaming-indicator">
            <div class="streaming-dot"></div>
            <span>思考中...</span>
          </div>
        </div>
        
        <div class="topbar-right">
          <div v-if="usage.totalTokens" class="usage-badge">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
            </svg>
            <span>{{ formatTokens(usage.totalTokens) }}</span>
          </div>
          
          <div class="topbar-actions">
            <button class="btn-icon-sm" @click="exportChat" title="导出对话">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
            </button>
            <button class="btn-icon-sm" @click="clearChat" title="清空对话">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </div>
        </div>
      </header>
      
      <!-- 聊天视图 -->
      <div class="chat-container">
        <ChatView ref="chatRef" hide-header style="flex: 1; min-height: 0;" />
      </div>
    </main>
    
    <!-- 工具箱模态框 -->
    <Teleport to="body">
      <div v-if="showTools" class="modal-overlay" @click.self="showTools = false">
        <div class="modal animate-scale-in">
          <div class="modal-header">
            <div class="modal-title">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
              </svg>
              <h3>工具箱</h3>
            </div>
            <button class="btn-icon-sm modal-close" @click="showTools = false">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="tools-grid">
              <div v-for="tool in tools" :key="tool.name" class="tool-card">
                <div class="tool-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
                  </svg>
                </div>
                <div class="tool-info">
                  <div class="tool-name">{{ tool.name }}</div>
                  <div class="tool-description">{{ tool.description }}</div>
                </div>
              </div>
            </div>
            <div v-if="!tools.length" class="loading-state">
              <div class="loading-spinner"></div>
              <p>加载中...</p>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
    
    <!-- 配置模态框 -->
    <Teleport to="body">
      <div v-if="showConfig" class="modal-overlay" @click.self="showConfig = false">
        <div class="modal animate-scale-in">
          <div class="modal-header">
            <div class="modal-title">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="3"/>
                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
              </svg>
              <h3>系统配置</h3>
            </div>
            <button class="btn-icon-sm modal-close" @click="showConfig = false">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="modal-body">
            <div class="config-list">
              <div v-for="(value, key) in config" :key="key" class="config-item">
                <div class="config-key">{{ key }}</div>
                <div class="config-value">{{ value }}</div>
              </div>
            </div>
            <div v-if="!Object.keys(config).length" class="loading-state">
              <div class="loading-spinner"></div>
              <p>加载中...</p>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
    
    <!-- 通知提示 -->
    <div class="notifications">
      <div 
        v-for="notification in notifications" 
        :key="notification.id"
        class="notification animate-fade-in-right"
        :class="notification.type"
      >
        <div class="notification-icon">
          <svg v-if="notification.type === 'success'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          <svg v-else-if="notification.type === 'error'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="15" y1="9" x2="9" y2="15"/>
            <line x1="9" y1="9" x2="15" y2="15"/>
          </svg>
          <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="16" x2="12" y2="12"/>
            <line x1="12" y1="8" x2="12.01" y2="8"/>
          </svg>
        </div>
        <div class="notification-content">
          <div class="notification-title">{{ notification.title }}</div>
          <div class="notification-message">{{ notification.message }}</div>
        </div>
        <button class="btn-icon-sm notification-close" @click="removeNotification(notification.id)">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, onUnmounted } from 'vue'
import { agentAPI, sessionsAPI, toolsAPI, configAPI } from './services/api'
import ChatView from './views/Chat.vue'

// 响应式状态
const theme = ref(localStorage.getItem('agent4j-theme') || 'light')
const sideOpen = ref(true)
const mobileMenuOpen = ref(false)
const searchQuery = ref('')
const searchFocused = ref(false)
const globalLoading = ref(false)
const streaming = ref(false)

// 数据状态
const sessions = ref([])
const currentSession = ref('')
const status = ref({})
const usage = ref({})
const tools = ref([])
const config = ref({})
const notifications = ref([])

// UI状态
const showTools = ref(false)
const showConfig = ref(false)
const loadingSessions = ref(false)

// ChatView引用
const chatRef = ref(null)

// 计算属性
const filteredSessions = computed(() => {
  if (!searchQuery.value) return sessions.value
  const query = searchQuery.value.toLowerCase()
  return sessions.value.filter(session => 
    (session.summary || session.name).toLowerCase().includes(query) ||
    session.name.toLowerCase().includes(query)
  )
})

// 工具函数
const formatTokens = (n) => {
  if (!n) return '0'
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

const timeAgo = (timestamp) => {
  if (!timestamp) return ''
  const now = Date.now()
  const date = Date.parse(timestamp)
  if (!Number.isFinite(date)) return ''
  
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return new Date(date).toLocaleDateString('zh-CN')
}

const formatSessionName = (name) => {
  // 尝试解析时间戳格式的会话名
  const match = name.match(/(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})/)
  if (match) {
    return `${match[2]}/${match[3]} ${match[4]}:${match[5]}`
  }
  return name.replace(/[-_]+/g, ' ').slice(0, 30)
}

// 通知系统
let notificationId = 0
const addNotification = (type, title, message, duration = 5000) => {
  const id = ++notificationId
  notifications.value.push({ id, type, title, message })
  
  if (duration > 0) {
    setTimeout(() => removeNotification(id), duration)
  }
  return id
}

const removeNotification = (id) => {
  const index = notifications.value.findIndex(n => n.id === id)
  if (index > -1) {
    notifications.value.splice(index, 1)
  }
}

// 主题切换
const toggleTheme = () => {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
  localStorage.setItem('agent4j-theme', theme.value)
  document.documentElement.setAttribute('data-theme', theme.value)
}

// 会话操作
const loadSessions = async () => {
  loadingSessions.value = true
  try {
    const response = await sessionsAPI.list()
    if (response.success) {
      sessions.value = response.data || []
    }
  } catch (error) {
    console.error('加载会话列表失败:', error)
    addNotification('error', '加载失败', '无法加载会话列表')
  } finally {
    loadingSessions.value = false
  }
}

const newChat = async () => {
  chatRef.value?.clearMessages()
  currentSession.value = ''
  chatRef.value?.sendCommand('/new')
  addNotification('success', '新会话', '已创建新对话')
}

const loadSession = async (name) => {
  currentSession.value = name
  chatRef.value?.loadSession(name)
  mobileMenuOpen.value = false
}

const deleteSession = async (name) => {
  if (!confirm(`确定删除会话 "${name}" 吗？`)) return
  
  try {
    await sessionsAPI.deleteSession(name)
    await loadSessions()
    addNotification('success', '已删除', '会话已成功删除')
    
    if (currentSession.value === name) {
      currentSession.value = ''
      chatRef.value?.clearMessages()
    }
  } catch (error) {
    console.error('删除会话失败:', error)
    addNotification('error', '删除失败', '无法删除会话')
  }
}

const clearChat = async () => {
  if (!confirm('确定清空当前对话吗？')) return
  
  chatRef.value?.clearMessages()
  currentSession.value = ''
  chatRef.value?.sendCommand('/new')
  addNotification('success', '已清空', '对话已清空')
}

const exportChat = () => {
  chatRef.value?.exportChat()
  addNotification('success', '导出成功', '对话已导出为文本文件')
}

// 键盘快捷键
const handleKeydown = (e) => {
  // Ctrl/Cmd + K: 聚焦搜索
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    searchQuery.value = ''
    document.querySelector('.search-input')?.focus()
  }
  
  // Ctrl/Cmd + N: 新建对话
  if ((e.ctrlKey || e.metaKey) && e.key === 'n') {
    e.preventDefault()
    newChat()
  }
  
  // Ctrl/Cmd + B: 切换侧边栏
  if ((e.ctrlKey || e.metaKey) && e.key === 'b') {
    e.preventDefault()
    sideOpen.value = !sideOpen.value
  }
  
  // Escape: 关闭模态框
  if (e.key === 'Escape') {
    showTools.value = false
    showConfig.value = false
    mobileMenuOpen.value = false
  }
}

// 初始化
onMounted(async () => {
  // 设置主题
  document.documentElement.setAttribute('data-theme', theme.value)
  
  // 添加键盘事件监听
  document.addEventListener('keydown', handleKeydown)
  
  // 加载初始数据
  globalLoading.value = true
  try {
    const [statusRes, currentRes, toolsRes, configRes, usageRes] = await Promise.allSettled([
      agentAPI.getStatus(),
      sessionsAPI.getCurrent(),
      toolsAPI.list(),
      configAPI.getConfig(),
      configAPI.getUsage()
    ])
    
    if (statusRes.status === 'fulfilled' && statusRes.value.success) {
      status.value = statusRes.value.data || {}
    }
    
    if (currentRes.status === 'fulfilled' && currentRes.value.success && currentRes.value.data?.name) {
      currentSession.value = currentRes.value.data.name
    }
    
    if (toolsRes.status === 'fulfilled' && toolsRes.value.success) {
      tools.value = toolsRes.value.data || []
    }
    
    if (configRes.status === 'fulfilled' && configRes.value.success) {
      config.value = configRes.value.data || {}
    }
    
    if (usageRes.status === 'fulfilled' && usageRes.value.success) {
      usage.value = usageRes.value.data || {}
    }
    
    await loadSessions()
  } catch (error) {
    console.error('初始化失败:', error)
    addNotification('error', '初始化失败', '无法加载应用数据')
  } finally {
    globalLoading.value = false
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})

// 监听流式状态
watch(() => chatRef.value?.streaming, (val) => {
  streaming.value = val
})
</script>

<style scoped>
/* 应用布局 */
.app {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--bg);
  color: var(--fg);
  transition: background var(--transition-base), color var(--transition-base);
}

/* 全局加载 */
.global-loading {
  position: fixed;
  inset: 0;
  background: var(--bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  backdrop-filter: var(--blur);
}

/* 侧边栏 */
.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  background: var(--surface);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  transition: all var(--transition-base);
  z-index: var(--z-sticky);
  position: relative;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed);
  opacity: 0.95;
}

.sidebar.collapsed .brand-text,
.sidebar.collapsed .sidebar-search,
.sidebar.collapsed .session-section,
.sidebar.collapsed .sidebar-footer .footer-btn span {
  display: none;
}

.sidebar.collapsed .sidebar-header {
  padding: var(--space-4);
}

.sidebar.collapsed .new-chat-btn {
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: var(--radius);
}

.sidebar.collapsed .new-chat-btn span {
  display: none;
}

/* 侧边栏头部 */
.sidebar-header {
  padding: var(--space-4) var(--space-4) var(--space-3);
  border-bottom: 1px solid var(--border);
}

.brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: var(--gradient-primary);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.brand-text {
  display: flex;
  flex-direction: column;
}

.brand-name {
  font-size: var(--text-lg);
  font-weight: var(--font-bold);
  color: var(--fg);
  line-height: 1.2;
}

.brand-tagline {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  font-weight: var(--font-medium);
}

.sidebar-toggle {
  color: var(--fg-muted);
  transition: all var(--transition-fast);
}

.sidebar-toggle:hover {
  color: var(--fg);
  background: var(--surface-hover);
}

.new-chat-btn {
  width: 100%;
  justify-content: center;
  font-weight: var(--font-semibold);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  transition: all var(--transition-fast);
}

.new-chat-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
}

/* 搜索框 */
.sidebar-search {
  padding: var(--space-3) var(--space-4);
}

.search-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: var(--space-3);
  color: var(--fg-muted);
  pointer-events: none;
  transition: color var(--transition-fast);
}

.search-input {
  width: 100%;
  padding: var(--space-2) var(--space-3) var(--space-2) var(--space-8);
  background: var(--bg-secondary);
  border: 1px solid transparent;
  border-radius: var(--radius);
  font-size: var(--text-sm);
  color: var(--fg);
  transition: all var(--transition-fast);
}

.search-input:focus {
  background: var(--surface);
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.search-input:focus + .search-icon {
  color: var(--brand-primary);
}

.search-clear {
  position: absolute;
  right: var(--space-2);
  color: var(--fg-muted);
  transition: all var(--transition-fast);
}

.search-clear:hover {
  color: var(--fg);
  background: var(--surface-hover);
}

/* 侧边栏内容 */
.sidebar-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-2) var(--space-3);
}

.session-section {
  margin-bottom: var(--space-4);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-1);
  margin-bottom: var(--space-2);
}

.section-title {
  font-size: var(--text-xs);
  font-weight: var(--font-semibold);
  color: var(--fg-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.session-count {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  background: var(--bg-tertiary);
  padding: 0.125rem 0.5rem;
  border-radius: var(--radius-full);
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-8) var(--space-4);
  color: var(--fg-muted);
}

.loading-dots {
  display: flex;
  gap: var(--space-1);
}

.loading-dots span {
  width: 6px;
  height: 6px;
  background: var(--fg-muted);
  border-radius: 50%;
  animation: pulse 1.4s ease-in-out infinite;
}

.loading-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.loading-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-8) var(--space-4);
  text-align: center;
}

.empty-icon {
  color: var(--fg-muted);
  margin-bottom: var(--space-3);
  opacity: 0.5;
}

.empty-text {
  font-size: var(--text-sm);
  color: var(--fg-secondary);
  margin-bottom: var(--space-1);
}

.empty-hint {
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

/* 会话列表 */
.session-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.session-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius);
  cursor: pointer;
  transition: all var(--transition-fast);
  animation: fadeInUp var(--transition-base) ease-out both;
}

.session-item:hover {
  background: var(--surface-hover);
}

.session-item.active {
  background: var(--accent-soft);
  border: 1px solid rgba(99, 102, 241, 0.2);
}

.session-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--border);
  flex-shrink: 0;
  transition: all var(--transition-fast);
}

.session-item.active .session-indicator {
  background: var(--brand-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 0.125rem;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.meta-separator {
  color: var(--fg-muted);
}

.session-actions {
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.session-item:hover .session-actions {
  opacity: 1;
}

.session-action {
  color: var(--fg-muted);
  transition: all var(--transition-fast);
}

.session-action:hover {
  color: var(--danger);
  background: var(--danger-bg);
}

/* 侧边栏底部 */
.sidebar-footer {
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.footer-btn {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius);
  font-size: var(--text-sm);
  color: var(--fg-secondary);
  transition: all var(--transition-fast);
  width: 100%;
  text-align: left;
}

.footer-btn:hover {
  background: var(--surface-hover);
  color: var(--fg);
}

/* 移动端 */
.mobile-overlay {
  position: fixed;
  inset: 0;
  background: var(--bg-overlay);
  z-index: var(--z-modal-backdrop);
  backdrop-filter: var(--blur);
}

.mobile-menu-btn {
  display: none;
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: -100%;
    top: 0;
    bottom: 0;
    z-index: var(--z-modal);
    transition: left var(--transition-base);
  }
  
  .sidebar.mobile-open {
    left: 0;
  }
  
  .mobile-menu-btn {
    display: flex;
  }
  
  .sidebar-toggle {
    display: none;
  }
}

/* 主内容区 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

/* 顶部栏 */
.topbar {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-6);
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  z-index: var(--z-sticky);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.session-info-bar {
  display: flex;
  flex-direction: column;
}

.current-session-title {
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
  color: var(--fg);
  line-height: 1.2;
}

.session-status {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--fg-muted);
  transition: all var(--transition-fast);
}

.status-dot.online {
  background: var(--success);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
}

.topbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.streaming-indicator {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-1) var(--space-3);
  background: var(--accent-soft);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  color: var(--brand-primary);
  font-weight: var(--font-medium);
}

.streaming-dot {
  width: 6px;
  height: 6px;
  background: var(--brand-primary);
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.usage-badge {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-3);
  background: var(--accent-soft);
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  color: var(--brand-primary);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

/* 聊天容器 */
.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--bg-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  backdrop-filter: var(--blur);
  padding: var(--space-4);
}

.modal {
  width: min(640px, 100%);
  max-height: 80vh;
  background: var(--surface);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-2xl);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-5) var(--space-6);
  border-bottom: 1px solid var(--border);
}

.modal-title {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.modal-title h3 {
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
  color: var(--fg);
}

.modal-title svg {
  color: var(--brand-primary);
}

.modal-close {
  color: var(--fg-muted);
  transition: all var(--transition-fast);
}

.modal-close:hover {
  color: var(--fg);
  background: var(--surface-hover);
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6);
}

/* 工具网格 */
.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
}

.tool-card {
  display: flex;
  align-items: flex-start;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--bg-secondary);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  transition: all var(--transition-fast);
}

.tool-card:hover {
  background: var(--surface-hover);
  border-color: var(--border-focus);
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.tool-icon {
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

.tool-info {
  flex: 1;
  min-width: 0;
}

.tool-name {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--fg);
  font-family: var(--font-mono);
  margin-bottom: var(--space-1);
}

.tool-description {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* 配置列表 */
.config-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.config-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  background: var(--bg-secondary);
  border-radius: var(--radius);
  border: 1px solid var(--border);
}

.config-key {
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--fg-secondary);
  font-family: var(--font-mono);
}

.config-value {
  font-size: var(--text-sm);
  color: var(--fg);
  font-family: var(--font-mono);
  word-break: break-all;
  text-align: right;
  max-width: 60%;
}

/* 通知 */
.notifications {
  position: fixed;
  top: var(--space-4);
  right: var(--space-4);
  z-index: var(--z-toast);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  max-width: 380px;
}

.notification {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border);
  animation: slideInRight var(--transition-base) ease-out;
}

.notification.success {
  border-left: 4px solid var(--success);
}

.notification.error {
  border-left: 4px solid var(--danger);
}

.notification.info {
  border-left: 4px solid var(--info);
}

.notification.warning {
  border-left: 4px solid var(--warning);
}

.notification-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.notification.success .notification-icon {
  background: var(--success-bg);
  color: var(--success);
}

.notification.error .notification-icon {
  background: var(--danger-bg);
  color: var(--danger);
}

.notification.info .notification-icon {
  background: var(--info-bg);
  color: var(--info);
}

.notification.warning .notification-icon {
  background: var(--warning-bg);
  color: var(--warning);
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--fg);
  margin-bottom: 0.125rem;
}

.notification-message {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  line-height: 1.5;
}

.notification-close {
  color: var(--fg-muted);
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.notification-close:hover {
  color: var(--fg);
  background: var(--surface-hover);
}

/* 动画 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(100%);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 响应式调整 */
@media (max-width: 1024px) {
  .sidebar {
    width: var(--sidebar-collapsed);
  }
  
  .sidebar .brand-text,
  .sidebar .sidebar-search,
  .sidebar .session-section,
  .sidebar .sidebar-footer .footer-btn span {
    display: none;
  }
  
  .sidebar .sidebar-header {
    padding: var(--space-4);
  }
  
  .sidebar .new-chat-btn {
    width: 40px;
    height: 40px;
    padding: 0;
    border-radius: var(--radius);
  }
  
  .sidebar .new-chat-btn span {
    display: none;
  }
}

@media (max-width: 768px) {
  .topbar {
    padding: 0 var(--space-4);
  }
  
  .session-info-bar {
    display: none;
  }
  
  .streaming-indicator {
    display: none;
  }
  
  .usage-badge {
    display: none;
  }
}
</style>