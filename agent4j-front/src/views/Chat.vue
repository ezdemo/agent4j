<template>
  <div class="chat">
    <!-- 可选头部 -->
    <div v-if="!hideHeader" class="chat-head">
      <span class="chat-head-title">对话</span>
      <span class="chat-head-count">{{ messages.length }} 条</span>
      <div style="flex:1"></div>
      <button class="btn btn-ghost btn-sm" @click="clearChat">清空</button>
      <button class="btn btn-ghost btn-sm" @click="exportChat">导出</button>
    </div>

    <!-- 消息区 -->
    <div class="messages" ref="messagesContainer">
      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="empty">
        <div class="empty-icon">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/></svg>
        </div>
        <p class="empty-title">开始对话</p>
        <p class="empty-desc">输入问题或指令，Agent4j 将为您提供帮助</p>
        <div class="empty-suggestions">
          <button v-for="s in suggestions" :key="s" class="suggestion" @click="inputText = s">{{ s }}</button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, idx) in messages" :key="msg.id" class="msg" :class="msg.role">
        <!-- 用户消息 -->
        <template v-if="msg.role === 'user'">
          <div class="msg-body user-body">
            <div class="msg-text">{{ msg.content }}</div>
            <div class="msg-time">{{ msg.time }}</div>
          </div>
        </template>

        <!-- 助手消息 -->
        <template v-else-if="msg.role === 'assistant'">
          <div class="msg-body assistant-body">
            <div class="msg-blocks">
              <template v-for="(block, bi) in (msg.blocks || [])" :key="bi">
                <!-- 思考 -->
                <div v-if="block.type === 'reasoning'" class="block-reasoning">
                  <div class="reasoning-head" @click="block.showContent = !block.showContent">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                    <span>思考</span>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :style="{ transform: block.showContent ? 'rotate(180deg)' : '' }"><polyline points="6 9 12 15 18 9"/></svg>
                  </div>
                  <div v-if="block.showContent" class="reasoning-text">{{ block.content }}</div>
                </div>

                <!-- 内容 -->
                <div v-else-if="block.type === 'content'" class="block-content" v-html="fmt(block.content)"></div>

                <!-- 工具调用 -->
                <div v-else-if="block.type === 'tool_call'" class="block-tool">
                  <div class="tool-head" @click="block.expanded = !block.expanded">
                    <span class="tool-icon" :class="block.status">
                      <svg v-if="block.status === '执行中'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="animate-spin"><path d="M21 12a9 9 0 11-6.219-8.56"/></svg>
                      <svg v-else-if="block.status === '成功'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                      <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/></svg>
                    </span>
                    <code class="tool-name">{{ block.name }}</code>
                    <span class="tool-status" :class="block.status">{{ block.status }}</span>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :style="{ transform: block.expanded ? 'rotate(180deg)' : '' }"><polyline points="6 9 12 15 18 9"/></svg>
                  </div>
                  <div v-if="block.expanded" class="tool-detail">
                    <pre v-if="block.args"><code>{{ fmtArgs(block.args) }}</code></pre>
                    <pre v-if="block.result"><code>{{ block.result }}</code></pre>
                  </div>
                </div>
              </template>
            </div>
            <div class="msg-time">{{ msg.time }}</div>
          </div>
        </template>
      </div>

      <!-- 加载中 -->
      <div v-if="streaming && !hasAssistant" class="msg assistant">
        <div class="msg-body assistant-body">
          <div class="typing">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <div class="input-box" :class="{ focused: inputFocused }">
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
          ref="inputField"
          v-model="inputText"
          @keydown.enter.exact="handleEnter"
          @focus="inputFocused = true"
          @blur="inputFocused = false"
          @input="autoResize"
          placeholder="输入消息... (Enter 发送)"
          rows="1"
        ></textarea>
        <div class="input-actions">
          <button class="btn-icon-sm" :class="{ active: planMode }" @click="togglePlan" title="计划模式">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
          </button>
          <button v-if="streaming" class="stop-btn" @click="abortChat" title="停止生成 (Esc)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="animate-spin"><path d="M21 12a9 9 0 11-6.219-8.56"/></svg>
            <span class="stop-text">停止</span>
          </button>
          <button
            v-else
            class="send-btn"
            :class="{ active: inputText.trim() && !streaming }"
            @click="sendMessage"
            :disabled="!inputText.trim() || streaming"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
          </button>
        </div>
      </div>
      <!-- Token 用量统计 -->
      <div class="usage-bar">
        <div class="usage-stats">
          <span class="usage-item" :title="'输入 Token: ' + formatTokens(usage.promptTokens)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            输入 {{ formatTokens(usage.promptTokens) }}
          </span>
          <span class="usage-item" :title="'输出 Token: ' + formatTokens(usage.completionTokens)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            输出 {{ formatTokens(usage.completionTokens) }}
          </span>
          <span class="usage-item" :title="'缓存命中: ' + formatTokens(usage.cacheHit) + ' / 未命中: ' + formatTokens(usage.cacheMiss)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
            缓存 {{ cacheRate }}%
          </span>
          <span class="usage-sep">|</span>
          <span class="usage-context-wrap" :title="'上下文使用: ' + formatTokens(usage.lastPromptTokens || usage.promptTokens) + ' / ' + formatTokens(usage.maxContextTokens)">
            上下文
            <span class="usage-progress">
              <span class="usage-progress-bar" :style="{ width: Math.min(contextPercent, 100) + '%' }" :class="{ high: contextPercent >= 80, medium: contextPercent >= 50 && contextPercent < 80 }"></span>
            </span>
            <span class="usage-value" :class="{ high: contextPercent >= 80, medium: contextPercent >= 50 && contextPercent < 80 }">{{ contextPercent }}%</span>
          </span>
          <button class="usage-refresh" @click="loadUsage" title="刷新用量">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          </button>
        </div>
        <div class="model-selector" v-if="currentModel">
          <button class="model-btn" @click="showModelPicker = !showModelPicker" :title="'当前模型: ' + currentModel">
            {{ currentModel }}
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
          </button>
          <div class="model-dropdown" v-if="showModelPicker">
            <div class="model-dropdown-title">切换模型</div>
            <div 
              v-for="m in availableModels" 
              :key="m.name" 
              class="model-option" 
              :class="{ active: m.active }"
              @click="switchModel(m.name)"
            >
              <span class="model-option-name">{{ m.name }}</span>
              <svg v-if="m.active" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { chatAPI, agentAPI, configAPI } from '../services/api'
