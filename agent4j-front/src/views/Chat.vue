<template>
  <div class="chat">
    <!-- 可选头部 -->
    <div v-if="!hideHeader" class="chat-head">
      <span class="chat-head-title">对话</span>
      <span class="chat-head-count">{{ messages.length }} 条</span>
      <div style="flex:1"></div>
      <button class="btn btn-ghost btn-sm" @click="clearChat">清空</button>
      <button class="btn btn-ghost btn-sm" @click="exportChat">导出</button>
      <button :disabled="loadingPrompt" class="btn btn-ghost btn-sm" @click="viewSystemPrompt">提示词</button>
    </div>

    <!-- 悬浮日志通知（全局，不受消息滚动影响） -->
    <div class="log-stack">
      <TransitionGroup name="log-bar">
        <div v-for="log in currentLogs" :key="log.id" :class="'log-' + (log.level || 'info').toLowerCase()"
             class="log-bar"
             @click="currentLogs = currentLogs.filter(l => l.id !== log.id)">
          <span class="log-bar-icon">📋</span>
          <span class="log-bar-text">{{ log.text }}</span>
          <span class="log-bar-time">{{ formatTime(log.time) }}</span>
        </div>
      </TransitionGroup>
    </div>

    <!-- 消息区 -->
    <div ref="messagesContainer" class="messages">
      <!-- 空状态：无会话 -->
      <div v-if="!props.sessionName" class="empty">
        <div class="empty-icon">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z"/>
          </svg>
        </div>
        <p class="empty-title">未选择会话</p>
        <p class="empty-desc">请从左侧选择一个已有会话，或点击「新建对话」开始新会话</p>
      </div>

      <!-- 空状态：有会话但无消息 -->
      <div v-else-if="messages.length === 0" class="empty">
        <div class="empty-icon">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
          </svg>
        </div>
        <p class="empty-title">开始对话</p>
        <p class="empty-desc">输入问题或指令，Agent4j 将为您提供帮助</p>
        <div class="empty-suggestions">
          <button v-for="s in suggestions" :key="s" class="suggestion" @click="inputText = s">{{ s }}</button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, idx) in messages" :key="msg.id" class="msg" :class="msg.role" :data-msg-idx="idx">
        <!-- 用户消息 -->
        <template v-if="msg.role === 'user'">
          <div class="msg-body user-body">
            <div class="msg-text">{{ msg.content }}</div>
            <div v-if="msg.images && msg.images.length > 0" class="user-images">
              <img v-for="(img, i) in msg.images" :key="i" :src="img" class="user-image" @click="previewImage(img)"/>
            </div>
            <div class="msg-footer">
              <span class="msg-time">{{ msg.time }}</span>
              <button v-if="msg.snapshotId" class="rollback-btn"
                      :class="{ loading: snapshotRollbackLoading.get(msg.snapshotId) }"
                      @click="rollbackSnapshot(msg.snapshotId)"
                      title="撤回 AI 修改，恢复到发送前状态"
                      v-html="ROLLBACK_ICON"></button>
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
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="10"/>
                      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                      <line x1="12" y1="17" x2="12.01" y2="17"/>
                    </svg>
                    <span>思考</span>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                         :style="{ transform: block.showContent ? 'rotate(180deg)' : '' }">
                      <polyline points="6 9 12 15 18 9"/>
                    </svg>
                  </div>
                  <div v-if="block.showContent" class="reasoning-text" v-html="fmt(block.content)"></div>
                </div>

                <!-- 内容 -->
                <div v-else-if="block.type === 'content'" class="block-content" v-html="fmt(block.content)"></div>

                <!-- 工具调用 -->
                <div v-else-if="block.type === 'tool_call'" class="block-tool">
                  <div class="tool-head" @click="block.expanded = !block.expanded">
                    <span class="tool-icon" :class="block.status">
                      <svg v-if="block.status === '执行中'" width="12" height="12" viewBox="0 0 24 24" fill="none"
                           stroke="currentColor" stroke-width="2" class="animate-spin"><path
                          d="M21 12a9 9 0 11-6.219-8.56"/></svg>
                      <svg v-else-if="block.status === '成功'" width="12" height="12" viewBox="0 0 24 24" fill="none"
                           stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                      <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                           stroke-width="2"><circle cx="12" cy="12" r="10"/></svg>
                    </span>
                    <code class="tool-name">{{ block.name }}</code>
                    <span class="tool-status" :class="block.status">{{ block.status }}</span>
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                         :style="{ transform: block.expanded ? 'rotate(180deg)' : '' }">
                      <polyline points="6 9 12 15 18 9"/>
                    </svg>
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

    <!-- 消息缩略图 dock（右侧 dock 栏，仅用户消息） -->
    <div v-if="userMessages.length > 0" class="msg-thumb-dock">
      <div class="thumb-dock-inner">
        <div
          v-for="(um, ui) in userMessages"
          :key="um.id"
          class="thumb-item"
          @click="jumpToMessage(um.globalIdx)"
        >
          <span class="thumb-indicator"></span>
          <span class="thumb-preview">{{ truncateText(um.content, 40) }}</span>
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

    <!-- 子代理浮窗入口按钮 -->
    <button v-if="hasSubAgentOutput" class="sub-float-btn" @click="subAgentModalOpen = true">
      子代理
      <span class="badge">{{ subAgentSessions.length }}</span>
    </button>

    <!-- 子代理 Modal -->
    <Teleport to="body">
      <div v-if="subAgentModalOpen" class="sub-modal-overlay" @click.self="subAgentModalOpen = false">
        <div class="sub-modal">
          <div class="sub-modal-head">
            <h3>子代理输出</h3>
            <button class="sub-modal-close" @click="subAgentModalOpen = false">&times;</button>
          </div>
          <div class="sub-modal-body" ref="subModalBody">
            <template v-if="subAgentSessions.length === 0">
              <div style="text-align:center;color:var(--fg-3);padding:40px 0;">暂无子代理输出</div>
            </template>
            <div class="sub-session">
              <div class="sub-session-body">
                <template v-for="(session, si) in subAgentSessions" :key="session.id">
                  <div v-for="(block, bi) in session.blocks" :key="bi">
                    <div v-if="block.type === 'reasoning'" class="block-reasoning">
                      <div class="reasoning-head" @click="block.showContent = !block.showContent">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2">
                          <circle cx="12" cy="12" r="10"/>
                          <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                          <line x1="12" y1="17" x2="12.01" y2="17"/>
                        </svg>
                        <span>思考</span>
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2" :style="{ transform: block.showContent ? 'rotate(180deg)' : '' }">
                          <polyline points="6 9 12 15 18 9"/>
                        </svg>
                      </div>
                      <div v-if="block.showContent" class="reasoning-text" v-html="fmt(block.content)"></div>
                    </div>
                    <div v-else-if="block.type === 'content'" class="block-content" v-html="fmt(block.content)"></div>
                    <div v-else-if="block.type === 'tool_call'" class="block-tool">
                      <div class="tool-head" @click="block.expanded = !block.expanded">
                        <span class="tool-icon" :class="block.status">
                          <svg v-if="block.status === '执行中'" width="12" height="12" viewBox="0 0 24 24" fill="none"
                               stroke="currentColor" stroke-width="2" class="animate-spin"><path
                              d="M21 12a9 9 0 11-6.219-8.56"/></svg>
                          <svg v-else-if="block.status === '成功'" width="12" height="12" viewBox="0 0 24 24"
                               fill="none" stroke="currentColor" stroke-width="2"><polyline
                              points="20 6 9 17 4 12"/></svg>
                          <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                               stroke-width="2"><circle cx="12" cy="12" r="10"/></svg>
                        </span>
                        <code class="tool-name">{{ block.name }}</code>
                        <span class="tool-status" :class="block.status">{{ block.status }}</span>
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                             stroke-width="2" :style="{ transform: block.expanded ? 'rotate(180deg)' : '' }">
                          <polyline points="6 9 12 15 18 9"/>
                        </svg>
                      </div>
                      <div v-if="block.expanded" class="tool-detail">
                        <pre v-if="block.args"><code>{{ fmtArgs(block.args) }}</code></pre>
                        <pre v-if="block.result"><code>{{ block.result }}</code></pre>
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 系统提示词 Modal -->
    <Teleport to="body">
      <div v-if="promptModalOpen" class="prompt-modal-overlay" @click.self="promptModalOpen = false">
        <div class="prompt-modal">
          <div class="prompt-modal-head">
            <h3>系统提示词</h3>
            <span class="prompt-modal-size">{{ promptLength }} 字符</span>
            <div style="flex:1"></div>
            <button class="prompt-modal-close" @click="promptModalOpen = false">&times;</button>
          </div>
          <div class="prompt-modal-body">
            <div class="prompt-modal-content" v-html="fmtPrompt(promptContent)"></div>
          </div>
          <div class="prompt-modal-foot">
            <button class="btn btn-sm" @click="copyPrompt">复制</button>
            <button class="btn btn-sm" @click="promptModalOpen = false">关闭</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 图片预览弹窗 -->
    <Teleport to="body">
      <div v-if="imagePreviewOpen" class="image-preview-overlay" @click="imagePreviewOpen = false">
        <img :src="imagePreviewUrl" class="image-preview-full" @click.stop/>
        <button class="image-preview-close" @click="imagePreviewOpen = false">&times;</button>
      </div>
    </Teleport>

    <!-- 无会话时：禁用输入框占位 -->
    <div v-if="!props.sessionName" class="no-session-input-bar">
      <div class="no-session-input-placeholder">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z"/>
        </svg>
        <span>请先选择或新建一个会话</span>
      </div>
    </div>

    <!-- 输入区（独立组件） -->
    <ChatInput v-else
        v-model:inputText="inputText"
        :streaming="streaming"
        :todos="todos"
        :usage="usage"
        :currentModel="currentModel"
        :availableModels="availableModels"
        :workspaceHash="props.workspaceHash"
        :sessionName="props.sessionName"
        :hasHistory="hasHistory"
        @send="(imgs) => sendMessage(imgs)"
        @abort="abortChat"
        @clear="clearChat"
        @export="exportChat"
        @fetchTodos="fetchTodos"
        @refreshUsage="loadUsage"
        @switchModel="handleSwitchModel"
        @refreshModels="loadUsage"
        @continue="continueChat"
    />
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {agentAPI, chatAPI, configAPI, snapshotAPI} from '../services/api'
import {md} from '../utils/highlight'
import ChatInput from '../components/ChatInput.vue'
import {useAppStore} from '../stores/app'

