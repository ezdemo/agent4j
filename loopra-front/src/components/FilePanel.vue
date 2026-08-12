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
    <div v-else-if="searchResults !== null" class="file-tree" role="listbox" aria-label="文件搜索结果">
      <div v-if="searching" class="file-state"><span class="loading-spinner"></span></div>
      <div v-else-if="searchResults.length === 0" class="file-state">未找到匹配的文件</div>
      <button
        v-for="result in searchResults"
        v-else
        :key="result.path"
        class="file-search-result"
        type="button"
        role="option"
        :title="result.path"
        @click="openFile(result.path)"
        @contextmenu.prevent.stop="openContextMenu($event, result)"
      >
        <svg class="file-icon" :class="fileClassOf(result.name)" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
        <span class="file-search-result-name">{{ result.name }}</span>
        <span class="file-search-result-path">{{ result.path }}</span>
      </button>
    </div>
    <div v-else class="file-tree" role="tree" aria-label="项目文件">
      <FileTreeNode
        v-for="node in rootNodes"
        :key="node.path"
        :node="node"
        :query="query"
        :selected-path="selectedPath"
        @toggle="toggleNode"
        @contextmenu="openContextMenu"
      />
      <div v-if="rootNodes.length === 0" class="file-state">项目文件夹为空</div>
    </div>

    <div
      v-if="contextMenu.visible"
      class="file-context-menu"
      role="menu"
      aria-label="文件操作菜单"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      @contextmenu.prevent
    >
      <template v-if="contextMenu.node && !contextMenu.node.directory">
        <button type="button" role="menuitem" @click="contextOpenFile">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          打开文件
        </button>
        <button type="button" role="menuitem" @click="contextAddToSession">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 5v14M5 12h14"/></svg>
          添加到上下文
        </button>
      </template>
      <template v-else>
        <button type="button" role="menuitem" @click="contextRefreshDir">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          刷新
        </button>
      </template>
      <button type="button" role="menuitem" class="danger" @click="contextDelete">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/></svg>
        删除
      </button>
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
import {filesAPI, gitAPI} from '../services/api'
import ActionConfirmDialog from './ActionConfirmDialog.vue'
import FileTreeNode from './FileTreeNode.vue'
import DiffViewer from './DiffViewer.vue'

const props = defineProps({ workspaceHash: { type: String, default: null } })
const emit = defineEmits(['addToSession'])

const rootNodes = ref([])
const query = ref('')
const loading = ref(false)
const error = ref('')
const selectedPath = ref('')
// 右键菜单：visible + 定位 + 目标节点
const contextMenu = reactive({ visible: false, x: 0, y: 0, node: null })
// 搜索模式：null = 未搜索（显示目录树）；非 null = 后端递归搜索结果列表
const searchResults = ref(null)
const searching = ref(false)
let searchTimer = null
let searchRequestSeq = 0
const diffViewer = ref({ open: false, file: '', diff: '', content: '', mode: 'content', loading: false, contentLoaded: false, diffLoaded: false })
// 删除确认对话框（系统统一 ActionConfirmDialog）
const deleteConfirm = reactive({ visible: false, node: null })
const deleteConfirmActions = [
  { key: 'cancel', label: '取消' },
  { key: 'confirm', label: '删除', variant: 'danger' }
]

// 点击其他区域 / Esc 关闭右键菜单
function onDocumentClick() {
  if (contextMenu.visible) closeContextMenu()
}
function onDocumentKeydown(event) {
  if (event.key === 'Escape' && contextMenu.visible) closeContextMenu()
}
onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
})

// 筛选文件：query 非空时走后端递归搜索（覆盖子目录），空时恢复目录树
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
  if (!props.workspaceHash) return
  const seq = ++searchRequestSeq
  searching.value = true
  try {
    const response = await filesAPI.search(props.workspaceHash, keyword)
    if (seq !== searchRequestSeq) return
    searchResults.value = response.success ? (response.data || []) : []
  } catch (e) {
    if (seq !== searchRequestSeq) return
    searchResults.value = []
  } finally {
    if (seq === searchRequestSeq) searching.value = false
  }
}

