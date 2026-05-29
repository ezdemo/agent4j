<template>
  <div class="composer-wrapper">
    <div class="composer-container">
      <!-- 排队消息 -->
      <div v-if="queuedSends.length" class="queued-messages">
        <div class="queued-header">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <line x1="3" y1="9" x2="21" y2="9"/>
            <line x1="9" y1="21" x2="9" y2="9"/>
          </svg>
          <span>{{ queuedSends.length }} 条排队消息</span>
        </div>
        <div class="queued-list">
          <div v-for="(text, i) in queuedSends" :key="i" class="queued-item">
            <span class="queued-text">{{ text.length > 50 ? text.slice(0, 50) + '...' : text }}</span>
            <button class="btn-icon-sm queued-remove" @click="$emit('dequeue', i)">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
      
      <!-- 输入框 -->
      <div class="composer" :class="{ focused, busy }">
        <!-- 斜杠命令弹窗 -->
        <Transition name="popup">
          <div v-if="slashPopup" class="slash-popup">
            <div class="popup-header">
              <span class="popup-title">可用命令</span>
              <span class="popup-hint">输入 / 触发</span>
            </div>
            <div class="popup-list">
              <div
                v-for="(cmd, index) in filteredSlashCmds"
                :key="cmd.cmd"
                class="popup-item"
                :class="{ active: index === activePopupIdx }"
                @click="selectSlashCmd(cmd)"
                @mouseenter="activePopupIdx = index"
              >
                <div class="cmd-icon">{{ getSlashIcon(cmd.cmd) }}</div>
                <div class="cmd-info">
                  <div class="cmd-name">
                    {{ cmd.cmd }}
                    <span v-if="cmd.type === 'skill'" class="cmd-badge skill">skill</span>
                    <span v-else-if="cmd.type === 'mode'" class="cmd-badge mode">模式</span>
                  </div>
                  <div class="cmd-desc">{{ cmd.desc }}</div>
                </div>
                <div v-if="cmd.shortcut" class="cmd-shortcut">
                  <kbd>{{ cmd.shortcut }}</kbd>
                </div>
              </div>
              <div v-if="!loaded" class="popup-empty popup-loading">
                <span class="loading-dot"></span> 加载命令中...
              </div>
              <div v-else-if="filteredSlashCmds.length === 0" class="popup-empty">
                无匹配命令
              </div>
            </div>
          </div>
        </Transition>
        
        <!-- 输入区域 -->
        <div class="input-area">
          <!-- TODO 图标按钮 -->
          <div class="todo-trigger" 
               @mouseenter="handleTodoMouseEnter" 
               @mouseleave="handleTodoMouseLeave">
            <button class="todo-btn" :class="{ 'has-todos': todos.length > 0 }">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 11l3 3L22 4"/>
                <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
              </svg>
              <span v-if="todos.length > 0" class="todo-badge">{{ todoStats.total }}</span>
            </button>
            
            <!-- TODO Tooltip -->
            <Transition name="tooltip">
              <div v-if="todoTooltipVisible" class="todo-tooltip">
                <div class="todo-tooltip-header">
                  <span class="todo-tooltip-title">📋 当前任务</span>
                  <span class="todo-tooltip-stats">
                    {{ todoStats.completed }}/{{ todoStats.total }} 完成
                  </span>
                </div>
                <div v-if="todos.length === 0" class="todo-tooltip-empty">
                  暂无任务
                </div>
                <div v-else class="todo-tooltip-list">
                  <div v-for="(todo, index) in todos" :key="index" class="todo-tooltip-item">
                    <span class="todo-status-icon">{{ getTodoStatusIcon(todo.status) }}</span>
                    <span class="todo-content" :class="todo.status">{{ todo.content }}</span>
                  </div>
                </div>
                <div v-if="todoStats.inProgress > 0" class="todo-tooltip-footer">
                  <div class="todo-progress-bar">
                    <div class="todo-progress-fill" :style="{ width: (todoStats.completed / todoStats.total * 100) + '%' }"></div>
                  </div>
                </div>
              </div>
            </Transition>
          </div>
          
          <textarea
            ref="textareaRef"
            :value="draft"
            @input="handleInput"
            @keydown="handleKeyDown"
            @focus="focused = true"
            @blur="handleBlur"
            :placeholder="busy ? '处理中... (Esc 中断)' : '输入消息... (Enter 发送, Shift+Enter 换行)'"
            rows="1"
            class="message-textarea"
          ></textarea>
          
          <!-- 操作栏 -->
          <div class="composer-toolbar">
            <div class="toolbar-left">
              <button class="toolbar-btn" @click="$emit('clear')" title="清空对话">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
              </button>
            </div>
            
            <div class="toolbar-center">
              <!-- 模式切换 -->
              <div class="mode-switcher">
                <button
                  v-for="mode in modes"
                  :key="mode.key"
                  class="mode-btn"
                  :class="{ active: editMode === mode.key }"
                  @click="$emit('setMode', mode.key)"
                  :title="mode.hint"
                >
                  <span class="mode-icon">{{ mode.icon }}</span>
                  <span class="mode-label">{{ mode.label }}</span>
                </button>
              </div>
            </div>
            
            <div class="toolbar-right">
              <!-- 模型选择 -->
              <button class="model-selector" @click="$emit('cycleModel')" :title="'当前模型: ' + modelLabel">
                <span class="model-badge">{{ modelLabel }}</span>
              </button>
              
              <!-- 发送按钮 -->
              <button
                class="send-btn"
                :class="{ active: draft.trim() && !busy }"
                @click="handleSend"
                :disabled="!draft.trim() || busy"
                title="发送消息 (Enter)"
              >
                <svg v-if="busy" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="animate-spin">
                  <path d="M21 12a9 9 0 11-6.219-8.56"/>
                </svg>
                <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="22" y1="2" x2="11" y2="13"/>
                  <polygon points="22 2 15 22 11 13 2 9 22 2"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 底部提示 -->
      <div class="composer-footer">
        <div v-if="busy" class="busy-status">
          <div class="busy-indicator">
            <div class="busy-dot"></div>
            <span>{{ busyLabel || '推理中' }}</span>
          </div>
          <div class="busy-hint">
            <kbd>Esc</kbd> 中断
          </div>
        </div>
        <div v-else class="ready-hints">
          <div class="hint-group">
            <span class="hint-item">
              <kbd>/</kbd> 命令
            </span>
            <span class="hint-divider"></span>
            <span class="hint-item">
              <kbd>Enter</kbd> 发送
            </span>
            <span class="hint-divider"></span>
            <span class="hint-item">
              <kbd>Shift</kbd> + <kbd>Enter</kbd> 换行
            </span>
          </div>
          <div v-if="editMode !== 'auto'" class="mode-hint">
            <span class="mode-tag" :class="editMode">{{ modeLabel }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { agentAPI } from '../services/api.js'

const props = defineProps({
  draft: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  busyLabel: { type: String, default: '' },
  modelLabel: { type: String, default: 'deepseek' },
  editMode: { type: String, default: 'auto' },
  queuedSends: { type: Array, default: () => [] },
  slashCommands: { type: Array, default: () => [] },
  todos: { type: Array, default: () => [] }
})

const emit = defineEmits([
  'update:draft', 'send', 'abort', 'setMode', 'cycleModel', 'clear', 'dequeue', 'export', 'slash'
])

// 响应式状态
const textareaRef = ref(null)
const focused = ref(false)
const slashPopup = ref(false)
const slashQuery = ref('')
const activePopupIdx = ref(0)
const todoTooltipVisible = ref(false)
const todoTooltipTimer = ref(null)

// 从后端获取的命令和 skill 列表
const backendCommands = ref([])
const backendSkills = ref([])
const loaded = ref(false)

// 模式配置
const modes = [
  { key: 'plan', label: '计划', icon: '📋', hint: '只读探索模式' },
  { key: 'review', label: '审核', icon: '🛡', hint: '需要确认' },
  { key: 'auto', label: '自动', icon: '⚡', hint: '自动执行' }
]

const modeLabel = computed(() => {
  const mode = modes.find(m => m.key === props.editMode)
  return mode ? mode.label : props.editMode
})

// 加载命令和 skill 列表
const fetchCommandsAndSkills = async () => {
  if (loaded.value) return
  try {
    const [cmdRes, skillRes] = await Promise.allSettled([
      agentAPI.getCommands(),
      agentAPI.getSkills()
    ])
    if (cmdRes.status === 'fulfilled' && cmdRes.value.success && cmdRes.value.data) {
      backendCommands.value = cmdRes.value.data
    }
    if (skillRes.status === 'fulfilled' && skillRes.value.success && skillRes.value.data) {
      backendSkills.value = skillRes.value.data
    }
  } catch (e) {
    console.warn('加载命令/skill列表失败:', e)
  } finally {
    loaded.value = true
  }
}

onMounted(() => {
  fetchCommandsAndSkills()
})

// 斜杠命令
const defaultSlashCmds = [
  { cmd: '/new', desc: '新建对话', shortcut: 'Ctrl+N', type: 'session' },
  { cmd: '/clear', desc: '清空对话', type: 'session' },
  { cmd: '/retry', desc: '重试最后一条', shortcut: 'Ctrl+R', type: 'session' },
  { cmd: '/compact', desc: '折叠上下文', type: 'session' },
  { cmd: '/export', desc: '导出对话', shortcut: 'Ctrl+E', type: 'session' },
  { cmd: '/plan', desc: '进入计划模式', type: 'mode' },
  { cmd: '/execute', desc: '退出计划模式', type: 'mode' }
]

// 合并后端命令（优先使用后端数据）
const mergedCommands = computed(() => {
  if (backendCommands.value.length > 0) {
    // 后端命令已包含完整信息，直接使用
    return backendCommands.value.map(cmd => ({
      cmd: cmd.cmd,
      desc: cmd.desc,
      type: cmd.type || 'system',
      argHint: cmd.argHint
    }))
  }
  return defaultSlashCmds
})

// Skill 列表（转换为命令格式）
const skillCommands = computed(() => {
  return backendSkills.value.map(skill => ({
    cmd: `/skill:${skill.name}`,
    desc: skill.description || '运行 skill',
    type: 'skill',
    runAs: skill.runAs
  }))
})

// 所有可用命令（命令 + skill）
const allSlashCmds = computed(() => [...mergedCommands.value, ...skillCommands.value, ...props.slashCommands])

const filteredSlashCmds = computed(() => {
  if (!slashQuery.value) return allSlashCmds.value
  const query = slashQuery.value.toLowerCase()
  return allSlashCmds.value.filter(cmd => 
    cmd.cmd.toLowerCase().includes(query) || 
    cmd.desc.toLowerCase().includes(query)
  )
})

const getSlashIcon = (cmd) => {
  const icons = {
    '/new': '✨',
    '/clear': '🗑️',
    '/retry': '🔄',
    '/compact': '📦',
    '/export': '📥',
    '/plan': '📋',
    '/execute': '⚡',
    '/sessions': '📂',
    '/load': '📂',
    '/rewind': '⏪',
    '/init': '🔧',
    '/hitl': '🛡',
    '/agree': '✅',
    '/deny': '❌',
    '/help': '❓',
    '/exit': '👋'
  }
  // Skill 图标
  if (cmd.startsWith('/skill:')) return '🧩'
  return icons[cmd] || '🔧'
}

// 输入处理
const handleInput = (e) => {
  const value = e.target.value
  emit('update:draft', value)
  
  // 检测斜杠命令
  const slashMatch = value.match(/(^|\s)(\/)([^\s]*)$/)
  if (slashMatch) {
    slashPopup.value = true
    slashQuery.value = slashMatch[3] || ''
    activePopupIdx.value = 0
    // 如果后端数据还没加载，触发加载
    if (!loaded.value || (backendCommands.value.length === 0 && backendSkills.value.length === 0)) {
      loaded.value = false // 重置以允许重试
      fetchCommandsAndSkills()
    }
  } else {
    slashPopup.value = false
  }
  
  // 自动调整高度
  autoResize()
}

const handleKeyDown = (e) => {
  // 斜杠命令导航
  if (slashPopup.value) {
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault()
        activePopupIdx.value = (activePopupIdx.value + 1) % filteredSlashCmds.value.length
        return
      case 'ArrowUp':
        e.preventDefault()
        activePopupIdx.value = (activePopupIdx.value - 1 + filteredSlashCmds.value.length) % filteredSlashCmds.value.length
        return
      case 'Escape':
        e.preventDefault()
        slashPopup.value = false
        return
      case 'Enter':
        e.preventDefault()
        if (filteredSlashCmds.value.length > 0) {
          selectSlashCmd(filteredSlashCmds.value[activePopupIdx.value])
        }
        return
    }
  }
  
  // 发送消息
  if (e.key === 'Enter' && !e.shiftKey && !slashPopup.value) {
    e.preventDefault()
    if (props.busy) {
      emit('abort')
    } else if (props.draft.trim()) {
      handleSend()
    }
  }
  
  // 中断处理
  if (e.key === 'Escape' && props.busy) {
    e.preventDefault()
    emit('abort')
  }
  
  // 快捷键
  if (e.ctrlKey || e.metaKey) {
    switch (e.key) {
      case 'n':
        e.preventDefault()
        emit('clear')
        break
      case 'r':
        e.preventDefault()
        emit('slash', 'retry')
        break
      case 'e':
        e.preventDefault()
        emit('export')
        break
    }
  }
}

