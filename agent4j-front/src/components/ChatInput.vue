<template>
  <div class="input-area">
    <!-- 斜杠命令弹窗 -->
    <Transition name="slash-popup">
      <div v-if="slashPopupOpen" class="slash-popup">
        <div class="slash-popup-header">
          <span class="slash-popup-title">可用命令</span>
          <span class="slash-popup-hint">输入 / 触发</span>
        </div>
        <div v-if="!commandsLoaded" class="slash-popup-loading">
          <span class="loading-dot"></span> 加载命令中...
        </div>
        <div v-else-if="filteredSlashCmds.length === 0" class="slash-popup-empty">无匹配命令</div>
        <div v-else class="slash-popup-list">
          <div v-for="(cmd, index) in filteredSlashCmds" :key="cmd.cmd"
               class="slash-popup-item" :class="{ active: index === activePopupIdx }"
               @click="selectSlashCmd(cmd)" @mouseenter="activePopupIdx = index">
            <div class="slash-popup-icon">{{ getSlashIcon(cmd.cmd) }}</div>
            <div class="slash-popup-info">
              <div class="slash-popup-cmd">
                {{ cmd.cmd }}
                <span v-if="cmd.type === 'skill'" class="slash-popup-badge skill">skill</span>
                <span v-else-if="cmd.type === 'mode'" class="slash-popup-badge mode">模式</span>
                <span v-else-if="cmd.type === 'session'" class="slash-popup-badge session">会话</span>
              </div>
              <div class="slash-popup-desc">{{ cmd.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <div class="input-box" :class="{ focused: inputFocused }">
      <!-- 已选技能标签 -->
      <div v-if="selectedSkills.length > 0" class="skill-chips-bar">
        <span v-for="s in selectedSkills" :key="s.name" class="skill-chip">
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
          </svg>
          {{ s.name }}
          <button class="skill-chip-remove" @click.stop="removeSkill(s)">&times;</button>
        </span>
      </div>

      <div class="input-row">
        <!-- 工作流 TODO 按钮 -->
        <div class="wf-todo-wrap" @mouseenter="onWfEnter" @mouseleave="onWfLeave">
          <button class="todo-btn" :class="{ has: !!wfData, active: !!wfData }" title="工作流进度">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <polyline points="9 14 11 16 15 10"/>
            </svg>
          </button>
          <!-- 悬浮弹出 -->
          <Transition name="wf-popup">
            <div v-if="wfHover" class="wf-popup">
              <div v-if="wfData" class="wf-popup-body">
                <WorkflowSteps :data="wfData" />
              </div>
              <div v-else class="wf-popup-empty">
                <span>暂无工作流</span>
                <span class="wf-popup-hint">AI 调用 workflow_start 后自动创建</span>
              </div>
            </div>
          </Transition>
        </div>

        <textarea ref="inputField" v-model="localText" @keydown="handleKeydown"
                  placeholder="输入消息... (Enter 发送, Tab 补全, / 命令，粘贴图片)" rows="1" @blur="handleBlur"
                  @focus="inputFocused=true"
                  @input="handleInput" @paste="handlePaste"></textarea>

        <!-- 图片预览 -->
        <div v-if="images.length > 0" class="image-preview-bar">
          <div v-for="(img, idx) in images" :key="idx" class="image-preview-item">
            <img :src="img" alt="粘贴的图片" class="image-preview-thumb"/>
            <button class="image-preview-remove" title="移除图片" @click="removeImage(idx)">&times;</button>
          </div>
        </div>

        <div class="input-actions">
          <!-- 计划模式按钮已移除 -->
          <button v-if="streaming" class="stop-btn" @click="$emit('abort')" title="停止生成 (Esc)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 class="animate-spin">
              <path d="M21 12a9 9 0 11-6.219-8.56"/>
            </svg>
            <span class="stop-text">停止</span>
          </button>
          <template v-else>
            <button :disabled="!hasHistory" class="continue-btn" title="让 AI 继续生成" @click="$emit('continue')">
              <svg fill="none" height="16" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="16">
                <polyline points="5 4 15 12 5 20"/>
                <line x1="19" x2="19" y1="5" y2="19"/>
              </svg>
            </button>
            <button :class="{ active: localText.trim() && !streaming }" :disabled="!localText.trim() || streaming"
                    class="send-btn" @click="handleSend">
              <svg fill="none" height="16" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" width="16">
                <line x1="22" x2="11" y1="2" y2="13"/>
                <polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </template>
        </div>
      </div>

      <!-- Token 用量 & 模型选择 -->
      <div class="usage-bar">
        <div class="usage-stats">
        <!-- 连接状态 -->
        <span class="usage-item status-connected" :class="{ offline: !connected }">
          <span class="status-dot-sm" :class="{ online: connected }"></span>
          {{ connected ? '已连接' : '连接中...' }}
        </span>
        <span class="usage-sep">|</span>
        <!-- 总 token（点击展开详情） -->
        <span class="usage-item usage-total" @click="showUsageDetail = !showUsageDetail" title="点击展开/折叠详情">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>
          </svg>
          {{ fmt(usage.totalTokens || 0) }}
          <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="usage-expand-icon" :class="{ open: showUsageDetail }">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </span>
        <!-- 详情：输入/输出/缓存/价格 -->
        <template v-if="showUsageDetail">
          <span class="usage-item hide-mobile" :title="'输入: '+fmt(usage.promptTokens)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            输入 {{ fmt(usage.promptTokens) }}
          </span>
          <span class="usage-item hide-mobile" :title="'输出: '+fmt(usage.completionTokens)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            输出 {{ fmt(usage.completionTokens) }}
          </span>
          <span class="usage-item hide-mobile"
                :title="'缓存命中: '+fmt(usage.cacheHit)+' / 未命中: '+fmt(usage.cacheMiss)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
            缓存 {{ cacheRate }}%
          </span>
          <span class="usage-item usage-cost-item" v-if="usage.hasPrice">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
            </svg>
            ¥{{ (usage.totalCost || 0).toFixed(2) }}
          </span>
        </template>
          <span class="usage-sep">|</span>
          <span class="usage-context-circle"
                :title="'上下文: '+fmt(usage.lastPromptTokens||usage.promptTokens)+' / '+fmt(usage.maxContextTokens)">
            <svg viewBox="0 0 32 32" class="context-ring">
              <circle cx="16" cy="16" r="13" fill="none" stroke="var(--bg-3)" stroke-width="4" />
              <circle cx="16" cy="16" r="13" fill="none" stroke="var(--fg-3)"
                      stroke-width="4" stroke-linecap="round"
                      :stroke-dasharray="81.68" :stroke-dashoffset="81.68 * (1 - Math.min(ctxPct,100)/100)"
                      transform="rotate(-90 16 16)" />
            </svg>
          </span>
          <button class="usage-refresh" @click="$emit('refreshUsage')" title="刷新用量">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M23 4v6h-6"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
          </button>
        </div>
        <div class="model-actions">
        <!-- 技能指定 -->
        <div class="skill-selector">
          <button class="effort-btn" @click="toggleSkillPicker" title="选择技能">
            技能
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
          <div class="skill-panel" v-if="showSkillPicker">
            <div class="skill-panel-search">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input ref="skillSearchInput" v-model="skillSearchQuery" type="text" placeholder="搜索技能..." class="skill-search-input" @keydown.esc="showSkillPicker = false"/>
            </div>
            <div class="skill-panel-list">
              <div v-if="skillLoading" class="skill-panel-empty">
                <span class="loading-dot"></span> 加载中...
              </div>
              <div v-else-if="filteredSkills.length === 0" class="skill-panel-empty">无匹配技能</div>
              <div v-for="s in filteredSkills" :key="s.name" class="skill-panel-item"
                   :class="{ active: isSkillSelected(s) }" @click="toggleSkill(s)">
                <div class="skill-item-info">
                  <div class="skill-item-name">{{ s.name }}</div>
                  <div v-if="s.description" class="skill-item-desc">{{ s.description }}</div>
                </div>
                <svg v-if="isSkillSelected(s)" class="skill-item-check" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </div>
            </div>
          </div>
        </div>
        <!-- 权限切换 -->
        <div class="permission-hitl-selector">
          <div class="reasoning-effort-selector">
            <button class="effort-btn" @click="togglePermissionPicker" :title="currentPermission ? '审批模式' : '自由模式'">
              {{ currentPermission ? '审批' : '自由' }}
              <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </button>
            <div class="effort-dropdown" v-if="showPermissionPicker">
              <div class="effort-dropdown-title">权限模式</div>
              <div class="effort-dropdown-list">
                <div v-for="opt in permissionOptions" :key="String(opt.value)" class="effort-option"
                     :class="{ active: opt.value === currentPermission }" @click="pickPermission(opt.value)">
                  <span class="effort-option-name">{{ opt.label }}</span>
                  <svg v-if="opt.value === currentPermission" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="reasoning-effort-selector">
          <button class="effort-btn" @click="toggleEffortPicker" :title="'当前推理强度: '+currentReasoningEffort">
            {{ effortLabel }}
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
          <div class="effort-dropdown" v-if="showEffortPicker">
            <div class="effort-dropdown-title">推理强度</div>
            <div class="effort-dropdown-list">
              <div v-for="opt in effortOptions" :key="opt.value" class="effort-option"
                   :class="{ active: opt.value === currentReasoningEffort }" @click="pickEffort(opt.value)">
                <span class="effort-option-name">{{ opt.label }}</span>
                <svg v-if="opt.value === currentReasoningEffort" width="14" height="14" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </div>
            </div>
          </div>
        </div>
        <div class="model-selector" v-if="currentModel">
          <button class="model-btn" @click="toggleModelPicker" :title="'当前模型: '+currentModel">
            {{ currentModel }}
            <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
          <div class="model-dropdown" v-if="showModelPicker">
            <div class="model-dropdown-title">切换模型</div>
            <div class="model-dropdown-list">
              <div v-for="m in availableModels" :key="m.name" class="model-option"
                   :class="{ active: m.active }" @click="pickModel(m.name)">
                <span class="model-option-name">{{ m.name }}</span>
                <svg v-if="m.active" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                     stroke-width="2">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </div>
            </div>
          </div>
        </div>
        </div>
      </div>
    </div>

    <!-- 桌面宠物精灵 -->
    <PetSprite v-if="petSpritesheetUrl" class="pet-float"
               :spritesheet-url="petSpritesheetUrl"
               :state="petState"
               :initial-x="petPosition.x" :initial-y="petPosition.y"
               :initial-size-index="petSizeIndex"
               @position-change="savePetPosition"
               @size-change="savePetSize" />
  </div>
</template>

<script setup>
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useAppStore} from '../stores/app'
import {agentAPI, petAPI} from '../services/api'
import PetSprite from './PetSprite.vue'
import WorkflowSteps from './WorkflowSteps.vue'

const props = defineProps({
  inputText: {type: String, default: ''},
  streaming: {type: Boolean, default: false},
  usage: {type: Object, default: () => ({})},
  currentModel: {type: String, default: ''},
  availableModels: {type: Array, default: () => []},
  workspaceHash: {type: String, default: null},
  sessionName: {type: String, default: null},
  hasHistory: {type: Boolean, default: false},
  currentReasoningEffort: {type: String, default: 'max'},
  connected: {type: Boolean, default: true},
  version: {type: String, default: ''},
  currentSkill: {type: Object, default: null},
  currentPermission: {type: Boolean, default: false},
  petState: {type: String, default: 'idle'}
})

const emit = defineEmits(['update:inputText', 'send', 'abort', 'clear', 'export', 'refreshUsage', 'switchModel', 'continue', 'refreshModels', 'switchReasoningEffort', 'switchSkill', 'switchPermission'])

const inputField = ref(null)
const inputFocused = ref(false)
const localText = ref(props.inputText)
const images = ref([]) // 粘贴的图片 base64 Data URI 列表

// 同步 props 到本地
watch(() => props.inputText, v => localText.value = v)
watch(localText, v => emit('update:inputText', v))

// ============= 斜杠命令 =============
const slashPopupOpen = ref(false)
const slashQuery = ref('')
const activePopupIdx = ref(0)
const backendCommands = ref([])
const backendSkills = ref([])
const commandsLoaded = ref(false)

const defaultSlashCmds = [
  {cmd: '/new', desc: '新建对话', type: 'session'},
  {cmd: '/clear', desc: '清空对话', type: 'session'},
  {cmd: '/retry', desc: '重试最后一条', type: 'session'},
  {cmd: '/compact', desc: '折叠上下文', type: 'session'},
  {cmd: '/export', desc: '导出对话', type: 'session'},
  {cmd: '/plan', desc: '进入计划模式', type: 'mode'},
  {cmd: '/execute', desc: '退出计划模式', type: 'mode'},
  {cmd: '/continue', desc: '继续生成', type: 'mode'},
  {cmd: '/sessions', desc: '列出历史会话', type: 'session'},
  {cmd: '/help', desc: '显示帮助信息', type: 'system'},
  {cmd: '/exit', desc: '退出', type: 'system'},
  {cmd: '/hitl', desc: '切换 HITL 模式', type: 'mode'},
  {cmd: '/agree', desc: '批准待执行工具', type: 'mode'},
  {cmd: '/deny', desc: '拒绝待执行工具', type: 'mode'},
  {cmd: '/init', desc: '初始化项目文档', type: 'system'}
]

const mergedCommands = computed(() => {
  if (backendCommands.value.length > 0) return backendCommands.value.map(c => ({
    cmd: c.cmd,
    desc: c.desc || '',
    type: c.type || 'system'
  }))
  return defaultSlashCmds
})

const skillCommands = computed(() => backendSkills.value.map(s => ({
  cmd: `/skill:${s.name}`,
  desc: s.description || '运行 skill',
  type: 'skill'
})))
const allSlashCmds = computed(() => [...mergedCommands.value, ...skillCommands.value])

const filteredSlashCmds = computed(() => {
  if (!slashQuery.value) return allSlashCmds.value
  const q = slashQuery.value.toLowerCase()
  return allSlashCmds.value.filter(c => c.cmd.toLowerCase().includes(q) || c.desc.toLowerCase().includes(q))
})

const getSlashIcon = (cmd) => {
  const icons = {
    '/new': '✨', '/clear': '🗑️', '/retry': '🔄', '/compact': '📦', '/export': '📥', '/plan': '📋',
    '/execute': '⚡', '/continue': '▶️', '/sessions': '📂', '/load': '📂', '/rewind': '⏪', '/init': '🔧', '/hitl': '🛡',
    '/agree': '✅', '/deny': '❌', '/help': '❓', '/exit': '👋'
  }
  if (cmd?.startsWith('/skill:')) return '🧩'
  return icons[cmd] || '🔧'
}

const loadCommands = async () => {
  if (commandsLoaded.value) return
  try {
    const [cr, sr] = await Promise.allSettled([agentAPI.getCommands(), agentAPI.getSkills()])
    if (cr.status === 'fulfilled' && cr.value.success && cr.value.data) backendCommands.value = cr.value.data
    if (sr.status === 'fulfilled' && sr.value.success && sr.value.data) backendSkills.value = sr.value.data
  } catch {
  }
  commandsLoaded.value = true
}

const selectSlashCmd = (cmd) => {
  slashPopupOpen.value = false;
  slashQuery.value = ''
  localText.value = cmd.cmd + ' '
  nextTick(() => inputField.value?.focus())
}

// ============= 输入处理 =============
const handleInput = () => {
  autoResize()
  const m = localText.value.match(/(^|\s)(\/)([^\s]*)$/)
  if (m) {
    slashPopupOpen.value = true;
    slashQuery.value = m[3] || '';
    activePopupIdx.value = 0;
    if (!commandsLoaded.value) {
      commandsLoaded.value = false;
      loadCommands()
    }
  } else slashPopupOpen.value = false
}

const handleBlur = () => {
  inputFocused.value = false;
  setTimeout(() => {
    slashPopupOpen.value = false
  }, 200)
}

const handleKeydown = (e) => {
  if (slashPopupOpen.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      activePopupIdx.value = (activePopupIdx.value + 1) % filteredSlashCmds.value.length;
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      activePopupIdx.value = (activePopupIdx.value - 1 + filteredSlashCmds.value.length) % filteredSlashCmds.value.length;
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault();
      slashPopupOpen.value = false;
      return
    }
    if ((e.key === 'Enter' && !e.shiftKey) || e.key === 'Tab') {
      e.preventDefault();
      if (filteredSlashCmds.value.length > 0) selectSlashCmd(filteredSlashCmds.value[activePopupIdx.value]);
      return
    }
  }
  if (e.key === 'Escape' && props.streaming) {
    e.preventDefault();
    emit('abort');
    return
  }
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend()
  }
}

