<template>
  <div class="file-explorer">
    <!-- VS Code Explorer 风格标题栏 + 操作按钮（hover 标题栏才显示，对齐 VS Code pane-header） -->
    <div class="fe-head">
      <span class="fe-title">文件</span>
      <div class="fe-head-actions">
        <button type="button" class="fe-head-action" title="刷新" aria-label="刷新" @click="refresh">
          <i class="codicon codicon-refresh"></i>
        </button>
        <button type="button" class="fe-head-action" title="折叠全部" aria-label="折叠全部" @click="collapseAll">
          <i class="codicon codicon-collapse-all"></i>
        </button>
      </div>
    </div>

    <!-- 搜索输入框（常驻） -->
    <div class="fe-search">
      <input v-model="query" type="search" placeholder="搜索文件..." aria-label="搜索文件" />
      <button v-if="query" type="button" class="fe-search-clear" title="清除搜索" @click="query = ''">×</button>
    </div>

    <!-- 主体：搜索模式显示结果列表，否则显示目录树 -->
    <div class="fe-body">
      <div v-if="loading" class="fe-state"><span class="loading-spinner"></span></div>
      <div v-else-if="error" class="fe-state fe-error">{{ error }}</div>
      <div v-else-if="!rootPath" class="fe-state">请选择一个项目</div>
      <template v-else-if="searchResults !== null">
        <div v-if="searching" class="fe-state"><span class="loading-spinner"></span></div>
        <div v-else-if="searchResults.length === 0" class="fe-state">未找到匹配的文件</div>
        <div v-else class="fe-tree" role="listbox" aria-label="文件搜索结果">
          <div
            v-for="result in searchResults"
            :key="result.path"
            class="fen-row fe-search-result"
            :class="{ active: result.path === selectedPath }"
            role="option"
            :title="result.path"
            :draggable="!result.directory"
            @click="selectResult(result)"
            @dblclick="openSearchResult(result)"
            @contextmenu.prevent.stop="openContextMenu($event, result)"
            @dragstart="startFileDrag($event, result)"
          >
            <span class="fen-twistie-placeholder"></span>
            <i class="codicon codicon-file fen-icon"></i>
            <span class="fen-name">{{ result.name }}</span>
            <span class="fe-result-path">{{ result.path }}</span>
          </div>
        </div>
      </template>
      <div v-else class="fe-tree" role="tree" aria-label="项目文件">
        <FileExplorerNode
          v-for="node in rootNodes"
          :key="node.path || node.uid"
          :node="node"
          :depth="0"
          :selected-path="selectedPath"
          :decorations="decorations"
          @toggle="toggleNode"
          @dblclick="onDblclick"
          @contextmenu="openContextMenu"
          @edit-commit="commitEdit"
          @edit-cancel="cancelEdit"
        />
        <div v-if="rootNodes.length === 0" class="fe-state">项目文件夹为空</div>
      </div>
    </div>

    <!-- 右键菜单 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="fe-context-menu"
        role="menu"
        aria-label="文件操作菜单"
        :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
        @contextmenu.prevent
      >
        <template v-if="contextMenu.node && !contextMenu.node.directory">
          <button type="button" role="menuitem" @click="contextAction('add-to-session')">
            <i class="codicon codicon-comment-add"></i>
            添加到对话
          </button>
          <button type="button" role="menuitem" @click="contextAction('open')">
            <i class="codicon codicon-open-preview"></i>
            打开预览
          </button>
        </template>
        <button v-if="contextMenu.node" type="button" role="menuitem" @click="contextAction('reveal')">
          <i class="codicon codicon-link-external"></i>
          在文件管理器中显示
        </button>
        <button v-if="contextMenu.node" type="button" role="menuitem" @click="contextAction('rename')">
          <i class="codicon codicon-edit"></i>
          重命名
        </button>
        <button v-if="contextMenu.node" type="button" role="menuitem" class="danger" @click="contextAction('delete')">
          <i class="codicon codicon-trash"></i>
          删除
        </button>
        <button v-if="contextMenu.node?.directory" type="button" role="menuitem" @click="contextAction('refresh')">
          <i class="codicon codicon-refresh"></i>
          刷新
        </button>
      </div>
    </Teleport>

    <!-- 删除确认 -->
    <ActionConfirmDialog
      :model-value="deleteConfirm.visible"
      :title="deleteConfirm.node?.directory ? '删除目录？' : '删除文件？'"
      :message="deleteConfirm.node ? `“${deleteConfirm.node.name}”将被永久删除，无法恢复。` : ''"
      :actions="deleteConfirmActions"
      @update:model-value="dismissDeleteConfirm"
      @action="handleDeleteConfirmAction"
    />
  </div>
