<template>
  <div class="req-board">
    <!-- 看板视图 -->
    <template v-if="!selected">
      <header class="req-board-header">
        <div class="req-board-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M9 21V9"/></svg>
          <span>需求池</span>
          <span class="req-board-count">{{ requirements.length }} 条需求</span>
        </div>
        <div class="req-board-header-actions">
          <span class="req-board-storage-tip">需求由 AI 自动执行并流转状态</span>
          <button type="button" class="req-btn req-btn-primary" @click="openCreateModal">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
            新建需求
          </button>
        </div>
      </header>

      <main class="req-board-columns">
        <section
          v-for="col in columns"
          :key="col.key"
          class="req-column"
          :class="`req-column-${col.key}`"
        >
          <header class="req-column-header">
            <span class="req-column-dot" :style="{ background: col.color }"></span>
            <span class="req-column-name">{{ col.label }}</span>
            <span class="req-column-count">{{ listOf(col.key).length }}</span>
            <span v-if="col.key === 'todo'" class="req-column-ai-hint">AI 待调度</span>
            <span v-else-if="col.key === 'doing'" class="req-column-ai-hint">AI 执行中</span>
          </header>
          <div class="req-column-body">
            <article
              v-for="item in listOf(col.key)"
              :key="item.id"
              class="req-card"
              @click="openDetail(item)"
            >
              <div class="req-card-title">
                {{ item.title }}
                <span v-if="item.priority === 'high'" class="req-priority req-priority-high" title="高优先级">高</span>
                <span v-else-if="item.priority === 'medium'" class="req-priority req-priority-medium" title="中优先级">中</span>
                <span v-else class="req-priority req-priority-low" title="低优先级">低</span>
              </div>
              <div class="req-card-meta">
                <span v-if="item.project?.name" class="req-project-badge" :title="`项目：${item.project.name}`">{{ item.project.name }}</span>
                <span class="req-ai-badge" title="由 AI 根据任务描述自动执行">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="4" y="8" width="16" height="12" rx="2"/><path d="M12 4v4M9 13h.01M15 13h.01M9 17h6"/></svg>
                  AI 执行
                </span>
                <span class="req-card-time">{{ fmtTime(item.updatedAt) }}</span>
              </div>
            </article>
            <div v-if="!listOf(col.key).length" class="req-column-empty">暂无需求</div>
          </div>
        </section>
      </main>
    </template>

    <!-- 全屏详情视图（参考聊天框界面） -->
    <div v-else class="req-detail-view" :data-theme="theme">
      <!-- 顶部栏 -->
      <header class="req-detail-topbar">
        <button type="button" class="req-detail-back" title="返回需求池" @click="closeDetail">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg>
          需求池
        </button>
        <div class="req-detail-topbar-title">
          <span class="req-status" :class="`req-status-${selected.status}`">{{ statusLabel(selected.status) }}</span>
          <span class="req-priority" :class="`req-priority-${selected.priority}`">{{ priorityLabel(selected.priority) }}优先级</span>
          <span class="req-detail-id">#{{ selected.id.slice(-4) }}</span>
          <span class="req-detail-title-text">{{ selected.title }}</span>
        </div>
        <button type="button" class="req-detail-close" title="关闭" @click="closeDetail">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 6 12 12M18 6 6 18"/></svg>
        </button>
      </header>

      <div class="req-detail-main">
        <!-- 信息区：描述 + AI 执行 -->
        <section class="req-detail-info">
          <div class="req-info-card req-info-desc">
            <h3>描述 <span v-if="selected.project?.name" class="req-info-project">项目：{{ selected.project.name }}</span></h3>
            <p class="req-detail-desc">{{ selected.description || '暂无描述' }}</p>
          </div>
          <div class="req-info-card">
            <h3>AI 执行</h3>
            <div class="req-ai-box">
              <span class="req-ai-icon">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="4" y="8" width="16" height="12" rx="2"/><path d="M12 4v4M9 13h.01M15 13h.01M9 17h6"/></svg>
              </span>
              <div class="req-ai-info">
                <span class="req-ai-name">{{ aiStateText }}</span>
                <span class="req-ai-role">AI 根据任务描述自动执行并流转状态</span>
              </div>
              <div class="req-ai-actions">
                <button
                  v-if="selected.status === 'todo'"
                  type="button"
                  class="req-btn req-btn-sm req-btn-primary"
                  :disabled="aiRunning"
                  @click="runAI('doing')"
                >
                  <svg v-if="aiRunning" class="req-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
                  <template v-else>让 AI 执行</template>
                </button>
                <template v-else-if="selected.status === 'doing'">
                  <button type="button" class="req-btn req-btn-sm req-btn-success" :disabled="aiRunning" @click="runAI('done')">AI 已完成</button>
                  <button type="button" class="req-btn req-btn-sm req-btn-danger" :disabled="aiRunning" @click="runAI('failed')">AI 失败</button>
                </template>
                <button
                  v-else
                  type="button"
                  class="req-btn req-btn-sm"
                  :disabled="aiRunning"
                  @click="runAI('todo')"
                >重新执行</button>
              </div>
            </div>
          </div>
        </section>

        <!-- 评论 / 执行日志 切换 -->
        <div class="req-detail-tabs">
          <button
            type="button"
            class="req-detail-tab"
            :class="{ active: detailTab === 'comments' }"
            @click="switchTab('comments')"
          >评论 ({{ selected.comments.length }})</button>
          <button
            type="button"
            class="req-detail-tab"
            :class="{ active: detailTab === 'logs' }"
            @click="switchTab('logs')"
          >执行日志 ({{ selected.logs.length }})</button>
        </div>

        <!-- 聊天区：复用聊天框组件（ChatMessage / BlockRenderer） -->
        <div class="req-chat-area">
          <div ref="chatMessagesRef" class="req-chat-messages">
            <ChatMessage
              v-for="(m, i) in currentMessages"
              :key="m.id"
              :idx="i"
              :msg="m"
              :workspace-path="''"
              :snapshot-rollback-loading="snapshotRollbackLoading"
              :rollback-disabled="true"
              :branch-disabled="true"
              @copy-message="copyMessage"
            />
            <div v-if="!currentMessages.length" class="req-chat-empty">
              {{ detailTab === 'comments' ? '暂无评论，来抢沙发～' : '暂无执行日志' }}
            </div>
          </div>
          <form v-if="detailTab === 'comments'" class="req-chat-input" @submit.prevent="addComment">
            <input
              v-model="commentDraft"
              type="text"
              placeholder="写下你的评论…"
              maxlength="200"
            />
            <button type="submit" class="req-btn req-btn-sm req-btn-primary" :disabled="!commentDraft.trim()">发送</button>
          </form>
        </div>
      </div>
    </div>

    <!-- 新建需求弹窗 -->
    <div v-if="createOpen" class="req-create-mask" @click.self="createOpen = false">
      <form class="req-create-modal" :data-theme="theme" @submit.prevent="createRequirement">
        <header class="req-create-header">
          <span>新建需求</span>
          <button type="button" class="req-detail-close" title="关闭" @click="createOpen = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 6 12 12M18 6 6 18"/></svg>
          </button>
        </header>
        <div class="req-create-body">
          <label class="req-field">
            <span>标题 <em>*</em></span>
            <input v-model="draft.title" type="text" placeholder="需求标题" maxlength="60" required />
          </label>
          <label class="req-field">
            <span>描述</span>
            <textarea v-model="draft.description" rows="4" placeholder="需求描述、验收标准…（AI 将根据描述自动执行）" maxlength="500"></textarea>
          </label>
          <label class="req-field">
            <span>项目 <em>*</em></span>
            <ReqSelect
              v-model="draft.projectHash"
              :options="projectOptions"
              placeholder="请选择项目"
            >
              <template #option="{ option }">
                <svg class="req-option-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"/></svg>
                {{ option.label }}
              </template>
            </ReqSelect>
          </label>
          <label class="req-field">
            <span>优先级</span>
            <ReqSelect
              v-model="draft.priority"
              :options="priorityOptions"
              placeholder="选择优先级"
            >
              <template #option="{ option }">
                <span class="req-option-dot" :style="{ background: option.dot }"></span>
                {{ option.label }}
              </template>
            </ReqSelect>
          </label>
          <p class="req-create-tip">创建后将自动进入「待执行」，由 AI 调度执行并流转状态。</p>
        </div>
        <footer class="req-create-footer">
          <button type="button" class="req-btn" @click="createOpen = false">取消</button>
          <button type="submit" class="req-btn req-btn-primary" :disabled="!draft.title.trim() || !draft.projectHash">创建</button>
        </footer>
      </form>
    </div>
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import ChatMessage from '../components/ChatMessage.vue'
import ReqSelect from '../components/ReqSelect.vue'
import {configAPI} from '../services/api'

