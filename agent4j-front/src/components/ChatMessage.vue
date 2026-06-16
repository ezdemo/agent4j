<template>
  <div ref="msgRef" class="msg" :class="msg.role" :data-msg-idx="idx">
    <!-- 用户消息 -->
    <template v-if="msg.role === 'user'">
      <div class="msg-body user-body">
        <div class="msg-text">{{ msg.content }}</div>
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
    <template v-else-if="msg.role === 'assistant'">
      <div class="msg-body assistant-body">
        <div class="msg-blocks">
          <template v-for="(block, bi) in (msg.blocks || [])" :key="bi">
            <!-- 思考 -->
            <div v-if="block.type === 'reasoning'" class="block-reasoning">
              <div class="reasoning-head" @click="block.showContent = !block.showContent">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
                <span>思考</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                     :style="{ transform: block.showContent ? 'rotate(180deg)' : '' }">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </div>
              <div v-if="block.showContent" class="reasoning-text" v-html="getReasoningHtml(block)"></div>
            </div>

            <!-- 内容 -->
            <div v-else-if="block.type === 'content'" class="block-content" v-html="fmt(block.content)"></div>

            <!-- 工具调用 -->
            <template v-else-if="block.type === 'tool_call'">
              <!-- finish 工具：完成时将 content 渲染为模型输出样式 -->
              <div v-if="block.name === 'finish' && block.result" class="block-finish">
                <div class="finish-head">
                  <span class="finish-icon">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                  </span>
                  <span class="finish-label">最终回答</span>
                </div>
                <div class="finish-content" v-html="fmt(block.result)"></div>
              </div>
              <!-- finish 执行中 -->
              <div v-else-if="block.name === 'finish'" class="block-tool">
                <div class="tool-head">
                  <span class="tool-icon" :class="block.status">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="animate-spin"><path d="M21 12a9 9 0 11-6.219-8.56"/></svg>
                  </span>
                  <code class="tool-name">finish</code>
                  <span class="tool-status" :class="block.status">{{ block.status }}</span>
                </div>
              </div>
              <!-- 其他工具 -->
              <div v-else class="block-tool">
                <div class="tool-head" @click="block.expanded = !block.expanded">
                  <span class="tool-icon" :class="block.status">
                    <svg v-if="block.status === '执行中'" width="12" height="12" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" class="animate-spin"><path
                        d="M21 12a9 9 0 11-6.219-8.56"/></svg>
                    <svg v-else-if="block.status === '成功'" width="12" height="12" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
                    <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                         stroke-width="2"><circle cx="12" cy="12" r="10"/></svg>
                  </span>
                  <code class="tool-name">{{ block.name }}</code>
                  <span class="tool-status" :class="block.status">{{ block.status }}</span>
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                       :style="{ transform: block.expanded ? 'rotate(180deg)' : '' }">
                    <polyline points="6 9 12 15 18 9"/>
                  </svg>
                </div>
                <div v-if="block.expanded" class="tool-detail">
                  <pre v-if="block.args"><code>{{ fmtArgs(block.args) }}</code></pre>
                  <pre v-if="block.result"><code>{{ block.result }}</code></pre>
                </div>
              </div>
            </template>

            <!-- 选项按钮（choice） -->
            <div v-else-if="block.type === 'choice'" class="block-choice">
              <div v-if="!block.resolved" class="choice-buttons">
                <button v-for="opt in (block.options || [])" :key="opt.value"
                        class="choice-btn"
                        @click="$emit('sendChoice', opt.value, block)">
                  {{ opt.title }}
                </button>
              </div>
              <div v-else class="choice-resolved">
                <span class="choice-label">已选择：</span>
                <span class="choice-value">{{ block.selectedTitle || block.options?.[0]?.title || '—' }}</span>
              </div>
            </div>

          </template>
        </div>
        <div class="msg-footer">
          <span class="msg-time">{{ msg.time }}</span>
          <button class="copy-msg-btn" @click="$emit('copyMessage', msg)" title="复制消息" v-html="COPY_ICON"></button>
        </div>
      </div>
    </template>
  </div>

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
import {md} from '../utils/highlight'
import {onMounted, onBeforeUnmount, reactive, ref} from 'vue'
import platform from '../services/platform'

const props = defineProps({
  msg: {type: Object, required: true},
  idx: {type: Number, default: 0},
  snapshotRollbackLoading: {type: Object, required: true}
})

const emit = defineEmits(['previewImage', 'rollbackSnapshot', 'copyMessage', 'sendChoice'])

const isElectron = platform.isElectron

// SVG 图标
const COPY_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>'
const ROLLBACK_ICON = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>'

