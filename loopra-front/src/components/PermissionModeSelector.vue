<template>
  <div class="permission-selector">
    <button class="selector-button" type="button" @click="toggle">
      {{ current.label }}
      <svg width="8" height="8" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
    </button>
    <div v-if="open" class="selector-menu">
      <div class="selector-menu-title">权限模式</div>
      <button v-for="option in options" :key="option.value" type="button" :class="{ active: option.value === modelValue }" @click="select(option.value)">
        <span>{{ option.label }}</span>
        <svg v-if="option.value === modelValue" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
      </button>
    </div>
  </div>
</template>

<script setup>
import {computed, ref} from 'vue'

const props = defineProps({modelValue: {type: String, default: 'free'}})
const emit = defineEmits(['update:modelValue', 'open'])
const open = ref(false)
const options = [
  {value: 'free', label: '自由模式'},
  {value: 'approval', label: '审批模式'},
  {value: 'auto', label: '自动模式'}
]
const current = computed(() => options.find(option => option.value === props.modelValue) || options[0])
const toggle = () => {
  open.value = !open.value
  if (open.value) emit('open')
}
const select = (value) => {
  open.value = false
  if (value !== props.modelValue) emit('update:modelValue', value)
}
defineExpose({close: () => { open.value = false }})
</script>

<style scoped>
.permission-selector { position: relative; display: inline-flex; }
.selector-button { display:flex; align-items:center; gap:4px; padding:2px 6px; border:0; border-radius:var(--r-sm); background:none; color:var(--fg-2); font:600 12px var(--sans); white-space:nowrap; cursor:pointer; }
.selector-button:hover { background:var(--bg-3); }
.selector-button svg { color:var(--fg-4); }
.selector-menu { position:absolute; right:0; bottom:calc(100% + 4px); z-index:100; min-width:140px; overflow:hidden; border:1px solid var(--border); border-radius:var(--r); background:var(--bg); box-shadow:var(--shadow); }
.selector-menu-title { padding:8px 12px; border-bottom:1px solid var(--border); color:var(--fg-4); font-size:11px; font-weight:600; }
.selector-menu button { display:flex; align-items:center; justify-content:space-between; width:100%; padding:8px 12px; border:0; background:transparent; color:var(--fg-2); font:12px var(--sans); text-align:left; cursor:pointer; }
.selector-menu button:hover { background:var(--bg-2); }
.selector-menu button.active, .selector-menu button.active svg { color:var(--accent); font-weight:500; }
</style>
