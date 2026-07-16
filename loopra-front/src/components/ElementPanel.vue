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
        @error="onFrameError"
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

  <!-- 组件信息弹窗 -->
  <Teleport to="body">
    <div v-if="selectedComponent" class="elem-modal-mask" @click.self="closeModal" @keydown.escape="closeModal">
      <div class="elem-modal">
        <div class="elem-modal-head">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
          <span class="elem-modal-title">{{ selectedComponent.name }}</span>
          <button class="elem-modal-close" @click="closeModal">×</button>
        </div>
        <div class="elem-modal-body">
          <!-- 组件路径 -->
          <div v-if="selectedComponent.path" class="elem-modal-section">
            <div class="elem-modal-label">组件路径</div>
            <div class="elem-comp-path">
              <span
                v-for="(seg, i) in selectedComponent.path"
                :key="i"
                class="path-seg"
                :class="{ active: i === selectedComponent.path.length - 1 }"
              >{{ seg }}<span v-if="i < selectedComponent.path.length - 1" class="path-arrow">›</span></span>
            </div>
          </div>

          <!-- 文件 -->
          <div v-if="selectedComponent.file" class="elem-modal-section">
            <div class="elem-modal-label">文件路径</div>
            <code class="elem-modal-value file-path" :title="selectedComponent.file">{{ selectedComponent.file }}</code>
          </div>
        </div>
        <div class="elem-modal-foot">
          <div class="elem-modal-input-row">
            <textarea
              ref="msgInputRef"
              v-model="userMessage"
              class="elem-modal-input"
              placeholder="输入你的需求..."
              rows="2"
              @keydown.enter.prevent="handleSend"
            ></textarea>
            <button class="elem-modal-send" @click="handleSend" :disabled="!userMessage.trim()" title="发送">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <line x1="22" y1="2" x2="11" y2="13"/>
                <polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'

const props = defineProps({
  workspaceHash: { type: String, default: null },
  sessionName: { type: String, default: '' },
  sessions: { type: Array, default: () => [] }
})

const emit = defineEmits(['send'])

const url = ref('')
const currentSrc = ref('')
const loaded = ref(false)
const designMode = ref(false)
const selectedComponent = ref(null)
const crossOrigin = ref(false)
const frameRef = ref(null)
const userMessage = ref('')
const msgInputRef = ref(null)

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

let loadedTimer = null

// 暴露给父组件的方法：外部导航到指定 URL
function loadUrl(newUrl) {
  url.value = newUrl
  navigate()
}

defineExpose({ loadUrl })

function navigate() {
  const normalized = normalizeUrl(url.value)
  if (!normalized) return
  currentSrc.value = normalized
  loaded.value = false
  crossOrigin.value = false
  selectedComponent.value = null
  designMode.value = false
  // 兜底：3秒后无论 iframe 是否触发 load，都启用按钮
  clearTimeout(loadedTimer)
  if (!loaded.value) {
    loadedTimer = setTimeout(() => {
      console.log('[ElementPanel] 响应超时，强制启用')
      loaded.value = true
    }, 3000)
  }
}

async function toggleDesignMode() {
  if (!loaded.value) return
  designMode.value = !designMode.value
  selectedComponent.value = null
  console.log('[ElementPanel] 切换设计模式:', designMode.value)
  if (designMode.value) {
    await injectInspector()
  } else {
    await removeInspector()
  }
}

// 向 iframe 注入检测脚本
// Electron 模式：通过 IPC 由主进程注入，突破跨域限制
// 普通模式：直接访问 contentDocument（仅同源有效）
async function injectInspector() {
  const frame = frameRef.value
  if (!frame) {
    console.warn('[ElementPanel] iframe 未就绪，无法注入')
    crossOrigin.value = true
    designMode.value = false
    return
  }

  // ---- Electron 模式：通过主进程注入（无视跨域） ----
  if (window.electronAPI?.inspector) {
    const result = await window.electronAPI.inspector.inject()
    if (result.success) {
      console.log('[ElementPanel] Electron 模式：检测脚本已注入')
      return  // designMode 保持 true
    }
    console.warn('[ElementPanel] Electron 注入失败:', result.reason)
    // 降级到普通模式
  }

  // ---- 普通模式：直接 DOM 访问（仅同源） ----
  if (!frame.contentDocument) {
    console.warn('[ElementPanel] iframe 跨域，无法注入')
    crossOrigin.value = true
    designMode.value = false
    return
  }
  try {
    const doc = frame.contentDocument
    if (!doc.head) {
      console.warn('[ElementPanel] iframe head 不可用')
      crossOrigin.value = true
      designMode.value = false
      return
    }
    // 注入样式
    const style = doc.createElement('style')
    style.id = '__loopra_elem_style'
    style.textContent = `
      *.__loopra-highlight {
        outline: 2px dashed #2563eb !important;
        outline-offset: 2px !important;
        background: rgba(37, 99, 235, 0.08) !important;
        cursor: crosshair !important;
      }
    `
    doc.head.appendChild(style)

    // 用事件捕获监听点击
    doc.addEventListener('click', onFrameClick, true)
    // 悬停高亮（使用 capture phase 确保优先执行）
    doc.addEventListener('mouseover', onFrameHover, true)
    doc.addEventListener('mouseout', onFrameOut, true)
    console.log('[ElementPanel] 设计模式已激活，检测脚本已注入')
  } catch (e) {
    console.warn('[ElementPanel] 注入失败（跨域限制）:', e.message)
    crossOrigin.value = true
    designMode.value = false
  }
}

