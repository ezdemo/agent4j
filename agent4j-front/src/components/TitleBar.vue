<template>
  <header class="titlebar" data-tauri-drag-region @dblclick="toggleMaximize">
    <!-- 左侧：侧边栏切换 + Logo + 会话名 -->
    <div class="titlebar-left">
      <button class="tb-btn sidebar-toggle" @click="$emit('toggleSide')" @dblclick.stop :class="{ active: sideOn }" title="切换侧边栏">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <line x1="9" y1="3" x2="9" y2="21"/>
        </svg>
      </button>

      <div class="tb-brand">
        <img src="../assets/logo.png" alt="Agent4j Logo" class="titlebar-logo-img" />
        <span class="tb-brand-name">Agent4j</span>
      </div>

      <div v-if="session" class="tb-breadcrumb">
        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
        <span class="tb-session">{{ session }}</span>
      </div>
    </div>

    <!-- 右侧：操作按钮 + 窗口控制 -->
    <div class="titlebar-right">
      <button v-if="hasMessages" class="tb-btn" @click.stop="$emit('clear')" @dblclick.stop title="清空对话">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="3 6 5 6 21 6"/>
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
        </svg>
      </button>

      <button v-if="hasMessages" class="tb-btn" @click.stop="$emit('export')" @dblclick.stop title="导出对话">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="7 10 12 15 17 10"/>
          <line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
      </button>

      <button class="tb-btn" @click.stop="$emit('openSettings')" @dblclick.stop title="设置">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
      </button>

      <template v-if="isTauri">
        <!-- 分隔线 -->
        <div class="tb-sep"></div>

        <!-- 窗口控制按钮 -->
        <button class="tb-win-btn minimize" @click.stop="minimize" @dblclick.stop title="最小化">
          <svg width="10" height="1" viewBox="0 0 10 1">
            <rect width="10" height="1" fill="currentColor"/>
          </svg>
        </button>
        <button class="tb-win-btn maximize" @click.stop="toggleMaximize" @dblclick.stop :title="isMaximized ? '还原' : '最大化'">
          <!-- 还原图标 -->
          <svg v-if="isMaximized" width="10" height="10" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.2">
            <rect x="2.5" y="0.5" width="9" height="9" rx="1"/>
            <rect x="0.5" y="2.5" width="9" height="9" rx="1" fill="var(--bg)" stroke="currentColor"/>
          </svg>
          <!-- 最大化图标 -->
          <svg v-else width="10" height="10" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.2">
            <rect x="0.5" y="0.5" width="11" height="11" rx="1.5"/>
          </svg>
        </button>
        <button class="tb-win-btn close" @click.stop="closeWindow" @dblclick.stop title="关闭">
          <svg width="10" height="10" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round">
            <line x1="1" y1="1" x2="11" y2="11"/>
            <line x1="11" y1="1" x2="1" y2="11"/>
          </svg>
        </button>
      </template>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

defineProps({
  session: { type: String, default: '' },
  sideOn: { type: Boolean, default: true },
  hasMessages: { type: Boolean, default: false }
})

defineEmits(['toggleSide', 'openSettings', 'clear', 'export'])

const isMaximized = ref(false)
const isTauri = ref(false)
let appWindow = null

onMounted(async () => {
  try {
    const { getCurrentWindow } = await import('@tauri-apps/api/window')
    appWindow = getCurrentWindow()
    isTauri.value = true
    isMaximized.value = await appWindow.isMaximized()

    // 监听窗口状态变化
    await appWindow.onResized(async () => {
      isMaximized.value = await appWindow.isMaximized()
    })
  } catch {
    // 非 Tauri 环境（浏览器开发模式），静默忽略
  }
})

const minimize = async () => {
  if (appWindow) await appWindow.minimize()
}

const toggleMaximize = async () => {
  if (appWindow) await appWindow.toggleMaximize()
}

const closeWindow = async () => {
  if (appWindow) await appWindow.close()
}
</script>

<style scoped>
.titlebar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  min-height: 36px;
  padding: 0 6px 0 10px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
  border-radius: 10px 10px 0 0;
  user-select: none;
  -webkit-app-region: drag;
  z-index: 200;
}

/* ── 左侧 ── */
.titlebar-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.tb-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-3);
  transition: all var(--t);
  flex-shrink: 0;
  -webkit-app-region: no-drag;
}
.tb-btn:hover {
  background: var(--bg-3);
  color: var(--fg);
}

.sidebar-toggle.active {
  background: var(--accent-bg);
  color: var(--accent);
}

.tb-brand {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
}

.titlebar-logo-img {
  width: 18px;
  height: 18px;
  object-fit: contain;
}
.tb-brand-name {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg);
  letter-spacing: -0.01em;
}

.tb-breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  margin-left: 2px;
}
.tb-breadcrumb svg {
  color: var(--fg-4);
  flex-shrink: 0;
  opacity: 0.5;
}
.tb-session {
  font-size: 12px;
  color: var(--fg-3);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

/* ── 右侧 ── */
.titlebar-right {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.tb-sep {
  width: 1px;
  height: 14px;
  background: var(--border);
  margin: 0 4px;
}

/* ── 窗口控制按钮 ── */
.tb-win-btn {
  width: 36px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-3);
  transition: all 80ms ease;
  -webkit-app-region: no-drag;
}

.tb-win-btn.minimize:hover,
.tb-win-btn.maximize:hover {
  background: var(--bg-3);
  color: var(--fg);
}

.tb-win-btn.close:hover {
  background: #e81123;
  color: #fff;
}

/* ── 响应式 ── */
@media (max-width: 768px) {
  .titlebar {
    height: 32px;
    min-height: 32px;
  }
  .tb-breadcrumb {
    display: none;
  }
  .tb-brand-name {
    display: none;
  }
  .tb-win-btn {
    width: 32px;
  }
}

/* 深色模式微调 */
[data-theme="dark"] .titlebar {
  background: var(--bg-2);
  border-color: var(--border);
}

[data-theme="dark"] .tb-win-btn.close:hover {
  background: #c42b1c;
}
</style>
