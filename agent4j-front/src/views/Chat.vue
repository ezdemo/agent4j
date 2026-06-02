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
      <!-- 悬浮日志通知（堆叠） -->
      <div class="log-stack">
        <TransitionGroup name="log-bar">
          <div v-for="log in currentLogs" :key="log.id" class="log-bar" :class="'log-' + (log.level || 'info').toLowerCase()" @click="currentLogs = currentLogs.filter(l => l.id !== log.id)">
            <span class="log-bar-icon">📋</span>
            <span class="log-bar-text">{{ log.text }}</span>
            <span class="log-bar-time">{{ formatTime(log.time) }}</span>
          </div>
        </TransitionGroup>
      </div>
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
            <div class="msg-footer">
              <span class="msg-time">{{ msg.time }}</span>
              <button class="copy-msg-btn" @click="copyMessage(msg)" title="复制消息" v-html="COPY_ICON"></button>
            </div>
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

                <!-- 选项按钮（choice） -->
                <div v-else-if="block.type === 'choice'" class="block-choice">
                  <div v-if="!block.resolved" class="choice-buttons">
                    <button v-for="opt in (block.options || [])" :key="opt.value"
                            class="choice-btn"
                            @click="sendChoice(opt.value, block)">
                      {{ opt.title }}
                    </button>
                  </div>
                  <div v-else class="choice-resolved">
                    <span class="choice-label">已选择：</span>
                    <span class="choice-value">{{ block.selectedTitle || block.options?.[0]?.title || '—' }}</span>
                  </div>
                </div>

              </template>
            </div>
            <div class="msg-footer">
              <span class="msg-time">{{ msg.time }}</span>
              <button class="copy-msg-btn" @click="copyMessage(msg)" title="复制消息" v-html="COPY_ICON"></button>
            </div>
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

    <!-- 滚动到底部按钮（固定在消息区右下角） -->
    <button v-show="showScrollBtn" class="scroll-bottom-btn" @click="scrollToBottom" title="滚动到底部">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
        <polyline points="7 13 12 18 17 13"/>
        <line x1="12" y1="18" x2="12" y2="6"/>
      </svg>
    </button>

    <!-- 输入区（独立组件） -->
    <ChatInput
      v-model:inputText="inputText"
      :streaming="streaming"
      :planMode="planMode"
      :todos="todos"
      :usage="usage"
      :currentModel="currentModel"
      :availableModels="availableModels"
      :workspaceHash="props.workspaceHash"
      :sessionName="props.sessionName"
      @send="sendMessage"
      @abort="abortChat"
      @togglePlan="togglePlan"
      @clear="clearChat"
      @export="exportChat"
      @fetchTodos="fetchTodos"
      @refreshUsage="loadUsage"
      @switchModel="handleSwitchModel"
    />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { chatAPI, agentAPI, configAPI } from '../services/api'
import { marked } from 'marked'
import ChatInput from '../components/ChatInput.vue'

// ============= 模型切换 =============
const handleSwitchModel = async (modelName) => {
  if (modelName === currentModel.value) return
  try {
    const r = await configAPI.updateConfig({ model: modelName })
    if (r.success) {
      currentModel.value = modelName
      availableModels.value.forEach(m => { m.active = m.name === modelName })
    }
  } catch (e) { console.error('切换模型失败:', e) }
}

const props = defineProps({ 
  hideHeader: { type: Boolean, default: false },
  workspaceHash: { type: String, default: null },
  sessionName: { type: String, default: null }
})

const emit = defineEmits(['sessionUpdated'])

const messagesContainer = ref(null)
const inputText = ref('')
const messages = ref([])
const streaming = ref(false)
const planMode = ref(false)
let currentAbortController = null

// TODO 相关状态
const todos = ref([])

