<template>
  <div class="git-panel">
    <!-- 头部 -->
    <div class="git-head">
      <div class="git-title">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M13 6h3a2 2 0 0 1 2 2v7"/><line x1="6" y1="9" x2="6" y2="21"/></svg>
        <span>源代码管理</span>
      </div>
      <div class="git-head-actions">
        <button class="btn-icon-sm" @click="loadStatus" title="刷新">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
        </button>
        <button class="btn-icon-sm" @click="$emit('close')" title="关闭">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    </div>

    <!-- 操作反馈 -->
    <div v-if="feedback" class="git-feedback" :class="feedback.type">{{ feedback.message }}</div>

    <!-- 加载中 -->
    <div v-if="loading" class="git-loading">
      <div class="loading-spinner"></div>
    </div>

    <!-- 其他错误 -->
    <div v-else-if="error" class="git-error">{{ error }}</div>

    <!-- 三态界面 -->
    <template v-else>
      <!-- 状态 1: Git 不可用 -->
      <div v-if="!gitAvailable" class="git-state-unavailable">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
        <p>Git 未安装或不可用</p>
        <span class="hint">请安装 Git 后重启应用</span>
      </div>

      <!-- 状态 2: 仓库未初始化 -->
      <div v-else-if="!initialized" class="git-state-uninit">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M13 6h3a2 2 0 0 1 2 2v7"/><line x1="6" y1="9" x2="6" y2="21"/></svg>
        <p>当前目录尚未初始化为 Git 仓库</p>
        <label class="init-checkbox">
          <input type="checkbox" v-model="initCommit" />
          <span>初始化后创建首次提交</span>
        </label>
        <button class="btn-init" @click="handleInit" :disabled="initLoading">
          {{ initLoading ? '初始化中...' : '初始化 Git 仓库' }}
        </button>
      </div>

      <!-- 状态 3: 正常 -->
      <template v-else>
        <!-- 分支信息 -->
        <div class="git-branch">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="6" y1="3" x2="6" y2="15"/><circle cx="18" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M18 9a9 9 0 0 1-9 9"/></svg>
          <span class="branch-name">{{ branchName }}</span>
          <span class="change-count" v-if="hasChanges">{{ changedCount + untrackedCount }}</span>
        </div>

        <!-- 可滚动主体区 -->
        <div class="git-body">
          <!-- 提交区域 -->
          <div class="commit-area" v-if="hasChanges">
            <div class="commit-select-all">
              <input type="checkbox" class="git-checkbox" :checked="isAllSelected" @change="toggleSelectAll">
              <span class="select-all-label">全选变更 ({{ selectedCount }}/{{ changedCount }})</span>
            </div>
            <textarea
              class="commit-input"
              placeholder="提交信息（按 Enter 提交）..."
              v-model="commitMessage"
              rows="2"
              @keydown.enter.exact.prevent="handleCommit"
            ></textarea>
            <!-- 作者配置和模型选择 -->
            <div class="commit-author-bar">
              <!-- 模型选择在左边 -->
              <select v-model="commitModel" class="author-btn model-select-inline" @change="checkModelWarning">
                <option v-for="m in availableModels" :key="m.name" :value="m.name">
                  {{ m.name }}
                </option>
              </select>
              <!-- 提交人按钮在右边 -->
              <button class="author-btn" @click="showAuthorModal = true" title="配置提交作者名和邮箱">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
                <span>{{ authorName || 'Agent4j' }}</span>
                <svg class="author-chevron" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
              </button>
            </div>
            <!-- 模型警告 -->
            <div v-if="modelWarning" class="model-warning-bar">{{ modelWarning }}</div>
            <div class="commit-actions">
              <button
                class="generate-btn"
                @click="handleGenerateMessage"
                :disabled="generating || selectedCount === 0"
                title="AI 自动生成提交消息"
              >
                <svg v-if="!generating" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
                </svg>
                <span v-else class="generate-spinner"></span>
                {{ generating ? '生成中...' : 'AI 生成' }}
              </button>
              <button
                class="commit-button"
                @click="handleCommit"
                :disabled="committing || !commitMessage.trim() || selectedCount === 0"
              >
                {{ committing ? '提交中...' : `提交 (${selectedCount})` }}
              </button>
            </div>
          </div>

          <!-- 文件列表 -->
          <div class="git-files" v-if="hasChanges">
            <!-- 变更文件 -->
            <template v-if="changedCount > 0">
              <div class="git-section-header changed" @click="showChanged = !showChanged">
                <div class="section-left">
                  <svg class="chevron" :class="{ open: showChanged }" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
                  <span>变更</span>
                </div>
                <span class="section-count">{{ changedCount }}</span>
              </div>
              <template v-if="showChanged">
                <div v-for="f in changedFiles" :key="'c-'+f.path" class="git-file" @click="openDiff(f.path)">
                  <input type="checkbox" class="git-checkbox" :checked="selectedFiles.has(f.path)" @click.stop @change="toggleSelect(f.path)">
                  <span class="file-status" :class="(f.status || f.index || f.workTree || 'M')">{{ (f.status || f.index || f.workTree || 'M') }}</span>
                  <span class="file-path" :title="f.path">{{ f.path }}</span>
                  <button class="file-action-btn toggle-btn" @click.stop="handleToggle(f.path)" title="取消暂存">×</button>
                </div>
              </template>
            </template>

            <!-- 未跟踪文件 -->
            <template v-if="untrackedCount > 0">
              <div class="git-section-header untracked" @click="showUntracked = !showUntracked">
                <div class="section-left">
                  <svg class="chevron" :class="{ open: showUntracked }" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
                  <span>未跟踪文件</span>
                </div>
                <span class="section-count">{{ untrackedCount }}</span>
              </div>
              <template v-if="showUntracked">
                <div v-for="f in untrackedFiles" :key="'n-'+f.path" class="git-file" @click="openDiff(f.path)">
                  <input type="checkbox" class="git-checkbox" :checked="selectedFiles.has(f.path)" @click.stop @change="toggleSelect(f.path)">
                  <span class="file-status U">?</span>
                  <span class="file-path" :title="f.path">{{ f.path }}</span>
                  <button class="file-action-btn toggle-btn add" @click.stop="handleToggle(f.path)" title="添加到变更">+</button>
                </div>
              </template>
            </template>
          </div>

          <!-- 空状态 -->
          <div v-else class="git-empty">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
            <span>工作区干净，没有待提交的更改</span>
          </div>
        </div>

        <!-- 提交历史 -->
        <div class="git-history">
          <div class="git-section-header history" @click="showCommitHistory = !showCommitHistory">
            <div class="section-left">
              <svg class="chevron" :class="{ open: showCommitHistory }" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
              <span>历史提交</span>
            </div>
            <span class="section-count" v-if="commits.length">{{ commits.length }}</span>
          </div>
          <template v-if="showCommitHistory">
            <div v-if="historyLoading" class="git-history-loading">
              <div class="loading-spinner"></div>
            </div>
            <div v-else-if="commits.length === 0" class="git-history-empty">暂无提交记录</div>
            <div v-else class="git-commit-list">
              <div v-for="c in commits" :key="c.hash" class="git-commit-item">
                <div class="commit-top">
                  <code class="commit-hash" :title="c.hash">{{ c.shortHash }}</code>
                  <span class="commit-date" :title="c.date">{{ fmtRelative(c.date) }}</span>
                </div>
                <div class="commit-message">{{ c.message }}</div>
                <div class="commit-author">{{ c.author }}</div>
              </div>
            </div>
          </template>
        </div>

        <!-- Diff 预览弹层（Teleport 到 body，左右对比） -->
        <Teleport to="body">
          <div v-if="diffViewer.open" class="diff-overlay" @click.self="closeDiffViewer">
            <div class="diff-viewer diff-viewer-sbs">
              <div class="diff-viewer-head">
                <span class="diff-viewer-file">{{ diffViewer.file }}</span>
                <span class="diff-viewer-stat" v-if="diffViewer.stat">{{ diffViewer.stat }}</span>
                <button class="btn-icon-sm" @click="closeDiffViewer" title="关闭">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                </button>
              </div>
              <div class="diff-sbs" v-if="diffPairs.length > 0">
                <!-- 表头 -->
                <div class="diff-sbs-header">
                  <span class="diff-sbs-label diff-sbs-label-old">旧版本</span>
                  <span class="diff-sbs-label diff-sbs-label-new">新版本</span>
                </div>
                <!-- 行 -->
                <div v-for="(pair, i) in diffPairs" :key="i" class="diff-sbs-row" :class="'diff-sbs-' + pair.type">
                  <div class="diff-sbs-cell diff-sbs-cell-left">
                    <span class="diff-sbs-ln">{{ pair.leftLineNum ?? '' }}</span>
                    <span class="diff-sbs-code" v-html="pair.leftHtml"></span>
                  </div>
                  <div class="diff-sbs-gutter"></div>
                  <div class="diff-sbs-cell diff-sbs-cell-right">
                    <span class="diff-sbs-ln">{{ pair.rightLineNum ?? '' }}</span>
                    <span class="diff-sbs-code" v-html="pair.rightHtml"></span>
                  </div>
                </div>
              </div>
              <div v-else class="diff-viewer-empty">{{ diffViewer.diff ? '无变更' : '加载中...' }}</div>
            </div>
          </div>
        </Teleport>

        <!-- 作者配置 Modal -->
        <Teleport to="body">
          <div v-if="showAuthorModal" class="diff-overlay" @click.self="showAuthorModal = false">
            <div class="modal author-modal">
              <div class="modal-head">
                <span>提交作者配置</span>
                <button class="btn-icon-sm" @click="showAuthorModal = false">×</button>
              </div>
              <div class="modal-body">
                <div class="author-field">
                  <label>作者名</label>
                  <input v-model="authorName" placeholder="输入作者名" />
                </div>
                <div class="author-field">
                  <label>邮箱</label>
                  <input v-model="authorEmail" placeholder="输入邮箱" />
                </div>
              </div>
              <div class="modal-foot">
                <div class="modal-foot-left">
                  <button class="btn btn-ghost btn-reset" @click="handleResetAuthor" title="恢复为 Agent4j 默认值">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                    恢复默认
                  </button>
                </div>
                <div class="modal-foot-right">
                  <button class="btn btn-ghost" @click="handleFetchGitConfig" :disabled="fetchingConfig">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
                    {{ fetchingConfig ? '获取中...' : '从现有环境获取' }}
                  </button>
                  <button class="btn btn-primary" @click="handleSaveAuthorConfig">保存</button>
                </div>
              </div>
            </div>
          </div>
        </Teleport>
      </template>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { gitAPI } from '../services/api'
