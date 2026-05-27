<template>
  <div class="app">
    <!-- Sidebar -->
    <aside class="sidebar" :class="{ collapsed: !sideOpen }">
      <div class="sb-header">
        <div class="sb-brand">
          <div class="sb-logo">⚡</div>
          <span class="sb-name">Agent4j</span>
        </div>
        <button class="sb-action" @click="newChat" title="新建对话">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        </button>
      </div>

      <div class="sb-search">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        <input v-model="searchQ" placeholder="搜索会话…" />
      </div>

      <div class="sb-list">
        <div class="sb-label">最近 <span class="sb-count">{{ filtered.length }}</span></div>
        <div v-for="s in filtered" :key="s.name"
          class="sb-item" :class="{ active: s.name === currentSession }"
          @click="loadSession(s.name)">
          <div class="sb-item-dot" />
          <div class="sb-item-body">
            <div class="sb-item-title">{{ s.summary || s.name.slice(0,24) }}</div>
            <div class="sb-item-meta">{{ s.messageCount || 0 }} 条 · {{ timeAgo(s.mtime) }}</div>
          </div>
          <button class="sb-item-x" @click.stop="deleteSession(s.name)">×</button>
        </div>
        <div v-if="!filtered.length" class="sb-empty">无会话</div>
      </div>

      <div class="sb-footer">
        <button class="sb-foot-btn" @click="showTools = true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
          工具
        </button>
        <button class="sb-foot-btn" @click="showConfig = true">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
          设置
        </button>
      </div>
    </aside>

    <!-- Main -->
    <div class="main">
      <!-- Header bar -->
      <div class="topbar">
        <button class="topbar-btn" @click="sideOpen = !sideOpen">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="18" x2="21" y2="18"/></svg>
        </button>
        <div class="topbar-title">
          <span>{{ currentSession || '新对话' }}</span>
          <span class="topbar-dot" :class="{ on: status.ready }" />
        </div>
        <div class="grow" />
        <div v-if="usage.totalTokens" class="topbar-pill">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
          {{ fmt(usage.totalTokens) }}
        </div>
        <button v-if="messages.length" class="topbar-btn" @click="clearChat" title="清空">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        </button>
      </div>

      <!-- Thread -->
      <div class="thread" ref="threadEl">
        <!-- Empty -->
        <div v-if="!messages.length && !busy" class="welcome">
          <div class="welcome-logo">⚡</div>
          <h1>Agent4j</h1>
          <p class="welcome-sub">AI 代码助手，随时为您服务</p>
          <div class="welcome-cards">
            <button class="wc" @click="quickSend('帮我分析这个项目的结构')">
              <span class="wc-icon">📂</span>
              <span class="wc-text">分析项目</span>
            </button>
            <button class="wc" @click="quickSend('有什么需要注意的安全问题吗？')">
              <span class="wc-icon">🔒</span>
              <span class="wc-text">安全检查</span>
            </button>
            <button class="wc" @click="quickSend('帮我优化这段代码的性能')">
              <span class="wc-icon">⚡</span>
              <span class="wc-text">性能优化</span>
            </button>
            <button class="wc" @click="quickSend('帮我写单元测试')">
              <span class="wc-icon">🧪</span>
              <span class="wc-text">编写测试</span>
            </button>
          </div>
        </div>

        <!-- Messages -->
        <template v-for="m in messages" :key="m.id">
          <div v-if="m.role === 'user'" class="msg-row user">
            <div class="msg-bubble">{{ m.content }}</div>
          </div>
          <div v-else-if="m.role === 'system'" class="msg-row system">
            <div class="msg-bubble system-bubble" v-html="renderMd(m.content)" />
          </div>
          <div v-else class="msg-row asst">
            <div class="msg-avatar">A</div>
            <div class="msg-content">
              <!-- Thinking -->
              <details v-if="m.thinking" class="think" :open="m.showThinking">
                <summary>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                  思考过程
                  <span class="think-len">{{ m.thinking.length }} 字</span>
                </summary>
                <div class="think-body">{{ m.thinking }}</div>
              </details>
              <!-- Tools -->
              <div v-for="(tc, i) in m.toolCalls" :key="i"
                class="tool-chip" :class="tc.status === '成功' ? 'ok' : ''">
                <div class="tc-top" @click="tc._open = !tc._open">
                  <span class="tc-status" :class="tc.status === '执行中' ? 'running' : 'done'">
                    <template v-if="tc.status === '执行中'"><span class="dot-spin" /></template>
                    <template v-else>✓</template>
                  </span>
                  <span class="tc-name">{{ tc.name }}</span>
                  <span class="grow" />
                  <span class="tc-arrow">{{ tc._open === false ? '›' : '‹' }}</span>
                </div>
                <div v-if="tc._open !== false" class="tc-detail">
                  <div v-if="tc.arguments" class="tc-field">
                    <span class="tc-label">参数</span>
                    <pre>{{ fmtArgs(tc.arguments) }}</pre>
                  </div>
                  <div v-if="tc.result" class="tc-field">
                    <span class="tc-label">结果</span>
                    <pre>{{ tc.result.length > 1500 ? tc.result.slice(0, 1500) + '\n…' : tc.result }}</pre>
                  </div>
                </div>
              </div>
              <!-- Text -->
              <div v-if="m.content" class="msg-text" v-html="renderMd(m.content)" />
              <!-- Usage -->
              <div v-if="m.usage" class="msg-meta">
                <span class="meta-pill">{{ fmt(m.usage.totalTokens) }} tokens</span>
                <span v-if="m.usage.cacheHit" class="meta-pill green">cache {{ fmt(m.usage.cacheHit) }}</span>
              </div>
            </div>
          </div>
        </template>

        <!-- Thinking indicator -->
        <div v-if="busy" class="msg-row asst">
          <div class="msg-avatar">A</div>
          <div class="msg-content">
            <div class="thinking-bar">
              <span class="dot-pulse" />
              <span>思考中…</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Composer -->
      <div class="composer-wrap">
        <div class="composer">
          <textarea
            ref="textareaEl"
            v-model="draft"
            :placeholder="busy ? '正在处理中，按 Esc 中断' : '输入消息…'"
            @keydown.enter.exact.prevent="send"
            @keydown.esc="abort"
            @input="autoGrow"
            rows="1"
          />
          <div class="comp-actions">
            <div class="comp-left">
              <button class="comp-mode" :class="{ on: planMode }" @click="togglePlan" :title="planMode ? '计划模式' : '自动模式'">
                {{ planMode ? '📋' : '⚡' }}
              </button>
            </div>
            <div class="comp-right">
              <span class="comp-hint">⌘↵</span>
              <button class="comp-send" @click="send" :disabled="!draft.trim() || busy">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>
              </button>
            </div>
          </div>
        </div>
        <div class="composer-foot">
          <span>Enter 发送 · Shift+Enter 换行</span>
          <span class="grow" />
          <span v-if="busy" class="comp-status">
            <span class="dot-pulse sm" /> 推理中
          </span>
        </div>
      </div>
    </div>

    <!-- Tools Modal -->
    <Teleport to="body">
      <div v-if="showTools" class="modal-mask" @click.self="showTools = false">
        <div class="modal">
          <div class="modal-head">
            <h3>工具列表</h3>
            <button class="modal-close" @click="showTools = false">×</button>
          </div>
          <div class="modal-body">
            <div v-for="t in tools" :key="t.name" class="modal-row">
              <span class="mr-name">{{ t.name }}</span>
              <span class="mr-desc">{{ t.description }}</span>
            </div>
            <div v-if="!tools.length" class="modal-empty">加载中…</div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Config Modal -->
    <Teleport to="body">
      <div v-if="showConfig" class="modal-mask" @click.self="showConfig = false">
        <div class="modal">
          <div class="modal-head">
            <h3>系统配置</h3>
            <button class="modal-close" @click="showConfig = false">×</button>
          </div>
          <div class="modal-body">
            <div v-for="(v, k) in config" :key="k" class="modal-row">
              <span class="mr-name">{{ k }}</span>
              <span class="mr-val">{{ v }}</span>
            </div>
            <div v-if="!Object.keys(config).length" class="modal-empty">加载中…</div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { chatAPI, agentAPI, sessionsAPI, toolsAPI, configAPI } from './services/api'