</template>

<script setup>
import {onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue'
import {message} from 'ant-design-vue'
import {gitAPI} from '../services/api'
import ActionConfirmDialog from './ActionConfirmDialog.vue'
import FileExplorerNode from './FileExplorerNode.vue'

const props = defineProps({
  // 工作区绝对路径（桌面端由宿主传入，直接走 Electron 文件系统）
  rootPath: { type: String, default: '' },
  workspaceHash: { type: String, default: null }
})
const emit = defineEmits(['addToSession', 'openFile', 'fileDeleted', 'fileRenamed'])

const rootNodes = ref([])
const decorations = ref({})
const selectedPath = ref('')
const loading = ref(false)
const error = ref('')
const query = ref('')
const searchResults = ref(null)
const searching = ref(false)
let searchTimer = null
let fileChangeTimer = null
let stopFileChangeListener = null
let watchRequestSeq = 0
let treeReloadSeq = 0
let searchRequestSeq = 0
const contextMenu = reactive({ visible: false, x: 0, y: 0, node: null })
// 删除确认对话框（系统统一 ActionConfirmDialog）
const deleteConfirm = reactive({ visible: false, node: null })
const deleteConfirmActions = [
  { key: 'cancel', label: '取消' },
  { key: 'confirm', label: '删除', variant: 'danger' }
]

let uidSeq = 0

const explorerAPI = () => window.electronAPI?.fileExplorer

function normalizePath(path) {
  return String(path || '').replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
}

function decorationFor(status) {
  const label = String(status || 'M').toUpperCase()
  if (label === 'U' || label === '?') return { label: 'U', kind: 'untracked' }
  if (label === 'A') return { label, kind: 'added' }
  if (label === 'D') return { label, kind: 'deleted' }
  return { label, kind: 'modified' }
}

async function loadGitDecorations() {
  if (!props.workspaceHash || !props.rootPath) {
    decorations.value = {}
    return
  }
  try {
    const response = await gitAPI.status(props.workspaceHash)
    if (!response?.success || !response.data?.initialized) {
      decorations.value = {}
      return
    }
    const next = {}
    const root = normalizePath(props.rootPath)
    const files = [...(response.data.changed || []), ...(response.data.untracked || [])]
    for (const file of files) {
      const relative = String(file.path || '').replace(/\\/g, '/').replace(/^\/+/, '')
      if (!relative) continue
      const decoration = decorationFor(file.status)
      let current = normalizePath(`${props.rootPath}/${relative}`)
      next[current] = decoration
      while (current.startsWith(`${root}/`)) {
        current = current.slice(0, current.lastIndexOf('/'))
        if (!next[current]) next[current] = { label: '', kind: decoration.kind }
        if (current === root) break
      }
    }
    decorations.value = next
  } catch {
    decorations.value = {}
  }
}

function toNode(entry) {
  return { ...entry, children: [], loaded: false, loading: false, expanded: false, uid: `n${++uidSeq}` }
}

async function readDirectoryNodes(dirPath) {
  const response = await explorerAPI()?.list(dirPath)
  if (!response?.success) throw new Error(response?.error || '读取目录失败')
  return (response.data || []).map(toNode)
}

// ── 目录加载（懒加载） ──
async function loadRoot() {
  if (!props.rootPath) {
    rootNodes.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    rootNodes.value = await readDirectoryNodes(props.rootPath)
  } catch (e) {
    error.value = e.message || '读取项目文件失败'
  } finally {
    loading.value = false
  }
}

async function loadDirectory(node) {
  if (!node || node.loading) return
  node.loading = true
  try {
    node.children = await readDirectoryNodes(node.path)
    node.loaded = true
  } catch (e) {
    message.error('读取目录失败：' + (e.message || '未知错误'))
  } finally {
    node.loading = false
  }
}

function toggleNode(node) {
  if (!node.directory) {
    // 单击文件仅选中（VS Code 风格），双击打开编辑器标签
    selectedPath.value = node.path
    return
  }
  selectedPath.value = node.path
  node.expanded = !node.expanded
  if (node.expanded) void expandCompactDirectoryChain(node)
}

async function expandCompactDirectoryChain(node) {
  let current = node
  // 单一路径自动展开，最多 32 层，防止异常目录结构导致无休止请求
  for (let depth = 0; depth < 32; depth++) {
    if (!current.loaded) await loadDirectory(current)
    if (current.children.length !== 1 || !current.children[0].directory) return
    current = current.children[0]
    current.expanded = true
  }
}

// 双击文件在中间编辑器区域打开标签页（VS Code 风格）
function onDblclick(node) {
  if (!node.directory) {
    selectedPath.value = node.path
    emit('openFile', node.path)
  }
}

// ── 重命名（内联编辑） ──
function startRename(node) {
  node.editing = true
  node.editValue = node.name
  node.editCancelled = false
}

async function commitEdit(node) {
  if (node.editCancelled) {
    node.editCancelled = false
    return
  }
  const name = String(node.editValue ?? '').trim()
  if (!name || name === '.' || name === '..' || name.includes('/') || name.includes('\\')) {
    message.error('名称不能为空且不能包含路径分隔符')
    return
  }
  // 重命名
  if (name === node.name) {
    node.editing = false
    return
  }
  try {
    const response = await explorerAPI()?.rename(node.path, name)
    if (!response?.success) throw new Error(response?.error || '重命名失败')
    const oldPath = node.path
    node.name = response.data.name
    node.path = response.data.path
    node.editing = false
    if (selectedPath.value === oldPath) selectedPath.value = node.path
    emit('fileRenamed', oldPath, node.path)
  } catch (e) {
    message.error('重命名失败：' + (e.message || '未知错误'))
  }
}

function cancelEdit(node) {
  node.editCancelled = true
  node.editing = false
}

// ── 删除 ──
function removeNode(node) {
  deleteConfirm.node = node
  deleteConfirm.visible = true
}

function dismissDeleteConfirm() {
  deleteConfirm.visible = false
  deleteConfirm.node = null
}

async function handleDeleteConfirmAction(key) {
  if (key !== 'confirm') return dismissDeleteConfirm()
  const node = deleteConfirm.node
  if (!node) return dismissDeleteConfirm()
  try {
    const response = await explorerAPI()?.remove(node.path)
    if (!response?.success) throw new Error(response?.error || '删除失败')
    removeFromTree(rootNodes.value, node)
    if (selectedPath.value === node.path) selectedPath.value = ''
    emit('fileDeleted', node.path)
    message.success('已删除')
  } catch (e) {
    message.error('删除失败：' + (e.message || '未知错误'))
  } finally {
    dismissDeleteConfirm()
  }
}

function removeFromTree(nodes, target) {
  for (let i = 0; i < nodes.length; i++) {
    if (nodes[i] === target) {
      nodes.splice(i, 1)
      return true
    }
    if (nodes[i].children && removeFromTree(nodes[i].children, target)) return true
  }
  return false
}

// ── 刷新 / 折叠 ──
function collectExpandedPaths(nodes, paths = new Set()) {
  for (const node of nodes) {
    if (!node.directory) continue
    if (node.expanded) paths.add(normalizePath(node.path))
    collectExpandedPaths(node.children || [], paths)
  }
  return paths
}

async function restoreExpandedPaths(nodes, expandedPaths) {
  for (const node of nodes) {
    if (!node.directory || !expandedPaths.has(normalizePath(node.path))) continue
    node.expanded = true
    node.children = await readDirectoryNodes(node.path)
    node.loaded = true
    await restoreExpandedPaths(node.children, expandedPaths)
  }
}

async function reloadTree({preserveExpanded = true} = {}) {
  if (!props.rootPath) return
  const seq = ++treeReloadSeq
  const rootPath = props.rootPath
  const expandedPaths = preserveExpanded ? collectExpandedPaths(rootNodes.value) : new Set()
  try {
    const nextNodes = await readDirectoryNodes(rootPath)
    if (expandedPaths.size > 0) await restoreExpandedPaths(nextNodes, expandedPaths)
    if (seq === treeReloadSeq && rootPath === props.rootPath) {
      rootNodes.value = nextNodes
      error.value = ''
    }
  } catch (e) {
    if (seq === treeReloadSeq && rootPath === props.rootPath) {
      error.value = e.message || '读取项目文件失败'
    }
  }
}

function refresh() {
  if (!props.rootPath) return
  if (searchResults.value !== null) {
    searchResults.value = null
    query.value = ''
  }
  void reloadTree()
  void loadGitDecorations()
}

function isIgnoredFileSystemPath(relativePath) {
  const firstSegment = String(relativePath || '').replace(/\\/g, '/').split('/')[0].toLowerCase()
  return firstSegment === '.git' || firstSegment === '.loopra'
}

function handleFileSystemChange(event) {
  if (normalizePath(event?.rootPath) !== normalizePath(props.rootPath) || isIgnoredFileSystemPath(event?.path)) return
  clearTimeout(fileChangeTimer)
  fileChangeTimer = setTimeout(() => {
    void reloadTree()
    void loadGitDecorations()
  }, 120)
}

async function startFileWatcher() {
  const seq = ++watchRequestSeq
  const api = explorerAPI()
  await api?.unwatch?.()
  if (seq !== watchRequestSeq || !props.rootPath) return
  await api?.watch?.(props.rootPath)
}

function collapseAll() {
  const walk = (nodes) => {
    for (const node of nodes) {
      if (node.directory) {
        node.expanded = false
        walk(node.children || [])
      }
    }
  }
  walk(rootNodes.value)
}

// ── 搜索（Electron fs，不接后端） ──
watch(query, (value) => {
  const keyword = value.trim()
  if (!keyword) {
    clearTimeout(searchTimer)
    searchRequestSeq++
    searchResults.value = null
    searching.value = false
    return
  }
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { void runSearch(keyword) }, 200)
})