const handleBlur = () => {
  // 延迟关闭，允许点击命令
  setTimeout(() => {
    focused.value = false
    slashPopup.value = false
  }, 200)
}

const autoResize = () => {
  const textarea = textareaRef.value
  if (textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, 200) + 'px'
  }
}

// 发送消息
const handleSend = () => {
  if (!props.draft.trim() || props.busy) return
  emit('send')
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })
}

// 选择斜杠命令
const selectSlashCmd = (cmd) => {
  slashPopup.value = false
  slashQuery.value = ''
  emit('update:draft', '')
  
  // 执行命令
  if (cmd.cmd === '/clear') {
    emit('clear')
  } else if (cmd.cmd === '/export') {
    emit('export')
  } else if (cmd.type === 'skill') {
    // Skill 命令：发送 /skill:name 格式
    emit('slash', cmd.cmd.slice(1))
  } else if (cmd.cmd) {
    emit('slash', cmd.cmd.slice(1)) // 移除前面的 /
  }
}

// TODO tooltip 处理
const handleTodoMouseEnter = () => {
  todoTooltipTimer.value = setTimeout(() => {
    todoTooltipVisible.value = true
    emit('fetchTodos')
  }, 300)
}

const handleTodoMouseLeave = () => {
  clearTimeout(todoTooltipTimer.value)
  todoTooltipVisible.value = false
}