import { highlightCode, detectLanguage } from '../utils/highlight'
import { sanitize } from '../utils/sanitize'

const props = defineProps({
  workspaceHash: { type: String, default: null }
})

defineEmits(['close'])

// ---- 状态 ----
const loading = ref(false)
const error = ref('')
const gitAvailable = ref(false)
const initialized = ref(false)
const branchName = ref('')
const changedFiles = ref([])
const untrackedFiles = ref([])

// 折叠控制
const showChanged = ref(true)
const showUntracked = ref(false)
const showCommitHistory = ref(true)

// 提交历史
const commits = ref([])
const historyLoading = ref(false)

// 文件选择
const selectedFiles = ref(new Set())

// 提交
const commitMessage = ref('')
const committing = ref(false)
const generating = ref(false)

// 提交作者（从 API 加载/保存到 .agent4j/git-author.json）
const authorName = ref('Agent4j')
const authorEmail = ref('agent4j@sorghum.site')
const commitModel = ref('')
const availableModels = ref([])
const modelWarning = ref('')

// 获取可用模型列表
const loadAvailableModels = async () => {
  try {
    const r = await gitAPI.getModels()
    if (r.success && r.data) {
      availableModels.value = r.data.models || []
      // 如果当前配置的模型不在可用列表里，设置警告
      checkModelWarning()
    }
  } catch (e) {
    // 静默
  }
}

