<template>
  <main class="ai-browser-shell">
    <section class="ai-browser-tabs" aria-label="浏览器标签页">
      <button
        v-for="tab in state.tabs"
        :key="tab.id"
        class="ai-browser-tab"
        :class="{ active: tab.id === state.activeTabId }"
        :title="tab.title || tab.url"
        @click="activate(tab.id)"
      >
        <span v-if="tab.loading" class="ai-browser-spinner"></span>
        <span v-else class="ai-browser-favicon">{{ tab.title?.slice(0, 1) || 'W' }}</span>
        <span class="ai-browser-tab-title">{{ tab.title || '新标签页' }}</span>
        <span class="ai-browser-tab-close" title="关闭标签页" @click.stop="closeTab(tab.id)">×</span>
      </button>
      <button class="ai-browser-icon-button ai-browser-new-tab" title="新建标签页" @click="newTab()">+</button>
    </section>

    <section class="ai-browser-toolbar">
      <div class="ai-browser-nav-actions">
        <button class="ai-browser-icon-button" title="后退" :disabled="!activeTab?.canGoBack" @click="history('back')">‹</button>
        <button class="ai-browser-icon-button" title="前进" :disabled="!activeTab?.canGoForward" @click="history('forward')">›</button>
        <button class="ai-browser-icon-button reload" title="刷新" :disabled="!activeTab" @click="history('reload')">↻</button>
      </div>
      <form class="ai-browser-address-form" @submit.prevent="navigate">
        <span class="ai-browser-address-lock">⌁</span>
        <input v-model="address" class="ai-browser-address" autocomplete="off" spellcheck="false" placeholder="输入网址或搜索内容" />
      </form>
    </section>

    <section class="ai-browser-activity" :class="activity.state">
      <span class="ai-browser-activity-dot"></span>
      <strong>AI</strong>
      <span class="ai-browser-activity-message">{{ activity.message }}</span>
      <code v-if="activity.targetId">{{ activity.targetId }}</code>
    </section>

    <section ref="nativeHostRef" class="ai-browser-content">
      <div v-if="!activeTab" class="ai-browser-empty">
        <strong>新建一个标签页开始浏览</strong>
        <button class="ai-browser-primary" @click="newTab()">新建标签页</button>
      </div>
    </section>

    <footer class="ai-browser-status">
      <span>{{ activeTab?.loading ? '正在加载...' : (activeTab?.url || '就绪') }}</span>
      <span>AI 可通过 browser_screenshot 获取清洗后的页面结构</span>
    </footer>
  </main>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'

const nativeHostRef = ref(null)
const state = ref({ activeTabId: null, tabs: [] })
const address = ref('')
const activity = ref({ state: 'idle', message: '等待 AI 操作', targetId: null })
const activeTab = computed(() => state.value.tabs.find((tab) => tab.id === state.value.activeTabId) || null)
let removeStateListener = null
let removeActivityListener = null
let resizeObserver = null
let boundsFrame = 0

function syncAddress() {
  address.value = activeTab.value?.url || ''
}

function showBrowserError(prefix, error) {
  activity.value = {
    state: 'failed',
    message: `${prefix}：${error?.message || '未知错误'}`,
    targetId: null
  }
}

function scheduleNativeView() {
  cancelAnimationFrame(boundsFrame)
  boundsFrame = requestAnimationFrame(async () => {
    const host = nativeHostRef.value
    if (!host || !activeTab.value || !window.electronAPI?.aiBrowser) return
    const rect = host.getBoundingClientRect()
    if (rect.width < 1 || rect.height < 1) return
    try {
      await window.electronAPI.aiBrowser.showView(activeTab.value.id, {
        x: rect.x,
        y: rect.y,
        width: rect.width,
        height: rect.height
      })
    } catch (error) {
      console.warn('[ai-browser] failed to show page view:', error)
    }
  })
}

async function applyState(nextState) {
  state.value = {
    activeTabId: nextState?.activeTabId || null,
    tabs: Array.isArray(nextState?.tabs) ? nextState.tabs : []
  }
  syncAddress()
  await nextTick()
  scheduleNativeView()
}

async function newTab(url) {
  try {
    await window.electronAPI.aiBrowser.newTab(url)
    await applyState(await window.electronAPI.aiBrowser.getState())
  } catch (error) {
    console.error('[ai-browser] failed to create tab:', error)
    showBrowserError('新建标签页失败', error)
  }
}

async function activate(tabId) {
  try {
    await window.electronAPI.aiBrowser.activateTab(tabId)
  } catch (error) {
    console.error('[ai-browser] failed to activate tab:', error)
    showBrowserError('切换标签页失败', error)
  }
}

async function closeTab(tabId) {
  try {
    await window.electronAPI.aiBrowser.closeTab(tabId)
  } catch (error) {
    console.error('[ai-browser] failed to close tab:', error)
    showBrowserError('关闭标签页失败', error)
  }
}

async function navigate() {
  if (!activeTab.value || !address.value.trim()) return
  try {
    await window.electronAPI.aiBrowser.navigate(activeTab.value.id, address.value)
  } catch (error) {
    console.error('[ai-browser] navigation failed:', error)
    showBrowserError('页面跳转失败', error)
  }
}

async function history(action) {
  if (!activeTab.value) return
  try {
    await window.electronAPI.aiBrowser.history(activeTab.value.id, action)
  } catch (error) {
    console.error('[ai-browser] history action failed:', error)
    showBrowserError('浏览器操作失败', error)
  }
}

watch(() => activeTab.value?.url, syncAddress)

