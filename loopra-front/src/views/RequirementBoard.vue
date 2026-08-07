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
                <span v-if="item.projectName" class="req-project-badge" :title="`项目：${item.projectName}`">{{ item.projectName }}</span>
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
          <button v-if="deleteArmed" type="button" class="req-detail-close req-delete-confirm" title="再次点击确认删除" @click="requestDelete">确认删除？</button>
          <button v-else type="button" class="req-detail-close" title="删除需求" @click="requestDelete">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>
          </button>
          <button type="button" class="req-detail-close" title="关闭" @click="closeDetail">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 6 12 12M18 6 6 18"/></svg>
          </button>
      </header>

      <div class="req-detail-main">
        <!-- 信息区：描述 + AI 执行 -->
        <section class="req-detail-info">
          <div class="req-info-card req-info-desc">
            <h3>描述 <span v-if="selected.projectName" class="req-info-project">项目：{{ selected.projectName }}</span></h3>
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
                  @click="runRequirement"
                >
                  <svg v-if="aiRunning" class="req-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>
                  <template v-else>让 AI 执行</template>
                </button>
                <button
                  v-else-if="selected.status === 'doing'"
                  type="button"
                  class="req-btn req-btn-sm req-btn-danger"
                  :disabled="aiRunning"
                  @click="abortRequirement"
                >取消执行</button>
                <button
                  v-else
                  type="button"
                  class="req-btn req-btn-sm"
                  :disabled="aiRunning"
                  @click="runRequirement"
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
          >评论 ({{ commentCount }})</button>
          <button
            type="button"
            class="req-detail-tab"
            :class="{ active: detailTab === 'logs' }"
            @click="switchTab('logs')"
          >执行日志 ({{ logCount }})</button>
        </div>

        <!-- 执行日志：复用聊天框组件（ChatMessage / BlockRenderer） -->
        <div v-if="detailTab === 'logs'" class="req-chat-area">
          <div ref="chatMessagesRef" class="req-chat-messages">
            <ChatMessage
              v-for="(m, i) in logMessages"
              :key="m.id"
              :idx="i"
              :msg="m"
              :workspace-path="''"
              :snapshot-rollback-loading="snapshotRollbackLoading"
              :rollback-disabled="true"
              :branch-disabled="true"
              @copy-message="copyMessage"
            />
            <div v-if="!logMessages.length" class="req-chat-empty">暂无执行日志</div>
          </div>
        </div>

        <!-- 评论：看板系统风格（头像 + 作者 + 相对时间 + 评论条目） -->
        <div v-else class="req-comments">
          <div ref="commentListRef" class="req-comment-list">
            <div v-for="item in commentItems" :key="item.id" class="req-comment" :class="`req-comment-${item.role}`">
              <div class="req-comment-head">
                <span class="req-comment-avatar" :class="{ ai: item.role === 'assistant' }">
                  <svg v-if="item.role === 'assistant'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="4" y="8" width="16" height="12" rx="2"/><path d="M12 4v4M9 13h.01M15 13h.01M9 17h6"/></svg>
                  <span v-else>{{ commentAuthor(item).charAt(0) }}</span>
                </span>
                <span class="req-comment-author">{{ commentAuthor(item) }}</span>
                <span class="req-comment-time">{{ fmtRelative(item.timestamp) }}</span>
              </div>
              <p class="req-comment-text">{{ item.content }}</p>
            </div>
            <div v-if="!commentItems.length" class="req-comments-empty">暂无评论，来抢沙发～</div>
          </div>
          <form class="req-comment-form" @submit.prevent="addComment">
            <textarea
              v-model="commentDraft"
              rows="2"
              placeholder="写下你的评论…（Enter 发送，Shift+Enter 换行）"
              maxlength="500"
              @keydown.enter.exact.prevent="addComment"
            ></textarea>
            <div class="req-comment-form-foot">
              <button type="submit" class="req-btn req-btn-sm req-btn-primary" :disabled="!commentDraft.trim()">发送评论</button>
            </div>
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
import {configAPI, requirementAPI} from '../services/api'

// ============ 常量 ============
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

// 需求数据由后端 RequirementStore 权威管理（见 docs/requirement-board-ai-design.md）

// ============ 状态 ============
const requirements = ref([])
const selected = ref(null)
const detailTab = ref('comments')
const commentDraft = ref('')
const createOpen = ref(false)
const projects = ref([])
const draft = reactive({ title: '', description: '', priority: 'medium', projectHash: '' })
const theme = ref('gray')
const loading = ref(false)
// 需求专属会话的消息流（评论 + 执行日志，来自后端）
const messages = ref([])
// AI 操作请求中的 loading 态（执行状态以后端为准，轮询刷新）
const aiRunning = ref(false)
const chatMessagesRef = ref(null)
const commentListRef = ref(null)
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

