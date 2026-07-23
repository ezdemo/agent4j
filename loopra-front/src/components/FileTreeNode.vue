<template>
  <div v-if="visible" class="file-tree-node">
    <button
      class="file-tree-row"
      :class="{ active: displayNode.path === selectedPath }"
      :style="{ paddingLeft: `${10 + depth * 16}px` }"
      type="button"
      :draggable="!displayNode.directory"
      @click="$emit('toggle', displayNode)"
      @dragstart="startFileDrag"
    >
      <svg v-if="displayNode.directory" class="tree-chevron" :class="{ expanded: displayNode.expanded }" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
      <span v-else class="tree-indent"></span>
      <svg v-if="displayNode.directory" class="file-icon folder-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6.5A2.5 2.5 0 0 1 5.5 4H10l2 2.5h6.5A2.5 2.5 0 0 1 21 9v8.5A2.5 2.5 0 0 1 18.5 20h-13A2.5 2.5 0 0 1 3 17.5z"/></svg>
      <svg v-else class="file-icon" :class="fileClass" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
      <span class="file-tree-name">{{ displayName }}</span>
      <span v-if="displayNode.loading" class="tree-loading"></span>
    </button>
    <div v-if="displayNode.directory && displayNode.expanded">
      <FileTreeNode
        v-for="child in displayNode.children"
        :key="child.path"
        :node="child"
        :depth="depth + 1"
        :query="query"
        :selected-path="selectedPath"
        @toggle="$emit('toggle', $event)"
      />
      <div v-if="!displayNode.loading && displayNode.loaded && displayNode.children.length === 0" class="tree-empty" :style="{ paddingLeft: `${42 + depth * 16}px` }">空文件夹</div>
    </div>
  </div>
</template>

<script setup>
import {computed} from 'vue'

defineOptions({ name: 'FileTreeNode' })

const props = defineProps({
  node: { type: Object, required: true },
  depth: { type: Number, default: 0 },
  query: { type: String, default: '' },
  selectedPath: { type: String, default: '' }
})

defineEmits(['toggle'])

const normalizedQuery = computed(() => props.query.trim().toLowerCase())
const visible = computed(() => !normalizedQuery.value || props.node.name.toLowerCase().includes(normalizedQuery.value) || props.node.children?.some(child => nodeMatches(child, normalizedQuery.value)))
const compactNodes = computed(() => {
  const nodes = [props.node]
  let current = props.node
  while (current.loaded && current.children?.length === 1 && current.children[0].directory) {
    current = current.children[0]
    nodes.push(current)
  }
  return nodes
})
const displayNode = computed(() => compactNodes.value[compactNodes.value.length - 1])
const displayName = computed(() => compactNodes.value.map(node => node.name).join('.'))
const fileClass = computed(() => {
  const extension = displayNode.value.name.split('.').pop()?.toLowerCase()
  if (['js', 'jsx', 'ts', 'tsx', 'vue'].includes(extension)) return 'code-file'
  if (['java', 'kt', 'go', 'py'].includes(extension)) return 'source-file'
  if (['md', 'txt'].includes(extension)) return 'text-file'
  if (['png', 'jpg', 'jpeg', 'gif', 'svg'].includes(extension)) return 'image-file'
  return ''
})

function nodeMatches(node, query) {
  return node.name.toLowerCase().includes(query) || node.children?.some(child => nodeMatches(child, query))
}

function startFileDrag(event) {
  if (displayNode.value.directory) return
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-loopra-file-path', displayNode.value.path)
  event.dataTransfer.setData('text/plain', displayNode.value.path)
}
</script>

<style scoped>
.file-tree-node { min-width: 0; }
.file-tree-row { display: flex; align-items: center; width: 100%; min-height: 30px; padding-right: 8px; border: 0; border-radius: 4px; background: transparent; color: var(--fg-2); cursor: pointer; font: inherit; font-size: 13px; text-align: left; }
.file-tree-row:hover, .file-tree-row.active { background: var(--bg-3); color: var(--fg); }
.file-tree-row[draggable="true"] { cursor: grab; }
.file-tree-row[draggable="true"]:active { cursor: grabbing; }
.tree-chevron, .tree-indent { width: 14px; height: 14px; flex: 0 0 14px; margin-right: 3px; color: var(--fg-4); transition: transform .15s; }
.tree-chevron.expanded { transform: rotate(90deg); }
.file-icon { flex: 0 0 16px; margin-right: 7px; color: var(--fg-4); }
.folder-icon { color: #c6963b; }
.code-file { color: #e4bd42; }.source-file { color: #5d9cec; }.text-file { color: #54a874; }.image-file { color: #d16bb1; }
.file-tree-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tree-loading { width: 11px; height: 11px; margin-left: auto; border: 2px solid var(--border); border-top-color: var(--fg-3); border-radius: 50%; animation: tree-spin .7s linear infinite; }
.tree-empty { min-height: 26px; color: var(--fg-4); font-size: 12px; line-height: 26px; }
@keyframes tree-spin { to { transform: rotate(360deg); } }
</style>
