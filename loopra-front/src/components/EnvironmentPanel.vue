<template>
  <section class="environment-panel">
    <header class="environment-header">
      <div>
        <h2>环境信息</h2>
        <p v-if="environment?.message" class="environment-message">{{ environment.message }}</p>
      </div>
      <button class="icon-button" :class="{ loading: manualRefreshing }" type="button" title="刷新环境信息" aria-label="刷新环境信息" :disabled="manualRefreshing" @click="refreshManually">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 12a8 8 0 1 1-2.34-5.66L20 8"/><path d="M20 4v4h-4"/></svg>
      </button>
    </header>

    <div v-if="environment?.agentRunning || notice" class="environment-notice" :class="environment?.agentRunning ? 'warning' : noticeTone" role="status">
      <span>{{ environment?.agentRunning ? 'Agent 正在运行，暂不可操作' : notice }}</span>
      <button v-if="!environment?.agentRunning" type="button" title="关闭" @click="notice = ''">×</button>
    </div>

    <div v-if="loading && !environment" class="environment-empty">正在读取环境…</div>
    <div v-else-if="!environment || environment.mode === 'unavailable'" class="environment-empty">
      未找到可用的 Git 项目
    </div>
    <template v-else>
      <div class="environment-scroll">
      <div class="environment-summary" :class="environment.mode">
        <span class="environment-icon">
          <svg v-if="isWorktree" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="6" cy="5" r="2"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="12" r="2"/><path d="M6 7v10M8 7c5 0 3 5 8 5"/></svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="14" rx="2"/><path d="M8 21h8M12 18v3"/></svg>
        </span>
        <div class="environment-summary-main">
          <strong>{{ isWorktree ? '隔离分支' : '本地' }}</strong>
          <span :title="currentPath">{{ currentPath || '隔离分支尚未创建' }}</span>
        </div>
        <div class="environment-summary-actions">
          <span class="environment-state" :class="{ dirty: currentStatus?.dirty }">
            {{ statusError ? '读取失败' : currentStatus ? (currentStatus.dirty ? '有变更' : '干净') : '待创建' }}
          </span>
          <button class="environment-mode-button" type="button" :disabled="busy || environment.agentRunning" @click="toggleMode">
            {{ isWorktree ? '切到本地' : '启用隔离分支' }}
          </button>
        </div>
      </div>

      <div class="environment-section">
        <div class="environment-section-title">
          <span>{{ isWorktree ? '隔离分支变更' : '变更' }}</span>
          <span class="environment-count">{{ currentEntries.length }}</span>
        </div>
        <div v-if="statusError" class="environment-empty small error">{{ statusError }}</div>
        <div v-else-if="currentEntries.length === 0" class="environment-empty small">
          {{ currentStatus ? '暂无未提交变更' : '隔离分支尚未创建' }}
        </div>
        <div v-else class="environment-files">
          <div v-for="entry in currentEntries.slice(0, 12)" :key="entry.path" class="environment-file" :title="entry.path">
            <span class="environment-file-status" :class="statusTone(entry)">{{ statusLabel(entry) }}</span>
            <span class="environment-file-name">{{ entry.path }}</span>
          </div>
          <div v-if="currentEntries.length > 12" class="environment-more">还有 {{ currentEntries.length - 12 }} 个文件…</div>
        </div>
      </div>

      <div v-if="isWorktree" class="environment-section main-changes-section">
        <div class="environment-section-title">
          <span>本地变更</span>
          <span class="environment-count" :class="{ warning: mainEntries.length > 0 }">{{ mainEntries.length }}</span>
        </div>
        <div v-if="statusError" class="environment-empty small error">Git 状态不可用</div>
        <div v-else-if="mainEntries.length === 0" class="environment-empty small">主项目干净</div>
        <div v-else class="environment-files">
          <div v-for="entry in mainEntries.slice(0, 12)" :key="`main:${entry.path}`" class="environment-file" :title="entry.path">
            <span class="environment-file-status" :class="statusTone(entry)">{{ statusLabel(entry) }}</span>
            <span class="environment-file-name">{{ entry.path }}</span>
          </div>
          <div v-if="mainEntries.length > 12" class="environment-more">还有 {{ mainEntries.length - 12 }} 个文件…</div>
          <p class="environment-local-warning">主项目有未提交变更，暂不能合并。</p>
        </div>
      </div>

      <div class="environment-section environment-repository">
        <div class="environment-row">
          <span class="environment-row-icon">⌘</span>
          <div class="environment-row-main">
            <span class="environment-row-label">本地</span>
            <strong :title="environment.mainPath">{{ environment.mainBranch || '未命名分支' }}</strong>
          </div>
          <span v-if="mainStatus?.dirty" class="environment-dot dirty" title="主项目有变更" />
          <span v-else class="environment-dot" title="主项目干净" />
          <button class="environment-history-button" type="button" :disabled="historyLoading && historyScope === 'main'" @click="showHistory('main')">提交记录</button>
        </div>
        <div v-if="isWorktree" class="environment-row current-row">
          <span class="environment-row-icon">⑂</span>
          <div class="environment-row-main">
            <span class="environment-row-label">当前</span>
            <strong :title="environment.currentPath">{{ environment.currentBranch || '隔离分支尚未创建' }}</strong>
          </div>
          <span v-if="currentStatus?.dirty" class="environment-dot dirty" title="当前环境有变更" />
          <span v-else class="environment-dot" title="当前环境干净" />
          <button class="environment-history-button" type="button" :disabled="!environment.currentPath || (historyLoading && historyScope === 'current')" @click="showHistory('current')">提交记录</button>
        </div>
      </div>

      </div>

      <div class="environment-actions">
        <div class="environment-action-title">提交或推送</div>
        <input
          v-model="commitMessage"
          class="environment-commit-input"
          type="text"
          placeholder="提交信息"
          :disabled="busy || environment.agentRunning || !currentStatus?.dirty"
          @keyup.enter="commitCurrent"
        >
        <div class="environment-action-row">
          <button
            class="environment-button ai"
            type="button"
            :disabled="busy || generating || environment.agentRunning || !currentStatus?.dirty"
            title="AI 自动生成提交消息"
            @click="generateCommitMessage"
          >
            <svg v-if="!generating" class="environment-generate-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/></svg>
            <span v-else class="environment-generate-spinner" aria-hidden="true"></span>
            {{ generating ? '生成中...' : 'AI 生成' }}
          </button>
          <button class="environment-button primary" type="button" :disabled="busy || generating || environment.agentRunning || !currentStatus?.dirty || !commitMessage.trim()" @click="commitCurrent">
            提交当前
          </button>
          <button class="environment-button" type="button" :disabled="busy || environment.agentRunning || !mainStatus?.initialized || mainStatus?.dirty" @click="pushMain">
            推送
          </button>
        </div>
      </div>
    </template>

    <Teleport to="body">
      <div v-if="historyOpen" class="environment-history-overlay" @click.self="historyOpen = false">
        <section class="environment-history-modal" role="dialog" aria-modal="true" :aria-label="historyTitle">
          <header class="environment-history-modal-head">
            <div>
              <strong>{{ historyTitle }}</strong>
              <span>最近 {{ historyItems.length }} 条提交</span>
            </div>
            <button type="button" title="关闭" aria-label="关闭提交记录" @click="historyOpen = false">×</button>
          </header>
          <div v-if="historyLoading" class="environment-history-state">正在读取…</div>
          <div v-else-if="historyError" class="environment-history-state error">{{ historyError }}</div>
          <div v-else-if="historyItems.length === 0" class="environment-history-state">暂无提交</div>
          <div v-else class="environment-history-list">
            <article v-for="item in historyItems" :key="item.hash" class="environment-history-item">
              <div class="environment-history-item-head">
                <code :title="item.hash">{{ item.shortHash }}</code>
                <time :datetime="item.date">{{ formatHistoryDate(item.date) }}</time>
              </div>
              <div class="environment-history-subject">{{ item.subject || '无标题提交' }}</div>
              <div class="environment-history-author">{{ item.author }}</div>
            </article>
          </div>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {message} from 'ant-design-vue'