// 检查模型警告
const checkModelWarning = () => {
  if (!commitModel.value || availableModels.value.length === 0) {
    modelWarning.value = ''
    return
  }
  const found = availableModels.value.some(m => m.name === commitModel.value)
  if (!found) {
    modelWarning.value = `警告: "${commitModel.value}" 不在可用模型列表中，请切换模型`
  } else {
    modelWarning.value = ''
  }
}

const loadAuthorConfig = async () => {
  try {
    const r = await gitAPI.getConfig(props.workspaceHash)
    if (r.success && r.data) {
      if (r.data.authorName) authorName.value = r.data.authorName
      if (r.data.authorEmail) authorEmail.value = r.data.authorEmail
      if (r.data.model) commitModel.value = r.data.model
    }
  } catch (e) {
    // 静默，默认值兜底
  }
}

const handleSaveAuthorConfig = async () => {
  try {
    const r = await gitAPI.saveConfig(props.workspaceHash, authorName.value.trim(), authorEmail.value.trim(), commitModel.value.trim())
    if (r.success) {
      showFeedback('success', '作者配置已保存')
      showAuthorModal.value = false
    } else {
      showFeedback('error', r.error || '保存失败')
    }
  } catch (e) {
    showFeedback('error', e.message || '保存失败')
  }
}

// 作者配置弹窗
const showAuthorModal = ref(false)
const fetchingConfig = ref(false)
const handleFetchGitConfig = async () => {
  fetchingConfig.value = true
  try {
    const r = await gitAPI.getConfig(props.workspaceHash)
    if (r.success && r.data) {
      if (r.data.authorName) authorName.value = r.data.authorName
      if (r.data.authorEmail) authorEmail.value = r.data.authorEmail
      if (r.data.model) commitModel.value = r.data.model
      showFeedback('success', '已从 Git 本地配置获取作者信息')
    } else {
      showFeedback('error', r.error || '获取 Git 配置失败')
    }
  } catch (e) {
    showFeedback('error', e.message || '获取 Git 配置失败')
  } finally {
    fetchingConfig.value = false
  }
}
const handleResetAuthor = async () => {
  authorName.value = 'Agent4j'
  authorEmail.value = 'agent4j@sorghum.site'
  commitModel.value = '' // 清空，使用默认模型
  try {
    await gitAPI.saveConfig(props.workspaceHash, 'Agent4j', 'agent4j@sorghum.site', '')
    showFeedback('success', '已恢复为默认作者信息')
  } catch (e) {
    showFeedback('error', e.message || '恢复失败')
  }
}

// 初始化
const initLoading = ref(false)
const initCommit = ref(true)

// 反馈
const feedback = ref(null)

// Diff 预览
const diffViewer = ref({ open: false, file: '', diff: '', stat: '' })

const hasChanges = computed(() => {
  const c = changedFiles.value || []
  const u = untrackedFiles.value || []
  return c.length + u.length > 0
})
const changedCount = computed(() => (changedFiles.value || []).length)
const untrackedCount = computed(() => (untrackedFiles.value || []).length)
const totalCount = computed(() => changedCount.value + untrackedCount.value)
const selectedCount = computed(() => selectedFiles.value.size)
const isAllSelected = computed(() => selectedCount.value === changedCount.value && changedCount.value > 0)

