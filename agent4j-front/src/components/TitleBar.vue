<template>
  <div class="titlebar">
    <div class="tb-left">
      <button class="iconbtn" @click="$emit('toggleSide')" :data-on="sideOn" title="切换侧边栏">
        <span>☰</span>
      </button>
      <div class="brand">
        <span class="mark" />
        <span class="brand-name">Agent4j</span>
      </div>
      <div class="crumbs" v-if="session">
        <span class="sep">/</span>
        <span class="cur">{{ session }}</span>
      </div>
    </div>
    <span class="grow" />
    <div class="tb-right">
      <button class="iconbtn" @click="$emit('clear')" title="清空对话" v-if="hasMessages">
        <span>🗑</span>
      </button>
      <button class="iconbtn" @click="$emit('export')" title="导出" v-if="hasMessages">
        <span>📥</span>
      </button>
      <button class="iconbtn" @click="$emit('openSettings')" title="设置">
        <span>⚙</span>
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  session: { type: String, default: '' },
  sideOn: { type: Boolean, default: true },
  hasMessages: { type: Boolean, default: false }
})
defineEmits(['toggleSide', 'openSettings', 'clear', 'export'])
</script>

<style scoped>
.titlebar {
  grid-area: title;
  display: flex;
  align-items: stretch;
  background: var(--bg-2);
  border-bottom: 1px solid var(--border);
  user-select: none;
  font-size: 14px;
  height: 36px;
}
.tb-left {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 6px 0 0;
}
.tb-right {
  display: flex;
  align-items: stretch;
}
.grow { flex: 1; }

.brand {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 4px 0 2px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--fg);
}
.brand .mark {
  width: 15px;
  height: 15px;
  border-radius: 4px;
  background: linear-gradient(135deg, var(--accent), var(--violet));
  flex-shrink: 0;
  position: relative;
}
.brand .mark::after {
  content: "";
  position: absolute;
  inset: 3px;
  border-radius: 2px;
  background: var(--bg-2);
}
.brand .brand-name { font-size: 13.5px; }

.crumbs {
  display: flex;
  gap: 5px;
  align-items: center;
  color: var(--muted);
  font-size: 13px;
  padding-left: 2px;
}
.crumbs .sep { opacity: 0.4; }
.crumbs .cur { color: var(--fg-2); }

.iconbtn {
  width: 30px;
  height: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 0;
  color: var(--muted);
  font-size: 13px;
}
.iconbtn:hover { background: var(--panel-2); color: var(--fg); }
.iconbtn[data-on="true"] { background: var(--accent-soft); color: var(--accent); }
</style>
