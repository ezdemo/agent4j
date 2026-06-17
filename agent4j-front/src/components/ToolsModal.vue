<template>
  <Teleport to="body">
    <div v-if="show" class="modal-mask" @click.self="close">
      <div class="modal">
        <div class="modal-head">
          <span>工具列表</span>
          <div class="modal-head-actions">
            <button
              class="btn-icon-sm refresh-tools-btn"
              :class="{ refreshing: refreshing }"
              @click="handleRefresh"
              :disabled="refreshing"
              title="刷新工具列表"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="23 4 23 10 17 10"/>
                <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
              </svg>
            </button>
            <button class="btn-icon-sm" @click="close">×</button>
          </div>
        </div>
        <div class="modal-body">
          <div v-for="t in tools" :key="t.name" class="tool-row">
            <code>{{ t.name }}</code>
            <span>{{ t.description }}</span>
          </div>
          <div v-if="!tools.length" class="modal-empty">加载中...</div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import {ref} from 'vue'
import {toolsAPI} from '../services/api'

const props = defineProps({
  show: { type: Boolean, default: false },
  tools: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:show', 'refreshTools'])

const refreshing = ref(false)

function close() {
  emit('update:show', false)
}

async function handleRefresh() {
  refreshing.value = true
  try {
    const r = await toolsAPI.list()
    if (r.success) {
      emit('refreshTools', r.data || [])
    }
  } catch {}
  refreshing.value = false
}
</script>