const toggleSelect = (path) => {
  const newSet = new Set(selectedFiles.value)
  if (newSet.has(path)) {
    newSet.delete(path)
  } else {
    newSet.add(path)
  }
  selectedFiles.value = newSet
}

const toggleSelectAll = () => {
  if (isAllSelected.value) {
    // 只取消变更列表的选中，保留未跟踪文件的手动选择
    const newSet = new Set(selectedFiles.value)
    ;(changedFiles.value || []).forEach(f => newSet.delete(f.path))
    selectedFiles.value = newSet
  } else {
    const newSet = new Set(selectedFiles.value)
    ;(changedFiles.value || []).forEach(f => newSet.add(f.path))
    selectedFiles.value = newSet
  }
}

// ---- 加载状态 ----
const loadStatus = async () => {
  loading.value = true
  error.value = ''
  try {
    const r = await gitAPI.status(props.workspaceHash)
    if (r.success && r.data) {
      const d = r.data
      gitAvailable.value = d.gitAvailable
      initialized.value = d.initialized
      branchName.value = d.branch || ''
      changedFiles.value = d.changed || []
      untrackedFiles.value = d.untracked || []
      // 从状态中读取配置的模型
      if (d.model) commitModel.value = d.model
      // 仓库初始化后加载提交历史
      if (initialized.value) await loadCommitHistory()
    } else {
      // 回退到 diff 接口
      await loadDiffFallback()
    }
  } catch (e) {
    await loadDiffFallback()
  } finally {
    loading.value = false
  }
}

const loadDiffFallback = async () => {
  try {
    const r = await gitAPI.diff(props.workspaceHash)
    if (r.success && r.data) {
      gitAvailable.value = true
      initialized.value = true
      branchName.value = r.data.branch || ''
      changedFiles.value = r.data.changed || []
      untrackedFiles.value = r.data.untracked || []
    } else {
      error.value = r.error || '加载失败'
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  }
}

// ---- 提交历史 ----
const loadCommitHistory = async () => {
  if (!initialized.value) return
  historyLoading.value = true
  try {
    const r = await gitAPI.commitHistory(props.workspaceHash, 50)
    if (r.success && r.data) {
      commits.value = r.data.commits || []
    }
  } catch (e) {
    // 静默失败，不影响主功能
    console.debug('加载提交历史失败:', e)
  } finally {
    historyLoading.value = false
  }
}

const fmtRelative = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diffMs = now - d
  const diffSec = Math.floor(diffMs / 1000)
  if (diffSec < 60) return '刚刚'
  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) return diffMin + ' 分钟前'
  const diffHr = Math.floor(diffMin / 60)
  if (diffHr < 24) return diffHr + ' 小时前'
  const diffDay = Math.floor(diffHr / 24)
  if (diffDay < 30) return diffDay + ' 天前'
  const diffMon = Math.floor(diffDay / 30)
  if (diffMon < 12) return diffMon + ' 个月前'
  return Math.floor(diffMon / 12) + ' 年前'
}

// ---- 操作 ----
const showFeedback = (type, message) => {
  feedback.value = { type, message }
  setTimeout(() => { feedback.value = null }, 3000)
}

const handleInit = async () => {
  initLoading.value = true
  try {
    const r = await gitAPI.init(props.workspaceHash, initCommit.value)
    if (r.success) {
      showFeedback('success', 'Git 仓库初始化成功')
      await loadStatus()
    } else {
      showFeedback('error', r.error || '初始化失败')
    }
  } catch (e) {
    showFeedback('error', e.message || '初始化失败')
  } finally {
    initLoading.value = false
  }
}

const handleToggle = async (path) => {
  try {
    const r = await gitAPI.toggle(props.workspaceHash, path)
    if (r.success) {
      const newState = r.data?.newState === 'changed' ? '已添加到变更' : '已移至未跟踪'
      showFeedback('success', newState + ': ' + path)
      await loadStatus()
    } else {
      showFeedback('error', r.error || '切换失败')
    }
  } catch (e) {
    showFeedback('error', e.message || '切换失败')
  }
}

const handleCommit = async () => {
  if (!commitMessage.value.trim() || committing.value || selectedCount.value === 0) return
  committing.value = true
  try {
    const files = Array.from(selectedFiles.value)
    const r = await gitAPI.commit(props.workspaceHash, commitMessage.value.trim(), files, authorName.value.trim(), authorEmail.value.trim())
    if (r.success) {
      showFeedback('success', `提交成功 (${files.length} 个文件)`)
      commitMessage.value = ''
      selectedFiles.value = new Set()
      await loadStatus()
    } else {
      showFeedback('error', r.error || '提交失败')
    }
  } catch (e) {
    showFeedback('error', e.message || '提交失败')
  } finally {
    committing.value = false
  }
}

