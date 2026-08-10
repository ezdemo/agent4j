<template>
  <section class="terminal-panel" :class="{ open }">
    <header class="terminal-panel-header">
      <div class="terminal-tabs">
        <button
          v-for="tab in terminals"
          :key="tab.id"
          class="terminal-tab"
          :class="{ active: tab.id === activeId }"
          type="button"
          :title="tab.title"
          @click="activateTerminal(tab.id)"
        >
          <span class="terminal-tab-title">{{ tab.title }}</span>
          <span class="terminal-tab-close" role="button" title="关闭此终端" aria-label="关闭此终端" @click.stop="closeTerminal(tab.id)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18"/></svg>
          </span>
        </button>
        <button class="terminal-tab-add" type="button" title="新建终端" aria-label="新建终端" @click="addTerminal">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14" /></svg>
        </button>
      </div>
      <span v-if="activePid" class="terminal-panel-pid">PID {{ activePid }}</span>
      <button
        type="button"
        class="terminal-panel-close"
        title="收起终端面板"
        aria-label="收起终端面板"
        @click="$emit('close')"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="m6 6 12 12M18 6 6 18"/></svg>
      </button>
    </header>
    <div v-if="unsupported" class="terminal-panel-unsupported">终端仅在桌面端可用</div>
    <div v-else class="terminal-panel-body" :class="terminalThemeClass">
      <div v-if="terminals.length === 0" class="terminal-empty">暂无终端，点击「+」新建</div>
      <div
        v-for="tab in terminals"
        :key="tab.id"
        class="terminal-slot"
        :class="{ active: tab.id === activeId }"
        :ref="(el) => setSlotEl(tab.id, el)"
      />
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'
import { useAppStore } from '../stores/app'

const props = defineProps({
  // 终端初始工作目录（当前工作区路径）；留空则使用主进程默认（用户主目录）
  cwd: { type: String, default: '' },
  // 面板展开状态：首次展开时创建首个终端，收起仅隐藏（所有终端持久保留）
  open: { type: Boolean, default: false },
  // 当前主题（dark / gray）；由宿主页面传入，保证与页面实际主题一致
  theme: { type: String, default: '' }
})
const emit = defineEmits(['close'])

const store = useAppStore()
const unsupported = ref(false)
// 标签列表（响应式，用于渲染标签栏与 PID）；xterm/PTY 实例放在 tabState 避免被 Vue 代理
const terminals = ref([])
const activeId = ref('')
const tabState = new Map()
const slotEls = new Map()
let nextTerminalNo = 1
let dataUnsubscribe = null
let exitUnsubscribe = null
let resizeObserver = null

// xterm 主题：跟随应用主题（dark / gray）
const THEMES = {
  dark: { background: '#1e1e1e', foreground: '#cccccc', cursor: '#aeafad', selectionBackground: '#264f78' },
  gray: { background: '#ffffff', foreground: '#333333', cursor: '#333333', selectionBackground: '#add6ff' }
}

// 主题：优先用宿主页面传入的主题，未传时回退到全局 store（独立使用场景）
const activeTheme = computed(() => props.theme || store.settings.theme)

const currentTheme = () => THEMES[activeTheme.value] || THEMES.gray

// xterm 容器背景跟随主题，保证留白区域与终端底色无缝衔接
const terminalThemeClass = computed(() => (activeTheme.value === 'dark' ? 'theme-dark' : 'theme-gray'))

const activePid = computed(() => {
  const tab = terminals.value.find((item) => item.id === activeId.value)
  return tab?.pid || ''
})

const findTab = (id) => terminals.value.find((item) => item.id === id)

const findTabByPtyId = (ptyId) => {
  for (const [id, state] of tabState) {
    if (state.ptyId === ptyId) return id
  }
  return ''
}

function setSlotEl(tabId, el) {
  if (el) slotEls.set(tabId, el)
  else slotEls.delete(tabId)
}

