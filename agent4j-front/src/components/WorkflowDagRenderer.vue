<template>
  <div class="workflow-dag">
    <div class="workflow-header">
      <span class="workflow-title">{{ data.title }}</span>
      <span class="workflow-status" :class="statusClass">{{ statusText }}</span>
      <span v-if="data.progress" class="workflow-progress">{{ data.progress }}</span>
    </div>
    
    <div class="workflow-graph">
      <svg class="dag-svg" :width="svgWidth" :height="svgHeight">
        <defs>
          <marker id="arrow" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
            <path d="M0,0 L8,3 L0,6" fill="none" stroke="var(--fg-4)" stroke-width="1" />
          </marker>
          <marker id="arrow-blue" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
            <path d="M0,0 L8,3 L0,6" fill="none" stroke="var(--blue)" stroke-width="1" />
          </marker>
          <marker id="arrow-green" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
            <path d="M0,0 L8,3 L0,6" fill="none" stroke="var(--green)" stroke-width="1" />
          </marker>
          <marker id="arrow-red" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
            <path d="M0,0 L8,3 L0,6" fill="none" stroke="var(--red)" stroke-width="1" />
          </marker>
          <marker id="arrow-amber" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
            <path d="M0,0 L8,3 L0,6" fill="none" stroke="var(--yellow)" stroke-width="1" />
          </marker>
        </defs>
        
        <!-- 边 -->
        <g class="edges">
          <template v-for="edge in layoutEdges" :key="edge.id">
            <path :d="edge.path" class="edge-line" :class="`edge-${edge.type}`" fill="none" :marker-end="getMarker(edge.type)" />
            <g v-if="edge.label" :transform="`translate(${edge.labelX}, ${edge.labelY})`">
              <rect :x="-edge.labelW/2" :y="-8" :width="edge.labelW" height="14" rx="2" class="edge-label-bg" />
              <text class="edge-label" text-anchor="middle" y="3">{{ edge.label }}</text>
            </g>
          </template>
        </g>
        
        <!-- 节点 -->
        <g class="nodes">
          <g v-for="node in layoutNodes" :key="node.id"
             :transform="`translate(${node.x}, ${node.y})`"
             class="node-group" :class="[node.status, node.type]">
            <rect :x="-nodeW/2" :y="-nodeH/2" :width="nodeW" :height="nodeH" rx="4" class="node-rect" />
            <text class="node-id" :x="-nodeW/2 + 6" :y="-nodeH/2 + 12" font-size="9">{{ node.id }}</text>
            <text class="node-type" :x="nodeW/2 - 6" :y="-nodeH/2 + 12" text-anchor="end" font-size="8">{{ node.type }}</text>
            <text class="node-desc" x="0" y="6" text-anchor="middle" font-size="11">{{ trunc(node.description) }}</text>
            <title>{{ node.id }}: {{ node.description }}</title>
          </g>
        </g>
      </svg>
    </div>
    
    <div class="node-list">
      <div v-for="node in data.nodes" :key="node.id" class="node-item" :class="[node.status, node.type]">
        <span class="ni-id">{{ node.id }}</span>
        <span class="ni-type">{{ node.type }}</span>
        <span class="ni-desc">{{ node.description }}</span>
        <span class="ni-status">{{ statusIcon(node.status) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ data: Object })

const nodeW = 120
const nodeH = 40
const gapX = 70
const gapY = 50
const padX = 50
const padY = 80

const statusText = computed(() => ({ DRAFT:'草稿', ACTIVE:'进行中', PAUSED:'暂停', COMPLETED:'完成', FAILED:'失败' })[props.data?.status] || props.data?.status)
const statusClass = computed(() => (props.data?.status || '').toLowerCase())

function statusIcon(s) {
  return { DONE:'v', RUNNING:'>', FAILED:'x', PENDING:'o' }[s] || '?'
}

function trunc(text) {
  if (!text) return ''
  return text.length > 10 ? text.slice(0, 10) + '..' : text
}

