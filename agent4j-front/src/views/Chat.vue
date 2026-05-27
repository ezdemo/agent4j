<template>
  <div class="chat-view">
    <!-- 聊天头部（可选显示） -->
    <div v-if="!hideHeader" class="chat-header">
      <div class="header-left">
        <div class="header-title">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <h2>对话终端</h2>
        </div>
        <span class="message-count">{{ messages.length }} 条消息</span>
      </div>
      <div class="header-actions">
        <button class="btn btn-ghost btn-sm" @click="clearChat">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/>
            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
          </svg>
          清空
        </button>
        <button class="btn btn-ghost btn-sm" @click="exportChat">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          导出
        </button>
      </div>
    </div>
    
    <!-- 消息列表 -->
    <div class="chat-messages" ref="messagesContainer">
      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
          </svg>
        </div>
        <h3 class="empty-title">开始对话</h3>
        <p class="empty-description">输入您的问题或指令，Agent4j 将为您提供智能帮助</p>
        <div class="empty-suggestions">
          <button 
            v-for="suggestion in suggestions" 
            :key="suggestion"
            class="suggestion-btn"
            @click="inputText = suggestion"
          >
            {{ suggestion }}
          </button>
        </div>
      </div>
      
      <!-- 消息列表 -->
      <div 
        v-for="(message, index) in messages" 
        :key="message.id"
        class="message-wrapper"
        :class="[message.role, { 'animate-fade-in-up': isNewMessage(index) }]"
      >
        <!-- 用户消息 -->
        <div v-if="message.role === 'user'" class="message user-message">
          <div class="message-avatar user-avatar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-role">您</span>
              <span class="message-time">{{ message.time }}</span>
            </div>
            <div class="message-text">{{ message.content }}</div>
          </div>
        </div>
        
        <!-- 助手消息 -->
        <div v-else-if="message.role === 'assistant'" class="message assistant-message">
          <div class="message-avatar assistant-avatar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
            </svg>
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-role">Agent4j</span>
              <span class="message-time">{{ message.time }}</span>
              <span v-if="message.usage" class="usage-badge">
                {{ formatTokens(message.usage.totalTokens) }} tokens
              </span>
            </div>
            
            <!-- 消息块 -->
            <div class="message-blocks">
              <template v-for="(block, bIdx) in (message.blocks || [])" :key="bIdx">
                <!-- 思考块 -->
                <div v-if="block.type === 'reasoning'" class="thinking-block">
                  <div class="thinking-header" @click="block.showContent = !block.showContent">
                    <div class="thinking-label">
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="12" cy="12" r="10"/>
                        <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                        <line x1="12" y1="17" x2="12.01" y2="17"/>
                      </svg>
                      <span>思考过程</span>
                      <span v-if="getReasoningCount(message.blocks, bIdx) > 1" class="thinking-count">
                        #{{ getReasoningCount(message.blocks, bIdx) }}
                      </span>
                    </div>
                    <svg 
                      width="14" 
                      height="14" 
                      viewBox="0 0 24 24" 
                      fill="none" 
                      stroke="currentColor" 
                      stroke-width="2"
                      class="thinking-toggle"
                      :class="{ expanded: block.showContent }"
                    >
                      <polyline points="6 9 12 15 18 9"/>
                    </svg>
                  </div>
                  <div v-if="block.showContent" class="thinking-content">
                    <div class="thinking-text">{{ block.content }}</div>
                  </div>
                </div>
                
                <!-- 内容块 -->
                <div v-else-if="block.type === 'content'" class="content-block">
                  <div class="content-text" v-html="formatMessage(block.content)"></div>
                </div>
                
                <!-- 工具调用块 -->
                <div v-else-if="block.type === 'tool_call'" class="tool-block">
                  <div class="tool-header" @click="block.expanded = !block.expanded">
                    <div class="tool-info">
                      <div class="tool-icon" :class="block.status">
                        <svg v-if="block.status === '执行中'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="animate-spin">
                          <path d="M21 12a9 9 0 11-6.219-8.56"/>
                        </svg>
                        <svg v-else-if="block.status === '成功'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <polyline points="20 6 9 17 4 12"/>
                        </svg>
                        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <circle cx="12" cy="12" r="10"/>
                          <line x1="12" y1="8" x2="12" y2="12"/>
                          <line x1="12" y1="16" x2="12.01" y2="16"/>
                        </svg>
                      </div>
                      <div class="tool-name">{{ block.name }}</div>
                      <span class="tool-status" :class="block.status">{{ block.status }}</span>
                    </div>
                    <svg 
                      width="14" 
                      height="14" 
                      viewBox="0 0 24 24" 
                      fill="none" 
                      stroke="currentColor" 
                      stroke-width="2"
                      class="tool-toggle"
                      :class="{ expanded: block.expanded }"
                    >
                      <polyline points="6 9 12 15 18 9"/>
                    </svg>
                  </div>
                  
                  <div v-if="block.expanded" class="tool-details">
                    <!-- 参数 -->
                    <div v-if="block.args" class="tool-args">
                      <div class="detail-label">参数</div>
                      <pre class="detail-content"><code>{{ formatArgs(block.args) }}</code></pre>
                    </div>
                    
                    <!-- 结果 -->
                    <div v-if="block.result" class="tool-result">
                      <div class="detail-label">结果</div>
                      <pre class="detail-content"><code>{{ block.result }}</code></pre>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 加载指示器 -->
      <div v-if="streaming && !messages.some(m => m.role === 'assistant' && m.blocks?.length > 0)" class="loading-indicator">
        <div class="loading-avatar">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
          </svg>
        </div>
        <div class="loading-content">
          <div class="loading-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
          <span class="loading-text">正在思考...</span>
        </div>
      </div>
    </div>
    
    <!-- 输入区域 -->
    <div class="chat-input">
      <div class="input-wrapper">
        <!-- 附件预览 -->
        <div v-if="attachedFiles.length" class="attached-files">
          <div v-for="(file, index) in attachedFiles" :key="index" class="file-item">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/>
              <polyline points="13 2 13 9 20 9"/>
            </svg>
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size">({{ formatFileSize(file.size) }})</span>
            <button class="btn-icon-sm remove-file" @click="removeFile(index)">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
        </div>
        
        <!-- 输入框 -->
        <div class="input-container">
          <textarea 
            v-model="inputText" 
            @keydown.enter.exact="handleEnter"
            @keydown.shift.enter="handleShiftEnter"
            @input="autoResize"
            placeholder="输入您的问题... (Enter 发送, Shift+Enter 换行)"
            rows="1"
            ref="inputField"
            class="message-input"
          ></textarea>
          
          <div class="input-actions">
            <label class="btn-icon-sm action-btn" title="添加附件">
              <input type="file" @change="handleFileUpload" multiple class="hidden" />
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
              </svg>
            </label>
            
            <button 
              class="btn-icon-sm action-btn" 
              :class="{ active: planMode }"
              @click="togglePlanMode"
              title="计划模式"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="16" y1="13" x2="8" y2="13"/>
                <line x1="16" y1="17" x2="8" y2="17"/>
                <polyline points="10 9 9 9 8 9"/>
              </svg>
            </button>
            
            <button 
              class="send-btn"
              :class="{ active: inputText.trim() && !streaming }"
              @click="sendMessage"
              :disabled="!inputText.trim() || streaming"
              title="发送消息 (Enter)"
            >
              <svg v-if="streaming" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="animate-spin">
                <path d="M21 12a9 9 0 11-6.219-8.56"/>
              </svg>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="22" y1="2" x2="11" y2="13"/>
                <polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </div>
        </div>
        
        <!-- 输入提示 -->
        <div class="input-hints">
          <span class="hint">
            <kbd>Enter</kbd> 发送
          </span>
          <span class="hint">
            <kbd>Shift</kbd> + <kbd>Enter</kbd> 换行
          </span>
          <span v-if="planMode" class="hint plan-hint">
            计划模式已启用
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, onBeforeUnmount } from 'vue'
import { chatAPI, agentAPI, sessionsAPI } from '../services/api'