// SVG 图标（模板使用）
const COPY_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>'
const ROLLBACK_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>'

// ============= 模型切换 =============
const handleSwitchModel = async (modelName) => {
  if (modelName === currentModel.value) return
  try {
    const r = await configAPI.updateConfig({model: modelName})
    if (r.success) {
      currentModel.value = modelName
      availableModels.value.forEach(m => {
        m.active = m.name === modelName
      })
    }
  } catch (e) {
    console.error('切换模型失败:', e)
  }
}

const props = defineProps({
  hideHeader: {type: Boolean, default: false},
  workspaceHash: {type: String, default: null},
  sessionName: {type: String, default: null}
})

const emit = defineEmits(['sessionUpdated'])
const store = useAppStore()

const messagesContainer = ref(null)
const inputText = ref('')

// 快照检查点：msgId -> snapshotId 映射（用于消息关联和撤回按钮显示）
const snapshotMap = ref(new Map())
const snapshotRollbackLoading = ref(new Map()) // msgId -> 是否正在撤回

// 图片预览
const imagePreviewUrl = ref('')
const imagePreviewOpen = ref(false)
const previewImage = (url) => {
  imagePreviewUrl.value = url
  imagePreviewOpen.value = true
}
const messages = computed(() => store.getSessionMessages(props.sessionName))
const streaming = computed(() => store.getSessionStreaming(props.sessionName))
const hasHistory = computed(() => messages.value.length > 0)
const planMode = ref(false)

// TODO 相关状态
const todos = ref([])

// Usage 相关
const usage = ref({
  promptTokens: 0,
  completionTokens: 0,
  cacheHit: 0,
  cacheMiss: 0,
  maxContextTokens: 128000,
  lastPromptTokens: 0
})
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
      usage.value = {...usage.value, ...usageRes.value.data}
    }
    if (modelsRes.status === 'fulfilled' && modelsRes.value.success) {
      currentModel.value = modelsRes.value.data?.current || ''
      availableModels.value = modelsRes.value.data?.models || []
    }
  } catch {
  }
}