const handleSend = () => {
  if (localText.value.trim() && !props.streaming) {
    let text = localText.value.trim()
    // 有选中技能时，拼接技能指令到消息顶部
    if (selectedSkills.value.length > 0) {
      const skillLines = selectedSkills.value.map(s => `/skill:${s.name}`).join('\n')
      text = `调用技能：\n${skillLines}\n\n${text}`
    }
    emit('send', images.value, text)
    // 发送后清空图片和技能标签
    images.value = []
    selectedSkills.value = []
    // 等待父组件清空文本后，重置 textarea 高度
    nextTick(() => autoResize())
  }
}

/**
 * 粘贴事件处理：从剪贴板捕获图片，转为 base64 Data URI。
 */
const handlePaste = async (e) => {
  const items = e.clipboardData?.items
  if (!items) return

  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault() // 阻止默认粘贴文本
      const file = item.getAsFile()
      if (!file) continue

      try {
        const dataUrl = await fileToDataUrl(file)
        // 限制图片数量（防止请求体过大）
        if (images.value.length >= 10) {
          console.warn('图片数量已达上限（10张），跳过')
          continue
        }
        images.value.push(dataUrl)
      } catch (err) {
        console.error('图片转换失败:', err)
      }
    }
  }
}

/**
 * 移除已粘贴的图片
 */
