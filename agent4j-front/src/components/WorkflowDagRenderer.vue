<template>
  <div class="workflow-dag">
    <div class="workflow-header">
      <span class="workflow-title">{{ data.title }}</span>
      <span class="workflow-status" :class="statusClass">{{ statusText }}</span>
      <span v-if="data.progress" class="workflow-progress">{{ data.progress }}</span>
    </div>
    
    <div class="workflow-graph">
      <svg class="dag-svg" :width="svgWidth" :height="svgHeight">
        <g class="edges">
          <path v-for="edge in layoutEdges" :key="edge.id"
                :d="edge.path"
                class="edge-line"
                :class="{ 'edge-conditional': edge.type !== 'NORMAL' }"
                fill="none" />
        </g>
        
        <g class="nodes">
          <g v-for="node in layoutNodes" :key="node.id"
             :transform="`translate(${node.x}, ${node.y})`"
             class="node-group"
             :class="`node-${node.status.toLowerCase()}`">
            <rect :x="-nodeWidth/2" :y="-nodeHeight/2"
                  :width="nodeWidth" :height="nodeHeight"
                  rx="4" ry="4"
                  class="node-rect" />
            <text class="node-label" x="0" y="4" text-anchor="middle">{{ truncateText(node.description, 8) }}</text>
            <title>{{ node.id }}: {{ node.description }}</title>
          </g>
        </g>
      </svg>
    </div>
    
    <div class="node-list">
      <div v-for="node in data.nodes" :key="node.id" class="node-item" :class="`status-${node.status.toLowerCase()}`">
        <span class="node-item-icon">{{ getNodeIcon(node.status) }}</span>
        <span class="node-item-id">{{ node.id }}</span>
        <span class="node-item-desc">{{ node.description }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: { type: Object, required: true }
})

const nodeWidth = 110
const nodeHeight = 32
const nodeGapX = 30
const nodeGapY = 16

const statusMap = {
  'DRAFT': { text: '草稿', class: 'draft' },
  'ACTIVE': { text: '进行中', class: 'active' },
  'PAUSED': { text: '暂停', class: 'paused' },
  'COMPLETED': { text: '完成', class: 'completed' },
  'FAILED': { text: '失败', class: 'failed' }
}

const statusText = computed(() => statusMap[props.data.status]?.text || props.data.status)
const statusClass = computed(() => statusMap[props.data.status]?.class || '')

function getNodeIcon(status) {
  const icons = {
    'DONE': '✓', 'RUNNING': '●', 'FAILED': '✗', 'SKIPPED': '⏭',
    'WAITING': '⏸', 'BLOCKED': '⊘', 'READY': '◉', 'PENDING': '○',
    'START': '▶', 'END': '■'
  }
  return icons[status] || '○'
}

function truncateText(text, maxLen) {
  if (!text) return ''
  return text.length > maxLen ? text.substring(0, maxLen) + '..' : text
}

const layoutNodes = computed(() => {
  if (!props.data.nodes) return []
  
  const nodes = props.data.nodes
  const edges = props.data.edges || []
  
  const adjacency = {}
  const inDegree = {}
  nodes.forEach(n => { adjacency[n.id] = []; inDegree[n.id] = 0 })
  edges.forEach(e => {
    if (adjacency[e.from]) adjacency[e.from].push(e.to)
    if (inDegree[e.to] !== undefined) inDegree[e.to]++
  })
  
  const levels = {}
  const queue = []
  nodes.forEach(n => {
    if (inDegree[n.id] === 0) { queue.push(n.id); levels[n.id] = 0 }
  })
  
  while (queue.length > 0) {
    const current = queue.shift()
    adjacency[current].forEach(next => {
      levels[next] = Math.max(levels[next] || 0, levels[current] + 1)
      inDegree[next]--
      if (inDegree[next] === 0) queue.push(next)
    })
  }
  
  const levelGroups = {}
  nodes.forEach(n => {
    const level = levels[n.id] || 0
    if (!levelGroups[level]) levelGroups[level] = []
    levelGroups[level].push(n)
  })
  
  const result = []
  Object.keys(levelGroups).sort((a, b) => a - b).forEach((level, levelIdx) => {
    const group = levelGroups[level]
    const x = 70 + levelIdx * (nodeWidth + nodeGapX)
    
    group.forEach((node, nodeIdx) => {
      const totalHeight = group.length * (nodeHeight + nodeGapY) - nodeGapY
      const startY = (svgHeight.value - totalHeight) / 2
      const y = startY + nodeIdx * (nodeHeight + nodeGapY) + nodeHeight / 2
      result.push({ ...node, x, y })
    })
  })
  
  return result
})