// 获取 TODO 状态图标
const getTodoStatusIcon = (status) => {
  switch (status) {
    case 'completed': return '✅'
    case 'in_progress': return '🔄'
    case 'pending': return '⬜'
    default: return '❓'
  }
}

// 计算 TODO 统计
const todoStats = computed(() => {
  const total = props.todos.length
  const completed = props.todos.filter(t => t.status === 'completed').length
  const inProgress = props.todos.filter(t => t.status === 'in_progress').length
  const pending = props.todos.filter(t => t.status === 'pending').length
  return { total, completed, inProgress, pending }
})

// 监听draft变化
watch(() => props.draft, (newVal) => {
  if (!newVal && slashPopup.value) {
    slashPopup.value = false
  }
})

// 暴露方法
defineExpose({
  focus: () => textareaRef.value?.focus(),
  autoResize
})
</script>

<style scoped>
/* 包装器 */
.composer-wrapper {
  padding: var(--space-4) var(--space-6);
  background: var(--surface);
  border-top: 1px solid var(--border);
  position: relative;
}

.composer-wrapper::before {
  content: '';
  position: absolute;
  top: -24px;
  left: 0;
  right: 0;
  height: 24px;
  background: linear-gradient(to top, var(--surface), transparent);
  pointer-events: none;
}