// ============ 常量 ============
const STORAGE_KEY = 'loopra-requirement-board'

const COLUMNS = [
  { key: 'todo', label: '待执行', color: '#9ca3af' },
  { key: 'doing', label: '执行中', color: '#3b82f6' },
  { key: 'done', label: '已完成', color: '#22c55e' },
  { key: 'failed', label: '已失败', color: '#ef4444' }
]

// 项目列表：优先从后端拉取；后端不可用时（纯前端演示）回退到演示项目
const MOCK_PROJECTS = [
  { hash: 'p_agent4j', name: 'agent4j' },
  { hash: 'p_loopra-web', name: 'loopra-web' },
  { hash: 'p_loopra-harness', name: 'loopra-harness' },
  { hash: 'p_loopra-model', name: 'loopra-model' },
  { hash: 'p_loopra-front', name: 'loopra-front' }
]
const STATUS_LABELS = { todo: '待执行', doing: '执行中', done: '已完成', failed: '已失败' }
const PRIORITY_LABELS = { high: '高', medium: '中', low: '低' }

// 状态流转时的 AI 视角文案（由 AI 根据任务自动流转）
const AI_ACTION_LOGS = {
  todo: { level: 'info', text: '重新提交给 AI，进入待执行队列' },
  doing: { level: 'info', text: 'AI 已接收需求，开始分析任务描述并制定执行计划' },
  done: { level: 'info', text: 'AI 已完成实现并通过验证，需求关闭' },
  failed: { level: 'error', text: 'AI 执行失败：未能满足验收条件，等待人工确认后重新执行' }
}

