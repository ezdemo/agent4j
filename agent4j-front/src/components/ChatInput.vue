<template>
  <div class="input-area">
    <!-- 斜杠命令弹窗 -->
    <Transition name="slash-popup">
      <div v-if="slashPopupOpen" class="slash-popup">
        <div class="slash-popup-header">
          <span class="slash-popup-title">可用命令</span>
          <span class="slash-popup-hint">输入 / 触发</span>
        </div>
        <div v-if="!commandsLoaded" class="slash-popup-loading">
          <span class="loading-dot"></span> 加载命令中...
        </div>
        <div v-else-if="filteredSlashCmds.length === 0" class="slash-popup-empty">无匹配命令</div>
        <div v-else class="slash-popup-list">
          <div v-for="(cmd, index) in filteredSlashCmds" :key="cmd.cmd"
               class="slash-popup-item" :class="{ active: index === activePopupIdx }"
               @click="selectSlashCmd(cmd)" @mouseenter="activePopupIdx = index">
            <div class="slash-popup-icon">{{ getSlashIcon(cmd.cmd) }}</div>
            <div class="slash-popup-info">
              <div class="slash-popup-cmd">
                {{ cmd.cmd }}
                <span v-if="cmd.type === 'skill'" class="slash-popup-badge skill">skill</span>
                <span v-else-if="cmd.type === 'mode'" class="slash-popup-badge mode">模式</span>
                <span v-else-if="cmd.type === 'session'" class="slash-popup-badge session">会话</span>
              </div>
              <div class="slash-popup-desc">{{ cmd.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <div class="input-box" :class="{ focused: inputFocused }">
      <div class="input-row">
        <!-- TODO 图标 -->
        <div class="todo-trigger" @mouseenter="handleTodoEnter" @mouseleave="handleTodoLeave">
          <button class="todo-btn" :class="{ 'has-todos': todoStats.pending + todoStats.inProgress > 0 }">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
            <span v-if="todoStats.pending + todoStats.inProgress > 0"
                  class="todo-badge">{{ todoStats.pending + todoStats.inProgress }}</span>
          </button>
          <Transition name="tooltip">
            <div v-if="todoTooltipVisible" class="todo-tooltip"
                 @mouseenter="clearTodoTimer" @mouseleave="handleTodoLeave">
              <div class="todo-tooltip-header">
                <span class="todo-tooltip-title">任务列表</span>
                <span class="todo-tooltip-stats">{{ todoStats.completed }}/{{ todoStats.total }} 完成</span>
              </div>
              <div v-if="todos.length === 0" class="todo-tooltip-empty">暂无任务</div>
              <div v-else class="todo-tooltip-list">
                <div v-for="(todo, i) in incompleteTodos" :key="'i'+i" class="todo-tooltip-item">
                <span class="todo-status-icon" :class="todo.status">
                  <svg v-if="todo.status==='in_progress'" width="12" height="12" viewBox="0 0 24 24" fill="none"
                       stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline
                      points="12 6 12 12 16 14"/></svg>
                  <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                       stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/></svg>
                </span>
                  <span class="todo-content" :class="todo.status">{{ todo.content }}</span>
                </div>
                <div v-if="completedTodos.length>0" class="todo-completed-section">
                  <div class="todo-completed-toggle" @click="showCompleted=!showCompleted">
                    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                         :style="{ transform: showCompleted ? 'rotate(90deg)' : '' }">
                      <polyline points="9 18 15 12 9 6"/>
                    </svg>
                    <span>{{ completedTodos.length }} 项已完成</span>
                  </div>
                  <Transition name="collapse">
                    <div v-if="showCompleted" class="todo-completed-list">
                      <div v-for="(todo,i) in completedTodos" :key="'c'+i" class="todo-tooltip-item completed">
                      <span class="todo-status-icon completed">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                      </span>
                        <span class="todo-content completed">{{ todo.content }}</span>
                      </div>
                    </div>
                  </Transition>
                </div>
              </div>
              <div v-if="todoStats.total>0" class="todo-tooltip-footer">
                <div class="todo-progress-bar">
                  <div class="todo-progress-fill"
                       :style="{ width: (todoStats.completed/todoStats.total*100)+'%' }"></div>
                </div>
              </div>
            </div>
          </Transition>
        </div>

        <textarea ref="inputField" v-model="localText" @keydown="handleKeydown"
                  placeholder="输入消息... (Enter 发送, Tab 补全, / 命令，粘贴图片)" rows="1" @blur="handleBlur"
                  @focus="inputFocused=true"
                  @input="handleInput" @paste="handlePaste"></textarea>

        <!-- 图片预览 -->
        <div v-if="images.length > 0" class="image-preview-bar">
          <div v-for="(img, idx) in images" :key="idx" class="image-preview-item">
            <img :src="img" alt="粘贴的图片" class="image-preview-thumb"/>
            <button class="image-preview-remove" title="移除图片" @click="removeImage(idx)">&times;</button>
          </div>
        </div>

        <div class="input-actions">
          <!-- 计划模式按钮已移除 -->
          <button v-if="streaming" class="stop-btn" @click="$emit('abort')" title="停止生成 (Esc)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 class="animate-spin">
              <path d="M21 12a9 9 0 11-6.219-8.56"/>
            </svg>
            <span class="stop-text">停止</span>
          </button>
          <template v-else>
            <button :disabled="!hasHistory" class="continue-btn" title="让 AI 继续生成" @click="$emit('continue')">
              <svg fill="none" height="16" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="16">
                <polyline points="5 4 15 12 5 20"/>
                <line x1="19" x2="19" y1="5" y2="19"/>
              </svg>
            </button>
            <button :class="{ active: localText.trim() && !streaming }" :disabled="!localText.trim() || streaming"
                    class="send-btn" @click="handleSend">
              <svg fill="none" height="16" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="16">
                <line x1="22" x2="11" y1="2" y2="13"/>
                <polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </template>
        </div>
      </div>

      <!-- Token 用量 & 模型选择 -->
      <div class="usage-bar">
        <div class="usage-stats">
        <span class="usage-item hide-mobile" :title="'输入: '+fmt(usage.promptTokens)">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3"
                                                                                                         x2="12"
                                                                                                         y2="15"/>
          </svg>
          输入 {{ fmt(usage.promptTokens) }}
        </span>
          <span class="usage-item hide-mobile" :title="'输出: '+fmt(usage.completionTokens)">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12"
                                                                                                            y1="15"
                                                                                                            x2="12"
                                                                                                            y2="3"/>
          </svg>
          输出 {{ fmt(usage.completionTokens) }}
        </span>
          <span class="usage-item hide-mobile"
                :title="'缓存命中: '+fmt(usage.cacheHit)+' / 未命中: '+fmt(usage.cacheMiss)">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          缓存 {{ cacheRate }}%
        </span>
          <span class="usage-item usage-cost-item" v-if="usage.hasPrice">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
          </svg>
          ¥{{ (usage.totalCost || 0).toFixed(2) }}
        </span>
          <span class="usage-sep">|</span>
          <span class="usage-context-wrap"
                :title="'上下文: '+fmt(usage.lastPromptTokens||usage.promptTokens)+' / '+fmt(usage.maxContextTokens)">
          上下文
          <span class="usage-progress">
            <span class="usage-progress-bar" :style="{ width: Math.min(ctxPct,100)+'%' }"
                  :class="{ high: ctxPct>=80, medium: ctxPct>=50 && ctxPct<80 }"></span>
          </span>
          <span class="usage-value" :class="{ high: ctxPct>=80, medium: ctxPct>=50 && ctxPct<80 }">{{ ctxPct }}%</span>
        </span>
          <button class="usage-refresh" @click="$emit('refreshUsage')" title="刷新用量">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
          </button>
        </div>
        <div class="model-actions">
        <div class="reasoning-effort-selector">
          <button class="effort-btn" @click="toggleEffortPicker" :title="'当前推理强度: '+currentReasoningEffort">
            {{ effortLabel }}
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
          <div class="effort-dropdown" v-if="showEffortPicker">
            <div class="effort-dropdown-title">推理强度</div>
            <div class="effort-dropdown-list">
              <div v-for="opt in effortOptions" :key="opt.value" class="effort-option"
                   :class="{ active: opt.value === currentReasoningEffort }" @click="pickEffort(opt.value)">
                <span class="effort-option-name">{{ opt.label }}</span>
                <svg v-if="opt.value === currentReasoningEffort" width="14" height="14" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </div>
            </div>
          </div>
        </div>
        <div class="model-selector" v-if="currentModel">
          <button class="model-btn" @click="toggleModelPicker" :title="'当前模型: '+currentModel">
            {{ currentModel }}
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
          <div class="model-dropdown" v-if="showModelPicker">
            <div class="model-dropdown-title">切换模型</div>
            <div class="model-dropdown-list">
              <div v-for="m in availableModels" :key="m.name" class="model-option"
                   :class="{ active: m.active }" @click="pickModel(m.name)">
                <span class="model-option-name">{{ m.name }}</span>
                <svg v-if="m.active" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </div>
            </div>
          </div>
        </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {agentAPI} from '../services/api'

const props = defineProps({
  inputText: {type: String, default: ''},
  streaming: {type: Boolean, default: false},
  todos: {type: Array, default: () => []},
  usage: {type: Object, default: () => ({})},
  currentModel: {type: String, default: ''},
  availableModels: {type: Array, default: () => []},
  workspaceHash: {type: String, default: null},
  sessionName: {type: String, default: null},
  hasHistory: {type: Boolean, default: false},
  currentReasoningEffort: {type: String, default: 'max'}
})

const emit = defineEmits(['update:inputText', 'send', 'abort', 'clear', 'export', 'fetchTodos', 'refreshUsage', 'switchModel', 'continue', 'refreshModels', 'switchReasoningEffort'])

const inputField = ref(null)
const inputFocused = ref(false)
const localText = ref(props.inputText)
const images = ref([]) // 粘贴的图片 base64 Data URI 列表

// 同步 props 到本地
watch(() => props.inputText, v => localText.value = v)
watch(localText, v => emit('update:inputText', v))

// ============= 斜杠命令 =============
const slashPopupOpen = ref(false)
const slashQuery = ref('')
const activePopupIdx = ref(0)
const backendCommands = ref([])
const backendSkills = ref([])
const commandsLoaded = ref(false)

const defaultSlashCmds = [
  {cmd: '/new', desc: '新建对话', type: 'session'},
  {cmd: '/clear', desc: '清空对话', type: 'session'},
  {cmd: '/retry', desc: '重试最后一条', type: 'session'},
  {cmd: '/compact', desc: '折叠上下文', type: 'session'},
  {cmd: '/export', desc: '导出对话', type: 'session'},
  {cmd: '/plan', desc: '进入计划模式', type: 'mode'},
  {cmd: '/execute', desc: '退出计划模式', type: 'mode'},
  {cmd: '/continue', desc: '继续生成', type: 'mode'},
  {cmd: '/sessions', desc: '列出历史会话', type: 'session'},
  {cmd: '/help', desc: '显示帮助信息', type: 'system'},
  {cmd: '/exit', desc: '退出', type: 'system'},
  {cmd: '/hitl', desc: '切换 HITL 模式', type: 'mode'},
  {cmd: '/agree', desc: '批准待执行工具', type: 'mode'},
  {cmd: '/deny', desc: '拒绝待执行工具', type: 'mode'},
  {cmd: '/init', desc: '初始化项目文档', type: 'system'}
]

const mergedCommands = computed(() => {
  if (backendCommands.value.length > 0) return backendCommands.value.map(c => ({
    cmd: c.cmd,
    desc: c.desc || '',
    type: c.type || 'system'
  }))
  return defaultSlashCmds
})

const skillCommands = computed(() => backendSkills.value.map(s => ({
  cmd: `/skill:${s.name}`,
  desc: s.description || '运行 skill',
  type: 'skill'
})))
const allSlashCmds = computed(() => [...mergedCommands.value, ...skillCommands.value])

const filteredSlashCmds = computed(() => {
  if (!slashQuery.value) return allSlashCmds.value
  const q = slashQuery.value.toLowerCase()
  return allSlashCmds.value.filter(c => c.cmd.toLowerCase().includes(q) || c.desc.toLowerCase().includes(q))
})

const getSlashIcon = (cmd) => {
  const icons = {
    '/new': '✨', '/clear': '🗑️', '/retry': '🔄', '/compact': '📦', '/export': '📥', '/plan': '📋',
    '/execute': '⚡', '/continue': '▶️', '/sessions': '📂', '/load': '📂', '/rewind': '⏪', '/init': '🔧', '/hitl': '🛡',
    '/agree': '✅', '/deny': '❌', '/help': '❓', '/exit': '👋'
  }
  if (cmd?.startsWith('/skill:')) return '🧩'
  return icons[cmd] || '🔧'
}

const loadCommands = async () => {
  if (commandsLoaded.value) return
  try {
    const [cr, sr] = await Promise.allSettled([agentAPI.getCommands(), agentAPI.getSkills()])
    if (cr.status === 'fulfilled' && cr.value.success && cr.value.data) backendCommands.value = cr.value.data
    if (sr.status === 'fulfilled' && sr.value.success && sr.value.data) backendSkills.value = sr.value.data
  } catch {
  }
  commandsLoaded.value = true
}

const selectSlashCmd = (cmd) => {
  slashPopupOpen.value = false;
  slashQuery.value = ''
  localText.value = cmd.cmd + ' '
  nextTick(() => inputField.value?.focus())
}

// ============= TODO =============
const todoTooltipVisible = ref(false)
const todoTimer = ref(null)
const showCompleted = ref(false)

const todoStats = computed(() => {
  const total = props.todos.length, completed = props.todos.filter(t => t.status === 'completed').length
  const inProgress = props.todos.filter(t => t.status === 'in_progress').length,
      pending = props.todos.filter(t => t.status === 'pending').length
  return {total, completed, inProgress, pending}
})
const incompleteTodos = computed(() => props.todos.filter(t => t.status !== 'completed'))
const completedTodos = computed(() => props.todos.filter(t => t.status === 'completed'))

const handleTodoEnter = () => {
  clearTimeout(todoTimer.value);
  todoTimer.value = setTimeout(() => {
    todoTooltipVisible.value = true;
    emit('fetchTodos')
  }, 300)
}
const handleTodoLeave = () => {
  clearTimeout(todoTimer.value);
  todoTimer.value = setTimeout(() => {
    todoTooltipVisible.value = false
  }, 50)
}
const clearTodoTimer = () => clearTimeout(todoTimer.value)

// ============= 输入处理 =============
const handleInput = () => {
  autoResize()
  const m = localText.value.match(/(^|\s)(\/)([^\s]*)$/)
  if (m) {
    slashPopupOpen.value = true;
    slashQuery.value = m[3] || '';
    activePopupIdx.value = 0;
    if (!commandsLoaded.value) {
      commandsLoaded.value = false;
      loadCommands()
    }
  } else slashPopupOpen.value = false
}

const handleBlur = () => {
  inputFocused.value = false;
  setTimeout(() => {
    slashPopupOpen.value = false
  }, 200)
}

const handleKeydown = (e) => {
  if (slashPopupOpen.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      activePopupIdx.value = (activePopupIdx.value + 1) % filteredSlashCmds.value.length;
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      activePopupIdx.value = (activePopupIdx.value - 1 + filteredSlashCmds.value.length) % filteredSlashCmds.value.length;
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault();
      slashPopupOpen.value = false;
      return
    }
    if ((e.key === 'Enter' && !e.shiftKey) || e.key === 'Tab') {
      e.preventDefault();
      if (filteredSlashCmds.value.length > 0) selectSlashCmd(filteredSlashCmds.value[activePopupIdx.value]);
      return
    }
  }
  if (e.key === 'Escape' && props.streaming) {
    e.preventDefault();
    emit('abort');
    return
  }
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend()
  }
}

