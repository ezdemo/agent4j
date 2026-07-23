<template>
  <div class="sch-panel">
    <!-- 头部 -->
    <div class="sch-head">
      <div class="sch-title">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
        </svg>
        <span>定时任务</span>
        <span v-if="filteredTasks.length > 0" class="sch-count">{{ filteredTasks.length }}{{ filterSession ? '/' + tasks.length : '' }}</span>
      </div>
      <div class="sch-head-actions">
        <button class="btn-icon-sm" @click="loadTasks" title="刷新">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
        </button>
        <button class="btn-icon-sm" @click="$emit('close')" title="关闭">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    </div>

    <!-- 未选择会话 -->
    <div v-if="!props.sessionName" class="sch-empty-full">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z"/>
      </svg>
      <p>请先选择一个会话</p>
      <span class="hint">定时任务会绑定到当前会话</span>
    </div>

    <!-- 加载中 -->
    <div v-else-if="loading" class="sch-loading">
      <div class="loading-spinner"></div>
    </div>

    <!-- 错误 -->
    <div v-else-if="error" class="sch-error">{{ error }}</div>

    <!-- 主体 -->
    <template v-else>
      <!-- 创建任务表单 -->
      <div class="sch-form">
        <div class="sch-form-row">
          <label>消息内容</label>
          <textarea v-model="form.message" rows="2" class="sch-input" placeholder="要发送给 Agent 的消息..."></textarea>
        </div>
        <div class="sch-form-row">
          <div class="sch-type-tabs">
            <button :class="{ active: form.scheduleType === 'cron' }" @click="form.scheduleType = 'cron'">Cron 表达式</button>
            <button :class="{ active: form.scheduleType === 'interval' }" @click="form.scheduleType = 'interval'">固定间隔</button>
          </div>
          <input v-if="form.scheduleType === 'cron'" v-model="form.cronExpr" class="sch-input" placeholder="0 0 9 * * ? (每天 9 点)" />
          <div v-else class="sch-interval-row">
            <input v-model.number="form.intervalSec" type="number" min="60" class="sch-input" placeholder="3600" />
            <span class="sch-interval-unit">秒</span>
          </div>
        </div>
        <div class="sch-form-row">
          <label>任务名称</label>
          <input v-model="form.name" class="sch-input" placeholder="我的定时任务" />
        </div>
        <div class="sch-form-actions">
          <button class="btn btn-primary btn-sm" @click="submitForm" :disabled="!canSubmit || submitting">
            {{ editingId ? '更新' : '创建' }}
          </button>
          <button v-if="editingId" class="btn btn-ghost btn-sm" @click="resetForm">取消</button>
        </div>
      </div>

      <!-- 会话筛选：固定两个选项，默认当前会话 -->
      <div class="sch-filter">
        <button
          :class="['sch-filter-chip', { active: filterSession === 'current' }]"
          @click="filterSession = 'current'"
        >当前会话</button>
        <button
          :class="['sch-filter-chip', { active: filterSession === 'all' }]"
          @click="filterSession = 'all'"
        >全部</button>
      </div>

      <!-- 任务列表 -->
      <div class="sch-list">
        <div v-if="filteredTasks.length === 0" class="sch-empty">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
          </svg>
          <p>{{ filterSession ? '该会话暂无定时任务' : '暂无定时任务' }}</p>
          <span class="hint">在上方表单中创建</span>
        </div>
        <div v-for="t in filteredTasks" :key="t.id" class="sch-task" :class="{ disabled: !t.enabled, 'current-session': t.sessionName === props.sessionName }">
          <div class="sch-task-head">
            <span class="sch-task-name">{{ t.name || '未命名任务' }}</span>
            <div class="sch-task-actions">
              <button class="btn-icon-xs" :title="t.enabled ? '禁用' : '启用'" @click="toggleTask(t)">
                <svg v-if="t.enabled" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
                <svg v-else width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <circle cx="12" cy="12" r="10"/>
                </svg>
              </button>
              <button class="btn-icon-xs" title="立即执行" @click="runTask(t.id)">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
              </button>
              <button class="btn-icon-xs" title="编辑" @click="editTask(t)">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z"/>
                </svg>
              </button>
              <button class="btn-icon-xs sch-task-del" title="删除" @click="deleteTask(t.id)">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </div>
          </div>
          <div class="sch-task-meta">
            <span v-if="t.sessionName" class="sch-task-session" :class="{ 'is-current': t.sessionName === props.sessionName }">{{ getSessionTitle(t.sessionName) }}</span>
            <span class="sch-task-schedule">{{ formatSchedule(t) }}</span>
          </div>
          <div v-if="t.message" class="sch-task-msg">{{ t.message }}</div>
          <div class="sch-task-status">
            <span v-if="t.runCount > 0">执行 {{ t.runCount }} 次</span>
            <span v-if="t.lastRunAt"> · 上次 {{ formatTime(t.lastRunAt) }}</span>
            <span v-if="t.lastError" class="sch-task-err"> · {{ t.lastError }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import {computed, onMounted, reactive, ref, watch} from 'vue'
import {scheduleAPI} from '../services/api.js'

const props = defineProps({
  workspaceHash: { type: String, default: null },
  sessionName: { type: String, default: '' },
  sessions: { type: Array, default: () => [] }
})

// 会话名 → 显示标题映射
const sessionTitleMap = computed(() => {
  const map = {}
  props.sessions.forEach(s => {
    map[s.name] = s.title || s.name
  })
  return map
})

function getSessionTitle(name) {
  return sessionTitleMap.value[name] || name
}

const emit = defineEmits(['close'])

const loading = ref(false)
const error = ref('')
const tasks = ref([])
const submitting = ref(false)
const editingId = ref(null)
const filterSession = ref('current')  // 默认筛选当前会话

// 按筛选条件过滤任务，当前会话置顶
const filteredTasks = computed(() => {
  const isCurrent = filterSession.value === 'current'
  const isAll = filterSession.value === 'all'
  let list
  if (isCurrent && props.sessionName) {
    list = tasks.value.filter(t => t.sessionName === props.sessionName)
  } else {
    list = [...tasks.value]
  }
  // 当前会话始终置顶
  list.sort((a, b) => {
    const aCur = a.sessionName === props.sessionName ? 0 : 1
    const bCur = b.sessionName === props.sessionName ? 0 : 1
    return aCur - bCur
  })
  return list
})

const form = reactive({
  message: '',
  scheduleType: 'cron',
  cronExpr: '',
  intervalSec: 3600,
  name: '',
  sessionName: ''  // 编辑时记录原任务的 sessionName
})

const canSubmit = computed(() => {
  if (!props.sessionName || !form.message) return false
  if (form.scheduleType === 'cron' && !form.cronExpr.trim()) return false
  if (form.scheduleType === 'interval' && (!form.intervalSec || form.intervalSec < 1)) return false
  return true
})

// 加载任务列表
async function loadTasks() {
  if (!props.workspaceHash) {
    tasks.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await scheduleAPI.list(props.workspaceHash)
    if (res && res.success) {
      tasks.value = res.data || []
    } else {
      error.value = res?.message || '加载失败'
    }
  } catch (e) {
    error.value = e?.message || '网络错误'
  } finally {
    loading.value = false
  }
}

// 提交表单（创建/更新）
async function submitForm() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    const payload = {
      name: form.name,
      sessionName: editingId.value ? form.sessionName : props.sessionName,
      message: form.message,
      cronExpr: form.scheduleType === 'cron' ? form.cronExpr.trim() : null,
      intervalSec: form.scheduleType === 'interval' ? form.intervalSec : null,
      enabled: true
    }
    let res
    if (editingId.value) {
      res = await scheduleAPI.update(props.workspaceHash, editingId.value, payload)
    } else {
      res = await scheduleAPI.create(props.workspaceHash, payload)
    }
    if (res && res.success) {
      resetForm()
      await loadTasks()
    } else {
      error.value = res?.message || '操作失败'
    }
  } catch (e) {
    error.value = e?.message || '操作失败'
  } finally {
    submitting.value = false
  }
}