let idSeed = 1
const nextId = () => `req_${Date.now().toString(36)}_${(idSeed++).toString(36)}`

// ============ 种子数据（AI 视角执行日志） ============
function seedRequirements() {
  const now = Date.now()
  const H = 3600 * 1000
  const D = 24 * H
  const mk = (partial) => ({
    id: nextId(),
    priority: 'medium',
    comments: [],
    logs: [],
    createdAt: now,
    updatedAt: now,
    ...partial
  })
  return [
    mk({
      title: '优化会话列表加载性能',
      description: '首页会话列表接口改为分页加载，首屏 200ms 内渲染完成；列表滚动到底自动加载下一页，避免一次性拉取全量会话导致卡顿。',
      priority: 'high',
      project: { hash: 'p_loopra-front', name: 'loopra-front' },
      status: 'todo',
      createdAt: now - 2 * D,
      updatedAt: now - 2 * D,
      logs: [
        { id: nextId(), time: now - 2 * D, level: 'info', text: '需求已创建，等待 AI 调度' }
      ]
    }),
    mk({
      title: '支持 Markdown 表格导出',
      description: '对话内容支持一键导出为 Markdown 文件，保留标题层级、代码块与表格结构。',
      project: { hash: 'p_agent4j', name: 'agent4j' },
      status: 'todo',
      createdAt: now - 1 * D,
      updatedAt: now - 20 * H,
      logs: [
        { id: nextId(), time: now - 1 * D, level: 'info', text: '需求已创建，等待 AI 调度' },
        { id: nextId(), time: now - 20 * H, level: 'info', text: 'AI 已完成优先级评估，进入待执行队列' }
      ]
    }),
    mk({
      title: '新增深色模式对比度优化',
      description: '调整深色主题下正文与背景的对比度，确保 WCAG AA 级别可读性；同步更新代码高亮配色。',
      project: { hash: 'p_loopra-front', name: 'loopra-front' },
      status: 'todo',
      createdAt: now - 10 * H,
      updatedAt: now - 10 * H,
      logs: [
        { id: nextId(), time: now - 10 * H, level: 'info', text: '需求已创建，等待 AI 调度' }
      ]
    }),
    mk({
      title: '重构工具调用结果渲染',
      description: '将工具调用结果从纯文本渲染升级为结构化展示（表格 / Diff / 文件树），支持折叠与展开。',
      priority: 'high',
      project: { hash: 'p_loopra-front', name: 'loopra-front' },
      status: 'doing',
      createdAt: now - 3 * D,
      updatedAt: now - 1 * H,
      logs: [
        { id: nextId(), time: now - 3 * D, level: 'info', text: '需求已创建，等待 AI 调度' },
        { id: nextId(), time: now - 3 * D + 1 * H, level: 'info', text: 'AI 已接收需求，开始分析现有 BlockRenderer 渲染链路' },
        { id: nextId(), time: now - 2 * D, level: 'debug', text: '完成工具结果事件流梳理（tool_call → tool_result）' },
        { id: nextId(), time: now - 1 * D, level: 'info', text: 'AI 已实现 Diff 渲染器 v1，通过本地冒烟测试' },
        { id: nextId(), time: now - 1 * H, level: 'warn', text: '表格渲染在窄窗口下溢出，AI 正在调整布局策略' }
      ]
    }),
    mk({
      title: '会话分支合并功能',
      description: '支持将分支会话合并回主会话，合并时保留两边的消息顺序并自动标记冲突片段。',
      project: { hash: 'p_loopra-front', name: 'loopra-front' },
      status: 'doing',
      createdAt: now - 5 * D,
      updatedAt: now - 6 * H,
      logs: [
        { id: nextId(), time: now - 5 * D, level: 'info', text: '需求已创建，等待 AI 调度' },
        { id: nextId(), time: now - 4 * D, level: 'info', text: 'AI 已接收需求，完成分支数据模型设计评审' },
        { id: nextId(), time: now - 6 * H, level: 'info', text: 'AI 正在实现合并算法：三条分支的冲突矩阵已生成' }
      ]
    }),
    mk({
      title: '修复窗口拖动失效问题',
      description: '无边框窗口在部分系统下无法通过标题栏拖动，需要补充 -webkit-app-region 兼容处理。',
      priority: 'high',
      project: { hash: 'p_loopra-front', name: 'loopra-front' },
      status: 'done',
      createdAt: now - 6 * D,
      updatedAt: now - 4 * D,
      logs: [
        { id: nextId(), time: now - 6 * D, level: 'info', text: '需求已创建，等待 AI 调度' },
        { id: nextId(), time: now - 5 * D, level: 'info', text: 'AI 已接收需求，定位根因：标题栏缺少 drag 区域标记' },
        { id: nextId(), time: now - 5 * D + 3 * H, level: 'debug', text: '修复方案：TitleBar / DesktopShell 补充 drag 区域样式' },
        { id: nextId(), time: now - 4 * D, level: 'info', text: 'AI 已完成修复并全平台冒烟通过，需求关闭' }
      ]
    }),
    mk({
      title: '接入第三方知识库检索',
      description: '将常用文档接入 RAG 检索，支持在对话中引用知识库片段作为上下文。',
      project: { hash: 'p_loopra-harness', name: 'loopra-harness' },
      status: 'failed',
      createdAt: now - 4 * D,
      updatedAt: now - 2 * D,
      logs: [
        { id: nextId(), time: now - 4 * D, level: 'info', text: '需求已创建，等待 AI 调度' },
        { id: nextId(), time: now - 3 * D, level: 'info', text: 'AI 已接收需求，完成第三方服务鉴权配置，开始索引构建' },
        { id: nextId(), time: now - 2 * D, level: 'error', text: 'AI 执行失败：服务端限流 429，重试 3 次后放弃' },
        { id: nextId(), time: now - 2 * D, level: 'error', text: '需求标记为失败，等待人工确认后重新执行' }
      ]
    })
  ]
}

