<template>
  <Teleport to="body">
    <div v-if="open" class="diff-overlay" @click.self="$emit('close')">
      <div ref="viewer" class="diff-viewer diff-viewer-sbs" @mousedown="hideSelectionAction">
        <div class="diff-viewer-head">
          <span class="diff-viewer-file">{{ file }}</span>
          <div v-if="mode === 'diff' && hunkCount > 0" class="diff-hunk-navigation" aria-label="差异块导航">
            <button
              type="button"
              class="btn-icon-sm"
              :disabled="activeHunkIndex <= 0"
              title="上一个差异块"
              aria-label="上一个差异块"
              @click="navigateHunk(-1)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m15 18-6-6 6-6"/></svg>
            </button>
            <span class="diff-hunk-position">{{ activeHunkIndex + 1 }}/{{ hunkCount }}</span>
            <button
              type="button"
              class="btn-icon-sm"
              :disabled="activeHunkIndex >= hunkCount - 1"
              title="下一个差异块"
              aria-label="下一个差异块"
              @click="navigateHunk(1)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m9 18 6-6-6-6"/></svg>
            </button>
          </div>
          <div class="diff-view-tabs" role="tablist" aria-label="文件视图">
            <button type="button" role="tab" :aria-selected="mode === 'content'" :class="{ active: mode === 'content' }"
                    @click="$emit('changeMode', 'content')">原文件</button>
            <button type="button" role="tab" :aria-selected="mode === 'diff'" :class="{ active: mode === 'diff' }"
                    @click="$emit('changeMode', 'diff')">Git Diff</button>
          </div>
          <button class="btn-icon-sm" @click="$emit('close')" title="关闭">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div ref="diffBody" class="diff-sbs-body" @mouseup="showSelectionAction" @scroll="handleBodyScroll">
          <!-- 当前文件代码预览 -->
          <template v-if="mode === 'content' && !loading">
            <div v-for="(line, i) in contentLines" :key="i" class="file-content-row">
              <span class="file-content-ln">{{ i + 1 }}</span>
              <span class="file-content-code" v-html="line"></span>
            </div>
          </template>
          <!-- diff 视图 -->
          <template v-else-if="mode === 'diff' && diffPairs.length > 0">
            <!-- 表头 -->
            <div class="diff-sbs-header">
              <span class="diff-sbs-label diff-sbs-label-old">旧版本</span>
              <span class="diff-sbs-label diff-sbs-label-new">新版本</span>
            </div>
            <!-- 行 -->
            <div v-for="(pair, i) in diffPairs" :key="i" class="diff-sbs-row" :class="'diff-sbs-' + pair.type" :data-hunk-index="pair.hunkIndex" :data-change="pair.type !== 'context'">
              <div class="diff-sbs-cell diff-sbs-cell-left">
                <span class="diff-sbs-ln">{{ pair.leftLineNum ?? '' }}</span>
                <span class="diff-sbs-code" v-html="pair.leftHtml"></span>
              </div>
              <div class="diff-sbs-gutter"></div>
              <div class="diff-sbs-cell diff-sbs-cell-right">
                <span class="diff-sbs-ln">{{ pair.rightLineNum ?? '' }}</span>
                <span class="diff-sbs-code" v-html="pair.rightHtml"></span>
              </div>
            </div>
          </template>
          <!-- 无变更：展示文件原文 -->
          <template v-else-if="mode === 'diff' && rawFileLines.length > 0">
            <div v-for="(line, i) in rawFileLines" :key="i" class="file-content-row">
              <span class="file-content-ln">{{ i + 1 }}</span>
              <span class="file-content-code" v-html="line"></span>
            </div>
          </template>
          <!-- 加载中 -->
          <div v-else-if="loading" class="diff-loading">
            <div class="diff-loading-spinner"></div>
            <span>加载中...</span>
          </div>
          <div v-else class="diff-empty">暂无可预览内容</div>
        </div>
      </div>
      <button
        v-if="selectionAction.visible"
        type="button"
        class="diff-selection-action"
        :style="{ left: `${selectionAction.left}px`, top: `${selectionAction.top}px` }"
        @mousedown.prevent
        @click="addSelectionToSession"
      >
        添加到会话
      </button>
    </div>
  </Teleport>
</template>

<script setup>
import {computed, nextTick, ref, watch} from 'vue'
import {detectLanguage, highlightCode, highlightVersion} from '../utils/highlight'
import {sanitize} from '../utils/sanitize'

const props = defineProps({
  open: { type: Boolean, default: false },
  file: { type: String, default: '' },
  diff: { type: String, default: '' },
  content: { type: String, default: '' },
  mode: { type: String, default: 'diff' },
  loading: { type: Boolean, default: false },
  stat: { type: String, default: '' }
})