// 编辑任务
function editTask(t) {
  editingId.value = t.id
  form.name = t.name || ''
  form.message = t.message || ''
  form.sessionName = t.sessionName || ''  // 记录原任务的 sessionName
  if (t.cronExpr) {
    form.scheduleType = 'cron'
    form.cronExpr = t.cronExpr
  } else {
    form.scheduleType = 'interval'
    form.intervalSec = t.intervalSec || 3600
  }
}

// 重置表单
function resetForm() {
  editingId.value = null
  form.name = ''
  form.message = ''
  form.scheduleType = 'cron'
  form.cronExpr = ''
  form.intervalSec = 3600
  form.sessionName = ''
}

// 切换启用/禁用
async function toggleTask(t) {
  try {
    await scheduleAPI.toggle(props.workspaceHash, t.id)
    await loadTasks()
  } catch (e) {
    error.value = e?.message || '操作失败'
  }
}

// 立即执行
async function runTask(id) {
  try {
    const res = await scheduleAPI.runNow(props.workspaceHash, id)
    if (res && res.success) {
      await loadTasks()
    } else {
      error.value = res?.message || '执行失败'
    }
  } catch (e) {
    error.value = e?.message || '执行失败'
  }
}

// 删除任务
async function deleteTask(id) {
  try {
    await scheduleAPI.delete(props.workspaceHash, id)
    await loadTasks()
  } catch (e) {
    error.value = e?.message || '删除失败'
  }
}

