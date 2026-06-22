<template>
  <Teleport to="body">
    <div v-if="show" class="modal-mask" @click.self="close">
      <div class="modal">
        <div class="modal-head">
          <span>项目管理</span>
          <button class="btn-icon-sm" @click="close">×</button>
        </div>
        <div class="modal-body">
          <div class="workspace-list">
            <div v-if="workspaces.length === 0" class="modal-empty">暂无项目记录</div>
            <div
              v-for="w in workspaces"
              :key="w.hash"
              class="workspace-item"
              :class="{ active: w.hash === currentSessionWorkspace }"
              @click="handleSwitchWorkspace(w.hash)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
              </svg>
              <div class="workspace-info">
                <div class="workspace-item-name">{{ w.name }}</div>
                <div class="workspace-item-path">{{ w.path }}</div>
              </div>
              <span class="workspace-item-count">{{ w.sessionCount }}</span>
              <button class="btn-icon-sm workspace-del" @click.stop="handleDeleteWorkspace(w.hash)" title="删除">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
          </div>
          <div class="workspace-add">
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
            <button class="btn-icon-sm" @click="handleAddWorkspace" :disabled="!newWorkspacePath.trim()">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import {ref} from 'vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  workspaces: { type: Array, default: () => [] },
  currentSessionWorkspace: { type: String, default: null },
  isDesktopEnv: { type: Boolean, default: false }
})

const emit = defineEmits(['update:show', 'switchWorkspace', 'addWorkspace', 'deleteWorkspace'])

const newWorkspacePath = ref('')
const workspacePathInput = ref(null)
const folderPicker = ref(null)

function close() {
  emit('update:show', false)
}

// 打开文件夹选择器（桌面端用原生对话框，浏览器用输入框）
async function openFolderPicker() {
  if (props.isDesktopEnv) {
    try {
      // Electron 环境：使用原生对话框（通过 IPC）
      const result = await window.electronAPI.agent4jWebService.pickFolder()
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

// 切换工作区
function handleSwitchWorkspace(hash) {
  emit('switchWorkspace', hash)
}

// 添加新工作区
function handleAddWorkspace() {
  const path = newWorkspacePath.value.trim()
  if (!path) return
  emit('addWorkspace', path)
  newWorkspacePath.value = ''
}

// 删除工作区
function handleDeleteWorkspace(hash) {
  emit('deleteWorkspace', hash)
}
</script>
