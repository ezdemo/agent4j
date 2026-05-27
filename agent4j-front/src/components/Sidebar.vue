<template>
  <aside class="sidebar">
    <div class="side-head">
      <button class="new-btn" @click="$emit('newChat')">
        <span>+</span>
        <span>新建对话</span>
      </button>
    </div>

    <!-- Workspace -->
    <div class="side-workspace">
      <button class="workspace-btn" @click="showWorkspacePicker = true" :title="workspace || '选择工作区'">
        <span class="ico">📁</span>
        <span class="body">
          <span class="label">工作区</span>
          <span class="name">{{ workspaceName }}</span>
        </span>
        <span class="chev">›</span>
      </button>
    </div>

    <!-- Workspace Picker Modal -->
    <div v-if="showWorkspacePicker" class="modal-overlay" @click.self="showWorkspacePicker = false">
      <div class="modal">
        <div class="modal-head">
          <h3>选择工作区</h3>
          <button class="close-btn" @click="showWorkspacePicker = false">×</button>
        </div>
        <div class="modal-body">
          <div class="workspace-list">
            <div v-if="workspaces.length === 0" class="empty-hint">暂无工作区记录</div>
            <div
              v-for="w in workspaces"
              :key="w.hash"
              class="workspace-item"
              :data-active="w.isActive"
              @click="handleSwitchWorkspace(w.path)"
            >
              <span class="ico">📁</span>
              <div class="body">
                <span class="name">{{ w.name }}</span>
                <span class="path">{{ w.path }}</span>
                <span class="meta">{{ w.sessionCount }} 个会话</span>
              </div>
              <button class="delete-btn" title="删除工作区" @click.stop="handleDeleteWorkspace(w.hash)">×</button>
            </div>
          </div>
          <div class="workspace-add">
            <input v-model="newWorkspacePath" placeholder="输入新的工作区路径…" @keyup.enter="handleAddWorkspace" />
            <button @click="handleAddWorkspace">添加</button>
          </div>
        </div>
      </div>
    </div>

    <div class="search-row">
      <div class="input">
        <span>🔍</span>
        <input v-model="query" placeholder="搜索会话…" />
      </div>
    </div>

    <div class="session-list">
      <div class="side-section">
        <div class="label">
          <span>最近会话</span>
          <span class="count">{{ filtered.length }}</span>
        </div>

        <div v-if="sessions.length === 0" class="empty-hint">暂无会话</div>
        <div v-else-if="filtered.length === 0" class="empty-hint">无匹配结果</div>

        <div
          v-for="s in filtered"
          :key="s.name"
          class="session-item"
          :data-active="s.name === activeName"
          @click="s.name !== activeName && $emit('loadSession', s.name)"
          role="button"
          tabindex="0"
          :title="s.name"
        >
          <span class="state" :style="{ background: s.name === activeName ? 'var(--accent)' : 'var(--border-strong)' }" />
          <div class="body">
            <span class="title">{{ prettyName(s) }}</span>
            <span class="meta">
              <span>{{ s.messageCount || 0 }} 条消息</span>
              <span class="sep">·</span>
              <span>{{ relativeTime(s.mtime) }}</span>
            </span>
          </div>
          <button class="rename-btn" title="重命名" @click.stop="$emit('rename', s.name)">✎</button>
          <button class="delete-btn" title="删除" @click.stop="$emit('deleteSession', s.name)">×</button>
        </div>
      </div>
    </div>

    <div class="side-foot">
      <div class="row" @click="$emit('openSettings')">
        <span class="ico">🛡</span>
        <span>审批规则</span>
      </div>
      <div class="row" @click="$emit('openAbout')">
        <span class="ico">?</span>
        <span>关于</span>
      </div>
      <div class="row" @click="$emit('openSettings')">
        <span class="ico">⚙</span>
        <span>设置</span>
        <span class="right">⌘,</span>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { configAPI } from '../services/api.js'

const props = defineProps({
  sessions: { type: Array, default: () => [] },
  activeName: { type: String, default: '' },
  workspace: { type: String, default: '' }
})