async function removeInspector() {
  const frame = frameRef.value
  if (!frame) return

  // ---- Electron 模式：通过主进程移除 ----
  if (window.electronAPI?.inspector) {
    try {
      const result = await window.electronAPI.inspector.remove()
      if (result.success) {
        console.log('[ElementPanel] Electron 模式：检测脚本已移除')
        return
      }
      console.warn('[ElementPanel] Electron 移除失败:', result.reason)
      // 降级到普通模式
    } catch (e) {
      console.warn('[ElementPanel] Electron 移除异常:', e.message)
      // 降级到普通模式
    }
  }

  // ---- 普通模式 ----
  try {
    const doc = frame.contentDocument
    if (doc) {
      doc.removeEventListener('click', onFrameClick, true)
      doc.removeEventListener('mouseover', onFrameHover, true)
      doc.removeEventListener('mouseout', onFrameOut, true)
      const style = doc.getElementById('__loopra_elem_style')
      if (style) style.remove()
      // 清除所有高亮
      doc.querySelectorAll('.__loopra-highlight').forEach(el => {
        el.classList.remove('__loopra-highlight')
      })
      console.log('[ElementPanel] 普通模式：检测脚本已移除')
    }
  } catch (e) {
    console.warn('[ElementPanel] 移除检测脚本失败（可能跨域）:', e.message)
  }
}

function onFrameHover(e) {
  if (!designMode.value) return
  const doc = e.target.ownerDocument
  if (!doc) return
  try {
    doc.querySelectorAll('.__loopra-highlight').forEach(el => {
      el.classList.remove('__loopra-highlight')
    })
    e.target.classList.add('__loopra-highlight')
  } catch {}
}

function onFrameOut(e) {
  if (!designMode.value) return
  try {
    e.target.classList.remove('__loopra-highlight')
  } catch {}
}

function onFrameClick(e) {
  if (!designMode.value) return
  // 阻止事件冒泡以防页面内链接被点击跳转
  e.stopPropagation()

  const target = e.target
  if (!target) return

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
  const compPath = []  // 组件层级路径
  const doc = el.ownerDocument  // 使用元素自身的 document，兼容 iframe
  const root = doc ? (doc.body || doc.documentElement) : null

  while (current && current !== root && attempts < 30) {
    const result = tryExtractComponent(current)
    if (result) {
      compPath.unshift(result.name)
      // 如果找到真实组件（非 Anonymous），收集信息
      if (result.name !== 'Anonymous' && result.name !== 'Transition' && result.name !== 'KeepAlive' && !result.name.startsWith('V')) {
        const children = extractChildComponents(result.type)

        // 继续向上查找更外层的组件路径
        let parent = current.parentElement
        while (parent && parent !== root) {
          const pr = tryExtractComponent(parent)
          if (pr && pr.name !== 'Anonymous' && pr.name !== 'Transition' && pr.name !== 'KeepAlive' && !pr.name.startsWith('V')) {
            compPath.unshift(pr.name)
          }
          parent = parent.parentElement
        }

        return {
          name: result.name,
          tag: el.tagName ? el.tagName.toLowerCase() : '?',
          text: extractElementText(el),
          selector: extractElementSelector(el),
          attrs: extractElementAttrs(el),
          path: [...new Set(compPath)],  // 去重
          props: result.props,
          slots: result.slots,
          file: result.file,
          children: children
        }
      }
    }
    current = current.parentElement
    attempts++
  }

  return {
    name: '原生元素（无 Vue 组件包裹）',
    tag: el.tagName ? el.tagName.toLowerCase() : '?',
    text: extractElementText(el),
    selector: extractElementSelector(el),
    attrs: extractElementAttrs(el),
    path: [],
    props: {},
    slots: {},
    file: '',
    children: []
  }
}

