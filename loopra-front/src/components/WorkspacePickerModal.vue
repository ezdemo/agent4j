<template>
  <Teleport to="body">
    <div v-if="show" class="modal-mask" @click.self="close">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="workspace-picker-title">
        <div class="modal-head">
          <div class="modal-title-group">
            <span id="workspace-picker-title" class="modal-title">项目管理</span>
            <span class="workspace-total">{{ workspaces.length }}</span>
          </div>
          <button class="btn-icon-sm modal-close" @click="close" title="关闭">×</button>
        </div>
        <div class="modal-body">
          <div class="workspace-list">
            <div v-if="workspaces.length === 0" class="modal-empty">暂无项目记录</div>
            <div
              v-for="w in workspaces"
              :key="w.hash"
              class="workspace-item"
              :class="{ active: w.hash === currentSessionWorkspace, 'drag-over': dragOverIndex === workspaces.indexOf(w) }"
              draggable="true"
              @dragstart="onDragStart($event, workspaces.indexOf(w))"
              @dragover.prevent="onDragOver($event, workspaces.indexOf(w))"
              @dragleave="onDragLeave"
              @drop.prevent="onDrop($event, workspaces.indexOf(w))"
              @dragend="onDragEnd"
              @click="handleSwitchWorkspace(w.hash)"
            >
              <svg class="drag-handle" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="9" cy="6" r="1.5"/><circle cx="15" cy="6" r="1.5"/>
                <circle cx="9" cy="12" r="1.5"/><circle cx="15" cy="12" r="1.5"/>
                <circle cx="9" cy="18" r="1.5"/><circle cx="15" cy="18" r="1.5"/>
              </svg>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
              <div class="workspace-info">
                <div class="workspace-item-title">
                  <span class="workspace-item-name">{{ w.name }}</span>
                </div>
                <div class="workspace-item-path">{{ formatWorkspacePath(w.path) }}</div>
              </div>
              <span class="workspace-item-count" :title="`${w.sessionCount} 个会话`">{{ w.sessionCount }}</span>
            </div>
          </div>
        </div>
        <div class="workspace-add">
          <div class="workspace-add-control">
            <input 
              ref="workspacePathInput"
              v-model="newWorkspacePath" 
              placeholder="输入新项目路径..."
              @keyup.enter="handleAddWorkspace"
            />
            <input
              ref="folderPicker"
              type="file"
              webkitdirectory
              style="display:none"
              @change="onFolderPicked"
            />
            <button v-if="isDesktopEnv" class="btn-icon-sm" title="选择文件夹（仅桌面端）" @click="openFolderPicker">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
            </button>
            <button class="btn-icon-sm workspace-add-submit" @click="handleAddWorkspace" :disabled="!newWorkspacePath.trim()" title="添加项目">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import {ref} from 'vue'

const dragIndex = ref(null)
const dragOverIndex = ref(null)

const props = defineProps({
  show: { type: Boolean, default: false },
  workspaces: { type: Array, default: () => [] },
  currentSessionWorkspace: { type: String, default: null },
  isDesktopEnv: { type: Boolean, default: false }
})

const emit = defineEmits(['update:show', 'switchWorkspace', 'addWorkspace', 'reorder'])

const newWorkspacePath = ref('')
const workspacePathInput = ref(null)
const folderPicker = ref(null)

function onDragStart(e, index) {
  dragIndex.value = index
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', String(index))
}

function onDragOver(e, index) {
  dragOverIndex.value = index
}

function onDragLeave() {
  dragOverIndex.value = null
}

function onDrop(e, index) {
  const from = dragIndex.value
  if (from === null || from === index) return
  const list = [...props.workspaces]
  const [item] = list.splice(from, 1)
  list.splice(index, 0, item)
  emit('reorder', list)
  dragIndex.value = null
  dragOverIndex.value = null
}

function onDragEnd() {
  dragIndex.value = null
  dragOverIndex.value = null
}

function close() {
  emit('update:show', false)
}

// 打开文件夹选择器（桌面端用原生对话框，浏览器用输入框）
async function openFolderPicker() {
  if (props.isDesktopEnv) {
    try {
      // Electron 环境：使用原生对话框（通过 IPC）
      const result = await window.electronAPI.loopraWebService.pickFolder()
      if (result) {
        newWorkspacePath.value = result
      }
    } catch (e) {
      console.error('选择文件夹失败:', e)
    }
  } else {
    // 浏览器环境：聚焦输入框让用户手动输入
    workspacePathInput.value?.focus()
  }
}

// 文件夹选中回调（浏览器回退方案）
function onFolderPicked(e) {
  const files = e.target.files
  if (files && files.length > 0) {
    newWorkspacePath.value = files[0].webkitRelativePath.split('/')[0] || ''
  }
  e.target.value = ''
}