// Props
const props = defineProps({
  hideHeader: { type: Boolean, default: false }
})

// 响应式状态
const messagesContainer = ref(null)
const inputField = ref(null)
const inputText = ref('')
const messages = ref([])
const attachedFiles = ref([])
const streaming = ref(false)
const planMode = ref(false)
const currentEventSource = ref(null)
const lastMessageIndex = ref(-1)

// 建议问题
const suggestions = [
  '解释一下这段代码的作用',
  '如何优化这个函数的性能？',
  '帮我写一个单元测试',
  '检查代码中的潜在问题'
]

// 工具函数
const formatTokens = (n) => {
  if (!n) return '0'
  if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'K'
  return String(n)
}

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatMessage = (content) => {
  if (!content) return ''
  
  // Markdown 转 HTML
  let html = content
    // 代码块
    .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code class="language-$1">$2</code></pre>')
    // 行内代码
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // 粗体
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    // 斜体
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    // 链接
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
    // 标题
    .replace(/^### (.*$)/gm, '<h4>$1</h4>')
    .replace(/^## (.*$)/gm, '<h3>$1</h3>')
    .replace(/^# (.*$)/gm, '<h2>$1</h2>')
    // 列表
    .replace(/^\s*[-*]\s+(.*$)/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
    // 换行
    .replace(/\n/g, '<br>')
  
  return html
}

const formatArgs = (args) => {
  if (typeof args === 'string') {
    try {
      return JSON.stringify(JSON.parse(args), null, 2)
    } catch {
      return args
    }
  }
  return JSON.stringify(args, null, 2)
}

const getReasoningCount = (blocks, currentIndex) => {
  let count = 0
  for (let i = 0; i <= currentIndex; i++) {
    if (blocks[i].type === 'reasoning') {
      count++
    }
  }
  return count
}

const isNewMessage = (index) => {
  return index > lastMessageIndex.value
}

// 自动调整输入框高度
const autoResize = () => {
  const textarea = inputField.value
  if (textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = Math.min(textarea.scrollHeight, 200) + 'px'
  }
}

// 滚动到底部
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTo({
      top: messagesContainer.value.scrollHeight,
      behavior: 'smooth'
    })
  }
}

// 键盘事件处理
const handleEnter = (e) => {
  if (!e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

const handleShiftEnter = () => {
  inputText.value += '\n'
  autoResize()
}

// 发送消息
const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || streaming.value) return
  
  // 添加用户消息
  const userMessage = {
    id: Date.now(),
    role: 'user',
    content: text,
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })
  }
  
  messages.value.push(userMessage)
  inputText.value = ''
  attachedFiles.value = []
  autoResize()
  await scrollToBottom()
  
  // 准备助手消息
  streaming.value = true
  const msgIndex = messages.value.length
  messages.value.push({
    id: Date.now() + 1,
    role: 'assistant',
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' }),
    blocks: []
  })
  
  const getMsg = () => messages.value[msgIndex]
  
  try {
    // 使用流式聊天
    const streamHandle = chatAPI.sendMessageStream(
      text,
      // onMessage
      (data) => {
        const msg = getMsg()
        if (!msg) return
        
        if (data.type === 'reasoning') {
          const blocks = msg.blocks
          const lastBlock = blocks[blocks.length - 1]
          if (lastBlock && lastBlock.type === 'reasoning') {
            lastBlock.content += (data.content || '')
          } else {
            blocks.push({ type: 'reasoning', content: data.content || '', showContent: true })
          }
        } else if (data.type === 'content') {
          const blocks = msg.blocks
          const lastBlock = blocks[blocks.length - 1]
          if (lastBlock && lastBlock.type === 'content') {
            lastBlock.content += (data.content || '')
          } else {
            blocks.push({ type: 'content', content: data.content || '' })
          }
        } else if (data.type === 'tool_call') {
          let name = data.name || ''
          let args = data.args || data.arguments || ''
          if (typeof args === 'string') {
            try { args = JSON.parse(args) } catch (e) { /* 保持原值 */ }
          }
          if (!name && data.content) {
            try {
              const parsed = JSON.parse(data.content)
              name = parsed.name || ''
              args = parsed.args || parsed.arguments || args
            } catch (e) {
              const m = data.content.match(/"name"\s*:\s*"?([^",}\n]+)"?/)
              if (m) name = m[1]
            }
          }
          msg.blocks.push({
            type: 'tool_call',
            name: name || 'unknown',
            status: '执行中',
            args: args,
            result: '',
            expanded: false
          })
        } else if (data.type === 'tool_result') {
          let result = data.result || data.content || ''
          if (typeof result === 'string' && result.startsWith('"') && result.endsWith('"')) {
            try { result = JSON.parse(result) } catch (e) { /* 保持原值 */ }
          }
          const resultStr = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
          const resultName = data.name || ''
          
          let matched = false
          for (let i = msg.blocks.length - 1; i >= 0; i--) {
            if (msg.blocks[i].type === 'tool_call' && !msg.blocks[i].result) {
              if (resultName && msg.blocks[i].name === resultName) {
                msg.blocks[i].result = resultStr
                msg.blocks[i].status = '成功'
                matched = true
                break
              }
            }
          }
          if (!matched) {
            for (let i = msg.blocks.length - 1; i >= 0; i--) {
              if (msg.blocks[i].type === 'tool_call' && !msg.blocks[i].result) {
                msg.blocks[i].result = resultStr
                msg.blocks[i].status = '成功'
                break
              }
            }
          }
        } else if (data.type === 'error') {
          msg.blocks.push({ type: 'content', content: `❌ 错误: ${data.error || data.content || '未知错误'}` })
        } else if (data.type === 'usage') {
          msg.usage = data
        } else if (data.type === 'reply') {
          const replyContent = data.content || ''
          if (replyContent) {
            const hasContent = msg.blocks.some(b => b.type === 'content')
            if (!hasContent) {
              msg.blocks.push({ type: 'content', content: replyContent })
            }
          }
        }
        scrollToBottom()
      },
      // onDone
      () => {
        streaming.value = false
        lastMessageIndex.value = messages.value.length - 1
      },
      // onError
      (error) => {
        console.error('SSE连接错误:', error)
        streaming.value = false
        const msg = getMsg()
        if (msg && msg.blocks.every(b => !b.content)) {
          msg.blocks.push({ type: 'content', content: '❌ 连接错误，请检查后端服务是否正常运行。' })
        }
      }
    )
    currentEventSource.value = streamHandle
  } catch (error) {
    console.error('发送消息失败:', error)
    streaming.value = false
    
    try {
      const response = await chatAPI.sendMessage(text)
      const msg = getMsg()
      if (msg) {
        if (response.success) {
          msg.blocks.push({ type: 'content', content: response.data.reply })
        } else {
          msg.blocks.push({ type: 'content', content: `❌ 错误: ${response.error}` })
        }
      }
    } catch (syncError) {
      const msg = getMsg()
      if (msg) {
        msg.blocks.push({ type: 'content', content: `❌ 发送失败: ${syncError.message}` })
      }
    }
  }
  
  await scrollToBottom()
}