const handleSend = () => {
  if (localText.value.trim() && !props.streaming) {
    emit('send', images.value)
    // 发送后清空图片，localText 由父组件 v-model 清空
    images.value = []
    // 等待父组件清空文本后，重置 textarea 高度
    nextTick(() => autoResize())
  }
}

/**
 * 粘贴事件处理：从剪贴板捕获图片，转为 base64 Data URI。
 */
const handlePaste = async (e) => {
  const items = e.clipboardData?.items
  if (!items) return

  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault() // 阻止默认粘贴文本
      const file = item.getAsFile()
      if (!file) continue

      try {
        const dataUrl = await fileToDataUrl(file)
        // 限制图片数量（防止请求体过大）
        if (images.value.length >= 10) {
          console.warn('图片数量已达上限（10张），跳过')
          continue
        }
        images.value.push(dataUrl)
      } catch (err) {
        console.error('图片转换失败:', err)
      }
    }
  }
}

/**
 * 移除已粘贴的图片
 */
const removeImage = (idx) => {
  images.value.splice(idx, 1)
}

/**
 * 将 File 对象转为 base64 Data URI
 */
const fileToDataUrl = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

const autoResize = () => {
  const el = inputField.value;
  if (el) {
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 160) + 'px'
  }
}

// ============= 模型切换 =============
const showModelPicker = ref(false)
const toggleModelPicker = () => {
  showModelPicker.value = !showModelPicker.value
  if (showModelPicker.value) {
    emit('refreshModels')
  }
}
const pickModel = async (name) => {
  if (name === props.currentModel) {
    showModelPicker.value = false;
    return
  }
  emit('switchModel', name)
  showModelPicker.value = false
}

