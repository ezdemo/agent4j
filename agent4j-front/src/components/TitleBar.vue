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
        <span class="tb-version" :class="{ 'has-update': hasNewVersion }" @click.stop="$emit('showUpdate')" :title="hasNewVersion ? '有新版本可用，点击查看' : ''">
          v{{ version }}
          <span v-if="hasNewVersion" class="tb-version-dot"></span>
        </span>
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
      <button class="tb-btn" title="系统提示词" @click.stop="$emit('viewPrompt')" @dblclick.stop>
        <svg fill="none" height="13" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
             viewBox="0 0 24 24" width="13">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="16" x2="8" y1="13" y2="13"/>
          <line x1="16" x2="8" y1="17" y2="17"/>
          <polyline points="10 9 9 9 8 9"/>
        </svg>
      </button>

      <button class="tb-btn" :class="{ active: gitOn }" :disabled="!hasSession" @click.stop="hasSession && $emit('toggleGit')" @dblclick.stop title="面板">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <line x1="15" y1="3" x2="15" y2="21"/>
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
import {onMounted, ref} from 'vue'

defineProps({
  session: { type: String, default: '' },
  sideOn: { type: Boolean, default: true },
  hasMessages: { type: Boolean, default: false },
  hasSession: { type: Boolean, default: false },
  gitOn: { type: Boolean, default: false },
  version: { type: String, default: '' },
  hasNewVersion: { type: Boolean, default: false }
})

defineEmits(['toggleSide', 'openSettings', 'clear', 'export', 'toggleGit', 'showUpdate', 'viewPrompt'])

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
  if (appWindow) {
    await appWindow.close()
  } else {
    // 浏览器环境：尝试关闭标签页
    window.close()
  }
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
  background: var(--glass-bg-2);
  backdrop-filter: blur(var(--blur));
  -webkit-backdrop-filter: blur(var(--blur));
  border-bottom: 1px solid var(--glass-border);
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
.tb-btn:disabled {
  opacity: 0.35;
  pointer-events: none;
}
.tb-btn.active {
  background: var(--accent-bg);
  color: var(--accent);
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
  background: var(--glass-bg-2);
  border-color: var(--glass-border);
}

[data-theme="dark"] .tb-win-btn.close:hover {
  background: #c42b1c;
}

/* 版本号 */
.tb-version {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--fg-4);
  margin-left: 4px;
  padding: 1px 5px;
  border-radius: 4px;
  cursor: pointer;
  transition: all var(--t);
  position: relative;
  -webkit-app-region: no-drag;
  user-select: none;
}

.tb-version:hover {
  color: var(--accent);
  background: var(--bg-3);
}

.tb-version.has-update {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

.tb-version.has-update:hover {
  background: rgba(239, 68, 68, 0.2);
}

.tb-version-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 8px;
  height: 8px;
  background: #ef4444;
  border-radius: 50%;
  animation: tb-version-pulse 1.5s ease-in-out infinite;
}

@keyframes tb-version-pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.4;
    transform: scale(1.3);
  }
}

@media (max-width: 768px) {
  .tb-version {
    display: none;
  }
}
</style>