const messages = ref([])
const draft = ref('')
const busy = ref(false)
const sessions = ref([])
const searchQ = ref('')
const currentSession = ref('')
const status = ref({})
const usage = ref({})
const tools = ref([])
const config = ref({})
const showTools = ref(false)
const showConfig = ref(false)
const sideOpen = ref(true)
const planMode = ref(false)
const threadEl = ref(null)
const textareaEl = ref(null)
let abortCtrl = null

// Helpers
const filtered = ref([])
watch([sessions, searchQ], () => {
  const q = searchQ.value.toLowerCase()
  filtered.value = q ? sessions.value.filter(s => (s.summary || s.name).toLowerCase().includes(q)) : [...sessions.value]
}, { immediate: true })

const scroll = async () => { await nextTick(); if (threadEl.value) threadEl.value.scrollTop = threadEl.value.scrollHeight }
const autoGrow = (e) => { const el = e.target; el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 200) + 'px' }
const fmt = (n) => !n ? '0' : n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n)
const fmtArgs = (a) => typeof a === 'string' ? a : (() => { try { return JSON.stringify(a, null, 2) } catch { return String(a) } })()
const renderMd = (s) => s?.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>').replace(/`(.*?)`/g, '<code>$1</code>')
  .replace(/^- (.*)/gm, '• $1').replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
  .replace(/\n/g, '<br>') || ''
