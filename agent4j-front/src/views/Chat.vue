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
import { ref, onMounted, nextTick, watch } from 'vue'

const messagesContainer = ref(null)
const inputField = ref(null)
const inputText = ref('')
const messages = ref([])
const attachedFiles = ref([])
const isTyping = ref(false)
const planMode = ref(false)

// 模拟消息
const mockMessages = [
  {
    id: 1,
    role: 'user',
    content: '你好，Agent4J！',
    time: '09:30'
  },
  {
    id: 2,
    role: 'assistant',
    content: '你好！我是 Agent4J，你的 AI 代码助手。我可以帮你：\n\n1. **编写和修改代码** - 使用 edit_file、multi_edit 等工具\n2. **分析代码结构** - 使用 get_symbols、find_in_code 等工具\n3. **执行命令** - 使用 run_command 工具\n4. **搜索文件** - 使用 glob、grep 等工具\n\n有什么我可以帮你的吗？',
    time: '09:30',
    thinking: '用户发送了问候消息。我需要友好地回应，并介绍我的功能。作为 Agent4J，我是一个 Java AI 代理框架，专注于代码编辑和开发辅助。',
    showThinking: false
  },
  {
    id: 3,
    role: 'user',
    content: '请帮我查看当前项目的目录结构',
    time: '09:31'
  },
  {
    id: 4,
    role: 'assistant',
    content: '好的，我来查看项目目录结构。',
    time: '09:31',
    toolCalls: [
      {
        name: 'tree',
        status: '执行中',
        arguments: { maxDepth: 3 },
        result: 'agent4j/\n├── pom.xml\n├── agent4j-tool/\n│   ├── pom.xml\n│   └── src/main/java/site/sorghum/agent4j/tool/\n├── agent4j-bin/\n│   ├── pom.xml\n│   ├── src/main/resources/app.yml\n│   └── src/main/java/site/sorghum/agent4j/bin/\n├── agent4j-web/\n│   └── pom.xml\n└── agent4j-front/\n    └── package.json'
      }
    ]
  }
]

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
  
  // 模拟助手回复
  isTyping.value = true
  setTimeout(async () => {
    const assistantMessage = {
      id: Date.now() + 1,
      role: 'assistant',
      content: `收到你的消息："${text}"。\n\n我正在处理你的请求...`,
      time: new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' }),
      thinking: `用户发送了消息：${text}。我需要分析这个消息并给出合适的回复。`,
      showThinking: false
    }
    
    messages.value.push(assistantMessage)
    isTyping.value = false
    await scrollToBottom()
  }, 1500)
}

const clearChat = () => {
  if (confirm('确定要清空所有对话记录吗？')) {
    messages.value = []
  }
}

const exportChat = () => {
  const chatText = messages.value.map(msg => {
    const header = `[${msg.time}] ${msg.role === 'user' ? '用户' : '助手'}:`
    return `${header}\n${msg.content}\n`
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

const togglePlanMode = () => {
  planMode.value = !planMode.value
  window.dispatchEvent(new CustomEvent('terminal-output', { 
    detail: { 
      type: 'system', 
      text: planMode.value ? '已启用计划模式 - 只读工具可用' : '已禁用计划模式'
    }
  }))
}

// 监听清空事件
window.addEventListener('terminal-clear', () => {
  messages.value = []
})

onMounted(() => {
  // 加载初始消息
  messages.value = [...mockMessages]
  scrollToBottom()
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