function fileClassOf(name) {
  const extension = String(name).split('.').pop()?.toLowerCase()
  if (['js', 'jsx', 'ts', 'tsx', 'vue'].includes(extension)) return 'code-file'
  if (['java', 'kt', 'go', 'py'].includes(extension)) return 'source-file'
  if (['md', 'txt'].includes(extension)) return 'text-file'
  if (['png', 'jpg', 'jpeg', 'gif', 'svg'].includes(extension)) return 'image-file'
  return ''
}

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
  clearTimeout(searchTimer)
  searchRequestSeq++
  searchResults.value = null
  searching.value = false
  loadDirectory()
}

function openContextMenu(event, node) {
  closeContextMenu()
  contextMenu.node = node
  contextMenu.x = Math.min(event.clientX, window.innerWidth - 180)
  contextMenu.y = Math.min(event.clientY, window.innerHeight - 150)
  contextMenu.visible = true
}

function closeContextMenu() {
  contextMenu.visible = false
  contextMenu.node = null
}

function contextOpenFile() {
  const node = contextMenu.node
  closeContextMenu()
  if (node) void openFile(node.path)
}

function contextAddToSession() {
  const node = contextMenu.node
  closeContextMenu()
  if (node) emit('addToSession', { file: node.path })
}

function contextRefreshDir() {
  const node = contextMenu.node
  closeContextMenu()
  if (node) void loadDirectory(node.path, node)
}

function contextDelete() {
  const node = contextMenu.node
  closeContextMenu()
  if (!node) return
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
    const response = await filesAPI.remove(props.workspaceHash, node.path)
    if (!response?.success) throw new Error(response?.error || '删除失败')
    removeNodeFromTree(rootNodes.value, node.path)
    if (searchResults.value !== null) {
      searchResults.value = searchResults.value.filter((result) => result.path !== node.path)
    }
    if (selectedPath.value === node.path) selectedPath.value = ''
    message.success('已删除')
  } catch (e) {
    message.error('删除失败：' + (e.message || '未知错误'))
  } finally {
    dismissDeleteConfirm()
  }
}

function removeNodeFromTree(nodes, path) {
  for (let i = 0; i < nodes.length; i++) {
    const node = nodes[i]
    if (node.path === path) {
      nodes.splice(i, 1)
      return true
    }
    if (node.children && removeNodeFromTree(node.children, path)) return true
  }
  return false
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
.file-search-result { display: flex; align-items: center; gap: 7px; width: 100%; min-height: 30px; padding: 0 8px; border: 0; border-radius: 4px; background: transparent; color: var(--fg-2); cursor: pointer; font: inherit; font-size: 13px; text-align: left; }
.file-search-result:hover { background: var(--bg-3); color: var(--fg); }
.file-search-result-name { flex: 0 0 auto; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 45%; }
.file-search-result-path { margin-left: auto; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--fg-4); font-size: 12px; max-width: 55%; }
.file-search-result:hover .file-search-result-path { color: var(--fg-3); }
.file-context-menu { box-sizing: border-box; position: fixed; z-index: 1000; width: 168px; padding: 4px; border: 1px solid var(--border, #e5e7eb); border-radius: 6px; background: var(--bg, #fff); box-shadow: var(--shadow-lg, 0 10px 28px rgba(0, 0, 0, 0.16)); }
.file-context-menu button { width: 100%; height: 34px; display: flex; align-items: center; gap: 8px; padding: 0 8px; border: 0; border-radius: 4px; background: transparent; color: var(--fg-2, #525866); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.file-context-menu button:hover, .file-context-menu button:focus-visible { color: var(--fg, #202124); background: var(--bg-3, #f2f3f5); outline: 0; }
.file-context-menu svg { width: 15px; height: 15px; flex: 0 0 auto; }
.file-context-menu button.danger { color: var(--red, #dc2626); }
.file-context-menu button.danger:hover { background: rgba(220, 38, 38, 0.08); color: var(--red, #dc2626); }
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