function getMarker(type) {
  const map = {
    'NORMAL': 'url(#arrow)',
    'CONDITION_SELECT': 'url(#arrow-blue)',
    'CONDITION_TRUE': 'url(#arrow-green)',
    'CONDITION_FALSE': 'url(#arrow-red)',
    'LOOP_BACK': 'url(#arrow-amber)'
  }
  return map[type] || 'url(#arrow)'
}

// ============ 布局算法 ============
const layoutNodes = computed(() => {
  if (!props.data?.nodes) return []
  const nodes = props.data.nodes
  const edges = (props.data.edges || [])
  
  // 构建图
  const children = {}, parents = {}
  nodes.forEach(n => { children[n.id] = []; parents[n.id] = [] })
  edges.forEach(e => {
    if (e.type !== 'LOOP_BACK') {
      children[e.from]?.push(e.to)
      parents[e.to]?.push(e.from)
    }
  })
  
  // BFS 计算层级
  const level = {}
  const queue = []
  nodes.forEach(n => {
    if (!parents[n.id]?.length) { queue.push(n.id); level[n.id] = 0 }
  })
  
  const visited = new Set()
  while (queue.length) {
    const id = queue.shift()
    if (visited.has(id)) continue
    visited.add(id)
    children[id]?.forEach(cid => {
      level[cid] = Math.max(level[cid] || 0, level[id] + 1)
      queue.push(cid)
    })
  }
  nodes.forEach(n => { if (level[n.id] === undefined) level[n.id] = 0 })
  
  // 按层级分组
  const groups = {}
  nodes.forEach(n => { (groups[level[n.id]] ||= []).push(n) })
  const sortedLevels = Object.keys(groups).sort((a, b) => a - b)
  
  // 识别条件分支
  const condBranches = {}
  edges.forEach(e => {
    if (e.type === 'CONDITION_SELECT' || e.type === 'CONDITION_TRUE' || e.type === 'CONDITION_FALSE') {
      if (!condBranches[e.from]) condBranches[e.from] = []
      condBranches[e.from].push({ target: e.to, type: e.type, label: e.label })
    }
  })
  
  // 计算坐标
  const coords = {}
  const mainY = padY + 150
  
  sortedLevels.forEach((l, li) => {
    const group = groups[l]
    const x = padX + li * (nodeW + gapX)
    
    if (group.length === 1) {
      coords[group[0].id] = { x, y: mainY }
    } else {
      const parentNodes = [...new Set(group.flatMap(n => parents[n.id]))]
      const isCondBranch = parentNodes.some(pid => condBranches[pid])
      
      if (isCondBranch && group.length === 2) {
        coords[group[0].id] = { x, y: mainY - gapY }
        coords[group[1].id] = { x, y: mainY + gapY }
      } else {
        const totalH = (group.length - 1) * gapY
        group.forEach((n, ni) => {
          coords[n.id] = { x, y: mainY - totalH/2 + ni * gapY }
        })
      }
    }
  })
  
  return nodes.map(n => ({ ...n, ...coords[n.id] }))
})

const layoutEdges = computed(() => {
  if (!props.data?.edges || !layoutNodes.value.length) return []
  const map = {}
  layoutNodes.value.forEach(n => map[n.id] = n)
  
  return props.data.edges.map(e => {
    const f = map[e.from], t = map[e.to]
    if (!f || !t) return null
    
    let path, lx, ly, labelW = 0
    const sx = f.x + nodeW/2
    const sy = f.y
    const ex = t.x - nodeW/2
    const ey = t.y
    
    if (e.type === 'LOOP_BACK') {
      const upY = Math.min(f.y, t.y) - nodeH - 25
      path = `M${f.x},${f.y - nodeH/2} L${f.x},${upY} L${t.x},${upY} L${t.x},${t.y - nodeH/2}`
      lx = (f.x + t.x) / 2
      ly = upY - 8
    } else if (e.type === 'CONDITION_SELECT' || e.type === 'CONDITION_TRUE' || e.type === 'CONDITION_FALSE') {
      const midX = sx + 20
      path = `M${sx},${sy} L${midX},${sy} L${midX},${ey} L${ex},${ey}`
      lx = midX + 15
      ly = (sy + ey) / 2
    } else if (Math.abs(sy - ey) < 5) {
      path = `M${sx},${sy} L${ex},${ey}`
      lx = (sx + ex) / 2
      ly = sy - 12
    } else {
      const midX = (sx + ex) / 2
      path = `M${sx},${sy} C${midX},${sy} ${midX},${ey} ${ex},${ey}`
      lx = midX
      ly = Math.min(sy, ey) - 12
    }
    
    if (e.label) {
      labelW = Math.max(40, e.label.length * 8 + 12)
    }
    
    return { ...e, path, labelX: lx, labelY: ly, labelW }
  }).filter(Boolean)
})