// 聊天消息流：从后端会话消息派生（复用 ChatMessage 渲染）
// 日志 tab → assistant 消息（执行过程）；评论 tab → user 消息 + 紧跟其后的 assistant 消息（AI 回复）
// 消息流数据源：
// 日志 tab → logMessages（assistant 执行过程，复用 ChatMessage 聊天组件）
// 评论 tab → commentItems（看板风格评论条目：user 消息 + 紧跟其后的 AI 回复）
const logMessages = computed(() => {
  if (!selected.value) return []
  return messages.value
    .filter((m) => m.role === 'assistant' && m.content)
    .map((m) => ({
      id: m.id || `assistant_${m.timestamp}`,
      role: 'assistant',
      time: fmtTime(m.timestamp),
      blocks: [{ type: 'content', content: m.content }]
    }))
})
const commentItems = computed(() => {
  if (!selected.value) return []
  const items = []
  for (let i = 0; i < messages.value.length; i++) {
    const message = messages.value[i]
    if (message.role !== 'user' || !message.content) continue
    items.push({ id: message.id || `user_${message.timestamp}`, role: 'user', content: message.content, timestamp: message.timestamp })
    const next = messages.value[i + 1]
    if (next && next.role === 'assistant' && next.content) {
      items.push({ id: next.id || `assistant_${next.timestamp}`, role: 'assistant', content: next.content, timestamp: next.timestamp })
      i++
    }
  }
  return items
})
const commentAuthor = (item) => (item.role === 'assistant' ? 'AI 执行 Agent' : '我')
const commentCount = computed(() => messages.value.filter((m) => m.role === 'user' && m.content).length)
const logCount = computed(() => messages.value.filter((m) => m.role === 'assistant' && m.content).length)

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

