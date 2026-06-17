<template>
  <div
    class="rp-panel"
    :class="{ collapsed: !open }"
  >
    <!-- 统一头部：标签页切换 + 刷新 + 关闭 -->
    <div class="rp-head">
      <div class="rp-tabs">
        <button
          class="rp-tab"
          :class="{ active: modelValue === 'git' }"
          @click="$emit('update:modelValue', 'git')"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="18" r="3"/><circle cx="6" cy="6" r="3"/><path d="M13 6h3a2 2 0 0 1 2 2v7"/><line x1="6" y1="9" x2="6" y2="21"/></svg>
          Git
        </button>
        <button
          class="rp-tab"
          :class="{ active: modelValue === 'schedule' }"
          @click="$emit('update:modelValue', 'schedule')"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          定时
        </button>
      </div>
      <div class="rp-head-actions">
        <button v-if="modelValue === 'git'" class="btn-icon-sm" @click="gitRef?.loadStatus?.()" title="刷新 Git 状态">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
        </button>
        <button v-if="modelValue === 'schedule'" class="btn-icon-sm" @click="scheduleRef?.loadTasks?.()" title="刷新定时任务">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M23 4v6h-6"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
        </button>
        <button class="btn-icon-sm" @click="$emit('close')" title="关闭">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
      </div>
    </div>

    <!-- 面板内容（保活：用 v-show） -->
    <div class="rp-body">
      <div v-show="modelValue === 'git'" class="rp-page">
        <GitPanel ref="gitRef" :workspace-hash="workspaceHash" />
      </div>
      <div v-show="modelValue === 'schedule'" class="rp-page">
        <SchedulePanel ref="scheduleRef" :workspace-hash="workspaceHash" :session-name="sessionName" :sessions="sessions" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import GitPanel from './GitPanel.vue'
import SchedulePanel from './SchedulePanel.vue'

const props = defineProps({
  modelValue: { type: String, default: 'git' },
  open: { type: Boolean, default: true },
  workspaceHash: { type: String, default: null },
  sessionName: { type: String, default: '' },
  sessions: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'close'])

const gitRef = ref(null)
const scheduleRef = ref(null)
</script>

<style scoped>
.rp-panel {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border-left: 1px solid var(--glass-border);
  overflow: hidden;
  transition: width 0.2s, opacity 0.2s;
}
.rp-panel.collapsed {
  width: 0;
  opacity: 0;
  border-left: none;
  pointer-events: none;
}

/* 头部标签栏 */
.rp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 0 0;
  border-bottom: 1px solid var(--glass-border);
  min-height: 36px;
  flex-shrink: 0;
}
.rp-head-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
.rp-tabs {
  display: flex;
  gap: 0;
  flex: 1;
}
.rp-tab {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 8px 0;
  font-size: 12px;
  font-weight: 500;
  color: var(--fg-3);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
}
.rp-tab:hover {
  color: var(--fg);
  background: var(--accent-bg);
}
.rp-tab.active {
  color: var(--accent);
  border-bottom-color: var(--accent);
}
.rp-tab svg {
  color: inherit;
}

/* 内容区 */
.rp-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 每个页面撑满 rp-body */
.rp-page {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 嵌入式 GitPanel 去掉自身的头部和外框 */
.rp-page :deep(.git-panel) {
  border-left: none;
  background: transparent;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  width: 100%;
  flex: 1;
}
.rp-page :deep(.git-head) {
  display: none;
}

/* 嵌入式 SchedulePanel 去掉自身的头部和外框 */
.rp-page :deep(.sch-panel) {
  border-left: none;
  background: transparent;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  width: 100%;
  flex: 1;
}
.rp-page :deep(.sch-head) {
  display: none;
}
</style>