// 点击外部关闭模型选择器
const handleOutside = (e) => {
  if (!e.target.closest('.model-selector')) showModelPicker.value = false;
  if (!e.target.closest('.reasoning-effort-selector')) showEffortPicker.value = false
}

// ============= 推理强度切换 =============
const showEffortPicker = ref(false)
const effortOptions = [
  {value: 'none', label: '无'},
  {value: 'low', label: '低'},
  {value: 'medium', label: '中'},
  {value: 'high', label: '高'},
  {value: 'max', label: '最大'}
]
const effortLabel = computed(() => {
  const found = effortOptions.find(o => o.value === props.currentReasoningEffort)
  return found ? found.label : props.currentReasoningEffort
})
const toggleEffortPicker = () => {
  showEffortPicker.value = !showEffortPicker.value
}
const pickEffort = async (value) => {
  if (value === props.currentReasoningEffort) {
    showEffortPicker.value = false;
    return
  }
  emit('switchReasoningEffort', value)
  showEffortPicker.value = false
}

// ============= Usage =============
const fmt = (n) => {
  if (!n || n === 0) return '0';
  if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M';
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K';
  return String(n)
}
const cacheRate = computed(() => {
  const t = props.usage.cacheHit + props.usage.cacheMiss;
  return t === 0 ? '0' : ((props.usage.cacheHit / t) * 100).toFixed(1)
})
const ctxPct = computed(() => {
  const m = props.usage.maxContextTokens || 128000
  const c = props.usage.lastPromptTokens || props.usage.promptTokens || 0
  if (m <= 0 || c <= 0) return 0
  const pct = Math.round((c / m) * 100)
  // 小于 5% 时至少展示 5%，让填充条肉眼可见
  return Math.max(5, Math.min(pct, 100))
})