// 格式化调度信息
function formatSchedule(t) {
  if (t.cronExpr) return `cron: ${t.cronExpr}`
  if (t.intervalSec) {
    if (t.intervalSec >= 3600) return `每 ${Math.round(t.intervalSec / 3600)} 小时`
    if (t.intervalSec >= 60) return `每 ${Math.round(t.intervalSec / 60)} 分钟`
    return `每 ${t.intervalSec} 秒`
  }
  return '未知'
}

// 格式化时间
function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const diffMs = now - d
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚才'
  if (diffMin < 60) return `${diffMin}分钟前`
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return `${diffHr}小时前`
  const diffDay = Math.floor(diffHr / 24)
  if (diffDay < 30) return `${diffDay}天前`
  return d.toLocaleDateString()
}

// 监听 workspaceHash / sessionName 变化自动加载
watch(() => props.workspaceHash, () => {
  resetForm()
  loadTasks()
})
watch(() => props.sessionName, () => {
  resetForm()
  filterSession.value = 'current'  // 切换会话时重置为当前会话筛选
})

onMounted(() => {
  loadTasks()
})

defineExpose({ loadTasks })
</script>

<style scoped>
.sch-panel {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border-left: 1px solid var(--glass-border);
  overflow: hidden;
}

/* 头部 */
.sch-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--glass-border);
}
.sch-head-actions { display: flex; align-items: center; gap: 2px; }
.sch-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}
.sch-title svg { color: var(--fg-3); }
.sch-count {
  font-size: 10px;
  background: var(--accent-bg);
  color: var(--accent);
  padding: 0 5px;
  border-radius: 8px;
  font-weight: 500;
}

/* 加载/错误 */
.sch-loading { display: flex; align-items: center; justify-content: center; padding: 24px; }
.sch-error { padding: 8px 12px; font-size: 11px; color: var(--red); text-align: center; border-bottom: 1px solid var(--border); }

/* 未选择会话 */
.sch-empty-full {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 48px 16px;
  text-align: center;
  flex: 1;
}
.sch-empty-full svg { color: var(--fg-4); }
.sch-empty-full p { font-size: 13px; color: var(--fg-3); margin: 0; }
.sch-empty-full .hint { font-size: 11px; color: var(--fg-4); }