const removeImage = (idx) => {
  images.value.splice(idx, 1)
}

/**
 * 将 File 对象转为 base64 Data URI
 */
const fileToDataUrl = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

const autoResize = () => {
  const el = inputField.value;
  if (el) {
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 160) + 'px'
  }
}

// ============= 模型切换 =============
const showModelPicker = ref(false)
const toggleModelPicker = () => {
  showModelPicker.value = !showModelPicker.value
  if (showModelPicker.value) {
    emit('refreshModels')
  }
}
const pickModel = async (name) => {
  if (name === props.currentModel) {
    showModelPicker.value = false;
    return
  }
  emit('switchModel', name)
  showModelPicker.value = false
}

// ============= 用量详情折叠 =============
const showUsageDetail = ref(false)

// ============= 技能选择（多选） =============
const showSkillPicker = ref(false)
const availableSkills = ref([])
const skillLoading = ref(false)
const selectedSkills = ref([])
const skillSearchQuery = ref('')
const skillSearchInput = ref(null)

const filteredSkills = computed(() => {
  if (!skillSearchQuery.value) return availableSkills.value
  const q = skillSearchQuery.value.toLowerCase()
  return availableSkills.value.filter(s =>
    (s.name || '').toLowerCase().includes(q) ||
    (s.description || '').toLowerCase().includes(q)
  )
})