// 清空聊天
const clearChat = async () => {
  if (confirm('确定要清空所有对话记录吗？')) {
    messages.value = []
    inputText.value = '/new'
    await sendMessage()
  }
}

// 导出聊天
const exportChat = () => {
  const chatText = messages.value.map(msg => {
    const header = `[${msg.time}] ${msg.role === 'user' ? '用户' : '助手'}:`
    let content = header + '\n'
    
    if (msg.blocks) {
      for (const block of msg.blocks) {
        if (block.type === 'reasoning') {
          content += '\n💭 思考过程:\n' + block.content + '\n'
        } else if (block.type === 'content') {
          content += block.content + '\n'
        } else if (block.type === 'tool_call') {
          content += `\n🔧 工具调用 - ${block.name}:`
          if (block.args) {
            content += `\n   参数: ${formatArgs(block.args)}`
          }
          if (block.result) {
            content += `\n   结果: ${block.result}`
          }
          content += '\n'
        }
      }
    }
    
    return content
  }).join('\n---\n\n')
  
  const blob = new Blob([chatText], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `agent4j-chat-${new Date().toISOString().slice(0, 10)}.txt`
  a.click()
  URL.revokeObjectURL(url)
}

// 文件处理
const handleFileUpload = (event) => {
  const files = Array.from(event.target.files)
  attachedFiles.value.push(...files)
  event.target.value = ''
}

const removeFile = (index) => {
  attachedFiles.value.splice(index, 1)
}

// 计划模式
const togglePlanMode = async () => {
  planMode.value = !planMode.value
  inputText.value = planMode.value ? '/plan' : '/execute'
  await sendMessage()
}

// 加载历史
const loadHistory = async () => {
  try {
    const response = await agentAPI.getHistory()
    if (response.success && response.data) {
      const raw = response.data
      const toolResults = {}
      for (const msg of raw) {
        if (msg.role === 'tool' && msg.tool_call_id) {
          toolResults[msg.tool_call_id] = msg.content || ''
        }
      }
      
      const merged = []
      for (const msg of raw) {
        if (msg.role === 'tool') continue
        
        const item = {
          id: Date.now() + merged.length,
          role: msg.role,
          time: new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' }),
          blocks: []
        }
        
        if (msg.reasoning_content) {
          item.blocks.push({ type: 'reasoning', content: msg.reasoning_content, showContent: false })
        }
        
        if (msg.tool_calls && Array.isArray(msg.tool_calls)) {
          for (const tc of msg.tool_calls) {
            let name = ''
            let args = tc.function?.arguments || tc.arguments || ''
            if (tc.function) {
              name = tc.function.name || ''
            } else {
              name = tc.name || ''
            }
            if (typeof args === 'string') {
              try { args = JSON.parse(args) } catch (e) { /* 保持原字符串 */ }
            }
            const result = toolResults[tc.id] || ''
            item.blocks.push({
              type: 'tool_call',
              name,
              status: result ? '成功' : '执行中',
              args,
              result,
              expanded: false
            })
          }
        }
        
        if (msg.content) {
          item.blocks.push({ type: 'content', content: msg.content })
        }
        
        merged.push(item)
      }
      
      messages.value = merged
      lastMessageIndex.value = merged.length - 1
    }
  } catch (error) {
    console.error('加载历史消息失败:', error)
  }
}

// 加载会话
const loadSession = async (name) => {
  try {
    await sessionsAPI.loadSession(name)
    await loadHistory()
  } catch (error) {
    console.error('加载会话失败:', error)
  }
}

// 发送命令
const sendCommand = async (command) => {
  inputText.value = command
  await sendMessage()
}

// 生命周期
onMounted(async () => {
  await loadHistory()
  await scrollToBottom()
  
  // 监听清空事件
  window.addEventListener('terminal-clear', clearChat)
})

onBeforeUnmount(() => {
  window.removeEventListener('terminal-clear', clearChat)
  if (currentEventSource.value) {
    currentEventSource.value.close()
  }
})

// 暴露方法给父组件
defineExpose({
  clearMessages: () => { messages.value = [] },
  loadSession,
  sendCommand,
  exportChat,
  streaming: () => streaming.value
})
</script>

<style scoped>
/* 聊天视图 */
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg);
  overflow: hidden;
}

