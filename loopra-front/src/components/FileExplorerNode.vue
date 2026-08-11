<template>
  <div class="fen-node">
    <div
      class="fen-row"
      :class="{ active: rowNode.path && rowNode.path === selectedPath }"
      :draggable="!rowNode.directory && !rowNode.editing"
      @click="$emit('toggle', rowNode)"
      @dblclick="$emit('dblclick', rowNode)"
      @contextmenu.prevent.stop="$emit('contextmenu', $event, rowNode)"
      @dragstart="startFileDrag"
    >
      <!-- 缩进导引线位于内容左侧；每级宽度固定 16px。 -->
      <div v-if="depth > 0" class="fen-indent" :style="{ left: '16px' }">
        <span v-for="i in depth" :key="i" class="fen-indent-guide"></span>
      </div>
      <!-- twistie / 占位按深度右移，保证导引线不会压到图标或名称。 -->
      <i
        :class="rowNode.directory
          ? `codicon ${rowNode.expanded ? 'codicon-chevron-down' : 'codicon-chevron-right'} fen-chevron`
          : 'fen-twistie-placeholder'"
        :style="{ marginLeft: `${depth * 16}px` }"
      ></i>
      <!-- 目录与截图一致只显示展开箭头；文件显示紧凑的类型图标。 -->
      <span
        v-if="!rowNode.directory"
        class="fen-file-icon"
        :data-icon="fileIcon.kind"
        :style="{ color: fileIcon.color }"
        aria-hidden="true"
      >{{ fileIcon.glyph }}</span>
      <template v-if="!rowNode.editing">
        <span
          class="fen-name"
          :class="[decoration ? [`decorated`, `is-${decoration.kind}`] : [], { compact: compactNodes.length > 1 }]"
          :title="rowNode.path || rowNode.name"
        >
          <template v-for="(item, index) in compactNodes" :key="item.path || item.uid">
            <span v-if="index" class="fen-compact-separator">\</span>{{ item.name }}
          </template>
        </span>
        <span
          v-if="decoration"
          class="fen-decoration"
          :class="[`is-${decoration.kind}`, { 'is-directory': rowNode.directory }]"
        >{{ rowNode.directory ? '' : decoration.label }}</span>
        <span v-if="rowNode.loading" class="fen-loading"></span>
      </template>
      <input
        v-else
        ref="editInput"
        v-model="rowNode.editValue"
        class="fen-edit-input"
        :class="{ 'is-folder': rowNode.directory }"
        :placeholder="rowNode.directory ? '文件夹名' : '文件名'"
        @click.stop
        @keydown.enter="$emit('edit-commit', rowNode)"
        @keydown.esc="$emit('edit-cancel', rowNode)"
        @blur="$emit('edit-commit', rowNode)"
      />
    </div>
    <div v-if="rowNode.directory && rowNode.expanded">
      <FileExplorerNode
        v-for="child in rowNode.children"
        :key="child.path || child.uid"
        :node="child"
        :depth="depth + 1"
        :selected-path="selectedPath"
        :decorations="decorations"
        @toggle="(n) => $emit('toggle', n)"
        @dblclick="(n) => $emit('dblclick', n)"
        @contextmenu="(e, n) => $emit('contextmenu', e, n)"
        @edit-commit="(n) => $emit('edit-commit', n)"
        @edit-cancel="(n) => $emit('edit-cancel', n)"
      />
    </div>
  </div>
</template>

<script setup>
import {computed, nextTick, ref, watch} from 'vue'
import {fileIconFor} from '../utils/fileIcons'

const props = defineProps({
  node: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  selectedPath: { type: String, default: '' },
  decorations: { type: Object, default: () => ({}) }
})

defineEmits(['toggle', 'dblclick', 'contextmenu', 'edit-commit', 'edit-cancel'])

const editInput = ref(null)

const compactNodes = computed(() => {
  const chain = [props.node]
  let current = props.node
  while (
    current.directory &&
    current.expanded &&
    current.loaded &&
    current.children?.length === 1 &&
    current.children[0].directory
  ) {
    current = current.children[0]
    chain.push(current)
  }
  return chain
})
const rowNode = computed(() => compactNodes.value.at(-1))
const decoration = computed(() => props.decorations[normalizePath(rowNode.value.path)] || null)
const fileIcon = computed(() => fileIconFor(rowNode.value.name))

function startFileDrag(event) {
  if (rowNode.value.directory || rowNode.value.editing) return
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-loopra-file-path', rowNode.value.path)
  event.dataTransfer.setData('text/plain', rowNode.value.path)
}