const svgWidth = computed(() => {
  if (!layoutNodes.value.length) return 200
  return Math.max(...layoutNodes.value.map(n => n.x)) + nodeW + padX + 30
})

const svgHeight = computed(() => {
  if (!layoutNodes.value.length) return 100
  const ys = layoutNodes.value.map(n => n.y)
  const maxY = Math.max(...ys)
  const hasLoop = props.data?.edges?.some(e => e.type === 'LOOP_BACK')
  return maxY + nodeH/2 + (hasLoop ? 70 : 40)
})
</script>

<style scoped>
.workflow-dag {
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  padding: 10px;
  font-size: 12px;
  color: var(--fg);
}

.workflow-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.workflow-title { font-weight: 600; }
.workflow-status { font-size: 11px; padding: 1px 6px; border-radius: var(--r-sm); background: var(--bg-3); color: var(--fg-3); }
.workflow-status.active { background: var(--accent-bg); color: var(--accent); }
.workflow-status.completed { background: var(--green-bg); color: var(--green); }
.workflow-status.failed { background: var(--red-bg); color: var(--red); }
.workflow-progress { font-size: 11px; color: var(--fg-4); margin-left: auto; }

.workflow-graph { overflow-x: auto; margin-bottom: 10px; }

.edge-NORMAL { stroke: var(--fg-4); stroke-width: 1.5; }
.edge-CONDITION_SELECT { stroke: var(--blue); stroke-width: 2; }
.edge-CONDITION_TRUE { stroke: var(--green); stroke-width: 2; }
.edge-CONDITION_FALSE { stroke: var(--red); stroke-width: 2; }
.edge-LOOP_BACK { stroke: var(--yellow); stroke-width: 1.5; stroke-dasharray: 8 4; }

.edge-label-bg { fill: var(--bg); stroke: var(--border); stroke-width: 0.5; }
.edge-label { font-size: 9px; fill: var(--fg-2); font-weight: 500; }

.node-rect { fill: var(--bg-2); stroke: var(--border-2); stroke-width: 1.5; }
.PARALLEL .node-rect { fill: var(--blue-bg); stroke: var(--blue); }
.CONDITION .node-rect { fill: var(--yellow-bg); stroke: var(--yellow); stroke-width: 2; }
.SUBFLOW .node-rect { fill: var(--purple-bg, var(--bg-3)); stroke: var(--purple, var(--fg-4)); }
.HITL .node-rect { fill: var(--orange-bg, var(--bg-3)); stroke: var(--orange, var(--fg-4)); }
.LOOP .node-rect { fill: var(--cyan-bg, var(--bg-3)); stroke: var(--cyan, var(--fg-4)); }

.DONE .node-rect { stroke: var(--green); }
.RUNNING .node-rect { stroke: var(--accent); }
.FAILED .node-rect { stroke: var(--red); }

.node-id { fill: var(--fg-4); font-family: var(--mono); }
.node-type { fill: var(--fg-4); text-transform: uppercase; }
.node-desc { fill: var(--fg); }

.node-list { border-top: 1px solid var(--border); padding-top: 8px; }

.node-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 6px;
  font-size: 11px;
  border-radius: var(--r-sm);
  color: var(--fg-2);
}

.node-item.DONE { color: var(--green); }
.node-item.RUNNING { color: var(--accent); }
.node-item.FAILED { color: var(--red); }

.ni-id { font-family: var(--mono); color: var(--fg-4); min-width: 28px; font-size: 10px; }
.ni-type { font-size: 9px; color: var(--fg-4); min-width: 50px; }
.ni-desc { flex: 1; }
.ni-status { width: 14px; text-align: center; }
</style>