// ==================== 子代理 Modal 状态 ====================
// 收集子代理的 sub_xxx 事件，在独立 Modal 中渲染，不占用主消息流
const subAgentBlocks = ref([])        // 当前正在进行的子代理的 blocks
const subAgentSessions = ref([])      // 已完成的子代理会话列表
const subAgentModalOpen = ref(false)
const subAgentModalTask = ref('')
const subAgentSessionId = ref(0)      // 自增 ID
const hasSubAgentOutput = computed(() => subAgentSessions.value.length > 0)
const subModalBody = ref(null)        // 子代理 Modal 容器 ref，用于自动滚底

// 子代理 Modal 内容变化时始终滚动到底部
const scrollSubModalToBottom = async () => {
  await nextTick()
  await nextTick() // 两层 nextTick 确保 Vue 渲染完成
  const el = subModalBody.value
  if (el) el.scrollTop = el.scrollHeight
}

watch([subAgentSessions, subAgentBlocks], async () => {
  if (subAgentModalOpen.value) {
    await scrollSubModalToBottom()
  }
}, {deep: true})

// 打开 Modal 时滚动到底部（显示最新内容）
watch(subAgentModalOpen, async (open) => {
  if (open) {
    await scrollSubModalToBottom()
  }
})

// 切换会话/清空时重置子代理状态
const resetSubAgentState = () => {
  subAgentBlocks.value = []
  subAgentSessions.value = []
  subAgentSessionId.value = 0
  subAgentModalOpen.value = false
}

// 日志通知列表（逐条堆叠，每条6秒后自动移除）
const currentLogs = ref([])

const addLog = (log) => {
  const id = Date.now() + Math.random()
  currentLogs.value.unshift({...log, id})
  setTimeout(() => {
    currentLogs.value = currentLogs.value.filter(l => l.id !== id)
  }, 6000)
}

const formatTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleTimeString('zh-CN', {hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit'})
}

// 获取当前会话的 TODO 列表
const fetchTodos = async () => {
  try {
    const params = {}
    params.sessionName = props.sessionName || 'default'
    if (props.workspaceHash) params.workspaceHash = props.workspaceHash
    const res = await configAPI.getTodos(params)
    if (res.success) {
      todos.value = res.data || []
    }
  } catch (e) {
    todos.value = []
  }
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
  if (props.sessionName) loadUsage()
  // 监听复制成功事件
  window.addEventListener('copy-success', (e) => {
    addLog({level: 'INFO', text: '✅ ' + (e.detail || '已复制'), time: Date.now()})
  })
  // 监听消息容器滚动 + 初始检查
  const el = messagesContainer.value
  if (el) {
    el.addEventListener('scroll', onScroll)
    requestAnimationFrame(() => onScroll())
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('copy-success', () => {
  })
  const el = messagesContainer.value
  if (el) el.removeEventListener('scroll', onScroll)
})

const suggestions = ['解释这段代码', '优化这个函数', '写个单元测试', '检查潜在问题']

// 不在聊天区显示的静默命令（只发给后端，不加用户消息气泡）
const SILENT_CMDS = new Set(['/agree', '/deny', '/exit', '/continue'])

const hasAssistant = computed(() => messages.value.some(m => m.role === 'assistant' && m.blocks?.length > 0))

// ===== 消息缩略图 dock =====
/** 只取 role === 'user' 的消息，并记录全局索引用于跳转 */
const userMessages = computed(() => {
  return messages.value
    .map((m, idx) => ({...m, globalIdx: idx}))
    .filter(m => m.role === 'user')
})

/** 截取文本前 N 个字符 */
const truncateText = (text, max) => {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '…' : text
}

/** 点击缩略图跳转到对应消息 */
const jumpToMessage = (globalIdx) => {
  const el = messagesContainer.value?.querySelector(`[data-msg-idx="${globalIdx}"]`)
  if (el) {
    el.scrollIntoView({behavior: 'smooth', block: 'start'})
  }
}

const now = () => new Date().toLocaleTimeString('zh-CN', {hour12: false, hour: '2-digit', minute: '2-digit'})

// 全局函数：代码复制（被 onclick 引用）
window.copyCode = (btn) => {
  const wrap = btn.closest('.code-block-wrap')
  const code = wrap?.querySelector('code')?.textContent || ''
  navigator.clipboard.writeText(code).then(() => {
    window.dispatchEvent(new CustomEvent('copy-success', {detail: '代码已复制'}))
  }).catch(() => {
  })
}

// 使用共享 marked 实例（语法高亮 + 复制按钮已内置）
const fmt = c => {
  if (!c) return ''
  return md.parse(c)
}

const fmtPrompt = c => {
  if (!c) return ''
  return md.parse(c)
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
    window.dispatchEvent(new CustomEvent('copy-success', {detail: '消息已复制'}))
  }).catch(() => {
  })
}

