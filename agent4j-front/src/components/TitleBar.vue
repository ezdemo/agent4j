<template>
  <div class="titlebar">
    <div class="titlebar-left">
      <button class="btn-icon-sm sidebar-toggle" @click="$emit('toggleSide')" :class="{ active: sideOn }" title="切换侧边栏">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <line x1="9" y1="3" x2="9" y2="21"/>
        </svg>
      </button>
      
      <div class="brand">
        <div class="brand-logo">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" fill="url(#titleGradient)" stroke="url(#titleGradient)" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            <defs>
              <linearGradient id="titleGradient" x1="3" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
                <stop stop-color="#6366f1"/>
                <stop offset="1" stop-color="#8b5cf6"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <span class="brand-name">Agent4j</span>
      </div>
      
      <div v-if="session" class="session-breadcrumb">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="breadcrumb-sep">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
        <span class="session-name">{{ session }}</span>
      </div>
    </div>
    
    <div class="titlebar-center">
      <div class="window-controls">
        <div class="window-dot close"></div>
        <div class="window-dot minimize"></div>
        <div class="window-dot maximize"></div>
      </div>
    </div>
    
    <div class="titlebar-right">
      <button v-if="hasMessages" class="btn-icon-sm action-btn" @click="$emit('clear')" title="清空对话">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="3 6 5 6 21 6"/>
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
        </svg>
      </button>
      
      <button v-if="hasMessages" class="btn-icon-sm action-btn" @click="$emit('export')" title="导出对话">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="7 10 12 15 17 10"/>
          <line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
      </button>
      
      <button class="btn-icon-sm action-btn" @click="$emit('openSettings')" title="设置">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  session: { type: String, default: '' },
  sideOn: { type: Boolean, default: true },
  hasMessages: { type: Boolean, default: false }
})

defineEmits(['toggleSide', 'openSettings', 'clear', 'export'])
</script>

<style scoped>
.titlebar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height, 64px);
  padding: 0 var(--space-4);
  background: var(--surface);
  border-bottom: 1px solid var(--border);
  user-select: none;
  -webkit-app-region: drag;
  position: relative;
  z-index: var(--z-sticky);
}

/* 左侧 */
.titlebar-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  -webkit-app-region: no-drag;
}

.sidebar-toggle {
  color: var(--fg-muted);
  transition: all var(--transition-fast);
}

.sidebar-toggle:hover {
  background: var(--surface-hover);
  color: var(--fg);
}

.sidebar-toggle.active {
  background: var(--accent-soft);
  color: var(--brand-primary);
}

/* 品牌 */
.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.brand-logo {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-name {
  font-size: var(--text-base);
  font-weight: var(--font-bold);
  color: var(--fg);
  letter-spacing: -0.02em;
}

/* 会话面包屑 */
.session-breadcrumb {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--fg-muted);
  font-size: var(--text-sm);
}

.breadcrumb-sep {
  color: var(--fg-muted);
  opacity: 0.5;
}

.session-name {
  color: var(--fg-secondary);
  font-weight: var(--font-medium);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 中间 */
.titlebar-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  -webkit-app-region: no-drag;
}

.window-controls {
  display: flex;
  gap: var(--space-2);
}

.window-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  transition: all var(--transition-fast);
  cursor: pointer;
}

.window-dot.close {
  background: var(--danger);
}

.window-dot.minimize {
  background: var(--warning);
}

.window-dot.maximize {
  background: var(--success);
}

.window-dot:hover {
  transform: scale(1.1);
  opacity: 0.8;
}

/* 右侧 */
.titlebar-right {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  -webkit-app-region: no-drag;
}

.action-btn {
  color: var(--fg-muted);
  transition: all var(--transition-fast);
}

.action-btn:hover {
  background: var(--surface-hover);
  color: var(--fg);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .titlebar {
    height: 56px;
    padding: 0 var(--space-3);
  }
  
  .session-breadcrumb {
    display: none;
  }
  
  .window-controls {
    display: none;
  }
  
  .brand-name {
    display: none;
  }
}

/* 深色模式调整 */
[data-theme="dark"] .titlebar {
  background: var(--bg-secondary);
  border-color: var(--border);
}

[data-theme="dark"] .window-dot.close {
  background: #ff5f57;
}

[data-theme="dark"] .window-dot.minimize {
  background: #febc2e;
}

[data-theme="dark"] .window-dot.maximize {
  background: #28c840;
}

/* 窗口控制按钮样式 */
.window-dot.close:hover {
  background: #ff3b30;
}

.window-dot.minimize:hover {
  background: #ff9500;
}

.window-dot.maximize:hover {
  background: #34c759;
}
</style>