const emit = defineEmits(['close', 'changeMode', 'addToSession'])
const viewer = ref(null)
const diffBody = ref(null)
const activeHunkIndex = ref(-1)
const selectionAction = ref({ visible: false, left: 0, top: 0, text: '' })

function showSelectionAction() {
  requestAnimationFrame(() => {
    const selection = window.getSelection()
    const text = selection?.toString().trim()
    const range = selection?.rangeCount ? selection.getRangeAt(0) : null
    if (!text || !range || !viewer.value?.contains(range.commonAncestorContainer)) {
      selectionAction.value.visible = false
      return
    }

    const rect = range.getBoundingClientRect()
    selectionAction.value = {
      visible: true,
      text,
      left: Math.min(Math.max(8, rect.left), window.innerWidth - 110),
      top: Math.max(8, rect.top - 38)
    }
  })
}

function addSelectionToSession() {
  const text = selectionAction.value.text
  if (!text) return
  emit('addToSession', { file: props.file, content: text })
  window.getSelection()?.removeAllRanges()
  selectionAction.value.visible = false
}

function hideSelectionAction() {
  selectionAction.value.visible = false
}

function handleBodyScroll() {
  hideSelectionAction()

  const body = diffBody.value
  if (!body || hunkCount.value === 0) return
  const headerHeight = body.querySelector('.diff-sbs-header')?.offsetHeight || 0
  const currentTop = body.getBoundingClientRect().top + headerHeight + 8
  let index = -1

  for (const row of body.querySelectorAll('[data-change="true"]')) {
    if (row.getBoundingClientRect().top > currentTop) break
    index = Number(row.dataset.hunkIndex)
  }
  activeHunkIndex.value = index
}

// ---- Diff 左右对比 (Side-by-Side) ----
const diffPairs = computed(() => {
  // 主题切换 / 异步高亮就绪后重渲染
  void highlightVersion.value
  if (!props.diff) return []
  const pairs = parseSideBySide(props.diff)
  // 语法高亮：根据文件扩展名检测语言，逐行高亮
  const lang = detectLanguage(props.file)
  for (const p of pairs) {
    p.leftHtml = p.left ? sanitize(highlightCode(p.left, lang)) : ''
    p.rightHtml = p.right ? sanitize(highlightCode(p.right, lang)) : ''
  }
  return pairs
})

const hunkCount = computed(() => {
  const indices = new Set(diffPairs.value.map(pair => pair.hunkIndex))
  return indices.size
})

watch(() => [props.open, props.diff, props.file], () => {
  activeHunkIndex.value = -1
  nextTick(() => {
    if (props.open && diffBody.value) diffBody.value.scrollTop = 0
  })
})

function navigateHunk(direction) {
  const nextIndex = activeHunkIndex.value + direction
  if (nextIndex < 0 || nextIndex >= hunkCount.value) return

  activeHunkIndex.value = nextIndex
  nextTick(() => {
    const body = diffBody.value
    const row = body?.querySelector(`[data-hunk-index="${nextIndex}"][data-change="true"]`) || body?.querySelector(`[data-hunk-index="${nextIndex}"]`)
    if (!body || !row) return
    const headerHeight = body.querySelector('.diff-sbs-header')?.offsetHeight || 0
    const top = body.scrollTop + row.getBoundingClientRect().top - body.getBoundingClientRect().top - headerHeight - 8
    body.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
  })
}

// ---- 无 hunk 时，把 diff 当原文展示 ----
const rawFileLines = computed(() => {
  // 主题切换 / 异步高亮就绪后重渲染
  void highlightVersion.value
  if (diffPairs.value.length > 0 || !props.diff) return []
  const lang = detectLanguage(props.file)
  return props.diff.split('\n').map(l => sanitize(highlightCode(l, lang)))
})

const contentLines = computed(() => {
  // 主题切换 / 异步高亮就绪后重渲染
  void highlightVersion.value
  if (props.mode !== 'content') return []
  const lang = detectLanguage(props.file)
  return props.content.split('\n').map(line => sanitize(highlightCode(line, lang)))
})