const fmtArgs = a => {
  if (typeof a === 'string') {
    try {
      return JSON.stringify(JSON.parse(a), null, 2)
    } catch {
      return a
    }
  }
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
    el.scrollTo({top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto'})
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
const sendMessage = async (images = []) => {
  const text = inputText.value.trim()
  if ((!text || streaming.value) && images.length === 0) return
  const sessionName = props.sessionName
  if (!sessionName) return

  const firstWord = text.split(/\s+/)[0].toLowerCase()
  // 静默命令不显示用户气泡（系统命令、模式切换、HITL 审批等）
  const isSilent = SILENT_CMDS.has(firstWord)

  // 每条新消息开始时重置子代理状态
  resetSubAgentState()

  // 静默命令不显示用户气泡
  if (!isSilent) {
    const userMsg = {id: Date.now(), role: 'user', content: text, time: now(), snapshotId: null}
    if (images.length > 0) userMsg.images = images
    store.addSessionMessage(sessionName, userMsg)
  }
  userScrolledAway = false
  inputText.value = ''
  await scroll(true)  // 用户刚发送，强制滚到底

  store.setSessionStreaming(sessionName, true)

  // 使用唯一 ID 追踪当前 assistant 消息
  const assistantId = Date.now() + 1
  let silentAssistantId = null

  // 静默命令不预创建助手占位
  if (!isSilent) {
    store.addSessionMessage(sessionName, {id: assistantId, role: 'assistant', time: now(), blocks: []})
  }

  let getMsg = () => {
    const msgs = store.getSessionMessages(sessionName)
    const targetId = silentAssistantId || assistantId
    if (!targetId) return null
    return msgs.find(m => m.id === targetId)
  }
  let silentBubbleCreated = false

  try {
    const streamResult = chatAPI.sendMessageStream(text,
        data => {
          // 静默命令：首次收到有内容的数据时才创建助手气泡（只创建一次）
          if (isSilent && !silentBubbleCreated) {
            if (!data.type || data.type === 'done') return
            const hasContent = (data.type === 'content' && data.content?.trim()) ||
                (data.type === 'reasoning' && data.content?.trim()) ||
                data.type === 'tool_call' || data.type === 'tool_result' || data.type === 'error'
            if (!hasContent) return
            // 有实际内容了，插入助手气泡
            silentAssistantId = Date.now()
            store.addSessionMessage(sessionName, {id: silentAssistantId, role: 'assistant', time: now(), blocks: []})
            silentBubbleCreated = true
          }

          const msg = getMsg()
          if (!msg) return
          // ===== 子代理事件（独立通道，不占用主消息流） =====
          if (data.type === 'sub_content') {
            const lb = subAgentBlocks.value[subAgentBlocks.value.length - 1]
            if (lb?.type === 'content') lb.content += (data.content || '')
            else subAgentBlocks.value.push({type: 'content', content: data.content || ''})
          } else if (data.type === 'sub_reasoning') {
            const lb = subAgentBlocks.value[subAgentBlocks.value.length - 1]
            if (lb?.type === 'reasoning') lb.content += (data.content || '')
            else subAgentBlocks.value.push({type: 'reasoning', content: data.content || '', showContent: false})
          } else if (data.type === 'sub_tool_call') {
            let name = data.name || '', args = data.args || data.arguments || ''
            if (typeof args === 'string') try {
              args = JSON.parse(args)
            } catch {
            }
            subAgentBlocks.value.push({
              type: 'tool_call',
              name: name || 'unknown',
              status: '执行中',
              args,
              result: '',
              expanded: true
            })
          } else if (data.type === 'sub_tool_result') {
            let result = data.result || data.content || ''
            const rn = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
            let targetName = data.name || ''
            // 优先按 name 匹配（异步执行时完成顺序与调用顺序可能不同）
            let matched = false
            if (targetName) {
              for (let i = subAgentBlocks.value.length - 1; i >= 0; i--) {
                if (subAgentBlocks.value[i].type === 'tool_call' && subAgentBlocks.value[i].name === targetName && !subAgentBlocks.value[i].result) {
                  subAgentBlocks.value[i].result = rn;
                  subAgentBlocks.value[i].status = '成功';
                  subAgentBlocks.value[i].expanded = false;
                  matched = true
                  break
                }
              }
            }
            if (!matched) {
              for (let i = subAgentBlocks.value.length - 1; i >= 0; i--) {
                if (subAgentBlocks.value[i].type === 'tool_call' && !subAgentBlocks.value[i].result) {
                  subAgentBlocks.value[i].result = rn;
                  subAgentBlocks.value[i].status = '成功';
                  subAgentBlocks.value[i].expanded = false;
                  break
                }
              }
            }
          } else if (data.type === 'sub_complete') {
            // 子代理完成 → 将当前 blocks 归档为一个会话
            if (subAgentBlocks.value.length > 0) {
              subAgentSessionId.value++
              const taskName = data?.task || data?.content?.task || '子代理'
              subAgentSessions.value.push({
                id: subAgentSessionId.value,
                taskName: typeof taskName === 'string' ? taskName : '子代理',
                blocks: [...subAgentBlocks.value]
              })
              subAgentBlocks.value = []
            }
          } else if (data.type === 'sub_error') {
            const errText = data.error || data.content || '未知错误'
            subAgentBlocks.value.push({type: 'content', content: '❌ ' + errText})
          } else if (data.type === 'sub_usage' || data.type === 'sub_choice' || data.type === 'sub_log') {
            // 子代理用量/选择/日志暂不处理
            // ===== 普通主代理事件 =====
          } else if (data.type === 'reasoning') {
            const lb = msg.blocks[msg.blocks.length - 1]
            if (lb?.type === 'reasoning') lb.content += (data.content || '')
            else msg.blocks.push({type: 'reasoning', content: data.content || '', showContent: false})
          } else if (data.type === 'content') {
            const lb = msg.blocks[msg.blocks.length - 1]
            if (lb?.type === 'content') lb.content += (data.content || '')
            else msg.blocks.push({type: 'content', content: data.content || ''})
          } else if (data.type === 'tool_call') {
            let name = data.name || '', args = data.args || data.arguments || ''
            if (typeof args === 'string') try {
              args = JSON.parse(args)
            } catch {
            }
            msg.blocks.push({
              type: 'tool_call',
              name: name || 'unknown',
              status: '执行中',
              args,
              result: '',
              expanded: true
            })
          } else if (data.type === 'tool_result') {
            let result = data.result || data.content || ''
            const rn = typeof result === 'string' ? result : JSON.stringify(result, null, 2)
            let targetName = data.name || ''
            // 优先按 name 匹配（异步执行时完成顺序与调用顺序可能不同）
            let matched = false
            if (targetName) {
              for (let i = msg.blocks.length - 1; i >= 0; i--) {
                if (msg.blocks[i].type === 'tool_call' && msg.blocks[i].name === targetName && !msg.blocks[i].result) {
                  msg.blocks[i].result = rn;
                  msg.blocks[i].status = '成功';
                  msg.blocks[i].expanded = false;
                  matched = true
                  break
                }
              }
            }
            // 按 name 没匹配到，fallback 到从后往前找第一个无结果的
            if (!matched) {
              for (let i = msg.blocks.length - 1; i >= 0; i--) {
                if (msg.blocks[i].type === 'tool_call' && !msg.blocks[i].result) {
                  msg.blocks[i].result = rn;
                  msg.blocks[i].status = '成功';
                  msg.blocks[i].expanded = false;
                  break
                }
              }
            }
          } else if (data.type === 'error') {
            msg.blocks.push({type: 'content', content: '错误: ' + (data.error || data.content || '未知')})
          } else if (data.type === 'usage') {
            // 更新 usage 数据
            if (data.promptTokens !== undefined) {
              usage.value = {...usage.value, ...data}
            }
          } else if (data.type === 'choice') {
            // 选项按钮（如 HITL 审批）
            let options = data.options || []
            if (typeof options === 'string') {
              try {
                options = JSON.parse(options)
              } catch {
              }
            }
            if (Array.isArray(options) && options.length > 0) {
              msg.blocks.push({type: 'choice', options})
            }
          } else if (data.type === 'log') {
            // 系统日志（如 [compact] 折叠结果）→ 仅展示 INFO 及以上级别
            const level = (data.level || 'INFO').toUpperCase()
            if (level === 'DEBUG') return
            const text = data.message || data.content || ''
            addLog({level, text, time: Date.now()})
          } else if (data.type === 'snapshot') {
            // 快照检查点事件：记录快照 ID，关联到当前用户消息，用于后续撤回
            if (data.msgId) {
              store.addSnapshot(sessionName, data.msgId, data.msgId)
              snapshotMap.value.set(data.msgId, data.msgId)
              // 将快照 ID 关联到最后一条用户消息
              const msgs = store.getSessionMessages(sessionName)
              for (let i = msgs.length - 1; i >= 0; i--) {
                if (msgs[i].role === 'user' && !msgs[i].snapshotId) {
                  msgs[i].snapshotId = data.msgId
                  break
                }
              }
            }
          }
          scroll()
        },
        () => {
          store.setSessionStreaming(sessionName, false)
          // 流结束后清理空的助手气泡
          const msgs = store.getSessionMessages(sessionName)
          const last = msgs[msgs.length - 1]
          if (last?.role === 'assistant' && (!last.blocks || last.blocks.length === 0)) {
            store.setSessionMessages(sessionName, msgs.slice(0, -1))
          }
          // 刷新 usage 数据
          loadUsage()
          // 通知父组件刷新会话列表（标题可能已更新）
          emit('sessionUpdated')
        },
        () => {
          store.setSessionStreaming(sessionName, false)
          const msg = getMsg()
          if (msg && !msg.blocks.length) msg.blocks.push({type: 'content', content: '连接错误'})
        },
        // 传递工作区、会话和图片信息
        {
          workspaceHash: props.workspaceHash,
          sessionName,
          images
        }
    )
    store.setSessionController(sessionName, streamResult)
  } catch {
    store.setSessionStreaming(sessionName, false)
  }
  await scroll()
}

const abortChat = async () => {
  const ctrl = store.getSessionController(props.sessionName)
  if (ctrl) ctrl.abort()
  // 同时通知后端中断
  try {
    await chatAPI.abort()
  } catch {
  }
  store.setSessionStreaming(props.sessionName, false)
}

/** 撤回快照：回滚到 AI 修改前的状态，并截断会话消息 + 回填输入框 */
const rollbackSnapshot = async (msgId) => {
  if (!msgId) return
  const loadingKey = msgId
  if (snapshotRollbackLoading.value.get(loadingKey)) return // 防止重复点击
  snapshotRollbackLoading.value.set(loadingKey, true)

  try {
    const res = await snapshotAPI.rollback(props.workspaceHash, msgId, props.sessionName)
    if (res.success) {
      addLog({level: 'INFO', text: `✅ ${res.data?.message || '工作区已恢复'}`, time: Date.now()})
      // 截断该消息之后的所有快照记录
      store.truncateSnapshotsAfter(props.sessionName, msgId)
      // 从 snapshotMap 中移除
      snapshotMap.value.delete(msgId)

      // 找到对应用户消息及其位置，截断会话消息，回填输入框
      const msgs = store.getSessionMessages(props.sessionName)
      let targetIdx = -1
      // 优先使用后端返回的 rollbackUserText（从 JSONL 持久化数据中取得）
      let rollbackContent = res.data?.rollbackUserText || ''
      for (let i = 0; i < msgs.length; i++) {
        if (msgs[i].snapshotId === msgId) {
          targetIdx = i
          // 如果后端没返回文本，从前端消息中取
          if (!rollbackContent) rollbackContent = msgs[i].content || ''
          break
        }
      }
      if (targetIdx >= 0) {
        // 截断：保留目标消息之前的所有消息，删除目标消息及之后的所有消息
        const kept = msgs.slice(0, targetIdx)
        store.setSessionMessages(props.sessionName, kept)
        // 回填输入框
        if (rollbackContent) {
          inputText.value = rollbackContent
        }
      }

      // 通知父组件刷新会话列表和 Git 状态
      emit('sessionUpdated')
    } else {
      addLog({level: 'ERROR', text: `❌ 撤回失败: ${res.error || '未知错误'}`, time: Date.now()})
    }
  } catch (e) {
    addLog({level: 'ERROR', text: `❌ 撤回失败: ${e.message || e}`, time: Date.now()})
  } finally {
    snapshotRollbackLoading.value.delete(loadingKey)
  }
}

const clearChat = async () => {
  store.clearSessionMessages(props.sessionName)
  resetSubAgentState()
  // 发送 /new 给后端清空会话
  store.setSessionStreaming(props.sessionName, true)
  try {
    chatAPI.sendMessageStream('/new', () => {
    }, () => {
      store.setSessionStreaming(props.sessionName, false);
      loadUsage()
    }, () => {
      store.setSessionStreaming(props.sessionName, false)
    })
  } catch {
    store.setSessionStreaming(props.sessionName, false)
  }
}

// 暴露给父组件的清空方法（/new 属于 SILENT_CMDS，不显示气泡）
const clearMessages = () => {
  store.clearSessionMessages(props.sessionName)
  resetSubAgentState()
  store.setSessionStreaming(props.sessionName, true)
  try {
    chatAPI.sendMessageStream('/new', () => {
    }, () => {
      store.setSessionStreaming(props.sessionName, false);
      loadUsage()
    }, () => {
      store.setSessionStreaming(props.sessionName, false)
    })
  } catch {
    store.setSessionStreaming(props.sessionName, false)
  }
}

/** 继续生成：发送 /continue 命令让 AI 继续推理，复用以有的 SSE 流式逻辑 */
const continueChat = async () => {
  if (!props.sessionName || streaming.value) return
  inputText.value = '/continue'
  nextTick(() => sendMessage())
}

// 仅清空本地消息，不请求后端（配合 REST API 创建新会话时使用）
const resetLocalMessages = () => {
  store.clearSessionMessages(props.sessionName)
  resetSubAgentState()
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
  const blob = new Blob([text], {type: 'text/plain'})
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `chat-${new Date().toISOString().slice(0, 10)}.txt`
  a.click()
}

// 查看系统提示词
const loadingPrompt = ref(false)
const promptModalOpen = ref(false)
const promptContent = ref('')
const promptLength = ref(0)

const copyPrompt = () => {
  if (!promptContent.value) return
  navigator.clipboard.writeText(promptContent.value).then(() => {
    addLog({level: 'INFO', text: '✅ 提示词已复制', time: Date.now()})
  }).catch(() => {
  })
}

const viewSystemPrompt = async () => {
  loadingPrompt.value = true
  try {
    const params = {}
    if (props.workspaceHash) params.workspaceHash = props.workspaceHash
    if (props.sessionName) params.sessionName = props.sessionName
    const res = await agentAPI.getSystemPrompt(params)
    if (res.success && res.data) {
      promptContent.value = res.data.content || ''
      promptLength.value = res.data.length || 0
      promptModalOpen.value = true
    } else {
      addLog({level: 'ERROR', text: '获取提示词失败', time: Date.now()})
    }
  } catch (e) {
    addLog({level: 'ERROR', text: '获取提示词失败: ' + (e.message || '未知错误'), time: Date.now()})
  } finally {
    loadingPrompt.value = false
  }
}

const togglePlan = async () => {
  planMode.value = !planMode.value
  inputText.value = planMode.value ? '/plan' : '/execute'
  await sendMessage()
}

const loadHistory = async (sessionName, force = false) => {
  const targetSession = sessionName || props.sessionName
  if (!targetSession) return
  
  // 如果 force=true 强制从后端刷新，跳过缓存
  const existing = store.getSessionMessages(targetSession)
  if (!force && existing.length > 0) {
    await scroll(true)
    return
  }
  
  try {
    const r = await agentAPI.getHistory(props.workspaceHash, targetSession)
    if (r.success && r.data) {
      const raw = r.data, tr = {}
      for (const m of raw) if (m.role === 'tool' && m.tool_call_id) tr[m.tool_call_id] = m.content || ''
      const merged = []
      for (const m of raw) {
        if (m.role === 'tool') continue
        const item = {id: Date.now() + merged.length, role: m.role, time: now(), blocks: []}
        if (m.role === 'user') {
          // 多模态消息：contentParts 为 [{type:'text',...},{type:'image_url',...}] 数组
          const parts = m.contentParts || (Array.isArray(m.content) ? m.content : null)
          if (parts && parts.length > 0) {
            const texts = []
            const imgs = []
            for (const part of parts) {
              if (part.type === 'text' && part.text) texts.push(part.text)
              if (part.type === 'image_url') {
                const url = part.image_url?.url || part.imageUrl?.url
                if (url) imgs.push(url)
              }
            }
            item.content = texts.join('\n')
            if (imgs.length > 0) item.images = imgs
          } else {
            item.content = m.content || ''
          }
          // 恢复快照检查点 ID（JSONL 持久化的 snapshot_id 字段）
          if (m.snapshot_id) {
            item.snapshotId = m.snapshot_id
          }
        } else {
          if (m.reasoning_content) item.blocks.push({
            type: 'reasoning',
            content: m.reasoning_content,
            showContent: false
          })
          if (m.tool_calls) for (const tc of m.tool_calls) {
            let name = tc.function?.name || tc.name || '', args = tc.function?.arguments || tc.arguments || ''
            if (typeof args === 'string') try {
              args = JSON.parse(args)
            } catch {
            }
            item.blocks.push({
              type: 'tool_call',
              name,
              status: tr[tc.id] ? '成功' : '执行中',
              args,
              result: tr[tc.id] || '',
              expanded: !tr[tc.id]
            })
          }
          if (m.content) item.blocks.push({type: 'content', content: m.content})
        }
        merged.push(item)
      }
      store.setSessionMessages(targetSession, merged)
      await scroll(true)
    }
  } catch {
  }
}

// 强制从后端刷新指定会话的历史（跳过缓存）
// 不传 name 则刷新当前会话，并滚动到底部
const refreshHistory = async (name) => {
  const target = name || props.sessionName
  if (!target) return
  try {
    await loadHistory(target, true)
    if (!name) await scroll(true)
    addLog({level: 'INFO', text: '聊天记录已刷新', time: Date.now()})
  } catch (e) {
    addLog({level: 'ERROR', text: '刷新失败: ' + (e.message || '未知错误'), time: Date.now()})
  }
}

const loadSession = async (name, workspaceHash) => {
  try {
    resetSubAgentState()
    const {sessionsAPI} = await import('../services/api')
    await sessionsAPI.switchSession(name, workspaceHash)
    const existing = store.getSessionMessages(name)
    if (existing.length === 0) {
      await loadHistory(name)
    } else {
      // 缓存命中，直接滚动到底部
      await scroll(true)
    }
    await loadUsage({sessionName: name, workspaceHash})
  } catch (e) {
    console.error('切换会话失败:', e)
  }
}

const sendCommand = async cmd => {
  inputText.value = cmd;
  await sendMessage()
}

// 加载历史消息（仅在明确选了 session 时）
onMounted(() => {
  if (props.sessionName) loadHistory()
})

defineExpose({clearMessages, resetLocalMessages, loadSession, sendCommand, exportChat, refreshHistory})
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

.chat-head-title {
  font-size: 14px;
  font-weight: 600;
}

.chat-head-count {
  font-size: 12px;
  color: var(--fg-4);
}

/* 消息区 */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 16px 120px;
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

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--fg);
  margin-bottom: 4px;
}

.empty-desc {
  font-size: 13px;
  color: var(--fg-3);
  margin-bottom: 16px;
}

.empty-suggestions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
}

