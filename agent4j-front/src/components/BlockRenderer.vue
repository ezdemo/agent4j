<template>
  <template v-if="blocks && blocks.length > 0" v-for="(block, bi) in blocks" :key="bi">
    <!-- 思考 -->
    <div v-if="block.type === 'reasoning'" class="block-reasoning">
      <div class="reasoning-head" @click="block.showContent = !block.showContent">
        <span v-html="THINKING_ICON"></span>
        <span>思考</span>
        <span v-html="CHEVRON_DOWN_ICON" :style="{ transform: block.showContent ? 'rotate(180deg)' : '', display: 'inline-block' }"></span>
      </div>
      <div v-if="block.showContent" class="reasoning-text" v-html="getReasoningHtml(block)"></div>
    </div>

    <!-- 内容 -->
    <div v-else-if="block.type === 'content' && block.content" class="block-content" v-html="fmt(block.content)"></div>

    <!-- 工具调用 -->
    <template v-else-if="block.type === 'tool_call'">
      <!-- finish 工具：完成时将 content 渲染为模型输出样式 -->
      <div v-if="block.name === 'finish' && block.result" class="block-finish">
        <div class="finish-head">
          <span class="finish-icon" v-html="CHECK_ICON"></span>
          <span class="finish-label">最终回答</span>
        </div>
        <div class="finish-content" v-html="fmt(block.result)"></div>
      </div>
      <!-- finish 执行中 -->
      <div v-else-if="block.name === 'finish' && block.status" class="block-tool">
        <div class="tool-head">
          <span class="tool-icon" :class="block.status" v-html="SPINNER_ICON"></span>
          <code class="tool-name">finish</code>
          <span class="tool-status" :class="block.status">{{ block.status }}</span>
        </div>
      </div>
      <!-- 其他工具 -->
      <div v-else class="block-tool">
        <div class="tool-head" @click="block.expanded = !block.expanded">
          <span class="tool-icon" :class="block.status">
            <span v-if="block.status === '执行中'" v-html="SPINNER_ICON"></span>
            <span v-else-if="block.status === '成功'" v-html="CHECK_ICON_SM"></span>
            <span v-else v-html="CIRCLE_ICON"></span>
          </span>
          <code class="tool-name">{{ block.name }}</code>
          <span class="tool-status" :class="block.status">{{ block.status }}</span>
          <span v-html="CHEVRON_DOWN_ICON" :style="{ transform: block.expanded ? 'rotate(180deg)' : '', display: 'inline-block' }"></span>
        </div>
        <div v-if="block.expanded" class="tool-detail">
          <!-- 打开文件按钮 -->
          <div v-if="shouldShowOpenFile(block)" class="tool-actions">
            <button class="open-file-btn" @click="openFile(block)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path>
              </svg>
              打开文件
            </button>
          </div>
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
</template>

<script setup>
import {md} from '../utils/highlight'
import {sanitize} from '../utils/sanitize'
import {THINKING_ICON, CHEVRON_DOWN_ICON, CHECK_ICON, CHECK_ICON_SM, CIRCLE_ICON, SPINNER_ICON} from '../utils/icons'
import {LRUCache} from '../utils/cache'

const props = defineProps({
  blocks: { type: Array, required: true }
})

const emit = defineEmits(['sendChoice', 'openFile'])

// Markdown 渲染缓存
const renderCache = new LRUCache(200)

const fmt = c => {
  if (!c) return ''
  const cached = renderCache.get(c)
  if (cached) return cached
  const result = sanitize(md.parse(c))
  renderCache.set(c, result)
  return result
}

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
  if (typeof a === 'string') { try { return JSON.stringify(JSON.parse(a), null, 2) } catch { return a } }
  return JSON.stringify(a, null, 2)
}

// 检查是否显示打开文件按钮
const shouldShowOpenFile = (block) => {
  if (!block || block.type !== 'tool_call') return false
  const toolName = block.name
  // 只对 write 和 edit 工具显示打开文件按钮
  if (toolName !== 'write' && toolName !== 'edit') return false
  // 检查args是否包含file_path字段
  let args = block.args
  if (typeof args === 'string') {
    try { args = JSON.parse(args) } catch { return false }
  }
  return args && typeof args === 'object' && args.file_path
}

// 获取文件路径
const getFilePath = (block) => {
  let args = block.args
  if (typeof args === 'string') {
    try { args = JSON.parse(args) } catch { return null }
  }
  return args?.file_path || null
}

// 触发打开文件事件
const openFile = (block) => {
  const filePath = getFilePath(block)
  if (filePath) {
    emit('openFile', filePath)
  }
}
</script>

<style scoped>
/* 思考块 */
.block-reasoning {
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  border-radius: var(--r);
  overflow: hidden;
  margin-bottom: 4px;
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

.reasoning-head :deep(svg:last-child) {
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
.reasoning-text :deep(ul) { margin: 0.4em 0; padding-left: 1.5em; }
.reasoning-text :deep(ol) { margin: 0.4em 0; padding-left: 1.5em; }
.reasoning-text :deep(li) { margin: 0.2em 0; }
.reasoning-text :deep(blockquote) {
  margin: 0.4em 0;
  padding: 0.3em 0.8em;
  border-left: 3px solid var(--accent);
  background: var(--bg-3);
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
}
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
  margin-bottom: 4px;
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
  margin-bottom: 4px;
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
  margin-bottom: 4px;
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

.tool-head :deep(svg:last-child) {
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

.tool-actions {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  margin-bottom: 6px;
}

.open-file-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--accent-bg, rgba(var(--accent-rgb, 59 130 246), 0.1));
  border: 1px solid var(--accent);
  border-radius: var(--r-sm);
  color: var(--accent);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--t);
}

.open-file-btn:hover {
  background: var(--accent);
  color: #fff;
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
</style>