const isSkillSelected = (skill) => selectedSkills.value.some(s => s.name === skill.name)

const toggleSkillPicker = async () => {
  showSkillPicker.value = !showSkillPicker.value
  if (showSkillPicker.value && availableSkills.value.length === 0 && !skillLoading.value) {
    skillLoading.value = true
    try {
      const r = await agentAPI.getSkills()
      if (r.success && r.data) availableSkills.value = r.data
    } catch {}
    skillLoading.value = false
  }
  if (showSkillPicker.value) {
    skillSearchQuery.value = ''
    nextTick(() => skillSearchInput.value?.focus())
  }
}

const toggleSkill = (skill) => {
  const idx = selectedSkills.value.findIndex(s => s.name === skill.name)
  if (idx >= 0) {
    selectedSkills.value.splice(idx, 1)
  } else {
    selectedSkills.value.push(skill)
  }
  emit('switchSkill', [...selectedSkills.value])
}

const removeSkill = (skill) => {
  selectedSkills.value = selectedSkills.value.filter(s => s.name !== skill.name)
  emit('switchSkill', [...selectedSkills.value])
}

// ============= 工作流 TODO =============
const wfData = ref(null)
const wfHover = ref(false)
let wfLoadTimer = null

const loadWorkflow = async () => {
  if (!props.workspaceHash || !props.sessionName) {
    wfData.value = null
    return
  }
  try {
    const { sessionsAPI } = await import('../services/api')
    const res = await sessionsAPI.getWorkflow(props.sessionName, props.workspaceHash)
    if (res.success && res.data) {
      wfData.value = res.data
    } else {
      wfData.value = null
    }
  } catch {
    wfData.value = null
  }
}