function parseSideBySide(diffText) {
  if (!diffText) return []
  const lines = diffText.split('\n')
  const result = []

  let i = 0
  let hunkIndex = -1
  // 跳过元信息行，直到第一个 hunk 头
  while (i < lines.length && !lines[i].startsWith('@@')) {
    i++
  }

  for (; i < lines.length; i++) {
    const line = lines[i]
    if (line.startsWith('@@')) {
      // @@ -oldStart[,oldCount] +newStart[,newCount] @@
      const m = line.match(/@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@/)
      if (!m) continue
      hunkIndex++
      let oldNum = parseInt(m[1])
      let newNum = parseInt(m[3])
      const oldCount = m[2] !== undefined ? parseInt(m[2]) : 1
      const newCount = m[4] !== undefined ? parseInt(m[4]) : 1

      // 处理空文件 diff
      if (oldCount === 0 && newCount === 0) {
        result.push({
          left: '',
          right: '（空文件）',
          leftLineNum: null,
          rightLineNum: null,
          type: 'empty',
          hunkIndex
        })
        continue
      }

      const removedQueue = []
      const addedQueue = []

      const flushQueues = () => {
        // 配对删/改为替换
        while (removedQueue.length > 0 && addedQueue.length > 0) {
          const r = removedQueue.shift()
          const a = addedQueue.shift()
          result.push({
            left: r.content,
            right: a.content,
            leftLineNum: r.lineNum,
            rightLineNum: a.lineNum,
            type: 'replace',
            hunkIndex
          })
        }
        // 纯删除（左栏）
        while (removedQueue.length > 0) {
          const r = removedQueue.shift()
          result.push({
            left: r.content,
            right: '',
            leftLineNum: r.lineNum,
            rightLineNum: null,
            type: 'remove',
            hunkIndex
          })
        }
        // 纯新增（右栏）
        while (addedQueue.length > 0) {
          const a = addedQueue.shift()
          result.push({
            left: '',
            right: a.content,
            leftLineNum: null,
            rightLineNum: a.lineNum,
            type: 'add',
            hunkIndex
          })
        }
      }

      let j = i + 1
      while (j < lines.length && !lines[j].startsWith('@@')) {
        const l = lines[j]
        if (l.startsWith('+') && !l.startsWith('+++')) {
          addedQueue.push({ content: l.substring(1), lineNum: newNum++ })
        } else if (l.startsWith('-') && !l.startsWith('---')) {
          removedQueue.push({ content: l.substring(1), lineNum: oldNum++ })
        } else if (l.startsWith(' ')) {
          flushQueues()
          const content = l.substring(1)
          result.push({
            left: content,
            right: content,
            leftLineNum: oldNum++,
            rightLineNum: newNum++,
            type: 'context',
            hunkIndex
          })
        }
        j++
      }

      flushQueues()
      i = j - 1
    }
  }

  return result
}
</script>

<style scoped>
.diff-viewer {
  width: min(92vw, 980px);
  height: min(84vh, 780px);
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
}
.diff-viewer-sbs {
  width: min(92vw, 980px);
}
.diff-viewer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-2);
}
.diff-viewer-file { font-size: 12px; font-family: var(--mono); color: var(--fg); font-weight: 600; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.diff-hunk-navigation { display: inline-flex; align-items: center; flex-shrink: 0; gap: 4px; }
.diff-hunk-navigation .btn-icon-sm { width: 24px; height: 24px; }
.diff-hunk-position { min-width: 30px; color: var(--fg-4); font: 600 10px var(--mono); text-align: center; }
.diff-view-tabs { display: inline-flex; flex-shrink: 0; gap: 2px; padding: 2px; border: 1px solid var(--border); border-radius: var(--r-sm); background: var(--bg); }
.diff-view-tabs button { min-height: 24px; padding: 2px 8px; border: 0; border-radius: 3px; background: transparent; color: var(--fg-3); font-family: var(--sans); font-size: 11px; font-weight: 600; cursor: pointer; }
.diff-view-tabs button:hover { color: var(--fg); background: var(--bg-3); }
.diff-view-tabs button.active { color: var(--accent); background: color-mix(in srgb, var(--accent) 10%, var(--bg)); }
.btn-icon-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--surface);
  color: var(--fg-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
  flex-shrink: 0;
}
.btn-icon-sm:hover:not(:disabled) { color: var(--fg); border-color: var(--border-focus); }
.btn-icon-sm:disabled { opacity: 0.45; cursor: not-allowed; }
.diff-selection-action {
  position: fixed;
  z-index: 3;
  min-height: 28px;
  padding: 4px 9px;
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--bg);
  box-shadow: 0 5px 16px rgba(0,0,0,0.16);
  color: var(--fg-2);
  cursor: pointer;
  font: 600 12px var(--sans);
}
.diff-selection-action:hover { border-color: var(--accent); color: var(--accent); background: var(--bg-2); }