async function runSearch(keyword) {
  if (!props.rootPath) return
  const seq = ++searchRequestSeq
  searching.value = true
  try {
    const response = await explorerAPI()?.search(props.rootPath, keyword)
    if (seq !== searchRequestSeq) return
    searchResults.value = response?.success ? (response.data || []) : []
  } catch (e) {
    if (seq !== searchRequestSeq) return
    searchResults.value = []
  } finally {
    if (seq === searchRequestSeq) searching.value = false
  }
}

function selectResult(result) {
  selectedPath.value = result.path
}

// 搜索结果双击打开编辑器标签
function openSearchResult(result) {
  if (result.directory) return
  selectedPath.value = result.path
  emit('openFile', result.path)
}

function startFileDrag(event, node) {
  if (!node || node.directory) return
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-loopra-file-path', node.path)
  event.dataTransfer.setData('text/plain', node.path)
}

// ── 右键菜单 ──
function openContextMenu(event, node) {
  closeContextMenu()
  contextMenu.node = node
  contextMenu.x = Math.min(event.clientX, window.innerWidth - 180)
  contextMenu.y = Math.min(event.clientY, window.innerHeight - 190)
  contextMenu.visible = true
}

function closeContextMenu() {
  contextMenu.visible = false
  contextMenu.node = null
}

