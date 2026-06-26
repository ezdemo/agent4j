<template>
  <div ref="msgRef" class="msg" :class="msg.role" :data-msg-idx="idx">
    <!-- 用户消息 -->
    <template v-if="msg.role === 'user'">
      <div class="msg-body user-body">
        <div class="msg-text">{{ userDisplayText }}</div>
        <button v-if="isUserLong" class="user-expand-btn" @click="userExpanded = !userExpanded">
          {{ userExpanded ? '收起' : '展开全部' }}
        </button>
        <div v-if="msg.images && msg.images.length > 0" class="user-images">
          <img v-for="(img, i) in msg.images" :key="i" :src="img" class="user-image" @click="$emit('previewImage', img)"/>
        </div>
        <div class="msg-footer">
          <span class="msg-time">{{ msg.time }}</span>
          <button v-if="msg.snapshotId" class="rollback-btn"
                  :class="{ loading: snapshotRollbackLoading.get(msg.snapshotId) }"
                  @click="$emit('rollbackSnapshot', msg.snapshotId)"
                  title="撤回 AI 修改，恢复到发送前状态"
                  v-html="ROLLBACK_ICON"></button>
          <button class="copy-msg-btn" @click="$emit('copyMessage', msg)" title="复制消息" v-html="COPY_ICON"></button>
        </div>
      </div>
    </template>

    <!-- 助手消息 -->
    <template v-else-if="msg.role === 'assistant' && msg.blocks && msg.blocks.length > 0">
      <div class="msg-body assistant-body">
        <div class="msg-blocks">
          <BlockRenderer :blocks="msg.blocks || []" @send-choice="(val, block) => $emit('sendChoice', val, block)" @open-file="(filePath) => $emit('openFile', filePath)" />
        </div>
        <div class="msg-footer">
          <span class="msg-time-group">
            <span class="msg-time">{{ msg.time }}</span>
            <template v-if="fileStats">
              <span v-if="fileStats.edited > 0" class="msg-file-stat clickable" @mouseenter="showFileListDelayed('edited', $event)" @mouseleave="hideFileListDelayed">修改 {{ fileStats.edited }} 文件</span>
              <span v-if="fileStats.created > 0" class="msg-file-stat clickable" @mouseenter="showFileListDelayed('created', $event)" @mouseleave="hideFileListDelayed">新增 {{ fileStats.created }} 文件</span>
            </template>
          </span>
          <button class="copy-msg-btn" @click="$emit('copyMessage', msg)" title="复制消息" v-html="COPY_ICON"></button>
        </div>
      </div>
    </template>
  </div>

  <!-- 文件列表弹出 -->
  <Teleport to="body">
    <div v-if="showFileList" class="file-list-popover" :class="{ above: fileListPos.above }" :style="{ position: 'fixed', left: fileListPos.x + 'px', top: fileListPos.y + 'px' }" @mouseenter="cancelHideFileList" @mouseleave="hideFileListDelayed">
      <div class="file-list-header">
        <span class="file-list-title">{{ fileListType === 'edited' ? '修改的文件' : '新增的文件' }}</span>
        <span class="file-list-count">{{ fileList.length }} 个</span>
      </div>
      <div class="file-list-body">
        <div v-for="fp in fileList" :key="fp" class="file-list-item" @click="showFileList = false; $emit('openFile', fp)">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          <span class="file-list-path" :title="fp">{{ fp }}</span>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- 链接悬停浮层 -->
  <Teleport to="body">
    <div v-if="linkPopover.visible"
         class="link-popover"
         :style="{ left: linkPopover.x + 'px', top: linkPopover.y + 'px' }"
         @mouseenter="onPopoverMouseEnter"
         @mouseleave="onPopoverMouseLeave">
      <button v-if="isElectron" class="link-popover-btn" @click="openInElement">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><line x1="3" y1="9" x2="21" y2="9"/><line x1="9" y1="3" x2="9" y2="21"/></svg>
        元素界面打开
      </button>
      <button class="link-popover-btn" @click="openInBrowser">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
        浏览器打开
      </button>
    </div>
  </Teleport>
