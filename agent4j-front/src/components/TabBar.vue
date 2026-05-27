<template>
  <div class="tabbar">
    <div
      v-for="tab in tabs"
      :key="tab.id"
      class="tab"
      :data-active="tab.id === activeId"
      @click="$emit('setActive', tab.id)"
    >
      <span class="dot" :data-state="tab.state" />
      <span class="label">{{ tab.label }}</span>
      <button
        v-if="tabs.length > 1"
        class="close"
        @click.stop="$emit('close', tab.id)"
        title="关闭标签"
      >×</button>
    </div>
    <button class="tab newtab" @click="$emit('new')" title="新建标签">
      <span>+</span>
    </button>
  </div>
</template>

<script setup>
defineProps({
  tabs: { type: Array, default: () => [] },
  activeId: { type: String, default: '' }
})
defineEmits(['setActive', 'close', 'new'])
</script>

<style scoped>
.tabbar {
  grid-area: tabs;
  display: flex;
  align-items: stretch;
  background: var(--bg);
  border-bottom: 1px solid var(--border);
  padding: 0 8px;
  gap: 2px;
  overflow-x: auto;
  scrollbar-width: none;
  height: 36px;
}
.tabbar::-webkit-scrollbar { display: none; }

.tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px 0 12px;
  min-width: 0;
  max-width: 240px;
  font-size: 12px;
  color: var(--muted);
  border-right: 1px solid transparent;
  border-left: 1px solid transparent;
  position: relative;
  cursor: pointer;
}
.tab + .tab { margin-left: -1px; }
.tab:hover { color: var(--fg-2); }
.tab[data-active="true"] {
  color: var(--fg);
  background: var(--panel);
  border-color: var(--border);
}
.tab[data-active="true"]::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 2px;
  background: var(--accent);
}
.tab .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.tab .dot[data-state="running"] {
  background: var(--accent);
  animation: pulse 1.6s ease-out infinite;
  color: var(--accent);
}
.tab .dot[data-state="done"] { background: var(--success); }
.tab .dot[data-state="error"] { background: var(--danger); }
.tab .dot[data-state="idle"] { background: var(--border-strong); }
.tab .label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tab .close {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--muted-2);
  opacity: 0;
  font-size: 14px;
}
.tab:hover .close,
.tab[data-active="true"] .close { opacity: 1; }
.tab .close:hover { background: var(--panel-2); color: var(--fg); }

.tab.newtab {
  color: var(--muted);
  padding: 0 10px;
}
.tab.newtab:hover { color: var(--fg); background: var(--panel); }
</style>