/* 聊天头部 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-6);
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.header-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--fg);
}

.header-title svg {
  color: var(--brand-primary);
}

.header-title h2 {
  font-size: var(--text-lg);
  font-weight: var(--font-semibold);
}

.message-count {
  font-size: var(--text-sm);
  color: var(--fg-muted);
  background: var(--bg-tertiary);
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-full);
}

.header-actions {
  display: flex;
  gap: var(--space-2);
}

/* 消息列表 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-4) var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  scroll-behavior: smooth;
}

/* 空状态 */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: var(--space-8);
  color: var(--fg-muted);
}

.empty-icon {
  width: 80px;
  height: 80px;
  background: var(--gradient-primary);
  border-radius: var(--radius-2xl);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-6);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.3);
}

.empty-icon svg {
  color: white;
}

.empty-title {
  font-size: var(--text-2xl);
  font-weight: var(--font-bold);
  color: var(--fg);
  margin-bottom: var(--space-2);
}

.empty-description {
  font-size: var(--text-base);
  color: var(--fg-muted);
  margin-bottom: var(--space-6);
  max-width: 400px;
}

.empty-suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  justify-content: center;
}

.suggestion-btn {
  padding: var(--space-2) var(--space-4);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-full);
  font-size: var(--text-sm);
  color: var(--fg-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.suggestion-btn:hover {
  background: var(--accent-soft);
  border-color: var(--brand-primary);
  color: var(--brand-primary);
  transform: translateY(-2px);
}

/* 消息包装器 */
.message-wrapper {
  display: flex;
  gap: var(--space-3);
  max-width: 100%;
  animation: fadeInUp var(--transition-base) ease-out;
}

.message-wrapper.user-message {
  flex-direction: row-reverse;
}

/* 消息 */
.message {
  display: flex;
  gap: var(--space-3);
  max-width: 85%;
}

.user-message {
  margin-left: auto;
}

.assistant-message {
  margin-right: auto;
}

/* 头像 */
.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
}