const handleGenerateMessage = async () => {
  if (generating.value || selectedCount.value === 0) return
  generating.value = true
  try {
    const files = Array.from(selectedFiles.value)
    const r = await gitAPI.generateCommitMessage(props.workspaceHash, files, commitModel.value)
    if (r.success && r.data && r.data.message) {
      commitMessage.value = r.data.message
      showFeedback('success', `AI 已生成提交消息（基于 ${files.length} 个文件）`)
    } else {
      showFeedback('error', r.error || '生成失败')
    }
  } catch (e) {
    showFeedback('error', e.message || '生成失败')
  } finally {
    generating.value = false
  }
}

const openDiff = async (path) => {
  diffViewer.value = { open: true, file: path, diff: '', stat: '' }
  try {
    const r = await gitAPI.diffContent(props.workspaceHash, path)
    if (r.success && r.data) {
      diffViewer.value.diff = r.data.diff || ''
      diffViewer.value.stat = r.data.stat || ''
    }
  } catch (e) {
    diffViewer.value.diff = '加载 diff 失败: ' + (e.message || '')
  }
}

const closeDiffViewer = () => {
  diffViewer.value = { open: false, file: '', diff: '', stat: '' }
}

// ---- Diff 左右对比 (Side-by-Side) ----
const diffPairs = computed(() => {
  if (!diffViewer.value.diff) return []
  const pairs = parseSideBySide(diffViewer.value.diff)
  // 语法高亮：根据文件扩展名检测语言，逐行高亮
  const lang = detectLanguage(diffViewer.value.file)
  for (const p of pairs) {
    p.leftHtml = p.left ? sanitize(highlightCode(p.left, lang)) : ''
    p.rightHtml = p.right ? sanitize(highlightCode(p.right, lang)) : ''
  }
  return pairs
})

function parseSideBySide(diffText) {
  if (!diffText) return []
  const lines = diffText.split('\n')
  const result = []

  let i = 0
  // 跳过元信息行，直到第一个 hunk 头
  while (i < lines.length && !lines[i].startsWith('@@')) {
    i++
  }

  for (; i < lines.length; i++) {
    const line = lines[i]
    if (line.startsWith('@@')) {
      // @@ -oldStart[,oldCount] +newStart[,newCount] @@
      const m = line.match(/@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@/)
      if (!m) continue
      let oldNum = parseInt(m[1])
      let newNum = parseInt(m[3])
      const oldCount = m[2] !== undefined ? parseInt(m[2]) : 1
      const newCount = m[4] !== undefined ? parseInt(m[4]) : 1

      // 处理空文件 diff：新增0行，删除0行
      if (oldCount === 0 && newCount === 0) {
        // 添加一个表示空文件的行
        result.push({
          left: '',
          right: '（空文件）',
          leftLineNum: null,
          rightLineNum: null,
          type: 'empty'
        })
        continue
      }

      const removedQueue = []
      const addedQueue = []

      const flushQueues = () => {
        // 配对删/改为替换
        while (removedQueue.length > 0 && addedQueue.length > 0) {
          const r = removedQueue.shift()
          const a = addedQueue.shift()
          result.push({
            left: r.content,
            right: a.content,
            leftLineNum: r.lineNum,
            rightLineNum: a.lineNum,
            type: 'replace'
          })
        }
        // 纯删除（左栏）
        while (removedQueue.length > 0) {
          const r = removedQueue.shift()
          result.push({
            left: r.content,
            right: '',
            leftLineNum: r.lineNum,
            rightLineNum: null,
            type: 'remove'
          })
        }
        // 纯新增（右栏）
        while (addedQueue.length > 0) {
          const a = addedQueue.shift()
          result.push({
            left: '',
            right: a.content,
            leftLineNum: null,
            rightLineNum: a.lineNum,
            type: 'add'
          })
        }
      }

      let j = i + 1
      while (j < lines.length && !lines[j].startsWith('@@')) {
        const l = lines[j]
        if (l.startsWith('+') && !l.startsWith('+++')) {
          addedQueue.push({ content: l.substring(1), lineNum: newNum++ })
        } else if (l.startsWith('-') && !l.startsWith('---')) {
          removedQueue.push({ content: l.substring(1), lineNum: oldNum++ })
        } else if (l.startsWith(' ')) {
          flushQueues()
          const content = l.substring(1)
          result.push({
            left: content,
            right: content,
            leftLineNum: oldNum++,
            rightLineNum: newNum++,
            type: 'context'
          })
        }
        // 其他元信息行忽略
        j++
      }

      // 刷新队列中剩余条目
      flushQueues()
      i = j - 1
    }
  }

  return result
}

// ---- 生命周期 ----
onMounted(async () => {
  await loadStatus()
  await loadAuthorConfig()
  await loadAvailableModels()
})

watch(() => props.workspaceHash, async () => {
  if (props.workspaceHash) {
    await loadStatus()
    await loadAuthorConfig()
    await loadAvailableModels()
  }
})

// 监听模型变化，更新警告
watch(commitModel, () => {
  checkModelWarning()
})

defineExpose({ loadStatus })
</script>