const emit = defineEmits(['newChat', 'loadSession', 'deleteSession', 'rename', 'openSettings', 'openAbout', 'workspaceChanged'])

const query = ref('')
const showWorkspacePicker = ref(false)
const workspaces = ref([])
const newWorkspacePath = ref('')

const workspaceName = computed(() => {
  if (!props.workspace) return '未设置'
  const parts = props.workspace.split(/[\\/]/)
  return parts[parts.length - 1] || props.workspace
})

const filtered = computed(() => {
  if (!query.value) return props.sessions
  const q = query.value.toLowerCase()
  return props.sessions.filter(s =>
    prettyName(s).toLowerCase().includes(q) || s.name.toLowerCase().includes(q)
  )
})

// 加载工作区列表
async function loadWorkspaces() {
  try {
    const res = await configAPI.listWorkspaces()
    if (res.ok) {
      workspaces.value = res.data || []
    }
  } catch (e) {
    console.error('加载工作区列表失败:', e)
  }
}

// 切换工作区
async function handleSwitchWorkspace(path) {
  try {
    const res = await configAPI.switchToWorkspace(path)
    if (res.ok) {
      showWorkspacePicker.value = false
      emit('workspaceChanged', path)
      await loadWorkspaces()
    } else {
      alert(res.message || '切换工作区失败')
    }
  } catch (e) {
    alert('切换工作区失败: ' + e.message)
  }
}

// 添加新工作区
async function handleAddWorkspace() {
  const path = newWorkspacePath.value.trim()
  if (!path) return
  
  try {
    const res = await configAPI.switchWorkspace(path)
    if (res.ok) {
      newWorkspacePath.value = ''
      showWorkspacePicker.value = false
      emit('workspaceChanged', path)
      await loadWorkspaces()
    } else {
      alert(res.message || '添加工作区失败')
    }
  } catch (e) {
    alert('添加工作区失败: ' + e.message)
  }
}

// 删除工作区
async function handleDeleteWorkspace(hash) {
  if (!confirm('确定要删除此工作区吗？（不会删除实际文件）')) return
  
  try {
    const res = await configAPI.deleteWorkspace(hash)
    if (res.ok) {
      await loadWorkspaces()
    } else {
      alert(res.message || '删除工作区失败')
    }
  } catch (e) {
    alert('删除工作区失败: ' + e.message)
  }
}

// 打开工作区选择器时加载列表
function openWorkspacePicker() {
  showWorkspacePicker.value = true
  loadWorkspaces()
}

onMounted(() => {
  loadWorkspaces()
})

function prettyName(s) {
  // 优先使用自动生成的会话标题
  if (s.title && s.title.trim()) return s.title.trim()
  // 然后使用会话名称
  const m = s.name.match(/(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})/)
  if (m) return `${m[2]}/${m[3]} ${m[4]}:${m[5]}`
  return s.name.replace(/[-_]+/g, ' ').slice(0, 40)
}

function relativeTime(mtime) {
  if (!mtime) return ''
  const ms = Date.now() - Date.parse(mtime)
  if (!Number.isFinite(ms)) return mtime
  const min = ms / 60000
  if (min < 1) return '刚刚'
  if (min < 60) return `${Math.floor(min)}分钟前`
  const hr = min / 60
  if (hr < 24) return `${Math.floor(hr)}小时前`
  const d = hr / 24
  if (d < 7) return `${Math.floor(d)}天前`
  return `${Math.floor(d / 7)}周前`
}
</script>

<style scoped>
.sidebar {
  background: var(--panel);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}
.side-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
}
.new-btn {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 12px;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 500;
  background: var(--accent-soft);
  color: var(--accent);
}
.new-btn:hover { background: var(--accent); color: oklch(99% 0 0); }