// Usage 相关
const usage = ref({ promptTokens: 0, completionTokens: 0, cacheHit: 0, cacheMiss: 0, maxContextTokens: 128000, lastPromptTokens: 0 })
const currentModel = ref('')
const availableModels = ref([])

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

// 日志通知列表（逐条堆叠，每条6秒后自动移除）
const currentLogs = ref([])

const addLog = (log) => {
  const id = Date.now() + Math.random()
  currentLogs.value.unshift({ ...log, id })
  setTimeout(() => {
    currentLogs.value = currentLogs.value.filter(l => l.id !== id)
  }, 6000)
}

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

// 获取当前会话的 TODO 列表
const fetchTodos = async () => {
  try {
    const params = {}
    params.sessionName = props.sessionName || 'default'
    if (props.workspaceHash) params.workspaceHash = props.workspaceHash
    const res = await configAPI.getTodos(params)
    if (res.success) { todos.value = res.data?.todos || [] }
  } catch (e) { todos.value = [] }
}

// 监听 workspace 和 session 变化，重新加载 usage
watch([() => props.workspaceHash, () => props.sessionName], ([ws, sess]) => {
  if (ws || sess) {
    if (sess) { loadUsage() }
  }
})

onMounted(() => {
  if (props.sessionName) loadUsage()
  // 监听复制成功事件
  window.addEventListener('copy-success', (e) => {
    addLog({ level: 'INFO', text: '✅ ' + (e.detail || '已复制'), time: Date.now() })
  })
  // 监听消息容器滚动 + 初始检查
  const el = messagesContainer.value
  if (el) {
    el.addEventListener('scroll', onScroll)
    requestAnimationFrame(() => onScroll())
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('copy-success', () => {})
  const el = messagesContainer.value
  if (el) el.removeEventListener('scroll', onScroll)
})

const suggestions = ['解释这段代码', '优化这个函数', '写个单元测试', '检查潜在问题']

// 不在聊天区显示的静默命令（只发给后端，不加用户消息气泡）
const SILENT_CMDS = new Set(['/agree', '/deny', '/exit'])

const hasAssistant = computed(() => messages.value.some(m => m.role === 'assistant' && m.blocks?.length > 0))

const now = () => new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })

// 配置marked选项 —— 代码块右上角悬浮复制按钮
const markedRenderer = new marked.Renderer()
markedRenderer.code = (code, language) => {
  const lang = language ? ` class="language-${language}"` : ''
  return `<div class="code-block-wrap">
    <pre><code${lang}>${code}</code><button class="code-copy-btn" onclick="copyCode(this)" title="复制代码">${COPY_ICON}</button></pre>
  </div>`
}

marked.setOptions({
  breaks: true,
  gfm: true,
  headerIds: false,
  mangle: false,
  renderer: markedRenderer
})

// 全局函数：代码复制（被 onclick 引用）
// SVG 复制图标
const COPY_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>'
const CHECK_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>'

window.copyCode = (btn) => {
  const wrap = btn.closest('.code-block-wrap')
  const code = wrap?.querySelector('code')?.textContent || ''
  navigator.clipboard.writeText(code).then(() => {
    // 通过自定义事件通知 Vue 更新日志条
    window.dispatchEvent(new CustomEvent('copy-success', { detail: '代码已复制' }))
  }).catch(() => {})
}

const fmt = c => {
  if (!c) return ''
  return marked(c)
}

// 复制整条消息内容
const copyMessage = (msg) => {
  let text = ''
  if (msg.role === 'user') {
    text = msg.content || ''
  } else if (msg.role === 'assistant' && msg.blocks) {
    text = msg.blocks
      .filter(b => b.type === 'content' || b.type === 'reasoning')
      .map(b => b.content || '')
      .join('\n\n')
  }
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    window.dispatchEvent(new CustomEvent('copy-success', { detail: '消息已复制' }))
  }).catch(() => {})
}

const fmtArgs = a => {
  if (typeof a === 'string') { try { return JSON.stringify(JSON.parse(a), null, 2) } catch { return a } }
  return JSON.stringify(a, null, 2)
}