.composer-container {
  max-width: 800px;
  margin: 0 auto;
}

/* 排队消息 */
.queued-messages {
  margin-bottom: var(--space-3);
  padding: var(--space-3);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

.queued-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--fg-secondary);
  margin-bottom: var(--space-2);
}

.queued-header svg {
  color: var(--warning);
}

.queued-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.queued-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.queued-text {
  font-size: var(--text-sm);
  color: var(--fg);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queued-remove {
  color: var(--fg-muted);
  transition: color var(--transition-fast);
}

.queued-remove:hover {
  color: var(--danger);
}

/* 输入框 */
.composer {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  overflow: visible;
  position: relative;
  transition: all var(--transition-fast);
}

.composer.focused {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.composer.busy {
  border-color: var(--warning);
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.1);
}

/* 输入区域 */
.input-area {
  position: relative;
}

/* 输入区域布局 */
.input-area {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
}

/* TODO 触发器 */
.todo-trigger {
  position: relative;
  padding: var(--space-3) 0 var(--space-3) var(--space-3);
  flex-shrink: 0;
}

.todo-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fg-muted);
  transition: all var(--transition-fast);
  position: relative;
}

.todo-btn:hover {
  background: var(--surface-hover);
  color: var(--fg);
}

.todo-btn.has-todos {
  color: var(--brand-primary);
}

