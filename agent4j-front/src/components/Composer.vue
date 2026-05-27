<template>
  <div class="composer-wrap">
    <div class="composer-inner">
      <!-- Queued sends -->
      <div v-if="queuedSends.length" class="composer-queued">
        <span class="composer-queued-label">{{ queuedSends.length }} 条排队</span>
        <span v-for="(text, i) in queuedSends" :key="i" class="composer-queue-chip" :title="text">
          <span class="text">{{ text.length > 30 ? text.slice(0, 30) + '…' : text }}</span>
          <span class="x" @click="$emit('dequeue', i)">×</span>
        </span>
      </div>

      <div class="composer" :class="{ focused }">
        <!-- Slash command popup -->
        <div v-if="slashPopup" class="popup slash-popup">
          <div
            v-for="(cmd, i) in filteredSlashCmds"
            :key="cmd.cmd"
            class="popup-item"
            :data-active="i === activePopupIdx"
            @click="pickSlashCmd(cmd)"
            @mouseenter="activePopupIdx = i"
          >
            <span class="cmd-icon">{{ getSlashIcon(cmd.cmd) }}</span>
            <div class="cmd-body">
              <span class="cmd-name">{{ cmd.cmd }}</span>
              <span class="cmd-desc">{{ cmd.desc }}</span>
            </div>
            <span v-if="cmd.kb" class="cmd-kb">{{ cmd.kb }}</span>
          </div>
          <div v-if="filteredSlashCmds.length === 0" class="popup-empty">无匹配命令</div>
        </div>

        <textarea
          ref="textareaRef"
          :value="draft"
          @input="handleInput"
          @keydown="handleKeyDown"
          @focus="focused = true"
          @blur="focused = false"
          :placeholder="busy ? '处理中… (Esc 中断)' : '输入消息…'"
          rows="1"
        />

        <div class="composer-foot">
          <button class="cf-btn" @click="$emit('clear')" title="清空">
            <span class="ico">🗑</span>
          </button>
          <span class="grow" />
          <!-- Mode switch -->
          <div class="mode-switch" :data-mode="editMode">
            <button
              v-for="m in modes"
              :key="m.k"
              class="ms-seg"
              :data-on="editMode === m.k"
              @click="$emit('setMode', m.k)"
              :title="m.hint"
            >
              <span>{{ m.icon }}</span>
              <span>{{ m.label }}</span>
            </button>
          </div>
          <span class="hint-sep" />
          <!-- Model pill -->
          <span class="model-pill" @click="$emit('cycleModel')">
            <span class="badge">{{ modelLabel }}</span>
          </span>
          <!-- Send button -->
          <button
            class="send-btn"
            @click="handleSend"
            :disabled="!draft.trim() || busy"
            title="发送 (Enter)"
          >↑</button>
        </div>
      </div>

      <div class="hint-row">
        <template v-if="busy">
          <span class="composer-busy-status">
            <span class="composer-busy-pip" />
            <span>{{ busyLabel || '推理中' }}</span>
          </span>
          <span class="grow" />
          <span><kbd>Esc</kbd> 中断</span>
        </template>
        <template v-else>
          <span><kbd>/</kbd> 命令</span>
          <span class="hint-sep" />
          <span><kbd>Enter</kbd> 发送</span>
          <span class="hint-sep" />
          <span><kbd>Shift+Enter</kbd> 换行</span>
          <span class="grow" />
          <span v-if="editMode !== 'auto'" class="pill-tag info">{{ modeLabel }}</span>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

const props = defineProps({
  draft: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  busyLabel: { type: String, default: '' },
  modelLabel: { type: String, default: 'deepseek' },
  editMode: { type: String, default: 'auto' },
  queuedSends: { type: Array, default: () => [] },
  slashCommands: { type: Array, default: () => [] }
})

const emit = defineEmits([
  'update:draft', 'send', 'abort', 'setMode', 'cycleModel', 'clear', 'dequeue'
])

const textareaRef = ref(null)
const focused = ref(false)
const slashPopup = ref(false)
const slashQuery = ref('')
const activePopupIdx = ref(0)

