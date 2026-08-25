<template>
  <div class="skill-selector">
    <button class="skill-trigger" type="button" title="选择技能" @click="toggle">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>
      <span v-if="modelValue.length">{{ modelValue.length }}</span>
    </button>
    <div v-if="open" class="skill-panel">
      <div class="skill-search"><input v-model="query" placeholder="搜索技能..." @keydown.esc="open = false"/></div>
      <div class="skill-list">
        <div v-if="loading" class="skill-empty">加载中...</div>
        <div v-else-if="filtered.length === 0" class="skill-empty">无匹配技能</div>
        <button v-for="skill in filtered" :key="skill.name" type="button" :class="{active: selected(skill)}" @click="select(skill)">
          <span><strong>{{ skill.name }}</strong><small v-if="skill.description">{{ skill.description }}</small></span>
          <svg v-if="selected(skill)" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, ref} from 'vue'
import {agentAPI} from '../services/api'

const props = defineProps({modelValue: {type: Array, default: () => []}})
const emit = defineEmits(['update:modelValue', 'open'])
const open = ref(false)
const query = ref('')
const skills = ref([])
const loading = ref(false)
const filtered = computed(() => {
  const term = query.value.trim().toLowerCase()
  return term ? skills.value.filter(skill => skill.name.toLowerCase().includes(term) || skill.description?.toLowerCase().includes(term)) : skills.value
})
const selected = skill => props.modelValue.some(item => item.name === skill.name)
const toggle = async () => {
  open.value = !open.value
  if (!open.value) return
  emit('open')
  if (skills.value.length || loading.value) return
  loading.value = true
  try { const result = await agentAPI.getSkills(); if (result.success) skills.value = result.data || [] } finally { loading.value = false }
}
const select = skill => {
  const next = selected(skill) ? props.modelValue.filter(item => item.name !== skill.name) : [...props.modelValue, skill]
  emit('update:modelValue', next)
}
defineExpose({close: () => { open.value = false }})
</script>

<style scoped>
.skill-selector {position:relative}.skill-trigger {position:relative;display:grid;width:28px;height:28px;place-items:center;border:0;border-radius:4px;background:transparent;color:var(--fg-3);cursor:pointer}.skill-trigger:hover{background:var(--bg-3);color:var(--fg)}.skill-trigger span{position:absolute;top:-3px;right:-3px;min-width:13px;padding:0 3px;border-radius:8px;background:var(--accent-btn);color:#fff;font-size:9px}.skill-panel{position:absolute;bottom:calc(100% + 8px);left:0;z-index:100;width:280px;overflow:hidden;border:1px solid var(--border);border-radius:var(--r);background:var(--bg);box-shadow:var(--shadow-lg)}.skill-search{padding:8px;border-bottom:1px solid var(--border)}.skill-search input{width:100%;height:30px;padding:0 8px;border:1px solid var(--border);border-radius:4px;background:var(--bg);color:var(--fg);font:12px var(--sans);outline:none}.skill-search input:focus{border-color:var(--accent)}.skill-list{max-height:240px;overflow:auto}.skill-list button{display:flex;align-items:center;justify-content:space-between;gap:10px;width:100%;padding:9px 10px;border:0;background:transparent;color:var(--fg-2);text-align:left;cursor:pointer}.skill-list button:hover,.skill-list button.active{background:var(--bg-2)}.skill-list button.active,.skill-list button.active svg{color:var(--accent)}.skill-list span{min-width:0}.skill-list strong,.skill-list small{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.skill-list strong{font-size:12px}.skill-list small{margin-top:2px;color:var(--fg-4);font-size:11px}.skill-empty{padding:20px;color:var(--fg-4);font-size:12px;text-align:center}
</style>