import {gitAPI, sessionsAPI} from '../services/api'

const props = defineProps({
  workspaceHash: {type: String, default: ''},
  sessionName: {type: String, default: ''}
})
const emit = defineEmits(['modeChange'])

const environment = ref(null)
const currentStatus = ref(null)
const mainStatus = ref(null)
const loading = ref(false)
const manualRefreshing = ref(false)
const busy = ref(false)
const generating = ref(false)
const commitMessage = ref('')
const notice = ref('')
const noticeTone = ref('info')
const statusError = ref('')
const historyOpen = ref(false)
const historyLoading = ref(false)
const historyScope = ref('')
const historyTitle = ref('提交记录')
const historyItems = ref([])
const historyError = ref('')
let refreshTimer = null

const isWorktree = computed(() => environment.value?.mode === 'worktree')
const currentPath = computed(() => isWorktree.value
  ? environment.value?.currentPath || ''
  : environment.value?.mainPath || '')
const currentEntries = computed(() => [
  ...(currentStatus.value?.changed || []),
  ...(currentStatus.value?.untracked || [])
])
const mainEntries = computed(() => [
  ...(mainStatus.value?.changed || []),
  ...(mainStatus.value?.untracked || [])
])

function desktopGit() {
  return window.electronAPI?.gitEnvironment || null
}