watch([() => props.workspaceHash, () => props.sessionName], () => {
  loadWorkflow()
}, { immediate: true })

const onWfEnter = () => {
  wfHover.value = true
  loadWorkflow()
}
const onWfLeave = () => {
  wfHover.value = false
}

// ============= 权限切换 =============
const showPermissionPicker = ref(false)
const permissionOptions = [
  {value: false, label: '自由模式'},
  {value: true, label: '审批模式'}
]
const togglePermissionPicker = () => {
  showPermissionPicker.value = !showPermissionPicker.value
}
const pickPermission = (level) => {
  emit('switchPermission', level)
  showPermissionPicker.value = false
}

// 点击外部关闭选择器
const handleOutside = (e) => {
  if (!e.target.closest('.model-selector')) showModelPicker.value = false;
  if (!e.target.closest('.reasoning-effort-selector')) showEffortPicker.value = false
  if (!e.target.closest('.skill-selector')) showSkillPicker.value = false
  if (!e.target.closest('.permission-hitl-selector')) showPermissionPicker.value = false
}

// ============= 推理强度切换 =============
const showEffortPicker = ref(false)
const effortOptions = [
  {value: 'none', label: '无'},
  {value: 'low', label: '低'},
  {value: 'medium', label: '中'},
  {value: 'high', label: '高'},
  {value: 'max', label: '最大'}
]
const effortLabel = computed(() => {
  const found = effortOptions.find(o => o.value === props.currentReasoningEffort)
  return found ? found.label : props.currentReasoningEffort
})
const toggleEffortPicker = () => {
  showEffortPicker.value = !showEffortPicker.value
}
const pickEffort = async (value) => {
  if (value === props.currentReasoningEffort) {
    showEffortPicker.value = false;
    return
  }
  emit('switchReasoningEffort', value)
  showEffortPicker.value = false
}

// ============= Usage =============
const fmt = (n) => {
  if (!n || n === 0) return '0';
  if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M';
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K';
  return String(n)
}
const cacheRate = computed(() => {
  const t = props.usage.cacheHit + props.usage.cacheMiss;
  return t === 0 ? '0' : ((props.usage.cacheHit / t) * 100).toFixed(1)
})
const ctxPct = computed(() => {
  const m = props.usage.maxContextTokens || 128000
  const c = props.usage.lastPromptTokens || props.usage.promptTokens || 0
  if (m <= 0 || c <= 0) return 0
  const pct = Math.round((c / m) * 100)
  // 小于 5% 时至少展示 5%，让填充条肉眼可见
  return Math.max(5, Math.min(pct, 100))
})

onMounted(() => {
  loadCommands();
  document.addEventListener('click', handleOutside)
})
onBeforeUnmount(() => document.removeEventListener('click', handleOutside))

// ── 桌面宠物精灵图 ──
const petSpritesheetUrl = ref('')
const petPosition = ref({ x: 0, y: 0 })
const petSizeIndex = ref(1)

async function loadPet() {
  try {
    const resp = await petAPI.getInfo()
    const petData = resp.data
    if (petData && petData.active && (petData.spritesheetUrl || petData.spritesheetPath)) {
      // 兼容新旧字段名：spritesheetUrl（新）或 spritesheetPath（旧）
      const url = petData.spritesheetUrl || petData.spritesheetPath
      if (url && !url.startsWith('/api/')) {
        petSpritesheetUrl.value = petAPI.getSpritesheetUrl() + '?t=' + Date.now()
      } else if (url) {
        petSpritesheetUrl.value = url + '?t=' + Date.now()
      }
      if (petData.position) {
        petPosition.value = { x: petData.position.x || 0, y: petData.position.y || 0 }
      }
      if (typeof petData.sizeIndex === 'number') {
        petSizeIndex.value = petData.sizeIndex
      }
    }
  } catch { /* pet 不可用时静默 */ }
}
loadPet()

const appStore = useAppStore()
// 当其他组件（如设置页）切换宠物时，重新加载
watch(() => appStore.activePetName, (newName, oldName) => {
  if (newName && newName !== oldName) {
    loadPet()
  }
})

async function savePetPosition(pos) {
  try {
    await petAPI.savePosition(pos)
  } catch { /* 保存失败静默 */ }
}

async function savePetSize(idx) {
  petSizeIndex.value = idx
  try {
    await petAPI.savePosition({ sizeIndex: idx })
  } catch { /* 保存失败静默 */ }
}

// 暴露焦點方法给父组件
defineExpose({focus: () => inputField.value?.focus(), autoResize})
</script>

<style scoped>
.input-area {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 10px;
  background: transparent;
  z-index: 10;
}

