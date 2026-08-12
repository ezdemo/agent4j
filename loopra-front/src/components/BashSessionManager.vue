<template>
  <div ref="rootRef" class="bash-manager" :class="{ 'opens-up': placement === 'top', embedded }">
    <button
      v-if="!embedded"
      class="tb-bash-btn"
      :class="{ active: open }"
      title="后台进程"
      @click.stop="toggle"
      @dblclick.stop
    >
      <CodeOutlined />
      <span v-if="runningCount" class="bash-live-dot"></span>
    </button>

    <section v-show="embedded || open" class="bash-popover" @click.stop @dblclick.stop>
      <header class="bash-header">
        <div>
          <h3>后台进程</h3>
          <p>{{ summary }}</p>
        </div>
        <button class="icon-btn" title="刷新进程列表" :disabled="loading" @click="refresh">
          <ReloadOutlined :class="{ spinning: loading }" />
        </button>
      </header>

      <div v-if="error" class="bash-error">{{ error }}</div>

      <div v-if="loading && !sessions.length" class="bash-empty">
        <LoadingOutlined class="spinning" />
        <span>正在读取后台进程</span>
      </div>

      <div v-else-if="!sessions.length" class="bash-empty">
        <CloudServerOutlined />
        <span>暂无后台进程</span>
      </div>

      <div v-else class="bash-list">
        <article v-for="item in sessions" :key="item.sessionId" class="bash-row">
          <div class="bash-status"><span :class="{ done: item.status === 'completed' }"></span></div>
          <div class="bash-main">
            <div class="bash-title">
              <strong
                class="bash-copy"
                :class="{ copied: copiedKey === item.sessionId + ':command' }"
                :title="copiedKey === item.sessionId + ':command' ? '已复制' : '点击复制命令'"
                @click.stop="copyText(item.command, item.sessionId + ':command')"
              >{{ item.command }}</strong>
              <span class="bash-state" :class="{ done: item.status === 'completed' }">
                {{ item.status === 'running' ? '运行中' : '已结束' }}
              </span>
            </div>
            <div class="bash-meta">
              <span
                v-if="item.workspace"
                class="bash-workspace bash-copy"
                :class="{ copied: copiedKey === item.sessionId + ':workspace' }"
                :title="copiedKey === item.sessionId + ':workspace' ? '已复制' : '点击复制路径'"
                @click.stop="copyText(item.workspace, item.sessionId + ':workspace')"
              >{{ workspaceName(item.workspace) }}</span>
              <span>{{ formatUptime(item) }}</span>
            </div>
            <div
              v-if="item.workdir"
              class="bash-workdir bash-copy"
              :class="{ copied: copiedKey === item.sessionId + ':workdir' }"
              :title="copiedKey === item.sessionId + ':workdir' ? '已复制' : '点击复制工作目录'"
              @click.stop="copyText(item.workdir, item.sessionId + ':workdir')"
            >{{ item.workdir }}</div>
          </div>
          <div class="bash-actions">
            <button
              v-if="item.status === 'running'"
              class="icon-btn danger"
              :disabled="busySessionId === item.sessionId"
              :title="'关闭进程 ' + item.sessionId"
              @click.stop="terminate(item)"
            >
              <LoadingOutlined v-if="busySessionId === item.sessionId" />
              <CloseOutlined v-else />
            </button>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {
  CloseOutlined,
  CloudServerOutlined,
  CodeOutlined,
  LoadingOutlined,
  ReloadOutlined
} from '@ant-design/icons-vue'
import {agentAPI} from '@/services/api'

const props = defineProps({
  placement: { type: String, default: 'bottom' },
  embedded: { type: Boolean, default: false }
})

const rootRef = ref(null)
const open = ref(false)
const loading = ref(false)
const sessions = ref([])
const error = ref('')
let refreshTimer = null

const runningCount = computed(() => sessions.value.filter(s => s.status === 'running').length)
const summary = computed(() => {
  if (!sessions.value.length) return '暂无后台进程'
  return `${runningCount.value} 个运行中 · 共 ${sessions.value.length} 个进程`
})

const copiedKey = ref('')
let copyTipTimer = null

