<template>
  <div class="tabbar">
    <div class="tabs-container">
      <div
        v-for="tab in tabs"
        :key="tab.id"
        class="tab"
        :class="{ active: tab.id === activeId, running: tab.state === 'running' }"
        @click="$emit('setActive', tab.id)"
      >
        <div class="tab-indicator" :class="tab.state"></div>
        <div class="tab-content">
          <span class="tab-label">{{ tab.label }}</span>
          <span v-if="tab.state === 'running'" class="tab-status">运行中</span>
        </div>
        <button
          v-if="tabs.length > 1"
          class="btn-icon-sm tab-close"
          @click.stop="$emit('close', tab.id)"
          title="关闭标签"
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>
    
    <button class="btn-icon-sm new-tab-btn" @click="$emit('new')" title="新建标签">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="12" y1="5" x2="12" y2="19"/>
        <line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
defineProps({
  tabs: { type: Array, default: () => [] },
  activeId: { type: String, default: '' }
})

defineEmits(['setActive', 'close', 'new'])
</script>

<style scoped>
.tabbar {
  display: flex;
  align-items: center;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  padding: 0 var(--space-2);
  height: 40px;
  overflow: hidden;
}

.tabs-container {
  display: flex;
  align-items: stretch;
  flex: 1;
  gap: var(--space-1);
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
  padding: var(--space-1) 0;
}

.tabs-container::-webkit-scrollbar {
  display: none;
}

.tab {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  min-width: 0;
  max-width: 200px;
  height: 32px;
  border-radius: var(--radius);
  font-size: var(--text-sm);
  color: var(--fg-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
  position: relative;
  flex-shrink: 0;
}

.tab:hover {
  background: var(--surface-hover);
  color: var(--fg-secondary);
}

.tab.active {
  background: var(--accent-soft);
  color: var(--brand-primary);
  font-weight: var(--font-medium);
}

.tab.running {
  color: var(--warning);
}

/* 标签指示器 */
.tab-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  transition: all var(--transition-fast);
}

.tab-indicator.idle {
  background: var(--fg-muted);
}

.tab-indicator.running {
  background: var(--warning);
  animation: pulse 1.5s ease-in-out infinite;
}

.tab-indicator.done {
  background: var(--success);
}

.tab-indicator.error {
  background: var(--danger);
}

.tab.active .tab-indicator {
  background: var(--brand-primary);
}

.tab.active .tab-indicator.running {
  background: var(--warning);
}

/* 标签内容 */
.tab-content {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex: 1;
  min-width: 0;
}

.tab-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-status {
  font-size: var(--text-xs);
  color: var(--warning);
  background: var(--warning-bg);
  padding: 0.125rem 0.375rem;
  border-radius: var(--radius-full);
  flex-shrink: 0;
}

/* 关闭按钮 */
.tab-close {
  opacity: 0;
  color: var(--fg-muted);
  transition: all var(--transition-fast);
  flex-shrink: 0;
}

.tab:hover .tab-close,
.tab.active .tab-close {
  opacity: 1;
}

.tab-close:hover {
  color: var(--danger);
  background: var(--danger-bg);
}

/* 新建标签按钮 */
.new-tab-btn {
  width: 32px;
  height: 32px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fg-muted);
  transition: all var(--transition-fast);
  flex-shrink: 0;
  margin-left: var(--space-1);
}

.new-tab-btn:hover {
  background: var(--surface-hover);
  color: var(--fg);
}

/* 动画 */
@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(0.9);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .tabbar {
    height: 36px;
    padding: 0 var(--space-1);
  }
  
  .tab {
    padding: 0 var(--space-2);
    max-width: 150px;
  }
  
  .tab-status {
    display: none;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .tab.active {
  background: rgba(129, 140, 248, 0.15);
}

[data-theme="dark"] .tab-indicator.running {
  box-shadow: 0 0 0 3px rgba(251, 191, 36, 0.3);
}
</style>