.suggestion {
  padding: 4px 10px;
  font-size: 12px;
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r);
  color: var(--fg-2);
  transition: all var(--t);
}

.suggestion:hover {
  border-color: var(--accent);
  color: var(--accent);
}

/* 消息 */
.msg {
  margin-bottom: 12px;
}

.msg.user {
  display: flex;
  justify-content: flex-end;
}

.msg-body {
  max-width: 80%;
}

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

.user-body .msg-time {
  font-size: 10px;
  opacity: 0.7;
  margin-top: 4px;
  text-align: right;
}

.assistant-body {
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  padding: 8px 12px;
  box-shadow: var(--glass-shadow);
}

.assistant-body ::selection {
  background: var(--accent);
  color: #fff;
}

.assistant-body .msg-time {
  font-size: 10px;
  color: var(--fg-4);
  margin-top: 4px;
}

.msg-text {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 用户消息中的图片 */
.user-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.user-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--border);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.user-image:hover {
  transform: scale(1.05);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

/* 图片预览弹窗 */
.image-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.image-preview-full {
  max-width: 90vw;
  max-height: 90vh;
  object-fit: contain;
  border-radius: 8px;
  cursor: default;
}

.image-preview-close {
  position: fixed;
  top: 16px;
  right: 24px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  font-size: 28px;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.image-preview-close:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* 消息底部栏（时间 + 复制按钮） */
.msg-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.user-body .msg-footer {
  justify-content: flex-end;
}

.assistant-body .msg-footer {
  justify-content: space-between;
}

.copy-msg-btn {
  opacity: 0;
  background: none;
  border: none;
  font-size: 12px;
  cursor: pointer;
  padding: 3px 5px;
  border-radius: var(--r-sm);
  transition: opacity 0.2s, background 0.2s;
  line-height: 1;
  color: var(--fg-3);
}

.user-body .copy-msg-btn {
  color: rgba(255, 255, 255, 0.8);
}

.msg-body:hover .copy-msg-btn {
  opacity: 0.6;
}

.copy-msg-btn:hover {
  opacity: 1;
  background: var(--glass-bg-2);
}

.user-body .copy-msg-btn:hover {
  background: rgba(255, 255, 255, 0.15);
}

/* 撤回按钮（恢复到 AI 修改前的状态） */
.rollback-btn {
  opacity: 0;
  background: none;
  border: none;
  font-size: 12px;
  cursor: pointer;
  padding: 3px 5px;
  border-radius: var(--r-sm);
  transition: opacity 0.2s, background 0.2s;
  line-height: 1;
  color: var(--fg-3);
}

.rollback-btn.loading {
  opacity: 0.5;
  pointer-events: none;
}

.user-body .rollback-btn {
  color: rgba(255, 255, 255, 0.8);
}

.msg-body:hover .rollback-btn {
  opacity: 0.6;
}

.rollback-btn:hover {
  opacity: 1;
  background: rgba(231, 76, 60, 0.12);
  color: var(--accent-5, #e74c3c);
}

.user-body .rollback-btn:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
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
.msg-blocks {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 思考块 */
.block-reasoning {
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
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

.reasoning-head svg:last-child {
  transition: transform var(--t);
}

.reasoning-text {
  padding: 0 10px 8px;
  font-size: 12px;
  font-family: var(--mono);
  color: var(--fg-3);
  line-height: 1.6;
}
.reasoning-text :deep(p) { margin: 0.4em 0; }
.reasoning-text :deep(pre) {
  background: var(--bg-3);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 6px 10px;
  margin: 4px 0;
  overflow-x: auto;
  font-size: 11px;
  line-height: 1.5;
}
.reasoning-text :deep(pre code) { background: none; padding: 0; }
.reasoning-text :deep(code) {
  font-size: 11px;
  background: var(--bg-3);
  padding: 1px 4px;
  border-radius: 3px;
}
.reasoning-text :deep(.code-block-wrap) { margin: 4px 0; }
.reasoning-text :deep(.code-block-wrap pre) { position: relative; margin: 0 !important; }
.reasoning-text :deep(.code-copy-btn) {
  position: absolute;
  top: 4px;
  right: 4px;
  opacity: 0;
  background: var(--bg-2);
  border: 1px solid var(--border);
  font-size: 12px;
  cursor: pointer;
  padding: 1px 5px;
  border-radius: var(--r-sm);
  transition: opacity 0.15s;
  line-height: 1;
  z-index: 2;
}
.reasoning-text :deep(.code-block-wrap pre:hover .code-copy-btn) { opacity: 0.7; }

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

.block-content :deep(pre code) {
  background: none;
  padding: 0;
}

.block-content :deep(strong) {
  font-weight: 600;
}

.block-content :deep(a) {
  color: var(--accent);
  text-decoration: none;
}

.block-content :deep(a:hover) {
  text-decoration: underline;
}

/* Markdown标题样式 */
.block-content :deep(h1) {
  font-size: 1.5em;
  margin: 0.5em 0;
  font-weight: 600;
}

.block-content :deep(h2) {
  font-size: 1.3em;
  margin: 0.5em 0;
  font-weight: 600;
}

.block-content :deep(h3) {
  font-size: 1.1em;
  margin: 0.5em 0;
  font-weight: 600;
}

.block-content :deep(h4) {
  font-size: 1em;
  margin: 0.5em 0;
  font-weight: 600;
}

.block-content :deep(h5) {
  font-size: 0.9em;
  margin: 0.5em 0;
  font-weight: 600;
}

.block-content :deep(h6) {
  font-size: 0.8em;
  margin: 0.5em 0;
  font-weight: 600;
}

/* 列表样式 */
.block-content :deep(ul) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.block-content :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.block-content :deep(li) {
  margin: 0.25em 0;
}

.block-content :deep(li > ul) {
  margin: 0.25em 0;
}

.block-content :deep(li > ol) {
  margin: 0.25em 0;
}

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
.block-content :deep(p) {
  margin: 0.5em 0;
}

.block-content :deep(p:first-child) {
  margin-top: 0;
}

.block-content :deep(p:last-child) {
  margin-bottom: 0;
}

/* 斜体 */
.block-content :deep(em) {
  font-style: italic;
}

/* 删除线 */
.block-content :deep(del) {
  text-decoration: line-through;
  color: var(--fg-3);
}

/* 工具块 */
.block-tool {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
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

.tool-head:hover {
  background: var(--bg-2);
}

.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-sm);
}

.tool-icon.执行中 {
  color: var(--yellow);
}

.tool-icon.成功 {
  color: var(--green);
}

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

.tool-status.执行中 {
  background: var(--yellow-bg);
  color: var(--yellow);
}

.tool-status.成功 {
  background: var(--green-bg);
  color: var(--green);
}

.tool-head svg:last-child {
  margin-left: auto;
  transition: transform var(--t);
  color: var(--fg-4);
}

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

.typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing span:nth-child(3) {
  animation-delay: 0.4s;
}

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
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(3px);
  }
}

/* 使用 v-show 控制显隐 */

/* ===== 日志堆叠容器 ===== */
.log-stack {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  padding: 12px 16px;
  z-index: 100;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
  /* 确保在消息滚动时仍固定在顶部 */
  overflow: visible;
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

  background: rgba(30, 30, 40, 0.78);
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


/* ==================== 子代理 Modal ==================== */
.sub-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
}

.sub-modal {
  width: min(90vw, 860px);
  height: min(85vh, 700px);
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border: 1px solid var(--glass-border);
  border-radius: 12px;
  box-shadow: var(--glass-shadow);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sub-modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.sub-modal-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.sub-modal-close {
  background: none;
  border: none;
  color: var(--fg-3);
  cursor: pointer;
  font-size: 18px;
  padding: 4px 8px;
  border-radius: 4px;
}

.sub-modal-close:hover {
  background: var(--bg-2);
  color: var(--fg);
}

.sub-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

/* 子代理会话卡片 */
.sub-session {
  margin-bottom: 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}

.sub-session-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 600;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
  color: var(--fg-2);
}

.sub-session-body {
  padding: 8px 12px;
}

.sub-session-body .block-content {
  padding: 6px 0;
  font-size: 13px;
}

.sub-session-body .block-reasoning {
  margin: 4px 0;
  background: var(--bg-2);
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 12px;
  color: var(--fg-3);
}

.sub-session-body .block-tool {
  margin: 4px 0;
}

.sub-session-body .tool-head {
  padding: 3px 8px;
  font-size: 12px;
}

.sub-session-body .tool-detail {
  font-size: 12px;
}

/* 浮动入口按钮 */
.sub-float-btn {
  position: fixed;
  right: 24px;
  bottom: 170px;
  z-index: 100;
  padding: 8px 14px;
  border-radius: 20px;
  background: var(--accent);
  color: #fff;
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.25);
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s ease;
}

.sub-float-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.35);
}

