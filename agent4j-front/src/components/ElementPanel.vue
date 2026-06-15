<template>
  <div class="elem-panel">
    <!-- URL 输入栏 -->
    <div class="elem-toolbar">
      <div class="elem-url-bar">
        <input
          v-model="url"
          class="elem-url-input"
          placeholder="输入网址，例如 http://localhost:5173"
          @keyup.enter="navigate"
        />
        <button class="elem-go-btn" @click="navigate" :disabled="!url.trim()" title="加载">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
        </button>
      </div>

      <!-- 设计模式切换 -->
      <div class="elem-design-toggle">
        <button
          class="design-btn"
          :class="{ active: designMode }"
          @click="toggleDesignMode"
          :disabled="!loaded"
        >
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 20h9"/>
            <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
          </svg>
          {{ designMode ? '退出设计模式' : '设计模式' }}
        </button>
      </div>
    </div>

    <!-- 已选中的组件信息 -->
    <div v-if="selectedComponent" class="elem-component-info">
      <div class="elem-comp-header">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
        <span class="elem-comp-name">{{ selectedComponent.name }}</span>
        <button class="elem-comp-close" @click="selectedComponent = null">×</button>
      </div>
      <div class="elem-comp-section">
        <div class="elem-comp-label">标签</div>
        <code class="elem-comp-value">{{ selectedComponent.tag }}</code>
      </div>
      <div v-if="selectedComponent.props && Object.keys(selectedComponent.props).length" class="elem-comp-section">
        <div class="elem-comp-label">Props</div>
        <div class="elem-comp-props">
          <div v-for="(val, key) in selectedComponent.props" :key="key" class="elem-comp-prop">
            <span class="prop-key">{{ key }}</span>
            <span class="prop-val">{{ formatValue(val) }}</span>
          </div>
        </div>
      </div>
      <div v-if="selectedComponent.slots" class="elem-comp-section">
        <div class="elem-comp-label">Slots</div>
        <code class="elem-comp-value">{{ Object.keys(selectedComponent.slots).join(', ') || '无' }}</code>
      </div>
      <div v-if="selectedComponent.file" class="elem-comp-section">
        <div class="elem-comp-label">文件</div>
        <code class="elem-comp-value file-path" :title="selectedComponent.file">{{ selectedComponent.file }}</code>
      </div>
    </div>

    <!-- 设计模式提示 -->
    <div v-if="designMode && !selectedComponent" class="elem-hint">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path d="M12 20h9"/>
        <path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/>
      </svg>
      <span>点击页面中的任意元素以查看其 Vue 组件</span>
    </div>

    <!-- iframe 容器 -->
    <div class="elem-frame-wrap" :class="{ 'design-active': designMode }">
      <iframe
        v-if="currentSrc"
        ref="frameRef"
        :src="currentSrc"
        class="elem-frame"
        @load="onFrameLoad"
        sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
      ></iframe>
      <div v-else class="elem-placeholder">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2">
          <rect x="2" y="3" width="20" height="14" rx="2" ry="2"/>
          <line x1="8" y1="21" x2="16" y2="21"/>
          <line x1="12" y1="17" x2="12" y2="21"/>
        </svg>
        <p>输入网址后点击加载</p>
        <span class="hint">页面将在右侧面板中显示</span>
      </div>
      <!-- 跨域提示 -->
      <div v-if="crossOrigin" class="elem-cross-overlay">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"/>
          <line x1="15" y1="9" x2="9" y2="15"/>
          <line x1="9" y1="9" x2="15" y2="15"/>
        </svg>
        <p>跨域限制：无法直接访问该页面 DOM</p>
        <span class="hint">请使用 Chrome DevTools + Vue Devtools 或在本地开发环境中使用</span>
      </div>
    </div>

    <!-- 底部状态 -->
    <div class="elem-status">
      <span v-if="!loaded" class="elem-status-text">就绪</span>
      <span v-else class="elem-status-text" :class="{ inspecting: designMode }">
        {{ designMode ? '🖱 点击元素查看组件' : '✓ 页面已加载' }}
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from 'vue'

const props = defineProps({
  workspaceHash: { type: String, default: null },
  sessionName: { type: String, default: '' },
  sessions: { type: Array, default: () => [] }
})

const url = ref('')
const currentSrc = ref('')
const loaded = ref(false)
const designMode = ref(false)
const selectedComponent = ref(null)
const crossOrigin = ref(false)
const frameRef = ref(null)

// 规范化 URL
function normalizeUrl(input) {
  let str = input.trim()
  if (!str) return ''
  if (!/^https?:\/\//i.test(str)) {
    str = 'http://' + str
  }
  try {
    const u = new URL(str)
    return u.href
  } catch {
    return ''
  }
}