// 尝试从一个 DOM 节点提取 Vue 组件信息
function tryExtractComponent(el) {
  // Vue 3: __vueParentComponent
  const vn = el.__vueParentComponent
  if (vn) {
    const type = vn.type
    const name = typeof type === 'object' ? (type.name || type.__name || type.displayName || 'Anonymous') : type
    const props = vn.props ? { ...vn.props } : {}
    Object.keys(props).forEach(k => {
      if (k.startsWith('on') || k.startsWith('$')) delete props[k]
    })
    const serializedProps = {}
    for (const [k, v] of Object.entries(props)) {
      serializedProps[k] = typeof v === 'function' ? 'ƒ()' : v
    }
    return {
      name: name,
      type: type,
      props: serializedProps,
      slots: vn.slots ? { ...vn.slots } : {},
      file: type.__file || type.__source || ''
    }
  }
  // 尝试 vnode
  const vnode = el.__vnode
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
      type: type,
      props: serializedProps,
      slots: comp.slots ? { ...comp.slots } : {},
      file: type.__file || type.__source || ''
    }
  }
  return null
}

// 提取元素的文本内容（用于定位）
function extractElementText(el) {
  if (!el) return ''
  let text = (el.textContent || '').trim()
  // 只取前 60 个字符
  if (text.length > 60) {
    text = text.substring(0, 60) + '…'
  }
  return text
}

// 生成从根到元素的 CSS 选择器路径
function extractElementSelector(el) {
  const doc = el.ownerDocument
  const root = doc ? (doc.body || doc.documentElement) : null
  if (!el || !root || el === root) return ''
  const parts = []
  let current = el
  let maxDepth = 10
  while (current && current !== root && maxDepth > 0) {
    let seg = current.tagName.toLowerCase()
    if (current.id) {
      seg = '#' + current.id
      parts.unshift(seg)
      break  // 有 id 就可以停了，全局唯一
    }
    // 类名取前两个最有区分度的
    if (current.className && typeof current.className === 'string') {
      const classes = current.className.trim().split(/\s+/).filter(c => c && !c.startsWith('__loopra') && c !== 'active' && c !== 'selected' && c !== 'hover')
      if (classes.length) {
        seg += '.' + classes.slice(0, 2).join('.')
      }
    }
    // nth-child
    const parent = current.parentElement
    if (parent) {
      const siblings = Array.from(parent.children).filter(s => s.tagName === current.tagName)
      if (siblings.length > 1) {
        const idx = siblings.indexOf(current) + 1
        seg += `:nth-child(${idx})`
      }
    }
    parts.unshift(seg)
    current = current.parentElement
    maxDepth--
  }
  return parts.join(' > ')
}

// 提取元素的关键属性（用于定位）
function extractElementAttrs(el) {
  if (!el || !el.attributes) return []
  const keepKeys = ['id', 'class', 'type', 'name', 'value', 'placeholder', 'href', 'src', 'alt', 'title', 'role', 'for', 'data-v-', 'aria-']
  const result = []
  for (let i = 0; i < el.attributes.length; i++) {
    const attr = el.attributes[i]
    const name = attr.name
    const val = attr.value
    if (!val && val !== '') continue
    if (name === 'style' || name === 'class') continue  // class 已显示在选择器中
    if (val.length > 50) continue  // 太长的值跳过
    if (keepKeys.some(k => name === k || name.startsWith(k))) {
      result.push({ key: name, val: val.length > 40 ? val.substring(0, 40) + '…' : val })
    }
  }
  return result
}

// 提取注册的子组件列表
function extractChildComponents(type) {
  if (!type || !type.components) return []
  try {
    return Object.keys(type.components).filter(k => k !== 'Fragment' && k !== 'Teleport' && k !== 'Suspense')
  } catch {
    return []
  }
}

function onFrameLoad() {
  console.log('[ElementPanel] iframe 加载完成')
  clearTimeout(loadedTimer)
  loaded.value = true
  crossOrigin.value = false
}

function onFrameError() {
  console.warn('[ElementPanel] iframe 加载失败')
  clearTimeout(loadedTimer)
  // 加载失败也启用按钮，让用户能重试
  loaded.value = true
}

function closeModal() {
  selectedComponent.value = null
  userMessage.value = ''
}

function handleSend() {
  const msg = userMessage.value.trim()
  if (!msg || !selectedComponent.value) return
  emit('send', {
    message: msg,
    component: {
      name: selectedComponent.value.name,
      tag: selectedComponent.value.tag,
      text: selectedComponent.value.text,
      selector: selectedComponent.value.selector,
      attrs: selectedComponent.value.attrs,
      file: selectedComponent.value.file,
      path: selectedComponent.value.path
    }
  })
  closeModal()
}

function onKeydown(e) {
  if (e.key === 'Escape' && selectedComponent.value) {
    closeModal()
  }
}

/**
 * 接收 Electron 模式下从 iframe 通过 postMessage 传来的元素点击信息
 */