onMounted(() => {
  loadCommands();
  document.addEventListener('click', handleOutside)
})
onBeforeUnmount(() => document.removeEventListener('click', handleOutside))

// 暴露焦點方法给父组件
defineExpose({focus: () => inputField.value?.focus(), autoResize})
</script>

<style scoped>
.input-area {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 10px;
  background: transparent;
  z-index: 10;
}

.input-box {
  display: flex;
  flex-direction: column;
  gap: 0;
  background: var(--glass-bg-2);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  padding: 6px 8px 0;
  transition: border-color var(--t);
  box-shadow: var(--glass-shadow);
}

.input-box.focused {
  border-color: var(--accent);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.input-box textarea {
  flex: 1;
  min-height: 22px;
  max-height: 160px;
  padding: 0;
  background: none;
  border: none;
  outline: none;
  font-size: 14px;
  line-height: 1.5;
  color: var(--fg);
  resize: none;
}

.input-box textarea::placeholder {
  color: var(--fg-4);
}

/* 图片预览 */
.image-preview-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 0 4px 0;
  border-top: 1px solid var(--border);
  margin-top: 4px;
}

.image-preview-item {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--border);
  flex-shrink: 0;
}

.image-preview-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.image-preview-remove {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  cursor: pointer;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.image-preview-remove:hover {
  background: rgba(239, 68, 68, 0.9);
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.btn-icon-sm {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-4);
  transition: all var(--t);
  cursor: pointer;
}

.btn-icon-sm:hover {
  background: var(--bg-3);
  color: var(--fg-2);
}

.btn-icon-sm.active {
  background: var(--accent-bg);
  color: var(--accent);
}

.send-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-4);
  transition: all var(--t);
}