// 同步单个终端尺寸到主进程 PTY
function fitTab(tabId) {
  const tab = findTab(tabId)
  const state = tabState.get(tabId)
  if (!tab || !state || !state.term || !state.ptyId) return
  try {
    state.fitAddon.fit()
    window.electronAPI.terminal.resize({ id: state.ptyId, cols: state.term.cols, rows: state.term.rows })
  } catch (error) {
    // 容器不可见时 fit 可能失败，等待 ResizeObserver 下次触发
  }
}

// 只对当前激活的终端做尺寸同步（其余标签处于隐藏状态）
function fitActive() {
  if (activeId.value) fitTab(activeId.value)
}

// 新建一个终端标签：创建 xterm + PTY（工作目录 = 当前工作区路径）
async function addTerminal() {
  const terminalAPI = window.electronAPI?.terminal
  if (!terminalAPI || unsupported.value) return

  const tab = { id: `term-${Date.now()}-${nextTerminalNo}`, title: `终端 ${nextTerminalNo++}`, pid: '' }
  terminals.value.push(tab)
  activeId.value = tab.id
  await nextTick()

  const hostEl = slotEls.get(tab.id)
  const term = new Terminal({
    cursorBlink: true,
    fontSize: 13,
    fontFamily: "Consolas, Menlo, 'Courier New', monospace",
    theme: currentTheme(),
    scrollback: 5000
  })
  const fitAddon = new FitAddon()
  term.loadAddon(fitAddon)
  term.open(hostEl)
  const state = { term, fitAddon, ptyId: '' }
  tabState.set(tab.id, state)

  // 键盘输入 → PTY
  term.onData((data) => terminalAPI.input({ id: state.ptyId, data }))

  try {
    const created = await terminalAPI.create({
      cols: term.cols,
      rows: term.rows,
      cwd: props.cwd
    })
    state.ptyId = created.id
    tab.pid = String(created.pid)
    fitTab(tab.id)
  } catch (error) {
    console.warn('[terminal] 创建 PTY 失败:', error)
  }
}

function activateTerminal(id) {
  if (activeId.value === id) return
  activeId.value = id
  // 等 slot 显示后再 fit，确保尺寸正确
  void nextTick(() => fitTab(id))
}

async function closeTerminal(id) {
  const index = terminals.value.findIndex((item) => item.id === id)
  if (index < 0) return
  const state = tabState.get(id)
  if (state?.ptyId && window.electronAPI?.terminal) {
    window.electronAPI.terminal.kill(state.ptyId).catch(() => {})
  }
  state?.term?.dispose()
  tabState.delete(id)
  slotEls.delete(id)
  terminals.value.splice(index, 1)
  // 关闭的是激活标签时，激活相邻标签
  if (activeId.value === id) {
    const next = terminals.value[Math.min(index, terminals.value.length - 1)]
    activeId.value = next ? next.id : ''
  }
}

onMounted(() => {
  const terminalAPI = window.electronAPI?.terminal
  if (!terminalAPI) {
    unsupported.value = true
    return
  }

  // PTY 输出 → 对应标签的 xterm
  dataUnsubscribe = terminalAPI.onData(({ id, data }) => {
    const tabId = findTabByPtyId(id)
    if (tabId) tabState.get(tabId).term.write(data)
  })

  // PTY 退出提示
  exitUnsubscribe = terminalAPI.onExit(({ id, exitCode }) => {
    const tabId = findTabByPtyId(id)
    if (tabId) {
      tabState.get(tabId).term.write(`\r\n\x1b[90m[进程已退出，代码 ${exitCode}]\x1b[0m\r\n`)
    }
  })

  resizeObserver = new ResizeObserver(fitActive)
  resizeObserver.observe(document.querySelector('.terminal-panel-body') || document.body)
})

// 面板首次展开时自动创建首个终端（此时工作区已加载，cwd 有效）；收起/再展开不销毁
watch(
  () => props.open,
  (open) => {
    if (open && terminals.value.length === 0) void addTerminal()
  },
  { immediate: true }
)