function onFrameMessage(e) {
  if (!e.data || e.data.type !== 'loopra-element-click') return
  if (!designMode.value) return  // 非设计模式下忽略
  const data = e.data
  console.log('[ElementPanel] 收到元素点击数据:', data.tag)

  selectedComponent.value = {
    name: data.vueComponent?.name || '原生元素（无 Vue 组件包裹）',
    tag: data.tag || '?',
    text: data.text || '',
    selector: data.selector || '',
    attrs: data.attrs || [],
    file: data.vueComponent?.file || '',
    path: data.path || [],
    children: data.children || []
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('message', onFrameMessage)
})

// 弹窗打开时自动聚焦输入框
watch(selectedComponent, (val) => {
  if (val) {
    nextTick(() => {
      msgInputRef.value?.focus()
    })
  }
})

onBeforeUnmount(() => {
  removeInspector()
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('message', onFrameMessage)
  clearTimeout(loadedTimer)
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

/* ── 组件路径 ── */
.elem-comp-path {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  align-items: center;
  font-family: var(--mono);
  font-size: 11px;
}
.path-seg {
  color: var(--fg-3);
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.path-seg.active {
  color: var(--accent);
  font-weight: 600;
}
.path-arrow {
  margin: 0 2px;
  color: var(--fg-4);
  font-size: 13px;
}

/* ── 元素文本 ── */
.elem-comp-el-text {
  margin-top: 3px;
  padding: 2px 6px;
  font-size: 10px;
  font-family: var(--mono);
  color: var(--fg-3);
  background: var(--bg);
  border: 1px dashed var(--border);
  border-radius: var(--r-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── CSS 选择器 ── */
.elem-selector {
  font-size: 10px;
  word-break: break-all;
  line-height: 1.6;
  color: #8b5cf6 !important;  /* 紫色突出 */
  cursor: pointer;
}
.elem-selector:hover {
  text-decoration: underline;
}

/* ── 元素属性 ── */
.elem-comp-attrs {
  display: flex;
  flex-wrap: wrap;
  gap: 3px 6px;
}
.elem-comp-attr {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-family: var(--mono);
  font-size: 10px;
  padding: 1px 5px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
}
.attr-key {
  color: #d63384;
  font-weight: 500;
}
.attr-eq {
  color: var(--fg-4);
}
.attr-val {
  color: #0d6efd;
}

/* ── 子组件 ── */
.elem-comp-children {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.child-chip {
  display: inline-block;
  padding: 1px 6px;
  font-size: 10px;
  font-family: var(--mono);
  background: var(--bg-3);
  border: 1px solid var(--border);
  border-radius: var(--r-sm);
  color: var(--fg-2);
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

/* ── 弹窗遮罩 ── */
.elem-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 10vh;
  background: rgba(0,0,0,0.35);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
}

.elem-modal {
  width: 480px;
  max-width: 90vw;
  max-height: 75vh;
  display: flex;
  flex-direction: column;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: var(--shadow-xl);
  overflow: hidden;
  animation: elem-modal-in 0.15s ease-out;
}

@keyframes elem-modal-in {
  from {
    opacity: 0;
    transform: translateY(-16px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.elem-modal-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: var(--fg);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.elem-modal-head svg {
  color: var(--accent);
  flex-shrink: 0;
}

.elem-modal-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.elem-modal-close {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  border: none;
  background: none;
  color: var(--fg-4);
  cursor: pointer;
  border-radius: var(--r);
  transition: all 0.12s;
}

.elem-modal-close:hover {
  background: var(--bg-3);
  color: var(--fg);
}

.elem-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
}

.elem-modal-section {
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
}

.elem-modal-section:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.elem-modal-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--fg-4);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

.elem-modal-value {
  font-family: var(--mono);
  font-size: 12px;
  color: var(--fg-2);
  word-break: break-all;
}

.elem-modal-foot {
  display: flex;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
  flex-shrink: 0;
}

.elem-modal-input-row {
  display: flex;
  gap: 6px;
  width: 100%;
  align-items: flex-end;
}

.elem-modal-input {
  flex: 1;
  padding: 6px 10px;
  font-size: 13px;
  border: 1px solid var(--border);
  border-radius: var(--r);
  background: var(--bg-2);
  color: var(--fg);
  outline: none;
  transition: border-color 0.15s;
  font-family: inherit;
  resize: vertical;
  min-height: 32px;
  max-height: 120px;
  line-height: 1.5;
}

.elem-modal-input:focus {
  border-color: var(--accent);
  background: var(--bg);
}

.elem-modal-input::placeholder {
  color: var(--fg-4);
}

.elem-modal-send {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--r);
  background: var(--accent);
  color: #fff;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.elem-modal-send:hover:not(:disabled) {
  background: var(--accent-dark, #1d4ed8);
}

.elem-modal-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