.send-btn.active {
  background: var(--accent);
  color: #fff;
}

.send-btn.active:hover {
  background: var(--blue-dark);
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.continue-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-4);
  transition: all var(--t);
  cursor: pointer;
}

.continue-btn:hover {
  background: var(--bg-3);
  color: var(--accent);
}

.continue-btn:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.stop-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--red);
  color: #fff;
  border: none;
  border-radius: var(--r);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--t);
  animation: pulse-red 1.5s infinite;
}

.stop-btn:hover {
  background: #b91c1c;
}

.stop-btn svg {
  animation: spin 1s linear infinite;
}

.stop-text {
  margin-left: 2px;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes pulse-red {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(239, 68, 68, 0);
  }
}

/* TODO */
.todo-trigger {
  position: relative;
  flex-shrink: 0;
}

.todo-btn {
  width: 28px;
  height: 28px;
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fg-4);
  transition: all var(--t);
  position: relative;
  cursor: pointer;
}

.todo-btn:hover {
  background: var(--bg-3);
  color: var(--fg-2);
}

.todo-btn.has-todos {
  color: var(--accent);
}

.todo-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  background: var(--accent);
  color: white;
  font-size: 9px;
  font-weight: 700;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.todo-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  width: 280px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  z-index: 100;
  overflow: hidden;
}

