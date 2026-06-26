<template>
  <div class="task-item" :class="{ active: isActive }" @click="$emit('select')">
    <span class="task-status" :class="status"></span>
    <div class="task-info">
      <div class="task-title">{{ title }}</div>
      <div class="task-meta">
        <span><i class="far fa-clock"></i> {{ time }}</span>
      </div>
    </div>
    <button class="btn-icon-sm" @click.stop="$emit('delete')" title="删除">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="18" x2="6" y1="6" y2="18"/>
        <line x1="6" x2="18" y1="6" y2="18"/>
      </svg>
    </button>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  status: { type: String, default: 'pending' },
  time: { type: String, default: '' },
  isActive: { type: Boolean, default: false }
})

defineEmits(['select', 'delete'])
</script>

<style scoped>
.task-item {
  padding: 8px 10px;
  border-radius: 7px;
  cursor: pointer;
  transition: background 0.12s;
  margin-bottom: 1px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.task-item:hover {
  background: var(--bg-muted, var(--bg-3));
}

.task-item.active {
  background: var(--accent-bg);
}

.task-status {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
  background: var(--fg-4);
}

.task-status.completed {
  background: var(--green);
}

.task-status.running {
  background: var(--accent);
  animation: pulse 2s infinite;
}

.task-status.failed {
  background: var(--red);
}

.task-status.pending {
  background: var(--fg-4);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
  line-height: 1.4;
}

.task-meta {
  font-size: 11px;
  color: var(--fg-4);
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono);
}

.task-meta i {
  font-size: 10px;
}
</style>
