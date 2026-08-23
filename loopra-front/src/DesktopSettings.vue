<template>
  <div class="desktop-settings-window" :data-theme="theme">
    <header class="ds-header">
      <div class="ds-title">
        <svg fill="none" height="16" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="16">
          <circle cx="12" cy="12" r="3"/>
          <path
              d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
        <span>Loopra 设置</span>
      </div>
      <button class="ds-close" type="button" title="关闭" @click="closeWindow">×</button>
    </header>

    <main class="ds-body">
      <SettingsView class="desktop-settings" />
    </main>
  </div>
</template>

<script setup>
import {computed} from 'vue'
import {useAppStore} from './stores/app'
import SettingsView from './views/Settings.vue'

const store = useAppStore()
const theme = computed(() => store.settings.theme)

async function closeWindow() {
  try {
    if (window.electronAPI?.settingsWindow?.close) {
      await window.electronAPI.settingsWindow.close()
      return
    }
  } catch {}
  window.close()
}
</script>

<style scoped>
.desktop-settings-window {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg);
  color: var(--fg);
  font-size: 14px;
}

/* 标题栏（可拖拽） */
.ds-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  height: 44px;
  min-height: 44px;
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  -webkit-app-region: drag;
  user-select: none;
  flex-shrink: 0;
}

.ds-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
}

.ds-title svg {
  color: var(--fg-3);
}

.ds-close {
  margin-left: auto;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--fg-3);
  font-size: 16px;
  cursor: pointer;
  -webkit-app-region: no-drag;
}

.ds-close:hover {
  background: var(--bg-3);
  color: var(--fg);
}

/* 主体：设置页填满剩余空间 */
.ds-body {
  flex: 1;
  min-height: 0;
}

.desktop-settings {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
</style>