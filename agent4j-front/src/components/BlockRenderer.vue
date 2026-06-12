<template>
  <template v-for="(block, bi) in blocks" :key="bi">
    <!-- 思考 -->
    <div v-if="block.type === 'reasoning'" class="block-reasoning">
      <div class="reasoning-head" @click="block.showContent = !block.showContent">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        <span>思考</span>
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :style="{ transform: block.showContent ? 'rotate(180deg)' : '' }"><polyline points="6 9 12 15 18 9"/></svg>
      </div>
      <div v-if="block.showContent" class="reasoning-text" v-html="getReasoningHtml(block)"></div>
    </div>

    <!-- 内容 -->
    <div v-else-if="block.type === 'content'" class="block-content" v-html="fmt(block.content)"></div>

    <!-- 工具调用 -->
    <div v-else-if="block.type === 'tool_call'" class="block-tool">
      <div class="tool-head" @click="block.expanded = !block.expanded">
        <span class="tool-icon" :class="block.status">
          <svg v-if="block.status === '执行中'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="animate-spin"><path d="M21 12a9 9 0 11-6.219-8.56"/></svg>
          <svg v-else-if="block.status === '成功'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
          <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/></svg>
        </span>
        <code class="tool-name">{{ block.name }}</code>
        <span class="tool-status" :class="block.status">{{ block.status }}</span>
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :style="{ transform: block.expanded ? 'rotate(180deg)' : '' }"><polyline points="6 9 12 15 18 9"/></svg>
      </div>
      <div v-if="block.expanded" class="tool-detail">
        <pre v-if="block.args"><code>{{ fmtArgs(block.args) }}</code></pre>
        <pre v-if="block.result"><code>{{ block.result }}</code></pre>
      </div>
    </div>
  </template>
</template>

<script setup>
import {md} from '../utils/highlight'

const props = defineProps({
  blocks: { type: Array, required: true }
})

const fmt = c => {
  if (!c) return ''
  return md.parse(c)
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
</script>