// ============ 状态 ============
const requirements = ref([])
const selected = ref(null)
const detailTab = ref('comments')
const commentDraft = ref('')
const createOpen = ref(false)
const projects = ref([])
const draft = reactive({ title: '', description: '', priority: 'medium', projectHash: '' })
const theme = ref('gray')
// 模拟 AI 执行中的 loading 态（后续接后端时替换为真实的执行中状态）
const aiRunning = ref(false)
let aiTimer = null
const chatMessagesRef = ref(null)
// ChatMessage 必需 prop：快照回滚 loading 表（需求池不使用回滚，传空 Map）
const snapshotRollbackLoading = new Map()

const columns = COLUMNS
const listOf = (key) => requirements.value.filter((item) => item.status === key)
const aiStateText = computed(() => {
  if (aiRunning.value) return 'AI 执行中…'
  return STATUS_LABELS[selected.value?.status] || ''
})

// 弹窗下拉选项（项目风格自定义下拉）
const projectOptions = computed(() => projects.value.map((project) => ({ value: project.hash, label: project.name })))
const priorityOptions = [
  { value: 'high', label: '高', dot: '#ef4444' },
  { value: 'medium', label: '中', dot: '#f59e0b' },
  { value: 'low', label: '低', dot: '#6b7280' }
]

// 聊天消息流：执行日志 → AI 消息；评论 → 用户消息（复用 ChatMessage 渲染）
const currentMessages = computed(() => {
  if (!selected.value) return []
  if (detailTab.value === 'logs') {
    return selected.value.logs.map((log) => ({
      id: log.id,
      role: 'assistant',
      time: fmtTime(log.time),
      blocks: [{ type: 'content', content: log.text }]
    }))
  }
  return selected.value.comments.map((comment) => ({
    id: comment.id,
    role: 'user',
    time: fmtTime(comment.time),
    content: comment.text,
    blocks: []
  }))
})