.input-box {
  display: flex;
  flex-direction: column;
  gap: 0;
  background: var(--glass-bg-2);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  padding: 6px 8px 0;
  transition: border-color var(--t);
  box-shadow: var(--glass-shadow);
  position: relative;
  z-index: 10;
}

.input-box.focused {
  border-color: var(--accent);
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.input-box textarea {
  flex: 1;
  min-height: 22px;
  max-height: 160px;
  padding: 0;
  background: none;
  border: none;
  outline: none;
  font-size: 14px;
  line-height: 1.5;
  color: var(--fg);
  resize: none;
}

.input-box textarea::placeholder {
  color: var(--fg-4);
}

/* 图片预览 */
.image-preview-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 0 4px 0;
  border-top: 1px solid var(--border);
  margin-top: 4px;
}

.image-preview-item {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--border);
  flex-shrink: 0;
}

.image-preview-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.image-preview-remove {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  cursor: pointer;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.image-preview-remove:hover {
  background: rgba(239, 68, 68, 0.9);
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.btn-icon-sm {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-4);
  transition: all var(--t);
  cursor: pointer;
}

.btn-icon-sm:hover {
  background: var(--bg-3);
  color: var(--fg-2);
}

.btn-icon-sm.active {
  background: var(--accent-bg);
  color: var(--accent);
}

.send-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-4);
  transition: all var(--t);
}

.send-btn.active {
  background: var(--accent);
  color: #fff;
}

.send-btn.active:hover {
  background: var(--blue-dark);
}

.send-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.continue-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-4);
  transition: all var(--t);
  cursor: pointer;
}

.continue-btn:hover {
  background: var(--bg-3);
  color: var(--accent);
}

.continue-btn:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.stop-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--red);
  color: #fff;
  border: none;
  border-radius: var(--r);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--t);
  animation: pulse-red 1.5s infinite;
}

.stop-btn:hover {
  background: #b91c1c;
}

.stop-btn svg {
  animation: spin 1s linear infinite;
}

.stop-text {
  margin-left: 2px;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes pulse-red {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(239, 68, 68, 0);
  }
}

.todo-trigger {
  position: relative;
  flex-shrink: 0;
}

.todo-btn {
  width: 28px;
  height: 28px;
  border-radius: var(--r-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--fg-4);
  transition: all var(--t);
  position: relative;
  cursor: pointer;
}

.todo-btn:hover {
  background: var(--bg-3);
  color: var(--fg-2);
}

.todo-btn.has-todos {
  color: var(--accent);
}

.todo-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 14px;
  height: 14px;
  padding: 0 3px;
  background: var(--accent);
  color: white;
  font-size: 9px;
  font-weight: 700;
  border-radius: 7px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.todo-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  width: 280px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  z-index: 100;
  overflow: hidden;
}

.todo-tooltip-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
}

.todo-tooltip-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
}

.todo-tooltip-stats {
  font-size: 11px;
  color: var(--fg-4);
  background: var(--bg-3);
  padding: 2px 6px;
  border-radius: var(--r-sm);
}

.todo-tooltip-empty {
  padding: 16px;
  text-align: center;
  color: var(--fg-4);
  font-size: 12px;
}

.todo-tooltip-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 4px;
}

.todo-tooltip-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: var(--r-sm);
  transition: background var(--t);
}

.todo-tooltip-item:hover {
  background: var(--bg-2);
}

.todo-status-icon {
  flex-shrink: 0;
  font-size: 11px;
  line-height: 1.5;
}

.todo-content {
  font-size: 12px;
  color: var(--fg-2);
  line-height: 1.5;
}

.todo-content.completed {
  text-decoration: line-through;
  color: var(--fg-4);
}

.todo-content.in_progress {
  color: var(--accent);
  font-weight: 500;
}

.todo-tooltip-footer {
  padding: 6px 12px 8px;
  border-top: 1px solid var(--border);
}

.todo-progress-bar {
  height: 3px;
  background: var(--bg-3);
  border-radius: 2px;
  overflow: hidden;
}

.todo-progress-fill {
  height: 100%;
  background: var(--accent);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.todo-completed-section {
  border-top: 1px solid var(--border);
  margin-top: 4px;
}

.todo-completed-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  font-size: 11px;
  color: var(--fg-4);
  cursor: pointer;
  transition: all var(--t);
}

.todo-completed-toggle:hover {
  color: var(--fg-2);
  background: var(--bg-2);
}

.todo-completed-toggle svg {
  transition: transform 0.2s ease;
}

.collapse-enter-active, .collapse-leave-active {
  transition: all 0.2s ease;
  max-height: 200px;
}

.collapse-enter-from, .collapse-leave-to {
  max-height: 0;
  opacity: 0;
}

.tooltip-enter-active, .tooltip-leave-active {
  transition: all 0.2s ease;
}

.tooltip-enter-from, .tooltip-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

/* 斜杠命令弹窗 */
.slash-popup {
  position: absolute;
  bottom: 100%;
  left: 16px;
  right: 16px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  z-index: 100;
  overflow: hidden;
  margin-bottom: 4px;
}

.slash-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
}

.slash-popup-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
}

.slash-popup-hint {
  font-size: 11px;
  color: var(--fg-4);
}

.slash-popup-list {
  max-height: 280px;
  overflow-y: auto;
  padding: 4px;
}