/* Workspace */
.side-workspace { padding: 8px 12px; }
.workspace-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--bg);
  font-size: 12px;
}
.workspace-btn:hover { background: var(--panel-2); }
.workspace-btn .ico { font-size: 14px; }
.workspace-btn .body { flex: 1; min-width: 0; text-align: left; }
.workspace-btn .label { display: block; font-size: 10px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.06em; }
.workspace-btn .name {
  display: block;
  font-size: 12px;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.workspace-btn .chev { color: var(--muted); }

/* Search */
.search-row { padding: 8px 12px; }
.search-row .input {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
  color: var(--muted);
  font-size: 13px;
}
.search-row input {
  flex: 1;
  background: none;
  border: none;
  outline: none;
  font-size: 13px;
}
.search-row input::placeholder { color: var(--muted-2); }

/* Sessions */
.session-list { flex: 1; overflow-y: auto; padding: 4px 8px; }
.side-section .label {
  display: flex;
  justify-content: space-between;
  padding: 8px 6px 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--muted-2);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
.side-section .label .count { color: var(--muted); }
.empty-hint { padding: 12px 8px; font-size: 11px; color: var(--muted-2); }

.session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--radius);
  cursor: pointer;
  transition: background 0.12s;
}
.session-item:hover { background: var(--bg-2); }
.session-item[data-active="true"] { background: var(--accent-soft); }
.session-item .state { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
.session-item .body { flex: 1; min-width: 0; }
.session-item .title {
  display: block;
  font-size: 13px;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-item .meta { display: block; font-size: 11px; color: var(--muted); }
.session-item .meta .sep { margin: 0 4px; }
.rename-btn, .delete-btn {
  width: 20px; height: 20px; border-radius: 4px;
  display: inline-flex; align-items: center; justify-content: center;
  color: var(--muted-2); opacity: 0; font-size: 12px;
}
.session-item:hover .rename-btn, .session-item:hover .delete-btn { opacity: 1; }
.rename-btn:hover { background: var(--panel-2); color: var(--fg); }
.delete-btn:hover { background: var(--danger-soft); color: var(--danger); }

/* Footer */
.side-foot {
  padding: 8px 12px;
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.side-foot .row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: var(--radius);
  font-size: 13px;
  color: var(--muted);
  cursor: pointer;
}
.side-foot .row:hover { background: var(--bg-2); color: var(--fg); }
.side-foot .row .ico { display: inline-flex; width: 16px; justify-content: center; font-size: 12px; }
.side-foot .row .right { margin-left: auto; font-size: 11px; color: var(--muted-2); }

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.modal {
  background: var(--panel);
  border-radius: var(--radius-lg, 12px);
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}
.modal-head h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--fg);
}
.close-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: var(--muted);
  background: none;
  border: none;
  cursor: pointer;
}
.close-btn:hover { background: var(--bg-2); color: var(--fg); }
.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

/* Workspace List */
.workspace-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}
.workspace-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: all 0.15s;
}
.workspace-item:hover { background: var(--bg-2); }
.workspace-item[data-active="true"] {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.workspace-item .ico { font-size: 18px; }
.workspace-item .body { flex: 1; min-width: 0; }
.workspace-item .name {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.workspace-item .path {
  display: block;
  font-size: 11px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.workspace-item .meta {
  display: block;
  font-size: 11px;
  color: var(--muted-2);
  margin-top: 2px;
}
.workspace-item .delete-btn {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--muted-2);
  opacity: 0;
  transition: opacity 0.15s;
}
.workspace-item:hover .delete-btn { opacity: 1; }
.workspace-item .delete-btn:hover {
  background: var(--danger-soft);
  color: var(--danger);
}

/* Workspace Add */
.workspace-add {
  display: flex;
  gap: 8px;
}
.workspace-add input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg);
  font-size: 13px;
  outline: none;
}
.workspace-add input:focus {
  border-color: var(--accent);
}
.workspace-add button {
  padding: 8px 16px;
  border-radius: var(--radius);
  background: var(--accent);
  color: white;
  font-size: 13px;
  font-weight: 500;
  border: none;
  cursor: pointer;
}
.workspace-add button:hover {
  opacity: 0.9;
}
</style>
