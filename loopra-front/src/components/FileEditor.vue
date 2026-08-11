<template>
  <div class="file-editor">
    <div class="fe-body">
      <div ref="editorRef" class="fe-monaco"></div>
      <div v-if="loading" class="fe-state fe-overlay">
        <span class="loading-spinner"></span>
      </div>
      <div v-else-if="error" class="fe-state fe-error fe-overlay">{{ error }}</div>
    </div>
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch} from 'vue'
import {message} from 'ant-design-vue'

const props = defineProps({
  activeFile: {type: Object, default: null},
  theme: {type: String, default: 'gray'}
})

const emit = defineEmits(['saved', 'dirtyChange'])

const editorRef = ref(null)
const editor = shallowRef(null)
const monaco = shallowRef(null)
const loadingPath = ref('')
const error = ref('')
const saving = ref(false)
const models = new Map()
const loadRequests = new Map()
let disposed = false

const activePath = computed(() => props.activeFile?.path || '')
const loading = computed(() => loadingPath.value === activePath.value)
const explorerAPI = () => window.electronAPI?.fileExplorer
const editorTheme = () => props.theme === 'dark' ? 'vs-dark' : 'vs'

function createEntry(path, content, dirty = false) {
  const uri = monaco.value.Uri.file(path)
  const language = /\.vue$/i.test(path) ? 'html' : undefined
  const model = monaco.value.editor.createModel(content, language, uri)
  const entry = {
    model,
    dirty,
    savedVersion: dirty ? -1 : model.getAlternativeVersionId(),
    viewState: null,
    changeDisposable: null
  }
  entry.changeDisposable = model.onDidChangeContent(() => {
    const nextDirty = model.getAlternativeVersionId() !== entry.savedVersion
    if (nextDirty === entry.dirty) return
    entry.dirty = nextDirty
    emit('dirtyChange', path, nextDirty)
  })
  models.set(path, entry)
  return entry
}

function showEntry(path, entry) {
  const currentPath = activePath.value
  const currentEntry = models.get(currentPath)
  if (currentEntry && editor.value?.getModel() === currentEntry.model) {
    currentEntry.viewState = editor.value.saveViewState()
  }
  editor.value?.setModel(entry?.model || null)
  if (entry?.viewState) editor.value?.restoreViewState(entry.viewState)
  if (path) editor.value?.focus()
}

async function activateFile(path) {
  if (!path || !monaco.value || !editor.value) {
    editor.value?.setModel(null)
    return
  }
  error.value = ''
  const existing = models.get(path)
  if (existing) {
    showEntry(path, existing)
    await nextTick()
    editor.value.layout()
    return
  }

  editor.value.setModel(null)
  const request = Symbol(path)
  loadRequests.set(path, request)
  loadingPath.value = path
  try {
    const response = await explorerAPI()?.read(path)
    if (!response?.success) throw new Error(response?.error || '读取失败')
    if (disposed || loadRequests.get(path) !== request) return
    const entry = createEntry(path, response.data)
    if (activePath.value === path) showEntry(path, entry)
  } catch (e) {
    if (loadRequests.get(path) === request && activePath.value === path) {
      error.value = e.message || '读取文件失败'
    }
  } finally {
    if (loadRequests.get(path) === request) {
      loadRequests.delete(path)
      if (loadingPath.value === path) loadingPath.value = ''
    }
  }
}

async function save() {
  const path = activePath.value
  const entry = models.get(path)
  if (!entry?.dirty || saving.value) return
  saving.value = true
  const savingVersion = entry.model.getAlternativeVersionId()
  const savingContent = entry.model.getValue()
  try {
    const response = await explorerAPI()?.write(path, savingContent)
    if (!response?.success) throw new Error(response?.error || '保存失败')
    if (models.get(path) !== entry) return
    entry.savedVersion = savingVersion
    const nextDirty = entry.model.getAlternativeVersionId() !== savingVersion
    entry.dirty = nextDirty
    emit('dirtyChange', path, nextDirty)
    message.success('已保存')
    emit('saved', path)
  } catch (e) {
    message.error('保存失败：' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

function disposeEntry(path) {
  const entry = models.get(path)
  if (!entry) return
  if (editor.value?.getModel() === entry.model) editor.value.setModel(null)
  entry.changeDisposable?.dispose()
  entry.model.dispose()
  models.delete(path)
}

function closeFile(path) {
  loadRequests.delete(path)
  disposeEntry(path)
}

function renameFile(oldPath, newPath) {
  loadRequests.delete(oldPath)
  const entry = models.get(oldPath)
  if (!entry || oldPath === newPath) return
  const existingTarget = models.get(newPath)
  if (existingTarget) disposeEntry(newPath)
  const wasActive = editor.value?.getModel() === entry.model
  if (wasActive) entry.viewState = editor.value.saveViewState()
  const content = entry.model.getValue()
  const dirty = entry.dirty
  const viewState = entry.viewState
  const nextEntry = createEntry(newPath, content, dirty)
  nextEntry.viewState = viewState
  disposeEntry(oldPath)
  if (wasActive) showEntry(newPath, nextEntry)
}

function closeAll() {
  loadRequests.clear()
  for (const path of [...models.keys()]) disposeEntry(path)
}

async function initializeEditor() {
  loadingPath.value = activePath.value
  try {
    await import('monaco-editor/nls/lang/zh-cn')
    const module = await import('../utils/monaco')
    if (disposed || !editorRef.value) return
    monaco.value = module.default
    editor.value = monaco.value.editor.create(editorRef.value, {
      automaticLayout: true,
      fontFamily: 'var(--mono)',
      fontSize: 13,
      lineHeight: 21,
      minimap: {enabled: true},
      padding: {top: 8, bottom: 8},
      scrollBeyondLastLine: false,
      smoothScrolling: true,
      tabSize: 2,
      theme: editorTheme()
    })
    editor.value.addCommand(monaco.value.KeyMod.CtrlCmd | monaco.value.KeyCode.KeyS, () => { void save() })
    await activateFile(activePath.value)
  } catch (e) {
    error.value = e.message || '编辑器加载失败'
    loadingPath.value = ''
  }
}

watch(activePath, (path, previousPath) => {
  const previousEntry = models.get(previousPath)
  if (previousEntry && editor.value?.getModel() === previousEntry.model) {
    previousEntry.viewState = editor.value.saveViewState()
  }
  void activateFile(path)
})

watch(() => props.theme, () => {
  if (monaco.value) monaco.value.editor.setTheme(editorTheme())
})

onMounted(() => { void initializeEditor() })

onBeforeUnmount(() => {
  disposed = true
  editor.value?.setModel(null)
  closeAll()
  editor.value?.dispose()
})

defineExpose({closeAll, closeFile, renameFile, save})
</script>

<style scoped>
.file-editor {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 0;
  background: var(--bg);
}

.fe-body {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.fe-monaco {
  width: 100%;
  height: 100%;
  min-width: 0;
}

.fe-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--fg-muted);
  font-size: var(--text-sm);
}

.fe-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: var(--bg);
}

.fe-error {
  color: var(--danger);
  padding: 24px;
  text-align: center;
}
</style>