const timeAgo = (t) => { if (!t) return ''; const d = Date.now() - Date.parse(t); if (!Number.isFinite(d)) return ''; const m = d / 6e4; if (m < 1) return '刚刚'; if (m < 60) return Math.floor(m) + '分钟前'; const h = m / 60; if (h < 24) return Math.floor(h) + '小时前'; return Math.floor(h / 24) + '天前' }

// Send
const quickSend = (text) => { draft.value = text; send() }

const send = () => {
  let t = draft.value.trim()
  if (!t || busy.value) return

  // 所有消息（包括 / 命令）都走 doSend → 后端统一解析
  // 只有 /retry 和 /clear 需要本地预处理
  if (t.startsWith('/')) {
    const cmd = t.trim().split(/\s+/)[0].toLowerCase()
    if (cmd === '/retry') {
      handleRetryLocally()
      return
    }
    if (cmd === '/clear') {
      handleClearLocally()
      return
    }
    // /plan 和 /execute 同步本地 planMode 状态
    if (cmd === '/plan') {
      planMode.value = true
    } else if (cmd === '/execute') {
      planMode.value = false
    }
  }

  doSend(t)
}

const abort = () => { if (abortCtrl) { abortCtrl.abort(); abortCtrl = null }; busy.value = false }

// 本地处理 /retry
const handleRetryLocally = () => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') {
      const text = messages.value[i].content
      messages.value.splice(i)
      scroll()
      doSend(text)
      return
    }
  }
  addSystemMsg('没有可重试的消息。')
}

// 本地处理 /clear
const handleClearLocally = () => {
  if (!messages.value.length) return
  messages.value = []
  currentSession.value = ''
}

// 添加系统提示消息（不调用后端）
const addSystemMsg = (content) => {
  messages.value.push({
    id: Date.now(),
    role: 'system',
    content,
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })
  })
  scroll()
}

