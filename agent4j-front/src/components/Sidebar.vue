<template>
  <aside class="sidebar" :class="{ collapsed: !sideOpen }">
    <div class="sidebar-header">
      <button class="new-task-btn" @click="$emit('show-workspace-picker')">+ 新建会话</button>

    </div>

    <div class="sidebar-search">
      <div class="search-wrapper">
        <i class="fas fa-search"></i>
        <input class="search-input" v-model="searchQuery" placeholder="搜索会话..." />
      </div>
    </div>

    <!-- 工作区选择器 -->
    <div class="sidebar-section-title">
      <span>项目</span>
      <div class="sidebar-section-actions">
        <button
          class="btn-icon-sm"
          :title="allProjectsExpanded ? '折叠所有项目会话' : '展开所有项目会话'"
          @click="toggleAllProjects"
        >
          <svg
            width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
            :style="{ transform: allProjectsExpanded ? 'rotate(0deg)' : 'rotate(-90deg)', transition: 'transform 0.2s' }"
          >
            <polyline points="18 15 12 9 6 15"/>
          </svg>
        </button>
        <button class="btn-icon-sm" title="刷新项目列表" @click="$emit('refresh-sessions')">
          <svg fill="none" height="14" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="14">
            <path d="M23 4v6h-6"/>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
          </svg>
        </button>
        <button class="btn-icon-sm" title="添加项目" @click="$emit('show-workspace-picker')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        </button>
      </div>
    </div>

    <div class="project-list">
      <div v-for="p in projectsData" :key="p.workspace.hash" class="project-item">
        <div class="project-header" :class="{ active: p.workspace.hash === currentSessionWorkspace }" @click="toggleProject(p.workspace.hash)">
          <svg class="project-chevron" :class="{ open: expandedWorkspaces.has(p.workspace.hash) }" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="project-icon">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
          </svg>
          <div class="project-info">
            <div class="project-name">{{ p.workspace.name }}</div>
            <div class="project-meta-row">
              <span class="project-path">{{ truncatePath(p.workspace.path) }}</span>
              <span class="project-sep">·</span>
              <span class="project-time">{{ relativeTime(p.workspace.lastAccessedAt) }}</span>
            </div>
          </div>
          <button class="btn-icon-sm project-new" title="新建会话" @click.stop="$emit('new-project-chat', p.workspace.hash)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          </button>
          <button class="btn-icon-sm project-refresh" title="刷新该项目会话" @click.stop="$emit('refresh-project', p.workspace.hash)">
            <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
              <path d="M23 4v6h-6"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
          </button>
          <button class="btn-icon-sm project-clear" title="管理项目会话" @click.stop="$emit('manage-project', p.workspace.hash)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="m7 21-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21"/>
              <path d="M22 21H7"/>
              <path d="m5 11 9 9"/>
            </svg>
          </button>
        </div>
        <div v-if="expandedWorkspaces.has(p.workspace.hash)" class="project-sessions">
          <div v-if="p.sessions.length === 0" class="project-empty">暂无会话</div>
          <div
            v-for="s in p.sessions"
            :key="s.name"
            class="session-item"
            :class="{ active: s.name === currentSession && currentSessionWorkspace === p.workspace.hash }"
            @click="$emit('select-session', { workspaceHash: p.workspace.hash, sessionName: s.name })"
          >
            <span class="session-dot" :class="{ on: s.name === currentSession && currentSessionWorkspace === p.workspace.hash }"></span>
            <div class="session-info">
              <div class="session-name">{{ s.title || formatName(s.name) }}</div>
              <div class="session-meta">{{ s.messageCount || 0 }}条 · {{ relativeTime(s.mtime) }}</div>
            </div>
            <div class="session-item-actions">
              <button class="btn-icon-sm session-refresh" title="刷新" @click.stop="$emit('refresh-session-chat', s.name)">
                <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                  <path d="M23 4v6h-6"/>
                  <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
                </svg>
              </button>
              <button class="btn-icon-sm session-del" @click.stop="$emit('delete-session', { workspaceHash: p.workspace.hash, sessionName: s.name })">
                <svg fill="none" height="12" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="12">
                  <line x1="18" x2="6" y1="6" y2="18"/>
                  <line x1="6" x2="18" y1="6" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
      <div v-if="workspaces.length === 0 && initialDataLoaded" class="sidebar-empty">
        暂无项目
      </div>
      <div v-if="!initialDataLoaded" class="sidebar-empty">
        加载中...
      </div>
    </div>

    <div class="sidebar-foot">
      <button class="foot-btn" @click="$emit('show-tools')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
        工具
      </button>
      <button class="foot-btn" @click="$emit('show-dashboard')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10"/><path d="M18 20V4"/><path d="M6 20v-4"/></svg>
        数据
      </button>
      <button class="foot-btn" @click="$emit('toggle-theme')">
        <!-- 浅色：月亮图标 -->
        <svg v-if="theme === 'light'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
        <!-- 深色：太阳图标 -->
        <svg v-else-if="theme === 'dark'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
        <!-- 浅绿：Material Design 风格图标 -->
        <svg v-else-if="theme === 'retro'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
        <!-- 复古黄：书本图标 -->
        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
        {{ { light: '浅色', dark: '深色', retro: '浅绿', 'retro-yellow': '复古黄' }[theme] }}
      </button>
      <button class="foot-btn" @click="$emit('show-settings')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
        设置
      </button>
    </div>
  </aside>