/* 内容区域（始终占位，避免高度跳动） */
.diff-sbs-body {
  flex: 1;
  overflow: auto;
  font-size: 12px;
  font-family: var(--mono);
  line-height: 1.6;
  background: var(--bg);
  min-height: 120px;
}
.diff-sbs-header {
  display: flex;
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
}
.diff-sbs-label {
  flex: 1;
  padding: 4px 8px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  text-align: center;
}
.diff-sbs-label-old { color: var(--fg-4); border-right: 1px solid var(--border); }
.diff-sbs-label-new { color: var(--fg-4); }

/* 行：diff 和文件内容共用 */
.diff-sbs-row, .file-content-row {
  display: flex;
  border-bottom: 1px solid var(--border-muted);
}
.diff-sbs-row:last-child, .file-content-row:last-child { border-bottom: none; }

.diff-sbs-cell {
  flex: 1;
  display: flex;
  align-items: stretch;
  min-width: 0;
}
.diff-sbs-cell-left { border-right: 1px solid var(--border); }
.diff-sbs-gutter {
  width: 0;
  flex-shrink: 0;
}

.diff-sbs-ln, .file-content-ln {
  flex-shrink: 0;
  width: 40px;
  padding: 0 6px;
  text-align: right;
  font-size: 10px;
  color: var(--fg-4);
  background: var(--bg-2);
  user-select: none;
  border-right: 1px solid var(--border-muted);
  line-height: 1.6;
}
.diff-sbs-code, .file-content-code {
  flex: 1;
  padding: 0 8px;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: anywhere;
  min-width: 0;
}

/* 变更行高亮 */
.diff-sbs-context .diff-sbs-cell { background: transparent; }
.diff-sbs-context .diff-sbs-ln { background: var(--bg-2); }
.diff-sbs-empty .diff-sbs-cell { background: rgba(128, 128, 128, 0.05); }
.diff-sbs-empty .diff-sbs-code { color: var(--fg-4); font-style: italic; }
.diff-sbs-add .diff-sbs-cell-right { background: rgba(16, 185, 129, 0.10); }
.diff-sbs-add .diff-sbs-cell-left .diff-sbs-code { color: var(--fg-4); }
.diff-sbs-remove .diff-sbs-cell-left { background: rgba(239, 68, 68, 0.10); }
.diff-sbs-remove .diff-sbs-cell-right .diff-sbs-code { color: var(--fg-4); }
.diff-sbs-replace .diff-sbs-cell-left { background: rgba(239, 68, 68, 0.10); }
.diff-sbs-replace .diff-sbs-cell-right { background: rgba(16, 185, 129, 0.10); }

[data-theme="dark"] .diff-sbs-add .diff-sbs-cell-right { background: rgba(16, 185, 129, 0.08); }
[data-theme="dark"] .diff-sbs-remove .diff-sbs-cell-left { background: rgba(239, 68, 68, 0.08); }
[data-theme="dark"] .diff-sbs-replace .diff-sbs-cell-left { background: rgba(239, 68, 68, 0.08); }
[data-theme="dark"] .diff-sbs-replace .diff-sbs-cell-right { background: rgba(16, 185, 129, 0.08); }

[data-theme="retro"] .diff-sbs-add .diff-sbs-cell-right { background: rgba(51, 255, 51, 0.08); }
[data-theme="retro"] .diff-sbs-remove .diff-sbs-cell-left { background: rgba(255, 102, 102, 0.08); }
[data-theme="retro"] .diff-sbs-replace .diff-sbs-cell-left { background: rgba(255, 102, 102, 0.08); }
[data-theme="retro"] .diff-sbs-replace .diff-sbs-cell-right { background: rgba(51, 255, 51, 0.08); }

[data-theme="retro-yellow"] .diff-sbs-add .diff-sbs-cell-right { background: rgba(74, 103, 65, 0.10); }
[data-theme="retro-yellow"] .diff-sbs-remove .diff-sbs-cell-left { background: rgba(139, 37, 0, 0.08); }
[data-theme="retro-yellow"] .diff-sbs-replace .diff-sbs-cell-left { background: rgba(139, 37, 0, 0.08); }
[data-theme="retro-yellow"] .diff-sbs-replace .diff-sbs-cell-right { background: rgba(74, 103, 65, 0.10); }

/* 加载中 */
.diff-loading {
  height: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 12px;
  color: var(--fg-4);
}
.diff-empty {
  display: grid;
  min-height: 120px;
  place-items: center;
  color: var(--fg-4);
  font-family: var(--sans);
  font-size: 12px;
}
.diff-loading-spinner {
  width: 24px;
  height: 24px;
  border: 2.5px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: diff-spin 0.7s linear infinite;
}
@keyframes diff-spin { to { transform: rotate(360deg); } }
</style>