.todo-tooltip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
}

.todo-tooltip-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
}

.todo-tooltip-stats {
  font-size: 11px;
  color: var(--fg-4);
  background: var(--bg-3);
  padding: 2px 6px;
  border-radius: var(--r-sm);
}

.todo-tooltip-empty {
  padding: 16px;
  text-align: center;
  color: var(--fg-4);
  font-size: 12px;
}

.todo-tooltip-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 4px;
}

.todo-tooltip-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: var(--r-sm);
  transition: background var(--t);
}

.todo-tooltip-item:hover {
  background: var(--bg-2);
}

.todo-status-icon {
  flex-shrink: 0;
  font-size: 11px;
  line-height: 1.5;
}

.todo-content {
  font-size: 12px;
  color: var(--fg-2);
  line-height: 1.5;
}

.todo-content.completed {
  text-decoration: line-through;
  color: var(--fg-4);
}

.todo-content.in_progress {
  color: var(--accent);
  font-weight: 500;
}

.todo-tooltip-footer {
  padding: 6px 12px 8px;
  border-top: 1px solid var(--border);
}

.todo-progress-bar {
  height: 3px;
  background: var(--bg-3);
  border-radius: 2px;
  overflow: hidden;
}

.todo-progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.todo-completed-section {
  border-top: 1px solid var(--border);
  margin-top: 4px;
}

