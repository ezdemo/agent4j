<template>
  <div class="git-panel">
    <div class="git-head">
      <div class="git-title">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M13 6h3a2 2 0 0 1 2 2v7"/><line x1="6" y1="9" x2="6" y2="21"/></svg>
        <span>源代码管理</span>
      </div>
      <div class="git-head-actions">
        <button class="btn-icon-sm" @click="load" title="刷新">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
        </button>
        <button class="btn-icon-sm" @click="$emit('close')" title="关闭">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    </div>

    <!-- 分支信息 -->
    <div class="git-branch" v-if="branch && branch !== 'unknown'">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="6" y1="3" x2="6" y2="15"/><circle cx="18" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M18 9a9 9 0 0 1-9 9"/></svg>
      <span class="branch-name">{{ branch }}</span>
      <span class="change-count" v-if="hasChanges">{{ staged.length + unstaged.length + untracked.length }}</span>
    </div>

    <div class="git-branch" v-else-if="!loading && !error">
      <span class="branch-empty">非 Git 仓库</span>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="git-loading">
      <div class="loading-spinner"></div>
    </div>

    <!-- 错误 -->
    <div v-else-if="error" class="git-error">{{ error }}</div>

    <!-- 文件列表 -->
    <div v-else-if="hasChanges" class="git-files">
      <!-- 未暂存变更（默认展开） -->
      <template v-if="unstaged.length">
        <div class="git-section-header" @click="showUnstaged = !showUnstaged">
          <div class="section-left">
            <svg class="chevron" :class="{ open: showUnstaged }" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
            <span>未暂存的变更</span>
          </div>
          <span class="section-count">{{ unstaged.length }}</span>
        </div>
        <template v-if="showUnstaged">
          <div v-for="f in unstaged" :key="'u-'+f.path" class="git-file">
            <span class="file-status" :class="f.status">{{ f.status }}</span>
            <span class="file-path" :title="f.path">{{ f.path }}</span>
          </div>
        </template>
      </template>
      <!-- 已暂存变更（默认折叠） -->
      <template v-if="staged.length">
        <div class="git-section-header" @click="showStaged = !showStaged">
          <div class="section-left">
            <svg class="chevron" :class="{ open: showStaged }" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
            <span>已暂存的变更</span>
          </div>
          <span class="section-count">{{ staged.length }}</span>
        </div>
        <template v-if="showStaged">
          <div v-for="f in staged" :key="'s-'+f.path" class="git-file">
            <span class="file-status" :class="f.status">{{ f.status }}</span>
            <span class="file-path" :title="f.path">{{ f.path }}</span>
          </div>
        </template>
      </template>
      <!-- 未跟踪文件（默认折叠） -->
      <template v-if="untracked.length">
        <div class="git-section-header" @click="showUntracked = !showUntracked">
          <div class="section-left">
            <svg class="chevron" :class="{ open: showUntracked }" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
            <span>未跟踪文件</span>
          </div>
          <span class="section-count">{{ untracked.length }}</span>
        </div>
        <template v-if="showUntracked">
          <div v-for="f in untracked" :key="'n-'+f.path" class="git-file">
            <span class="file-status U">?</span>
            <span class="file-path" :title="f.path">{{ f.path }}</span>
          </div>
        </template>
      </template>
    </div>

    <!-- 空状态 -->
    <div v-else-if="branch && branch !== 'unknown'" class="git-empty">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
      <span>工作区干净</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { gitAPI } from '../services/api'

const props = defineProps({
  workspaceHash: { type: String, default: null }
})

defineEmits(['close'])

const branch = ref('')
const staged = ref([])
const unstaged = ref([])
const untracked = ref([])
const loading = ref(false)
const error = ref('')
const showUnstaged = ref(true)
const showStaged = ref(false)
const showUntracked = ref(false)

const hasChanges = computed(() => staged.value.length || unstaged.value.length || untracked.value.length)

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const r = await gitAPI.diff(props.workspaceHash)
    if (r.success && r.data) {
      branch.value = r.data.branch || ''
      staged.value = r.data.staged || []
      unstaged.value = r.data.unstaged || []
      untracked.value = r.data.untracked || []
    } else {
      error.value = r.error || '加载失败'
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

// 工作区切换时刷新
watch(() => props.workspaceHash, () => {
  if (props.workspaceHash) load()
})
</script>

<style scoped>
.git-panel {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-2);
  border-left: 1px solid var(--border);
  overflow: hidden;
}

.git-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
}

.git-head-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.git-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}
.git-title svg { color: var(--fg-3); }

.git-branch {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
}
.git-branch svg { color: var(--fg-4); flex-shrink: 0; }

.branch-name {
  font-weight: 600;
  font-family: var(--mono);
  color: var(--accent);
}
.branch-empty {
  color: var(--fg-4);
}
.change-count {
  margin-left: auto;
  background: var(--accent-bg);
  color: var(--accent);
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 8px;
}

.git-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.git-error {
  padding: 12px;
  font-size: 12px;
  color: var(--red);
  text-align: center;
}

.git-files {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

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
.section-left {
  display: flex;
  align-items: center;
  gap: 5px;
}
.chevron {
  transition: transform 0.15s ease;
  flex-shrink: 0;
}
.chevron.open {
  transform: rotate(90deg);
}
.section-count {
  font-size: 10px;
  background: var(--bg-3);
  padding: 0 5px;
  border-radius: 8px;
  font-weight: 600;
}

.git-file {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  font-size: 12px;
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

[data-theme="retro"] .file-status.M { background: #1a1500; color: #ccaa33; }
[data-theme="retro"] .file-status.A { background: #0a1f0a; color: #33ff33; }
[data-theme="retro"] .file-status.D { background: #1a0505; color: #ff6666; }
[data-theme="retro"] .file-status.R { background: #0a0e1a; color: #66aaff; }
[data-theme="retro"] .file-status.U { background: #150a1a; color: #cc66ff; }

[data-theme="retro-yellow"] .file-status.M { background: #f5ecd0; color: #8b6914; }
[data-theme="retro-yellow"] .file-status.A { background: #e8eddf; color: #4a6741; }
[data-theme="retro-yellow"] .file-status.D { background: #f5e0d8; color: #8b2500; }
[data-theme="retro-yellow"] .file-status.R { background: #e0e8f0; color: #4a5a7a; }
[data-theme="retro-yellow"] .file-status.U { background: #ede0f5; color: #6b3a8a; }

.file-path {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--fg-2);
  font-family: var(--mono);
}

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
</style>
