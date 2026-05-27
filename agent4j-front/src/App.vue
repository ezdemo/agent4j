<template>
  <div class="app" :data-theme="theme">
    <!-- 侧边栏 -->
    <aside class="sidebar" :class="{ collapsed: !sideOpen }">
      <div class="sidebar-head">
        <div class="logo">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
          </svg>
          <span>Agent4j</span>
        </div>
        <button class="btn-icon-sm" @click="sideOpen = !sideOpen">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 17l-5-5 5-5M18 17l-5-5 5-5"/></svg>
        </button>
      </div>

      <button class="btn btn-secondary btn-sm new-btn" @click="newChat">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建对话
      </button>

      <div class="sidebar-search">
        <input v-model="searchQuery" placeholder="搜索会话..." />
      </div>

      <!-- 工作区选择器 -->
      <div class="workspace-section">
        <div class="workspace-header" @click="showWorkspacePicker = !showWorkspacePicker">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
          </svg>
          <span class="workspace-name">{{ workspaceName }}</span>
          <svg class="workspace-chevron" :class="{ open: showWorkspacePicker }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </div>
        <div v-if="showWorkspacePicker" class="workspace-dropdown">
          <div v-if="loadingWorkspaces" class="workspace-loading">加载中...</div>
          <div v-else-if="workspaces.length === 0" class="workspace-empty">暂无工作区记录</div>
          <div
            v-for="w in workspaces"
            :key="w.hash"
            class="workspace-item"
            :class="{ active: w.isActive }"
            @click="handleSwitchWorkspace(w.path)"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
            </svg>
            <div class="workspace-info">
              <div class="workspace-item-name">{{ w.name }}</div>
              <div class="workspace-item-path">{{ w.path }}</div>
            </div>
            <span class="workspace-item-count">{{ w.sessionCount }}</span>
            <button class="btn-icon-sm workspace-del" @click.stop="handleDeleteWorkspace(w.hash)" title="删除">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
            </button>
          </div>
          <div class="workspace-add">
            <input 
              v-model="newWorkspacePath" 
              placeholder="输入新工作区路径..."
              @keyup.enter="handleAddWorkspace"
            />
            <button class="btn-icon-sm" @click="handleAddWorkspace" :disabled="!newWorkspacePath.trim()">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div class="sidebar-list">
        <div v-if="loadingSessions" class="sidebar-empty">加载中...</div>
        <div v-else-if="filteredSessions.length === 0" class="sidebar-empty">
          {{ searchQuery ? '无匹配' : '暂无会话' }}
        </div>
        <div
          v-for="s in filteredSessions"
          :key="s.name"
          class="session-item"
          :class="{ active: s.name === currentSession }"
          @click="loadSession(s.name)"
        >
          <span class="session-dot" :class="{ on: s.name === currentSession }"></span>
          <div class="session-info">
            <div class="session-name">{{ s.title || s.summary || formatName(s.name) }}</div>
            <div class="session-meta">{{ s.messageCount || 0 }}条 · {{ timeAgo(s.mtime) }}</div>
          </div>
          <button class="btn-icon-sm session-del" @click.stop="deleteSession(s.name)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
      </div>

      <div class="sidebar-foot">
        <button class="foot-btn" @click="showTools = true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
          工具
        </button>
        <button class="foot-btn" @click="toggleTheme">
          <svg v-if="theme === 'light'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
          {{ theme === 'light' ? '深色' : '浅色' }}
        </button>
        <button class="foot-btn" @click="showConfig = true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
          设置
        </button>
      </div>
    </aside>

    <!-- 主区域 -->
    <main class="main">
      <!-- 顶栏 -->
      <header class="topbar">
        <button class="btn-icon-sm" @click="sideOpen = !sideOpen">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
        </button>
        <span class="topbar-title">{{ currentSessionTitle }}</span>
        <span class="topbar-status" :class="{ on: status.ready }"></span>
        <div style="flex:1"></div>
        <span v-if="usage.totalTokens" class="topbar-tokens">{{ fmtTokens(usage.totalTokens) }} tok</span>
        <button class="btn-icon-sm" @click="clearChat" title="清空">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
      </header>

      <!-- 聊天区 -->
      <ChatView ref="chatRef" hide-header style="flex:1;min-height:0" />
    </main>

    <!-- 工具弹窗 -->
    <Teleport to="body">
      <div v-if="showTools" class="modal-mask" @click.self="showTools = false">
        <div class="modal">
          <div class="modal-head">
            <span>工具列表</span>
            <button class="btn-icon-sm" @click="showTools = false">×</button>
          </div>
          <div class="modal-body">
            <div v-for="t in tools" :key="t.name" class="tool-row">
              <code>{{ t.name }}</code>
              <span>{{ t.description }}</span>
            </div>
            <div v-if="!tools.length" class="modal-empty">加载中...</div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 配置弹窗 -->
    <Teleport to="body">
      <div v-if="showConfig" class="modal-mask" @click.self="showConfig = false">
        <div class="modal">
          <div class="modal-head">
            <span>系统配置</span>
            <button class="btn-icon-sm" @click="showConfig = false">×</button>
          </div>
          <div class="modal-body">
            <!-- 工作目录切换 -->
            <div class="config-section">
              <div class="config-section-title">工作目录</div>
              <div class="workspace-control">
                <div class="workspace-current">
                  <span class="workspace-label">当前目录：</span>
                  <span class="workspace-path">{{ workspace || '未设置' }}</span>
                </div>
                <div class="workspace-input">
                  <input 
                    v-model="newWorkspace" 
                    placeholder="输入新的工作目录路径"
                    @keyup.enter="switchWorkspace"
                  />
                  <button class="btn btn-sm" @click="switchWorkspace" :disabled="!newWorkspace.trim()">
                    切换
                  </button>
                </div>
              </div>
            </div>
            
            <!-- 其他配置 -->
            <div class="config-section">
              <div class="config-section-title">系统配置</div>
              <div v-for="(v, k) in config" :key="k" class="config-row">
                <span class="config-key">{{ k }}</span>
                <span class="config-val">{{ v }}</span>
              </div>
              <div v-if="!Object.keys(config).length" class="modal-empty">加载中...</div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { agentAPI, sessionsAPI, toolsAPI, configAPI } from './services/api'