const layoutEdges = computed(() => {
  if (!props.data.edges || !layoutNodes.value.length) return []
  
  const nodeMap = {}
  layoutNodes.value.forEach(n => { nodeMap[n.id] = n })
  
  return props.data.edges.map(edge => {
    const from = nodeMap[edge.from]
    const to = nodeMap[edge.to]
    if (!from || !to) return null
    
    const startX = from.x + nodeWidth / 2
    const startY = from.y
    const endX = to.x - nodeWidth / 2
    const endY = to.y
    const midX = (startX + endX) / 2
    
    return {
      ...edge,
      path: `M ${startX} ${startY} C ${midX} ${startY}, ${midX} ${endY}, ${endX} ${endY}`
    }
  }).filter(Boolean)
})

const svgWidth = computed(() => {
  if (!layoutNodes.value.length) return 200
  const maxX = Math.max(...layoutNodes.value.map(n => n.x))
  return maxX + nodeWidth + 40
})

const svgHeight = computed(() => {
  if (!props.data.nodes) return 100
  const maxNodesInLevel = getMaxNodesInLevel()
  return Math.max(80, maxNodesInLevel * (nodeHeight + nodeGapY) + 30)
})

function getMaxNodesInLevel() {
  if (!props.data.nodes) return 1
  
  const edges = props.data.edges || []
  const nodes = props.data.nodes
  
  const inDegree = {}
  nodes.forEach(n => inDegree[n.id] = 0)
  edges.forEach(e => { if (inDegree[e.to] !== undefined) inDegree[e.to]++ })
  
  const levels = {}
  const queue = []
  nodes.forEach(n => {
    if (inDegree[n.id] === 0) { queue.push(n.id); levels[n.id] = 0 }
  })
  
  const adjacency = {}
  nodes.forEach(n => adjacency[n.id] = [])
  edges.forEach(e => { if (adjacency[e.from]) adjacency[e.from].push(e.to) })
  
  while (queue.length > 0) {
    const current = queue.shift()
    adjacency[current].forEach(next => {
      levels[next] = Math.max(levels[next] || 0, levels[current] + 1)
      inDegree[next]--
      if (inDegree[next] === 0) queue.push(next)
    })
  }
  
  const levelCounts = {}
  Object.values(levels).forEach(level => {
    levelCounts[level] = (levelCounts[level] || 0) + 1
  })
  
  return Math.max(1, ...Object.values(levelCounts))
}
</script>

<style scoped>
.workflow-dag {
  background: var(--bg, #fff);
  border: 1px solid var(--border, #e5e7eb);
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
}

.workflow-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border, #e5e7eb);
}

.workflow-title {
  font-weight: 600;
  color: var(--fg, #333);
}

.workflow-status {
  font-size: 11px;
  padding: 1px 5px;
  border-radius: 3px;
}

.workflow-status.draft { background: var(--bg-3); color: var(--fg-3); }
.workflow-status.active { background: var(--accent-bg); color: var(--accent); }
.workflow-status.paused { background: var(--yellow-bg, #fef3c7); color: var(--yellow, #f59e0b); }
.workflow-status.completed { background: var(--green-bg); color: var(--green); }
.workflow-status.failed { background: var(--red-bg, #fee2e2); color: var(--red, #ef4444); }

.workflow-progress {
  font-size: 11px;
  color: var(--fg-3, #9ca3af);
  margin-left: auto;
}

.workflow-graph {
  overflow-x: auto;
  margin-bottom: 10px;
}

.dag-svg {
  display: block;
}

.edge-line {
  stroke: var(--border);
  stroke-width: 1.5;
}

.edge-conditional {
  stroke: var(--fg-4);
  stroke-dasharray: 4 2;
}

.node-rect {
  fill: var(--bg);
  stroke: var(--border);
  stroke-width: 1.5;
}

.node-done .node-rect { fill: var(--green-bg); stroke: var(--green); }
.node-running .node-rect { fill: var(--accent-bg); stroke: var(--accent); }
.node-failed .node-rect { fill: var(--red-bg, #fef2f2); stroke: var(--red, #ef4444); }
.node-pending .node-rect { fill: var(--bg-3); stroke: var(--border); }

.node-label {
  font-size: 11px;
  fill: var(--fg);
}

.node-list {
  border-top: 1px solid var(--border, #e5e7eb);
  padding-top: 8px;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  font-size: 11px;
  color: var(--fg-2, #6b7280);
}

.node-item.status-done { color: var(--green); }
.node-item.status-running { color: var(--accent); }
.node-item.status-failed { color: var(--red, #ef4444); }

.node-item-icon {
  width: 14px;
  text-align: center;
  font-size: 12px;
}

.node-item-id {
  font-family: var(--mono, monospace);
  color: var(--fg-3, #9ca3af);
  min-width: 24px;
  font-size: 10px;
}

.node-item-desc {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