const modes = [
  { k: 'plan', label: '计划', icon: '📋', hint: '只读探索模式' },
  { k: 'review', label: '审核', icon: '🛡', hint: '需要确认' },
  { k: 'auto', label: '自动', icon: '⚡', hint: '自动执行' },
]

const modeLabel = computed(() => {
  const m = modes.find(x => x.k === props.editMode)
  return m ? m.label : props.editMode
})

const defaultSlashCmds = [
  { cmd: '/new', desc: '新建对话', run: 'new' },
  { cmd: '/clear', desc: '清空对话', run: 'clear' },
  { cmd: '/retry', desc: '重试最后一条', run: 'retry' },
  { cmd: '/compact', desc: '折叠上下文', run: 'compact' },
  { cmd: '/export', desc: '导出对话', run: 'export' },
]

const allSlashCmds = computed(() => [...defaultSlashCmds, ...props.slashCommands])

const filteredSlashCmds = computed(() => {
  if (!slashQuery.value) return allSlashCmds.value
  const q = slashQuery.value.toLowerCase()
  return allSlashCmds.value.filter(c => c.cmd.toLowerCase().includes(q))
})

const getSlashIcon = (cmd) => {
  const icons = { '/new': '+', '/clear': '🗑', '/retry': '↺', '/compact': '📦', '/export': '📥' }
  return icons[cmd] || '/'
}

const handleInput = (e) => {
  const v = e.target.value
  emit('update:draft', v)

  const trail = v.match(/(^|\s)(\/)([^\s]*)$/)
  if (trail) {
    slashPopup.value = true
    slashQuery.value = trail[3] ?? ''
    activePopupIdx.value = 0
  } else {
    slashPopup.value = false
  }

  // Auto-resize
  const el = textareaRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 220) + 'px'
  }
}

const handleKeyDown = (e) => {
  if (slashPopup.value) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      activePopupIdx.value = (activePopupIdx.value + 1) % filteredSlashCmds.value.length
      return
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault()
      activePopupIdx.value = (activePopupIdx.value - 1 + filteredSlashCmds.value.length) % filteredSlashCmds.value.length
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      slashPopup.value = false
      return
    }
    if (e.key === 'Enter') {
      e.preventDefault()
      if (filteredSlashCmds.value.length > 0) {
        pickSlashCmd(filteredSlashCmds.value[activePopupIdx.value])
      }
      return
    }
  }

  if (e.key === 'Enter' && !e.shiftKey && !slashPopup.value) {
    e.preventDefault()
    if (props.busy) {
      emit('abort')
    } else if (props.draft.trim()) {
      handleSend()
    }
  }
  if (e.key === 'Escape' && props.busy) {
    e.preventDefault()
    emit('abort')
  }
}

const handleSend = () => {
  if (!props.draft.trim() || props.busy) return
  emit('send')
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })
}

const pickSlashCmd = (cmd) => {
  slashPopup.value = false
  slashQuery.value = ''
  emit('update:draft', '')
  if (cmd.run) {
    // Emit specific events based on command
    if (cmd.run === 'clear') emit('clear')
    else if (cmd.run === 'export') emit('export')
    else if (cmd.run) emit('slash', cmd.run)
  }
}

watch(() => props.draft, () => {
  if (!props.draft && slashPopup.value) {
    slashPopup.value = false
  }
})
</script>

<style scoped>
.composer-wrap {
  padding: 0 28px 18px;
  background: var(--bg);
  position: relative;
}
.composer-wrap::before {
  content: "";
  position: absolute;
  inset: -24px 0 auto 0;
  height: 24px;
  background: linear-gradient(to top, var(--bg), transparent);
  pointer-events: none;
}
.composer-inner { max-width: 760px; margin: 0 auto; }

.composer {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  overflow: visible;
  position: relative;
}
.composer.focused {
  border-color: var(--accent);
  box-shadow: var(--shadow-md), 0 0 0 3px var(--accent-soft);
}