import ChatView from './views/Chat.vue'

const theme = ref(localStorage.getItem('agent4j-theme') || 'light')
const sideOpen = ref(true)
const searchQuery = ref('')
const sessions = ref([])
const currentSession = ref('')
const status = ref({})
const usage = ref({})
const tools = ref([])
const config = ref({})
const showTools = ref(false)
const showConfig = ref(false)
const loadingSessions = ref(false)
const chatRef = ref(null)
const workspace = ref('')
const newWorkspace = ref('')

// 工作区相关
const showWorkspacePicker = ref(false)
const workspaces = ref([])
const loadingWorkspaces = ref(false)
const newWorkspacePath = ref('')

const filteredSessions = computed(() => {
  if (!searchQuery.value) return sessions.value
  const q = searchQuery.value.toLowerCase()
  return sessions.value.filter(s => (s.title || s.summary || s.name).toLowerCase().includes(q))
})

const currentSessionTitle = computed(() => {
  if (!currentSession.value) return '新对话'
  const s = sessions.value.find(s => s.name === currentSession.value)
  return (s && s.title) || formatName(currentSession.value)
})

const workspaceName = computed(() => {
  if (!workspace.value) return '选择工作区'
  const parts = workspace.value.split(/[\\/]/)
  return parts[parts.length - 1] || workspace.value
})

const fmtTokens = n => !n ? '0' : n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n)

const timeAgo = t => {
  if (!t) return ''
  const d = Date.now() - Date.parse(t)
  if (!Number.isFinite(d)) return ''
  const m = d / 6e4
  if (m < 1) return '刚刚'
  if (m < 60) return Math.floor(m) + '分钟前'
  const h = m / 60
  if (h < 24) return Math.floor(h) + '小时前'
  return Math.floor(h / 24) + '天前'
}

const formatName = n => {
  const m = n.match(/(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})/)
  return m ? `${m[2]}/${m[3]} ${m[4]}:${m[5]}` : n.replace(/[-_]+/g, ' ').slice(0, 24)
}

const toggleTheme = () => {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
  localStorage.setItem('agent4j-theme', theme.value)
  document.documentElement.setAttribute('data-theme', theme.value)
}

const loadSessions = async () => {
  loadingSessions.value = true
  try {
    const r = await sessionsAPI.list()
    if (r.success) sessions.value = r.data || []
  } catch {}
  loadingSessions.value = false
}

// 加载工作区列表
const loadWorkspaces = async () => {
  loadingWorkspaces.value = true
  try {
    const r = await configAPI.listWorkspaces()
    if (r.success) workspaces.value = r.data || []
  } catch (e) {
    console.error('加载工作区列表失败:', e)
  }
  loadingWorkspaces.value = false
}