<style scoped>
.git-panel {
  width: 280px;
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
.git-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--glass-border);
}
.git-head-actions { display: flex; align-items: center; gap: 2px; }
.git-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}
.git-title svg { color: var(--fg-3); }

/* 反馈 */
.git-feedback {
  padding: 6px 12px;
  font-size: 11px;
  text-align: center;
  border-bottom: 1px solid var(--border);
}
.git-feedback.success { background: #d1fae5; color: #065f46; }
.git-feedback.error { background: #fee2e2; color: #991b1b; }
[data-theme="dark"] .git-feedback.success { background: #052e16; color: #4ade80; }
[data-theme="dark"] .git-feedback.error { background: #450a0a; color: #f87171; }

/* 加载 */
.git-loading { display: flex; align-items: center; justify-content: center; padding: 24px; }
.git-error { padding: 12px; font-size: 12px; color: var(--red); text-align: center; }

/* 三态 */
.git-state-unavailable,
.git-state-uninit {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 16px;
  text-align: center;
}
.git-state-unavailable svg { color: var(--fg-4); }
.git-state-unavailable p { font-size: 13px; color: var(--fg-3); margin: 0; }
.git-state-unavailable .hint { font-size: 11px; color: var(--fg-4); }

.git-state-uninit svg { color: var(--accent); }
.git-state-uninit p { font-size: 13px; color: var(--fg-2); margin: 0; }

.init-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--fg-3);
  cursor: pointer;
}
.init-checkbox input { accent-color: var(--accent); }

.btn-init {
  margin-top: 4px;
  padding: 6px 18px;
  border: none;
  border-radius: 6px;
  background: var(--accent);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity var(--t);
}
.btn-init:hover { opacity: 0.85; }
.btn-init:disabled { opacity: 0.5; cursor: not-allowed; }

/* 分支 */
.git-branch {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
}
.git-branch svg { color: var(--fg-4); flex-shrink: 0; }
.branch-name { font-weight: 600; font-family: var(--mono); color: var(--accent); }
.change-count {
  margin-left: auto;
  background: var(--accent-bg);
  color: var(--accent);
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 8px;
}

/* 提交区域 */
.commit-area { padding: 8px 12px; border-bottom: 1px solid var(--border); }
.commit-select-all {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 11px;
  color: var(--fg-3);
}
.select-all-label {
  cursor: pointer;
  user-select: none;
}
.commit-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--bg);
  color: var(--fg);
  font-size: 12px;
  font-family: var(--mono);
  resize: vertical;
  box-sizing: border-box;
}
.commit-input::placeholder { color: var(--fg-4); }
.commit-input:focus { outline: none; border-color: var(--accent); }

/* 提交作者配置按钮 */
.commit-author-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  gap: 8px;
}
.model-select-inline {
  flex: 1;
  min-width: 0;
  -webkit-appearance: none;
  -moz-appearance: none;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10' viewBox='0 0 24 24' fill='none' stroke='%23999' stroke-width='2'%3E%3Cpolyline points='9 18 15 12 9 6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 6px center;
  padding-right: 20px;
}
.model-warning-bar {
  font-size: 10px;
  color: #e74c3c;
  margin-top: 2px;
  padding: 0 2px;
}
.author-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--bg-2);
  color: var(--fg-3);
  font-size: 11px;
  cursor: pointer;
  transition: border-color var(--t), color var(--t);
  white-space: nowrap;
}
.author-btn:hover {
  border-color: var(--accent);
  color: var(--accent);
}
.author-btn svg { flex-shrink: 0; }
.author-chevron {
  transition: transform 0.15s ease;
}
.author-btn:hover .author-chevron {
  transform: rotate(90deg);
}

/* 作者配置 Modal — 基础 modal 样式（Teleport 到 body，需在 scoped 中覆盖） */
.author-modal {
  width: min(90vw, 420px);
  max-height: 70vh;
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--glass-shadow);
}
.author-modal .modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
  font-weight: 600;
}
.author-modal .modal-body {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px 24px;
}
.author-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.author-field label {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
}
.author-field input {
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg);
  color: var(--fg);
  font-size: 13px;
  font-family: var(--mono);
  outline: none;
  transition: border-color var(--t);
}
.author-field input:focus {
  border-color: var(--accent);
}
.author-modal .modal-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  border-top: 1px solid var(--border);
}
.author-modal .modal-foot-left {
  display: flex;
  align-items: center;
}
.author-modal .modal-foot-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.author-modal .modal-foot .btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg-2);
  color: var(--fg-2);
  font-size: 12px;
  cursor: pointer;
  transition: border-color var(--t), background var(--t);
  white-space: nowrap;
}
.author-modal .modal-foot .btn-ghost:hover {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--bg-3);
}
.author-modal .modal-foot .btn-ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.author-modal .modal-foot .btn-reset {
  color: var(--fg-4);
  border-color: transparent;
  background: transparent;
  font-size: 11px;
}
.author-modal .modal-foot .btn-reset:hover {
  color: var(--accent);
  border-color: var(--border);
  background: var(--bg-2);
}
.author-modal .modal-foot .btn-primary {
  padding: 6px 20px;
  border: none;
  border-radius: 6px;
  background: var(--accent);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity var(--t);
}
.author-modal .modal-foot .btn-primary:hover {
  opacity: 0.85;
}