</template>

<script setup>
import {COPY_ICON, ROLLBACK_ICON} from '../utils/icons'
import BlockRenderer from './BlockRenderer.vue'
import {onMounted, onBeforeUnmount, reactive, ref, computed} from 'vue'
import platform from '../services/platform'

const props = defineProps({
  msg: {type: Object, required: true},
  idx: {type: Number, default: 0},
  snapshotRollbackLoading: {type: Object, required: true}
})

const emit = defineEmits(['previewImage', 'rollbackSnapshot', 'copyMessage', 'sendChoice', 'openFile'])

const isElectron = platform.isElectron

// 用户消息截断
const USER_MAX_LEN = 300
const userExpanded = ref(false)
const isUserLong = computed(() => (props.msg.content || '').length > USER_MAX_LEN)
const userDisplayText = computed(() => {
  const c = props.msg.content || ''
  if (userExpanded.value || c.length <= USER_MAX_LEN) return c
  return c.slice(0, USER_MAX_LEN) + '...'
})

// 助手消息文件统计（edit=修改, write=新增，按 file_path 去重）
const fileStats = computed(() => {
  const blocks = props.msg.blocks
  if (!blocks) return null
  const edited = new Set(), created = new Set()
  for (const b of blocks) {
    if (b.type !== 'tool_call' || b.name === 'finish') continue
    const fp = b.args?.file_path
    if (!fp) continue
    if (b.name === 'edit') edited.add(fp)
    else if (b.name === 'write') created.add(fp)
  }
  if (edited.size === 0 && created.size === 0) return null
  return { edited: edited.size, created: created.size, editedFiles: [...edited], createdFiles: [...created] }
})

// 文件列表弹出
const showFileList = ref(false)
const fileListType = ref('') // 'edited' | 'created'
const fileList = computed(() => {
  if (!fileStats.value) return []
  return fileListType.value === 'edited' ? fileStats.value.editedFiles : fileStats.value.createdFiles
})
const fileListPos = ref({ x: 0, y: 0, above: false })
let fileHideTimer = null
let fileShowTimer = null

const showFileListDelayed = (type, event) => {
  clearTimeout(fileHideTimer)
  clearTimeout(fileShowTimer)
  // 必须在 setTimeout 外捕获，Vue 事件回调结束后 event 会被回收
  const rect = event.currentTarget.getBoundingClientRect()
  fileShowTimer = setTimeout(() => {
    fileListType.value = type
    const popH = Math.min(fileList.value.length * 32 + 40, 320)
    const spaceBelow = window.innerHeight - rect.bottom
    const above = spaceBelow < popH + 8 && rect.top > popH
    fileListPos.value = {
      x: Math.max(8, Math.min(rect.left, window.innerWidth - 300)),
      y: above ? rect.top - 4 : rect.bottom + 4,
      above
    }
    showFileList.value = true
  }, 150)
}

const hideFileListDelayed = () => {
  clearTimeout(fileShowTimer)
  fileHideTimer = setTimeout(() => { showFileList.value = false }, 200)
}

const cancelHideFileList = () => {
  clearTimeout(fileHideTimer)
}

// SVG 图标已迁移至 ../utils/icons.js

// ═══════════════════════════════════════════
// 链接悬停浮层
// ═══════════════════════════════════════════
const msgRef = ref(null)
const linkPopover = reactive({ visible: false, x: 0, y: 0, url: '' })
let hideTimer = null

function showLinkPopover(el) {
  clearTimeout(hideTimer)
  const href = el.getAttribute('href')
  if (!href) return
  const rect = el.getBoundingClientRect()
  linkPopover.url = href
  // 居中对齐，超出视口时修正
  const centerX = rect.left + rect.width / 2
  linkPopover.x = Math.max(80, Math.min(centerX, window.innerWidth - 80))
  linkPopover.y = rect.bottom + 4
  linkPopover.visible = true
}