import { marked } from 'marked'

const props = defineProps({ 
  hideHeader: { type: Boolean, default: false },
  workspaceHash: { type: String, default: null },
  sessionName: { type: String, default: null }
})

const emit = defineEmits(['sessionUpdated'])

const messagesContainer = ref(null)
const inputField = ref(null)
const inputText = ref('')
const messages = ref([])
const streaming = ref(false)
const planMode = ref(false)
const inputFocused = ref(false)
let currentAbortController = null

// TODO 相关状态
const todos = ref([])
const todoTooltipVisible = ref(false)
const todoTooltipTimer = ref(null)

// Usage 相关
const usage = ref({ promptTokens: 0, completionTokens: 0, cacheHit: 0, cacheMiss: 0, maxContextTokens: 128000, lastPromptTokens: 0 })
const currentModel = ref('')
const availableModels = ref([])
const showModelPicker = ref(false)
let usageTimer = null

const formatTokens = (n) => {
  if (!n || n === 0) return '0'
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

const cacheRate = computed(() => {
  const total = usage.value.cacheHit + usage.value.cacheMiss
  if (total === 0) return '0'
  return ((usage.value.cacheHit / total) * 100).toFixed(1)
})

const contextPercent = computed(() => {
  const max = usage.value.maxContextTokens || 128000
  const current = usage.value.lastPromptTokens || usage.value.promptTokens || 0
  if (max === 0) return '0'
  return ((current / max) * 100).toFixed(1)
})

const loadUsage = async (override) => {
  try {
    const params = {}
    const wsHash = override?.workspaceHash ?? props.workspaceHash
    const sessName = override?.sessionName ?? props.sessionName
    if (wsHash) params.workspaceHash = wsHash
    if (sessName) params.sessionName = sessName
    
    const [usageRes, modelsRes] = await Promise.allSettled([
      configAPI.getUsage(params),
      configAPI.getModels()
    ])
    if (usageRes.status === 'fulfilled' && usageRes.value.success) {
      usage.value = { ...usage.value, ...usageRes.value.data }
    }
    if (modelsRes.status === 'fulfilled' && modelsRes.value.success) {
      currentModel.value = modelsRes.value.data?.current || ''
      availableModels.value = modelsRes.value.data?.models || []
    }
  } catch {}
}

// 获取当前会话的 TODO 列表
const fetchTodos = async () => {
  try {
    const params = {}
    // 使用当前会话名，如果没有则使用 'default'
    params.sessionName = props.sessionName || 'default'
    if (props.workspaceHash) params.workspaceHash = props.workspaceHash
    console.log('[fetchTodos] 请求参数:', params)
    const res = await configAPI.getTodos(params)
    console.log('[fetchTodos] 响应:', res)
    if (res.success) {
      todos.value = res.data?.todos || []
    }
  } catch (e) {
    console.error('[fetchTodos] 错误:', e)
    todos.value = []
  }
}

// TODO tooltip 处理
const handleTodoMouseEnter = () => {
  todoTooltipTimer.value = setTimeout(() => {
    todoTooltipVisible.value = true
    fetchTodos()
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
  const total = todos.value.length
  const completed = todos.value.filter(t => t.status === 'completed').length
  const inProgress = todos.value.filter(t => t.status === 'in_progress').length
  const pending = todos.value.filter(t => t.status === 'pending').length
  return { total, completed, inProgress, pending }
})

const switchModel = async (modelName) => {
  if (modelName === currentModel.value) {
    showModelPicker.value = false
    return
  }
  try {
    const r = await configAPI.updateConfig({ model: modelName })
    if (r.success) {
      currentModel.value = modelName
      // 更新 availableModels 中的 active 状态
      availableModels.value.forEach(m => {
        m.active = m.name === modelName
      })
      showModelPicker.value = false
    }
  } catch (e) {
    console.error('切换模型失败:', e)
  }
}

// 点击外部关闭模型选择器
const handleClickOutside = (e) => {
  if (!e.target.closest('.model-selector')) {
    showModelPicker.value = false
  }
}

// 只加载模型列表（不加载 usage，除非明确选了 session）
const loadModels = async () => {
  try {
    const r = await configAPI.getModels()
    if (r.success) {
      currentModel.value = r.data?.current || ''
      availableModels.value = r.data?.models || []
    }
  } catch {}
}

// 监听 workspace 和 session 变化，重新加载 usage
watch([() => props.workspaceHash, () => props.sessionName], ([ws, sess]) => {
  if (ws || sess) {
    if (sess) {
      loadUsage()
    }
  }
})

onMounted(() => {
  // 仅加载模型列表；usage 在明确选择 session 后才查询
  loadModels()
  if (props.sessionName) loadUsage()
  usageTimer = setInterval(() => {
    if (props.sessionName) loadUsage()
    else loadModels()
  }, 30000)
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  if (usageTimer) clearInterval(usageTimer)
  window.removeEventListener('terminal-clear', clearChat)
})

const suggestions = ['解释这段代码', '优化这个函数', '写个单元测试', '检查潜在问题']

// 不在聊天区显示的静默命令（只发给后端，不加用户消息气泡）
const SILENT_CMDS = new Set(['/new', '/plan', '/execute', '/compact', '/retry', '/sessions', '/load', '/init', '/hitl', '/agree', '/deny'])

const hasAssistant = computed(() => messages.value.some(m => m.role === 'assistant' && m.blocks?.length > 0))

const now = () => new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })

// 配置marked选项
marked.setOptions({
  breaks: true,
  gfm: true,
  headerIds: false,
  mangle: false
})

const fmt = c => {
  if (!c) return ''
  // 使用marked渲染Markdown
  return marked(c)
}

const fmtArgs = a => {
  if (typeof a === 'string') { try { return JSON.stringify(JSON.parse(a), null, 2) } catch { return a } }
  return JSON.stringify(a, null, 2)
}

const autoResize = () => {
  const el = inputField.value
  if (el) { el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 160) + 'px' }
}

const scroll = async () => {
  await nextTick()
  if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
}

const handleEnter = e => {
  if (e.key === 'Escape' && streaming.value) {
    e.preventDefault()
    abortChat()
    return
  }
  if (!e.shiftKey) { e.preventDefault(); sendMessage() }
}

/**
 * 核心发送逻辑：
 * - 普通消息：加用户气泡 → 创建助手占位 → 流式填充
 * - 静默命令（/new /plan 等）：不加气泡，直接发后端；若后端无内容回复则不显示助手气泡
 */
const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || streaming.value) return

  const isSilent = SILENT_CMDS.has(text.split(/\s+/)[0].toLowerCase())

  // 非静默命令才显示用户气泡
  if (!isSilent) {
    messages.value.push({ id: Date.now(), role: 'user', content: text, time: now() })
  }
  inputText.value = ''
  autoResize()
  await scroll()

  streaming.value = true
  const mi = messages.value.length

  // 非静默命令才预创建助手占位
  if (!isSilent) {
    messages.value.push({ id: Date.now() + 1, role: 'assistant', time: now(), blocks: [] })
  }

  const getMsg = () => messages.value[isSilent ? -1 : mi] // 静默命令时 getMsg 返回 undefined

  try {
    const streamResult = chatAPI.sendMessageStream(text,
      data => {
        currentAbortController = streamResult
        // 静默命令：首次收到有内容的数据时才创建助手气泡
        if (isSilent) {
          if (!data.type || data.type === 'done') return
          const hasContent = (data.type === 'content' && data.content?.trim()) ||
                             (data.type === 'reasoning' && data.content?.trim()) ||
                             data.type === 'tool_call' || data.type === 'tool_result' || data.type === 'error'
          if (!hasContent) return
          // 有实际内容了，插入助手气泡
          messages.value.push({ id: Date.now(), role: 'assistant', time: now(), blocks: [] })
          // 修正 mi 指向
          const idx = messages.value.length - 1
          getMsg = () => messages.value[idx]
          // 不再走静默分支
        }

        const msg = getMsg()
        if (!msg) return
        if (data.type === 'reasoning') {
          const lb = msg.blocks[msg.blocks.length - 1]
          if (lb?.type === 'reasoning') lb.content += (data.content || '')
          else msg.blocks.push({ type: 'reasoning', content: data.content || '', showContent: true })
        } else if (data.type === 'content') {
          const lb = msg.blocks[msg.blocks.length - 1]
          if (lb?.type === 'content') lb.content += (data.content || '')
          else msg.blocks.push({ type: 'content', content: data.content || '' })
        } else if (data.type === 'tool_call') {
          let name = data.name || '', args = data.args || data.arguments || ''
          if (typeof args === 'string') try { args = JSON.parse(args) } catch {}
          msg.blocks.push({ type: 'tool_call', name: name || 'unknown', status: '执行中', args, result: '', expanded: false })
        } else if (data.type === 'tool_result') {
          let result = data.result || data.content || ''
          const rn = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
          for (let i = msg.blocks.length - 1; i >= 0; i--) {
            if (msg.blocks[i].type === 'tool_call' && !msg.blocks[i].result) {
              msg.blocks[i].result = rn; msg.blocks[i].status = '成功'; break
            }
          }
        } else if (data.type === 'error') {
          msg.blocks.push({ type: 'content', content: '错误: ' + (data.error || data.content || '未知') })
        } else if (data.type === 'usage') {
          // 更新 usage 数据
          if (data.promptTokens !== undefined) {
            usage.value = { ...usage.value, ...data }
          }
        }
        scroll()
      },
      () => {
        streaming.value = false
        currentAbortController = null
        // 流结束后清理空的助手气泡
        const last = messages.value[messages.value.length - 1]
        if (last?.role === 'assistant' && (!last.blocks || last.blocks.length === 0)) {
          messages.value.pop()
        }
        // 刷新 usage 数据
        loadUsage()
        // 通知父组件刷新会话列表（标题可能已更新）
        emit('sessionUpdated')
      },
      () => {
        streaming.value = false
        currentAbortController = null
        const msg = getMsg()
        if (msg && !msg.blocks.length) msg.blocks.push({ type: 'content', content: '连接错误' })
      },
      // 传递工作区和会话信息
      {
        workspaceHash: props.workspaceHash,
        sessionName: props.sessionName
      }
    )
  } catch { streaming.value = false }
  await scroll()
}