.commit-actions {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}
.generate-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--bg-2);
  color: var(--fg-2);
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--t), border-color var(--t);
  white-space: nowrap;
  flex-shrink: 0;
}
.generate-btn:hover { background: var(--bg-3); border-color: var(--accent); color: var(--accent); }
.generate-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.generate-btn svg { flex-shrink: 0; }
.generate-spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 2px solid var(--fg-4);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: gen-spin 0.6s linear infinite;
  flex-shrink: 0;
}
@keyframes gen-spin {
  to { transform: rotate(360deg); }
}
.commit-button {
  flex: 1;
  padding: 5px 0;
  border: none;
  border-radius: 4px;
  background: var(--accent);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity var(--t);
}
.commit-button:hover { opacity: 0.85; }
.commit-button:disabled { opacity: 0.4; cursor: not-allowed; }

/* 文件列表 */
/* 主体可滚动区 */
.git-body { flex: 1; overflow-y: auto; }
.git-files { padding: 4px 0; }
.git-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--fg-4);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  cursor: pointer;
  user-select: none;
}
.git-section-header:hover { color: var(--fg-2); }
.git-section-header.staged { color: var(--green); }
.git-section-header.untracked { color: var(--fg-4); }
.section-left { display: flex; align-items: center; gap: 5px; }
.chevron { transition: transform 0.15s ease; flex-shrink: 0; }
.chevron.open { transform: rotate(90deg); }
.section-count { font-size: 10px; background: var(--bg-3); padding: 0 5px; border-radius: 8px; font-weight: 600; }

.git-file {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: background var(--t);
}
.git-file:hover { background: var(--bg-3); }