.sub-float-btn .badge {
  background: rgba(255, 255, 255, 0.25);
  border-radius: 10px;
  padding: 0 6px;
  font-size: 11px;
  min-width: 18px;
  text-align: center;
}

/* ===== 系统提示词弹窗 ===== */
.prompt-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.prompt-modal {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  width: 80vw;
  max-width: 900px;
  height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--glass-shadow);
}

.prompt-modal-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.prompt-modal-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.prompt-modal-size {
  font-size: 11px;
  color: var(--fg-4);
  background: var(--bg-3);
  padding: 2px 8px;
  border-radius: var(--r-sm);
}

.prompt-modal-close {
  background: none;
  border: none;
  font-size: 20px;
  color: var(--fg-3);
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.prompt-modal-close:hover {
  color: var(--fg);
}

.prompt-modal-body {
  flex: 1;
  overflow: auto;
  padding: 20px 24px;
}

.prompt-modal-content {
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  overflow-x: hidden;
}

.prompt-modal-content :deep(pre) {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 10px;
  margin: 6px 0;
  overflow-x: auto;
}

.prompt-modal-content :deep(code) {
  font-family: var(--mono);
  font-size: 12px;
}

.prompt-modal-content :deep(pre code) {
  background: none;
  padding: 0;
}

.prompt-modal-content :deep(strong) {
  font-weight: 600;
}

.prompt-modal-content :deep(a) {
  color: var(--accent);
  text-decoration: none;
}

.prompt-modal-content :deep(h1),
.prompt-modal-content :deep(h2),
.prompt-modal-content :deep(h3),
.prompt-modal-content :deep(h4) {
  margin: 0.5em 0;
  font-weight: 600;
}

.prompt-modal-content :deep(ul),
.prompt-modal-content :deep(ol) {
  margin: 0.5em 0;
  padding-left: 1.5em;
}

.prompt-modal-content :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.5em 1em;
  border-left: 3px solid var(--accent);
  background: var(--bg-3);
  border-radius: 0 var(--r) var(--r) 0;
}