function scheduleHide() {
  hideTimer = setTimeout(() => {
    linkPopover.visible = false
  }, 150)
}

function onPopoverMouseEnter() {
  clearTimeout(hideTimer)
}

function onPopoverMouseLeave() {
  scheduleHide()
}

function onMsgMouseOver(e) {
  const link = e.target.closest('.ai-link')
  if (!link) return
  showLinkPopover(link)
}

function onMsgMouseOut(e) {
  const link = e.target.closest('.ai-link')
  if (!link) return
  const related = e.relatedTarget
  if (related && (link.contains(related) || related.closest?.('.link-popover'))) return
  scheduleHide()
}

function onMsgClick(e) {
  const link = e.target.closest('.ai-link')
  if (!link) return
  e.preventDefault()
  e.stopPropagation()
  // 浏览器环境直接打开
  if (!isElectron) {
    window.open(link.getAttribute('href'), '_blank')
  }
  // 桌面环境由浮层按钮处理
}

async function openInBrowser() {
  const url = linkPopover.url
  linkPopover.visible = false
  if (!url) return
  if (window.electronAPI?.openExternal) {
    await window.electronAPI.openExternal(url)
  } else {
    window.open(url, '_blank')
  }
}

function openInElement() {
  const url = linkPopover.url
  linkPopover.visible = false
  if (!url) return
  window.dispatchEvent(new CustomEvent('agent4j:open-in-element', { detail: { url } }))
}

onMounted(() => {
  const el = msgRef.value
  if (!el) return
  el.addEventListener('mouseover', onMsgMouseOver)
  el.addEventListener('mouseout', onMsgMouseOut)
  el.addEventListener('click', onMsgClick)
})
onBeforeUnmount(() => {
  const el = msgRef.value
  if (el) {
    el.removeEventListener('mouseover', onMsgMouseOver)
    el.removeEventListener('mouseout', onMsgMouseOut)
    el.removeEventListener('click', onMsgClick)
  }
  clearTimeout(hideTimer)
})


</script>

<style scoped>
/* 消息 */
.msg {
  margin-bottom: 8px;
}

/* 角色切换时增大间距 */
.msg.user + .msg.assistant,
.msg.assistant + .msg.user {
  margin-top: 20px;
  margin-bottom: 20px;
}

.msg.user {
  display: flex;
  justify-content: flex-end;
}

.msg-body {
  max-width: 85%;
}

.user-body {
  background: var(--accent-bg);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  color: var(--fg);
  padding: 8px 12px;
  border-radius: var(--r-lg);
  box-shadow: var(--glass-shadow);
}

.user-body ::selection {
  background: var(--accent);
  color: #fff;
}

.user-body .msg-time {
  font-size: 10px;
  opacity: 0.7;
  margin-top: 4px;
  text-align: left;
}

.assistant-body {
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  padding: 8px 12px;
  box-shadow: var(--glass-shadow);
}