.todo-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  background: var(--brand-primary);
  color: white;
  font-size: 10px;
  font-weight: var(--font-bold);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* TODO Tooltip */
.todo-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  width: 280px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  overflow: hidden;
}

.todo-tooltip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
}

.todo-tooltip-title {
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--fg);
}

.todo-tooltip-stats {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  background: var(--surface-hover);
  padding: 2px 8px;
  border-radius: var(--radius-sm);
}

.todo-tooltip-empty {
  padding: var(--space-4);
  text-align: center;
  color: var(--fg-muted);
  font-size: var(--text-sm);
}

.todo-tooltip-list {
  max-height: 200px;
  overflow-y: auto;
  padding: var(--space-2);
}

.todo-tooltip-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius);
  transition: background var(--transition-fast);
}

.todo-tooltip-item:hover {
  background: var(--surface-hover);
}

.todo-status-icon {
  flex-shrink: 0;
  font-size: 12px;
  line-height: 1.5;
}

.todo-content {
  font-size: var(--text-sm);
  color: var(--fg);
  line-height: 1.5;
}

.todo-content.completed {
  text-decoration: line-through;
  color: var(--fg-muted);
}

.todo-content.in_progress {
  color: var(--brand-primary);
  font-weight: var(--font-medium);
}

.todo-tooltip-footer {
  padding: var(--space-2) var(--space-4) var(--space-3);
  border-top: 1px solid var(--border);
}

.todo-progress-bar {
  height: 4px;
  background: var(--bg-secondary);
  border-radius: 2px;
  overflow: hidden;
}

.todo-progress-fill {
  height: 100%;
  background: var(--gradient-primary);
  border-radius: 2px;
  transition: width var(--transition-normal);
}

/* Tooltip 动画 */
.tooltip-enter-active,
.tooltip-leave-active {
  transition: all 0.2s ease;
}

.tooltip-enter-from,
.tooltip-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

.message-textarea {
  display: block;
  width: 100%;
  min-height: 24px;
  max-height: 200px;
  padding: var(--space-3) var(--space-4);
  background: none;
  border: none;
  outline: none;
  resize: none;
  font-family: inherit;
  font-size: var(--text-base);
  line-height: 1.6;
  color: var(--fg);
  flex: 1;
}

.message-textarea::placeholder {
  color: var(--fg-muted);
}

/* 工具栏 */
.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-2) var(--space-3);
  border-top: 1px solid var(--border);
}

.toolbar-left,
.toolbar-center,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.toolbar-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fg-muted);
  transition: all var(--transition-fast);
}

.toolbar-btn:hover {
  background: var(--surface-hover);
  color: var(--fg);
}

/* 模式切换 */
.mode-switcher {
  display: flex;
  gap: var(--space-1);
  background: var(--bg-secondary);
  border-radius: var(--radius);
  padding: var(--space-1);
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  color: var(--fg-muted);
  transition: all var(--transition-fast);
}

.mode-btn:hover {
  color: var(--fg);
  background: var(--surface-hover);
}

.mode-btn.active {
  background: var(--accent-soft);
  color: var(--brand-primary);
  box-shadow: var(--shadow-xs);
}

.mode-icon {
  font-size: 12px;
}

.mode-label {
  display: none;
}

@media (min-width: 640px) {
  .mode-label {
    display: inline;
  }
}

/* 模型选择 */
.model-selector {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  font-size: var(--text-xs);
  color: var(--fg-secondary);
  transition: all var(--transition-fast);
}

.model-selector:hover {
  background: var(--surface-hover);
  border-color: var(--fg-muted);
}

.model-badge {
  font-family: var(--font-mono);
  font-weight: var(--font-medium);
  color: var(--brand-primary);
}

/* 发送按钮 */
.send-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  color: var(--fg-muted);
  transition: all var(--transition-fast);
}

