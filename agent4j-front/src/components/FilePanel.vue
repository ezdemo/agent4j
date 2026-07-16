<template>
  <div class="file-panel">
    <div class="file-search">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="6"/><path d="m16 16 4 4"/></svg>
      <input v-model="query" type="search" placeholder="筛选文件..." aria-label="筛选文件" />
      <button v-if="query" type="button" class="clear-search" title="清除筛选" @click="query = ''">×</button>
    </div>

    <div v-if="loading" class="file-state"><span class="loading-spinner"></span></div>
    <div v-else-if="error" class="file-state file-error">{{ error }}</div>
    <div v-else-if="!workspaceHash" class="file-state">请选择一个项目</div>
    <div v-else class="file-tree" role="tree" aria-label="项目文件">
      <FileTreeNode
        v-for="node in rootNodes"
        :key="node.path"
        :node="node"
        :query="query"
        :selected-path="selectedPath"
        @toggle="toggleNode"
      />
      <div v-if="rootNodes.length === 0" class="file-state">项目文件夹为空</div>
    </div>

    <DiffViewer
      :open="diffViewer.open"
      :file="diffViewer.file"
      :diff="diffViewer.diff"
      :content="diffViewer.content"
      :mode="diffViewer.mode"
      :loading="diffViewer.loading"
      @close="closeDiffViewer"
      @change-mode="changeDiffViewerMode"
      @add-to-session="$emit('addToSession', $event)"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { filesAPI, gitAPI } from '../services/api'
import FileTreeNode from './FileTreeNode.vue'
import DiffViewer from './DiffViewer.vue'

const props = defineProps({ workspaceHash: { type: String, default: null } })
defineEmits(['addToSession'])

const rootNodes = ref([])
const query = ref('')
const loading = ref(false)
const error = ref('')
const selectedPath = ref('')
const diffViewer = ref({ open: false, file: '', diff: '', content: '', mode: 'content', loading: false, contentLoaded: false, diffLoaded: false })

function toNode(entry) {
  return { ...entry, children: [], loaded: false, loading: false, expanded: false }
}

async function loadDirectory(path = '', target = null) {
  if (!props.workspaceHash) return
  if (target) target.loading = true
  else loading.value = true
  error.value = ''
  try {
    const response = await filesAPI.list(props.workspaceHash, path)
    if (!response.success) throw new Error(response.error || '读取项目文件失败')
    const entries = (response.data || []).map(toNode)
    if (target) {
      target.children = entries
      target.loaded = true
    } else {
      rootNodes.value = entries
    }
  } catch (e) {
    error.value = e.message || '读取项目文件失败'
  } finally {
    if (target) target.loading = false
    else loading.value = false
  }
}

async function toggleNode(node) {
  selectedPath.value = node.path
  if (!node.directory) {
    await openFile(node.path)
    return
  }
  node.expanded = !node.expanded
  if (node.expanded) await expandCompactDirectoryChain(node)
}

async function expandCompactDirectoryChain(node) {
  let current = node
  // 单一路径自动展开，最多 32 层，防止异常目录结构导致无休止请求。
  for (let depth = 0; depth < 32; depth++) {
    if (!current.loaded) await loadDirectory(current.path, current)
    if (current.children.length !== 1 || !current.children[0].directory) return
    current = current.children[0]
    current.expanded = true
  }
}

async function loadDiffViewerContent() {
  const { file } = diffViewer.value
  if (!file) return
  diffViewer.value.loading = true
  try {
    const response = await gitAPI.workingFileContent(props.workspaceHash, file)
    if (diffViewer.value.open && diffViewer.value.file === file && response.success && response.data) {
      diffViewer.value.content = response.data.content ?? response.data.message ?? ''
      diffViewer.value.contentLoaded = true
    }
  } catch (e) {
    if (diffViewer.value.open && diffViewer.value.file === file) {
      diffViewer.value.content = '加载文件失败: ' + (e.message || '')
      diffViewer.value.contentLoaded = true
    }
  } finally {
    if (diffViewer.value.open && diffViewer.value.file === file) diffViewer.value.loading = false
  }
}