.file-status {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  border-radius: 3px;
}
.file-status.M { background: #fef3c7; color: #92400e; }
.file-status.A { background: #d1fae5; color: #065f46; }
.file-status.D { background: #fee2e2; color: #991b1b; }
.file-status.R { background: #dbeafe; color: #1e40af; }
.file-status.U { background: #f3e8ff; color: #6b21a8; }

[data-theme="dark"] .file-status.M { background: #422006; color: #fbbf24; }
[data-theme="dark"] .file-status.A { background: #052e16; color: #4ade80; }
[data-theme="dark"] .file-status.D { background: #450a0a; color: #f87171; }
[data-theme="dark"] .file-status.R { background: #172554; color: #60a5fa; }
[data-theme="dark"] .file-status.U { background: #3b0764; color: #c084fc; }

[data-theme="retro"] .file-status.M { background: #FFF3E0; color: #E65100; }
[data-theme="retro"] .file-status.A { background: #E8F5E9; color: #2E7D32; }
[data-theme="retro"] .file-status.D { background: #FFEBEE; color: #C62828; }
[data-theme="retro"] .file-status.R { background: #E3F2FD; color: #1565C0; }
[data-theme="retro"] .file-status.U { background: #F3E5F5; color: #6A1B9A; }

[data-theme="retro-yellow"] .file-status.M { background: #f5ecd0; color: #8b6914; }
[data-theme="retro-yellow"] .file-status.A { background: #e8eddf; color: #4a6741; }
[data-theme="retro-yellow"] .file-status.D { background: #f5e0d8; color: #8b2500; }
[data-theme="retro-yellow"] .file-status.R { background: #e0e8f0; color: #4a5a7a; }
[data-theme="retro-yellow"] .file-status.U { background: #ede0f5; color: #6b3a8a; }

.git-checkbox {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: var(--accent);
}

.file-path {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--fg-2);
  font-family: var(--mono);
}

.file-action-btn {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border: none;
  border-radius: 3px;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity var(--t);
}
.git-file:hover .file-action-btn { opacity: 1; }
.toggle-btn { background: #fee2e2; color: #991b1b; }
.toggle-btn.add { background: #d1fae5; color: #065f46; }
[data-theme="dark"] .toggle-btn { background: #450a0a; color: #f87171; }
[data-theme="dark"] .toggle-btn.add { background: #052e16; color: #4ade80; }

/* 提交历史 */
.git-history { border-top: 1px solid var(--border); }
.git-section-header.history { padding: 7px 12px; }
.git-history-loading { display: flex; justify-content: center; padding: 12px; }
.git-history-empty { padding: 12px; font-size: 11px; color: var(--fg-4); text-align: center; }
.git-commit-list { max-height: 240px; overflow-y: auto; }
.git-commit-item {
  padding: 7px 12px;
  border-bottom: 1px solid var(--border);
  cursor: default;
  transition: background var(--t);
}
.git-commit-item:hover { background: var(--bg-3); }
.commit-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}
.commit-hash {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--accent);
  background: var(--accent-bg);
  padding: 1px 5px;
  border-radius: 3px;
}
.commit-date { font-size: 10px; color: var(--fg-4); margin-left: auto; }
.commit-message {
  font-size: 12px;
  color: var(--fg-2);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.commit-author {
  font-size: 10px;
  color: var(--fg-4);
  margin-top: 1px;
}

/* 空状态 */
.git-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 24px 12px;
  font-size: 12px;
  color: var(--fg-4);
}
.git-empty svg { color: var(--green); }

/* Diff 预览弹层 - 左右对比 */
.diff-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
}
.diff-viewer {
  width: min(90vw, 800px);
  max-height: 80vh;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
}
.diff-viewer-sbs {
  width: min(95vw, 1200px);
}
.diff-viewer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-2);
}
.diff-viewer-file { font-size: 12px; font-family: var(--mono); color: var(--fg); font-weight: 600; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.diff-viewer-stat { font-size: 11px; color: var(--fg-4); white-space: nowrap; }

/* Side-by-Side 表格 */
.diff-sbs {
  flex: 1;
  overflow-y: auto;
  font-size: 12px;
  font-family: var(--mono);
  line-height: 1.6;
  background: var(--bg);
}
.diff-sbs-header {
  display: flex;
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
}
.diff-sbs-label {
  flex: 1;
  padding: 4px 8px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  text-align: center;
}
.diff-sbs-label-old { color: var(--fg-4); border-right: 1px solid var(--border); }
.diff-sbs-label-new { color: var(--fg-4); }

.diff-sbs-row {
  display: flex;
  min-height: 20px;
  border-bottom: 1px solid var(--border-muted);
}
.diff-sbs-row:last-child { border-bottom: none; }

.diff-sbs-cell {
  flex: 1;
  display: flex;
  align-items: stretch;
  min-width: 0;
}
.diff-sbs-gutter {
  width: 1px;
  background: var(--border);
  flex-shrink: 0;
}

.diff-sbs-ln {
  flex-shrink: 0;
  width: 40px;
  padding: 0 6px;
  text-align: right;
  font-size: 10px;
  color: var(--fg-4);
  background: var(--bg-2);
  user-select: none;
  border-right: 1px solid var(--border-muted);
  line-height: 1.6;
}
.diff-sbs-code {
  flex: 1;
  padding: 0 8px;
  white-space: pre-wrap;
  word-break: break-all;
  min-width: 0;
}

/* 变更行高亮 — 背景区分，文字颜色由 highlight.js 控制 */
.diff-sbs-context .diff-sbs-cell { background: transparent; }
.diff-sbs-context .diff-sbs-ln { background: var(--bg-2); }

/* 空文件 diff */
.diff-sbs-empty .diff-sbs-cell {
  background: rgba(128, 128, 128, 0.05);
}
.diff-sbs-empty .diff-sbs-code {
  color: var(--fg-4);
  font-style: italic;
}

.diff-sbs-add .diff-sbs-cell-right {
  background: rgba(16, 185, 129, 0.10);
}
.diff-sbs-add .diff-sbs-cell-left .diff-sbs-code { color: var(--fg-4); }

.diff-sbs-remove .diff-sbs-cell-left {
  background: rgba(239, 68, 68, 0.10);
}
.diff-sbs-remove .diff-sbs-cell-right .diff-sbs-code { color: var(--fg-4); }

.diff-sbs-replace .diff-sbs-cell-left {
  background: rgba(239, 68, 68, 0.10);
}
.diff-sbs-replace .diff-sbs-cell-right {
  background: rgba(16, 185, 129, 0.10);
}

[data-theme="dark"] .diff-sbs-add .diff-sbs-cell-right { background: rgba(16, 185, 129, 0.08); }
[data-theme="dark"] .diff-sbs-remove .diff-sbs-cell-left { background: rgba(239, 68, 68, 0.08); }
[data-theme="dark"] .diff-sbs-replace .diff-sbs-cell-left { background: rgba(239, 68, 68, 0.08); }
[data-theme="dark"] .diff-sbs-replace .diff-sbs-cell-right { background: rgba(16, 185, 129, 0.08); }

[data-theme="retro"] .diff-sbs-add .diff-sbs-cell-right { background: rgba(51, 255, 51, 0.08); }
[data-theme="retro"] .diff-sbs-remove .diff-sbs-cell-left { background: rgba(255, 102, 102, 0.08); }
[data-theme="retro"] .diff-sbs-replace .diff-sbs-cell-left { background: rgba(255, 102, 102, 0.08); }
[data-theme="retro"] .diff-sbs-replace .diff-sbs-cell-right { background: rgba(51, 255, 51, 0.08); }

[data-theme="retro-yellow"] .diff-sbs-add .diff-sbs-cell-right { background: rgba(74, 103, 65, 0.10); }
[data-theme="retro-yellow"] .diff-sbs-remove .diff-sbs-cell-left { background: rgba(139, 37, 0, 0.08); }
[data-theme="retro-yellow"] .diff-sbs-replace .diff-sbs-cell-left { background: rgba(139, 37, 0, 0.08); }
[data-theme="retro-yellow"] .diff-sbs-replace .diff-sbs-cell-right { background: rgba(74, 103, 65, 0.10); }

.diff-viewer-empty { padding: 32px; text-align: center; font-size: 12px; color: var(--fg-4); }
</style>