</template>

<script setup>
import {computed, ref} from 'vue'

const props = defineProps({
  sideOpen: { type: Boolean, default: true },
  theme: { type: String, default: 'light' },
  currentSession: { type: String, default: '' },
  currentSessionWorkspace: { type: String, default: null },
  workspaces: { type: Array, default: () => [] },
  workspaceSessions: { type: Object, default: () => ({}) },
  initialDataLoaded: { type: Boolean, default: false }
})

defineEmits([
  'update:sideOpen',
  'show-workspace-picker',
  'refresh-sessions',
  'new-project-chat',
  'refresh-project',
  'manage-project',
  'select-session',
  'refresh-session-chat',
  'delete-session',
  'toggle-theme',
  'show-tools',
  'show-dashboard',
  'show-settings'
])

// 本地搜索状态
const searchQuery = ref('')

// 本地展开/折叠状态
const expandedWorkspaces = ref(new Set())
const allProjectsExpanded = ref(true)

// 根据 props + searchQuery 计算项目列表
const projectsData = computed(() => {
  if (!props.workspaces.length) return []
  if (!searchQuery.value) {
    return props.workspaces.map(w => ({
      workspace: w,
      sessions: props.workspaceSessions[w.hash] || []
    }))
  }
  const q = searchQuery.value.toLowerCase()
  return props.workspaces
    .map(w => ({
      workspace: w,
      sessions: (props.workspaceSessions[w.hash] || [])
        .filter(s => (s.title || s.name || '').toLowerCase().includes(q))
    }))
    .filter(p => p.workspace.name.toLowerCase().includes(q) || p.sessions.length > 0)
})



// 展开/折叠单个项目
const toggleProject = (hash) => {
  const s = new Set(expandedWorkspaces.value)
  if (s.has(hash)) {
    s.delete(hash)
  } else {
    s.add(hash)
  }
  expandedWorkspaces.value = s
}

// 一键展开/折叠所有项目会话
const toggleAllProjects = () => {
  allProjectsExpanded.value = !allProjectsExpanded.value
  const s = new Set()
  if (allProjectsExpanded.value) {
    props.workspaces.forEach(w => s.add(w.hash))
  }
  expandedWorkspaces.value = s
}

// 工具函数
const truncatePath = (p) => {
  if (!p) return ''
  if (p.length <= 40) return p
  return p.slice(0, 36) + '...'
}

const relativeTime = (t) => {
  if (!t) return ''
  const d = Date.now() - (typeof t === 'number' ? t : Date.parse(t))
  if (!Number.isFinite(d)) return ''
  const min = d / 6e4
  if (min < 1) return '刚刚'
  if (min < 60) return Math.floor(min) + 'm'
  const hour = min / 60
  if (hour < 24) return Math.floor(hour) + 'h'
  const day = hour / 24
  if (day < 7) return Math.floor(day) + 'd'
  if (day < 30) return Math.floor(day / 7) + 'w'
  if (day < 365) return Math.floor(day / 30) + 'mo'
  return Math.floor(day / 365) + 'y'
}

const formatName = (n) => {
  const m = n.match(/(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})/)
  return m ? `${m[2]}/${m[3]} ${m[4]}:${m[5]}${n.slice(m.index + m[0].length)}` : n.replace(/[-_]+/g, ' ').slice(0, 24)
}
</script>

<style scoped>
.sidebar {
  width: 272px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border-right: 1px solid var(--glass-border);
  transition: width 0.2s, opacity 0.2s;
  overflow: hidden;
}
.sidebar.collapsed {
  width: 0;
  opacity: 0;
  overflow: hidden;
  pointer-events: none;
}