// ============ 工具函数 ============
const statusLabel = (status) => STATUS_LABELS[status] || status
const priorityLabel = (priority) => PRIORITY_LABELS[priority] || priority

function fmtTime(timestamp) {
  const date = new Date(timestamp)
  const pad = (n) => String(n).padStart(2, '0')
  const now = new Date()
  const sameDay = date.toDateString() === now.toDateString()
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (sameDay) return `今天 ${time}`
  const yesterday = new Date(now.getTime() - 24 * 3600 * 1000)
  if (date.toDateString() === yesterday.toDateString()) return `昨天 ${time}`
  return `${date.getMonth() + 1}/${date.getDate()} ${time}`
}

// ============ 持久化 ============
function persist() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(requirements.value))
}
function load() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      // 旧版数据含 agent 字段（执行 Agent 已废弃），直接重置为新版种子数据
      if (Array.isArray(parsed) && parsed.some((item) => item && item.agent)) {
        requirements.value = seedRequirements()
        persist()
        return
      }
      requirements.value = parsed
    } else {
      requirements.value = seedRequirements()
      persist()
    }
  } catch (error) {
    console.warn('[requirement-board] 读取本地数据失败，使用演示数据:', error)
    requirements.value = seedRequirements()
  }
}

// ============ 交互 ============
function openDetail(item) {
  selected.value = item
  detailTab.value = 'comments'
}
function closeDetail() {
  selected.value = null
  detailTab.value = 'comments'
  commentDraft.value = ''
}

function switchTab(tab) {
  detailTab.value = tab
  scrollChatToBottom()
}