const abortChat = async () => {
  if (currentAbortController) {
    currentAbortController.abort()
    currentAbortController = null
  }
  // 同时通知后端中断
  try {
    await chatAPI.abort()
  } catch {}
  streaming.value = false
}

const clearChat = async () => {
  messages.value = []
  // 静默发送 /new 给后端，不显示任何气泡
  streaming.value = true
  try {
    chatAPI.sendMessageStream('/new', () => {}, () => { streaming.value = false; loadUsage() }, () => { streaming.value = false })
  } catch { streaming.value = false }
}

// 暴露给父组件的清空方法（同样静默发 /new）
const clearMessages = () => {
  messages.value = []
  streaming.value = true
  try {
    chatAPI.sendMessageStream('/new', () => {}, () => { streaming.value = false; loadUsage() }, () => { streaming.value = false })
  } catch { streaming.value = false }
}

const exportChat = () => {
  const text = messages.value.map(m => {
    const h = `[${m.time}] ${m.role === 'user' ? '用户' : '助手'}:`
    let c = h + '\n'
    if (m.blocks) for (const b of m.blocks) {
      if (b.type === 'reasoning') c += '\n思考: ' + b.content + '\n'
      else if (b.type === 'content') c += b.content + '\n'
      else if (b.type === 'tool_call') c += `工具 ${b.name}: ${JSON.stringify(b.args)}\n`
    }
    return c
  }).join('\n---\n\n')
  const blob = new Blob([text], { type: 'text/plain' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `chat-${new Date().toISOString().slice(0, 10)}.txt`
  a.click()
}

const togglePlan = async () => {
  planMode.value = !planMode.value
  inputText.value = planMode.value ? '/plan' : '/execute'
  await sendMessage()
}

const loadHistory = async () => {
  try {
    const r = await agentAPI.getHistory()
    if (r.success && r.data) {
      const raw = r.data, tr = {}
      for (const m of raw) if (m.role === 'tool' && m.tool_call_id) tr[m.tool_call_id] = m.content || ''
      const merged = []
      for (const m of raw) {
        if (m.role === 'tool') continue
        const item = { id: Date.now() + merged.length, role: m.role, time: now(), blocks: [] }
        // 用户消息：直接设置 content 属性（模板渲染用 msg.content）
        if (m.role === 'user') {
          item.content = m.content || ''
        } else {
          // 助手消息：使用 blocks 结构
          if (m.reasoning_content) item.blocks.push({ type: 'reasoning', content: m.reasoning_content, showContent: false })
          if (m.tool_calls) for (const tc of m.tool_calls) {
            let name = tc.function?.name || tc.name || '', args = tc.function?.arguments || tc.arguments || ''
            if (typeof args === 'string') try { args = JSON.parse(args) } catch {}
            item.blocks.push({ type: 'tool_call', name, status: tr[tc.id] ? '成功' : '执行中', args, result: tr[tc.id] || '', expanded: false })
          }
          if (m.content) item.blocks.push({ type: 'content', content: m.content })
        }
        merged.push(item)
      }
      messages.value = merged
      // 加载完成后滚动到底部
      await scroll()
    }
  } catch {}
}

const loadSession = async (name, workspaceHash) => {
  try {
    const { sessionsAPI } = await import('../services/api')
    await sessionsAPI.switchSession(name)
    await loadHistory()
    // 切换会话后刷新 usage 数据（传入 sessionName 和 workspaceHash）
    await loadUsage({ sessionName: name, workspaceHash })
  } catch (e) { console.error('切换会话失败:', e) }
}

const sendCommand = async cmd => { inputText.value = cmd; await sendMessage() }

// 加载历史消息（仅在明确选了 session 时）
onMounted(() => { if (props.sessionName) loadHistory() })

defineExpose({ clearMessages, loadSession, sendCommand, exportChat })
</script>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg);
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-bottom: 1px solid var(--border);
}
.chat-head-title { font-size: 14px; font-weight: 600; }
.chat-head-count { font-size: 12px; color: var(--fg-4); }