async function readGitStatus(git, path, label) {
  const result = await git.status(path)
  if (!result?.initialized) {
    throw new Error(`${label}读取失败：${result?.message || '不是 Git 仓库'}`)
  }
  return result
}

function notifyChanged() {
  window.dispatchEvent(new CustomEvent('loopra:git-changed', {
    detail: {workspaceHash: props.workspaceHash, sessionName: props.sessionName}
  }))
}

async function refresh() {
  if (!props.workspaceHash || !props.sessionName) return
  loading.value = true
  try {
    const response = await gitAPI.environment(props.workspaceHash, props.sessionName, {silent: true})
    if (!response?.success) throw new Error(response?.message || '环境信息读取失败')
    environment.value = response.data || null
    const git = desktopGit()
    if (!git) throw new Error('Git 功能未加载，请重启桌面端')
    if (!environment.value?.mainPath) throw new Error('无法读取主项目')

    const nextMainStatus = await readGitStatus(git, environment.value.mainPath, '主项目')
    const nextCurrentStatus = !environment.value.currentPath
      ? null
      : environment.value.currentPath === environment.value.mainPath
        ? nextMainStatus
        : await readGitStatus(git, environment.value.currentPath, '隔离分支')
    mainStatus.value = nextMainStatus
    currentStatus.value = nextCurrentStatus
    statusError.value = ''
  } catch (error) {
    currentStatus.value = null
    mainStatus.value = null
    statusError.value = error?.message || 'Git 状态读取失败'
    noticeTone.value = 'error'
    notice.value = statusError.value
  } finally {
    loading.value = false
  }
}

async function refreshManually() {
  if (manualRefreshing.value) return
  manualRefreshing.value = true
  try {
    await refresh()
  } finally {
    manualRefreshing.value = false
  }
}

function statusTone(entry) {
  if (entry.index === '?' && entry.workTree === '?') return 'untracked'
  if (entry.index !== ' ') return 'staged'
  return 'changed'
}

function statusLabel(entry) {
  if (entry.index === '?' && entry.workTree === '?') return '？'
  if (entry.index !== ' ') return '暂'
  if (entry.workTree === 'D') return '删'
  return '改'
}