function navigate() {
  const normalized = normalizeUrl(url.value)
  if (!normalized) return
  currentSrc.value = normalized
  loaded.value = false
  crossOrigin.value = false
  selectedComponent.value = null
  designMode.value = false
}

function toggleDesignMode() {
  if (!loaded.value) return
  designMode.value = !designMode.value
  selectedComponent.value = null
  if (designMode.value) {
    injectInspector()
  } else {
    removeInspector()
  }
}

// 向 iframe 注入检测脚本（仅同源有效）
function injectInspector() {
  const frame = frameRef.value
  if (!frame || !frame.contentDocument) {
    crossOrigin.value = true
    designMode.value = false
    return
  }
  try {
    const doc = frame.contentDocument
    // 注入样式
    const style = doc.createElement('style')
    style.id = '__agent4j_elem_style'
    style.textContent = `
      *.__agent4j-highlight {
        outline: 2px dashed #2563eb !important;
        outline-offset: 2px !important;
        background: rgba(37, 99, 235, 0.08) !important;
        cursor: crosshair !important;
      }
    `
    doc.head.appendChild(style)

    // 用事件捕获监听点击
    doc.addEventListener('click', onFrameClick, true)
    // 悬停高亮
    doc.addEventListener('mouseover', onFrameHover, true)
    doc.addEventListener('mouseout', onFrameOut, true)
  } catch (e) {
    crossOrigin.value = true
    designMode.value = false
  }
}

function removeInspector() {
  const frame = frameRef.value
  if (!frame) return
  try {
    const doc = frame.contentDocument
    if (doc) {
      doc.removeEventListener('click', onFrameClick, true)
      doc.removeEventListener('mouseover', onFrameHover, true)
      doc.removeEventListener('mouseout', onFrameOut, true)
      const style = doc.getElementById('__agent4j_elem_style')
      if (style) style.remove()
      // 清除所有高亮
      doc.querySelectorAll('.__agent4j-highlight').forEach(el => {
        el.classList.remove('__agent4j-highlight')
      })
    }
  } catch (e) {
    // 忽略跨域错误
  }
}

function onFrameHover(e) {
  if (!designMode.value) return
  // 清除其他高亮
  const frame = frameRef.value
  if (!frame) return
  try {
    const doc = frame.contentDocument
    doc.querySelectorAll('.__agent4j-highlight').forEach(el => {
      el.classList.remove('__agent4j-highlight')
    })
    e.target.classList.add('__agent4j-highlight')
  } catch {}
}

function onFrameOut(e) {
  if (!designMode.value) return
  try {
    e.target.classList.remove('__agent4j-highlight')
  } catch {}
}

function onFrameClick(e) {
  if (!designMode.value) return
  e.preventDefault()
  e.stopPropagation()

  const target = e.target
  const frame = frameRef.value
  if (!frame) return

  try {
    const component = findVueComponent(target)
    if (component) {
      selectedComponent.value = component
    }
  } catch (err) {
    console.warn('[ElementPanel] 分析组件失败:', err)
  }
}

// 从 DOM 元素反向查找 Vue 组件
function findVueComponent(el) {
  let current = el
  let attempts = 0
  while (current && current !== document.body && current !== document.documentElement && attempts < 20) {
    // Vue 3: __vueParentComponent
    const vn = current.__vueParentComponent
    if (vn) {
      const type = vn.type
      const name = typeof type === 'object' ? (type.name || type.__name || type.displayName || 'Anonymous') : type
      const props = vn.props ? { ...vn.props } : {}
      // 过滤 Vue 内部属性
      Object.keys(props).forEach(k => {
        if (k.startsWith('on') || k.startsWith('$')) delete props[k]
      })
      // 序列化值
      const serializedProps = {}
      for (const [k, v] of Object.entries(props)) {
        serializedProps[k] = typeof v === 'function' ? 'ƒ()' : v
      }
      return {
        name: name,
        tag: current.tagName ? current.tagName.toLowerCase() : '?',
        props: serializedProps,
        slots: vn.slots ? { ...vn.slots } : {},
        file: type.__file || type.__source || ''
      }
    }
    // 尝试 vnode
    const vnode = current.__vnode
    if (vnode && vnode.component) {
      const comp = vnode.component
      const type = comp.type
      const name = typeof type === 'object' ? (type.name || type.__name || type.displayName || 'Anonymous') : type
      const props = comp.props || {}
      const serializedProps = {}
      for (const [k, v] of Object.entries(props)) {
        if (k.startsWith('on')) continue
        serializedProps[k] = typeof v === 'function' ? 'ƒ()' : v
      }
      return {
        name: name,
        tag: current.tagName ? current.tagName.toLowerCase() : '?',
        props: serializedProps,
        slots: comp.slots ? { ...comp.slots } : {},
        file: type.__file || type.__source || ''
      }
    }
    current = current.parentElement
    attempts++
  }
  return {
    name: '未知元素',
    tag: el.tagName ? el.tagName.toLowerCase() : '?',
    props: {},
    slots: {},
    file: ''
  }
}