.composer textarea {
  display: block;
  width: 100%;
  resize: none;
  background: none;
  border: none;
  outline: none;
  padding: 12px 14px 6px;
  font-size: 14px;
  line-height: 1.55;
  min-height: 24px;
  max-height: 220px;
}
.composer textarea::placeholder { color: var(--muted-2); }

.composer-foot {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px 8px;
}
.cf-btn {
  padding: 5px 8px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--muted);
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.cf-btn:hover { background: var(--panel); color: var(--fg); }
.grow { flex: 1; }

/* Mode switch */
.mode-switch { display: flex; gap: 2px; }
.ms-seg {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--muted);
  border: 1px solid transparent;
}
.ms-seg:hover { background: var(--panel-2); color: var(--fg); }
.ms-seg[data-on="true"] {
  background: var(--accent-soft);
  color: var(--accent);
  border-color: var(--accent);
}

.hint-sep {
  width: 1px;
  height: 12px;
  background: var(--border-strong);
  flex-shrink: 0;
}

.model-pill {
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--panel);
  border: 1px solid var(--border);
  color: var(--fg-2);
  cursor: pointer;
}
.model-pill:hover { background: var(--panel-2); }
.model-pill .badge {
  font-size: 11px;
  background: var(--accent-soft);
  color: var(--accent);
  padding: 1px 5px;
  border-radius: 3px;
}

.send-btn {
  width: 30px;
  height: 30px;
  border-radius: var(--radius);
  background: var(--accent);
  color: oklch(99% 0 0);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}
.send-btn:hover { background: var(--accent-strong); }
.send-btn:disabled { background: var(--panel-2); color: var(--muted-2); cursor: not-allowed; opacity: 1; }

/* Hint row */
.hint-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 6px 6px;
  font-size: 12px;
  color: var(--muted-2);
}
.hint-row .grow { flex: 1; }
.hint-row kbd {
  background: var(--panel);
  border: 1px solid var(--border);
  padding: 0 4px;
  border-radius: 3px;
  font-size: 11px;
  font-family: var(--font-mono);
}

.composer-busy-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--fg-2);
}
.composer-busy-pip {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  animation: pulse 1.6s ease-out infinite;
}

/* Slash popup */
.popup {
  position: absolute;
  bottom: 100%;
  left: 8px;
  right: 8px;
  max-height: 280px;
  overflow-y: auto;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-lg);
  margin-bottom: 6px;
  z-index: 10;
}
.popup-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
}
.popup-item:hover,
.popup-item[data-active="true"] {
  background: var(--accent-soft);
}
.popup-item .cmd-icon {
  width: 20px;
  text-align: center;
  color: var(--muted);
}
.popup-item .cmd-body { flex: 1; }
.popup-item .cmd-name { color: var(--fg); font-weight: 500; }
.popup-item .cmd-desc { color: var(--muted); margin-left: 8px; font-size: 12px; }
.popup-item .cmd-kb { color: var(--muted-2); font-size: 11px; font-family: var(--font-mono); }
.popup-empty { padding: 12px; color: var(--muted); font-size: 12px; text-align: center; }

/* Queued */
.composer-queued { display: flex; flex-wrap: wrap; gap: 4px; padding: 6px 10px 0; }
.composer-queued-label { font-size: 11px; color: var(--muted-2); margin-right: 4px; }
.composer-queue-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 2px 6px; border-radius: var(--radius);
  background: var(--panel); border: 1px solid var(--border);
  font-size: 12px; color: var(--fg-2);
}
.composer-queue-chip .x { cursor: pointer; opacity: 0.5; }
.composer-queue-chip .x:hover { opacity: 1; }

.pill-tag {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 11px; font-weight: 600;
  padding: 1px 7px; border-radius: 9px;
  background: var(--panel-2); color: var(--fg-2);
}
.pill-tag.info { color: var(--accent); background: var(--accent-soft); }

@keyframes pulse {
  0%   { box-shadow: 0 0 0 0 currentColor; }
  70%  { box-shadow: 0 0 0 6px transparent; }
  100% { box-shadow: 0 0 0 0 transparent; }
}
</style>
