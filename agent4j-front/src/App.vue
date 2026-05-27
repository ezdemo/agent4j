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
            <div v-for="(v, k) in config" :key="k" class="config-row">
              <span class="config-key">{{ k }}</span>
              <span class="config-val">{{ v }}</span>
            </div>
            <div v-if="!Object.keys(config).length" class="modal-empty">加载中...</div>
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

onMounted(async () => {
  document.documentElement.setAttribute('data-theme', theme.value)
  try {
    const [s, c, t, cf, u] = await Promise.allSettled([
      agentAPI.getStatus(), sessionsAPI.getCurrent(), toolsAPI.list(), configAPI.getConfig(), configAPI.getUsage()
    ])
    if (s.status === 'fulfilled' && s.value.success) status.value = s.value.data || {}
    if (c.status === 'fulfilled' && c.value.success && c.value.data?.name) currentSession.value = c.value.data.name
    if (t.status === 'fulfilled' && t.value.success) tools.value = t.value.data || []
    if (cf.status === 'fulfilled' && cf.value.success) config.value = cf.value.data || {}
    if (u.status === 'fulfilled' && u.value.success) usage.value = u.value.data || {}
  } catch {}
  await loadSessions()
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