function formatValue(val) {
  if (val === null || val === undefined) return '—'
  if (typeof val === 'boolean') return val ? 'true' : 'false'
  if (typeof val === 'number' || typeof val === 'string') return String(val)
  if (Array.isArray(val)) return `Array(${val.length})`
  if (typeof val === 'object') return `{${Object.keys(val).join(', ')}}`
  return String(val)
}

function onFrameLoad() {
  loaded.value = true
  crossOrigin.value = false
}

function onFrameError() {
  loaded.value = false
}

onBeforeUnmount(() => {
  removeInspector()
})
</script>

<style scoped>
.elem-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  font-size: 12px;
  color: var(--fg);
}

/* ── 工具栏 ── */
.elem-toolbar {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 8px;
  border-bottom: 1px solid var(--glass-border);
}

.elem-url-bar {
  display: flex;
  gap: 4px;
  align-items: center;
}

.elem-url-input {
  flex: 1;
  height: 28px;
  padding: 0 8px;
  font-size: 12px;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--bg);
  color: var(--fg);
  outline: none;
  transition: border-color 0.15s;
}

.elem-url-input:focus {
  border-color: var(--accent);
}

.elem-go-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--accent);
  color: #fff;
  cursor: pointer;
  transition: all 0.15s;
}

.elem-go-btn:hover:not(:disabled) {
  background: var(--accent-dark, #1d4ed8);
}

.elem-go-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.elem-design-toggle {
  display: flex;
}

.design-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 10px;
  font-size: 11px;
  font-weight: 500;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--bg);
  color: var(--fg-3);
  cursor: pointer;
  transition: all 0.15s;
  width: 100%;
  justify-content: center;
}

.design-btn:hover:not(:disabled) {
  background: var(--bg-3);
  color: var(--fg);
}

.design-btn.active {
  background: #2563eb;
  color: #fff;
  border-color: #2563eb;
}

.design-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ── 组件信息 ── */
.elem-component-info {
  margin: 6px 8px;
  border: 1px solid var(--accent);
  border-radius: var(--r);
  background: var(--accent-bg);
  overflow: hidden;
}

.elem-comp-header {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 8px;
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  border-bottom: 1px solid rgba(37, 99, 235, 0.15);
}

.elem-comp-name {
  flex: 1;
}

.elem-comp-close {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  border: none;
  background: none;
  color: var(--fg-4);
  cursor: pointer;
  border-radius: 3px;
}

.elem-comp-close:hover {
  background: rgba(0,0,0,0.08);
  color: var(--fg);
}

.elem-comp-section {
  padding: 4px 8px;
  border-bottom: 1px solid rgba(37, 99, 235, 0.08);
}

.elem-comp-section:last-child {
  border-bottom: none;
}

.elem-comp-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--fg-4);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 2px;
}

.elem-comp-value {
  font-family: var(--mono);
  font-size: 11px;
  color: var(--fg-2);
  word-break: break-all;
}

.file-path {
  font-size: 10px;
}

.elem-comp-props {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.elem-comp-prop {
  display: flex;
  gap: 6px;
  font-size: 11px;
  font-family: var(--mono);
}

.prop-key {
  color: var(--accent);
  font-weight: 500;
  white-space: nowrap;
}

.prop-val {
  color: var(--fg-2);
  word-break: break-all;
}

/* ── 提示 ── */
.elem-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  font-size: 11px;
  color: var(--fg-3);
  background: rgba(37, 99, 235, 0.06);
  border-bottom: 1px solid var(--glass-border);
}

/* ── iframe 容器 ── */
.elem-frame-wrap {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: #fff;
}

.elem-frame-wrap.design-active {
  cursor: crosshair;
}

.elem-frame {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}

.elem-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
  color: var(--fg-4);
  text-align: center;
  padding: 20px;
}

.elem-placeholder p {
  margin: 0;
  font-size: 13px;
  color: var(--fg-3);
}

.hint {
  font-size: 11px;
  color: var(--fg-4);
}

/* 跨域遮罩 */
.elem-cross-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(255,255,255,0.92);
  backdrop-filter: blur(4px);
  color: var(--fg-4);
  text-align: center;
  padding: 20px;
  z-index: 10;
}

.elem-cross-overlay p {
  margin: 0;
  font-size: 13px;
  color: var(--fg-2);
  font-weight: 500;
}

/* ── 底部状态 ── */
.elem-status {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  border-top: 1px solid var(--glass-border);
  min-height: 24px;
}

.elem-status-text {
  font-size: 11px;
  color: var(--fg-4);
}

.elem-status-text.inspecting {
  color: var(--accent);
  font-weight: 500;
}
</style>