.todo-completed-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  font-size: 11px;
  color: var(--fg-4);
  cursor: pointer;
  transition: all var(--t);
}

.todo-completed-toggle:hover {
  color: var(--fg-2);
  background: var(--bg-2);
}

.todo-completed-toggle svg {
  transition: transform 0.2s ease;
}

.collapse-enter-active, .collapse-leave-active {
  transition: all 0.2s ease;
  max-height: 200px;
}

.collapse-enter-from, .collapse-leave-to {
  max-height: 0;
  opacity: 0;
}

.tooltip-enter-active, .tooltip-leave-active {
  transition: all 0.2s ease;
}

.tooltip-enter-from, .tooltip-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

/* 斜杠命令弹窗 */
.slash-popup {
  position: absolute;
  bottom: 100%;
  left: 16px;
  right: 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  z-index: 100;
  overflow: hidden;
  margin-bottom: 4px;
}

.slash-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
}

.slash-popup-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
}

.slash-popup-hint {
  font-size: 11px;
  color: var(--fg-4);
}

.slash-popup-list {
  max-height: 280px;
  overflow-y: auto;
  padding: 4px;
}

.slash-popup-loading, .slash-popup-empty {
  padding: 24px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--fg-4);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.slash-popup-loading {
  color: var(--accent);
}

.slash-popup-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: background var(--t);
}

.slash-popup-item:hover, .slash-popup-item.active {
  background: var(--bg-2);
}

.slash-popup-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  background: var(--bg-3);
  border-radius: var(--r-sm);
  flex-shrink: 0;
}

.slash-popup-info {
  flex: 1;
  min-width: 0;
}

.slash-popup-cmd {
  font-size: 13px;
  font-weight: 600;
  font-family: var(--mono);
  color: var(--fg);
  display: flex;
  align-items: center;
  gap: 4px;
}

.slash-popup-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 4px;
  font-size: 10px;
  font-weight: 500;
  border-radius: var(--r-sm);
  line-height: 1.4;
}

.slash-popup-badge.skill {
  background: #dcfce7;
  color: #16a34a;
}

.slash-popup-badge.mode {
  background: #dbeafe;
  color: #2563eb;
}

.slash-popup-badge.session {
  background: #fef3c7;
  color: #d97706;
}

.slash-popup-desc {
  font-size: 11px;
  color: var(--fg-4);
  margin-top: 1px;
}

[data-theme="dark"] .slash-popup-badge.skill {
  background: #052e16;
  color: #4ade80;
}

[data-theme="dark"] .slash-popup-badge.mode {
  background: #1e3a5f;
  color: #60a5fa;
}

[data-theme="dark"] .slash-popup-badge.session {
  background: #422006;
  color: #fbbf24;
}

.slash-popup-enter-active, .slash-popup-leave-active {
  transition: all 0.15s ease;
}

.slash-popup-enter-from, .slash-popup-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}

/* Usage bar — 融入 input-box 底部 */
.usage-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 4px 12px;
  font-size: 11px;
  color: var(--fg-3);
  border-top: 1px solid var(--glass-border);
  margin-top: 4px;
}

.usage-stats {
  display: flex;
  align-items: center;
  gap: 12px;
}

.usage-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  cursor: default;
  color: var(--fg-3);
}

.usage-item svg {
  color: var(--fg-4);
  flex-shrink: 0;
}

.usage-sep {
  color: var(--border);
  font-size: 14px;
}

.usage-context-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.usage-progress {
  display: inline-block;
  width: 80px;
  height: 5px;
  background: var(--bg-3);
  border-radius: 3px;
  overflow: hidden;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.1);
  vertical-align: middle;
}