// 看板评论风格：相对时间（刚刚 / N 分钟前 / N 小时前 / N 天前 / 日期）
function fmtRelative(timestamp) {
  if (!timestamp) return ''
  const diff = Date.now() - timestamp
  if (diff < 60 * 1000) return '刚刚'
  if (diff < 60 * 60 * 1000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / 3600000)} 小时前`
  if (diff < 7 * 24 * 60 * 60 * 1000) return `${Math.floor(diff / 86400000)} 天前`
  const date = new Date(timestamp)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

// ============ 数据加载（后端权威） ============
async function loadFromAPI() {
  loading.value = true
  try {
    const res = await requirementAPI.list()
    if (res?.success && Array.isArray(res.data)) {
      requirements.value = res.data
    }
  } catch (error) {
    console.warn('[requirement-board] 加载需求失败:', error)
  } finally {
    loading.value = false
  }
}

async function loadMessages() {
  if (!selected.value) return
  try {
    const res = await requirementAPI.getMessages(selected.value.id)
    if (res?.success && Array.isArray(res.data)) {
      messages.value = res.data
      scrollChatToBottom()
    }
  } catch (error) {
    console.warn('[requirement-board] 加载需求消息失败:', error)
    messages.value = []
  }
}

// ============ 交互 ============
function openDetail(item) {
  selected.value = item
  detailTab.value = 'comments'
  messages.value = []
  loadMessages()
}
function closeDetail() {
  selected.value = null
  detailTab.value = 'comments'
  commentDraft.value = ''
  messages.value = []
}

function switchTab(tab) {
  detailTab.value = tab
  scrollChatToBottom()
}

// 切换 tab / 新增消息后滚动到当前展示区底部（日志聊天流 / 评论列表）
function scrollChatToBottom() {
  nextTick(() => {
    const el = detailTab.value === 'logs' ? chatMessagesRef.value : commentListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// 让 AI 执行：调后端 /run（RequirementExecutor 驱动真实流转），状态由后端权威维护
async function runRequirement() {
  const item = selected.value
  if (!item || aiRunning.value) return
  aiRunning.value = true
  try {
    const res = await requirementAPI.run(item.id)
    if (res?.success) {
      item.status = 'doing' // 立即反馈，轮询兜底
      item.updatedAt = Date.now()
    }
  } catch (error) {
    console.warn('[requirement-board] 触发执行失败:', error)
  } finally {
    aiRunning.value = false
  }
}

// 人工取消执行：中断会话并回退 todo
async function abortRequirement() {
  const item = selected.value
  if (!item || aiRunning.value) return
  aiRunning.value = true
  try {
    const res = await requirementAPI.abort(item.id)
    if (res?.success) {
      item.status = 'todo'
      item.updatedAt = Date.now()
    }
  } catch (error) {
    console.warn('[requirement-board] 取消执行失败:', error)
  } finally {
    aiRunning.value = false
  }
}

// 删除需求（二次点击确认，3 秒内未确认自动解除）
const deleteArmed = ref(false)
let deleteArmTimer = null
async function requestDelete() {
  const item = selected.value
  if (!item) return
  if (!deleteArmed.value) {
    deleteArmed.value = true
    deleteArmTimer = setTimeout(() => { deleteArmed.value = false }, 3000)
    return
  }
  clearTimeout(deleteArmTimer)
  deleteArmed.value = false
  try {
    const res = await requirementAPI.delete(item.id)
    if (res?.success) {
      closeDetail()
      await loadFromAPI()
    }
  } catch (error) {
    console.warn('[requirement-board] 删除需求失败:', error)
  }
}

async function addComment() {
  const text = commentDraft.value.trim()
  if (!text || !selected.value) return
  try {
    const res = await requirementAPI.addComment(selected.value.id, text)
    if (res?.success) {
      commentDraft.value = ''
      await loadMessages()
    }
  } catch (error) {
    console.warn('[requirement-board] 提交评论失败:', error)
  }
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

async function createRequirement() {
  const title = draft.title.trim()
  if (!title) return
  // 新建需求必须选择项目
  const project = projects.value.find((item) => item.hash === draft.projectHash)
  if (!project) return
  try {
    const res = await requirementAPI.create({
      title,
      description: draft.description.trim(),
      priority: draft.priority,
      projectHash: project.hash,
      projectName: project.name
    })
    if (res?.success && res.data) {
      createOpen.value = false
      await loadFromAPI()
    }
  } catch (error) {
    console.warn('[requirement-board] 创建需求失败:', error)
  }
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

// ============ 轮询刷新 ============
const POLL_INTERVAL = 5000
let pollTimer = null
let aiTimer = null

// 周期刷新：看板列表 + 执行中需求的消息流（日志增量）
async function poll() {
  await loadFromAPI()
  if (selected.value?.status === 'doing') {
    await loadMessages()
  }
}
function startPolling() {
  stopPolling()
  pollTimer = setInterval(poll, POLL_INTERVAL)
}
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

watch([logMessages, commentItems], scrollChatToBottom)

onMounted(() => {
  // 独立窗口主题：与主应用保持一致（localStorage 同步）
  theme.value = localStorage.getItem('loopra-theme') || 'gray'
  document.documentElement.setAttribute('data-theme', theme.value)
  loadFromAPI()
  loadProjects()
  startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
  if (aiTimer) clearTimeout(aiTimer)
  if (deleteArmTimer) clearTimeout(deleteArmTimer)
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
.req-delete-confirm { padding: 0 10px; color: #dc2626; font-size: 12px; }
.req-delete-confirm:hover { color: #b42318; background: rgba(220, 38, 38, 0.1); }

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

/* 执行日志聊天区（复用聊天框组件渲染） */
.req-chat-area { min-height: 0; flex: 1; display: flex; flex-direction: column; }
.req-chat-messages { min-height: 0; flex: 1; overflow-y: auto; padding: 12px 4px 4px; }
.req-chat-messages::-webkit-scrollbar { width: 6px; }
.req-chat-messages::-webkit-scrollbar-thumb { background: rgba(80, 88, 102, 0.35); border-radius: 6px; }
.req-chat-empty { padding: 32px 0; text-align: center; color: var(--fg-4, #9ca3af); font-size: 12px; }

/* 评论（看板系统风格：头像 + 作者 + 相对时间 + 评论条目） */
.req-comments { min-height: 0; flex: 1; display: flex; flex-direction: column; }
.req-comment-list { min-height: 0; flex: 1; overflow-y: auto; padding: 14px 0; display: grid; gap: 10px; align-content: start; }
.req-comment-list::-webkit-scrollbar { width: 6px; }
.req-comment-list::-webkit-scrollbar-thumb { background: rgba(80, 88, 102, 0.35); border-radius: 6px; }
.req-comment { padding: 10px 12px; border: 1px solid var(--border, #e8e8e8); border-radius: 8px; background: var(--bg-2, #f9fafb); }
.req-comment-head { display: flex; align-items: center; gap: 8px; }
.req-comment-avatar { width: 22px; height: 22px; display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; border-radius: 50%; background: var(--bg-3, #f3f4f6); color: var(--fg-3, #727987); font-size: 11px; font-weight: 700; }
.req-comment-avatar svg { width: 12px; height: 12px; }
.req-comment-avatar.ai { background: var(--accent-bg, rgba(82, 82, 91, 0.12)); color: var(--fg-2, #525866); }
.req-comment-author { font-size: 12px; font-weight: 600; color: var(--fg, #202124); }
.req-comment-time { margin-left: auto; font-size: 11px; color: var(--fg-4, #9ca3af); }
.req-comment-text { margin: 8px 0 0; font-size: 13px; line-height: 1.65; color: var(--fg-2, #525866); white-space: pre-wrap; word-break: break-word; }
.req-comments-empty { padding: 32px 0; text-align: center; color: var(--fg-4, #9ca3af); font-size: 12px; }
.req-comment-form { flex: 0 0 auto; display: flex; flex-direction: column; gap: 8px; padding-top: 12px; border-top: 1px solid var(--border, #e8e8e8); }
.req-comment-form textarea { width: 100%; padding: 8px 12px; border: 1px solid var(--border, #e8e8e8); border-radius: var(--r, 6px); outline: 0; background: var(--bg, #fff); color: var(--fg, #202124); font: 13px var(--sans, inherit); box-sizing: border-box; resize: vertical; min-height: 56px; line-height: 1.6; transition: all var(--t, 0.15s); }
.req-comment-form textarea::placeholder { color: var(--fg-4, #9ca3af); }
.req-comment-form textarea:focus { border-color: var(--accent, #52525b); box-shadow: 0 0 0 2px var(--accent-bg, rgba(82, 82, 91, 0.12)); }
.req-comment-form-foot { display: flex; justify-content: flex-end; }

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