onMounted(async () => {
  removeStateListener = window.electronAPI.events.listen('ai-browser-state', applyState)
  removeActivityListener = window.electronAPI.events.listen('ai-browser-activity', (nextActivity) => {
    activity.value = nextActivity || { state: 'idle', message: '等待 AI 操作', targetId: null }
  })
  try {
    const initialState = await window.electronAPI.aiBrowser.getState()
    await applyState(initialState)
  } catch (error) {
    showBrowserError('浏览器初始化失败', error)
  }
  resizeObserver = new ResizeObserver(scheduleNativeView)
  if (nativeHostRef.value) resizeObserver.observe(nativeHostRef.value)
})

onBeforeUnmount(() => {
  cancelAnimationFrame(boundsFrame)
  resizeObserver?.disconnect()
  removeStateListener?.()
  removeActivityListener?.()
  window.electronAPI?.aiBrowser?.hideView?.()
})
</script>

<style scoped>
.ai-browser-shell {
  height: 100vh;
  display: grid;
  grid-template-rows: 42px 48px 30px minmax(0, 1fr) 28px;
  color: var(--fg);
  background: var(--bg);
  overflow: hidden;
}

.ai-browser-tabs {
  display: flex;
  align-items: end;
  gap: 3px;
  padding: 7px 10px 0;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
  overflow: hidden;
}

.ai-browser-tab {
  width: min(220px, 23vw);
  height: 34px;
  min-width: 120px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 8px 0 10px;
  border: 1px solid transparent;
  border-bottom: 0;
  border-radius: 7px 7px 0 0;
  background: transparent;
  color: var(--fg-3);
  cursor: pointer;
}

.ai-browser-tab:hover { background: var(--bg-3); }
.ai-browser-tab.active { background: var(--bg); border-color: var(--border); color: var(--fg); }
.ai-browser-tab-title { flex: 1; overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.ai-browser-favicon { width: 16px; height: 16px; display: grid; place-items: center; border-radius: 4px; background: var(--accent-bg); color: var(--accent); font-size: 10px; font-weight: 700; }
.ai-browser-spinner { width: 12px; height: 12px; border: 2px solid var(--border); border-top-color: var(--accent); border-radius: 50%; animation: ai-browser-spin 0.8s linear infinite; }
.ai-browser-tab-close { width: 18px; height: 18px; display: grid; place-items: center; border-radius: 4px; font-size: 16px; line-height: 1; }
.ai-browser-tab-close:hover { background: var(--bg-3); color: var(--fg); }

.ai-browser-icon-button {
  width: 30px;
  height: 30px;
  display: inline-grid;
  place-items: center;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--fg-2);
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
}
.ai-browser-icon-button:hover:not(:disabled) { background: var(--bg-3); color: var(--fg); }
.ai-browser-icon-button:disabled { opacity: 0.35; cursor: default; }
.ai-browser-new-tab { margin: 2px 0 2px 3px; font-size: 21px; }
.ai-browser-icon-button.reload { font-size: 18px; }

.ai-browser-toolbar { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-bottom: 1px solid var(--border); background: var(--bg); }
.ai-browser-nav-actions { display: flex; gap: 1px; }
.ai-browser-address-form { flex: 1; min-width: 0; display: flex; align-items: center; gap: 8px; height: 32px; padding: 0 10px; border: 1px solid var(--border); border-radius: 6px; background: var(--bg-2); }
.ai-browser-address-form:focus-within { border-color: var(--accent); background: var(--bg); }
.ai-browser-address-lock { color: var(--fg-4); font-size: 15px; }
.ai-browser-address { width: 100%; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--fg); font: inherit; font-size: 13px; }
.ai-browser-address::placeholder { color: var(--fg-4); }

.ai-browser-activity { display: flex; align-items: center; gap: 7px; padding: 0 13px; border-bottom: 1px solid var(--border); background: var(--bg-2); color: var(--fg-3); font-size: 12px; overflow: hidden; }
.ai-browser-activity strong { color: var(--fg-2); font-size: 11px; }
.ai-browser-activity-message { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ai-browser-activity code { padding: 1px 5px; border-radius: 4px; background: var(--bg-3); color: var(--fg-3); font-size: 10px; }
.ai-browser-activity-dot { width: 7px; height: 7px; flex-shrink: 0; border-radius: 50%; background: var(--fg-4); }
.ai-browser-activity.running { color: #0f766e; background: rgba(13, 148, 136, 0.08); }
.ai-browser-activity.running .ai-browser-activity-dot { background: #0d9488; box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.16); animation: ai-browser-pulse 1s ease-in-out infinite; }
.ai-browser-activity.completed .ai-browser-activity-dot { background: #16a34a; }
.ai-browser-activity.failed { color: #dc2626; background: rgba(220, 38, 38, 0.07); }
.ai-browser-activity.failed .ai-browser-activity-dot { background: #dc2626; }

.ai-browser-content { position: relative; min-height: 0; background: #fff; }
.ai-browser-empty { height: 100%; display: grid; place-content: center; justify-items: center; gap: 14px; color: var(--fg-3); }
.ai-browser-primary { padding: 7px 12px; border: 0; border-radius: 5px; background: var(--accent); color: #fff; cursor: pointer; font: inherit; font-size: 13px; }
.ai-browser-status { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 0 12px; border-top: 1px solid var(--border); color: var(--fg-4); font-size: 11px; white-space: nowrap; overflow: hidden; }
.ai-browser-status span { overflow: hidden; text-overflow: ellipsis; }

@keyframes ai-browser-spin { to { transform: rotate(360deg); } }
@keyframes ai-browser-pulse { 50% { opacity: 0.45; transform: scale(0.8); } }

@media (max-width: 720px) {
  .ai-browser-tab { width: 150px; min-width: 96px; }
  .ai-browser-status span:last-child { display: none; }
}
</style>