// 输入框事件已迁移到 ChatInput 组件

const showScrollBtn = ref(false)

// 用户是否主动滚离了底部（区别于被内容推上去）
let userScrolledAway = false

const SCROLL_THRESHOLD = 80

const isNearBottom = () => {
  const el = messagesContainer.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < SCROLL_THRESHOLD
}

const scroll = async (force = false, smooth = false) => {
  await nextTick()
  const el = messagesContainer.value
  if (!el) return
  // 流式渲染中只要用户没主动滚走就一直滚；否则按阈值
  if (force || (streaming.value && !userScrolledAway) || isNearBottom()) {
    el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  }
  updateScrollBtn()
}

const scrollToBottom = () => {
  userScrolledAway = false
  scroll(true, true)
}

// 监听容器的滚动事件，检测用户是否主动滚离底部
const updateScrollBtn = () => {
  const nearBottom = isNearBottom()
  showScrollBtn.value = !nearBottom
  if (!nearBottom && !streaming.value) {
    // 不在流式时，用户滚走就算主动离开
    userScrolledAway = true
  }
}

// 额外监听 wheel / touch 事件：滚动中如果用户向上滚，标记为主动离开
const onScroll = () => {
  const el = messagesContainer.value
  if (!el) return
  const nearBottom = isNearBottom()
  showScrollBtn.value = !nearBottom
  if (!nearBottom) {
    userScrolledAway = true
  } else {
    userScrolledAway = false
  }
}

/** 用户点击选项按钮 → 直接发送 value 作为消息，清理旧工具卡片 */
const sendChoice = (value, block) => {
  // 标记已选择
  if (block) {
    block.resolved = true
    const opt = (block.options || []).find(o => o.value === value)
    block.selectedTitle = opt ? opt.title : value
  }
  // 清理当前消息中已拦截的 tool_call 块（避免与重放执行重复）
  const last = messages.value[messages.value.length - 1]
  if (last?.role === 'assistant' && last.blocks) {
    last.blocks = last.blocks.filter(b =>
      b === block || b.type !== 'tool_call'
    )
  }
  inputText.value = value
  sendMessage()
}

// 键盘事件已迁移到 ChatInput 组件

/**
 * 核心发送逻辑：
 * - 普通消息：加用户气泡 → 创建助手占位 → 流式填充
 * - 静默命令（SILENT_CMDS 中的命令，如 /new、/agree、/deny 等）：不加气泡直接发后端；
 *   收到有内容的 SSE 事件时才创建助手气泡
 * - /skill: 命令：显示用户气泡 + 助手气泡（正常流程）
 */