onUnmounted(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  // 关闭所有终端（标签会话销毁时清空 PTY）
  for (const state of tabState.values()) {
    if (state.ptyId && window.electronAPI?.terminal) {
      window.electronAPI.terminal.kill(state.ptyId).catch(() => {})
    }
    state.term?.dispose()
  }
  tabState.clear()
  terminals.value = []
  dataUnsubscribe?.()
  exitUnsubscribe?.()
})

// 主题切换时同步所有 xterm 配色
watch(
  activeTheme,
  () => {
    for (const state of tabState.values()) {
      state.term?.setOption('theme', currentTheme())
    }
  }
)
</script>

<style scoped>
.terminal-panel {
  display: flex;
  flex-direction: column;
  height: 0;
  overflow: hidden;
  background: var(--bg, #fff);
  border-top: 1px solid var(--border, #e8e8e8);
  transition: height 0.22s ease;
}
.terminal-panel.open {
  height: 187px;
}
.terminal-panel-header {
  height: 34px;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px 0 8px;
  border-bottom: 1px solid var(--border, #e8e8e8);
  user-select: none;
}
.terminal-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.terminal-tabs::-webkit-scrollbar {
  display: none;
}
.terminal-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 6px 0 10px;
  flex: 0 0 auto;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--fg-2, #5f6368);
  font-size: 12px;
  cursor: pointer;
}
.terminal-tab:hover {
  background: var(--bg-3, #f3f4f6);
  color: var(--fg, #202124);
}
.terminal-tab.active {
  background: var(--bg-3, #f1f2f4);
  color: var(--fg, #202124);
  font-weight: 500;
}
.terminal-tab-title {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 120px;
}
.terminal-tab-close {
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  color: var(--fg-3, #9aa0a6);
}
.terminal-tab-close:hover {
  background: rgba(0, 0, 0, 0.08);
  color: var(--fg, #202124);
}
.terminal-tab-close svg {
  width: 10px;
  height: 10px;
}
.terminal-tab-add {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--fg-2, #5f6368);
  cursor: pointer;
}
.terminal-tab-add:hover {
  background: var(--bg-3, #f3f4f6);
  color: var(--fg, #202124);
}
.terminal-tab-add svg {
  width: 13px;
  height: 13px;
}
.terminal-panel-pid {
  font-size: 11px;
  color: var(--fg-4, #9ca3af);
  flex: 0 0 auto;
}
.terminal-panel-close {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--fg-2, #5f6368);
  cursor: pointer;
}
.terminal-panel-close:hover {
  background: var(--bg-3, #f3f4f6);
  color: var(--fg, #202124);
}
.terminal-panel-close svg {
  width: 14px;
  height: 14px;
}
.terminal-panel-body {
  flex: 1;
  min-height: 0;
  background: #1e1e1e;
  position: relative;
}
.terminal-panel-body.theme-gray {
  background: #ffffff;
}
/* 每个终端标签独立容器：仅激活的显示 */
.terminal-slot {
  display: none;
  position: absolute;
  inset: 0;
}
.terminal-slot.active {
  display: block;
}
/* 留白：padding 加在 .xterm 上，FitAddon 会自动扣除（行列数精确）
   .xterm-viewport 绝对定位铺满整个 .xterm（含 padding 区），默认背景 #000，
   必须显式跟随主题，否则留白区域会显示黑色 */
.terminal-panel-body :deep(.xterm) {
  padding: 8px 12px;
  background: #1e1e1e;
}
.terminal-panel-body :deep(.xterm .xterm-viewport) {
  background-color: #1e1e1e;
}
.terminal-panel-body.theme-gray :deep(.xterm) {
  background: #ffffff;
}
.terminal-panel-body.theme-gray :deep(.xterm .xterm-viewport) {
  background-color: #ffffff;
}
.terminal-empty {
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--fg-4, #9ca3af);
  font-size: 13px;
}
.terminal-panel-unsupported {
  flex: 1;
  display: grid;
  place-items: center;
  color: var(--fg-4, #9ca3af);
  font-size: 13px;
}
</style>