.send-btn.active {
  background: var(--gradient-primary);
  color: white;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.send-btn.active:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.4);
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

/* 斜杠命令弹窗 */
.slash-popup {
  position: absolute;
  bottom: calc(100% + var(--space-2));
  left: 0;
  right: 0;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  z-index: var(--z-dropdown);
  overflow: hidden;
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--border);
  background: var(--bg-secondary);
}

.popup-title {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--fg);
}

.popup-hint {
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

.popup-list {
  max-height: 280px;
  overflow-y: auto;
  padding: var(--space-2);
}

.popup-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.popup-item:hover,
.popup-item.active {
  background: var(--accent-soft);
}

.cmd-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  background: var(--bg-secondary);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.cmd-info {
  flex: 1;
  min-width: 0;
}

.cmd-name {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  font-family: var(--font-mono);
  color: var(--fg);
  display: flex;
  align-items: center;
  gap: 4px;
}

.cmd-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 4px;
  font-size: 10px;
  font-weight: var(--font-medium);
  border-radius: var(--radius-sm);
  line-height: 1.4;
}

.cmd-badge.skill {
  background: var(--success-bg, #dcfce7);
  color: var(--success, #16a34a);
}

.cmd-badge.mode {
  background: var(--info-bg, #dbeafe);
  color: var(--info, #2563eb);
}

.cmd-desc {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  margin-top: 0.125rem;
}

.cmd-shortcut kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 var(--space-1);
  background: var(--bg-tertiary);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: var(--font-medium);
  color: var(--fg-secondary);
}

.popup-empty {
  padding: var(--space-4);
  text-align: center;
  font-size: var(--text-sm);
  color: var(--fg-muted);
}

.popup-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  color: var(--brand-primary);
}

.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--brand-primary);
  animation: loading-bounce 0.6s infinite alternate;
}

@keyframes loading-bounce {
  from { opacity: 0.3; transform: scale(0.8); }
  to   { opacity: 1;   transform: scale(1.2); }
}

/* 底部提示 */
.composer-footer {
  padding: var(--space-2) var(--space-1) 0;
}

.busy-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.busy-indicator {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--fg-secondary);
}

.busy-dot {
  width: 8px;
  height: 8px;
  background: var(--warning);
  border-radius: 50%;
  animation: pulse 1.5s ease-in-out infinite;
}

.busy-hint {
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

.busy-hint kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 var(--space-1);
  background: var(--bg-tertiary);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: var(--font-medium);
  color: var(--fg-secondary);
}

.ready-hints {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hint-group {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

.hint-item {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.hint-item kbd {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 var(--space-1);
  background: var(--bg-tertiary);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: var(--font-medium);
  color: var(--fg-secondary);
}

.hint-divider {
  width: 1px;
  height: 12px;
  background: var(--border);
}

.mode-hint {
  display: flex;
  align-items: center;
}

.mode-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 0.25rem 0.5rem;
  border-radius: var(--radius-full);
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
}

.mode-tag.plan {
  background: var(--info-bg);
  color: var(--info);
}

.mode-tag.review {
  background: var(--warning-bg);
  color: var(--warning);
}

/* 动画 */
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.animate-spin {
  animation: spin 1s linear infinite;
}

/* 弹窗动画 */
.popup-enter-active,
.popup-leave-active {
  transition: all var(--transition-fast);
}

.popup-enter-from,
.popup-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.95);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .composer-wrapper {
    padding: var(--space-3) var(--space-4);
  }
  
  .composer-wrapper::before {
    display: none;
  }
  
  .toolbar-center {
    display: none;
  }
  
  .model-selector {
    display: none;
  }
  
  .ready-hints {
    flex-direction: column;
    gap: var(--space-1);
    align-items: flex-start;
  }
  
  .hint-group {
    flex-wrap: wrap;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .composer {
  background: var(--bg-secondary);
  border-color: var(--border);
}

[data-theme="dark"] .composer.focused {
  border-color: var(--brand-primary-light);
  box-shadow: 0 0 0 3px rgba(129, 140, 248, 0.15);
}

[data-theme="dark"] .slash-popup {
  background: var(--bg-secondary);
  border-color: var(--border);
}

[data-theme="dark"] .popup-header {
  background: var(--bg-tertiary);
}
</style>