// 切换 tab / 新增消息后滚动到聊天区底部
function scrollChatToBottom() {
  nextTick(() => {
    const el = chatMessagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// 模拟 AI 流转：短暂 loading 后写 AI 视角日志并推进状态
// （后续接后端时，由后端 AI 执行器真实驱动，前端仅展示）
function runAI(nextStatus) {
  if (aiRunning.value) return
  const item = selected.value
  if (!item) return
  aiRunning.value = true
  aiTimer = setTimeout(() => {
    const from = item.status
    item.status = nextStatus
    item.updatedAt = Date.now()
    const action = AI_ACTION_LOGS[nextStatus]
    item.logs.push({
      id: nextId(),
      time: item.updatedAt,
      level: action.level,
      text: `${action.text}（${STATUS_LABELS[from]} → ${STATUS_LABELS[nextStatus]}）`
    })
    aiRunning.value = false
    aiTimer = null
    persist()
    // 停留在日志 tab 时展示新增日志
    if (detailTab.value === 'logs') scrollChatToBottom()
  }, 600)
}

function addComment() {
  const text = commentDraft.value.trim()
  if (!text || !selected.value) return
  selected.value.comments.push({
    id: nextId(),
    author: '我',
    time: Date.now(),
    text
  })
  selected.value.updatedAt = Date.now()
  commentDraft.value = ''
  persist()
  scrollChatToBottom()
}

function copyMessage(msg) {
  const text = msg.content || (msg.blocks || []).map((b) => b.content || '').join('\n')
  if (!text) return
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(text).catch(() => {})
  }
}

function openCreateModal() {
  draft.title = ''
  draft.description = ''
  draft.priority = 'medium'
  draft.projectHash = ''
  createOpen.value = true
}

function createRequirement() {
  const title = draft.title.trim()
  if (!title) return
  // 新建需求必须选择项目
  const project = projects.value.find((item) => item.hash === draft.projectHash)
  if (!project) return
  const now = Date.now()
  requirements.value.unshift({
    id: nextId(),
    title,
    description: draft.description.trim(),
    priority: draft.priority,
    project: { hash: project.hash, name: project.name },
    status: 'todo',
    createdAt: now,
    updatedAt: now,
    comments: [],
    logs: [{ id: nextId(), time: now, level: 'info', text: '需求已创建，等待 AI 调度' }]
  })
  createOpen.value = false
  persist()
}

// 加载项目列表：后端可用时用真实项目，否则回退演示项目（纯前端模式）
async function loadProjects() {
  try {
    const res = await configAPI.listWorkspaces()
    const list = res?.data
    if (Array.isArray(list) && list.length > 0) {
      projects.value = list.map((workspace) => ({ hash: workspace.hash, name: workspace.name || workspace.path }))
      return
    }
  } catch (error) {
    console.warn('[requirement-board] 加载项目列表失败，使用演示项目:', error)
  }
  projects.value = MOCK_PROJECTS
}

watch(currentMessages, scrollChatToBottom)

onMounted(() => {
  // 独立窗口主题：与主应用保持一致（localStorage 同步）
  theme.value = localStorage.getItem('loopra-theme') || 'gray'
  document.documentElement.setAttribute('data-theme', theme.value)
  load()
  loadProjects()
})

onBeforeUnmount(() => {
  if (aiTimer) clearTimeout(aiTimer)
})
</script>

<style scoped>
.req-board { height: 100vh; display: flex; flex-direction: column; overflow: hidden; background: var(--bg, #fff); color: var(--fg, #202124); }

/* ============ 顶栏 ============ */
.req-board-header { height: 56px; flex: 0 0 auto; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; border-bottom: 1px solid var(--border, #e8e8e8); background: var(--bg, #fff); }
.req-board-title { display: flex; align-items: center; gap: 10px; font-size: 16px; font-weight: 650; }
.req-board-title svg { width: 18px; height: 18px; color: var(--fg-3, #727987); }
.req-board-count { font-size: 12px; font-weight: 400; color: var(--fg-4, #9ca3af); }
.req-board-header-actions { display: flex; align-items: center; gap: 12px; }
.req-board-storage-tip { font-size: 12px; color: var(--fg-4, #9ca3af); }

/* ============ 通用按钮 ============ */
.req-btn { height: 32px; display: inline-flex; align-items: center; gap: 6px; padding: 0 14px; border: 1px solid var(--border-2, #d1d5db); border-radius: 6px; background: var(--bg, #fff); color: var(--fg-2, #525866); font: inherit; font-size: 13px; cursor: pointer; }
.req-btn:hover { background: var(--bg-2, #f9fafb); color: var(--fg, #202124); }
.req-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.req-btn svg { width: 14px; height: 14px; }
.req-btn-sm { height: 26px; padding: 0 10px; font-size: 12px; }
.req-btn-primary { background: var(--accent, #52525b); border-color: var(--accent, #52525b); color: #fff; }
.req-btn-primary:hover { background: var(--accent, #52525b); color: #fff; opacity: 0.88; }
.req-btn-success { background: #22c55e; border-color: #22c55e; color: #fff; }
.req-btn-success:hover { background: #16a34a; border-color: #16a34a; color: #fff; }
.req-btn-danger { background: #ef4444; border-color: #ef4444; color: #fff; }
.req-btn-danger:hover { background: #dc2626; border-color: #dc2626; color: #fff; }
.req-spin { animation: req-spin 0.8s linear infinite; }
@keyframes req-spin { to { transform: rotate(360deg); } }

/* ============ 看板四列 ============ */
.req-board-columns { min-height: 0; flex: 1; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; padding: 16px 20px 20px; overflow: hidden; background: var(--bg-2, #f9fafb); }
.req-column { min-height: 0; display: flex; flex-direction: column; border-radius: 10px; background: var(--bg-3, #f3f4f6); border: 1px solid var(--border, #e8e8e8); overflow: hidden; }
.req-column-header { height: 40px; flex: 0 0 auto; display: flex; align-items: center; gap: 8px; padding: 0 14px; border-bottom: 1px solid var(--border, #e8e8e8); background: var(--bg, #fff); }
.req-column-dot { width: 8px; height: 8px; border-radius: 50%; }
.req-column-name { font-size: 13px; font-weight: 600; }
.req-column-count { min-width: 20px; height: 20px; display: inline-flex; align-items: center; justify-content: center; padding: 0 6px; border-radius: 10px; background: var(--bg-3, #f3f4f6); color: var(--fg-3, #727987); font-size: 12px; box-sizing: border-box; }
.req-column-ai-hint { margin-left: auto; font-size: 11px; color: var(--fg-4, #9ca3af); }
.req-column-body { min-height: 0; flex: 1; overflow-y: auto; padding: 10px; display: grid; gap: 8px; align-content: start; }
.req-column-body::-webkit-scrollbar { width: 6px; }
.req-column-body::-webkit-scrollbar-thumb { background: rgba(80, 88, 102, 0.35); border-radius: 6px; }

/* ============ 卡片 ============ */
.req-card { padding: 10px 12px; border: 1px solid var(--border, #e8e8e8); border-radius: 8px; background: var(--bg, #fff); cursor: pointer; transition: border-color 0.15s, box-shadow 0.15s; }
.req-card:hover { border-color: var(--border-2, #d1d5db); box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06); }
.req-card-title { display: flex; align-items: flex-start; gap: 6px; font-size: 13px; line-height: 1.45; word-break: break-word; }
.req-card-title .req-priority { margin-top: 1px; flex: 0 0 auto; }
.req-card-meta { display: flex; align-items: center; gap: 6px; margin-top: 8px; }
.req-ai-badge { height: 18px; display: inline-flex; align-items: center; gap: 4px; padding: 0 6px; border-radius: 4px; background: var(--accent-bg, rgba(82, 82, 91, 0.1)); color: var(--fg-3, #727987); font-size: 11px; flex: 0 0 auto; }
.req-ai-badge svg { width: 11px; height: 11px; }
.req-project-badge { max-width: 120px; height: 18px; display: inline-flex; align-items: center; padding: 0 6px; border: 1px solid var(--border-2, #d1d5db); border-radius: 4px; color: var(--fg-3, #727987); font-size: 11px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; flex: 0 0 auto; }
.req-card-time { margin-left: auto; flex: 0 0 auto; font-size: 11px; color: var(--fg-4, #9ca3af); }
.req-column-empty { padding: 24px 0; text-align: center; color: var(--fg-4, #9ca3af); font-size: 12px; }

/* ============ 优先级 / 状态徽章 ============ */
.req-priority { display: inline-flex; align-items: center; height: 16px; padding: 0 6px; border-radius: 4px; font-size: 11px; font-weight: 500; }
.req-priority-high { background: rgba(239, 68, 68, 0.12); color: #dc2626; }
.req-priority-medium { background: rgba(245, 158, 11, 0.14); color: #d97706; }
.req-priority-low { background: rgba(107, 114, 128, 0.12); color: #6b7280; }
.req-status { display: inline-flex; align-items: center; height: 20px; padding: 0 8px; border-radius: 10px; font-size: 12px; font-weight: 500; }
.req-status-todo { background: rgba(156, 163, 175, 0.16); color: #6b7280; }
.req-status-doing { background: rgba(59, 130, 246, 0.14); color: #2563eb; }
.req-status-done { background: rgba(34, 197, 94, 0.14); color: #16a34a; }
.req-status-failed { background: rgba(239, 68, 68, 0.14); color: #dc2626; }

/* ============ 全屏详情视图 ============ */
.req-detail-view { position: fixed; inset: 0; z-index: 50; display: flex; flex-direction: column; background: var(--bg, #fff); animation: req-fade-in 0.18s ease-out; }
@keyframes req-fade-in { from { opacity: 0.5; } to { opacity: 1; } }
.req-detail-topbar { height: 52px; flex: 0 0 auto; display: flex; align-items: center; gap: 16px; padding: 0 16px; border-bottom: 1px solid var(--border, #e8e8e8); background: var(--bg, #fff); }
.req-detail-back { height: 30px; display: inline-flex; align-items: center; gap: 4px; padding: 0 10px; border: 0; border-radius: 6px; background: transparent; color: var(--fg-3, #727987); font: inherit; font-size: 13px; cursor: pointer; flex: 0 0 auto; }
.req-detail-back:hover { background: var(--bg-3, #f3f4f6); color: var(--fg, #202124); }
.req-detail-back svg { width: 15px; height: 15px; }
.req-detail-topbar-title { min-width: 0; flex: 1; display: flex; align-items: center; gap: 8px; }
.req-detail-title-text { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 14px; font-weight: 600; }
.req-detail-id { font-size: 11px; color: var(--fg-4, #9ca3af); }
.req-detail-close { width: 28px; height: 28px; display: inline-flex; align-items: center; justify-content: center; border: 0; border-radius: 6px; background: transparent; color: var(--fg-3, #727987); cursor: pointer; flex: 0 0 auto; }
.req-detail-close:hover { background: var(--bg-3, #f3f4f6); color: var(--fg, #202124); }
.req-detail-close svg { width: 15px; height: 15px; }

.req-detail-main { min-height: 0; flex: 1; display: flex; flex-direction: column; padding: 14px clamp(16px, 4vw, 48px) 0; }

/* 信息区：描述 + AI 执行 */
.req-detail-info { flex: 0 0 auto; display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 12px; }
.req-info-card { padding: 12px 14px; border: 1px solid var(--border, #e8e8e8); border-radius: 8px; background: var(--bg-2, #f9fafb); }
.req-info-card h3 { margin: 0 0 8px; font-size: 12px; font-weight: 600; color: var(--fg-4, #9ca3af); letter-spacing: 0.04em; }
.req-info-project { margin-left: 8px; font-weight: 400; color: var(--fg-3, #727987); }
.req-detail-desc { margin: 0; font-size: 13px; line-height: 1.7; color: var(--fg-2, #525866); white-space: pre-wrap; word-break: break-word; }

/* AI 执行卡片 */
.req-ai-box { display: flex; align-items: center; gap: 10px; }
.req-ai-icon { width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; border-radius: 8px; background: var(--accent-bg, rgba(82, 82, 91, 0.12)); color: var(--fg-2, #525866); }
.req-ai-icon svg { width: 17px; height: 17px; }
.req-ai-info { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 2px; }
.req-ai-name { font-size: 13px; font-weight: 600; }
.req-ai-role { font-size: 11px; color: var(--fg-4, #9ca3af); }
.req-ai-actions { display: flex; align-items: center; gap: 6px; flex: 0 0 auto; }

/* Tab 切换 */
.req-detail-tabs { display: flex; gap: 4px; margin-top: 14px; border-bottom: 1px solid var(--border, #e8e8e8); flex: 0 0 auto; }
.req-detail-tab { height: 36px; padding: 0 14px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: var(--fg-3, #727987); font: inherit; font-size: 13px; cursor: pointer; }
.req-detail-tab:hover { color: var(--fg, #202124); }
.req-detail-tab.active { color: var(--fg, #202124); font-weight: 600; border-bottom-color: var(--accent, #52525b); }

/* 聊天区（复用聊天框组件渲染） */
.req-chat-area { min-height: 0; flex: 1; display: flex; flex-direction: column; }
.req-chat-messages { min-height: 0; flex: 1; overflow-y: auto; padding: 12px 4px 4px; }
.req-chat-messages::-webkit-scrollbar { width: 6px; }
.req-chat-messages::-webkit-scrollbar-thumb { background: rgba(80, 88, 102, 0.35); border-radius: 6px; }
.req-chat-empty { padding: 32px 0; text-align: center; color: var(--fg-4, #9ca3af); font-size: 12px; }
.req-chat-input { flex: 0 0 auto; display: flex; gap: 8px; padding: 10px 0 14px; border-top: 1px solid var(--border, #e8e8e8); }
.req-chat-input input { min-width: 0; flex: 1; height: 34px; padding: 0 12px; border: 1px solid var(--border, #e8e8e8); border-radius: var(--r, 6px); outline: 0; background: var(--bg, #fff); color: var(--fg, #202124); font: 13px var(--sans, inherit); box-sizing: border-box; transition: all var(--t, 0.15s); }
.req-chat-input input::placeholder { color: var(--fg-4, #9ca3af); }
.req-chat-input input:focus { border-color: var(--accent, #52525b); box-shadow: 0 0 0 2px var(--accent-bg, rgba(82, 82, 91, 0.12)); }

/* ============ 新建需求弹窗 ============ */
.req-create-mask { position: fixed; inset: 0; z-index: 60; display: flex; align-items: center; justify-content: center; background: rgba(0, 0, 0, 0.3); }
.req-create-modal { width: min(480px, 92vw); display: flex; flex-direction: column; border: 1px solid var(--border, #e8e8e8); border-radius: 12px; background: var(--bg, #fff); box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18); }
.req-create-header { height: 48px; display: flex; align-items: center; justify-content: space-between; padding: 0 16px 0 20px; border-bottom: 1px solid var(--border, #e8e8e8); font-size: 14px; font-weight: 650; }
.req-create-body { padding: 18px 20px; display: grid; gap: 14px; }
.req-field { display: flex; flex-direction: column; gap: 6px; }
.req-field > span { font-size: 12px; color: var(--fg-3, #727987); }
.req-field > span em { color: #dc2626; font-style: normal; }
.req-field input, .req-field textarea { width: 100%; padding: 8px 12px; border: 1px solid var(--border, #e8e8e8); border-radius: var(--r, 6px); outline: 0; background: var(--bg, #fff); color: var(--fg, #202124); font: 13px var(--sans, inherit); box-sizing: border-box; transition: all var(--t, 0.15s); }
.req-field input:focus, .req-field textarea:focus { border-color: var(--accent, #52525b); box-shadow: 0 0 0 2px var(--accent-bg, rgba(82, 82, 91, 0.12)); }
.req-field input::placeholder, .req-field textarea::placeholder { color: var(--fg-4, #9ca3af); }
.req-field textarea { resize: vertical; min-height: 80px; line-height: 1.6; }
.req-option-icon { width: 14px; height: 14px; flex: 0 0 auto; color: var(--fg-3, #727987); }
.req-option-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 auto; }
.req-create-tip { margin: 0; font-size: 12px; color: var(--fg-4, #9ca3af); }
.req-create-footer { display: flex; justify-content: flex-end; gap: 8px; padding: 14px 20px; border-top: 1px solid var(--border, #e8e8e8); }

@media (max-width: 900px) {
  .req-detail-info { grid-template-columns: 1fr; }
}
</style>