.usage-progress-bar {
  display: block;
  height: 100%;
  min-width: 2px;
  background: var(--accent);
  opacity: 0.65;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.usage-progress-bar.medium {
  background: var(--yellow);
}

.usage-progress-bar.high {
  background: var(--red);
}

.usage-value {
  font-weight: 500;
  color: var(--fg-2);
  font-family: var(--mono);
}

.usage-value.high {
  color: var(--red);
  font-weight: 600;
}

.usage-value.medium {
  color: var(--yellow);
}

.usage-refresh {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-sm);
  color: var(--fg-4);
  font-size: 14px;
  transition: all var(--t);
  cursor: pointer;
}

.usage-refresh:hover {
  background: var(--bg-3);
  color: var(--fg-2);
}

.usage-cost-item {
  color: var(--yellow);
  font-weight: 500;
  font-family: var(--mono);
}

.usage-cost-item svg {
  color: var(--yellow);
}

.model-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.reasoning-effort-selector {
  position: relative;
  display: inline-flex;
  vertical-align: middle;
}

.effort-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
  padding: 2px 6px;
  border-radius: var(--r-sm);
  transition: all var(--t);
  cursor: pointer;
  background: none;
  border: none;
  white-space: nowrap;
}

.effort-btn:hover {
  background: var(--bg-3);
}

.effort-btn svg {
  color: var(--fg-4);
  width: 8px;
  height: 8px;
  flex-shrink: 0;
}

.effort-dropdown {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 4px;
  min-width: 140px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  z-index: 100;
  overflow: hidden;
}

.effort-dropdown-title {
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--fg-4);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.effort-dropdown-list {
  max-height: 200px;
  overflow-y: auto;
}

.effort-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--fg-2);
  cursor: pointer;
  transition: all var(--t);
}

.effort-option:hover {
  background: var(--bg-2);
}

.effort-option.active {
  color: var(--accent);
  font-weight: 500;
}

.effort-option svg {
  color: var(--accent);
}

.model-selector {
  position: relative;
  display: inline-flex;
  vertical-align: middle;
}

.model-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
  font-family: var(--mono);
  padding: 2px 6px;
  border-radius: var(--r-sm);
  transition: all var(--t);
  cursor: pointer;
  background: none;
  border: none;
  white-space: nowrap;
}

.model-btn:hover {
  background: var(--bg-3);
}

.model-dropdown {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 4px;
  min-width: 200px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  z-index: 100;
  overflow: hidden;
}

.model-dropdown-title {
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--fg-4);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.model-dropdown-list {
  max-height: 200px;
  overflow-y: auto;
}

.model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 13px;
  font-family: var(--mono);
  color: var(--fg-2);
  cursor: pointer;
  transition: all var(--t);
}

.model-option:hover {
  background: var(--bg-2);
}

.model-option.active {
  color: var(--accent);
  font-weight: 500;
}

.model-option svg {
  color: var(--accent);
}

.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  animation: loadBounce 0.6s infinite alternate;
}

@keyframes loadBounce {
  from {
    opacity: 0.3;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1.2);
  }
}

/* ===== 移动端适配 ===== */
@media (max-width: 640px) {
  .input-area {
    padding: 8px 6px;
  }

  .input-box {
    padding: 4px 6px 0;
    border-radius: var(--r);
  }

  .input-box textarea {
    font-size: 16px;
    min-height: 20px;
  }

  .input-row {
    gap: 4px;
  }

  .btn-icon-sm, .send-btn, .continue-btn, .todo-btn {
    width: 32px;
    height: 32px;
  }

  .hide-mobile {
    display: none !important;
  }

  .model-btn {
    font-size: 11px;
    padding: 2px 4px;
  }

  .model-dropdown {
    min-width: 160px;
  }

  .effort-btn {
    font-size: 11px;
    padding: 2px 4px;
  }

  .effort-dropdown {
    min-width: 120px;
  }

  .todo-tooltip {
    width: 240px;
  }

  .slash-popup {
    left: 8px;
    right: 8px;
  }
}
</style>