async function copyText(text, key) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    // 剪贴板 API 不可用时退回 execCommand
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
  }
  copiedKey.value = key
  // 走全局 log 提示框（Chat.vue 监听 copy-success 显示 ✅ 复制成功）
  window.dispatchEvent(new CustomEvent('copy-success', {detail: '复制成功'}))
  clearTimeout(copyTipTimer)
  copyTipTimer = setTimeout(() => {
    if (copiedKey.value === key) copiedKey.value = ''
  }, 1500)
}

function formatDuration(seconds) {
  if (seconds < 60) return `${Math.max(0, Math.floor(seconds))} 秒`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小时`
  return `${Math.floor(seconds / 86400)} 天`
}

function formatUptime(item) {
  if (item.status === 'completed') return '已结束'
  const seconds = (Date.now() - Number(item.startedAt)) / 1000
  if (Number.isFinite(seconds)) return `已运行 ${formatDuration(seconds)}`
  return '启动时间未知'
}

function workspaceName(path) {
  if (!path) return ''
  const parts = String(path).replace(/\\/g, '/').split('/').filter(Boolean)
  return parts[parts.length - 1] || path
}

async function refresh() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const response = await agentAPI.getBashSessions()
    sessions.value = response?.data || []
  } catch (cause) {
    error.value = cause?.message || '读取后台进程失败'
  } finally {
    loading.value = false
  }
}

const busySessionId = ref('')

async function terminate(item) {
  if (busySessionId.value) return
  busySessionId.value = item.sessionId
  error.value = ''
  let failed = false
  try {
    const response = await agentAPI.terminateBashSession(item.sessionId)
    if (response?.success) {
      // 走全局 log 提示框（Chat.vue 监听 app-notify），展示服务端终止状态日志
      window.dispatchEvent(new CustomEvent('app-notify', {detail: response?.data || '✅ 后台进程已关闭'}))
    } else {
      error.value = response?.message || '关闭后台进程失败'
      failed = true
    }
  } catch (cause) {
    error.value = cause?.message || '关闭后台进程失败'
    failed = true
  } finally {
    busySessionId.value = ''
  }
  // 成功时刷新列表（进程移除），失败保留错误提示
  if (!failed) await refresh()
}

function startPolling() {
  refresh()
  if (!refreshTimer) refreshTimer = setInterval(refresh, 3000)
}

function handleOutsideClick(event) {
  if (!rootRef.value?.contains(event.target)) close()
}

function handleKeydown(event) {
  if (event.key === 'Escape') close()
}

function close() {
  open.value = false
  clearInterval(refreshTimer)
  refreshTimer = null
  clearTimeout(copyTipTimer)
  document.removeEventListener('pointerdown', handleOutsideClick)
  document.removeEventListener('keydown', handleKeydown)
}

async function toggle() {
  if (open.value) {
    close()
    return
  }
  sessions.value = []
  error.value = ''
  open.value = true
  document.addEventListener('pointerdown', handleOutsideClick)
  document.addEventListener('keydown', handleKeydown)
  startPolling()
}

onMounted(() => {
  // 嵌入式（右侧栏页签）挂载即轮询
  if (props.embedded) startPolling()
})

onBeforeUnmount(close)
</script>

<style scoped>
.bash-manager {
  position: relative;
  display: flex;
  -webkit-app-region: no-drag;
}

/* 嵌入式（右侧栏页签）：去掉悬浮定位，铺满容器 */
.bash-manager.embedded {
  display: block;
  flex: 1;
  min-height: 0;
}

.bash-manager.embedded .bash-popover {
  position: static;
  top: auto;
  left: auto;
  width: 100%;
  max-height: none;
  height: 100%;
  display: flex;
  flex-direction: column;
  border: none;
  border-radius: 0;
  box-shadow: none;
}

.bash-manager.embedded .bash-list {
  flex: 1;
  max-height: none;
}

.tb-bash-btn,
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--fg-3);
  transition: background var(--t), color var(--t);
}

.tb-bash-btn {
  position: relative;
  width: 32px;
  height: 32px;
  border-radius: var(--r);
  font-size: 16px;
}

.tb-bash-btn:hover,
.tb-bash-btn.active,
.icon-btn:hover:not(:disabled) {
  color: var(--fg);
  background: var(--bg-3);
}

.bash-live-dot {
  position: absolute;
  right: 5px;
  bottom: 5px;
  width: 6px;
  height: 6px;
  border: 1px solid var(--bg);
  border-radius: 50%;
  background: #22a06b;
}

.bash-popover {
  position: absolute;
  top: 38px;
  left: -8px;
  z-index: 600;
  width: min(440px, calc(100vw - 24px));
  max-height: min(620px, calc(100vh - 64px));
  overflow: hidden;
  color: var(--fg);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 7px;
  box-shadow: var(--shadow-lg);
}

.bash-manager.opens-up .bash-popover {
  top: auto;
  bottom: 38px;
}

.bash-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 12px;
  border-bottom: 1px solid var(--border);
}

.bash-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
}

.bash-header p {
  margin: 3px 0 0;
  color: var(--fg-4);
  font-size: 11px;
}

.icon-btn {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border-radius: 5px;
}

.icon-btn:disabled {
  cursor: default;
  opacity: 0.4;
}

.bash-error {
  padding: 9px 14px;
  color: #b42318;
  background: rgba(220, 38, 38, 0.08);
  border-bottom: 1px solid rgba(220, 38, 38, 0.18);
  font-size: 12px;
}

.bash-empty {
  min-height: 150px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--fg-4);
  font-size: 12px;
}

.bash-empty :deep(svg) {
  font-size: 25px;
  opacity: 0.65;
}

.bash-list {
  max-height: 410px;
  overflow-y: auto;
}

.bash-row {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr) 28px;
  gap: 9px;
  align-items: start;
  padding: 12px 12px 11px 14px;
  border-bottom: 1px solid var(--border);
}

.bash-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 22px;
}

.bash-actions .icon-btn {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  border-radius: 5px;
}

.icon-btn.danger:hover:not(:disabled) {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.1);
}

.bash-status {
  height: 22px;
  display: flex;
  align-items: center;
}

.bash-status span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22a06b;
  box-shadow: 0 0 0 3px rgba(34, 160, 107, 0.12);
}

.bash-status span.done {
  background: #9aa4af;
  box-shadow: 0 0 0 3px rgba(154, 164, 175, 0.12);
}

.bash-main {
  min-width: 0;
}

.bash-title,
.bash-meta {
  display: flex;
  align-items: center;
  gap: 7px;
}

.bash-title strong {
  overflow: hidden;
  font-size: 12px;
  font-family: var(--font-mono);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 可复制文本：悬停高亮，点击后短暂显示“已复制” */
.bash-copy {
  cursor: pointer;
}

.bash-copy:hover {
  color: var(--fg);
  text-decoration: underline;
  text-decoration-color: var(--fg-3);
  text-underline-offset: 3px;
}

.bash-copy.copied {
  color: #1f7a4d;
  text-decoration: underline;
  text-decoration-color: #1f7a4d;
  text-underline-offset: 3px;
}

[data-theme="dark"] .bash-copy.copied {
  color: #4ade80;
  text-decoration-color: #4ade80;
}

.bash-state {
  flex: 0 0 auto;
  padding: 1px 5px;
  color: #1f7a4d;
  background: rgba(34, 160, 107, 0.12);
  border-radius: 3px;
  font-size: 10px;
}

.bash-state.done {
  color: var(--fg-4);
  background: var(--bg-3);
}

.bash-meta {
  margin-top: 5px;
  color: var(--fg-3);
  font-size: 11px;
}

.bash-workspace {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bash-meta span + span::before {
  content: '';
  display: inline-block;
  width: 2px;
  height: 2px;
  margin: 0 7px 3px 0;
  border-radius: 50%;
  background: var(--fg-4);
}

.bash-workdir {
  margin-top: 6px;
  overflow: hidden;
  color: var(--fg-4);
  font-family: var(--font-mono);
  font-size: 10px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spinning {
  animation: bash-spin 0.8s linear infinite;
}

@keyframes bash-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 680px) {
  .bash-popover {
    position: fixed;
    top: 44px;
    left: 8px;
    right: 8px;
    width: auto;
  }
}

[data-theme="dark"] .bash-popover {
  background: var(--bg-2);
  border-color: var(--glass-border);
}
</style>