.user-avatar {
  background: var(--gradient-secondary);
  color: white;
}

.assistant-avatar {
  background: var(--gradient-primary);
  color: white;
}

/* 消息内容 */
.message-content {
  flex: 1;
  min-width: 0;
}

.message-header {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-1);
}

.user-message .message-header {
  justify-content: flex-end;
}

.message-role {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  color: var(--fg);
}

.message-time {
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

.usage-badge {
  font-size: var(--text-xs);
  color: var(--brand-primary);
  background: var(--accent-soft);
  padding: 0.125rem 0.5rem;
  border-radius: var(--radius-full);
}

/* 消息文本 */
.message-text {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-4);
  font-size: var(--text-base);
  line-height: 1.6;
  color: var(--fg);
  white-space: pre-wrap;
  word-break: break-word;
}

.user-message .message-text {
  background: var(--brand-primary);
  color: white;
  border-color: var(--brand-primary);
}

/* 消息块 */
.message-blocks {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

/* 思考块 */
.thinking-block {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--transition-fast);
}

.thinking-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.thinking-header:hover {
  background: var(--surface-hover);
}

.thinking-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--fg-secondary);
}

.thinking-label svg {
  color: var(--warning);
}

.thinking-count {
  font-size: var(--text-xs);
  color: var(--fg-muted);
  background: var(--bg-tertiary);
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-sm);
}

