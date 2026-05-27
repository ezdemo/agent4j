<template>
  <div class="statusbar">
    <div class="item">
      <span class="dot" :class="{ warn: busy }" />
      <span>{{ busy ? '处理中' : '就绪' }}</span>
    </div>
    <span class="grow" />
    <div class="item" v-if="model">
      <span class="v">{{ model }}</span>
    </div>
    <div class="item" v-if="usage.totalTokens">
      <span class="v">{{ formatTokens(usage.totalTokens) }}</span>
      <span>tokens</span>
    </div>
    <div class="item" v-if="usage.cacheHit">
      <span>cache</span>
      <span class="v">{{ formatTokens(usage.cacheHit) }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  usage: { type: Object, default: () => ({ totalTokens: 0, cacheHit: 0 }) },
  model: { type: String, default: '' },
  busy: { type: Boolean, default: false }
})

const formatTokens = (n) => !n ? '0' : n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n)
</script>

<style scoped>
.statusbar {
  grid-area: status;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 14px;
  background: var(--panel);
  border-top: 1px solid var(--border);
  font-size: 11px;
  color: var(--muted);
  height: 26px;
}
.item { display: flex; align-items: center; gap: 4px; }
.item .v { color: var(--fg-2); }
.item .dot { width: 6px; height: 6px; border-radius: 50%; background: var(--success); }
.item .dot.warn { background: var(--warning); animation: pulse 1.6s ease-out infinite; }
.grow { flex: 1; }

@keyframes pulse {
  0%   { box-shadow: 0 0 0 0 currentColor; }
  70%  { box-shadow: 0 0 0 6px transparent; }
  100% { box-shadow: 0 0 0 0 transparent; }
}
</style>
