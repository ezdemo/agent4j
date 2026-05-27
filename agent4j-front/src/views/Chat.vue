<template>
  <div class="chat-view">
    <div class="chat-header">
      <h2>对话终端</h2>
      <div class="chat-controls">
        <button class="terminal-button" @click="clearChat">清空对话</button>
        <button class="terminal-button" @click="exportChat">导出对话</button>
        <span class="message-count">{{ messages.length }} 条消息</span>
      </div>
    </div>
    
    <div class="chat-messages" ref="messagesContainer">
      <div v-for="message in messages" :key="message.id" :class="['message', message.role]">
        <div class="message-header">
          <span class="role">{{ message.role === 'user' ? '用户' : '助手' }}</span>
          <span class="time">{{ message.time }}</span>
          <span v-if="message.toolCalls" class="tool-badge">工具调用</span>
        </div>
        <div class="message-content">
          <div v-if="message.role === 'assistant' && message.thinking" class="thinking-section">
            <div class="thinking-header" @click="message.showThinking = !message.showThinking">
              <span>思考过程</span>
              <span class="toggle">{{ message.showThinking ? '▼' : '▶' }}</span>
            </div>
            <div v-if="message.showThinking" class="thinking-content">
              {{ message.thinking }}
            </div>
          </div>
          
          <div class="text-content" v-html="formatMessage(message.content)"></div>
          
          <div v-if="message.toolCalls" class="tool-calls">
            <div v-for="(call, index) in message.toolCalls" :key="index" class="tool-call">
              <div class="tool-header">
                <span class="tool-name">{{ call.name }}</span>
                <span class="tool-status" :class="call.status">{{ call.status }}</span>
              </div>
              <div v-if="call.arguments" class="tool-args">
                <pre>{{ JSON.stringify(call.arguments, null, 2) }}</pre>
              </div>
              <div v-if="call.result" class="tool-result">
                <div class="result-header">结果:</div>
                <pre>{{ call.result }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <div v-if="isTyping" class="message assistant typing">
        <div class="message-header">
          <span class="role">助手</span>
          <span class="typing-indicator">正在输入...</span>
        </div>
        <div class="message-content">
          <div class="typing-dots">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>
    </div>
    
    <div class="chat-input">
      <div class="input-container">
        <textarea 
          v-model="inputText" 
          @keydown.enter.exact="sendMessage"
          @keydown.shift.enter="inputText += '\n'"
          placeholder="输入消息... (Enter发送, Shift+Enter换行)"
          rows="3"
          ref="inputField"
        ></textarea>
        <div class="input-controls">
          <div class="left-controls">
            <label class="file-upload">
              <input type="file" @change="handleFileUpload" multiple />
              <span class="terminal-button">附件</span>
            </label>
            <button class="terminal-button" @click="togglePlanMode" :class="{ active: planMode }">
              {{ planMode ? '计划模式 ON' : '计划模式 OFF' }}
            </button>
          </div>
          <div class="right-controls">
            <button class="terminal-button send-button" @click="sendMessage" :disabled="!inputText.trim()">
              发送 [Enter]
            </button>
          </div>
        </div>
      </div>
      
      <div v-if="attachedFiles.length" class="attached-files">
        <div v-for="(file, index) in attachedFiles" :key="index" class="file-item">
          <span class="file-name">{{ file.name }}</span>
          <span class="file-size">({{ formatFileSize(file.size) }})</span>
          <button class="remove-file" @click="removeFile(index)">×</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, onBeforeUnmount } from 'vue'
import { chatAPI, agentAPI, sessionsAPI } from '../services/api'

const messagesContainer = ref(null)
const inputField = ref(null)
const inputText = ref('')
const messages = ref([])
const attachedFiles = ref([])
const isTyping = ref(false)
const planMode = ref(false)
const streaming = ref(false)
const currentEventSource = ref(null)