/* 表单 */
.sch-form {
  padding: 10px 12px;
  border-bottom: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sch-form-row {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.sch-form-row label {
  font-size: 11px;
  font-weight: 600;
  color: var(--fg-3);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.sch-input {
  width: 100%;
  padding: 5px 8px;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--bg);
  color: var(--fg);
  font-size: 12px;
  outline: none;
  transition: border-color 0.15s;
  box-sizing: border-box;
  font-family: inherit;
}
.sch-input:focus { border-color: var(--accent); }
.sch-input::placeholder { color: var(--fg-4); }
textarea.sch-input { resize: vertical; min-height: 36px; }
.sch-interval-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.sch-interval-row .sch-input { flex: 1; }
.sch-interval-unit {
  font-size: 11px;
  color: var(--fg-4);
}
.sch-type-tabs {
  display: flex;
  gap: 0;
  border-radius: var(--r-sm);
  overflow: hidden;
  border: 1px solid var(--border);
}
.sch-type-tabs button {
  flex: 1;
  padding: 4px 0;
  font-size: 11px;
  border: none;
  background: var(--bg);
  color: var(--fg-3);
  cursor: pointer;
  transition: all 0.15s;
}
.sch-type-tabs button.active {
  background: var(--accent);
  color: #fff;
}
.sch-form-actions {
  display: flex;
  gap: 6px;
}

/* 任务列表 */
.sch-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
}
.sch-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 16px;
  text-align: center;
}
.sch-empty svg { color: var(--fg-4); }
.sch-empty p { font-size: 13px; color: var(--fg-3); margin: 0; }
.sch-empty .hint { font-size: 11px; color: var(--fg-4); }

.sch-task {
  margin: 2px 8px;
  padding: 8px 10px;
  border-radius: var(--r);
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  transition: border-color 0.15s, opacity 0.15s;
}
.sch-task:hover { border-color: var(--accent); }
.sch-task.disabled { opacity: 0.55; }

.sch-task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.sch-task-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.sch-task-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.sch-task-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  font-size: 10px;
  color: var(--fg-4);
}
.sch-task-schedule {
  background: var(--bg-3);
  padding: 1px 5px;
  border-radius: 3px;
  font-family: var(--mono);
  font-size: 10px;
}
.sch-task-msg {
  margin-top: 3px;
  font-size: 11px;
  color: var(--fg-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sch-task-status {
  margin-top: 3px;
  font-size: 10px;
  color: var(--fg-4);
}
.sch-task-err { color: var(--red); }

/* 当前会话任务高亮 */
.sch-task.current-session {
  border-color: var(--accent);
  border-left-width: 3px;
}

/* 会话标签 */
.sch-task-session {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  background: var(--bg-3);
  color: var(--fg-4);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sch-task-session.is-current {
  background: var(--accent-bg);
  color: var(--accent);
}

/* 会话筛选栏 */
.sch-filter {
  display: flex;
  gap: 4px;
  padding: 6px 10px;
  overflow-x: auto;
  border-bottom: 1px solid var(--glass-border);
}
.sch-filter-chip {
  flex-shrink: 0;
  padding: 2px 8px;
  font-size: 11px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg);
  color: var(--fg-3);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.sch-filter-chip:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.sch-filter-chip.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
.sch-filter-chip.is-current:not(.active) {
  border-color: var(--accent);
  color: var(--accent);
}

[data-theme="dark"] .sch-type-tabs button.active,
[data-theme="dark"] .sch-filter-chip.active {
  background: #53677f;
  border-color: #647b96;
  color: #f8f9fb;
}

/* btn-icon-xs（复用设计） */
.btn-icon-xs {
  background: none;
  border: none;
  padding: 3px;
  cursor: pointer;
  color: var(--fg-3);
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.btn-icon-xs:hover { color: var(--accent); background: var(--accent-bg); }
.sch-task-del:hover { color: var(--red); background: rgba(239, 68, 68, 0.1); }

/* loading-spinner */
.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
</style>