.thinking-toggle {
  color: var(--fg-muted);
  transition: transform var(--transition-fast);
}

.thinking-toggle.expanded {
  transform: rotate(180deg);
}

.thinking-content {
  padding: 0 var(--space-4) var(--space-4);
  border-top: 1px solid var(--border);
}

.thinking-text {
  font-size: var(--text-sm);
  line-height: 1.6;
  color: var(--fg-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono);
}

/* 内容块 */
.content-block {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-4);
}

.content-text {
  font-size: var(--text-base);
  line-height: 1.6;
  color: var(--fg);
}

.content-text :deep(h2),
.content-text :deep(h3),
.content-text :deep(h4) {
  margin-top: var(--space-4);
  margin-bottom: var(--space-2);
  font-weight: var(--font-semibold);
  color: var(--fg);
}

.content-text :deep(h2) {
  font-size: var(--text-xl);
}

.content-text :deep(h3) {
  font-size: var(--text-lg);
}

.content-text :deep(h4) {
  font-size: var(--text-base);
}

.content-text :deep(pre) {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--space-3);
  margin: var(--space-2) 0;
  overflow-x: auto;
}

.content-text :deep(code) {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  background: var(--bg-tertiary);
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-sm);
}

.content-text :deep(pre code) {
  background: none;
  padding: 0;
}

.content-text :deep(ul),
.content-text :deep(ol) {
  margin: var(--space-2) 0;
  padding-left: var(--space-6);
}

.content-text :deep(li) {
  margin-bottom: var(--space-1);
}

.content-text :deep(a) {
  color: var(--brand-primary);
  text-decoration: none;
}

.content-text :deep(a:hover) {
  text-decoration: underline;
}

.content-text :deep(strong) {
  font-weight: var(--font-semibold);
  color: var(--fg);
}

.content-text :deep(em) {
  font-style: italic;
}

/* 工具块 */
.tool-block {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: all var(--transition-fast);
}

.tool-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.tool-header:hover {
  background: var(--surface-hover);
}