/* 消息区 */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

/* 空状态 */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--fg-3);
  text-align: center;
}
.empty-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-3);
  border-radius: var(--r);
  margin-bottom: 12px;
  color: var(--fg-4);
}
.empty-title { font-size: 16px; font-weight: 600; color: var(--fg); margin-bottom: 4px; }
.empty-desc { font-size: 13px; color: var(--fg-3); margin-bottom: 16px; }
.empty-suggestions { display: flex; gap: 6px; flex-wrap: wrap; justify-content: center; }
.suggestion {
  padding: 4px 10px;
  font-size: 12px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  color: var(--fg-2);
  transition: all var(--t);
}
.suggestion:hover { border-color: var(--accent); color: var(--accent); }

/* 消息 */
.msg { margin-bottom: 12px; }
.msg.user { display: flex; justify-content: flex-end; }

.msg-body { max-width: 80%; }

.user-body {
  background: var(--accent);
  color: #fff;
  padding: 8px 12px;
  border-radius: var(--r);
}
.user-body .msg-time { font-size: 10px; opacity: 0.7; margin-top: 4px; text-align: right; }

.assistant-body {
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 8px 12px;
}
.assistant-body .msg-time { font-size: 10px; color: var(--fg-4); margin-top: 4px; }

.msg-text { font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }

/* 消息块 */
.msg-blocks { display: flex; flex-direction: column; gap: 8px; }

/* 思考块 */
.block-reasoning {
  background: var(--bg-3);
  border: 1px solid var(--border);
  border-radius: var(--r);
  overflow: hidden;
}
.reasoning-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-3);
  cursor: pointer;
}
.reasoning-head svg:last-child { transition: transform var(--t); }
.reasoning-text {
  padding: 0 10px 8px;
  font-size: 12px;
  font-family: var(--mono);
  color: var(--fg-3);
  line-height: 1.6;
  white-space: pre-wrap;
}

/* 内容块 */
.block-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--fg);
}
.block-content :deep(pre) {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 10px;
  margin: 6px 0;
  overflow-x: auto;
}
.block-content :deep(code) {
  font-family: var(--mono);
  font-size: 12px;
}
.block-content :deep(pre code) { background: none; padding: 0; }
.block-content :deep(strong) { font-weight: 600; }
.block-content :deep(a) { color: var(--accent); text-decoration: none; }
.block-content :deep(a:hover) { text-decoration: underline; }

/* Markdown标题样式 */
.block-content :deep(h1) { font-size: 1.5em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h2) { font-size: 1.3em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h3) { font-size: 1.1em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h4) { font-size: 1em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h5) { font-size: 0.9em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h6) { font-size: 0.8em; margin: 0.5em 0; font-weight: 600; }

/* 列表样式 */
.block-content :deep(ul) { margin: 0.5em 0; padding-left: 1.5em; }
.block-content :deep(ol) { margin: 0.5em 0; padding-left: 1.5em; }
.block-content :deep(li) { margin: 0.25em 0; }
.block-content :deep(li > ul) { margin: 0.25em 0; }
.block-content :deep(li > ol) { margin: 0.25em 0; }