const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || streaming.value) return

  const firstWord = text.split(/\s+/)[0].toLowerCase()
  // 静默命令不显示用户气泡（系统命令、模式切换、HITL 审批等）
  const isSilent = SILENT_CMDS.has(firstWord)

  // 静默命令不显示用户气泡
  if (!isSilent) {
    messages.value.push({ id: Date.now(), role: 'user', content: text, time: now() })
  }
  userScrolledAway = false
  inputText.value = ''
  await scroll(true)  // 用户刚发送，强制滚到底

  streaming.value = true
  const mi = messages.value.length

  // 静默命令不预创建助手占位
  if (!isSilent) {
    messages.value.push({ id: Date.now() + 1, role: 'assistant', time: now(), blocks: [] })
  }

  let getMsg = () => messages.value[isSilent ? -1 : mi] // 静默命令时 getMsg 返回 undefined
  let silentBubbleCreated = false // 静默命令首次收到内容时创建气泡，之后复用

  try {
    const streamResult = chatAPI.sendMessageStream(text,
      data => {
        currentAbortController = streamResult
        // 静默命令：首次收到有内容的数据时才创建助手气泡（只创建一次）
        if (isSilent && !silentBubbleCreated) {
          if (!data.type || data.type === 'done') return
          const hasContent = (data.type === 'content' && data.content?.trim()) ||
                             (data.type === 'reasoning' && data.content?.trim()) ||
                             data.type === 'tool_call' || data.type === 'tool_result' || data.type === 'error'
          if (!hasContent) return
          // 有实际内容了，插入助手气泡
          messages.value.push({ id: Date.now(), role: 'assistant', time: now(), blocks: [] })
          silentBubbleCreated = true
          getMsg = () => messages.value[messages.value.length - 1]
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
        } else if (data.type === 'choice') {
          // 选项按钮（如 HITL 审批）
          let options = data.options || []
          if (typeof options === 'string') {
            try { options = JSON.parse(options) } catch {}
          }
          if (Array.isArray(options) && options.length > 0) {
            msg.blocks.push({ type: 'choice', options })
          }
        } else if (data.type === 'log') {
          // 系统日志（如 [compact] 折叠结果）→ 仅展示 INFO 及以上级别
          const level = (data.level || 'INFO').toUpperCase()
          if (level === 'DEBUG') return
          const text = data.message || data.content || ''
          addLog({ level, text, time: Date.now() })
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
  // 发送 /new 给后端清空会话
  streaming.value = true
  try {
    chatAPI.sendMessageStream('/new', () => {}, () => { streaming.value = false; loadUsage() }, () => { streaming.value = false })
  } catch { streaming.value = false }
}

// 暴露给父组件的清空方法（/new 属于 SILENT_CMDS，不显示气泡）
const clearMessages = () => {
  messages.value = []
  streaming.value = true
  try {
    chatAPI.sendMessageStream('/new', () => {}, () => { streaming.value = false; loadUsage() }, () => { streaming.value = false })
  } catch { streaming.value = false }
}

// 仅清空本地消息，不请求后端（配合 REST API 创建新会话时使用）
const resetLocalMessages = () => {
  messages.value = []
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

const loadHistory = async (sessionName) => {
  try {
    const r = await agentAPI.getHistory(props.workspaceHash, sessionName || props.sessionName)
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
      await scroll(true)
    }
  } catch {}
}

const loadSession = async (name, workspaceHash) => {
  try {
    const { sessionsAPI } = await import('../services/api')
    await sessionsAPI.switchSession(name)
    await loadHistory(name)   // 显式传 sessionName，避免 props 尚未更新的时序问题
    // 切换会话后刷新 usage 数据（传入 sessionName 和 workspaceHash）
    await loadUsage({ sessionName: name, workspaceHash })
  } catch (e) { console.error('切换会话失败:', e) }
}

const sendCommand = async cmd => { inputText.value = cmd; await sendMessage() }

// 加载历史消息（仅在明确选了 session 时）
onMounted(() => { 
  if (props.sessionName) loadHistory()
})

defineExpose({ clearMessages, resetLocalMessages, loadSession, sendCommand, exportChat })
</script>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg);
  position: relative;
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
  position: relative;
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
.user-body ::selection {
  background: rgba(255, 255, 255, 0.35);
  color: #000;
}
.user-body .msg-time { font-size: 10px; opacity: 0.7; margin-top: 4px; text-align: right; }

.assistant-body {
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 8px 12px;
}
.assistant-body ::selection {
  background: var(--accent);
  color: #fff;
}
.assistant-body .msg-time { font-size: 10px; color: var(--fg-4); margin-top: 4px; }

.msg-text { font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }

/* 消息底部栏（时间 + 复制按钮） */
.msg-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  gap: 8px;
}
.copy-msg-btn {
  opacity: 0;
  background: none;
  border: none;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: var(--r-sm);
  transition: opacity 0.15s;
  line-height: 1;
  color: var(--fg-3);
}
.user-body .copy-msg-btn {
  color: rgba(255, 255, 255, 0.7);
}
.msg-body:hover .copy-msg-btn {
  opacity: 0.7;
}
.copy-msg-btn:hover {
  opacity: 1 !important;
}

/* 代码块内嵌复制按钮（通过 :deep 穿透 v-html） */
.block-content :deep(.code-block-wrap) {
  margin: 8px 0;
}
.block-content :deep(.code-block-wrap pre) {
  position: relative;
  margin: 0 !important;
}
.block-content :deep(.code-copy-btn) {
  position: absolute;
  top: 6px;
  right: 6px;
  opacity: 0;
  background: var(--bg-2);
  border: 1px solid var(--border);
  font-size: 13px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: var(--r-sm);
  transition: opacity 0.15s;
  line-height: 1;
  z-index: 2;
}
.block-content :deep(.code-block-wrap pre:hover .code-copy-btn) {
  opacity: 0.7;
}
.block-content :deep(.code-copy-btn:hover) {
  opacity: 1 !important;
  background: var(--bg);
}

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

/* 选项按钮 */
.block-choice {
  margin: 4px 0;
}
.choice-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.choice-btn {
  padding: 6px 16px;
  border: 1px solid var(--accent);
  border-radius: var(--r);
  background: var(--accent-bg, rgba(var(--accent-rgb, 59 130 246), 0.1));
  color: var(--accent);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--t);
}
.choice-btn:hover {
  background: var(--accent);
  color: #fff;
}
.choice-resolved {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  font-size: 13px;
  color: var(--fg-3);
}
.choice-label {
  font-weight: 500;
}
.choice-value {
  color: var(--accent);
  font-weight: 600;
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

/* 全部输入区样式已迁移到 ChatInput.vue 组件中（.input-area, .input-box, .usage-bar, .todo-*, .slash-popup, .model-selector 等） */

/* 滚动到底部按钮（固定在消息区右下角，不随内容滚动） */
.scroll-bottom-btn {
  position: absolute;
  right: 24px;
  bottom: 110px;
  z-index: 60;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--accent);
  color: #fff;
  border: 2px solid var(--bg);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}