// 使用共享 marked 实例（语法高亮 + 复制按钮已内置）
const fmt = c => {
  if (!c) return ''
  return md.parse(c)
}

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

// 带缓存的 reasoning Markdown 渲染
const getReasoningHtml = (block) => {
  if (!block.showContent) return ''
  if (!block.content) return ''
  if (block._cachedContent === block.content && block._cachedHtml) {
    return block._cachedHtml
  }
  block._cachedContent = block.content
  block._cachedHtml = fmt(block.content)
  return block._cachedHtml
}

const fmtArgs = a => {
  if (typeof a === 'string') {
    try {
      return JSON.stringify(JSON.parse(a), null, 2)
    } catch {
      return a
    }
  }
  return JSON.stringify(a, null, 2)
}
</script>

<style scoped>
/* 消息 */
.msg {
  margin-bottom: 12px;
}

.msg.user {
  display: flex;
  justify-content: flex-end;
}

.msg-body {
  max-width: 80%;
}

.user-body {
  background: var(--accent);
  color: #fff;
  padding: 8px 12px;
  border-radius: var(--r);
}

.user-body ::selection {
  background: rgba(255, 255, 255, 0.35);
  color: #000;
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

.assistant-body ::selection {
  background: var(--accent);
  color: #fff;
}

.assistant-body .msg-time {
  font-size: 10px;
  color: var(--fg-4);
  margin-top: 4px;
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
  color: rgba(255, 255, 255, 0.8);
}

.msg-body:hover .copy-msg-btn {
  opacity: 0.6;
}

.copy-msg-btn:hover {
  opacity: 1;
  background: var(--glass-bg-2);
}

.user-body .copy-msg-btn:hover {
  background: rgba(255, 255, 255, 0.15);
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
  color: rgba(255, 255, 255, 0.8);
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
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

/* 消息块 */
.msg-blocks {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 8px 0 8px
}

/* 思考块 */
.block-reasoning {
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  border-radius: var(--r);
  overflow: hidden;
}

.reasoning-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-3);
  cursor: pointer;
}

.reasoning-head svg:last-child {
  transition: transform var(--t);
}

.reasoning-text {
  padding: 0 10px 8px;
  font-size: 12px;
  font-family: var(--mono);
  color: var(--fg-3);
  line-height: 1.6;
}
.reasoning-text :deep(p) { margin: 0.4em 0; }
.reasoning-text :deep(pre) {
  background: var(--bg-3);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 6px 10px;
  margin: 4px 0;
  overflow-x: auto;
  font-size: 11px;
  line-height: 1.5;
}
.reasoning-text :deep(pre code) { background: none; padding: 0; }
.reasoning-text :deep(code) {
  font-size: 11px;
  background: var(--bg-3);
  padding: 1px 4px;
  border-radius: 3px;
}

/* 内容块 */
.block-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--fg);
}

.block-content :deep(pre) {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 10px;
  margin: 6px 0;
  overflow-x: auto;
}

.block-content :deep(code) {
  font-family: var(--mono);
  font-size: 12px;
}

.block-content :deep(pre code) {
  background: none;
  padding: 0;
}

.block-content :deep(strong) {
  font-weight: 600;
}

.block-content :deep(a) {
  color: var(--accent);
  text-decoration: none;
}

.block-content :deep(a:hover) {
  text-decoration: underline;
}

.block-content :deep(h1) { font-size: 1.5em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h2) { font-size: 1.3em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h3) { font-size: 1.1em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h4) { font-size: 1em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h5) { font-size: 0.9em; margin: 0.5em 0; font-weight: 600; }
.block-content :deep(h6) { font-size: 0.8em; margin: 0.5em 0; font-weight: 600; }

.block-content :deep(ul) { margin: 0.5em 0; padding-left: 1.5em; }
.block-content :deep(ol) { margin: 0.5em 0; padding-left: 1.5em; }
.block-content :deep(li) { margin: 0.25em 0; }
.block-content :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.5em 1em;
  border-left: 3px solid var(--accent);
  background: var(--bg-3);
  border-radius: 0 var(--r) var(--r) 0;
}
.block-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5em 0;
}
.block-content :deep(th),
.block-content :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
}
.block-content :deep(th) {
  background: var(--bg-3);
  font-weight: 600;
}
.block-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1em 0;
}
.block-content :deep(p) {
  margin: 0.5em 0;
}
.block-content :deep(p:first-child) { margin-top: 0; }
.block-content :deep(p:last-child) { margin-bottom: 0; }

/* 完成块（finish 工具输出） */
.block-finish {
  margin-top: 2px;
}

.finish-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.finish-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-sm);
  color: var(--green);
}

.finish-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--green);
}