// 切换项目
function handleSwitchWorkspace(hash) {
  emit('switchWorkspace', hash)
}

// 添加新项目
function handleAddWorkspace() {
  const path = newWorkspacePath.value.trim()
  if (!path) return
  emit('addWorkspace', path)
  newWorkspacePath.value = ''
}

function formatWorkspacePath(path) {
  if (!path) return ''
  const homePath = path.match(/^[a-z]:\\Users\\[^\\]+/i)
  return homePath ? '~' + path.slice(homePath[0].length) : path
}
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(19, 27, 35, 0.28);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal {
  width: min(560px, calc(100vw - 32px));
  max-height: min(520px, calc(100vh - 48px));
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border: 1px solid var(--glass-border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--glass-shadow);
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  box-sizing: border-box;
  padding: 9px 12px;
  border-bottom: 1px solid var(--border);
}

.modal-title-group {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.modal-title {
  color: var(--fg);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.25;
}

.workspace-total {
  min-width: 16px;
  padding: 1px 4px;
  border-radius: 3px;
  background: var(--bg-3);
  color: var(--fg-4);
  font-family: var(--mono);
  font-size: 10px;
  line-height: 1.4;
}

.modal-close {
  color: var(--fg-3);
}

.modal-body {
  flex: 0 1 auto;
  overflow-y: auto;
  max-height: 360px;
  padding: 6px 8px;
}

.modal-empty {
  padding: 16px 0;
  text-align: center;
  color: var(--fg-4);
  font-size: 13px;
}

/* 项目列表 */
.workspace-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.workspace-item .drag-handle {
  color: var(--fg-4);
  cursor: grab;
  opacity: 0;
  transition: opacity var(--t);
  flex-shrink: 0;
  margin-top: 2px;
}
.workspace-item:hover .drag-handle {
  opacity: 0.6;
}
.workspace-item:hover .drag-handle:hover {
  opacity: 1;
  color: var(--fg-2);
}
.workspace-item.drag-over {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 1px var(--accent-bg);
}
.workspace-item.dragging {
  opacity: 0.4;
}
.workspace-item {
  position: relative;
  display: grid;
  grid-template-columns: 14px 16px minmax(0, 1fr);
  align-items: start;
  gap: 9px;
  min-height: 72px;
  box-sizing: border-box;
  padding: 10px;
  cursor: pointer;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg);
  transition: background var(--t), border-color var(--t), box-shadow var(--t);
}
.workspace-item:hover {
  background: var(--bg-2);
  border-color: var(--glass-border);
}
.workspace-item.active {
  background: var(--bg);
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent-bg);
}
.workspace-item > svg {
  color: var(--fg-3);
  flex-shrink: 0;
  margin-top: 2px;
}
.workspace-item.active > svg {
  color: var(--fg-2);
}
.workspace-item .workspace-info {
  min-width: 0;
  padding-right: 24px;
}
.workspace-item-title {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 5px;
}
.workspace-item .workspace-item-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.workspace-item .workspace-item-path {
  margin-top: 3px;
  font-size: 10px;
  color: var(--fg-4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.workspace-item .workspace-item-count {
  position: absolute;
  right: 9px;
  bottom: 8px;
  color: var(--fg-4);
  font-family: var(--mono);
  font-size: 11px;
  text-align: right;
  white-space: nowrap;
}
.workspace-item.active .workspace-item-count {
  color: var(--fg-3);
}
/* 添加项目输入区 */
.workspace-add {
  padding: 8px;
  border-top: 1px solid var(--border);
  background: var(--glass-bg);
}

.workspace-add-control {
  display: flex;
  align-items: center;
  gap: 5px;
}
.workspace-add input {
  flex: 1;
  min-width: 0;
  height: 30px;
  box-sizing: border-box;
  padding: 6px 9px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  font-size: 12px;
  color: var(--fg);
}
.workspace-add input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 2px var(--accent-bg);
}
.workspace-add input::placeholder {
  color: var(--fg-4);
}

.workspace-add-control .btn-icon-sm {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  background: var(--bg);
  color: var(--fg-3);
}

.workspace-add-control .btn-icon-sm:hover {
  color: var(--accent);
  border-color: var(--accent);
}

.workspace-add-control .workspace-add-submit {
  border-color: var(--accent-btn);
  background: var(--accent-btn);
  color: #fff;
}

.workspace-add-control .workspace-add-submit:hover:not(:disabled) {
  filter: brightness(0.96);
}

.workspace-add-control .workspace-add-submit:disabled {
  cursor: not-allowed;
  opacity: 0.42;
}

@media (max-width: 520px) {
  .modal-head {
    padding: 11px 12px;
  }

  .workspace-list {
    grid-template-columns: 1fr;
  }
}
</style>