function normalizePath(path) {
  return String(path || '').replace(/\\/g, '/').replace(/\/+$/, '').toLowerCase()
}

// 进入编辑态时自动聚焦；重命名文件时按 VS Code 习惯只选中文件名主体（不含扩展名）
watch(() => rowNode.value.editing, (editing) => {
  if (editing) {
    void nextTick(() => {
      const input = editInput.value
      if (!input) return
      input.focus()
      const value = rowNode.value.name || ''
      if (!rowNode.value.directory) {
        const lastDot = value.lastIndexOf('.')
        if (lastDot > 0) {
          input.setSelectionRange(0, lastDot)
          return
        }
      }
      input.select()
    })
  }
})
</script>

<style scoped>
@font-face {
  font-family: 'Seti';
  src: url('../assets/seti.woff') format('woff');
  font-style: normal;
  font-weight: normal;
  font-display: block;
}

.fen-node {
  min-width: 0;
}
.fen-row {
  position: relative;
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
/* 悬停背景（VS Code 浅色 #e8e8e8 / 深色 #2a2d2e） */
.fen-row:hover {
  background: #e8e8e8;
}
[data-theme="dark"] .fen-row:hover {
  background: #2a2d2e;
}
/* 选中行使用截图中的非焦点选择色，保留 Git 装饰文字颜色。 */
.fen-row.active {
  background: #e4e6ef;
}
[data-theme="dark"] .fen-row.active {
  background: #37373d;
}
/* 缩进导引线：固定左基线，每级 16px；始终位于本级图标左侧。 */
.fen-indent {
  height: 100%;
  position: absolute;
  top: 0;
  pointer-events: none;
}
.fen-indent-guide {
  display: inline-block;
  box-sizing: border-box;
  width: 16px;
  height: 100%;
  border-left: 1px solid #e8e8e8;
}
[data-theme="dark"] .fen-indent-guide {
  border-left-color: #404040;
}
/* twistie：codicon-chevron-right，10px 字体；左内边距由 depth 决定。 */
.fen-chevron {
  box-sizing: content-box;
  width: 16px;
  height: 100%;
  padding-right: 6px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: inherit;
  font-size: 10px;
  line-height: 22px;
  text-align: right;
}
/* 文件行 twistie 占位与目录箭头占用相同宽度。 */
.fen-twistie-placeholder {
  flex: 0 0 22px;
}
.fen-file-icon {
  box-sizing: content-box;
  display: inline-block;
  flex: 0 0 16px;
  width: 16px;
  height: 22px;
  padding-right: 6px;
  color: #6d8086;
  font-family: 'Seti', sans-serif;
  font-size: 19.5px;
  font-style: normal;
  font-weight: normal;
  line-height: 22px;
  text-align: left;
  vertical-align: top;
  -webkit-font-smoothing: antialiased;
}
.fen-name {
  flex: 1;
  min-width: 0;
  height: 22px;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 22px;
}
.fen-name.compact {
  display: flex;
  align-items: center;
  gap: 4px;
}
.fen-compact-separator {
  color: var(--fg-4, #9ca3af);
}
.fen-name.decorated { color: #a67622; }
.fen-name.decorated.is-untracked,
.fen-name.decorated.is-added { color: #168124; }
.fen-name.decorated.is-deleted { color: #c43b3b; }
.fen-decoration {
  flex: 0 0 18px;
  margin-left: 6px;
  color: #a67622;
  font-size: 12px;
  text-align: center;
}
.fen-decoration.is-directory {
  width: 6px;
  height: 6px;
  flex-basis: 6px;
  margin-right: 3px;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.68;
}
.fen-decoration.is-untracked { color: #168124; }
.fen-decoration.is-deleted { color: #c43b3b; }
.fen-decoration.is-added { color: #168124; }
.fen-decoration.is-modified { color: #a67622; }
.fen-loading {
  width: 11px;
  height: 11px;
  flex: 0 0 auto;
  border: 2px solid var(--border);
  border-top-color: var(--fg-3);
  border-radius: 50%;
  animation: fen-spin 0.7s linear infinite;
}
/* 内联编辑输入框（VS Code monaco-inputbox 风格：保留图标，仅名称区变输入框） */
.fen-edit-input {
  flex: 1;
  min-width: 0;
  height: 20px;
  padding: 0 4px;
  margin: 0 2px;
  border: 1px solid #007acc;
  border-radius: 3px;
  outline: none;
  background: var(--bg, #fff);
  color: var(--fg, #1f1f1f);
  font: inherit;
  font-size: 13px;
}
[data-theme="dark"] .fen-edit-input {
  background: #3c3c3c;
  color: #cccccc;
}
@keyframes fen-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