.msg-blocks {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.assistant-body ::selection {
  background: var(--accent);
  color: #fff;
}

.assistant-body .msg-time {
  font-size: 12px;
  color: var(--fg-3);
}

.msg-text {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 用户消息中的图片 */
.user-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.user-image {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--border);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.user-image:hover {
  transform: scale(1.05);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

/* 消息底部栏 */
.msg-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.user-body .msg-footer {
  justify-content: space-between;
  align-items: center;
}

.assistant-body .msg-footer {
  justify-content: space-between;
}

.msg-time-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.msg-file-stat {
  font-size: 12px;
  color: var(--fg-3);
}

.msg-file-stat.clickable {
  cursor: pointer;
  transition: color var(--t);
}

.msg-file-stat.clickable:hover {
  color: var(--accent);
}

.copy-msg-btn {
  opacity: 0;
  background: none;
  border: none;
  font-size: 12px;
  cursor: pointer;
  padding: 3px 5px;
  border-radius: var(--r-sm);
  transition: opacity 0.2s, background 0.2s;
  line-height: 1;
  color: var(--fg-3);
}

.user-body .copy-msg-btn {
  color: var(--fg-3);
  opacity: 0.5;
}

.msg-body:hover .copy-msg-btn {
  opacity: 0.8;
}

.assistant-body .copy-msg-btn {
  opacity: 0.5;
}

.assistant-body:hover .copy-msg-btn {
  opacity: 0.8;
}

.user-body:hover .copy-msg-btn {
  opacity: 0.8;
}

.copy-msg-btn:hover {
  opacity: 1 !important;
  background: var(--glass-bg-2);
}

.user-body .copy-msg-btn:hover {
  background: var(--bg-3);
  color: var(--accent);
}

/* 用户消息展开/收起按钮 */
.user-expand-btn {
  background: none;
  border: none;
  color: var(--fg-3);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 0;
  margin-top: 2px;
  text-decoration: underline;
  text-underline-offset: 2px;
  transition: color var(--t);
}

.user-expand-btn:hover {
  color: var(--accent);
}

/* 撤回按钮 */
.rollback-btn {
  opacity: 0;
  background: none;
  border: none;
  font-size: 12px;
  cursor: pointer;
  padding: 3px 5px;
  border-radius: var(--r-sm);
  transition: opacity 0.2s, background 0.2s;
  line-height: 1;
  color: var(--fg-3);
  margin-left: auto;
}

.rollback-btn.loading {
  opacity: 0.5;
  pointer-events: none;
}

.user-body .rollback-btn {
  color: var(--fg-3);
  opacity: 0.5;
}

.user-body:hover .rollback-btn {
  opacity: 0.8;
}

.msg-body:hover .rollback-btn {
  opacity: 0.6;
}

.rollback-btn:hover {
  opacity: 1;
  background: rgba(231, 76, 60, 0.12);
  color: var(--accent-5, #e74c3c);
}

.user-body .rollback-btn:hover {
  background: var(--accent-bg);
  color: var(--accent);
}

/* 链接悬停浮层 */
.link-popover {
  position: fixed;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  gap: 2px;
  background: var(--bg, #fff);
  border: 1px solid var(--border, #e0e0e0);
  border-radius: 8px;
  padding: 4px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12), 0 1px 4px rgba(0, 0, 0, 0.08);
  animation: link-popover-in 0.15s ease-out;
}

@keyframes link-popover-in {
  from { opacity: 0; transform: translateX(-50%) translateY(-4px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}

.link-popover-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--fg, #333);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s, color 0.15s;
}

.link-popover-btn:hover {
  background: var(--accent, #3b82f6);
  color: #fff;
}

.link-popover-btn svg {
  flex-shrink: 0;
}
</style>

<style>
/* 文件列表弹出（非 scoped，因为 Teleport 到 body） */
.file-list-popover {
  z-index: 9999;
  min-width: 280px;
  max-width: 480px;
  max-height: 320px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: 0 8px 32px rgba(0,0,0,0.12);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.file-list-popover.above {
  transform: translateY(-100%);
}

.file-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.file-list-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
}

.file-list-count {
  font-size: 11px;
  color: var(--fg-4);
  background: var(--bg-3);
  padding: 1px 6px;
  border-radius: var(--r-sm);
}

.file-list-body {
  overflow-y: auto;
  padding: 4px;
}

.file-list-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: background var(--t);
}

.file-list-item:hover {
  background: var(--bg-2);
}

.file-list-item svg {
  color: var(--fg-4);
  flex-shrink: 0;
}

.file-list-path {
  font-size: 12px;
  font-family: var(--mono);
  color: var(--fg-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