.scroll-bottom-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}
.scroll-bottom-btn svg {
  animation: bounce-down 1.5s ease-in-out infinite;
}
@keyframes bounce-down {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(3px); }
}
/* 使用 v-show 控制显隐 */

/* ===== 日志堆叠容器 ===== */
.log-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: sticky;
  top: 0;
  z-index: 50;
  margin-bottom: 8px;
  pointer-events: none;
}
.log-stack > * {
  pointer-events: auto;
}

/* 🎯 灵动岛风格日志通知 — 大气居中，超长省略 */
.log-bar {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 18px;
  margin: 0 auto;

  background: rgba(30, 30, 40, 0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.4;
  color: #f0f0f0;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.25);
}
.log-bar:hover {
  background: rgba(40, 40, 55, 0.92);
  border-radius: 10px;
  padding: 8px 20px;
}
.log-bar.log-warn {
  border-color: rgba(245, 158, 11, 0.5);
}
.log-bar.log-error {
  border-color: rgba(239, 68, 68, 0.5);
}
.log-bar-icon {
  flex-shrink: 0;
  font-size: 14px;
  line-height: 1;
}
.log-bar-text {
  min-width: 0;
  max-width: 50ch;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 500;
  transition: max-width 0.3s ease;
}
.log-bar:hover .log-bar-text {
  max-width: 100ch;
}
.log-bar-time {
  flex-shrink: 0;
  font-size: 10px;
  opacity: 0.4;
  font-family: var(--mono);
}
/* 进出动画：从顶部滑入 + 淡入 */
.log-bar-enter-active {
  transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.log-bar-leave-active {
  transition: all 0.2s ease;
}
.log-bar-enter-from {
  opacity: 0;
  transform: translateY(-16px) scale(0.92);
}
.log-bar-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}
</style>