function contextAction(action) {
  const node = contextMenu.node
  closeContextMenu()
  if (!node) return
  if (action === 'open') {
    selectedPath.value = node.path
    if (!node.directory) emit('openFile', node.path)
  }
  else if (action === 'add-to-session' && !node.directory) emit('addToSession', { file: node.path })
  else if (action === 'reveal') revealInExplorer(node)
  else if (action === 'rename') startRename(node)
  else if (action === 'delete') removeNode(node)
  else if (action === 'refresh') void loadDirectory(node)
}

// 在系统文件管理器中显示（文件打开父目录）
function revealInExplorer(node) {
  if (!window.electronAPI?.openFolder) return
  void window.electronAPI.openFolder(node.path).then((result) => {
    if (!result?.success) message.error('打开文件管理器失败：' + (result?.error || '未知错误'))
  })
}

function onDocumentClick() {
  if (contextMenu.visible) closeContextMenu()
}

function onDocumentKeydown(event) {
  if (event.key === 'Escape' && contextMenu.visible) closeContextMenu()
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeydown)
  stopFileChangeListener = explorerAPI()?.onDidChange?.(handleFileSystemChange) || null
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
  clearTimeout(searchTimer)
  clearTimeout(fileChangeTimer)
  treeReloadSeq++
  watchRequestSeq++
  stopFileChangeListener?.()
  void explorerAPI()?.unwatch?.()
})