function formatHistoryDate(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value || ''
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

async function showHistory(scope) {
  const git = desktopGit()
  const isMain = scope === 'main'
  const path = isMain ? environment.value?.mainPath : environment.value?.currentPath
  const branch = isMain ? environment.value?.mainBranch : environment.value?.currentBranch
  if (!git?.history || !path) {
    noticeTone.value = 'error'
    notice.value = '无法读取提交记录'
    return
  }
  historyOpen.value = true
  historyLoading.value = true
  historyScope.value = scope
  historyTitle.value = `${isMain ? '本地' : '隔离分支'} · ${branch || 'HEAD'}`
  historyItems.value = []
  historyError.value = ''
  try {
    historyItems.value = await git.history({cwd: path, branch: branch || 'HEAD', limit: 30})
  } catch (error) {
    historyError.value = error?.message || '读取失败'
  } finally {
    historyLoading.value = false
  }
}

async function generateCommitMessage() {
  if (generating.value || busy.value || !currentPath.value || !currentStatus.value?.dirty) return
  generating.value = true
  try {
    const response = await gitAPI.generateEnvironmentCommitMessage(props.workspaceHash, props.sessionName)
    if (!response?.success || !response?.data?.message) {
      throw new Error(response?.message || 'AI 生成失败')
    }
    commitMessage.value = response.data.message
    noticeTone.value = 'success'
    notice.value = 'AI 已生成提交信息，可修改后提交'
  } catch (error) {
    noticeTone.value = 'error'
    notice.value = error?.message || 'AI 生成失败'
  } finally {
    generating.value = false
  }
}

async function commitCurrent() {
  if (!currentPath.value || !commitMessage.value.trim() || busy.value) return
  const git = desktopGit()
  if (!git) {
    message.warning('Git 环境操作仅在 Desktop 中可用')
    return
  }
  busy.value = true
  try {
    await git.commit({cwd: currentPath.value, message: commitMessage.value.trim()})
    commitMessage.value = ''
    noticeTone.value = 'success'
    notice.value = '已提交'
    notifyChanged()
    await refresh()
  } catch (error) {
    noticeTone.value = 'error'
    notice.value = error?.message || '提交失败'
  } finally {
    busy.value = false
  }
}

async function mergeCurrent() {
  if (!isWorktree.value || busy.value) return
  const git = desktopGit()
  if (!git) return
  busy.value = true
  try {
    const result = await git.merge({
      currentPath: environment.value.currentPath,
      currentBranch: environment.value.currentBranch,
      mainPath: environment.value.mainPath,
      mainBranch: environment.value.mainBranch
    })
    noticeTone.value = result.conflicted ? 'error' : result.merged ? 'success' : 'info'
    notice.value = result.conflictFiles?.length
      ? `${result.message}：${result.conflictFiles.join('、')}`
      : result.message || '合并未执行'
    if (result.merged) notifyChanged()
    await refresh()
  } catch (error) {
    noticeTone.value = 'error'
    notice.value = error?.message || '合并失败'
  } finally {
    busy.value = false
  }
}

async function pushMain() {
  if (!environment.value?.mainPath || busy.value) return
  const git = desktopGit()
  if (!git) return
  busy.value = true
  try {
    await git.push({cwd: environment.value.mainPath})
    noticeTone.value = 'success'
    notice.value = '已推送'
    await refresh()
  } catch (error) {
    noticeTone.value = 'error'
    notice.value = error?.message || '推送失败'
  } finally {
    busy.value = false
  }
}

async function toggleMode() {
  if (!props.workspaceHash || !props.sessionName || busy.value) return
  if (isWorktree.value && currentStatus.value?.dirty) {
    noticeTone.value = 'error'
    notice.value = '请先提交隔离分支变更'
    return
  }
  const enabled = !isWorktree.value
  busy.value = true
  try {
    const response = await sessionsAPI.setWorktreeMode(props.sessionName, props.workspaceHash, {worktreeMode: enabled}, {silent: true})
    if (!response?.success) throw new Error(response?.message || '工作模式切换失败')
    if (enabled) {
      try {
        const created = await gitAPI.worktreeCreate(props.workspaceHash, props.sessionName, {silent: true})
        if (!created?.success) throw new Error(created?.message || '隔离分支创建失败')
      } catch (error) {
        await sessionsAPI.setWorktreeMode(props.sessionName, props.workspaceHash, {worktreeMode: false}, {silent: true}).catch(() => {})
        throw error
      }
    }
    noticeTone.value = 'success'
    notice.value = enabled ? '已启用隔离分支' : '已切换到本地'
    emit('modeChange', enabled)
    notifyChanged()
    await refresh()
  } catch (error) {
    noticeTone.value = 'error'
    notice.value = error?.message || '工作模式切换失败'
  } finally {
    busy.value = false
  }
}

watch(() => [props.workspaceHash, props.sessionName], refresh, {immediate: true})
onMounted(() => {
  refreshTimer = window.setInterval(refresh, 3000)
})
onBeforeUnmount(() => {
  if (refreshTimer) window.clearInterval(refreshTimer)
})

defineExpose({refresh})
</script>

<style scoped>
.environment-panel { display: flex; flex-direction: column; height: 100%; min-width: 0; overflow: hidden; color: var(--fg-2); background: var(--bg-1); font-size: 13px; }
.environment-header { display: flex; align-items: flex-start; justify-content: space-between; padding: 14px 14px 10px; border-bottom: 1px solid var(--border); }
.environment-header h2 { margin: 0; font-size: 15px; font-weight: 650; color: var(--fg-1); }
.environment-message { margin: 4px 0 0; color: var(--fg-4); font-size: 11px; line-height: 1.35; }
.icon-button { display: inline-flex; align-items: center; justify-content: center; width: 28px; height: 28px; padding: 0; border: 1px solid transparent; border-radius: 50%; color: var(--fg-4); background: transparent; cursor: pointer; transition: color var(--t), background-color var(--t), border-color var(--t), transform .12s ease; }
.icon-button:hover:not(:disabled) { border-color: color-mix(in srgb, var(--accent) 16%, var(--border)); color: var(--accent); background: color-mix(in srgb, var(--accent) 7%, transparent); }
.icon-button:active:not(:disabled) { transform: scale(.94); }
.icon-button:focus-visible { border-color: color-mix(in srgb, var(--accent) 48%, transparent); outline: none; box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 10%, transparent); }
.icon-button:disabled { cursor: default; }
.icon-button svg { width: 15px; height: 15px; transition: opacity var(--t); }
.icon-button.loading svg { opacity: 0; }
.icon-button.loading::after { position: absolute; width: 13px; height: 13px; box-sizing: border-box; border: 1.5px solid color-mix(in srgb, currentColor 24%, transparent); border-top-color: currentColor; border-radius: 50%; content: ''; animation: environment-refresh-spin .7s linear infinite; }
@keyframes environment-refresh-spin { to { transform: rotate(360deg); } }
.environment-scroll { flex: 1 1 auto; min-height: 0; overflow-y: auto; }
.environment-summary { display: flex; align-items: center; gap: 9px; margin: 10px 10px 4px; padding: 9px 10px; border: 1px solid var(--border); border-radius: 7px; background: var(--bg-2); }
.environment-summary.worktree { border-color: color-mix(in srgb, var(--accent) 35%, var(--border)); }
.environment-icon { display: inline-flex; color: var(--accent); }
.environment-icon svg { width: 19px; height: 19px; }
.environment-summary-main { display: grid; min-width: 0; gap: 2px; flex: 1; }
.environment-summary-main strong { color: var(--fg-1); font-size: 13px; }
.environment-summary-main span { overflow: hidden; color: var(--fg-4); font: 10px var(--mono); text-overflow: ellipsis; white-space: nowrap; }
.environment-summary-actions { display: grid; justify-items: end; gap: 4px; }
.environment-state { color: var(--green, #16803c); font-size: 11px; white-space: nowrap; }
.environment-state.dirty { color: var(--orange, #b45309); }
.environment-mode-button { padding: 2px 5px; border: 0; border-radius: 4px; color: var(--accent); background: transparent; cursor: pointer; font-size: 10px; }
.environment-mode-button:hover:not(:disabled) { background: var(--bg-hover); }
.environment-mode-button:disabled { opacity: .45; cursor: default; }
.environment-section { padding: 9px 12px; border-bottom: 1px solid var(--border); }
.environment-section-title, .environment-action-title { display: flex; align-items: center; justify-content: space-between; color: var(--fg-3); font-size: 11px; font-weight: 650; letter-spacing: .02em; }
.environment-count { min-width: 18px; padding: 1px 5px; border-radius: 9px; text-align: center; color: var(--fg-3); background: var(--bg-hover); font-size: 10px; }
.environment-count.warning { color: var(--orange, #b45309); background: #fff3d6; }
.environment-files { display: grid; gap: 4px; margin-top: 7px; }
.environment-file { display: flex; align-items: center; gap: 6px; min-width: 0; line-height: 20px; }
.environment-file-status { width: 17px; flex: 0 0 17px; border-radius: 3px; text-align: center; font-size: 10px; font-weight: 700; }
.environment-file-status.changed { color: #b45309; background: #fff3d6; }
.environment-file-status.staged { color: #19734a; background: #e3f7eb; }
.environment-file-status.untracked { color: #596579; background: #edf0f4; }
.environment-file-name { overflow: hidden; color: var(--fg-2); font: 11px var(--mono); text-overflow: ellipsis; white-space: nowrap; }
.environment-more, .environment-empty { color: var(--fg-4); font-size: 11px; }
.environment-local-warning { margin: 7px 0 0; color: var(--orange, #b45309); font-size: 10px; line-height: 1.4; }
.environment-empty { padding: 28px 14px; text-align: center; }
.environment-empty.small { padding: 10px 0 2px; text-align: left; }
.environment-repository { display: grid; gap: 4px; }
.environment-row { display: flex; align-items: center; gap: 8px; min-width: 0; padding: 5px 0; }
.environment-row-icon { width: 18px; color: var(--fg-3); font-size: 16px; text-align: center; }
.environment-row-main { display: grid; min-width: 0; flex: 1; gap: 2px; }
.environment-row-label { color: var(--fg-4); font-size: 11px; }
.environment-row-main strong { overflow: hidden; color: var(--fg-1); font: 12px var(--mono); text-overflow: ellipsis; white-space: nowrap; }
.environment-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--green, #16803c); }
.environment-dot.dirty { background: var(--orange, #b45309); }
.environment-history-button { flex: 0 0 auto; padding: 3px 6px; border: 1px solid var(--border); border-radius: 4px; color: var(--fg-3); background: transparent; cursor: pointer; font-size: 10px; }
.environment-history-button:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.environment-history-button:disabled { opacity: .45; cursor: default; }
.environment-actions { padding: 10px 12px 12px; border-top: 1px solid var(--border); }
.environment-commit-input { width: 100%; box-sizing: border-box; margin: 8px 0; padding: 7px 8px; border: 1px solid var(--border); border-radius: 5px; outline: none; color: var(--fg-1); background: var(--bg-2); font-size: 12px; }
.environment-commit-input:focus { border-color: var(--accent); }
.environment-commit-input:disabled { opacity: .55; }
.environment-action-row { display: flex; flex-wrap: wrap; gap: 6px; }
.environment-button { flex: 1 1 auto; min-height: 28px; padding: 5px 8px; border: 1px solid var(--border); border-radius: 5px; color: var(--fg-2); background: var(--bg-2); cursor: pointer; font-size: 11px; }
.environment-button:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
.environment-button.primary { border-color: var(--accent-btn); color: #fff; background: var(--accent-btn); }
.environment-button:disabled { opacity: .45; cursor: default; }
.environment-button.ai { display: inline-flex; align-items: center; justify-content: center; gap: 5px; border-color: color-mix(in srgb, var(--accent) 28%, var(--border)); color: var(--accent); }
.environment-generate-icon { width: 12px; height: 12px; }
.environment-generate-spinner { width: 11px; height: 11px; box-sizing: border-box; border: 1.5px solid color-mix(in srgb, currentColor 24%, transparent); border-top-color: currentColor; border-radius: 50%; animation: environment-refresh-spin .7s linear infinite; }
.environment-notice { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin: 8px 10px 0; padding: 7px 9px; border: 1px solid var(--border); border-radius: 6px; color: var(--fg-2); background: var(--bg-2); font-size: 11px; line-height: 1.35; }
.environment-notice::before { width: 6px; height: 6px; flex: 0 0 6px; border-radius: 50%; background: var(--fg-4); content: ''; }
.environment-notice span { flex: 1 1 auto; min-width: 0; }
.environment-notice button { flex: 0 0 auto; padding: 0 2px; border: 0; color: var(--fg-4); background: transparent; cursor: pointer; font-size: 15px; line-height: 1; }
.environment-notice button:hover { color: var(--fg-1); }
.environment-notice.success { border-color: color-mix(in srgb, var(--green, #16803c) 28%, var(--border)); }
.environment-notice.success::before { background: var(--green, #16803c); }
.environment-notice.warning { border-color: color-mix(in srgb, var(--orange, #b45309) 28%, var(--border)); }
.environment-notice.warning::before { background: var(--orange, #b45309); }
.environment-notice.error { border-color: color-mix(in srgb, var(--red, #b42318) 28%, var(--border)); }
.environment-notice.error::before { background: var(--red, #b42318); }
.environment-history-overlay { position: fixed; z-index: 420; inset: 0; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(14, 16, 20, .34); backdrop-filter: blur(4px); }
.environment-history-modal { display: flex; width: min(680px, calc(100vw - 40px)); height: min(70vh, 640px); flex-direction: column; overflow: hidden; border: 1px solid var(--border); border-radius: 9px; color: var(--fg); background: var(--bg); box-shadow: 0 20px 56px rgba(0, 0, 0, .24); }
.environment-history-modal-head { display: flex; min-height: 54px; align-items: center; justify-content: space-between; padding: 0 14px 0 18px; border-bottom: 1px solid var(--border); background: var(--bg-2); }
.environment-history-modal-head > div { display: grid; gap: 2px; min-width: 0; }
.environment-history-modal-head strong { overflow: hidden; color: var(--fg); font: 13px var(--mono); text-overflow: ellipsis; white-space: nowrap; }
.environment-history-modal-head span { color: var(--fg-4); font-size: 11px; }
.environment-history-modal-head button { width: 28px; height: 28px; border: 0; border-radius: 5px; color: var(--fg-3); background: transparent; cursor: pointer; font-size: 20px; line-height: 1; }
.environment-history-modal-head button:hover { color: var(--fg); background: var(--bg-hover); }
.environment-history-state { display: grid; flex: 1; place-items: center; color: var(--fg-4); font-size: 12px; }
.environment-history-state.error { color: var(--red, #b42318); }
.environment-history-list { position: relative; flex: 1; overflow: auto; padding: 8px 14px 14px 34px; }
.environment-history-list::before { position: absolute; top: 18px; bottom: 18px; left: 22px; width: 1px; background: var(--border); content: ''; }
.environment-history-item { position: relative; padding: 10px 12px; border-radius: 6px; }
.environment-history-item::before { position: absolute; top: 16px; left: -16px; width: 7px; height: 7px; border: 2px solid var(--bg); border-radius: 50%; background: var(--fg-4); content: ''; }
.environment-history-item:hover { background: var(--bg-hover); }
.environment-history-item-head { display: flex; align-items: center; gap: 10px; }
.environment-history-item-head code { padding: 2px 6px; border-radius: 4px; color: var(--accent); background: var(--accent-bg); font: 11px var(--mono); }
.environment-history-item-head time { margin-left: auto; color: var(--fg-4); font-size: 11px; }
.environment-history-subject { margin-top: 5px; color: var(--fg); font-size: 13px; line-height: 1.45; }
.environment-history-author { margin-top: 3px; color: var(--fg-4); font-size: 11px; }
</style>