async function loadDiffViewerDiff() {
  const { file } = diffViewer.value
  if (!file) return
  diffViewer.value.loading = true
  try {
    const response = await gitAPI.diffContent(props.workspaceHash, file)
    if (diffViewer.value.open && diffViewer.value.file === file && response.success && response.data) {
      diffViewer.value.diff = response.data.diff || ''
      diffViewer.value.diffLoaded = true
    }
  } catch (e) {
    if (diffViewer.value.open && diffViewer.value.file === file) {
      diffViewer.value.diff = '加载 Git diff 失败: ' + (e.message || '')
      diffViewer.value.diffLoaded = true
    }
  } finally {
    if (diffViewer.value.open && diffViewer.value.file === file) diffViewer.value.loading = false
  }
}

async function openFile(file) {
  diffViewer.value = { open: true, file, diff: '', content: '', mode: 'content', loading: true, contentLoaded: false, diffLoaded: false }
  await loadDiffViewerContent()
}

async function changeDiffViewerMode(mode) {
  if (!diffViewer.value.open || diffViewer.value.mode === mode) return
  diffViewer.value.mode = mode
  if (mode === 'content' && !diffViewer.value.contentLoaded) await loadDiffViewerContent()
  if (mode === 'diff' && !diffViewer.value.diffLoaded) await loadDiffViewerDiff()
}

function closeDiffViewer() {
  diffViewer.value = { open: false, file: '', diff: '', content: '', mode: 'content', loading: false, contentLoaded: false, diffLoaded: false }
}

function refresh() {
  rootNodes.value = []
  selectedPath.value = ''
  loadDirectory()
}

watch(() => props.workspaceHash, refresh, { immediate: true })

defineExpose({ refresh })
</script>

<style scoped>
.file-panel { display: flex; flex: 1; min-height: 0; flex-direction: column; color: var(--fg); }
.file-search { display: flex; align-items: center; gap: 8px; margin: 10px; min-height: 34px; padding: 0 10px; border: 1px solid var(--border); border-radius: 6px; background: var(--bg); color: var(--fg-4); }
.file-search input { min-width: 0; flex: 1; border: 0; outline: 0; background: transparent; color: var(--fg); font: inherit; font-size: 13px; }
.file-search input::placeholder { color: var(--fg-4); }
.clear-search { width: 18px; height: 18px; padding: 0; border: 0; border-radius: 3px; background: transparent; color: var(--fg-4); cursor: pointer; font-size: 18px; line-height: 16px; }
.clear-search:hover { background: var(--bg-3); color: var(--fg); }
.file-tree { min-height: 0; flex: 1; overflow: auto; padding: 0 4px 10px; }
.file-tree-node { min-width: 0; }
.file-tree-row { display: flex; align-items: center; width: 100%; min-height: 30px; padding-right: 8px; border: 0; border-radius: 4px; background: transparent; color: var(--fg-2); cursor: pointer; font: inherit; font-size: 13px; text-align: left; }
.file-tree-row:hover, .file-tree-row.active { background: var(--bg-3); color: var(--fg); }
.tree-chevron, .tree-indent { width: 14px; height: 14px; flex: 0 0 14px; margin-right: 3px; color: var(--fg-4); transition: transform .15s; }
.tree-chevron.expanded { transform: rotate(90deg); }
.file-icon { flex: 0 0 16px; margin-right: 7px; color: var(--fg-4); }
.folder-icon { color: #c6963b; }
.code-file { color: #e4bd42; }.source-file { color: #5d9cec; }.text-file { color: #54a874; }.image-file { color: #d16bb1; }
.file-tree-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tree-loading { width: 11px; height: 11px; margin-left: auto; border: 2px solid var(--border); border-top-color: var(--fg-3); border-radius: 50%; animation: tree-spin .7s linear infinite; }
.tree-empty { min-height: 26px; color: var(--fg-4); font-size: 12px; line-height: 26px; }
.file-state { display: flex; flex: 1; align-items: center; justify-content: center; padding: 24px; color: var(--fg-4); font-size: 13px; text-align: center; }
.file-error { color: var(--red); }
@keyframes tree-spin { to { transform: rotate(360deg); } }
</style>
