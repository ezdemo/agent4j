<template>
  <div ref="rootRef" class="req-select" @keydown="onKeydown">
    <!-- 触发器：与项目表单控件同风格（bg + border + focus 光晕） -->
    <button
      type="button"
      class="req-select-trigger"
      :class="{ placeholder: !currentOption, open }"
      :aria-expanded="open"
      @click="toggle"
    >
      <span class="req-select-value">
        <slot name="trigger" :option="currentOption">
          {{ currentOption?.label || placeholder }}
        </slot>
      </span>
      <svg class="req-select-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
    </button>

    <!-- 下拉面板 -->
    <div v-if="open" class="req-select-panel">
      <button
        v-for="(option, index) in options"
        :key="option.value"
        type="button"
        class="req-select-option"
        :class="{ active: option.value === modelValue, highlighted: index === activeIndex }"
        @mouseenter="activeIndex = index"
        @click="select(option)"
      >
        <span class="req-select-option-label">
          <slot name="option" :option="option">
            {{ option.label }}
          </slot>
        </span>
        <svg v-if="option.value === modelValue" class="req-select-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
      </button>
      <div v-if="!options.length" class="req-select-empty">暂无选项</div>
    </div>
  </div>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] }, // [{ value, label }]
  placeholder: { type: String, default: '请选择' }
})
const emit = defineEmits(['update:modelValue'])

const rootRef = ref(null)
const open = ref(false)
const activeIndex = ref(-1)

const currentOption = computed(() => props.options.find((option) => option.value === props.modelValue))

function toggle() {
  open.value = !open.value
  activeIndex.value = -1
}

function select(option) {
  emit('update:modelValue', option.value)
  open.value = false
}

function onKeydown(event) {
  if (!open.value) return
  if (event.key === 'Escape') {
    open.value = false
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!props.options.length) return
    const delta = event.key === 'ArrowDown' ? 1 : -1
    activeIndex.value = (activeIndex.value + delta + props.options.length) % props.options.length
  } else if (event.key === 'Enter' && activeIndex.value >= 0) {
    event.preventDefault()
    select(props.options[activeIndex.value])
  }
}

function onDocumentClick(event) {
  if (open.value && rootRef.value && !rootRef.value.contains(event.target)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
</script>

<style scoped>
.req-select { position: relative; }

/* 触发器 */
.req-select-trigger {
  width: 100%;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid var(--border, #e8e8e8);
  border-radius: var(--r, 6px);
  background: var(--bg, #fff);
  color: var(--fg, #202124);
  font: 13px var(--sans, inherit);
  cursor: pointer;
  box-sizing: border-box;
  transition: all var(--t, 0.15s);
}
.req-select-trigger:hover { border-color: var(--border-2, #d1d5db); }
.req-select-trigger.open {
  border-color: var(--accent, #52525b);
  box-shadow: 0 0 0 2px var(--accent-bg, rgba(82, 82, 91, 0.12));
}
.req-select-trigger.placeholder .req-select-value { color: var(--fg-4, #9ca3af); }
.req-select-value {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  text-align: left;
}
.req-select-chevron {
  width: 13px;
  height: 13px;
  flex: 0 0 auto;
  color: var(--fg-4, #9ca3af);
  transition: transform var(--t, 0.15s);
}
.req-select-trigger.open .req-select-chevron { transform: rotate(180deg); }

/* 面板 */
.req-select-panel {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 100;
  max-height: 224px;
  overflow-y: auto;
  padding: 4px;
  background: var(--bg, #fff);
  border: 1px solid color-mix(in srgb, var(--accent, #52525b) 30%, var(--border, #e8e8e8));
  border-radius: var(--r, 6px);
  box-shadow: var(--shadow-md, 0 4px 12px rgba(0, 0, 0, 0.06));
  box-sizing: border-box;
}
.req-select-panel::-webkit-scrollbar { width: 6px; }
.req-select-panel::-webkit-scrollbar-thumb { background: rgba(80, 88, 102, 0.35); border-radius: 6px; }

/* 选项 */
.req-select-option {
  width: 100%;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 10px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--fg-2, #525866);
  font: 13px var(--sans, inherit);
  text-align: left;
  cursor: pointer;
  box-sizing: border-box;
}
.req-select-option:hover,
.req-select-option.highlighted { background: var(--bg-3, #f3f4f6); }
.req-select-option.active { color: var(--accent, #52525b); font-weight: 600; }
.req-select-option-label {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.req-select-check {
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
  color: var(--accent, #52525b);
}
.req-select-empty { padding: 16px 0; text-align: center; color: var(--fg-4, #9ca3af); font-size: 12px; }
</style>