.prompt-modal-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5em 0;
}

.prompt-modal-content :deep(th),
.prompt-modal-content :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
}

.prompt-modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
}

/* ===== 无会话时禁用输入条 ===== */
.no-session-input-bar {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  background: var(--bg);
}

.no-session-input-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border: 1px dashed var(--border-2);
  border-radius: var(--r);
  color: var(--fg-4);
  font-size: 13px;
  cursor: default;
  user-select: none;
}

/* ===== 消息缩略图 dock（右侧 dock 栏） ===== */
.msg-thumb-dock {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 50;
  pointer-events: none;
}

.thumb-dock-inner {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 4px;
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  box-shadow: var(--glass-shadow);
  pointer-events: auto;
  opacity: 0.35;
  transition: opacity 0.25s ease, box-shadow 0.25s ease;
  max-height: 70vh;
  overflow-y: auto;
  min-width: 10px;
}

.thumb-dock-inner:hover {
  opacity: 0.95;
  box-shadow: 0 4px 20px rgba(0,0,0,0.12);
}

.thumb-dock-inner::-webkit-scrollbar {
  width: 2px;
}

.thumb-dock-inner::-webkit-scrollbar-thumb {
  background: var(--fg-4);
  border-radius: 1px;
}

.thumb-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  overflow: hidden;
}

.thumb-item:hover {
  background: var(--accent-bg);
}

.thumb-item:hover .thumb-indicator {
  background: var(--accent);
  transform: scale(1.2);
}

.thumb-indicator {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--fg-4);
  transition: all 0.2s ease;
}

.thumb-preview {
  font-size: 11px;
  color: var(--fg-3);
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 0;
  opacity: 0;
  transition: max-width 0.25s ease, opacity 0.25s ease, margin-left 0.25s ease;
}

.thumb-dock-inner:hover .thumb-preview {
  max-width: 180px;
  opacity: 1;
  margin-left: 2px;
}
</style>