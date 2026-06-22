<template>
  <Teleport to="body">
    <div v-if="open" class="diff-overlay" @click.self="$emit('close')">
      <div class="diff-viewer diff-viewer-sbs">
        <div class="diff-viewer-head">
          <span class="diff-viewer-file">{{ file }}</span>
          <span class="diff-viewer-stat" v-if="stat">{{ stat }}</span>
          <button class="btn-icon-sm" @click="$emit('close')" title="关闭">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        </div>
        <div class="diff-sbs-body">
          <!-- diff 视图 -->
          <template v-if="diffPairs.length > 0">
            <!-- 表头 -->
            <div class="diff-sbs-header">
              <span class="diff-sbs-label diff-sbs-label-old">旧版本</span>
              <span class="diff-sbs-label diff-sbs-label-new">新版本</span>
            </div>
            <!-- 行 -->
            <div v-for="(pair, i) in diffPairs" :key="i" class="diff-sbs-row" :class="'diff-sbs-' + pair.type">
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
          <template v-else-if="rawFileLines.length > 0">
            <div v-for="(line, i) in rawFileLines" :key="i" class="file-content-row">
              <span class="file-content-ln">{{ i + 1 }}</span>
              <span class="file-content-code" v-html="line"></span>
            </div>
          </template>
          <!-- 加载中 -->
          <div v-else class="diff-loading">
            <div class="diff-loading-spinner"></div>
            <span>加载中...</span>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
import { highlightCode, detectLanguage } from '../utils/highlight'
import { sanitize } from '../utils/sanitize'

const props = defineProps({
  open: { type: Boolean, default: false },
  file: { type: String, default: '' },
  diff: { type: String, default: '' },
  stat: { type: String, default: '' }
})

defineEmits(['close'])

// ---- Diff 左右对比 (Side-by-Side) ----
const diffPairs = computed(() => {
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

// ---- 无 hunk 时，把 diff 当原文展示 ----
const rawFileLines = computed(() => {
  if (diffPairs.value.length > 0 || !props.diff) return []
  const lang = detectLanguage(props.file)
  return props.diff.split('\n').map(l => sanitize(highlightCode(l, lang)))
})

function parseSideBySide(diffText) {
  if (!diffText) return []
  const lines = diffText.split('\n')
  const result = []

  let i = 0
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
          type: 'empty'
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
            type: 'replace'
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
            type: 'remove'
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
            type: 'add'
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
            type: 'context'
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
.diff-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
}
.diff-viewer {
  width: min(90vw, 800px);
  max-height: 80vh;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
}
.diff-viewer-sbs {
  width: min(95vw, 1200px);
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
.diff-viewer-stat { font-size: 11px; color: var(--fg-4); white-space: nowrap; }
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
.btn-icon-sm:hover { color: var(--fg); border-color: var(--border-focus); }

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
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 12px;
  color: var(--fg-4);
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