.tool-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.tool-icon {
  width: 28px;
  height: 28px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tool-icon.执行中 {
  background: var(--warning-bg);
  color: var(--warning);
}

.tool-icon.成功 {
  background: var(--success-bg);
  color: var(--success);
}

.tool-icon.错误 {
  background: var(--danger-bg);
  color: var(--danger);
}

.tool-name {
  font-size: var(--text-sm);
  font-weight: var(--font-semibold);
  font-family: var(--font-mono);
  color: var(--fg);
}

.tool-status {
  font-size: var(--text-xs);
  font-weight: var(--font-medium);
  padding: 0.125rem 0.5rem;
  border-radius: var(--radius-full);
}

.tool-status.执行中 {
  background: var(--warning-bg);
  color: var(--warning);
}

.tool-status.成功 {
  background: var(--success-bg);
  color: var(--success);
}

.tool-status.错误 {
  background: var(--danger-bg);
  color: var(--danger);
}

.tool-toggle {
  color: var(--fg-muted);
  transition: transform var(--transition-fast);
}

.tool-toggle.expanded {
  transform: rotate(180deg);
}

.tool-details {
  padding: 0 var(--space-4) var(--space-4);
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.tool-args,
.tool-result {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.detail-label {
  font-size: var(--text-xs);
  font-weight: var(--font-semibold);
  color: var(--fg-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.detail-content {
  background: var(--bg-tertiary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: var(--space-3);
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  line-height: 1.5;
  overflow-x: auto;
  max-height: 200px;
  overflow-y: auto;
}

.detail-content code {
  background: none;
  padding: 0;
  font-size: inherit;
}

/* 加载指示器 */
.loading-indicator {
  display: flex;
  gap: var(--space-3);
  padding: var(--space-4);
  animation: fadeIn var(--transition-base) ease-out;
}

.loading-avatar {
  width: 36px;
  height: 36px;
  background: var(--gradient-primary);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
  box-shadow: var(--shadow-sm);
}

.loading-content {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-4);
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

.loading-text {
  font-size: var(--text-sm);
  color: var(--fg-muted);
}

/* 输入区域 */
.chat-input {
  padding: var(--space-4) var(--space-6);
  background: var(--surface);
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}

.input-wrapper {
  max-width: 100%;
  margin: 0 auto;
}

/* 附件预览 */
.attached-files {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.file-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  font-size: var(--text-sm);
}

.file-item svg {
  color: var(--fg-muted);
  flex-shrink: 0;
}

.file-name {
  color: var(--fg);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  color: var(--fg-muted);
  font-size: var(--text-xs);
}

.remove-file {
  color: var(--fg-muted);
  transition: color var(--transition-fast);
}

.remove-file:hover {
  color: var(--danger);
}

/* 输入容器 */
.input-container {
  display: flex;
  align-items: flex-end;
  gap: var(--space-2);
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  padding: var(--space-2);
  transition: all var(--transition-fast);
}

.input-container:focus-within {
  border-color: var(--border-focus);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.message-input {
  flex: 1;
  min-height: 24px;
  max-height: 200px;
  padding: var(--space-2) var(--space-3);
  background: none;
  border: none;
  outline: none;
  resize: none;
  font-size: var(--text-base);
  line-height: 1.5;
  color: var(--fg);
}

.message-input::placeholder {
  color: var(--fg-muted);
}

.input-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-1);
}

.action-btn {
  width: 36px;
  height: 36px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fg-muted);
  transition: all var(--transition-fast);
  cursor: pointer;
}

.action-btn:hover {
  background: var(--surface-hover);
  color: var(--fg);
}

.action-btn.active {
  background: var(--accent-soft);
  color: var(--brand-primary);
}

.hidden {
  display: none;
}

.send-btn {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-tertiary);
  color: var(--fg-muted);
  transition: all var(--transition-fast);
  cursor: pointer;
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

/* 输入提示 */
.input-hints {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  margin-top: var(--space-2);
  padding: 0 var(--space-2);
}

.hint {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-xs);
  color: var(--fg-muted);
}

.hint kbd {
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

.plan-hint {
  color: var(--brand-primary);
  font-weight: var(--font-medium);
}

/* 动画 */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

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

/* 响应式设计 */
@media (max-width: 768px) {
  .chat-header {
    padding: var(--space-3) var(--space-4);
  }
  
  .chat-messages {
    padding: var(--space-3) var(--space-4);
  }
  
  .chat-input {
    padding: var(--space-3) var(--space-4);
  }
  
  .message {
    max-width: 95%;
  }
  
  .input-hints {
    display: none;
  }
  
  .empty-suggestions {
    flex-direction: column;
    align-items: center;
  }
  
  .suggestion-btn {
    width: 100%;
    max-width: 300px;
    text-align: center;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .message-text {
  background: var(--surface);
  border-color: var(--border);
}

[data-theme="dark"] .user-message .message-text {
  background: var(--brand-primary);
  border-color: var(--brand-primary);
}

[data-theme="dark"] .thinking-block,
[data-theme="dark"] .tool-block {
  background: var(--bg-tertiary);
  border-color: var(--border);
}

[data-theme="dark"] .content-block {
  background: var(--surface);
  border-color: var(--border);
}
</style>