/* 引用块样式 */
.block-content :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.5em 1em;
  border-left: 3px solid var(--accent);
  background: var(--bg-3);
  border-radius: 0 var(--r) var(--r) 0;
}

/* 表格样式 */
.block-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5em 0;
}
.block-content :deep(th),
.block-content :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
}
.block-content :deep(th) {
  background: var(--bg-3);
  font-weight: 600;
}

/* 水平线 */
.block-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1em 0;
}

/* 段落 */
.block-content :deep(p) { margin: 0.5em 0; }
.block-content :deep(p:first-child) { margin-top: 0; }
.block-content :deep(p:last-child) { margin-bottom: 0; }

/* 斜体 */
.block-content :deep(em) { font-style: italic; }

/* 删除线 */
.block-content :deep(del) { text-decoration: line-through; color: var(--fg-3); }

/* 工具块 */
.block-tool {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  overflow: hidden;
}
.tool-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  transition: background var(--t);
}
.tool-head:hover { background: var(--bg-2); }

.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-sm);
}
.tool-icon.执行中 { color: var(--yellow); }
.tool-icon.成功 { color: var(--green); }

.tool-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
}

.tool-status {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: var(--r-sm);
}
.tool-status.执行中 { background: var(--yellow-bg); color: var(--yellow); }
.tool-status.成功 { background: var(--green-bg); color: var(--green); }

.tool-head svg:last-child { margin-left: auto; transition: transform var(--t); color: var(--fg-4); }

.tool-detail {
  padding: 0 10px 8px;
  border-top: 1px solid var(--border);
}
.tool-detail pre {
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 8px;
  margin-top: 6px;
  font-size: 11px;
  max-height: 150px;
  overflow: auto;
}

/* 打字动画 */
.typing {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}
.typing span {
  width: 6px;
  height: 6px;
  background: var(--fg-4);
  border-radius: 50%;
  animation: pulse 1.4s infinite;
}
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }

/* 输入区 */
.input-area {
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  background: var(--bg);
}

.input-box {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 6px 8px;
  transition: border-color var(--t);
}
.input-box.focused { border-color: var(--accent); }

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
.input-box textarea::placeholder { color: var(--fg-4); }

.input-actions {
  display: flex;
  align-items: center;
  gap: 4px;
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
.send-btn.active:hover { background: var(--blue-dark); }

.stop-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--red);
  color: #fff;
  border-radius: var(--r);
  font-size: 12px;
  font-weight: 500;
  transition: all var(--t);
  animation: pulse-red 1.5s infinite;
}
.stop-btn:hover {
  background: var(--red-dark);
  transform: scale(1.05);
}
.stop-btn svg {
  animation: spin 1s linear infinite;
}
.stop-text {
  margin-left: 2px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes pulse-red {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
  50% { box-shadow: 0 0 0 4px rgba(239, 68, 68, 0); }
}

.btn-icon-sm.active { background: var(--accent-bg); color: var(--accent); }

/* Token 用量统计 */
.usage-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--fg-3);
  border-top: 1px solid var(--border);
  background: var(--bg-2);
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

.usage-value {
  font-weight: 500;
  color: var(--fg-2);
  font-family: var(--mono);
}

.usage-sep {
  color: var(--border);
  font-size: 14px;
}

.usage-context-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.usage-progress {
  width: 60px;
  height: 4px;
  background: var(--bg-3);
  border-radius: 2px;
  overflow: hidden;
}

.usage-progress-bar {
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.usage-progress-bar.medium {
  background: var(--yellow);
}

.usage-progress-bar.high {
  background: var(--red);
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

/* 模型选择器 */
.model-selector {
  position: relative;
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
}

.model-btn:hover {
  background: var(--bg-3);
}

.model-btn svg {
  transition: transform var(--t);
}

.model-btn:has(+ .model-dropdown) svg {
  transform: rotate(180deg);
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

@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

/* TODO 触发器 */
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

/* TODO Tooltip */
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
  align-items: flex-start;
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

/* 输入框内 textarea 需要 flex: 1 */
.input-box textarea {
  flex: 1;
}
</style>