// 切换工作区
const handleSwitchWorkspace = async (path) => {
  try {
    const r = await configAPI.switchToWorkspace(path)
    if (r.success) {
      workspace.value = r.data.workspace
      showWorkspacePicker.value = false
      await loadWorkspaces()
      await loadSessions()
    } else {
      alert(r.message || '切换工作区失败')
    }
  } catch (e) {
    alert('切换工作区失败: ' + e.message)
  }
}

// 添加新工作区
const handleAddWorkspace = async () => {
  const path = newWorkspacePath.value.trim()
  if (!path) return
  
  try {
    const r = await configAPI.switchWorkspace(path)
    if (r.success) {
      workspace.value = r.data.workspace
      newWorkspacePath.value = ''
      showWorkspacePicker.value = false
      await loadWorkspaces()
      await loadSessions()
    } else {
      alert(r.message || '添加工作区失败')
    }
  } catch (e) {
    alert('添加工作区失败: ' + e.message)
  }
}

// 删除工作区
const handleDeleteWorkspace = async (hash) => {
  if (!confirm('确定要删除此工作区吗？（不会删除实际文件）')) return
  
  try {
    const r = await configAPI.deleteWorkspace(hash)
    if (r.success) {
      await loadWorkspaces()
    } else {
      alert(r.message || '删除工作区失败')
    }
  } catch (e) {
    alert('删除工作区失败: ' + e.message)
  }
}

const newChat = () => {
  chatRef.value?.clearMessages()
  currentSession.value = ''
}

const loadSession = name => {
  currentSession.value = name
  chatRef.value?.loadSession(name)
}

const deleteSession = async name => {
  if (!confirm(`删除会话？`)) return
  try { await sessionsAPI.deleteSession(name); await loadSessions() } catch {}
}

const clearChat = () => {
  if (!confirm('清空对话？')) return
  chatRef.value?.clearMessages()
  currentSession.value = ''
}

const switchWorkspace = async () => {
  const path = newWorkspace.value.trim()
  if (!path) return
  
  try {
    const r = await configAPI.switchWorkspace(path)
    if (r.success) {
      workspace.value = r.data.workspace
      newWorkspace.value = ''
      alert('工作目录已切换: ' + r.data.workspace)
    } else {
      alert('切换失败: ' + (r.message || '未知错误'))
    }
  } catch (e) {
    alert('切换失败: ' + (e.message || '网络错误'))
  }
}

onMounted(async () => {
  document.documentElement.setAttribute('data-theme', theme.value)
  try {
    const [s, c, t, cf, u] = await Promise.allSettled([
      agentAPI.getStatus(), sessionsAPI.getCurrent(), toolsAPI.list(), configAPI.getConfig(), configAPI.getUsage()
    ])
    if (s.status === 'fulfilled' && s.value.success) {
      status.value = s.value.data || {}
      if (s.value.data?.workspace) {
        workspace.value = s.value.data.workspace
      }
    }
    if (c.status === 'fulfilled' && c.value.success && c.value.data?.name) currentSession.value = c.value.data.name
    if (t.status === 'fulfilled' && t.value.success) tools.value = t.value.data || []
    if (cf.status === 'fulfilled' && cf.value.success) {
      config.value = cf.value.data || {}
      if (cf.value.data?.workspace && !workspace.value) {
        workspace.value = cf.value.data.workspace
      }
    }
    if (u.status === 'fulfilled' && u.value.success) usage.value = u.value.data || {}
  } catch {}
  await loadSessions()
  await loadWorkspaces()
})
</script>

<style scoped>
.app {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* 侧边栏 */
.sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-2);
  border-right: 1px solid var(--border);
  transition: width 0.2s, opacity 0.2s;
}
.sidebar.collapsed {
  width: 0;
  opacity: 0;
  overflow: hidden;
  pointer-events: none;
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid var(--border);
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
}

.new-btn {
  width: calc(100% - 24px);
  margin: 10px 12px 0;
  justify-content: center;
}

.sidebar-search {
  padding: 8px 12px;
}
.sidebar-search input {
  width: 100%;
  padding: 5px 8px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  font-size: 12px;
  color: var(--fg);
}
.sidebar-search input:focus {
  outline: none;
  border-color: var(--accent);
}
.sidebar-search input::placeholder { color: var(--fg-4); }

.sidebar-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px;
}

.sidebar-empty {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--fg-4);
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border-radius: var(--r);
  cursor: pointer;
  transition: background var(--t);
}
.session-item:hover { background: var(--bg-3); }
.session-item.active { background: var(--accent-bg); }

.session-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--fg-4);
  flex-shrink: 0;
}
.session-dot.on { background: var(--accent); }

.session-info { flex: 1; min-width: 0; }
.session-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-meta {
  font-size: 11px;
  color: var(--fg-4);
  margin-top: 1px;
}