.sidebar-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  gap: 10px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sidebar-logo-icon {
  width: 26px;
  height: 26px;
  background: var(--text, var(--fg));
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}

.sidebar-logo-text {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: -0.3px;
}

.new-task-btn {
  background: var(--fg);
  color: var(--bg);
  border: none;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 6px;
  transition: all 0.15s;
  font-family: inherit;
}

.new-task-btn:hover {
  opacity: 0.85;
}

.sidebar-search {
  padding: 12px 12px 8px;
}

.search-wrapper {
  position: relative;
}

.search-wrapper i {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--fg-4);
  font-size: 12px;
}

.search-input {
  width: 100%;
  background: var(--bg-3);
  border: 1px solid transparent;
  border-radius: 7px;
  padding: 7px 10px 7px 30px;
  color: var(--fg);
  font-size: 13px;
  font-family: inherit;
  outline: none;
  transition: all 0.15s;
}

.search-input::placeholder {
  color: var(--fg-4);
}

.search-input:focus {
  background: var(--bg);
  border-color: var(--border-2);
}



.section-label {
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--fg-4);
  padding: 12px 8px 6px;
}

.sidebar-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 4px;
  font-size: 12px;
  font-weight: 700;
  color: var(--fg-4);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  border-bottom: 1px solid var(--border);
}

.sidebar-section-title button {
  opacity: 0.5;
  transition: opacity 0.15s;
}
.sidebar-section-title button:hover {
  opacity: 1;
  color: var(--accent);
}
.sidebar-section-title button:disabled {
  opacity: 0.2;
  cursor: not-allowed;
}

.sidebar-section-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.project-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.project-item + .project-item {
  border-top: 1px solid var(--border);
}

.project-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background var(--t);
  user-select: none;
}
.project-header:hover {
  background: var(--bg-3);
}
.project-header.active {
  border-left: 2px solid var(--accent);
  background: transparent;
}
.project-header.active .project-name {
  color: var(--accent);
}

.project-chevron {
  flex-shrink: 0;
  color: var(--fg-4);
  transition: transform 0.2s;
}
.project-chevron.open {
  transform: rotate(90deg);
}

.project-icon {
  flex-shrink: 0;
  color: var(--fg-3);
}

.project-info {
  flex: 1;
  min-width: 0;
}

.project-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-meta-row {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--fg-4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-path {
  overflow: hidden;
  text-overflow: ellipsis;
  flex-shrink: 1;
  min-width: 0;
}

.project-sep {
  flex-shrink: 0;
}

.project-time {
  flex-shrink: 0;
}

.project-count {
  font-size: 11px;
  color: var(--fg-3);
  background: var(--bg-3);
  padding: 1px 6px;
  border-radius: var(--r-sm);
  flex-shrink: 0;
}

.project-clear,
.project-refresh,
.project-new {
  opacity: 0;
  flex-shrink: 0;
  transition: opacity var(--t);
}
.project-clear:hover {
  color: var(--red);
}
.project-refresh:hover {
  color: var(--accent);
}
.project-new:hover {
  color: var(--green);
}
.project-header:hover .project-clear,
.project-header:hover .project-refresh,
.project-header:hover .project-new {
  opacity: 0.6;
}
.project-header:hover .project-clear:hover,
.project-header:hover .project-refresh:hover,
.project-header:hover .project-new:hover {
  opacity: 1;
}

.project-sessions {
  padding: 0 0 4px;
}

.project-empty {
  padding: 8px 16px 8px 32px;
  font-size: 12px;
  color: var(--fg-4);
}

.project-sessions .session-item {
  padding: 5px 12px 5px 38px;
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

.session-item-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.session-refresh,
.session-del {
  opacity: 0.3;
  transition: opacity var(--t);
}

.session-item:hover .session-refresh,
.session-item:hover .session-del { opacity: 1; }

.session-refresh:hover {
  color: var(--accent);
}
.session-del:hover { color: var(--red); }



.sidebar-foot {
  padding: 8px;
  border-top: 1px solid var(--glass-border);
  display: flex;
  gap: 2px;
}
.foot-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 4px;
  white-space: nowrap;
  border-radius: var(--r);
  font-size: 12px;
  color: var(--fg-3);
  transition: all var(--t);
}
.foot-btn:hover { background: var(--bg-3); color: var(--fg); }
.foot-btn.active { background: var(--accent-bg); color: var(--accent); }

/* 响应式 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: -272px;
    top: 33px;
    bottom: 0;
    z-index: 200;
    transition: left 0.2s;
  }
  .sidebar:not(.collapsed) { left: 0; }
}


</style>