const formatMessage = (content) => {
  if (!content) return ''
  
  // 简单的 Markdown 转 HTML
  let formatted = content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`(.*?)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
    .replace(/^- (.*)/gm, '• $1')
    .replace(/^(\d+)\. (.*)/gm, '$1. $2')
  
  return formatted
}

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text) return
  
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
  
  await scrollToBottom()
  
  // 发送消息到后端
  isTyping.value = true
  streaming.value = true
  
  // 创建助手消息占位符（使用 reactive 保证 Vue 追踪更新）
  const msgIndex = messages.value.length
  messages.value.push({
    id: Date.now() + 1,
    role: 'assistant',
    content: '',
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' }),
    thinking: '',
    showThinking: false,
    toolCalls: []
  })
  
  // ★ 关键：通过 messages.value[index] 访问，确保操作的是 Vue reactive proxy
  const getMsg = () => messages.value[msgIndex]
  
  try {
    // 使用POST SSE流式聊天
    const streamHandle = chatAPI.sendMessageStream(
      text,
      // onMessage — data.type 来自 SSE event: 字段
      (data) => {
        const msg = getMsg()
        if (!msg) return
        
        if (data.type === 'reasoning') {
          msg.thinking += (data.content || '')
          msg.showThinking = true  // 有思考内容时自动展开
        } else if (data.type === 'content') {
          msg.content += (data.content || '')
        } else if (data.type === 'tool_call') {
          let name = data.name || ''
          let args = data.args || data.arguments || ''
          if (typeof args === 'string') {
            try { args = JSON.parse(args) } catch (e) { /* 保持原值 */ }
          }
          // 兜底：如果 name 为空，从 content 原始 JSON 提取
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
          msg.toolCalls.push({
            name: name || 'unknown',
            status: '执行中',
            arguments: args,
            result: ''
          })
        } else if (data.type === 'tool_result') {
          if (msg.toolCalls.length > 0) {
            const lastTool = msg.toolCalls[msg.toolCalls.length - 1]
            let result = data.result || data.content || ''
            if (typeof result === 'string' && result.startsWith('"') && result.endsWith('"')) {
              try { result = JSON.parse(result) } catch (e) { /* 保持原值 */ }
            }
            lastTool.result = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
            lastTool.status = '成功'
          }
        } else if (data.type === 'error') {
          msg.content = `错误: ${data.error || data.content || '未知错误'}`
        } else if (data.type === 'usage') {
          msg.usage = data
        } else if (data.type === 'reply') {
          // 最终回复：如果 content 为空则用 reply 内容填充
          const replyContent = data.content || ''
          if (!msg.content && replyContent) {
            msg.content = replyContent
          }
        }
        // done 事件不做特殊处理
        scrollToBottom()
      },
      // onDone
      () => {
        streaming.value = false
        isTyping.value = false
      },
      // onError
      (error) => {
        console.error('SSE连接错误:', error)
        streaming.value = false
        isTyping.value = false
        if (!assistantMessage.content) {
          assistantMessage.content = '连接错误，请检查后端服务是否正常运行。'
        }
      }
    )
    currentEventSource.value = streamHandle
  } catch (error) {
    console.error('发送消息失败:', error)
    isTyping.value = false
    streaming.value = false
    
    // 回退到同步聊天
    try {
      const response = await chatAPI.sendMessage(text)
      if (response.success) {
        assistantMessage.content = response.data.reply
        assistantMessage.thinking = '同步聊天模式'
      } else {
        assistantMessage.content = `错误: ${response.error}`
      }
    } catch (syncError) {
      assistantMessage.content = `发送失败: ${syncError.message}`
    }
  }
  
  await scrollToBottom()
}

const clearChat = async () => {
  if (confirm('确定要清空所有对话记录吗？')) {
    try {
      await sessionsAPI.createNew()
      messages.value = []
    } catch (error) {
      console.error('创建新会话失败:', error)
    }
  }
}

const exportChat = () => {
  const chatText = messages.value.map(msg => {
    const header = `[${msg.time}] ${msg.role === 'user' ? '用户' : '助手'}:`
    let content = header + '\n' + msg.content
    if (msg.thinking) {
      content += '\n\n思考过程: ' + msg.thinking
    }
    if (msg.toolCalls && msg.toolCalls.length > 0) {
      content += '\n\n工具调用:'
      msg.toolCalls.forEach(tool => {
        content += `\n  - ${tool.name}: ${JSON.stringify(tool.arguments)}`
        if (tool.result) {
          content += `\n    结果: ${tool.result}`
        }
      })
    }
    return content + '\n'
  }).join('\n')
  
  const blob = new Blob([chatText], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `agent4j-chat-${new Date().toISOString().slice(0, 10)}.txt`
  a.click()
  URL.revokeObjectURL(url)
}

const handleFileUpload = (event) => {
  const files = Array.from(event.target.files)
  attachedFiles.value.push(...files)
}

const removeFile = (index) => {
  attachedFiles.value.splice(index, 1)
}

const togglePlanMode = async () => {
  try {
    if (planMode.value) {
      await agentAPI.disablePlanMode()
      planMode.value = false
    } else {
      await agentAPI.enablePlanMode()
      planMode.value = true
    }
    
    window.dispatchEvent(new CustomEvent('terminal-output', { 
      detail: { 
        type: 'system', 
        text: planMode.value ? '已启用计划模式 - 只读工具可用' : '已禁用计划模式'
      }
    }))
  } catch (error) {
    console.error('切换计划模式失败:', error)
  }
}

const loadHistory = async () => {
  try {
    const response = await agentAPI.getHistory()
    if (response.success && response.data) {
      // 后端返回的是 JSONL 原始格式，需要转换为前端消息格式
      // 消息结构：user / assistant(含 reasoning_content + tool_calls) / tool(工具结果)
      const raw = response.data

      // 先收集所有 tool_call_id → tool result 的映射
      const toolResults = {}
      for (const msg of raw) {
        if (msg.role === 'tool' && msg.tool_call_id) {
          toolResults[msg.tool_call_id] = msg.content || ''
        }
      }

      // 合并 assistant + tool 消息
      const merged = []
      for (const msg of raw) {
        if (msg.role === 'tool') continue // 已合并到 assistant，跳过

        const item = {
          id: Date.now() + merged.length,
          role: msg.role,
          content: msg.content || '',
          time: new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' }),
          thinking: msg.reasoning_content || '',
          showThinking: false,
          toolCalls: []
        }

        // 解析 tool_calls（后端格式：OpenAI function calling 结构）
        if (msg.tool_calls && Array.isArray(msg.tool_calls)) {
          item.toolCalls = msg.tool_calls.map(tc => {
            let name = ''
            let args = tc.function?.arguments || tc.arguments || ''
            if (tc.function) {
              name = tc.function.name || ''
            } else {
              name = tc.name || ''
            }
            // arguments 是 JSON 字符串，需要解析
            if (typeof args === 'string') {
              try { args = JSON.parse(args) } catch (e) { /* 保持原字符串 */ }
            }
            // 从 toolResults 中查找对应结果
            const result = toolResults[tc.id] || ''
            return {
              name,
              status: result ? '成功' : '执行中',
              arguments: args,
              result
            }
          })
        }

        merged.push(item)
      }

      messages.value = merged
    }
  } catch (error) {
    console.error('加载历史消息失败:', error)
  }
}

// 监听清空事件
window.addEventListener('terminal-clear', clearChat)

// 加载当前会话状态
const loadSessionState = async () => {
  try {
    const statusResponse = await agentAPI.getStatus()
    if (statusResponse.success && statusResponse.data) {
      planMode.value = statusResponse.data.planMode || false
    }
    
    const currentSession = await sessionsAPI.getCurrent()
    if (currentSession.success && currentSession.data) {
      // 可以在这里显示当前会话信息
    }
  } catch (error) {
    console.error('加载会话状态失败:', error)
  }
}

onMounted(() => {
  loadSessionState()
  loadHistory()
  scrollToBottom()
})

onBeforeUnmount(() => {
  // 关闭SSE连接
  if (currentEventSource.value) {
    if (typeof currentEventSource.value.abort === 'function') {
      currentEventSource.value.abort()
    } else {
      currentEventSource.value.close()
    }
  }
  
  // 移除事件监听器
  window.removeEventListener('terminal-clear', clearChat)
})

watch(messages, () => {
  scrollToBottom()
}, { deep: true })
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  margin-bottom: var(--spacing-md);
}

.chat-header h2 {
  color: var(--terminal-amber);
  font-size: var(--font-size-lg);
}

.chat-controls {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.message-count {
  color: var(--terminal-gray);
  font-size: var(--font-size-sm);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  margin-bottom: var(--spacing-md);
}

.message {
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-md);
  border-left: 3px solid var(--terminal-green);
}

.message.user {
  border-left-color: var(--terminal-blue);
}

.message.assistant {
  border-left-color: var(--terminal-green);
}

.message-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.role {
  font-weight: bold;
  color: var(--terminal-amber);
}

.time {
  color: var(--terminal-gray);
  font-size: var(--font-size-sm);
}

.tool-badge {
  background: rgba(51, 255, 51, 0.1);
  color: var(--terminal-green);
  padding: var(--spacing-xs) var(--spacing-sm);
  font-size: var(--font-size-xs);
  border: 1px solid var(--terminal-green);
}

.message-content {
  line-height: 1.6;
}

.thinking-section {
  margin-bottom: var(--spacing-md);
  background: var(--bg-tertiary);
  padding: var(--spacing-sm);
  border-radius: 4px;
}

.thinking-header {
  display: flex;
  justify-content: space-between;
  cursor: pointer;
  color: var(--terminal-gray);
  font-size: var(--font-size-sm);
}

.thinking-content {
  margin-top: var(--spacing-sm);
  color: var(--terminal-gray);
  font-style: italic;
  white-space: pre-wrap;
  font-size: var(--font-size-sm);
}

.text-content {
  white-space: pre-wrap;
}

.tool-calls {
  margin-top: var(--spacing-md);
  border-top: 1px solid var(--border-color);
  padding-top: var(--spacing-md);
}

.tool-call {
  background: var(--bg-tertiary);
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
  border-radius: 4px;
}

.tool-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--spacing-sm);
}

.tool-name {
  color: var(--terminal-cyan);
  font-weight: bold;
}

.tool-status {
  font-size: var(--font-size-sm);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: 4px;
}

.tool-status.执行中 {
  background: rgba(255, 176, 0, 0.1);
  color: var(--terminal-amber);
}

.tool-status.成功 {
  background: rgba(51, 255, 51, 0.1);
  color: var(--terminal-green);
}

.tool-status.失败 {
  background: rgba(255, 51, 51, 0.1);
  color: var(--terminal-red);
}

.tool-args, .tool-result {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
}

.tool-args pre, .tool-result pre {
  background: var(--bg-primary);
  padding: var(--spacing-sm);
  border-radius: 4px;
  overflow-x: auto;
}

.result-header {
  color: var(--terminal-amber);
  margin-bottom: var(--spacing-xs);
}

.typing-indicator {
  color: var(--terminal-gray);
  font-style: italic;
}

.typing-dots {
  display: flex;
  gap: 4px;
}

.typing-dots span {
  width: 8px;
  height: 8px;
  background: var(--terminal-green);
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out;
}

.typing-dots span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-dots span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}

.chat-input {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  padding: var(--spacing-md);
}

.input-container textarea {
  width: 100%;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  color: var(--terminal-green);
  font-family: var(--font-mono);
  padding: var(--spacing-md);
  resize: none;
  font-size: var(--font-size-base);
  line-height: 1.5;
}

.input-container textarea:focus {
  outline: none;
  border-color: var(--terminal-green);
}

.input-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--spacing-md);
}

.left-controls, .right-controls {
  display: flex;
  gap: var(--spacing-sm);
}

.file-upload input {
  display: none;
}

.send-button {
  background: var(--terminal-green);
  color: var(--bg-primary);
  font-weight: bold;
}

.send-button:hover {
  background: var(--terminal-green-dark);
  box-shadow: 0 0 15px var(--terminal-green);
}

.send-button:disabled {
  background: var(--border-color);
  color: var(--terminal-gray);
  cursor: not-allowed;
  box-shadow: none;
}

.attached-files {
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--border-color);
}

.file-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  background: var(--bg-tertiary);
  margin-bottom: var(--spacing-sm);
}

.file-name {
  color: var(--terminal-cyan);
}

.file-size {
  color: var(--terminal-gray);
  font-size: var(--font-size-sm);
}

.remove-file {
  background: none;
  border: none;
  color: var(--terminal-red);
  cursor: pointer;
  font-size: var(--font-size-lg);
  margin-left: auto;
}

.remove-file:hover {
  color: var(--terminal-red);
  text-shadow: 0 0 5px var(--terminal-red);
}

@media (max-width: 768px) {
  .chat-header {
    flex-direction: column;
    gap: var(--spacing-sm);
    align-items: flex-start;
  }
  
  .input-controls {
    flex-direction: column;
    gap: var(--spacing-md);
  }
  
  .left-controls, .right-controls {
    width: 100%;
    justify-content: center;
  }
  
  .send-button {
    width: 100%;
  }
}
</style>