.finish-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--fg);
}

.finish-content :deep(pre) {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 10px;
  margin: 6px 0;
  overflow-x: auto;
}

.finish-content :deep(code) {
  font-family: var(--mono);
  font-size: 12px;
}

.finish-content :deep(pre code) {
  background: none;
  padding: 0;
}

.finish-content :deep(strong) {
  font-weight: 600;
}

.finish-content :deep(a) {
  color: var(--accent);
  text-decoration: none;
}

.finish-content :deep(a:hover) {
  text-decoration: underline;
}

.finish-content :deep(h1) { font-size: 1.5em; margin: 0.5em 0; font-weight: 600; }
.finish-content :deep(h2) { font-size: 1.3em; margin: 0.5em 0; font-weight: 600; }
.finish-content :deep(h3) { font-size: 1.1em; margin: 0.5em 0; font-weight: 600; }
.finish-content :deep(h4) { font-size: 1em; margin: 0.5em 0; font-weight: 600; }
.finish-content :deep(h5) { font-size: 0.9em; margin: 0.5em 0; font-weight: 600; }
.finish-content :deep(h6) { font-size: 0.8em; margin: 0.5em 0; font-weight: 600; }

.finish-content :deep(ul) { margin: 0.5em 0; padding-left: 1.5em; }
.finish-content :deep(ol) { margin: 0.5em 0; padding-left: 1.5em; }
.finish-content :deep(li) { margin: 0.25em 0; }
.finish-content :deep(blockquote) {
  margin: 0.5em 0;
  padding: 0.5em 1em;
  border-left: 3px solid var(--green);
  background: var(--bg-3);
  border-radius: 0 var(--r) var(--r) 0;
}
.finish-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5em 0;
}
.finish-content :deep(th),
.finish-content :deep(td) {
  border: 1px solid var(--border);
  padding: 6px 10px;
  text-align: left;
}
.finish-content :deep(th) {
  background: var(--bg-3);
  font-weight: 600;
}
.finish-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--border);
  margin: 1em 0;
}
.finish-content :deep(p) {
  margin: 0.5em 0;
}
.finish-content :deep(p:first-child) { margin-top: 0; }
.finish-content :deep(p:last-child) { margin-bottom: 0; }

/* 代码块内嵌复制按钮 */
.block-content :deep(.code-block-wrap) {
  margin: 8px 0;
}
.block-content :deep(.code-block-wrap pre) {
  position: relative;
  margin: 0 !important;
}
.block-content :deep(.code-copy-btn) {
  position: absolute;
  top: 6px;
  right: 6px;
  opacity: 0;
  background: var(--bg-2);
  border: 1px solid var(--border);
  font-size: 13px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: var(--r-sm);
  transition: opacity 0.15s;
  line-height: 1;
  z-index: 2;
}
.block-content :deep(.code-block-wrap pre:hover .code-copy-btn) {
  opacity: 0.7;
}
.block-content :deep(.code-copy-btn:hover) {
  opacity: 1 !important;
  background: var(--bg);
}

/* 工具块 */
.block-tool {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  border-radius: var(--r);
  overflow: hidden;
}

.tool-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  transition: background var(--t);
}

.tool-head:hover {
  background: var(--bg-2);
}

.tool-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--r-sm);
}

.tool-icon.执行中 {
  color: var(--yellow);
}

.tool-icon.成功 {
  color: var(--green);
}

.tool-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
}

.tool-status {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: var(--r-sm);
}

.tool-status.执行中 {
  background: var(--yellow-bg);
  color: var(--yellow);
}

.tool-status.成功 {
  background: var(--green-bg);
  color: var(--green);
}

.tool-head svg:last-child {
  margin-left: auto;
  transition: transform var(--t);
  color: var(--fg-4);
}

.tool-detail {
  padding: 0 10px 8px;
  border-top: 1px solid var(--border);
}

.tool-detail pre {
  background: var(--bg-2);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  padding: 8px;
  margin-top: 6px;
  font-size: 11px;
  max-height: 150px;
  overflow: auto;
}

/* 选项按钮 */
.block-choice {
  margin: 4px 0;
}

.choice-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.choice-btn {
  padding: 6px 16px;
  border: 1px solid var(--accent);
  border-radius: var(--r);
  background: var(--accent-bg, rgba(var(--accent-rgb, 59 130 246), 0.1));
  color: var(--accent);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--t);
}

.choice-btn:hover {
  background: var(--accent);
  color: #fff;
}

.choice-resolved {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  font-size: 13px;
  color: var(--fg-3);
}

.choice-label {
  font-weight: 500;
}

.choice-value {
  color: var(--accent);
  font-weight: 600;
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
