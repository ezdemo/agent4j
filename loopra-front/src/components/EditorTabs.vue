<template>
  <div class="editor-tabs" role="tablist" aria-label="编辑器标签">
    <div
      v-if="fixedTab"
      class="et-tab et-fixed-tab"
      :class="{ active: fixedTab.id === activeId }"
      role="tab"
      :aria-selected="fixedTab.id === activeId"
      :title="fixedTab.title || fixedTab.label"
      @click="$emit('setActive', fixedTab.id)"
    >
      <i v-if="fixedTab.icon" class="codicon et-icon" :class="fixedTab.icon"></i>
      <span class="et-label">
        <span class="et-label-text">{{ fixedTab.label }}</span>
      </span>
    </div>

    <div ref="scrollerRef" class="et-scroll" @scroll="updateScrollState" @wheel="onWheel">
      <div
        v-for="tab in scrollableTabs"
        :key="tab.id"
        class="et-tab"
        :class="{ active: tab.id === activeId }"
        :data-tab-id="tab.id"
        role="tab"
        :aria-selected="tab.id === activeId"
        :title="tab.title || tab.label"
        @click="$emit('setActive', tab.id)"
        @mousedown.middle.prevent="maybeClose(tab)"
      >
        <span
          v-if="tab.fileIcon"
          class="et-file-icon"
          :data-icon="tab.fileIcon.kind"
          :style="{color: tab.fileIcon.color}"
        >{{ tab.fileIcon.glyph }}</span>
        <i v-else-if="tab.icon" class="codicon et-icon" :class="tab.icon"></i>
        <span class="et-label">
          <span class="et-label-text">{{ tab.label }}</span>
        </span>
        <button
          v-if="tab.closable !== false"
          type="button"
          class="et-close"
          :class="{ 'is-dirty': tab.dirty }"
          title="关闭标签"
          aria-label="关闭标签"
          @click.stop="$emit('close', tab.id)"
        >
          <span v-if="tab.dirty" class="et-dirty-dot"></span>
          <i class="codicon codicon-close"></i>
        </button>
      </div>
    </div>

    <div v-if="canScrollLeft || canScrollRight" class="et-scroll-controls">
      <button
        type="button"
        class="et-scroll-button"
        :disabled="!canScrollLeft"
        title="向左滚动标签"
        aria-label="向左滚动标签"
        @click="scrollTabs(-1)"
      >
        <i class="codicon codicon-chevron-left"></i>
      </button>
      <button
        type="button"
        class="et-scroll-button"
        :disabled="!canScrollRight"
        title="向右滚动标签"
        aria-label="向右滚动标签"
        @click="scrollTabs(1)"
      >
        <i class="codicon codicon-chevron-right"></i>
      </button>
    </div>
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'

const props = defineProps({
  // tabs: [{ id, label, title?, icon?, dirty?, closable? }] — 固定标签（如 Chat）closable 传 false 且排在最前
  tabs: { type: Array, default: () => [] },
  activeId: { type: String, default: '' }
})

const emit = defineEmits(['setActive', 'close'])
const scrollerRef = ref(null)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)
const fixedTab = computed(() => props.tabs[0] || null)
const scrollableTabs = computed(() => props.tabs.slice(1))
let resizeObserver = null

function maybeClose(tab) {
  if (tab.closable !== false) emit('close', tab.id)
}

function updateScrollState() {
  const scroller = scrollerRef.value
  if (!scroller) return
  canScrollLeft.value = scroller.scrollLeft > 1
  canScrollRight.value = scroller.scrollLeft + scroller.clientWidth < scroller.scrollWidth - 1
}

function scrollTabs(direction) {
  scrollerRef.value?.scrollBy({left: direction * 240, behavior: 'smooth'})
}

function onWheel(event) {
  const scroller = scrollerRef.value
  if (!scroller || scroller.scrollWidth <= scroller.clientWidth) return
  const delta = Math.abs(event.deltaX) > Math.abs(event.deltaY) ? event.deltaX : event.deltaY
  if (!delta) return
  event.preventDefault()
  scroller.scrollLeft += delta
}

async function revealActiveTab() {
  await nextTick()
  const scroller = scrollerRef.value
  const tab = [...(scroller?.children || [])].find((element) => element.dataset.tabId === props.activeId)
  tab?.scrollIntoView({block: 'nearest', inline: 'nearest'})
  updateScrollState()
}

watch(() => [props.activeId, props.tabs.length], revealActiveTab)

onMounted(() => {
  resizeObserver = new ResizeObserver(updateScrollState)
  if (scrollerRef.value) resizeObserver.observe(scrollerRef.value)
  void revealActiveTab()
})

onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<style scoped>
@font-face {
  font-family: 'Seti';
  src: url('../assets/seti.woff') format('woff');
  font-style: normal;
  font-weight: normal;
  font-display: block;
}

.editor-tabs {
  display: flex;
  height: 36px;
  min-width: 0;
  flex-shrink: 0;
  background: var(--bg-2, #f7f7f8);
  border-bottom: 1px solid var(--border);
  overflow: hidden;
}

.et-scroll {
  display: flex;
  min-width: 0;
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.et-scroll::-webkit-scrollbar {
  display: none;
}

.et-tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  min-width: 80px;
  max-width: 200px;
  flex-shrink: 0;
  font-size: var(--text-sm);
  color: var(--fg-muted);
  cursor: pointer;
  user-select: none;
  border-right: 1px solid var(--border);
  transition: background 0.15s, color 0.15s;
}

.et-fixed-tab {
  z-index: 1;
  min-width: 88px;
  background: var(--bg-2, #f7f7f8);
}

.et-tab:hover {
  background: var(--surface-hover, rgba(0, 0, 0, 0.04));
  color: var(--fg-secondary);
}

.et-tab.active {
  background: var(--bg);
  color: var(--fg);
}

.et-icon {
  font-size: 14px;
  flex-shrink: 0;
  color: var(--fg-muted);
}

.et-file-icon {
  display: inline-block;
  width: 16px;
  height: 22px;
  flex: 0 0 16px;
  font-family: 'Seti', sans-serif;
  font-size: 19.5px;
  font-style: normal;
  font-weight: normal;
  line-height: 22px;
  text-align: left;
  -webkit-font-smoothing: antialiased;
}

.et-label {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.et-label-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.et-dirty-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

.et-close,
.et-scroll-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm, 4px);
  color: var(--fg-muted);
  flex-shrink: 0;
}

.et-close {
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
}

.et-close.is-dirty,
.et-tab:hover .et-close,
.et-tab.active .et-close {
  opacity: 1;
}

.et-close.is-dirty .codicon-close {
  display: none;
}

.et-tab:hover .et-close.is-dirty .et-dirty-dot {
  display: none;
}

.et-tab:hover .et-close.is-dirty .codicon-close {
  display: block;
}

.et-close:hover {
  background: var(--danger-bg, rgba(239, 68, 68, 0.12));
  color: var(--danger);
}

.et-scroll-controls {
  display: flex;
  align-items: center;
  gap: 1px;
  padding: 0 3px;
  flex-shrink: 0;
  background: var(--bg-2, #f7f7f8);
  border-left: 1px solid var(--border);
}

.et-scroll-button:hover:not(:disabled) {
  background: var(--surface-hover, rgba(0, 0, 0, 0.04));
  color: var(--fg);
}

.et-scroll-button:disabled {
  opacity: 0.35;
  cursor: default;
}

[data-theme="dark"] .et-tab:hover,
[data-theme="dark"] .et-scroll-button:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.06);
}
</style>