.slash-popup-loading, .slash-popup-empty {
  padding: 24px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--fg-4);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.slash-popup-loading {
  color: var(--accent);
}

.slash-popup-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: background var(--t);
}

.slash-popup-item:hover, .slash-popup-item.active {
  background: var(--bg-2);
}

.slash-popup-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  background: var(--bg-3);
  border-radius: var(--r-sm);
  flex-shrink: 0;
}

.slash-popup-info {
  flex: 1;
  min-width: 0;
}

.slash-popup-cmd {
  font-size: 13px;
  font-weight: 600;
  font-family: var(--mono);
  color: var(--fg);
  display: flex;
  align-items: center;
  gap: 4px;
}

.slash-popup-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 4px;
  font-size: 10px;
  font-weight: 500;
  border-radius: var(--r-sm);
  line-height: 1.4;
}

.slash-popup-badge.skill {
  background: #dcfce7;
  color: #16a34a;
}

.slash-popup-badge.mode {
  background: #dbeafe;
  color: #2563eb;
}

.slash-popup-badge.session {
  background: #fef3c7;
  color: #d97706;
}

.slash-popup-desc {
  font-size: 11px;
  color: var(--fg-4);
  margin-top: 1px;
}

[data-theme="dark"] .slash-popup-badge.skill {
  background: #052e16;
  color: #4ade80;
}

[data-theme="dark"] .slash-popup-badge.mode {
  background: #1e3a5f;
  color: #60a5fa;
}

[data-theme="dark"] .slash-popup-badge.session {
  background: #422006;
  color: #fbbf24;
}

.slash-popup-enter-active, .slash-popup-leave-active {
  transition: all 0.15s ease;
}

.slash-popup-enter-from, .slash-popup-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}

/* Usage bar — 融入 input-box 底部 */
.usage-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 4px 12px;
  font-size: 11px;
  color: var(--fg-3);
  border-top: 1px solid var(--glass-border);
  margin-top: 4px;
}

.usage-stats {
  display: flex;
  align-items: center;
  gap: 12px;
}

.usage-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  cursor: default;
  color: var(--fg-3);
}

.usage-item svg {
  color: var(--fg-4);
  flex-shrink: 0;
}

.usage-sep {
  color: var(--border);
  font-size: 14px;
}

.usage-total {
  cursor: pointer;
  user-select: none;
}

.usage-total:hover {
  color: var(--fg-2);
}

.usage-expand-icon {
  transition: transform 0.2s ease;
}

.usage-expand-icon.open {
  transform: rotate(180deg);
}

.usage-context-circle {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.context-ring {
  width: 14px;
  height: 14px;
}

.usage-progress {
  display: none;
}

.usage-value {
  display: none;
}

.usage-value.high {
  color: var(--red);
  font-weight: 600;
}

.usage-value.medium {
  color: var(--yellow);
}

.usage-refresh {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-sm);
  color: var(--fg-4);
  font-size: 14px;
  transition: all var(--t);
  cursor: pointer;
}

.usage-refresh:hover {
  background: var(--bg-3);
  color: var(--fg-2);
}

.usage-cost-item {
  color: var(--yellow);
  font-weight: 500;
  font-family: var(--mono);
}

.usage-cost-item svg {
  color: var(--yellow);
}

/* 连接状态 */
.status-connected {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
}

.status-connected.offline {
  color: var(--yellow);
}

.status-dot-sm {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--fg-4);
  flex-shrink: 0;
}

.status-dot-sm.online {
  background: var(--green, #10b981);
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.2);
}


.model-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ============= 技能选择器 ============= */
.skill-selector,
.permission-hitl-selector {
  position: relative;
  display: inline-flex;
  vertical-align: middle;
}

/* 技能面板 */
.skill-panel {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 4px;
  width: 340px;
  max-height: 420px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  z-index: 100;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.skill-panel-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.skill-panel-search svg {
  color: var(--fg-4);
  flex-shrink: 0;
}

.skill-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: none;
  font-size: 13px;
  color: var(--fg);
}

.skill-search-input::placeholder {
  color: var(--fg-4);
}

.skill-panel-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.skill-panel-empty {
  padding: 24px 16px;
  text-align: center;
  font-size: 12px;
  color: var(--fg-4);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.skill-panel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: background var(--t);
}

.skill-panel-item:hover {
  background: var(--bg-2);
}

.skill-panel-item.active {
  background: var(--accent-bg);
}

.skill-item-info {
  flex: 1;
  min-width: 0;
}

.skill-item-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.skill-item-desc {
  font-size: 11px;
  color: var(--fg-4);
  margin-top: 1px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.skill-item-check {
  color: var(--accent);
  flex-shrink: 0;
}

/* 已选技能标签 */
.skill-chips-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 8px 0;
}

.skill-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px 2px 8px;
  background: var(--accent-bg);
  color: var(--accent);
  border: 1px solid var(--accent);
  border-radius: 12px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.4;
  cursor: default;
}

.skill-chip svg {
  flex-shrink: 0;
}

.skill-chip-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border: none;
  background: none;
  color: var(--accent);
  font-size: 12px;
  padding: 0;
  cursor: pointer;
  border-radius: 50%;
  transition: all var(--t);
  line-height: 1;
}

.skill-chip-remove:hover {
  background: var(--accent);
  color: #fff;
}