.session-del {
  opacity: 0;
  transition: opacity var(--t);
}
.session-item:hover .session-del { opacity: 1; }
.session-del:hover { color: var(--red); }

.sidebar-foot {
  padding: 8px;
  border-top: 1px solid var(--border);
  display: flex;
  gap: 2px;
}
.foot-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 6px;
  border-radius: var(--r);
  font-size: 12px;
  color: var(--fg-3);
  transition: all var(--t);
}
.foot-btn:hover { background: var(--bg-3); color: var(--fg); }

/* 主区域 */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg);
}

.topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.topbar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
}

.topbar-status {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--fg-4);
}
.topbar-status.on { background: var(--green); }

.topbar-tokens {
  font-size: 11px;
  font-family: var(--mono);
  color: var(--fg-3);
  background: var(--bg-3);
  padding: 2px 6px;
  border-radius: var(--r-sm);
}

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.3);
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal {
  width: min(520px, 90vw);
  max-height: 70vh;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
  font-weight: 600;
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px;
}

.modal-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--fg-4);
}

/* 工作区选择器 */
.workspace-section {
  border-bottom: 1px solid var(--border);
  position: relative;
}

.workspace-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background var(--t);
}
.workspace-header:hover { background: var(--bg-3); }
.workspace-header svg { color: var(--fg-3); flex-shrink: 0; }
.workspace-name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.workspace-chevron {
  transition: transform 0.2s;
}
.workspace-chevron.open {
  transform: rotate(180deg);
}

.workspace-dropdown {
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  max-height: 300px;
  overflow-y: auto;
}

.workspace-loading,
.workspace-empty {
  padding: 12px;
  text-align: center;
  font-size: 12px;
  color: var(--fg-4);
}

.workspace-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background var(--t);
}
.workspace-item:hover { background: var(--bg-2); }
.workspace-item.active { background: var(--accent-bg); }
.workspace-item svg { color: var(--fg-3); flex-shrink: 0; }

.workspace-info {
  flex: 1;
  min-width: 0;
}
.workspace-item-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.workspace-item-path {
  font-size: 11px;
  color: var(--fg-4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.workspace-item-count {
  font-size: 11px;
  color: var(--fg-3);
  background: var(--bg-3);
  padding: 1px 5px;
  border-radius: var(--r-sm);
}

.workspace-del {
  opacity: 0;
  transition: opacity var(--t);
}
.workspace-item:hover .workspace-del { opacity: 1; }
.workspace-del:hover { color: var(--red); }

.workspace-add {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  border-top: 1px solid var(--border);
}
.workspace-add input {
  flex: 1;
  padding: 5px 8px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  font-size: 12px;
  color: var(--fg);
}
.workspace-add input:focus {
  outline: none;
  border-color: var(--accent);
}
.workspace-add input::placeholder { color: var(--fg-4); }

/* 工作目录切换 */
.config-section {
  margin-bottom: 16px;
}

.config-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--border);
}

.workspace-control {
  background: var(--bg-2);
  border-radius: var(--r);
  padding: 12px;
}

.workspace-current {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}

.workspace-label {
  font-weight: 500;
  color: var(--fg-2);
}

.workspace-path {
  font-family: var(--mono);
  color: var(--fg-3);
  background: var(--bg-3);
  padding: 2px 6px;
  border-radius: var(--r-sm);
  word-break: break-all;
}

.workspace-input {
  display: flex;
  gap: 8px;
}

.workspace-input input {
  flex: 1;
  padding: 6px 10px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  font-size: 13px;
  color: var(--fg);
}

.workspace-input input:focus {
  outline: none;
  border-color: var(--accent);
}

.workspace-input input::placeholder {
  color: var(--fg-4);
}

.workspace-input .btn {
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 500;
}

.workspace-input .btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tool-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.tool-row:last-child { border-bottom: none; }
.tool-row code {
  font-weight: 600;
  color: var(--accent);
  flex-shrink: 0;
  min-width: 100px;
}
.tool-row span { color: var(--fg-3); }

.config-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
}
.config-row:last-child { border-bottom: none; }
.config-key {
  font-family: var(--mono);
  font-weight: 500;
  color: var(--fg-2);
}
.config-val {
  font-family: var(--mono);
  color: var(--fg-3);
  word-break: break-all;
  text-align: right;
  max-width: 60%;
}

/* 响应式 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: -260px;
    top: 0;
    bottom: 0;
    z-index: 200;
    transition: left 0.2s;
  }
  .sidebar:not(.collapsed) { left: 0; }
  .topbar-title { display: none; }
  .topbar-tokens { display: none; }
}
</style>