// 导出对话
const exportChat = () => {
  const chatText = messages.value.map(m => {
    const role = m.role === 'user' ? '用户' : m.role === 'assistant' ? '助手' : '系统'
    return `[${role}] ${m.content}`
  }).join('\n\n---\n\n')
  const blob = new Blob([chatText], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `agent4j-${new Date().toISOString().slice(0,10)}.md`; a.click()
  URL.revokeObjectURL(url)
}

const doSend = async (text) => {
  messages.value.push({ id: Date.now(), role: 'user', content: text })
  draft.value = ''; if (textareaEl.value) textareaEl.value.style.height = 'auto'
  await scroll(); busy.value = true
  const idx = messages.value.length
  messages.value.push({ id: Date.now() + 1, role: 'assistant', content: '', thinking: '', showThinking: true, toolCalls: [], usage: null })
  const get = () => messages.value[idx]
  abortCtrl = new AbortController()
  try {
    await chatAPI.sendMessageStream(text,
      (d) => {
        const msg = get(); if (!msg) return
        if (d.type === 'reasoning') { msg.thinking += (d.content || ''); msg.showThinking = true }
        else if (d.type === 'content') msg.content += (d.content || '')
        else if (d.type === 'tool_call') {
          let n = d.name || '', a = d.args || d.arguments || ''
          if (typeof a === 'string') try { a = JSON.parse(a) } catch {}
          if (!n && d.content) try { const p = JSON.parse(d.content); n = p.name || '' } catch {}
          msg.toolCalls.push({ name: n || 'unknown', status: '执行中', arguments: a, result: '', _open: false })
        }
        else if (d.type === 'tool_result' && msg.toolCalls.length) {
          const last = msg.toolCalls[msg.toolCalls.length - 1]
          let r = d.result || d.content || ''; last.result = typeof r === 'string' ? r : JSON.stringify(r, null, 2); last.status = '成功'
        }
        else if (d.type === 'usage') { msg.usage = d; usage.value = d }
        else if (d.type === 'error') msg.content = `❌ ${d.error || '未知错误'}`
        else if (d.type === 'reply' && !msg.content && d.content) msg.content = d.content
        scroll()
      },
      () => { busy.value = false; abortCtrl = null; loadSessions() },
      () => { busy.value = false; abortCtrl = null }
    )
  } catch { busy.value = false }
  scroll()
}

// Actions
const clearChat = async () => {
  if (messages.value.length && !confirm('清空对话？')) return
  // 走消息解析：发送 /new 命令
  messages.value = []
  currentSession.value = ''
  draft.value = '/new'
  send()
}
const togglePlan = async () => {
  // 翻转本地状态后，发送命令消息让后端处理
  planMode.value = !planMode.value
  draft.value = planMode.value ? '/plan' : '/execute'
  send()
}

// Sessions
const loadSessions = async () => { try { const r = await sessionsAPI.list(); if (r.success) sessions.value = r.data || [] } catch {} }
const newChat = async () => {
  // 走消息解析：发送 /new 命令
  messages.value = []
  currentSession.value = ''
  draft.value = '/new'
  send()
}
const loadSession = async (name) => {
  try {
    await sessionsAPI.switchSession(name); currentSession.value = name; messages.value = []
    const r = await agentAPI.getHistory()
    if (r.success && r.data) {
      const tr = {}; for (const m of r.data) if (m.role === 'tool' && m.tool_call_id) tr[m.tool_call_id] = m.content || ''
      for (const m of r.data) {
        if (m.role === 'tool') continue
        const item = { id: Date.now() + messages.value.length, role: m.role, content: m.content || '', thinking: m.reasoning_content || '', showThinking: false, toolCalls: [], usage: null }
        if (m.tool_calls?.length) item.toolCalls = m.tool_calls.map(tc => {
          let n = tc.function?.name || tc.name || '', a = tc.function?.arguments || tc.arguments || ''
          if (typeof a === 'string') try { a = JSON.parse(a) } catch {}
          return { name: n, status: tr[tc.id] ? '成功' : '执行中', arguments: a, result: tr[tc.id] || '', _open: false }
        })
        messages.value.push(item)
      }
    }
  } catch {}
}
const deleteSession = async (name) => { if (!confirm(`删除 ${name}？`)) return; try { await sessionsAPI.deleteSession(name); await loadSessions() } catch {} }

// Init
onMounted(async () => {
  try {
    const s = await agentAPI.getStatus(); if (s.success) status.value = s.data || {}
    const c = await sessionsAPI.getCurrent(); if (c.success && c.data?.name) currentSession.value = c.data.name
    const t = await toolsAPI.list(); if (t.success) tools.value = t.data || []
    const cf = await configAPI.getConfig(); if (cf.success) config.value = cf.data || {}
    const u = await configAPI.getUsage(); if (u.success) usage.value = u.data || {}
  } catch {}
  await loadSessions()
})
watch(messages, () => scroll(), { deep: true })
</script>

<style scoped>
.app { display: flex; height: 100%; background: var(--bg-2); }

/* ===== Sidebar ===== */
.sidebar {
  width: 260px; flex-shrink: 0; background: var(--bg);
  border-right: 1px solid var(--border);
  display: flex; flex-direction: column;
  transition: width var(--transition), opacity var(--transition);
  box-shadow: 1px 0 0 var(--border);
}
.sidebar.collapsed { width: 0; opacity: 0; pointer-events: none; overflow: hidden; }

.sb-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 16px 12px; }
.sb-brand { display: flex; align-items: center; gap: 10px; }
.sb-logo { font-size: 22px; }
.sb-name { font-size: 17px; font-weight: 600; letter-spacing: -0.01em; color: var(--fg); }
.sb-action {
  width: 32px; height: 32px; border-radius: var(--r-sm);
  display: flex; align-items: center; justify-content: center;
  color: var(--accent); background: var(--accent-soft);
  transition: background var(--transition);
}
.sb-action:hover { background: var(--accent); color: #fff; }

.sb-search {
  display: flex; align-items: center; gap: 8px;
  margin: 0 12px 8px; padding: 8px 12px; border-radius: var(--r-sm);
  background: var(--bg-2); color: var(--muted);
}
.sb-search input { flex: 1; background: none; border: none; outline: none; font-size: 13px; }
.sb-search input::placeholder { color: var(--muted-2); }

.sb-list { flex: 1; overflow-y: auto; padding: 4px 8px; }
.sb-label { padding: 8px 8px 4px; font-size: 11px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.05em; }
.sb-count { color: var(--muted-2); font-weight: 400; }
.sb-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 10px;
  border-radius: var(--r-sm); cursor: pointer; transition: background var(--transition);
}
.sb-item:hover { background: var(--bg-2); }
.sb-item.active { background: var(--accent-soft); }
.sb-item-dot { width: 8px; height: 8px; border-radius: 2px; background: var(--border-s); flex-shrink: 0; }
.sb-item.active .sb-item-dot { background: var(--accent); }
.sb-item-body { flex: 1; min-width: 0; }
.sb-item-title { font-size: 13px; font-weight: 500; color: var(--fg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sb-item-meta { font-size: 11px; color: var(--muted); margin-top: 1px; }
.sb-item-x { width: 20px; height: 20px; border-radius: 6px; display: flex; align-items: center; justify-content: center; color: var(--muted-2); opacity: 0; font-size: 16px; transition: opacity var(--transition); }
.sb-item:hover .sb-item-x { opacity: 1; }
.sb-item-x:hover { background: var(--red-soft); color: var(--red); }
.sb-empty { padding: 20px; text-align: center; color: var(--muted-2); font-size: 13px; }

.sb-footer { padding: 12px; border-top: 1px solid var(--border); display: flex; gap: 4px; }
.sb-foot-btn {
  flex: 1; display: flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px; border-radius: var(--r-sm); font-size: 12px; color: var(--muted);
  transition: all var(--transition);
}
.sb-foot-btn:hover { background: var(--bg-2); color: var(--fg); }

/* ===== Main ===== */
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; background: var(--bg); overflow: hidden; }

/* Topbar */
.topbar {
  display: flex; align-items: center; gap: 10px; padding: 0 20px; height: 52px;
  border-bottom: 1px solid var(--border); flex-shrink: 0;
}
.topbar-btn {
  width: 32px; height: 32px; border-radius: var(--r-sm);
  display: flex; align-items: center; justify-content: center;
  color: var(--muted); transition: all var(--transition);
}
.topbar-btn:hover { background: var(--bg-2); color: var(--fg); }
.topbar-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; }
.topbar-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--muted-2); }
.topbar-dot.on { background: var(--green); }
.grow { flex: 1; }
.topbar-pill {
  display: flex; align-items: center; gap: 5px; padding: 4px 10px;
  border-radius: var(--r-sm); font-size: 12px; font-weight: 500;
  background: var(--accent-soft); color: var(--accent);
}