/* 权限选择器（下拉式） */
.tool-dropdown {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 4px;
  min-width: 160px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  z-index: 100;
  overflow: hidden;
}

.tool-dropdown-title {
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--fg-4);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.tool-dropdown-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 4px;
}

.tool-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--fg-2);
  cursor: pointer;
  border-radius: var(--r-sm);
  transition: all var(--t);
}

.tool-option:hover {
  background: var(--bg-2);
}

.tool-option.active {
  color: var(--accent);
  font-weight: 500;
}

.tool-option svg {
  color: var(--accent);
  flex-shrink: 0;
}

.reasoning-effort-selector {
  position: relative;
  display: inline-flex;
  vertical-align: middle;
}

.effort-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
  padding: 2px 6px;
  border-radius: var(--r-sm);
  transition: all var(--t);
  cursor: pointer;
  background: none;
  border: none;
  white-space: nowrap;
}

.effort-btn:hover {
  background: var(--bg-3);
}

.effort-btn svg {
  color: var(--fg-4);
  width: 8px;
  height: 8px;
  flex-shrink: 0;
}

.effort-dropdown {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 4px;
  min-width: 140px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  z-index: 100;
  overflow: hidden;
}

.effort-dropdown-title {
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--fg-4);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.effort-dropdown-list {
  max-height: 200px;
  overflow-y: auto;
}

.effort-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--fg-2);
  cursor: pointer;
  transition: all var(--t);
}

.effort-option:hover {
  background: var(--bg-2);
}

.effort-option.active {
  color: var(--accent);
  font-weight: 500;
}

.effort-option svg {
  color: var(--accent);
}

.model-selector {
  position: relative;
  display: inline-flex;
  vertical-align: middle;
}

.model-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--fg-2);
  font-family: var(--mono);
  padding: 2px 6px;
  border-radius: var(--r-sm);
  transition: all var(--t);
  cursor: pointer;
  background: none;
  border: none;
  white-space: nowrap;
}

.model-btn:hover {
  background: var(--bg-3);
}

.model-dropdown {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 4px;
  min-width: 200px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--r);
  box-shadow: var(--shadow);
  z-index: 100;
  overflow: hidden;
}

.model-dropdown-title {
  padding: 8px 12px;
  font-size: 11px;
  font-weight: 600;
  color: var(--fg-4);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.model-dropdown-list {
  max-height: 200px;
  overflow-y: auto;
}

.model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 13px;
  font-family: var(--mono);
  color: var(--fg-2);
  cursor: pointer;
  transition: all var(--t);
}

.model-option:hover {
  background: var(--bg-2);
}

.model-option.active {
  color: var(--accent);
  font-weight: 500;
}

.model-option svg {
  color: var(--accent);
}

.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  animation: loadBounce 0.6s infinite alternate;
}

@keyframes loadBounce {
  from {
    opacity: 0.3;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1.2);
  }
}

/* ===== 移动端适配 ===== */
@media (max-width: 640px) {
  .input-area {
    padding: 8px 6px;
  }

  .input-box {
    padding: 4px 6px 0;
    border-radius: var(--r);
  }

  .input-box textarea {
    font-size: 16px;
    min-height: 20px;
  }

  .input-row {
    gap: 4px;
  }

  .btn-icon-sm, .send-btn, .continue-btn, .todo-btn {
    width: 32px;
    height: 32px;
  }

  .hide-mobile {
    display: none !important;
  }

  .model-btn {
    font-size: 11px;
    padding: 2px 4px;
  }

  .model-dropdown {
    min-width: 160px;
  }

  .effort-btn {
    font-size: 11px;
    padding: 2px 4px;
  }

  .effort-dropdown {
    min-width: 120px;
  }

  .todo-tooltip {
    width: 240px;
  }

  .slash-popup {
    left: 8px;
    right: 8px;
  }

  .tool-btn {
    font-size: 11px;
    padding: 2px 4px;
  }

  .tool-dropdown {
    min-width: 140px;
  }
}

/* 宠物精灵浮层 — 优先级低于输入面板 */
:deep(.pet-float) {
  position: absolute;
  bottom: 60px;
  right: 16px;
  z-index: 5;
  pointer-events: auto;
}

/* ============= 工作流 TODO ============= */
.wf-todo-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.wf-todo-wrap .todo-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r);
  color: var(--fg-4);
  transition: all var(--t);
  border: none;
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
}
.wf-todo-wrap .todo-btn:hover { background: var(--bg-3); color: var(--accent); }
.wf-todo-wrap .todo-btn.active { color: var(--accent); }

.wf-popup {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0;
  min-width: 280px;
  max-width: 360px;
  background: var(--glass-bg);
  backdrop-filter: blur(var(--blur-sm));
  -webkit-backdrop-filter: blur(var(--blur-sm));
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  box-shadow: var(--glass-shadow);
  z-index: 100;
  padding: 10px;
}

.wf-popup-empty {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--fg-3);
}
.wf-popup-hint {
  font-size: 10px;
  color: var(--fg-4);
}

.wf-popup-enter-active,
.wf-popup-leave-active {
  transition: opacity 0.15s, transform 0.15s;
}
.wf-popup-enter-from,
.wf-popup-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>