watch([() => props.rootPath, () => props.workspaceHash], () => {
  treeReloadSeq++
  searchResults.value = null
  query.value = ''
  void loadRoot()
  void loadGitDecorations()
  void startFileWatcher()
}, { immediate: true })

defineExpose({ refresh })
</script>

<style scoped>
.file-explorer {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  color: var(--fg);
}
/* 标题栏（VS Code Explorer 风格） */
.fe-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 35px;
  padding: 0 8px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--border);
}
.fe-title {
  padding-left: 12px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.8px;
  text-transform: uppercase;
  color: var(--fg-3, #9aa0a6);
}
.fe-head-actions {
  display: flex;
  align-items: center;
  gap: 0;
  opacity: 0;
  transition: opacity 0.1s;
}
/* VS Code pane-header 行为：hover 标题栏才显示操作按钮 */
.fe-head:hover .fe-head-actions,
.fe-head-actions:focus-within {
  opacity: 1;
}
.fe-head-action {
  width: 28px;
  height: 35px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--fg-3, #9aa0a6);
  cursor: pointer;
}
.fe-head-action .codicon {
  font-size: 16px;
}
.fe-head-action:hover,
.fe-head-action.active {
  background: var(--bg-3, #f3f4f6);
  color: var(--fg, #202124);
}
[data-theme="dark"] .fe-head-action:hover,
[data-theme="dark"] .fe-head-action.active {
  background: #33353a;
}
/* 搜索框 */
.fe-search {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 6px 8px;
  min-height: 28px;
  padding: 0 8px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--bg-2, #f7f7f8);
}
.fe-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--fg);
  font: inherit;
  font-size: 12px;
}
.fe-search-clear {
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 3px;
  background: transparent;
  color: var(--fg-4);
  cursor: pointer;
  font-size: 15px;
  line-height: 16px;
}
.fe-search-clear:hover {
  background: var(--bg-3);
  color: var(--fg);
}
/* 主体 */
.fe-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.fe-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 2px 0 8px;
}
.fe-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  color: var(--fg-4, #9ca3af);
  font-size: 12px;
  text-align: center;
}
.fe-error {
  color: var(--red, #dc2626);
}
/* 搜索结果行（与树节点行同风格，对齐 VS Code 选中/悬停色） */
.fen-row {
  display: flex;
  align-items: center;
  width: 100%;
  height: 22px;
  padding: 0 6px 0 4px;
  line-height: 22px;
  border: 0;
  background: transparent;
  color: var(--fg, #1f1f1f);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  white-space: nowrap;
  user-select: none;
}
.fen-row[draggable="true"] {
  cursor: grab;
}
.fen-row[draggable="true"]:active {
  cursor: grabbing;
}
.fen-row:hover {
  background: #e8e8e8;
}
[data-theme="dark"] .fen-row:hover {
  background: #2a2d2e;
}
.fen-row.active {
  background: #0060c0;
  color: #ffffff;
}
[data-theme="dark"] .fen-row.active {
  background: #094771;
  color: #ffffff;
}
.fen-twistie-placeholder {
  flex: 0 0 22px;
}
.fen-icon {
  flex: 0 0 16px;
  font-size: 16px;
  margin-right: 6px;
  color: inherit;
}
.fen-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.fe-result-path {
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--fg-4, #9ca3af);
  font-size: 11px;
  max-width: 50%;
}
/* 右键菜单 */
.fe-context-menu {
  box-sizing: border-box;
  position: fixed;
  z-index: 1000;
  width: 172px;
  padding: 4px;
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 6px;
  background: var(--bg, #fff);
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.16);
}
.fe-context-menu button {
  width: 100%;
  height: 32px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--fg-2, #525866);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.fe-context-menu button:hover,
.fe-context-menu button:focus-visible {
  color: var(--fg, #202124);
  background: var(--bg-3, #f2f3f5);
  outline: 0;
}
.fe-context-menu svg {
  width: 15px;
  height: 15px;
  flex: 0 0 auto;
}
.fe-context-menu .codicon {
  font-size: 15px;
  flex: 0 0 auto;
}
.fe-context-menu button.danger {
  color: var(--red, #dc2626);
}
.fe-context-menu button.danger:hover {
  background: rgba(220, 38, 38, 0.08);
  color: var(--red, #dc2626);
}
.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--border);
  border-top-color: var(--fg-3);
  border-radius: 50%;
  animation: fe-spin 0.7s linear infinite;
}
@keyframes fe-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