/* Thread */
.thread { flex: 1; overflow-y: auto; padding: 24px 0 40px; scroll-padding-bottom: 24px; }
.thread > * { max-width: 740px; margin-left: auto; margin-right: auto; padding: 0 28px; }

/* Welcome */
.welcome { display: flex; flex-direction: column; align-items: center; padding: 80px 20px 40px; text-align: center; }
.welcome-logo { font-size: 56px; margin-bottom: 16px; }
.welcome h1 { font-size: 28px; font-weight: 700; letter-spacing: -0.03em; margin-bottom: 6px; }
.welcome-sub { color: var(--muted); font-size: 16px; margin-bottom: 32px; }
.welcome-cards { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; width: 100%; max-width: 420px; }
.wc {
  display: flex; align-items: center; gap: 10px; padding: 14px 16px;
  border-radius: var(--r); background: var(--bg-2); border: 1px solid var(--border);
  font-size: 13px; font-weight: 500; color: var(--fg); text-align: left;
  transition: all var(--transition);
}
.wc:hover { background: var(--accent-soft); border-color: var(--accent); color: var(--accent); }
.wc-icon { font-size: 20px; }

/* Messages */
.msg-row { display: flex; gap: 10px; margin-bottom: 24px; }
.msg-row.user { justify-content: flex-end; }
.msg-row.system { justify-content: center; }
.msg-bubble {
  max-width: 75%; padding: 12px 16px; background: var(--accent); color: #fff;
  border-radius: var(--r) var(--r) var(--r) 2px; font-size: 14px; line-height: 1.6;
  white-space: pre-wrap; box-shadow: var(--shadow-sm);
}
.system-bubble {
  background: var(--bg-2); color: var(--fg-2); font-size: 13px;
  text-align: center; border: 1px dashed var(--border-strong);
  max-width: 85%;
}
.msg-avatar {
  width: 32px; height: 32px; border-radius: var(--r-sm);
  background: var(--accent); color: #fff;
  font-size: 13px; font-weight: 600;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.msg-content { flex: 1; min-width: 0; }
.msg-text { font-size: 14px; line-height: 1.72; color: var(--fg); }
.msg-text :deep(code) { font-family: var(--mono); background: var(--bg-2); border: 1px solid var(--border); padding: 2px 6px; border-radius: 5px; font-size: .88em; }
.msg-text :deep(pre) { margin: 12px 0; }
.msg-text :deep(strong) { font-weight: 600; }
.msg-meta { display: flex; gap: 6px; margin-top: 6px; }
.meta-pill { font-size: 11px; padding: 3px 10px; border-radius: var(--r-sm); background: var(--bg-2); color: var(--muted); }
.meta-pill.green { background: var(--green-soft); color: var(--green); }

/* Thinking */
.think { border: 1px solid var(--border); border-radius: var(--r); overflow: hidden; margin-bottom: 10px; }
.think summary {
  display: flex; align-items: center; gap: 6px; padding: 10px 14px;
  font-size: 12px; color: var(--muted); cursor: pointer; user-select: none;
  background: var(--bg-2); transition: color var(--transition);
}
.think summary:hover { color: var(--fg-2); }
.think-len { margin-left: auto; font-family: var(--mono); font-size: 11px; color: var(--muted-2); }
.think-body {
  padding: 12px 16px; font-size: 13px; line-height: 1.7; color: var(--fg-2);
  font-style: italic; border-top: 1px solid var(--border);
  white-space: pre-wrap; max-height: 300px; overflow-y: auto;
}

/* Thinking bar */
.thinking-bar {
  display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px;
  background: var(--bg-2); border-radius: var(--r); font-size: 13px; color: var(--muted);
  border: 1px solid var(--border);
}

/* Tool chip */
.tool-chip { border: 1px solid var(--border); border-radius: var(--r); overflow: hidden; margin-bottom: 8px; font-size: 13px; }
.tool-chip.ok { border-color: rgba(52,199,89,.25); }
.tc-top {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  background: var(--bg-2); cursor: pointer; user-select: none;
}
.tc-top:hover { background: var(--bg-3); }
.tc-status { font-size: 11px; width: 18px; height: 18px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.tc-status.done { background: var(--green-soft); color: var(--green); font-size: 10px; }
.tc-status.running { background: var(--accent-soft); }
.tc-name { font-family: var(--mono); font-size: 12px; font-weight: 500; color: var(--fg); }
.tc-arrow { color: var(--muted-2); font-size: 14px; }
.tc-detail { border-top: 1px solid var(--border); }
.tc-field { display: flex; border-bottom: 1px solid var(--border); }
.tc-field:last-child { border-bottom: none; }
.tc-label { width: 56px; padding: 8px 10px; font-size: 11px; font-weight: 500; color: var(--muted); background: var(--bg-2); flex-shrink: 0; }
.tc-field pre { flex: 1; margin: 0; padding: 8px 10px; font-size: 12px; border: none; border-radius: 0; background: transparent; max-height: 180px; overflow-y: auto; }

/* Dot animations */
.dot-spin { display: inline-block; width: 10px; height: 10px; border: 2px solid var(--accent); border-top-color: transparent; border-radius: 2px; animation: spin .6s linear infinite; }
.dot-pulse { display: inline-block; width: 8px; height: 8px; border-radius: 2px; background: var(--accent); animation: pulse 1.4s ease-in-out infinite; }
.dot-pulse.sm { width: 6px; height: 6px; }
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes pulse { 0%, 100% { opacity: .3; } 50% { opacity: 1; } }

/* ===== Composer ===== */
.composer-wrap { padding: 0 28px 16px; flex-shrink: 0; }
.composer {
  max-width: 740px; margin: 0 auto; background: var(--bg);
  border: 1px solid var(--border-s); border-radius: var(--r);
  box-shadow: var(--shadow-md); overflow: hidden;
  transition: border-color var(--transition), box-shadow var(--transition);
}
.composer:focus-within { border-color: var(--accent); box-shadow: var(--shadow-md), 0 0 0 3px var(--accent-soft); }
.composer textarea {
  display: block; width: 100%; border: none; outline: none; background: none;
  padding: 14px 16px 4px; font-size: 15px; line-height: 1.5;
  min-height: 28px; max-height: 200px;
}
.composer textarea::placeholder { color: var(--muted-2); }
.comp-actions { display: flex; align-items: center; padding: 4px 10px 10px; }
.comp-left { display: flex; gap: 4px; }
.comp-mode {
  width: 32px; height: 32px; border-radius: var(--r-sm);
  display: flex; align-items: center; justify-content: center;
  font-size: 15px; color: var(--muted); transition: all var(--transition);
}
.comp-mode:hover { background: var(--bg-2); }
.comp-mode.on { background: var(--accent-soft); color: var(--accent); }
.comp-right { flex: 1; display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.comp-hint { font-size: 12px; color: var(--muted-2); }
.comp-send {
  width: 32px; height: 32px; border-radius: var(--r);
  background: var(--accent); color: #fff;
  display: flex; align-items: center; justify-content: center;
  transition: all var(--transition);
}
.comp-send:hover { filter: brightness(1.1); }
.comp-send:disabled { background: var(--bg-3); color: var(--muted-2); box-shadow: none; cursor: not-allowed; transform: none; }
.composer-foot { max-width: 740px; margin: 6px auto 0; display: flex; padding: 0 4px; font-size: 11px; color: var(--muted-2); }
.comp-status { display: flex; align-items: center; gap: 5px; color: var(--accent); }

/* ===== Modals ===== */
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,.3); backdrop-filter: blur(4px); z-index: 200; display: flex; align-items: center; justify-content: center; }
.modal {
  width: min(520px, 92vw); max-height: 75vh; background: var(--bg);
  border-radius: var(--r); box-shadow: var(--shadow-lg);
  display: flex; flex-direction: column; overflow: hidden;
}
.modal-head { display: flex; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--border); }
.modal-head h3 { flex: 1; font-size: 16px; font-weight: 600; }
.modal-close {
  width: 24px; height: 24px; border-radius: var(--r-sm); display: flex; align-items: center; justify-content: center;
  font-size: 16px; color: var(--muted); transition: all var(--transition);
}
.modal-close:hover { background: var(--bg-3); color: var(--fg); }
.modal-body { flex: 1; overflow-y: auto; padding: 12px 20px; }
.modal-row { padding: 10px 0; border-bottom: 1px solid var(--border); }
.modal-row:last-child { border-bottom: none; }
.mr-name { font-family: var(--mono); font-size: 13px; font-weight: 500; color: var(--accent); }
.mr-desc { display: block; font-size: 12px; color: var(--muted); margin-top: 2px; line-height: 1.4; }
.mr-val { font-size: 13px; color: var(--fg); word-break: break-all; }
.modal-empty { padding: 24px; text-align: center; color: var(--muted-2); font-size: 13px; }
</style>
